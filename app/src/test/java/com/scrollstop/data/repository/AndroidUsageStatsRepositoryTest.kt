package com.scrollstop.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.time.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidUsageStatsRepositoryTest {

    private lateinit var context: Context
    private lateinit var appOpsManager: AppOpsManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var shadowUsageStats: ShadowUsageStatsManager
    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var repository: AndroidUsageStatsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        shadowUsageStats = Shadows.shadowOf(usageStatsManager)

        fakeTimeProvider = FakeTimeProvider(
            currentTime = 1600000000000L,
            startOfDay = 1600000000000L - (10 * 3600 * 1000L) // 10 hours ago
        )
        repository = AndroidUsageStatsRepository(context, fakeTimeProvider)
    }

    @Test
    fun `hasUsagePermission returns false when app ops permission mode is ignored`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        val permissionGranted = repository.hasUsagePermission()
        assertFalse(permissionGranted)
    }

    @Test
    fun `hasUsagePermission returns true when app ops permission mode is allowed`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val permissionGranted = repository.hasUsagePermission()
        assertTrue(permissionGranted)
    }

    @Test
    fun `getTodayUsageForPackages returns failure when usage permission not granted`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        val result = repository.getTodayUsageForPackages(setOf("com.instagram.android"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `getTodayUsageForPackages returns calculated live usage when app has active ongoing foreground session`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val pkg = "in.amazon.mShop.android.shopping"
        val sessionStart = fakeTimeProvider.currentTime - 120_000L // 2 minutes ago

        val event = ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setPackage(pkg)
            .setClass("MainActivity")
            .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
            .setTimeStamp(sessionStart)
            .build()
        shadowUsageStats.addEvent(event)

        val result = repository.getTodayUsageForPackages(setOf(pkg))
        assertTrue(result.isSuccess)
        val usageMap = result.getOrThrow()
        assertEquals(120_000L, usageMap[pkg])
    }

    @Test
    fun `getTodayUsageForPackages correctly calculates usage across multiple activities without losing duration`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val pkg = "in.amazon.mShop.android.shopping"
        val t0 = fakeTimeProvider.currentTime - 180_000L // 3 minutes ago
        val t1 = fakeTimeProvider.currentTime - 120_000L // Activity B resumes
        val t2 = fakeTimeProvider.currentTime - 110_000L // Activity A pauses

        // Activity A resumes at t0
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("HomeActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t0)
                .build()
        )
        // Activity B resumes at t1
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("ProductDetailsActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t1)
                .build()
        )
        // Activity A pauses at t2
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("HomeActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(t2)
                .build()
        )

        // Session continues with ProductDetailsActivity until currentTime (180s total)
        val result = repository.getTodayUsageForPackages(setOf(pkg))
        assertTrue(result.isSuccess)
        val usageMap = result.getOrThrow()
        assertEquals(180_000L, usageMap[pkg])
    }

    @Test
    fun `getTodayUsageForPackages returns completed duration when app is backgrounded`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val pkg = "in.amazon.mShop.android.shopping"
        val sessionStart = fakeTimeProvider.currentTime - 200_000L
        val sessionEnd = fakeTimeProvider.currentTime - 100_000L // Session ended 100s ago

        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("MainActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(sessionStart)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("MainActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(sessionEnd)
                .build()
        )

        val result = repository.getTodayUsageForPackages(setOf(pkg))
        assertTrue(result.isSuccess)
        val usageMap = result.getOrThrow()
        assertEquals(100_000L, usageMap[pkg])
    }

    @Test
    fun `getTodayUsageForPackages correctly preserves ongoing session when older activity emits ACTIVITY_STOPPED after new activity resumed`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val pkg = "in.amazon.mShop.android.shopping"
        val t0 = fakeTimeProvider.currentTime - 180_000L // 3 minutes ago
        val t1 = fakeTimeProvider.currentTime - 120_000L // SearchActivity resumes
        val t2 = fakeTimeProvider.currentTime - 110_000L // HomeActivity pauses
        val t3 = fakeTimeProvider.currentTime - 90_000L  // HomeActivity stops while SearchActivity continues

        // HomeActivity resumes at t0
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("HomeActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t0)
                .build()
        )
        // SearchActivity resumes at t1
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("SearchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t1)
                .build()
        )
        // HomeActivity pauses at t2
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("HomeActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(t2)
                .build()
        )
        // HomeActivity stops at t3
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("HomeActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_STOPPED)
                .setTimeStamp(t3)
                .build()
        )

        // Session must continue with SearchActivity until currentTime (180s total)
        val result = repository.getTodayUsageForPackages(setOf(pkg))
        assertTrue(result.isSuccess)
        val usageMap = result.getOrThrow()
        assertEquals(180_000L, usageMap[pkg])
    }

    @Test
    fun `getTodayTotalUsageMs returns failure when permission not granted`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        val result = repository.getTodayTotalUsageMs()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `getTodayTotalUsageMs returns genuine zero usage when no activity has occurred today`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val result = repository.getTodayTotalUsageMs()
        assertTrue(result.isSuccess)
        assertEquals(0L, result.getOrThrow())
    }

    @Test
    fun `getTodayTotalUsageMs calculates accurate non-overlapping screen time across multiple apps`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val pkgA = "com.google.android.youtube"
        val pkgB = "com.instagram.android"

        val t0 = fakeTimeProvider.startOfDay + 600_000L  // 10 min after midnight
        val t1 = fakeTimeProvider.startOfDay + 1800_000L // 30 min after midnight (20 min session for pkgA)
        val t2 = fakeTimeProvider.startOfDay + 2400_000L // 40 min after midnight
        val t3 = fakeTimeProvider.startOfDay + 4200_000L // 70 min after midnight (30 min session for pkgB)

        // App A: 20 min session
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkgA)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t0)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkgA)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(t1)
                .build()
        )

        // App B: 30 min session
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkgB)
                .setClass("FeedActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t2)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkgB)
                .setClass("FeedActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(t3)
                .build()
        )

        val result = repository.getTodayTotalUsageMs()
        assertTrue(result.isSuccess)
        // 20 min + 30 min = 50 min = 3,000,000 ms
        assertEquals(3_000_000L, result.getOrThrow())
    }

    @Test
    fun `getTodayTotalUsageMs excludes keyboards, system apps, and launchers from inflating total usage`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val userApp = "in.amazon.mShop.android.shopping"
        val keyboard = "com.google.android.inputmethod.latin"
        val systemUi = "com.android.systemui"
        val launcher = "com.google.android.apps.nexuslauncher"
        val androidOs = "android"

        val t0 = fakeTimeProvider.startOfDay + 600_000L  // 10m
        val t1 = fakeTimeProvider.startOfDay + 1200_000L // 20m (user typing with keyboard)
        val t2 = fakeTimeProvider.startOfDay + 1500_000L // 25m (keyboard closes)
        val t3 = fakeTimeProvider.startOfDay + 2400_000L // 40m (user app pauses)

        // User app active from t0 to t3 (30 min total = 1,800,000 ms)
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(userApp)
                .setClass("MainActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t0)
                .build()
        )

        // Keyboard open while typing in user app from t1 to t2
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(keyboard)
                .setClass("InputMethod")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t1)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(keyboard)
                .setClass("InputMethod")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(t2)
                .build()
        )

        // User app pauses at t3
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(userApp)
                .setClass("MainActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(t3)
                .build()
        )

        // System UI and Launcher events in background/home screen
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(launcher)
                .setClass("NexusLauncherActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t3 + 60_000L)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(systemUi)
                .setClass("NotificationShade")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t3 + 120_000L)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(androidOs)
                .setClass("Framework")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t3 + 180_000L)
                .build()
        )

        val result = repository.getTodayTotalUsageMs()
        assertTrue(result.isSuccess)
        // Must strictly equal userApp duration (30 min = 1,800,000 ms), NOT inflated by keyboard, launcher, or system packages
        assertEquals(1_800_000L, result.getOrThrow())
    }

    @Test
    fun `getTodayTotalUsageMs pauses session on SCREEN_NON_INTERACTIVE`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val pkg = "com.google.android.youtube"
        val t0 = fakeTimeProvider.startOfDay + 600_000L  // 10m
        val t1 = fakeTimeProvider.startOfDay + 1500_000L // 25m (screen turns off)
        val t2 = fakeTimeProvider.startOfDay + 2400_000L // 40m (screen turns on)
        val t3 = fakeTimeProvider.startOfDay + 3000_000L // 50m (session pauses)

        // User starts watching
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t0)
                .build()
        )

        // Screen turns off at t1 (15m elapsed)
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setEventType(UsageEvents.Event.SCREEN_NON_INTERACTIVE)
                .setTimeStamp(t1)
                .build()
        )

        // Screen turns back on and app resumes at t2
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t2)
                .build()
        )

        // Session pauses at t3 (10m elapsed)
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(t3)
                .build()
        )

        val result = repository.getTodayTotalUsageMs()
        assertTrue(result.isSuccess)
        // 15 min + 10 min = 25 min = 1,500,000 ms (screen-off duration between t1 and t2 is excluded)
        assertEquals(1_500_000L, result.getOrThrow())
    }

    @Test
    fun `getTodayTotalUsageMs respects local day boundary`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val pkg = "com.google.android.youtube"
        val yesterdayStart = fakeTimeProvider.startOfDay - 3600_000L // 1 hour before midnight
        val yesterdayEnd = fakeTimeProvider.startOfDay - 1800_000L   // 30 min before midnight

        val todayStart = fakeTimeProvider.startOfDay + 600_000L      // 10 min after midnight
        val todayEnd = fakeTimeProvider.startOfDay + 2400_000L       // 40 min after midnight

        // Yesterday event (must be excluded from today's query)
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(yesterdayStart)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(yesterdayEnd)
                .build()
        )

        // Today event: 30 minutes
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(todayStart)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(todayEnd)
                .build()
        )

        val result = repository.getTodayTotalUsageMs()
        assertTrue(result.isSuccess)
        // Only today's 30 minutes = 1,800,000 ms
        assertEquals(1_800_000L, result.getOrThrow())
    }

    @Test
    fun `getSevenDayDailyUsage returns 7 days matching calendar dates`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val result = repository.getSevenDayDailyUsage()
        assertTrue(result.isSuccess)
        val records = result.getOrThrow()
        assertEquals(7, records.size)

        // Last record is always today
        val todayRecord = records.last()
        assertTrue(todayRecord.isCurrentDay)
        assertEquals("Today", todayRecord.dayLabel)
        assertTrue(todayRecord.hasRecord)

        // Previous 6 records are ordered chronologically
        for (i in 0 until 6) {
            assertFalse(records[i].isCurrentDay)
            assertTrue(records[i].dateEpochMillis < records[i + 1].dateEpochMillis)
        }
    }

    @Test
    fun `getSevenDayDailyUsage distinguishes genuine zero usage from missing data`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val result = repository.getSevenDayDailyUsage()
        assertTrue(result.isSuccess)
        val records = result.getOrThrow()

        // Today has an active session / running app -> hasRecord is true, screen time 0 ms (genuine zero usage)
        val today = records.last()
        assertTrue(today.hasRecord)
        assertEquals(0L, today.totalScreenTimeMs)
        assertEquals("0m", today.formattedDuration)

        // Past days with no OS records or events are marked hasRecord false ("No data")
        for (i in 0 until 6) {
            val pastDay = records[i]
            assertFalse(pastDay.hasRecord)
            assertEquals("No data", pastDay.formattedDuration)
        }
    }

    @Test
    fun `getSevenDayDailyUsage calculates accurate usage for past days with events`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val pkg = "com.google.android.youtube"
        // Add events for yesterday (1 day ago)
        val yesterdayMidnight = fakeTimeProvider.startOfDay - 86_400_000L
        val t0 = yesterdayMidnight + 3600_000L // 1:00 AM yesterday
        val t1 = yesterdayMidnight + 7200_000L // 2:00 AM yesterday (1 hour = 3,600,000 ms)

        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .setTimeStamp(t0)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .setTimeStamp(t1)
                .build()
        )

        val result = repository.getSevenDayDailyUsage()
        assertTrue(result.isSuccess)
        val records = result.getOrThrow()

        // Index 5 is yesterday (1 day before today at index 6)
        val yesterdayRecord = records[5]
        assertTrue(yesterdayRecord.hasRecord)
        assertEquals(3_600_000L, yesterdayRecord.totalScreenTimeMs)
        assertEquals("1h 0m", yesterdayRecord.formattedDuration)

        // Earlier days without events or OS buckets have hasRecord false
        assertFalse(records[0].hasRecord)
        assertFalse(records[1].hasRecord)
        assertFalse(records[2].hasRecord)
        assertFalse(records[3].hasRecord)
        assertFalse(records[4].hasRecord)
    }

    @Test
    fun `getSevenDayDailyUsage returns failure when permission not granted`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        val result = repository.getSevenDayDailyUsage()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    private class FakeTimeProvider(
        var currentTime: Long,
        var startOfDay: Long,
        var nextMidnight: Long = startOfDay + 86_400_000L
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        override fun getStartOfDayMillis(): Long = startOfDay
        override fun getNextMidnightMillis(): Long = nextMidnight
    }
}
