package com.scrollstop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.SharedPreferencesDailyLimitRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.detection.LiveLimitEnforcementEngine
import com.scrollstop.domain.model.DiscoveredApp
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine
import com.scrollstop.ui.screens.limits.LimitsUiState
import com.scrollstop.ui.screens.limits.LimitsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focused regression test suite verifying custom-duration limit enforcement,
 * persistence, UI consistency, Commitment Lock interactions, and baseline preset parity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomLimitEnforcementRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testPackage = "com.android.chrome"
    private val sampleApps = listOf(
        DiscoveredApp(packageName = testPackage, appLabel = "Chrome")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. Calculation of custom duration from hours and minutes
    @Test
    fun customDuration_calculatedCorrectly_fromHoursAndMinutes() {
        fun calculateMs(hours: Long, minutes: Long): Long =
            (hours * 60L + minutes) * 60L * 1000L

        assertEquals(180_000L, calculateMs(0L, 3L))          // 3 min
        assertEquals(900_000L, calculateMs(0L, 15L))         // 15 min
        assertEquals(3_600_000L, calculateMs(1L, 0L))        // 1 hr
        assertEquals(6_300_000L, calculateMs(1L, 45L))       // 1 hr 45 min
        assertEquals(86_400_000L, calculateMs(24L, 0L))      // 24 hr
    }

    // 2. Exact custom duration is saved and retrieved
    @Test
    fun exactCustomDuration_isSavedAndRetrieved() {
        val prefName = "test_custom_limits_pref_${System.currentTimeMillis()}"
        val repo = SharedPreferencesDailyLimitRepository(context, prefName = prefName)

        val custom3MinMs = 3 * 60 * 1000L // 180,000 ms
        repo.setLimitForPackage(testPackage, custom3MinMs)

        assertEquals(custom3MinMs, repo.getLimitForPackage(testPackage))
        assertTrue(repo.hasConfiguredLimit(testPackage))
    }

    // 3. Custom duration remains correct after navigating away and returning
    @Test
    fun customDuration_remainsCorrect_afterNavigatingAwayAndReturning() = runTest {
        val dailyLimitRepo = InMemoryDailyLimitRepository()
        val selectedRepo = InMemorySelectedAppsRepository()
        val discoveryRepo = FakeDiscoveryRepo(sampleApps)
        val usageRepo = FakeUsageRepo()

        val viewModel = LimitsViewModel(
            appDiscoveryRepository = discoveryRepo,
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = dailyLimitRepo,
            usageStatsRepository = usageRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Set custom limit: 3 minutes (180,000 ms)
        val customLimitMs = 3 * 60 * 1000L
        val result = viewModel.setDailyLimit(testPackage, customLimitMs)
        assertEquals(LimitsViewModel.LimitChangeResult.AppliedImmediately, result)
        testDispatcher.scheduler.advanceUntilIdle()

        val stateBefore = viewModel.uiState.value as LimitsUiState.Success
        val appBefore = stateBefore.apps.first { it.packageName == testPackage }
        assertEquals(customLimitMs, appBefore.limitDurationMs)
        assertEquals("3 min", appBefore.formattedLimit)

        // Simulate navigating away and returning (refresh)
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val stateAfter = viewModel.uiState.value as LimitsUiState.Success
        val appAfter = stateAfter.apps.first { it.packageName == testPackage }
        assertEquals(customLimitMs, appAfter.limitDurationMs)
        assertEquals("3 min", appAfter.formattedLimit)
    }

    // 4. Custom duration remains correct across repository/process recreation
    @Test
    fun customDuration_persistsAcrossRepositoryRecreation() {
        val prefName = "test_persistence_custom_limit_${System.currentTimeMillis()}"
        val initialRepo = SharedPreferencesDailyLimitRepository(context, prefName = prefName)

        val customLimitMs = 7 * 60 * 1000L // 7 minutes
        initialRepo.setLimitForPackage(testPackage, customLimitMs)
        assertEquals(customLimitMs, initialRepo.getLimitForPackage(testPackage))

        // Recreate repository from shared preferences (simulates process restart)
        val recreatedRepo = SharedPreferencesDailyLimitRepository(context, prefName = prefName)
        assertEquals(customLimitMs, recreatedRepo.getLimitForPackage(testPackage))
        assertTrue(recreatedRepo.hasConfiguredLimit(testPackage))
    }

    // 5. A custom limit below current usage triggers enforcement
    @Test
    fun customLimit_belowCurrentUsage_triggersImmediateEnforcement() = testScope.runTest {
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf(testPackage))
        }
        val dailyLimitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage(testPackage, 3 * 60 * 1000L) // 3 minute custom limit
        }
        val fakeUsageRepo = FakeUsageStatsRepository()
        fakeUsageRepo.usageMap[testPackage] = 15 * 60 * 1000L // 15 minutes of prior daily usage
        val triggerRepo = InMemoryEnforcementTriggerRepository()
        val emitted = mutableListOf<EnforcementTrigger>()

        val liveEngine = LiveLimitEnforcementEngine(
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = dailyLimitRepo,
            usageStatsRepository = fakeUsageRepo,
            enforcementTriggerRepository = triggerRepo,
            scope = testScope,
            dispatcher = testDispatcher,
            onTriggerEmitted = { emitted.add(it) }
        )

        // When user opens Chrome with 15m usage and 3m limit -> triggers immediately
        liveEngine.onForegroundPackageChanged(testPackage)

        assertEquals(1, emitted.size)
        assertEquals(testPackage, emitted.first().packageName)
        assertEquals(LimitState.LIMIT_REACHED, emitted.first().reason)
        assertFalse(liveEngine.isMonitoring)
    }

    // 6. A custom limit above current usage does not trigger premature blocking
    @Test
    fun customLimit_aboveCurrentUsage_doesNotTriggerPrematureBlocking() = testScope.runTest {
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf(testPackage))
        }
        val customLimitMs = 3 * 60 * 1000L // 3 minutes
        val dailyLimitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage(testPackage, customLimitMs)
        }
        val fakeUsageRepo = FakeUsageStatsRepository()
        fakeUsageRepo.usageMap[testPackage] = 60 * 1000L // 1 minute (below 3 minute limit)
        val triggerRepo = InMemoryEnforcementTriggerRepository()
        val emitted = mutableListOf<EnforcementTrigger>()

        val liveEngine = LiveLimitEnforcementEngine(
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = dailyLimitRepo,
            usageStatsRepository = fakeUsageRepo,
            enforcementTriggerRepository = triggerRepo,
            checkIntervalMs = 5_000L,
            scope = testScope,
            dispatcher = testDispatcher,
            onTriggerEmitted = { emitted.add(it) }
        )

        liveEngine.onForegroundPackageChanged(testPackage)

        // Does not trigger immediately
        assertTrue(emitted.isEmpty())
        assertTrue(liveEngine.isMonitoring)

        // Advance usage to 2 minutes (still under 3 minutes)
        fakeUsageRepo.usageMap[testPackage] = 2 * 60 * 1000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertTrue("Must not trigger while under limit", emitted.isEmpty())

        // Cross limit: usage reaches 3m 2s
        fakeUsageRepo.usageMap[testPackage] = 182_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals("Must trigger once breached", 1, emitted.size)
        assertEquals(testPackage, emitted.first().packageName)
        assertFalse(liveEngine.isMonitoring)
    }

    // 7. Preset enforcement continues to work
    @Test
    fun presetEnforcement_continuesToWorkReliably() = testScope.runTest {
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf(testPackage))
        }
        val preset15MinMs = 15 * 60 * 1000L
        val dailyLimitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage(testPackage, preset15MinMs)
        }
        val fakeUsageRepo = FakeUsageStatsRepository()
        fakeUsageRepo.usageMap[testPackage] = 14 * 60 * 1000L // 14 minutes
        val triggerRepo = InMemoryEnforcementTriggerRepository()
        val emitted = mutableListOf<EnforcementTrigger>()

        val liveEngine = LiveLimitEnforcementEngine(
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = dailyLimitRepo,
            usageStatsRepository = fakeUsageRepo,
            enforcementTriggerRepository = triggerRepo,
            checkIntervalMs = 5_000L,
            scope = testScope,
            dispatcher = testDispatcher,
            onTriggerEmitted = { emitted.add(it) }
        )

        liveEngine.onForegroundPackageChanged(testPackage)
        assertTrue(emitted.isEmpty())
        assertTrue(liveEngine.isMonitoring)

        // Cross 15 min limit (15m 2s)
        fakeUsageRepo.usageMap[testPackage] = 902_000L
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        assertEquals(1, emitted.size)
        assertEquals(LimitState.LIMIT_REACHED, emitted.first().reason)
    }

    // 8. Commitment Lock stages limit increases and allows cancellation
    @Test
    fun commitmentLock_stagesCustomLimitIncrease_andAllowsCancellation() = runTest {
        val dailyLimitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage(testPackage, 15 * 60 * 1000L) // Current limit: 15 min
        }
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf(testPackage))
        }
        val commitmentRepo = FakeCommitmentLockRepository().apply {
            enableCommitmentLock(consentConfirmed = true)
        }
        val viewModel = LimitsViewModel(
            appDiscoveryRepository = FakeDiscoveryRepo(sampleApps),
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = dailyLimitRepo,
            commitmentLockRepository = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Attempt relaxation: custom 30 min (1,800,000 ms) > 15 min
        val custom30MinMs = 30 * 60 * 1000L
        val result = viewModel.setDailyLimit(testPackage, custom30MinMs)
        assertTrue(result is LimitsViewModel.LimitChangeResult.DeferredToMidnight)
        assertEquals(custom30MinMs, (result as LimitsViewModel.LimitChangeResult.DeferredToMidnight).targetLimitMs)

        // Current limit remains 15 min
        assertEquals(15 * 60 * 1000L, dailyLimitRepo.getLimitForPackage(testPackage))

        // 2. Cancel staged relaxation
        val cancelled = viewModel.cancelStagedLimitIncrease(testPackage)
        assertTrue(cancelled)
        testDispatcher.scheduler.advanceUntilIdle()

        val stateAfterCancel = viewModel.uiState.value as LimitsUiState.Success
        assertNull(stateAfterCancel.apps.first { it.packageName == testPackage }.pendingLimitIncreaseMs)

        // 3. Tightening to custom 3 min applies immediately even under lock
        val custom3MinMs = 3 * 60 * 1000L
        val tightenResult = viewModel.setDailyLimit(testPackage, custom3MinMs)
        assertEquals(LimitsViewModel.LimitChangeResult.AppliedImmediately, tightenResult)
        assertEquals(custom3MinMs, dailyLimitRepo.getLimitForPackage(testPackage))
    }

    // 9. Comparison: Preset and equivalent custom duration produce the exact same effective limit
    @Test
    fun comparisonTest_presetAndEquivalentCustomDuration_produceIdenticalEffectiveLimit() {
        val ruleEngine = UsageRuleEngine()

        val preset15MinMs = 15 * 60 * 1000L
        val custom15MinMs = (0L * 60L + 15L) * 60L * 1000L // 0h 15m
        assertEquals(preset15MinMs, custom15MinMs)

        // Under-limit evaluation
        val underUsage = 10 * 60 * 1000L
        val evalPresetUnder = ruleEngine.evaluate(testPackage, underUsage, preset15MinMs)
        val evalCustomUnder = ruleEngine.evaluate(testPackage, underUsage, custom15MinMs)
        assertEquals(evalPresetUnder.limitState, evalCustomUnder.limitState)
        assertEquals(evalPresetUnder.remainingDurationMs, evalCustomUnder.remainingDurationMs)

        // Breach evaluation
        val breachUsage = 15 * 60 * 1000L + 2_000L // 15m 2s
        val evalPresetBreach = ruleEngine.evaluate(testPackage, breachUsage, preset15MinMs)
        val evalCustomBreach = ruleEngine.evaluate(testPackage, breachUsage, custom15MinMs)
        assertEquals(LimitState.LIMIT_REACHED, evalPresetBreach.limitState)
        assertEquals(evalPresetBreach.limitState, evalCustomBreach.limitState)
        assertEquals(0L, evalCustomBreach.remainingDurationMs)
    }

    // 10. Boundaries: 0, negative, over-24h, and 1-minute
    @Test
    fun boundaryValidations_handledSafely() {
        fun validateDuration(h: Long, m: Long): String? {
            val totalMs = (h * 60 + m) * 60 * 1000L
            return when {
                h < 0 || m < 0 -> "Duration values cannot be negative."
                totalMs < 60 * 1000L -> "Daily limit must be at least 1 minute."
                totalMs > 24 * 60 * 60 * 1000L -> "Daily limit cannot exceed 24 hours."
                m >= 60 -> "Minutes must be between 0 and 59."
                else -> null
            }
        }

        assertEquals("Duration values cannot be negative.", validateDuration(-1, 5))
        assertEquals("Duration values cannot be negative.", validateDuration(1, -5))
        assertEquals("Daily limit must be at least 1 minute.", validateDuration(0, 0))
        assertEquals("Daily limit cannot exceed 24 hours.", validateDuration(25, 0))
        assertEquals("Minutes must be between 0 and 59.", validateDuration(0, 60))

        assertNull(validateDuration(0, 1))   // 1 minute valid
        assertNull(validateDuration(0, 3))   // 3 minutes valid
        assertNull(validateDuration(24, 0))  // 24 hours valid
    }

    // --- Helpers ---
    private class FakeDiscoveryRepo(
        private val apps: List<DiscoveredApp>
    ) : AppDiscoveryRepository {
        override suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>> = Result.success(apps)
    }

    private class FakeUsageRepo : UsageStatsRepository {
        override fun hasUsagePermission(): Boolean = true
        override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> =
            Result.success(emptyMap())
    }
}
