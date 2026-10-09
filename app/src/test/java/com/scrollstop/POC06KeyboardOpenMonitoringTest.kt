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
 * Focused test suite verifying live limit monitoring when keyboard is open (POC-06 Follow-up).
 *
 * Verifies all 10 required scenarios:
 * 1. Amazon monitoring starts normally.
 * 2. Opening Gboard does not stop Amazon monitoring.
 * 3. Repeated keyboard-related events do not reset the active restricted package.
 * 4. Monitoring continues while the keyboard remains open.
 * 5. Usage crossing the limit while the keyboard is open triggers blocking.
 * 6. Dismissing the keyboard resumes normal Amazon event processing.
 * 7. Leaving Amazon for a genuinely different app transitions monitoring correctly.
 * 8. Switching between selected restricted apps monitors the correct package.
 * 9. Unselected apps remain usable.
 * 10. Existing YouTube live enforcement and repeated-launch tests remain passing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class POC06KeyboardOpenMonitoringTest {

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
    private val gboardPkg = "com.google.android.inputmethod.latin"
    private val samsungKeyboardPkg = "com.samsung.android.honeyboard"
    private val launcherPkg = "com.google.android.apps.nexuslauncher"
    private val settingsPkg = "com.android.settings"

    @Before
    fun setUp() {
        emittedTriggers.clear()
        selectedAppsRepository = InMemorySelectedAppsRepository()
        dailyLimitRepository = InMemoryDailyLimitRepository(defaultLimitMs = 120_000L) // 2 minutes (120s)
        fakeUsageStatsRepository = FakeUsageStatsRepository()
        triggerRepository = InMemoryEnforcementTriggerRepository()
        usageRuleEngine = UsageRuleEngine()

        val isTransient: (String) -> Boolean = { pkg ->
            val p = pkg.trim().lowercase()
            p == "android" ||
            p == "com.android.systemui" ||
            p == gboardPkg.lowercase() ||
            p == samsungKeyboardPkg.lowercase() ||
            p.contains(".inputmethod.") ||
            p.contains(".keyboard")
        }

        liveEngine = LiveLimitEnforcementEngine(
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            usageStatsRepository = fakeUsageStatsRepository,
            usageRuleEngine = usageRuleEngine,
            enforcementTriggerRepository = triggerRepository,
            selfPackageName = "com.scrollstop",
            checkIntervalMs = 5_000L,
            scope = testScope,
            dispatcher = testDispatcher,
            timeProvider = { 100_000L },
            isTransientPackage = isTransient,
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
    fun `1 - Amazon monitoring starts normally`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 0L

        liveEngine.onForegroundPackageChanged(amazonPkg)

        assertTrue("Monitoring must be active for Amazon", liveEngine.isMonitoring)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)
        assertTrue("No triggers emitted at 0ms", emittedTriggers.isEmpty())
        liveEngine.stopMonitoring()
    }

    @Test
    fun `2 - opening Gboard does not stop Amazon monitoring`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 10_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        assertTrue(liveEngine.isMonitoring)

        // User taps search field: Gboard window opens
        liveEngine.onForegroundPackageChanged(gboardPkg)

        assertTrue("Monitoring must remain active when Gboard opens", liveEngine.isMonitoring)
        assertEquals("Monitored package must remain Amazon", amazonPkg, liveEngine.currentMonitoredPackage)
        liveEngine.stopMonitoring()
    }

    @Test
    fun `3 - repeated keyboard-related events do not reset the active restricted package`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 20_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)

        // Multiple keystrokes / suggestion updates fire window events from keyboard
        for (i in 1..5) {
            liveEngine.onForegroundPackageChanged(gboardPkg)
            assertTrue("Monitoring must stay active through repeated IME events", liveEngine.isMonitoring)
            assertEquals("Monitored package must not reset or be cleared", amazonPkg, liveEngine.currentMonitoredPackage)
        }
        liveEngine.stopMonitoring()
    }

    @Test
    fun `4 - monitoring continues while the keyboard remains open`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 30_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        liveEngine.onForegroundPackageChanged(gboardPkg)

        // Polling loop executes while keyboard is open
        for (i in 1..4) {
            fakeUsageStatsRepository.usageMap[amazonPkg] = 30_000L + (i * 5_000L)
            testScheduler.advanceTimeBy(5_000L)
            testScheduler.runCurrent()

            assertTrue("Monitoring continues polling during keyboard session", liveEngine.isMonitoring)
            assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)
        }
        liveEngine.stopMonitoring()
    }

    @Test
    fun `5 - usage crossing the limit while the keyboard is open triggers blocking`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 110_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        liveEngine.onForegroundPackageChanged(gboardPkg) // Keyboard open

        // User continues typing in Amazon search and crosses 120s limit
        fakeUsageStatsRepository.usageMap[amazonPkg] = 125_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals("Trigger must be emitted even while keyboard is open", 1, emittedTriggers.size)
        assertEquals(amazonPkg, emittedTriggers[0].packageName)
        assertEquals(LimitState.LIMIT_REACHED, emittedTriggers[0].reason)
        assertFalse("Monitoring must stop after limit breach", liveEngine.isMonitoring)
    }

    @Test
    fun `6 - dismissing the keyboard resumes normal Amazon event processing`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 40_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        liveEngine.onForegroundPackageChanged(gboardPkg) // Keyboard open
        assertTrue(liveEngine.isMonitoring)

        // Keyboard dismissed: Amazon window event received
        liveEngine.onForegroundPackageChanged(amazonPkg)
        assertTrue("Monitoring continues seamlessly after keyboard dismissed", liveEngine.isMonitoring)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)

        // Continues usage and breaches later
        fakeUsageStatsRepository.usageMap[amazonPkg] = 130_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals(1, emittedTriggers.size)
        assertEquals(amazonPkg, emittedTriggers[0].packageName)
    }

    @Test
    fun `7 - leaving Amazon for a genuinely different app transitions monitoring correctly`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 40_000L

        liveEngine.onForegroundPackageChanged(amazonPkg)
        liveEngine.onForegroundPackageChanged(gboardPkg)
        assertTrue(liveEngine.isMonitoring)

        // User presses Home (launcher appears)
        liveEngine.onForegroundPackageChanged(launcherPkg)

        assertFalse("Leaving Amazon for launcher must stop monitoring immediately", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)
    }

    @Test
    fun `8 - switching between selected restricted apps monitors the correct package`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg, youtubePkg))
        fakeUsageStatsRepository.usageMap[amazonPkg] = 30_000L
        fakeUsageStatsRepository.usageMap[youtubePkg] = 20_000L

        // In Amazon with keyboard
        liveEngine.onForegroundPackageChanged(amazonPkg)
        liveEngine.onForegroundPackageChanged(gboardPkg)
        assertEquals(amazonPkg, liveEngine.currentMonitoredPackage)

        // Switch directly to YouTube
        liveEngine.onForegroundPackageChanged(youtubePkg)

        assertEquals("Target package must switch to YouTube", youtubePkg, liveEngine.currentMonitoredPackage)
        assertTrue("Monitoring must be active for YouTube", liveEngine.isMonitoring)
        liveEngine.stopMonitoring()
    }

    @Test
    fun `9 - unselected apps remain usable`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(amazonPkg))

        // Open unselected Settings
        liveEngine.onForegroundPackageChanged(settingsPkg)

        assertFalse("Unselected app must not be monitored", liveEngine.isMonitoring)
        assertNull(liveEngine.currentMonitoredPackage)
        assertTrue(emittedTriggers.isEmpty())
    }

    @Test
    fun `10 - existing YouTube live enforcement and repeated-launch tests remain passing`() = testScope.runTest {
        selectedAppsRepository.setSelectedPackages(setOf(youtubePkg))
        fakeUsageStatsRepository.usageMap[youtubePkg] = 115_000L // 5 seconds before 120s limit

        // Live entry
        liveEngine.onForegroundPackageChanged(youtubePkg)
        assertTrue(liveEngine.isMonitoring)
        assertEquals(youtubePkg, liveEngine.currentMonitoredPackage)

        // Crosses limit live
        fakeUsageStatsRepository.usageMap[youtubePkg] = 122_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals("Live limit breach triggers YouTube block", 1, emittedTriggers.size)
        assertEquals(youtubePkg, emittedTriggers[0].packageName)
        assertFalse(liveEngine.isMonitoring)

        // Repeated launch attempt while over limit
        liveEngine.onForegroundPackageChanged(youtubePkg)
        assertEquals("Repeated launch immediately triggers block", 2, emittedTriggers.size)
        assertEquals(youtubePkg, emittedTriggers[1].packageName)
        assertFalse("Monitoring loop does not stay active for blocked app", liveEngine.isMonitoring)
    }
}
