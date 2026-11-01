package com.scrollstop.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import com.scrollstop.data.time.SystemTimeProvider
import com.scrollstop.data.time.TimeProvider
import com.scrollstop.domain.model.DailyUsageRecord
import android.view.inputmethod.InputMethodManager
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
            val startTime = maxOf(0L, endTime - 300_000L)
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
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                        17 /* KEYGUARD_SHOWN */ -> {
                            resumedActivitiesMap.clear()
                            lastResumedPkg = null
                        }
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            resumedActivitiesMap.getOrPut(pkg) { mutableSetOf() }.add(className)
                            lastResumedPkg = pkg
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED,
                        24 /* ACTIVITY_DESTROYED */ -> {
                            val activeSet = resumedActivitiesMap[pkg]
                            if (activeSet != null) {
                                if (event.className != null) {
                                    activeSet.remove(event.className)
                                } else {
                                    val first = activeSet.firstOrNull()
                                    if (first != null) activeSet.remove(first)
                                }
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

    private data class EventsCalculationResult(
        val eventsUsageMap: Map<String, Long>,
        val ongoingDurationMap: Map<String, Long>,
        val hasEvents: Boolean = true
    )

    override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> {
        if (!hasUsagePermission()) {
            return Result.failure(SecurityException("Usage Access permission not granted"))
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return Result.failure(IllegalStateException("UsageStatsManager service unavailable"))

            val startTime = timeProvider.getStartOfDayMillis()
            val endTime = timeProvider.currentTimeMillis()

            val calculationResult = calculateUsageFromEvents(
                usageStatsManager = usageStatsManager,
                packageNames = packageNames,
                startTime = startTime,
                endTime = endTime
            )

            val resultMap = if (calculationResult.hasEvents) {
                packageNames.associateWith { pkg ->
                    calculationResult.eventsUsageMap[pkg] ?: 0L
                }
            } else {
                // Fallback for environments where queryEvents returns no events:
                // query daily stats strictly covering today (firstTimeStamp >= startTime)
                val dailyStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                ) ?: emptyList()
                val maxPossibleMs = maxOf(0L, endTime - startTime)
                val packageTotals = mutableMapOf<String, Long>()
                for (stat in dailyStats) {
                    if (stat.firstTimeStamp >= startTime && stat.packageName in packageNames) {
                        val current = packageTotals[stat.packageName] ?: 0L
                        packageTotals[stat.packageName] = minOf(maxOf(current, stat.totalTimeInForeground), maxPossibleMs)
                    }
                }
                packageNames.associateWith { pkg -> packageTotals[pkg] ?: 0L }
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
     * 1. Completed foreground intervals strictly belonging to today ([startTime] to [endTime]).
     * 2. Sessions spanning across local midnight, splitting durations so that pre-midnight time is excluded
     *    and post-midnight time is attributed to today.
     * 3. Live ongoing foreground intervals for the currently active app (up to [endTime]).
     * 4. Multiple activities within the same package to avoid duplicate counts or premature session closure.
     * 5. Screen interactive recovery upon unlock (SCREEN_INTERACTIVE and KEYGUARD_HIDDEN).
     */
    private fun calculateUsageFromEvents(
        usageStatsManager: UsageStatsManager,
        packageNames: Set<String>,
        startTime: Long,
        endTime: Long
    ): EventsCalculationResult {
        return try {
            val queryStart = maxOf(0L, startTime - 86_400_000L)
            val events = usageStatsManager.queryEvents(queryStart, endTime)
                ?: return EventsCalculationResult(emptyMap(), emptyMap(), hasEvents = false)
            val event = UsageEvents.Event()

            val accumulatedTimeMap = mutableMapOf<String, Long>()
            val currentSessionStartMap = mutableMapOf<String, Long>()
            val resumedActivitiesMap = mutableMapOf<String, MutableSet<String>>()
            val ongoingDurationMap = mutableMapOf<String, Long>()
            var screenIsInteractive = true
            var sawAnyEvent = false

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                sawAnyEvent = true
                val eventType = event.eventType
                val eventTime = event.timeStamp.coerceIn(queryStart, endTime)

                // Handle screen non-interactive (screen off) / keyguard shown (device locked):
                // Close active sessions at event timestamp; preserve resumed activities for resume upon unlock
                if (eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                    eventType == 17 /* KEYGUARD_SHOWN */
                ) {
                    screenIsInteractive = false
                    for (pkg in packageNames) {
                        val start = currentSessionStartMap.remove(pkg)
                        if (start != null) {
                            val effectiveStart = maxOf(start, startTime)
                            val effectiveEnd = minOf(eventTime, endTime)
                            if (effectiveEnd > effectiveStart) {
                                val sessionDuration = effectiveEnd - effectiveStart
                                accumulatedTimeMap[pkg] = (accumulatedTimeMap[pkg] ?: 0L) + sessionDuration
                                Log.d("UsageStatsRepository", "Diagnostic: App $pkg screen-off pause session: start=$effectiveStart, end=$effectiveEnd, duration=$sessionDuration")
                            }
                        }
                    }
                    continue
                }

                // Handle screen interactive (screen on) / keyguard hidden (device unlocked):
                // Resume sessions for active foreground apps
                if (eventType == UsageEvents.Event.SCREEN_INTERACTIVE ||
                    eventType == 18 /* KEYGUARD_HIDDEN */
                ) {
                    screenIsInteractive = true
                    for (pkg in packageNames) {
                        val activeSet = resumedActivitiesMap[pkg]
                        if (activeSet != null && activeSet.isNotEmpty() && !currentSessionStartMap.containsKey(pkg)) {
                            currentSessionStartMap[pkg] = eventTime
                            Log.d("UsageStatsRepository", "Diagnostic: App $pkg screen-on resume session: start=$eventTime")
                        }
                    }
                    continue
                }

                val pkg = event.packageName ?: continue

                // When ANY application resumes:
                // Close any OTHER monitored app's session since another app has taken the foreground
                if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    screenIsInteractive = true
                    for (monitoredPkg in packageNames) {
                        if (monitoredPkg != pkg) {
                            resumedActivitiesMap.remove(monitoredPkg)
                            val start = currentSessionStartMap.remove(monitoredPkg)
                            if (start != null) {
                                val effectiveStart = maxOf(start, startTime)
                                val effectiveEnd = minOf(eventTime, endTime)
                                if (effectiveEnd > effectiveStart) {
                                    val sessionDuration = effectiveEnd - effectiveStart
                                    accumulatedTimeMap[monitoredPkg] = (accumulatedTimeMap[monitoredPkg] ?: 0L) + sessionDuration
                                    Log.d("UsageStatsRepository", "Diagnostic: App $monitoredPkg preempted by $pkg: start=$effectiveStart, end=$effectiveEnd, duration=$sessionDuration")
                                }
                            }
                        }
                    }

                    if (pkg in packageNames) {
                        val activeSet = resumedActivitiesMap.getOrPut(pkg) { mutableSetOf() }
                        if (activeSet.isEmpty()) {
                            currentSessionStartMap[pkg] = eventTime
                            Log.d("UsageStatsRepository", "Diagnostic: App $pkg session start: start=$eventTime")
                        }
                        val className = event.className ?: "activity_${event.timeStamp}"
                        activeSet.add(className)
                    }
                    continue
                }

                if (pkg !in packageNames) continue

                when (eventType) {
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    24 /* ACTIVITY_DESTROYED */ -> {
                        val activeSet = resumedActivitiesMap[pkg]
                        if (activeSet != null && activeSet.isNotEmpty()) {
                            if (event.className != null) {
                                activeSet.remove(event.className)
                            } else {
                                val first = activeSet.firstOrNull()
                                if (first != null) activeSet.remove(first)
                            }
                            if (activeSet.isEmpty()) {
                                resumedActivitiesMap.remove(pkg)
                                val start = currentSessionStartMap.remove(pkg)
                                if (start != null) {
                                    val effectiveStart = maxOf(start, startTime)
                                    val effectiveEnd = minOf(eventTime, endTime)
                                    if (effectiveEnd > effectiveStart) {
                                        val sessionDuration = effectiveEnd - effectiveStart
                                        accumulatedTimeMap[pkg] = (accumulatedTimeMap[pkg] ?: 0L) + sessionDuration
                                        Log.d("UsageStatsRepository", "Diagnostic: App $pkg session closed: start=$effectiveStart, end=$effectiveEnd, duration=$sessionDuration")
                                    }
                                }
                            }
                        }
                        // Orphan or duplicate pause/stop/destroy event: do nothing. Never attribute time from day start.
                    }
                }
            }

            // Include ongoing session if the app is currently in foreground at endTime and screen is interactive
            if (screenIsInteractive) {
                for (pkg in packageNames) {
                    val activeSet = resumedActivitiesMap[pkg]
                    if (activeSet != null && activeSet.isNotEmpty()) {
                        val start = currentSessionStartMap[pkg]
                        if (start != null) {
                            val effectiveStart = maxOf(start, startTime)
                            if (endTime > effectiveStart) {
                                val ongoingDuration = endTime - effectiveStart
                                ongoingDurationMap[pkg] = ongoingDuration
                                accumulatedTimeMap[pkg] = (accumulatedTimeMap[pkg] ?: 0L) + ongoingDuration
                                Log.d("UsageStatsRepository", "Diagnostic: App $pkg ongoing session: start=$effectiveStart, end=$endTime, duration=$ongoingDuration")
                            }
                        }
                    }
                }
            }

            val maxPossibleMs = maxOf(0L, endTime - startTime)
            val clampedMap = packageNames.associateWith { pkg ->
                minOf(accumulatedTimeMap[pkg] ?: 0L, maxPossibleMs)
            }

            EventsCalculationResult(
                eventsUsageMap = clampedMap,
                ongoingDurationMap = ongoingDurationMap,
                hasEvents = sawAnyEvent
            )
        } catch (e: Exception) {
            EventsCalculationResult(emptyMap(), emptyMap(), hasEvents = false)
        }
    }

    /**
     * Resolves all home launcher packages on the device to exclude home screen idling from device screen time.
     */
    private fun getExcludedLauncherPackages(): Set<String> {
        return try {
            val pm = context.packageManager
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolvedLaunchers = pm.queryIntentActivities(homeIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()

            resolvedLaunchers + setOf(
                "com.google.android.apps.nexuslauncher",
                "com.sec.android.app.launcher",
                "com.android.launcher3",
                "com.miui.home",
                "com.oppo.launcher",
                "com.coloros.home",
                "com.huawei.android.launcher",
                "net.oneplus.launcher",
                "com.motorola.launcher3"
            )
        } catch (e: Exception) {
            setOf(
                "com.google.android.apps.nexuslauncher",
                "com.sec.android.app.launcher",
                "com.android.launcher3"
            )
        }
    }

    /**
     * Resolves all installed input method (keyboard) packages on the device.
     */
    private fun getExcludedImePackages(): Set<String> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.inputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Determines whether a package should be excluded from total device screen time.
     *
     * Matches Android Digital Wellbeing behavior:
     * - Excludes system daemons ("android", "com.android.systemui", "com.google.android.gms").
     * - Excludes the home screen launcher (Pixel Launcher, Samsung One UI Home, etc.).
     * - Excludes keyboards and input methods (IMEs) to prevent duplicate counting while typing.
     * - INCLUDES all user-facing applications, device Settings ("com.android.settings"), phone dialer/in-call
     *   screens ("com.android.incallui"), files/document viewers, and Stop Doom Scroll itself.
     */
    internal fun isExcludedFromDeviceUsage(
        pkg: String,
        launcherPackages: Set<String> = getExcludedLauncherPackages(),
        imePackages: Set<String> = getExcludedImePackages()
    ): Boolean {
        if (pkg == "android" || pkg == "com.android.systemui") return true
        if (pkg in launcherPackages) return true
        if (pkg in imePackages) return true

        val lower = pkg.lowercase(Locale.ROOT)
        if (lower.contains("inputmethod") || lower.contains(".ime") || lower.endsWith(".keyboard")) return true
        if (lower == "com.google.android.inputmethod.latin" || lower == "com.samsung.android.honeyboard" || lower.contains("swiftkey")) return true
        if (lower == "com.google.android.gms") return true

        return false
    }

    /**
     * Calculates non-overlapping device foreground screen time across user-facing applications
     * from chronological [UsageEvents] between [startTime] and [endTime].
     *
     * Returns null if no events exist in the requested timeframe (fallback to daily interval stats).
     */
    private fun calculateDeviceUsageFromEvents(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): Long? {
        val queryStart = maxOf(0L, startTime - 86_400_000L)
        val events = usageStatsManager.queryEvents(queryStart, endTime) ?: return null
        if (!events.hasNextEvent()) return null

        val launcherPackages = getExcludedLauncherPackages()
        val imePackages = getExcludedImePackages()
        val event = UsageEvents.Event()

        var activeSessionStart: Long? = null
        val activeResumedPackages = mutableSetOf<String>()
        val resumedActivitiesMap = mutableMapOf<String, MutableSet<String>>()
        var accumulatedTimeMs = 0L
        var screenIsInteractive = true

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val eventType = event.eventType
            val eventTime = event.timeStamp.coerceIn(queryStart, endTime)
            val pkg = event.packageName

            // Handle screen non-interactive (screen off) / keyguard shown (device locked):
            // Close active session at event timestamp
            if (eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                eventType == 17 /* KEYGUARD_SHOWN */
            ) {
                screenIsInteractive = false
                if (activeSessionStart != null) {
                    val effectiveStart = maxOf(activeSessionStart, startTime)
                    val effectiveEnd = minOf(eventTime, endTime)
                    if (effectiveEnd > effectiveStart) {
                        val duration = effectiveEnd - effectiveStart
                        accumulatedTimeMs += duration
                        Log.d("UsageStatsRepository", "Diagnostic: Device screen-off session: start=$effectiveStart, end=$effectiveEnd, duration=$duration")
                    }
                    activeSessionStart = null
                }
                continue
            }

            // Handle screen interactive (screen on) / keyguard hidden (device unlocked):
            // Resume active session if an app is still in foreground
            if (eventType == UsageEvents.Event.SCREEN_INTERACTIVE ||
                eventType == 18 /* KEYGUARD_HIDDEN */
            ) {
                screenIsInteractive = true
                if (activeResumedPackages.isNotEmpty() && activeSessionStart == null) {
                    activeSessionStart = eventTime
                    Log.d("UsageStatsRepository", "Diagnostic: Device screen-on resume session: start=$eventTime")
                }
                continue
            }

            if (pkg == null || isExcludedFromDeviceUsage(pkg, launcherPackages, imePackages)) continue

            val className = event.className ?: "default_activity"

            when (eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    screenIsInteractive = true
                    val activeSet = resumedActivitiesMap.getOrPut(pkg) { mutableSetOf() }
                    activeSet.add(className)
                    activeResumedPackages.add(pkg)

                    if (activeSessionStart == null) {
                        activeSessionStart = eventTime
                        Log.d("UsageStatsRepository", "Diagnostic: Device session start ($pkg): start=$eventTime")
                    }
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                24 /* ACTIVITY_DESTROYED */ -> {
                    val activeSet = resumedActivitiesMap[pkg]
                    if (activeSet != null && activeSet.isNotEmpty()) {
                        activeSet.remove(className)
                        if (activeSet.isEmpty()) {
                            resumedActivitiesMap.remove(pkg)
                            activeResumedPackages.remove(pkg)
                        }
                    } else {
                        activeResumedPackages.remove(pkg)
                    }

                    if (activeResumedPackages.isEmpty() && activeSessionStart != null) {
                        val effectiveStart = maxOf(activeSessionStart, startTime)
                        val effectiveEnd = minOf(eventTime, endTime)
                        if (effectiveEnd > effectiveStart) {
                            val duration = effectiveEnd - effectiveStart
                            accumulatedTimeMs += duration
                            Log.d("UsageStatsRepository", "Diagnostic: Device session closed: start=$effectiveStart, end=$effectiveEnd, duration=$duration")
                        }
                        activeSessionStart = null
                    }
                    // If activeSessionStart == null or activeResumedPackages is not empty, do not add orphan time
                }
            }
        }

        // Include ongoing active session if the device is currently in foreground use at endTime and screen is interactive
        if (screenIsInteractive && activeResumedPackages.isNotEmpty() && activeSessionStart != null) {
            val effectiveStart = maxOf(activeSessionStart, startTime)
            if (endTime > effectiveStart) {
                val duration = endTime - effectiveStart
                accumulatedTimeMs += duration
                Log.d("UsageStatsRepository", "Diagnostic: Device ongoing session: start=$effectiveStart, end=$endTime, duration=$duration")
            }
        }

        val maxPossible = maxOf(0L, endTime - startTime)
        return minOf(accumulatedTimeMs, maxPossible)
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

            val usageFromEvents = calculateDeviceUsageFromEvents(
                usageStatsManager = usageStatsManager,
                startTime = startTime,
                endTime = endTime
            )

            if (usageFromEvents != null) {
                Result.success(usageFromEvents)
            } else {
                // Fallback: Query daily interval stats filtered strictly to non-excluded applications
                val launcherPackages = getExcludedLauncherPackages()
                val imePackages = getExcludedImePackages()
                val dailyStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                ) ?: emptyList()

                val filteredStats = dailyStats.filter {
                    it.firstTimeStamp >= startTime && !isExcludedFromDeviceUsage(it.packageName, launcherPackages, imePackages)
                }

                // Consolidate foreground times per unique package to eliminate duplicate bucket intervals
                val packageTotals = mutableMapOf<String, Long>()
                for (stat in filteredStats) {
                    val pkg = stat.packageName
                    val current = packageTotals[pkg] ?: 0L
                    packageTotals[pkg] = maxOf(current, stat.totalTimeInForeground)
                }

                val totalMs = packageTotals.values.sum()
                val maxPossibleMs = maxOf(0L, endTime - startTime)
                Result.success(minOf(totalMs, maxPossibleMs))
            }
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

            // Calculate range start (6 days before today)
            val rangeCal = Calendar.getInstance().apply {
                timeInMillis = todayStart
                add(Calendar.DAY_OF_YEAR, -6)
            }
            val rangeStart = rangeCal.timeInMillis

            // Pre-fetch all daily interval stats over the 7-day range to verify record presence
            val intervalStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                rangeStart,
                now
            ) ?: emptyList()

            val launcherPackages = getExcludedLauncherPackages()
            val imePackages = getExcludedImePackages()
            val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.US)
            val dayLabelFormat = SimpleDateFormat("EEE d", Locale.US)
            val records = mutableListOf<DailyUsageRecord>()

            for (dayOffset in 6 downTo 0) {
                val dayCal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    add(Calendar.DAY_OF_YEAR, -dayOffset)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayStart = dayCal.timeInMillis
                val isToday = (dayOffset == 0)
                val dayEnd = if (isToday) now else {
                    Calendar.getInstance().apply {
                        timeInMillis = dayStart
                        add(Calendar.DAY_OF_YEAR, 1)
                    }.timeInMillis - 1L
                }

                val date = Date(dayStart)
                val dayOfWeek = dayOfWeekFormat.format(date)
                val dayLabel = if (isToday) "Today" else dayLabelFormat.format(date)

                if (isToday) {
                    val todayUsage = getTodayTotalUsageMs().getOrDefault(0L)
                    records.add(
                        DailyUsageRecord(
                            dayLabel = dayLabel,
                            dayOfWeek = dayOfWeek,
                            dateEpochMillis = dayStart,
                            totalScreenTimeMs = todayUsage,
                            hasRecord = true,
                            isCurrentDay = true
                        )
                    )
                } else {
                    // Past day: first attempt event reconstruction if available
                    val eventUsage = calculateDeviceUsageFromEvents(
                        usageStatsManager = usageStatsManager,
                        startTime = dayStart,
                        endTime = dayEnd
                    )

                    if (eventUsage != null) {
                        records.add(
                            DailyUsageRecord(
                                dayLabel = dayLabel,
                                dayOfWeek = dayOfWeek,
                                dateEpochMillis = dayStart,
                                totalScreenTimeMs = eventUsage,
                                hasRecord = true,
                                isCurrentDay = false
                            )
                        )
                    } else {
                        // Check if OS has daily bucket interval stats covering this calendar day
                        val statsForDay = intervalStats.filter { stat ->
                            stat.firstTimeStamp <= dayEnd && stat.lastTimeStamp >= dayStart
                        }

                        val hasOsRecord = statsForDay.isNotEmpty()

                        if (hasOsRecord) {
                            val userStats = statsForDay.filter {
                                !isExcludedFromDeviceUsage(it.packageName, launcherPackages, imePackages)
                            }

                            val packageTotals = mutableMapOf<String, Long>()
                            for (stat in userStats) {
                                val pkg = stat.packageName
                                val current = packageTotals[pkg] ?: 0L
                                packageTotals[pkg] = maxOf(current, stat.totalTimeInForeground)
                            }

                            val totalDayMs = packageTotals.values.sum()
                            val dayDuration = dayEnd - dayStart + 1L
                            val cappedDayMs = minOf(totalDayMs, dayDuration)

                            records.add(
                                DailyUsageRecord(
                                    dayLabel = dayLabel,
                                    dayOfWeek = dayOfWeek,
                                    dateEpochMillis = dayStart,
                                    totalScreenTimeMs = cappedDayMs,
                                    hasRecord = true,
                                    isCurrentDay = false
                                )
                            )
                        } else {
                            // Genuine missing / unavailable day (predates installation, device was off, or history purged)
                            records.add(
                                DailyUsageRecord(
                                    dayLabel = dayLabel,
                                    dayOfWeek = dayOfWeek,
                                    dateEpochMillis = dayStart,
                                    totalScreenTimeMs = 0L,
                                    hasRecord = false,
                                    isCurrentDay = false
                                )
                            )
                        }
                    }
                }
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
