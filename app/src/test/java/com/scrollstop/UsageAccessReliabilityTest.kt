package com.scrollstop

import android.view.accessibility.AccessibilityEvent
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.detection.LiveLimitEnforcementEngine
import com.scrollstop.domain.detection.RestrictedAppDetectionEngine
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
class UsageAccessReliabilityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var selectedAppsRepository: InMemorySelectedAppsRepository
    private lateinit var dailyLimitRepository: InMemoryDailyLimitRepository
    private lateinit var triggerRepository: InMemoryEnforcementTriggerRepository
    private lateinit var usageRuleEngine: UsageRuleEngine

    private class ConfigurableUsageStatsRepository : UsageStatsRepository {
        var hasPermission: Boolean = true
        var simulateFailure: Boolean = false
        var failureException: Throwable = SecurityException("Usage Access permission revoked")
        val usageMap = mutableMapOf<String, Long>()

        override fun hasUsagePermission(): Boolean = hasPermission

        override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> {
            if (!hasPermission) {
                return Result.failure(SecurityException("Usage Access permission missing"))
            }
            if (simulateFailure) {
                return Result.failure(failureException)
            }
            val map = packageNames.associateWith { usageMap[it] ?: 0L }
            return Result.success(map)
        }
    }

    private lateinit var configurableRepo: ConfigurableUsageStatsRepository
    private lateinit var detectionEngine: RestrictedAppDetectionEngine
    private lateinit var liveEngine: LiveLimitEnforcementEngine

    @Before
    fun setUp() {
        selectedAppsRepository = InMemorySelectedAppsRepository()
        dailyLimitRepository = InMemoryDailyLimitRepository(defaultLimitMs = 120_000L) // 2 min
        triggerRepository = InMemoryEnforcementTriggerRepository()
        usageRuleEngine = UsageRuleEngine()
        configurableRepo = ConfigurableUsageStatsRepository()

        detectionEngine = RestrictedAppDetectionEngine(
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            usageStatsRepository = configurableRepo,
            usageRuleEngine = usageRuleEngine,
            selfPackageName = "com.scrollstop"
        )

        liveEngine = LiveLimitEnforcementEngine(
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            usageStatsRepository = configurableRepo,
            usageRuleEngine = usageRuleEngine,
            enforcementTriggerRepository = triggerRepository,
            selfPackageName = "com.scrollstop",
            checkIntervalMs = 5_000L,
            scope = testScope,
            dispatcher = testDispatcher,
            timeProvider = { 100_000L }
        )
    }

    @After
    fun tearDown() {
        liveEngine.stopMonitoring()
    }

    @Test
    fun `detection engine - when usage query fails, does not treat as zero usage or crash`() {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        configurableRepo.usageMap[pkg] = 200_000L // over limit

        // Simulate query failure
        configurableRepo.simulateFailure = true
        configurableRepo.failureException = RuntimeException("Remote service died")

        val trigger = detectionEngine.processEvent(
            packageName = pkg,
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )

        // Must return null, not evaluate false positives or crash
        assertNull("Failed usage query must return null instead of assuming 0 or throwing", trigger)
    }

    @Test
    fun `detection engine - when permission is revoked, returns null and recovers when permission restored`() {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        configurableRepo.usageMap[pkg] = 200_000L // over limit

        // Permission revoked
        configurableRepo.hasPermission = false

        val triggerRevoked = detectionEngine.processEvent(
            packageName = pkg,
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        assertNull("Revoked permission must return null trigger", triggerRevoked)

        // Permission restored
        configurableRepo.hasPermission = true

        val triggerRestored = detectionEngine.processEvent(
            packageName = pkg,
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        assertNotNull("Restored permission must detect limit breach", triggerRestored)
        assertEquals(pkg, triggerRestored?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, triggerRestored?.reason)
    }

    @Test
    fun `live enforcement - initial check failure aborts monitoring safely without false zero assumption`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        configurableRepo.usageMap[pkg] = 60_000L

        // Query failure on initial launch
        configurableRepo.simulateFailure = true

        liveEngine.onForegroundPackageChanged(pkg)

        // Must abort monitoring immediately
        assertFalse("Monitoring must not start when initial usage query fails", liveEngine.isMonitoring)
        assertNull(triggerRepository.latestTrigger.value)
    }

    @Test
    fun `live enforcement - query failure during active polling loop stops monitoring cleanly`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        configurableRepo.usageMap[pkg] = 60_000L

        // Initial check succeeds
        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue(liveEngine.isMonitoring)

        // Mid-monitoring failure (e.g. user revoked permission in settings)
        configurableRepo.hasPermission = false

        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        // Loop stops cleanly
        assertFalse("Monitoring must stop when polling query encounters failure", liveEngine.isMonitoring)
        assertNull("Must not emit false trigger on query failure", triggerRepository.latestTrigger.value)
    }

    @Test
    fun `live enforcement - recovers after permission is restored`() = testScope.runTest {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        configurableRepo.usageMap[pkg] = 60_000L

        // Starts with revoked permission
        configurableRepo.hasPermission = false
        liveEngine.onForegroundPackageChanged(pkg)
        assertFalse(liveEngine.isMonitoring)

        // User restores permission in settings and brings app to foreground
        configurableRepo.hasPermission = true
        liveEngine.onForegroundPackageChanged(pkg)
        assertTrue("Monitoring resumes when permission is restored", liveEngine.isMonitoring)

        // Usage accumulates past limit
        configurableRepo.usageMap[pkg] = 130_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        val trigger = triggerRepository.latestTrigger.value
        assertNotNull("Enforcement occurs once permission is restored and limit exceeded", trigger)
        assertEquals(pkg, trigger?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, trigger?.reason)
    }
}
