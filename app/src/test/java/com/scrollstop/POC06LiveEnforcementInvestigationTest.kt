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

/**
 * Regression Test Suite for POC-06 Live Enforcement Failure Investigation.
 *
 * Covers the 10 required regression verification areas:
 * 1. Amazon-like selected app starts at zero usage and crosses its configured limit while continuously foreground.
 * 2. Usage values increase across successive monitoring cycles.
 * 3. Stale or zero usage responses are handled safely without inventing usage or claiming false enforcement.
 * 4. LIMIT_REACHED causes a trigger and blocking launch.
 * 5. Monitoring does not stop prematurely during normal foreground operation (transient system and IME windows).
 * 6. Switching between selected apps monitors the correct package.
 * 7. Under-limit and unselected apps remain usable.
 * 8. Reopening an over-limit app still blocks immediately.
 * 9. Existing YouTube POC-05A behavior remains intact.
 * 10. Service interruption and recovery remain correct.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class POC06LiveEnforcementInvestigationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var selectedAppsRepository: InMemorySelectedAppsRepository
    private lateinit var dailyLimitRepository: InMemoryDailyLimitRepository
    private lateinit var fakeUsageStatsRepository: FakeUsageStatsRepository
    private lateinit var triggerRepository: InMemoryEnforcementTriggerRepository
    private lateinit var usageRuleEngine: UsageRuleEngine
    private lateinit var liveEngine: LiveLimitEnforcementEngine

    private val emittedTriggers = mutableListOf<EnforcementTrigger>()
    private val amazonPkg = "in.amazon.mShop.android.shopping"
    private val youtubePkg = "com.google.android.youtube"
    private val chromePkg = "com.android.chrome"
    private val gboardPkg = "com.google.android.inputmethod.latin"

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
            isTransientPackage = { pkg ->
                pkg.equals("android", ignoreCase = true) ||
                pkg.equals("com.android.systemui", ignoreCase = true) ||
                pkg.equals(gboardPkg, ignoreCase = true)
            },
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
    fun `1 - Amazon-like app starts at zero usage and crosses configured limit while continuously foreground`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 0L // Starts at 0 ms usage

        liveEngine.onForegroundPackageChanged(amazonPkg)

        // Verifies monitoring starts and no premature trigger
        assertTrue("Live monitoring should start for Amazon at 0ms usage", liveEngine.isMonitoring)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)
        assertNull(triggerRepository.latestTrigger.value)
        assertTrue(emittedTriggers.isEmpty())

        // Cycle 1: 30 seconds used (below 120s limit)
        fakeUsageStatsRepository.usageMap[amazonPkg] = 30_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertNull("No trigger while below limit", triggerRepository.latestTrigger.value)
        assertTrue(liveEngine.isMonitoring)

        // Cycle 2: 90 seconds used (below 120s limit)
        fakeUsageStatsRepository.usageMap[amazonPkg] = 90_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertNull("No trigger while below limit", triggerRepository.latestTrigger.value)

        // Cycle 3: crosses limit to 125 seconds (> 120s limit)
        fakeUsageStatsRepository.usageMap[amazonPkg] = 125_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        // Breach detected
        val trigger = triggerRepository.latestTrigger.value
        assertNotNull("Enforcement trigger must be emitted when continuous foreground usage breaches limit", trigger)
        assertEquals(amazonPkg, trigger?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, trigger?.reason)
        assertEquals(1, emittedTriggers.size)
        assertEquals(amazonPkg, emittedTriggers[0].packageName)

        // Monitoring must stop immediately after breach
        assertFalse("Monitoring must stop after limit breach", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)
    }

    @Test
    fun `2 - usage values increase across successive monitoring cycles and record diagnostics`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 10_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        assertTrue(liveEngine.isMonitoring)

        // Successive cycles with increasing usage
        val usages = listOf(20_000L, 40_000L, 60_000L)
        for (usage in usages) {
            fakeUsageStatsRepository.usageMap[amazonPkg] = usage
            testScheduler.advanceTimeBy(5_000L)
            testScheduler.runCurrent()

            val diag = triggerRepository.diagnosticInfo.value
            assertEquals(amazonPkg, diag.lastMonitoringPackage)
            assertEquals(usage, diag.lastMonitoringUsageMs)
            assertEquals(120_000L, diag.lastMonitoringLimitMs)
            assertEquals(LimitState.WITHIN_LIMIT, diag.lastMonitoringRuleState)
            assertFalse(diag.lastMonitoringTriggerEmitted)
            assertTrue(liveEngine.isMonitoring)
        }
        liveEngine.stopMonitoring()
    }

    @Test
    fun `3 - stale or zero usage responses handled safely without inventing usage or false enforcement`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 0L // Usage stays at 0 (stale / no progress)

        liveEngine.onForegroundPackageChanged(amazonPkg)
        assertTrue(liveEngine.isMonitoring)

        // Advance 30 seconds of real time (6 cycles)
        for (i in 1..6) {
            testScheduler.advanceTimeBy(5_000L)
            testScheduler.runCurrent()

            assertNull("Must not invent usage or trigger false enforcement", triggerRepository.latestTrigger.value)
            assertEquals(0, emittedTriggers.size)
            assertTrue("Monitoring continues safely", liveEngine.isMonitoring)
        }
        liveEngine.stopMonitoring()
    }

    @Test
    fun `4 - LIMIT_REACHED causes trigger emission and blocking launch callback`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 110_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        fakeUsageStatsRepository.usageMap[amazonPkg] = 120_000L // Exactly reached limit

        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals("Exactly one trigger must be emitted to callback", 1, emittedTriggers.size)
        assertEquals(amazonPkg, emittedTriggers[0].packageName)
        assertEquals(LimitState.LIMIT_REACHED, emittedTriggers[0].reason)

        val diag = triggerRepository.diagnosticInfo.value
        assertTrue("Trigger emitted recorded in diagnostics", diag.lastMonitoringTriggerEmitted)
        assertTrue("Launch attempted recorded in diagnostics", diag.lastMonitoringLaunchAttempted)
    }

    @Test
    fun `5 - monitoring does not stop prematurely during normal foreground operation (transient system and IME windows)`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 30_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        assertTrue("Monitoring started for Amazon", liveEngine.isMonitoring)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)

        // Transient window 1: Android OS framework (e.g. system dialog or toast)
        liveEngine.onForegroundPackageChanged("android")
        assertTrue("Monitoring must remain active when android framework window fires", liveEngine.isMonitoring)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)

        // Transient window 2: System UI (e.g. volume bar or status bar peek)
        liveEngine.onForegroundPackageChanged("com.android.systemui")
        assertTrue("Monitoring must remain active when systemui window fires", liveEngine.isMonitoring)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)

        // Transient window 3: Soft keyboard / IME (user taps Amazon search bar)
        liveEngine.onForegroundPackageChanged(gboardPkg)
        assertTrue("Monitoring must remain active when keyboard opens inside Amazon", liveEngine.isMonitoring)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)

        // Limit breach while keyboard/transient overlay was active
        fakeUsageStatsRepository.usageMap[amazonPkg] = 130_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertNotNull("Breach must still trigger even after transient windows occurred", triggerRepository.latestTrigger.value)
        assertEquals(amazonPkg, triggerRepository.latestTrigger.value?.packageName)
    }

    @Test
    fun `6 - switching between selected apps monitors the correct package`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg, youtubePkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 30_000L
        fakeUsageStatsRepository.usageMap[youtubePkg] = 50_000L

        // Open Amazon
        liveEngine.onForegroundPackageChanged(amazonPkg)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)

        // Switch to YouTube
        liveEngine.onForegroundPackageChanged(youtubePkg)
        assertEquals("Monitoring must switch target package to YouTube", youtubePkg, liveEngine.currentMonitoredPackage)
        assertTrue(liveEngine.isMonitoring)

        // YouTube crosses limit
        fakeUsageStatsRepository.usageMap[youtubePkg] = 125_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals(1, emittedTriggers.size)
        assertEquals(youtubePkg, emittedTriggers[0].packageName)
    }

    @Test
    fun `7 - under-limit and unselected apps remain usable`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 20_000L
        fakeUsageStatsRepository.usageMap[chromePkg] = 200_000L // Chrome has high usage but is unselected

        // Open unselected Chrome
        liveEngine.onForegroundPackageChanged(chromePkg)
        assertFalse("Unselected app must not start monitoring", liveEngine.isMonitoring)
        assertNull(triggerRepository.latestTrigger.value)

        // Open under-limit Amazon
        liveEngine.onForegroundPackageChanged(amazonPkg)
        assertTrue(liveEngine.isMonitoring)
        assertNull("Under-limit app must not trigger", triggerRepository.latestTrigger.value)
        assertTrue(emittedTriggers.isEmpty())
        liveEngine.stopMonitoring()
    }

    @Test
    fun `8 - reopening an over-limit app still blocks immediately on entry`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 130_000L // 2m 10s > 2m limit

        liveEngine.onForegroundPackageChanged(amazonPkg)

        val trigger = triggerRepository.latestTrigger.value
        assertNotNull("Reopening an over-limit app must immediately block without waiting for poll", trigger)
        assertEquals(amazonPkg, trigger?.packageName)
        assertEquals(1, emittedTriggers.size)
        assertFalse("Monitoring loop should not start for already-over-limit app", liveEngine.isMonitoring)
    }

    @Test
    fun `9 - existing YouTube POC-05A behavior remains intact`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(youtubePkg))
        // Verified POC-05 physical test value (2 hr 32 min)
        fakeUsageStatsRepository.usageMap[youtubePkg] = 9_129_000L

        liveEngine.onForegroundPackageChanged(youtubePkg)

        val trigger = triggerRepository.latestTrigger.value
        assertNotNull("YouTube with existing high usage must block immediately", trigger)
        assertEquals(youtubePkg, trigger?.packageName)
        assertEquals(1, emittedTriggers.size)
        assertEquals(youtubePkg, emittedTriggers[0].packageName)
    }

    @Test
    fun `10 - service interruption and recovery remain correct`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 50_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        assertTrue(liveEngine.isMonitoring)

        // Service interrupted
        liveEngine.stopMonitoring()
        assertFalse("Monitoring must be cancelled immediately on interrupt", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)

        // Time advances during interruption: no triggers
        fakeUsageStatsRepository.usageMap[amazonPkg] = 150_000L
        testScheduler.advanceTimeBy(10_000L)
        testScheduler.runCurrent()
        assertNull(triggerRepository.latestTrigger.value)

        // Service recovers: user resumes Amazon
        liveEngine.onForegroundPackageChanged(amazonPkg)
        // Since Amazon is now over limit (150s), recovery immediately blocks
        assertNotNull("Recovery correctly re-evaluates and blocks", triggerRepository.latestTrigger.value)
        assertEquals(amazonPkg, triggerRepository.latestTrigger.value?.packageName)
    }
}
