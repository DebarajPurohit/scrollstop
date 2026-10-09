package com.scrollstop.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.scrollstop.data.time.SystemTimeProvider
import com.scrollstop.data.time.TimeProvider

/**
 * Repository interface for detecting Usage Access permission and querying today's application usage stats.
 */
interface UsageStatsRepository {
    fun hasUsagePermission(): Boolean
    fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>>
}

/**
 * Android implementation of [UsageStatsRepository] backed by [UsageStatsManager] and [AppOpsManager].
 *
 * Architecture & Accuracy (POC-06 Fix):
 * [UsageStatsManager.queryAndAggregateUsageStats] provides aggregated historical stats for closed sessions,
 * but does NOT update [UsageStats.totalTimeInForeground] in real time while an application remains actively
 * in the foreground.
 *
 * To ensure live usage freshness while keeping [UsageStatsManager] as the single authoritative source of truth,
 * this repository combines aggregated stats with real-time foreground session tracking computed from
 * [UsageStatsManager.queryEvents].
 */
class AndroidUsageStatsRepository(
    private val context: Context,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) : UsageStatsRepository {

    override fun hasUsagePermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false

            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> {
        if (!hasUsagePermission()) {
            return Result.failure(SecurityException("Usage Access permission not granted"))
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return Result.failure(IllegalStateException("UsageStatsManager service unavailable"))

            val startTime = timeProvider.getStartOfDayMillis()
            val endTime = timeProvider.currentTimeMillis()

            val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
                ?: emptyMap()

            val eventsCalculatedStats = calculateUsageFromEvents(
                usageStatsManager = usageStatsManager,
                packageNames = packageNames,
                startTime = startTime,
                endTime = endTime
            )

            val resultMap = packageNames.associateWith { pkg ->
                val aggregatedMs = aggregatedStats[pkg]?.totalTimeInForeground ?: 0L
                val eventsMs = eventsCalculatedStats[pkg] ?: 0L
                maxOf(aggregatedMs, eventsMs)
            }

            Result.success(resultMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reconstructs foreground session durations directly from [UsageEvents] between [startTime] and [endTime].
     *
     * Handles:
     * 1. Completed foreground intervals for the day.
     * 2. Live ongoing foreground intervals for the currently active app (up to [endTime]).
     * 3. Multiple activities within the same package to avoid duplicate counts or premature session closure.
     */
    private fun calculateUsageFromEvents(
        usageStatsManager: UsageStatsManager,
        packageNames: Set<String>,
        startTime: Long,
        endTime: Long
    ): Map<String, Long> {
        return try {
            val events = usageStatsManager.queryEvents(startTime, endTime) ?: return emptyMap()
            val event = UsageEvents.Event()

            val accumulatedTimeMap = mutableMapOf<String, Long>()
            val currentSessionStartMap = mutableMapOf<String, Long>()
            val resumedActivitiesMap = mutableMapOf<String, MutableSet<String>>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (pkg !in packageNames) continue

                val eventType = event.eventType
                val eventTime = event.timeStamp
                val className = event.className ?: "default_activity"

                when (eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        val activeSet = resumedActivitiesMap.getOrPut(pkg) { mutableSetOf() }
                        if (activeSet.isEmpty()) {
                            currentSessionStartMap[pkg] = eventTime
                        }
                        activeSet.add(className)
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val activeSet = resumedActivitiesMap[pkg]
                        if (activeSet != null && activeSet.isNotEmpty()) {
                            val removed = activeSet.remove(className)
                            if (activeSet.isEmpty() || (!removed && activeSet.size == 1)) {
                                activeSet.clear()
                                val start = currentSessionStartMap.remove(pkg)
                                if (start != null && eventTime > start) {
                                    val sessionDuration = eventTime - start
                                    accumulatedTimeMap[pkg] = (accumulatedTimeMap[pkg] ?: 0L) + sessionDuration
                                }
                            }
                        }
                    }
                }
            }

            // Include ongoing session if the app is currently in foreground at endTime
            for (pkg in packageNames) {
                val activeSet = resumedActivitiesMap[pkg]
                if (activeSet != null && activeSet.isNotEmpty()) {
                    val start = currentSessionStartMap[pkg]
                    if (start != null && endTime > start) {
                        val ongoingDuration = endTime - start
                        accumulatedTimeMap[pkg] = (accumulatedTimeMap[pkg] ?: 0L) + ongoingDuration
                    }
                }
            }

            accumulatedTimeMap
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
