package com.scrollstop

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.repository.AndroidUsageStatsRepository
import com.scrollstop.data.time.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager
import java.util.Calendar

/**
 * Deterministic unit test suite covering the Critical Usage Calculation Audit
 * and enforcing all 8 mathematical invariants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageCalculationAuditTest {

    private lateinit var context: Context
    private lateinit var appOpsManager: AppOpsManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var shadowUsageStats: ShadowUsageStatsManager

    private class AuditTestClock(
        var currentTime: Long,
        var startOfDay: Long,
        var nextMidnight: Long = startOfDay + 86_400_000L
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        override fun getStartOfDayMillis(): Long = startOfDay
        override fun getNextMidnightMillis(): Long = nextMidnight
    }

    private val midnight = 1_700_000_000_000L // 00:00:00 local time
    private val confirmtktPkg = "com.confirmtkt.lite"
    private val instagramPkg = "com.instagram.android"
    private val youtubePkg = "com.google.android.youtube"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        shadowUsageStats = Shadows.shadowOf(usageStatsManager)

        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )
    }

    // --- REPRODUCTION & ROOT CAUSE TESTS ---

    /**
     * Exact reproduction of the 85-hour bug:
     * Confirmtkt is used 4 times during the day (e.g. 2 minutes each = 8m total).
     * On each exit, Android emits both ACTIVITY_PAUSED and ACTIVITY_STOPPED.
     * With the old code, each ACTIVITY_STOPPED was treated as an orphan pause from midnight,
     * adding ~21 hours on each session and creating 85+ hours of usage.
     * Verify that with the fix, usage is exactly 8 minutes (4 * 2m).
     */
    @Test
    fun verify_confirmtkt_multiple_sessions_with_duplicate_pause_stop_events_calculates_exact_time() {
        // Current time is 10:28 PM (22h 28m after midnight)
        val now = midnight + (22 * 3600 + 28 * 60) * 1000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // Session 1: 9:00 AM - 9:02 AM (2 mins)
        val s1Start = midnight + (9 * 3600) * 1000L
        val s1End = s1Start + 120_000L
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "MainActivity", s1Start, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "MainActivity", s1End, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "MainActivity", s1End + 50L, UsageEvents.Event.ACTIVITY_STOPPED))

        // Session 2: 1:00 PM - 1:02 PM (2 mins)
        val s2Start = midnight + (13 * 3600) * 1000L
        val s2End = s2Start + 120_000L
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "SearchActivity", s2Start, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "SearchActivity", s2End, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "SearchActivity", s2End + 50L, UsageEvents.Event.ACTIVITY_STOPPED))

        // Session 3: 6:00 PM - 6:02 PM (2 mins)
        val s3Start = midnight + (18 * 3600) * 1000L
        val s3End = s3Start + 120_000L
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "PnrActivity", s3Start, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "PnrActivity", s3End, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "PnrActivity", s3End + 50L, UsageEvents.Event.ACTIVITY_STOPPED))

        // Session 4: 9:00 PM - 9:02 PM (2 mins)
        val s4Start = midnight + (21 * 3600) * 1000L
        val s4End = s4Start + 120_000L
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "DetailsActivity", s4Start, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "DetailsActivity", s4End, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "DetailsActivity", s4End + 50L, UsageEvents.Event.ACTIVITY_STOPPED))

        val result = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()
        val totalMs = result[confirmtktPkg] ?: 0L

        // Under the old bug: 4 sessions * ~21h fallback = 85h!
        // Under correct reconstruction: Exactly 4 * 120_000L = 480_000L (8 minutes).
        assertEquals(480_000L, totalMs)
    }

    /**
     * Exact reproduction of the 23h 59m device usage bug:
     * Throughout the day, several apps open and close.
     * Under the old code, every ACTIVITY_STOPPED when activeSessionStart was null
     * added (eventTime - midnight) to device usage, inflating device usage to hundreds of hours,
     * which was then clamped to maxPossible (22h 28m today, 23h 59m on past days).
     * Verify that with the fix, total device usage is the actual interactive time (e.g. 15 minutes).
     */
    @Test
    fun verify_device_usage_with_multiple_apps_and_stops_calculates_exact_screen_time_not_inflated() {
        // Current time is 10:28 PM (22h 28m after midnight)
        val now = midnight + (22 * 3600 + 28 * 60) * 1000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // App 1: 8:00 AM - 8:05 AM (5 mins)
        val t1 = midnight + (8 * 3600) * 1000L
        val t2 = t1 + 300_000L
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", t1, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", t2, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", t2 + 30L, UsageEvents.Event.ACTIVITY_STOPPED))

        // App 2: 12:00 PM - 12:10 PM (10 mins)
        val t3 = midnight + (12 * 3600) * 1000L
        val t4 = t3 + 600_000L
        shadowUsageStats.addEvent(createEvent(instagramPkg, "Feed", t3, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(instagramPkg, "Feed", t4, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(instagramPkg, "Feed", t4 + 30L, UsageEvents.Event.ACTIVITY_STOPPED))

        val totalDeviceMs = repository.getTodayTotalUsageMs().getOrThrow()

        // Total interactive time should be exactly 5m + 10m = 15m (900_000 ms), NOT 22h 27m!
        assertEquals(900_000L, totalDeviceMs)
    }

    // --- MATHEMATICAL INVARIANTS ---

    /**
     * Invariant 1: Usage for one package on one calendar day cannot exceed the elapsed time in that day.
     */
    @Test
    fun invariant1_usageCannotExceedElapsedDayTime() {
        val now = midnight + 3_600_000L // 1 hour into the day
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // Simulate malformed or wild event sequence
        val s1 = midnight + 100_000L
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", s1, UsageEvents.Event.ACTIVITY_RESUMED))
        // Still open at now (ongoing)

        val result = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()
        val usage = result[confirmtktPkg] ?: 0L

        assertTrue(usage <= 3_600_000L)
    }

    /**
     * Invariant 2: Total phone usage cannot exceed elapsed time.
     */
    @Test
    fun invariant2_totalPhoneUsageCannotExceedElapsedDayTime() {
        val now = midnight + 7_200_000L // 2 hours into the day
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // Multiple simulated sessions
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", midnight, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", now, UsageEvents.Event.ACTIVITY_PAUSED))

        val total = repository.getTodayTotalUsageMs().getOrThrow()
        assertTrue(total <= 7_200_000L)
    }

    /**
     * Invariant 3: Sum of non-overlapping app foreground durations must not exceed total interactive device time.
     */
    @Test
    fun invariant3_sumOfAppUsageDoesNotExceedDeviceTime() {
        val now = midnight + 10_000_000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // App A for 5m
        val aStart = midnight + 1_000_000L
        val aEnd = aStart + 300_000L
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", aStart, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", aEnd, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", aEnd + 50L, UsageEvents.Event.ACTIVITY_STOPPED))

        // App B for 10m
        val bStart = midnight + 2_000_000L
        val bEnd = bStart + 600_000L
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", bStart, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", bEnd, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", bEnd + 50L, UsageEvents.Event.ACTIVITY_STOPPED))

        val appUsage = repository.getTodayUsageForPackages(setOf(youtubePkg, confirmtktPkg)).getOrThrow()
        val sumAppUsage = (appUsage[youtubePkg] ?: 0L) + (appUsage[confirmtktPkg] ?: 0L)
        val deviceUsage = repository.getTodayTotalUsageMs().getOrThrow()

        assertEquals(900_000L, sumAppUsage)
        assertEquals(900_000L, deviceUsage)
        assertEquals(deviceUsage, sumAppUsage)
    }

    /**
     * Invariant 4: A session is counted exactly once.
     * Duplicate pause and stop events must not double count.
     */
    @Test
    fun invariant4_sessionCountedExactlyOnce_evenWithDuplicatePauseStop() {
        val now = midnight + 3_600_000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        val start = midnight + 600_000L
        val end = start + 300_000L // 5 mins
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", start, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", end, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", end + 10L, UsageEvents.Event.ACTIVITY_STOPPED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", end + 20L, 24 /* ACTIVITY_DESTROYED */))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()[confirmtktPkg] ?: 0L
        assertEquals(300_000L, usage)
    }

    /**
     * Invariant 5: No usage is attributed before its actual start.
     * An orphan pause event without any preceding resume must attribute 0 time.
     */
    @Test
    fun invariant5_orphanPauseEventWithoutResume_attributesZeroUsage() {
        val now = midnight + 3_600_000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // Orphan pause and stop events without resume
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", midnight + 1_800_000L, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", midnight + 1_800_050L, UsageEvents.Event.ACTIVITY_STOPPED))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()[confirmtktPkg] ?: 0L
        val deviceUsage = repository.getTodayTotalUsageMs().getOrThrow()

        assertEquals(0L, usage)
        assertEquals(0L, deviceUsage)
    }

    /**
     * Invariant 6: No usage is attributed after its actual end.
     */
    @Test
    fun invariant6_noUsageAttributedAfterActualEnd() {
        val now = midnight + 7_200_000L // 2 hours
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        val start = midnight + 600_000L
        val end = midnight + 1_200_000L // 10 mins (ended 1 hour ago)
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", start, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", end, UsageEvents.Event.ACTIVITY_PAUSED))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()[confirmtktPkg] ?: 0L
        assertEquals(600_000L, usage)
    }

    /**
     * Invariant 7: Today's usage cannot contain yesterday's completed session duration.
     */
    @Test
    fun invariant7_yesterdayCompletedSession_strictlyExcludedFromToday() {
        val now = midnight + 3_600_000L // 1:00 AM today
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // Session completed yesterday from 10:00 PM to 11:00 PM (1 hour)
        val yStart = midnight - (2 * 3600) * 1000L
        val yEnd = midnight - (1 * 3600) * 1000L
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", yStart, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", yEnd, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", yEnd + 50L, UsageEvents.Event.ACTIVITY_STOPPED))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()[confirmtktPkg] ?: 0L
        val deviceUsage = repository.getTodayTotalUsageMs().getOrThrow()

        assertEquals(0L, usage)
        assertEquals(0L, deviceUsage)
    }

    /**
     * Invariant 8: An app open across midnight contributes only the correct portion to each day.
     */
    @Test
    fun invariant8_appOpenAcrossMidnight_contributesOnlyPostMidnightPortion() {
        val now = midnight + 1_800_000L // 12:30 AM today
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // Opened at 11:40 PM yesterday (20m before midnight)
        // Closed at 12:20 AM today (20m after midnight)
        val start = midnight - 1_200_000L
        val end = midnight + 1_200_000L
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", start, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", end, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", end + 50L, UsageEvents.Event.ACTIVITY_STOPPED))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()[confirmtktPkg] ?: 0L
        val deviceUsage = repository.getTodayTotalUsageMs().getOrThrow()

        // Only the 20 minutes from 00:00:00 to 12:20:00 belong to today!
        assertEquals(1_200_000L, usage)
        assertEquals(1_200_000L, deviceUsage)
    }

    // --- EDGE CASE TESTS ---

    /**
     * Multiple internal activities within the same app:
     * Activity A resumes, then Activity B resumes, Activity A pauses and stops,
     * then Activity B pauses and stops.
     * Must calculate continuous single session from start of A to end of B.
     */
    @Test
    fun edgeCase_multipleInternalActivities_calculatesContinuousSession() {
        val now = midnight + 3_600_000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        val t1 = midnight + 100_000L // Activity A resumes
        val t2 = midnight + 200_000L // Activity B resumes
        val t3 = midnight + 250_000L // Activity A pauses/stops
        val t4 = midnight + 400_000L // Activity B pauses/stops

        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "ActivityA", t1, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "ActivityB", t2, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "ActivityA", t3, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "ActivityA", t3 + 10L, UsageEvents.Event.ACTIVITY_STOPPED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "ActivityB", t4, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "ActivityB", t4 + 10L, UsageEvents.Event.ACTIVITY_STOPPED))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()[confirmtktPkg] ?: 0L
        val deviceUsage = repository.getTodayTotalUsageMs().getOrThrow()

        // From t1 (100_000) to t4 (400_000) = 300_000 ms (5 mins)
        assertEquals(300_000L, usage)
        assertEquals(300_000L, deviceUsage)
    }

    /**
     * Screen off and screen on:
     * App is active, screen turns off for 10 minutes, screen turns on for 5 minutes, app pauses.
     * Only interactive time is counted (5m before screen off + 5m after screen on = 10m).
     */
    @Test
    fun edgeCase_screenOffAndOn_excludesScreenOffTime() {
        val now = midnight + 3_600_000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        val t1 = midnight + 100_000L // App resumes
        val t2 = midnight + 400_000L // Screen turns off (used 300_000 ms)
        val t3 = midnight + 1_000_000L // Screen turns on (was off for 600_000 ms)
        val t4 = midnight + 1_300_000L // App pauses (used 300_000 ms)

        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", t1, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(null, null, t2, UsageEvents.Event.SCREEN_NON_INTERACTIVE))
        shadowUsageStats.addEvent(createEvent(null, null, t3, UsageEvents.Event.SCREEN_INTERACTIVE))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", t4, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", t4 + 20L, UsageEvents.Event.ACTIVITY_STOPPED))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()[confirmtktPkg] ?: 0L
        val deviceUsage = repository.getTodayTotalUsageMs().getOrThrow()

        // (400_000 - 100_000) + (1_300_000 - 1_000_000) = 300_000 + 300_000 = 600_000 ms (10 mins)
        assertEquals(600_000L, usage)
        assertEquals(600_000L, deviceUsage)
    }

    /**
     * Device lock (KEYGUARD_SHOWN) and unlock (KEYGUARD_HIDDEN):
     * Keyguard events pause and resume sessions accurately.
     */
    @Test
    fun edgeCase_keyguardLockAndUnlock_pausesAndResumesAccurately() {
        val now = midnight + 3_600_000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        val t1 = midnight + 100_000L
        val t2 = midnight + 300_000L // Lock (used 200_000 ms)
        val t3 = midnight + 800_000L // Unlock
        val t4 = midnight + 1_000_000L // App pauses (used 200_000 ms)

        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", t1, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(null, null, t2, 17 /* KEYGUARD_SHOWN */))
        shadowUsageStats.addEvent(createEvent(null, null, t3, 18 /* KEYGUARD_HIDDEN */))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", t4, UsageEvents.Event.ACTIVITY_PAUSED))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg)).getOrThrow()[confirmtktPkg] ?: 0L
        assertEquals(400_000L, usage)
    }

    /**
     * Switching from Monitored App A to Monitored App B closes App A immediately.
     */
    @Test
    fun edgeCase_switchBetweenMonitoredApps_closesFirstAppSession() {
        val now = midnight + 3_600_000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        val t1 = midnight + 100_000L // Confirmtkt resumes
        val t2 = midnight + 400_000L // Instagram resumes (Confirmtkt pause delayed)
        val t3 = midnight + 450_000L // Confirmtkt pause event arrives late
        val t4 = midnight + 700_000L // Instagram pauses

        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", t1, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(instagramPkg, "Feed", t2, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", t3, UsageEvents.Event.ACTIVITY_PAUSED))
        shadowUsageStats.addEvent(createEvent(confirmtktPkg, "Main", t3 + 10L, UsageEvents.Event.ACTIVITY_STOPPED))
        shadowUsageStats.addEvent(createEvent(instagramPkg, "Feed", t4, UsageEvents.Event.ACTIVITY_PAUSED))

        val usage = repository.getTodayUsageForPackages(setOf(confirmtktPkg, instagramPkg)).getOrThrow()

        // Confirmtkt should be t2 - t1 = 300_000 ms (not extended to t3)
        assertEquals(300_000L, usage[confirmtktPkg])
        // Instagram should be t4 - t2 = 300_000 ms
        assertEquals(300_000L, usage[instagramPkg])
    }

    /**
     * Seven-day chart records verification:
     * Verify that 7-day daily records return reasonable, accurate data,
     * and do not display 23h 59m across all days.
     */
    @Test
    fun edgeCase_sevenDayDailyUsage_doesNotDefaultTo23h59m() {
        val now = midnight + 3_600_000L
        val clock = AuditTestClock(currentTime = now, startOfDay = midnight)
        val repository = AndroidUsageStatsRepository(context, clock)

        // Today has 10 minutes of usage
        val t1 = midnight + 100_000L
        val t2 = midnight + 700_000L
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", t1, UsageEvents.Event.ACTIVITY_RESUMED))
        shadowUsageStats.addEvent(createEvent(youtubePkg, "Main", t2, UsageEvents.Event.ACTIVITY_PAUSED))

        val sevenDayRecords = repository.getSevenDayDailyUsage().getOrThrow()
        assertEquals(7, sevenDayRecords.size)

        val todayRecord = sevenDayRecords.first { it.isCurrentDay }
        assertEquals(600_000L, todayRecord.totalScreenTimeMs)

        // Past days with no events must NOT be 23h 59m!
        for (record in sevenDayRecords.filter { !it.isCurrentDay }) {
            assertTrue("Past day total must not be 23h 59m: ${record.totalScreenTimeMs}", record.totalScreenTimeMs < 86_340_000L)
        }
    }

    private fun createEvent(
        packageName: String?,
        className: String?,
        timeStamp: Long,
        eventType: Int
    ): UsageEvents.Event {
        val builder = ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setTimeStamp(timeStamp)
            .setEventType(eventType)
        if (packageName != null) builder.setPackage(packageName)
        if (className != null) builder.setClass(className)
        return builder.build()
    }
}

