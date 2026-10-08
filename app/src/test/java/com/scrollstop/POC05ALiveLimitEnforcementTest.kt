package com.scrollstop

import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.domain.detection.LiveLimitEnforcementEngine
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class POC05ALiveLimitEnforcementTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var selectedAppsRepository: InMemorySelectedAppsRepository
    private lateinit var dailyLimitRepository: InMemoryDailyLimitRepository
    private lateinit var fakeUsageStatsRepository: FakeUsageStatsRepository
    private lateinit var triggerRepository: InMemoryEnforcementTriggerRepository
    private lateinit var usageRuleEngine: UsageRuleEngine
    private lateinit var liveEngine: LiveLimitEnforcementEngine

    private val emittedTriggers = mutableListOf<EnforcementTrigger>()

    @Before
    fun setUp() {
        emittedTriggers.clear()
        selectedAppsRepository = InMemorySelectedAppsRepository()
        dailyLimitRepository = InMemoryDailyLimitRepository(defaultLimitMs = 120_000L) // 2 minutes (120s)
        fakeUsageStatsRepository = FakeUsageStatsRepository()
        triggerRepository = InMemoryEnforcementTriggerRepository()
        usageRuleEngine = UsageRuleEngine()

        liveEngine = LiveLimitEnforcementEngine(
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            usageStatsRepository = fakeUsageStatsRepository,
            usageRuleEngine = usageRuleEngine,
            enforcementTriggerRepository = triggerRepository,
            selfPackageName = "com.scrollstop",
            checkIntervalMs = 5_000L, // 5 seconds
            scope = testScope,
            dispatcher = testDispatcher,
            timeProvider = { 100_000L },
            onTriggerEmitted = { trigger ->
                emittedTriggers.add(trigger)
            }
        )
    }

    @After
    fun tearDown() {
        liveEngine.stopMonitoring()
    }

    @Test
    fun `test 1 - selected app below limit produces no trigger`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 60_000L // 1 minute < 2 minute limit

        liveEngine.onForegroundPackageChanged(pkg)

        // No immediate trigger
        assertNull("Below limit must not produce trigger on entry", triggerRepository.latestTrigger.value)
        assertTrue("Live monitoring must be active for under-limit selected app", liveEngine.isMonitoring)
        assertEquals(pkg, liveEngine.currentMonitoredPackage)
        assertTrue("No triggers emitted", emittedTriggers.isEmpty())

        liveEngine.stopMonitoring()
    }

    @Test
    fun `test 2 - selected app crosses limit during live continuous monitoring generates trigger`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 60_000L // 1 minute (below 2 minute limit)

        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue("Monitoring started", liveEngine.isMonitoring)

        // Advance 5 seconds, usage still 60s
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertNull("No trigger while still under limit", triggerRepository.latestTrigger.value)
        assertTrue("Still monitoring", liveEngine.isMonitoring)

        // Usage accumulates in UsageStatsManager to 125s (> 120s limit)
        fakeUsageStatsRepository.usageMap[pkg] = 125_000L

        // Next periodic check occurs
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        val trigger = triggerRepository.latestTrigger.value
        assertNotNull("Trigger must be emitted when usage crosses limit during monitoring", trigger)
        assertEquals(pkg, trigger?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, trigger?.reason)
        assertEquals(1, emittedTriggers.size)
        assertEquals(pkg, emittedTriggers[0].packageName)

        // Monitoring must stop immediately after breach
        assertFalse("Monitoring must stop after limit is breached", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)
    }

    @Test
    fun `test 3 - selected app remains below limit does not trigger`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 30_000L // 30 seconds

        liveEngine.onForegroundPackageChanged(pkg)

        // Advance through multiple polling cycles (30 seconds)
        for (i in 1..6) {
            fakeUsageStatsRepository.usageMap[pkg] = 30_000L + (i * 5_000L) // up to 60s
            testScheduler.advanceTimeBy(5_000L)
            testScheduler.runCurrent()
            assertNull("No trigger at ${30 + i * 5}s", triggerRepository.latestTrigger.value)
            assertTrue("Still monitoring", liveEngine.isMonitoring)
        }

        assertTrue("Emitted triggers must remain empty", emittedTriggers.isEmpty())
        liveEngine.stopMonitoring()
    }

    @Test
    fun `test 4 - already-over-limit selected app immediately blocks preserving POC-05 behavior`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 180_000L // 3 minutes > 2 minute limit

        liveEngine.onForegroundPackageChanged(pkg)

        val trigger = triggerRepository.latestTrigger.value
        assertNotNull("Already-over-limit app must immediately produce EnforcementTrigger", trigger)
        assertEquals(pkg, trigger?.packageName)
        assertEquals(1, emittedTriggers.size)
        assertFalse("Monitoring loop should not start for already-over-limit app", liveEngine.isMonitoring)
    }

    @Test
    fun `test 5 - unselected foreground app does not start monitoring`() = testScope.runTest {
        val pkg = "com.android.chrome" // Not in selected set
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap[pkg] = 30_000L

        liveEngine.onForegroundPackageChanged(pkg)

        assertFalse("Unselected app must not start monitoring", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)
        assertNull(triggerRepository.latestTrigger.value)
        assertTrue(emittedTriggers.isEmpty())
    }

    @Test
    fun `test 6 - selected app leaves foreground stops monitoring immediately`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 60_000L

        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue("Monitoring started", liveEngine.isMonitoring)

        // User switches to Chrome (unselected)
        liveEngine.onForegroundPackageChanged("com.android.chrome")

        assertFalse("Monitoring must stop when user leaves selected app", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)

        // Advance time: no background polling or trigger should occur
        fakeUsageStatsRepository.usageMap[pkg] = 200_000L // YouTube usage changed while in background
        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()
        assertNull("No trigger while selected app is not in foreground", triggerRepository.latestTrigger.value)
    }

    @Test
    fun `test 7 - selected app returns foreground resumes monitoring correctly`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 60_000L

        // Open YouTube
        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue(liveEngine.isMonitoring)

        // Switch away
        liveEngine.onForegroundPackageChanged("com.android.chrome")
        assertFalse(liveEngine.isMonitoring)

        // Switch back to YouTube
        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue("Monitoring must resume when returning to selected app", liveEngine.isMonitoring)
        assertEquals(pkg, liveEngine.currentMonitoredPackage)

        // Cross limit
        fakeUsageStatsRepository.usageMap[pkg] = 130_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertNotNull("Trigger emitted upon limit breach after returning", triggerRepository.latestTrigger.value)
        assertEquals(pkg, triggerRepository.latestTrigger.value?.packageName)
        assertFalse(liveEngine.isMonitoring)
    }

    @Test
    fun `test 8 - limit reached emits only one trigger and does not spam`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 100_000L

        liveEngine.onForegroundPackageChanged(pkg)
        fakeUsageStatsRepository.usageMap[pkg] = 130_000L

        // Trigger limit
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals("Exactly one trigger must be emitted", 1, emittedTriggers.size)

        // Advance more time: monitoring was stopped, so no additional triggers
        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()
        assertEquals("Must not emit duplicate triggers", 1, emittedTriggers.size)
    }

    @Test
    fun `test 9 - repeated foreground events for same app do not create duplicate monitors`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 50_000L

        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue(liveEngine.isMonitoring)

        // Multiple window state events inside YouTube (tab switches, video opens)
        liveEngine.onForegroundPackageChanged(pkg)
        liveEngine.onForegroundPackageChanged(pkg)
        liveEngine.onForegroundPackageChanged(pkg)

        assertTrue(liveEngine.isMonitoring)
        assertEquals(pkg, liveEngine.currentMonitoredPackage)

        // Cross limit
        fakeUsageStatsRepository.usageMap[pkg] = 150_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals("Only one trigger emitted despite repeated window events", 1, emittedTriggers.size)
    }

    @Test
    fun `test 10 - service interruption cancels monitoring immediately`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 50_000L

        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue(liveEngine.isMonitoring)

        // Service interrupted
        liveEngine.stopMonitoring()
        assertFalse("Monitoring must be cancelled on service interruption", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)

        // Advance time: no queries or triggers
        fakeUsageStatsRepository.usageMap[pkg] = 200_000L
        testScheduler.advanceTimeBy(10_000L)
        testScheduler.runCurrent()
        assertNull(triggerRepository.latestTrigger.value)
    }

    @Test
    fun `test 11 - UsageStatsManager remains authoritative source of truth`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 80_000L

        liveEngine.onForegroundPackageChanged(pkg)

        // Engine runs 2 checks, but fakeUsageStatsRepository returns 80_000L
        testScheduler.advanceTimeBy(10_000L)
        testScheduler.runCurrent()
        assertNull("Usage evaluated strictly from UsageStatsManager", triggerRepository.latestTrigger.value)

        // Only when repository reports >= limit does engine trigger
        fakeUsageStatsRepository.usageMap[pkg] = 120_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertNotNull(triggerRepository.latestTrigger.value)
    }

    @Test
    fun `test 12 - no independent usage counter is maintained`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 10_000L

        liveEngine.onForegroundPackageChanged(pkg)

        // Advance 15 seconds of real time. If engine had its own counter, it might increment.
        // But UsageStatsManager still reports 10_000L (e.g. video was paused)
        testScheduler.advanceTimeBy(15_000L)
        testScheduler.runCurrent()
        assertNull(triggerRepository.latestTrigger.value)
        assertEquals(0, emittedTriggers.size)

        liveEngine.stopMonitoring()
    }

    @Test
    fun `test 13 - multiple selected apps handled independently without state bleed`() = testScope.runTest {
        val pkg1 = "com.google.android.youtube"
        val pkg2 = "com.instagram.android"
        selectedAppsRepository.setSelectedPackages(setOf(pkg1, pkg2))
        dailyLimitRepository.setLimitForPackage(pkg1, 120_000L) // 2 min
        dailyLimitRepository.setLimitForPackage(pkg2, 300_000L) // 5 min

        fakeUsageStatsRepository.usageMap[pkg1] = 60_000L // 1 min (under)
        fakeUsageStatsRepository.usageMap[pkg2] = 200_000L // 3 min 20s (under)

        // User opens YouTube
        liveEngine.onForegroundPackageChanged(pkg1)
        assertEquals(pkg1, liveEngine.currentMonitoredPackage)

        // User switches to Instagram
        liveEngine.onForegroundPackageChanged(pkg2)
        assertEquals(pkg2, liveEngine.currentMonitoredPackage)

        // Advance: Instagram usage crosses 5 min
        fakeUsageStatsRepository.usageMap[pkg2] = 310_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals(1, emittedTriggers.size)
        assertEquals(pkg2, emittedTriggers[0].packageName)
    }

    @Test
    fun `test 14 - monitoring does not continue after blocking`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 125_000L

        liveEngine.onForegroundPackageChanged(pkg)
        assertFalse("Monitoring must be inactive after immediate blocking", liveEngine.isMonitoring)

        testScheduler.advanceTimeBy(30_000L)
        testScheduler.runCurrent()
        assertEquals("No new triggers emitted after blocking", 1, emittedTriggers.size)
    }

    @Test
    fun `test 15 - daily reset boundary handled safely`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        dailyLimitRepository.setLimitForPackage(pkg, 120_000L)
        fakeUsageStatsRepository.usageMap[pkg] = 115_000L // almost at limit

        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue(liveEngine.isMonitoring)

        // Midnight occurs: UsageStatsManager resets today's usage to 0L
        fakeUsageStatsRepository.usageMap[pkg] = 0L

        // Next poll
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertNull("Reset to 0 must evaluate WITHIN_LIMIT / NOT_STARTED without blocking", triggerRepository.latestTrigger.value)
        assertTrue(liveEngine.isMonitoring)

        liveEngine.stopMonitoring()
    }

    @Test
    fun `test 16 - rapid foreground app switching is thread-safe and retains only latest app`() = testScope.runTest {
        val pkg1 = "com.google.android.youtube"
        val pkg2 = "com.instagram.android"
        val unselected = "com.android.chrome"
        selectedAppsRepository.setSelectedPackages(setOf(pkg1, pkg2))

        fakeUsageStatsRepository.usageMap[pkg1] = 50_000L
        fakeUsageStatsRepository.usageMap[pkg2] = 50_000L
        fakeUsageStatsRepository.usageMap[unselected] = 50_000L

        // Rapid switches
        liveEngine.onForegroundPackageChanged(pkg1)
        liveEngine.onForegroundPackageChanged(unselected)
        liveEngine.onForegroundPackageChanged(pkg2)
        liveEngine.onForegroundPackageChanged(pkg1)

        assertEquals("Only the latest selected app should be monitored", pkg1, liveEngine.currentMonitoredPackage)
        assertTrue(liveEngine.isMonitoring)

        // Rapid switch to unselected
        liveEngine.onForegroundPackageChanged(unselected)
        assertFalse("Monitoring must stop on unselected app", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)
    }
}
