package com.scrollstop.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.scrollstop.data.time.SystemTimeProvider
import com.scrollstop.data.time.TimeProvider
import com.scrollstop.domain.model.DailyUsageRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Repository interface for detecting Usage Access permission and querying today's application usage stats.
 */
interface UsageStatsRepository {
    fun hasUsagePermission(): Boolean
    fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>>
    fun getForegroundPackage(): String? = null
    fun getTodayTotalUsageMs(): Result<Long> = Result.success(0L)
    fun getSevenDayDailyUsage(): Result<List<DailyUsageRecord>> = Result.success(emptyList())
    fun getMultiDayUsageForPackages(packageNames: Set<String>, daysCount: Int = 7): Result<Map<String, Long>> = Result.success(emptyMap())
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

    override fun getForegroundPackage(): String? {
        if (!hasUsagePermission()) return null
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null

            val endTime = timeProvider.currentTimeMillis()
            // Inspect recent events (up to 5 minutes) to detect currently active foreground app
            val startTime = maxOf(timeProvider.getStartOfDayMillis(), endTime - 300_000L)
            val events = usageStatsManager.queryEvents(startTime, endTime)

            val resumedActivitiesMap = mutableMapOf<String, MutableSet<String>>()
            var lastResumedPkg: String? = null

            if (events != null) {
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    val eventType = event.eventType
                    val className = event.className ?: "default_activity"

                    when (eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            resumedActivitiesMap.getOrPut(pkg) { mutableSetOf() }.add(className)
                            lastResumedPkg = pkg
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            val activeSet = resumedActivitiesMap[pkg]
                            if (activeSet != null) {
                                activeSet.remove(className)
                                if (activeSet.isEmpty()) {
                                    resumedActivitiesMap.remove(pkg)
                                }
                            }
                        }
                    }
                }
            }

            val activePackages = resumedActivitiesMap.keys
            if (activePackages.isNotEmpty()) {
                if (lastResumedPkg != null && lastResumedPkg in activePackages) {
                    lastResumedPkg
                } else {
                    activePackages.firstOrNull()
                }
            } else {
                // Fallback: check queryUsageStats for most recent lastTimeUsed within last 60 seconds
                val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                val mostRecent = usageStatsList?.filter { (endTime - it.lastTimeUsed) <= 60_000L }?.maxByOrNull { it.lastTimeUsed }
                mostRecent?.packageName
            }
        } catch (e: Exception) {
            null
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
                            activeSet.remove(className)
                            if (activeSet.isEmpty()) {
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

    override fun getTodayTotalUsageMs(): Result<Long> {
        if (!hasUsagePermission()) {
            return Result.failure(SecurityException("Usage Access permission not granted"))
        }
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return Result.failure(IllegalStateException("UsageStatsManager service unavailable"))

            val startTime = timeProvider.getStartOfDayMillis()
            val endTime = timeProvider.currentTimeMillis()

            val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime) ?: emptyMap()
            val totalMs = aggregatedStats.values.sumOf { it.totalTimeInForeground }
            Result.success(totalMs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSevenDayDailyUsage(): Result<List<DailyUsageRecord>> {
        if (!hasUsagePermission()) {
            return Result.failure(SecurityException("Usage Access permission not granted"))
        }
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return Result.failure(IllegalStateException("UsageStatsManager service unavailable"))

            val now = timeProvider.currentTimeMillis()
            val todayStart = timeProvider.getStartOfDayMillis()

            // Pre-fetch all daily interval stats over the 7-day range to verify record presence
            val sevenDaysAgoCalendar = Calendar.getInstance().apply {
                timeInMillis = todayStart
                add(Calendar.DAY_OF_YEAR, -6)
            }
            val rangeStart = sevenDaysAgoCalendar.timeInMillis
            val intervalStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                rangeStart,
                now
            ) ?: emptyList()

            val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.US)
            val dayLabelFormat = SimpleDateFormat("EEE d", Locale.US)
            val records = mutableListOf<DailyUsageRecord>()

            for (dayOffset in 6 downTo 0) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    add(Calendar.DAY_OF_YEAR, -dayOffset)
                }
                val dayStart = cal.timeInMillis
                val isToday = (dayOffset == 0)
                val dayEnd = if (isToday) now else dayStart + (24 * 60 * 60 * 1000L) - 1L

                val dayAggregated = usageStatsManager.queryAndAggregateUsageStats(dayStart, dayEnd) ?: emptyMap()
                val totalDayMs = dayAggregated.values.sumOf { it.totalTimeInForeground }

                val date = Date(dayStart)
                val dayOfWeek = dayOfWeekFormat.format(date)
                val dayLabel = if (isToday) "Today" else dayLabelFormat.format(date)

                // Today always has an active record because Stop Doom Scroll is running.
                // For past days, hasRecord is true if either usage was recorded (> 0)
                // or the operating system recorded an interval covering this day.
                // If the day predates OS records/installation, hasRecord is false.
                val hasRecord = if (isToday) {
                    true
                } else if (totalDayMs > 0L) {
                    true
                } else {
                    intervalStats.any { stat ->
                        stat.firstTimeStamp <= dayEnd && stat.lastTimeStamp >= dayStart &&
                                (stat.totalTimeInForeground > 0L || stat.lastTimeUsed > 0L)
                    }
                }

                records.add(
                    DailyUsageRecord(
                        dayLabel = dayLabel,
                        dayOfWeek = dayOfWeek,
                        dateEpochMillis = dayStart,
                        totalScreenTimeMs = if (hasRecord) totalDayMs else 0L,
                        hasRecord = hasRecord,
                        isCurrentDay = isToday
                    )
                )
            }

            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getMultiDayUsageForPackages(packageNames: Set<String>, daysCount: Int): Result<Map<String, Long>> {
        if (!hasUsagePermission()) {
            return Result.failure(SecurityException("Usage Access permission not granted"))
        }
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return Result.failure(IllegalStateException("UsageStatsManager service unavailable"))

            val now = timeProvider.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                timeInMillis = timeProvider.getStartOfDayMillis()
                add(Calendar.DAY_OF_YEAR, -(daysCount - 1))
            }
            val startTime = cal.timeInMillis

            val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(startTime, now) ?: emptyMap()
            val resultMap = packageNames.associateWith { pkg ->
                aggregatedStats[pkg]?.totalTimeInForeground ?: 0L
            }
            Result.success(resultMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
