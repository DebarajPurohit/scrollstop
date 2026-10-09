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

    private class FakeTimeProvider(
        var currentTime: Long,
        var startOfDay: Long
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        override fun getStartOfDayMillis(): Long = startOfDay
    }
}
