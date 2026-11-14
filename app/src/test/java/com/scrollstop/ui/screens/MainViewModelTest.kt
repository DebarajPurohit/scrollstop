package com.scrollstop.ui.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.FakeCommitmentLockRepository
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.ProtectionHealthRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.domain.model.DailyUsageRecord
import com.scrollstop.domain.model.DiscoveredApp
import com.scrollstop.domain.model.MonitoredAppUsage
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.components.SevenDayChartState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelTest {

    private lateinit var context: Context
    private lateinit var fakeHealthRepo: FakeProtectionHealthRepository
    private lateinit var selectedAppsRepo: InMemorySelectedAppsRepository
    private lateinit var fakeCommitmentRepo: FakeCommitmentLockRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeHealthRepo = FakeProtectionHealthRepository()
        selectedAppsRepo = InMemorySelectedAppsRepository()
        fakeCommitmentRepo = FakeCommitmentLockRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_reflectsHealthAndSelectedAppCount() = runTest(testDispatcher) {
        fakeHealthRepo.setHealth(ProtectionHealthState.PROTECTED)
        selectedAppsRepo.setSelectedPackages(setOf("com.amazon.mShop.android.shopping", "com.google.android.youtube"))

        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProtectionHealthState.PROTECTED, state.healthState)
        assertEquals(2, state.selectedAppCount)
        assertFalse(state.commitmentState.isLocked)
    }

    @Test
    fun uiState_updatesWhenHealthChanges() = runTest(testDispatcher) {
        fakeHealthRepo.setHealth(ProtectionHealthState.PROTECTED)
        selectedAppsRepo.setSelectedPackages(setOf("com.instagram.android"))

        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        testScheduler.advanceUntilIdle()
        assertEquals(ProtectionHealthState.PROTECTED, viewModel.uiState.value.healthState)

        // Health drops to USAGE_ACCESS_REQUIRED
        fakeHealthRepo.setHealth(ProtectionHealthState.USAGE_ACCESS_REQUIRED)
        testScheduler.advanceUntilIdle()

        assertEquals(ProtectionHealthState.USAGE_ACCESS_REQUIRED, viewModel.uiState.value.healthState)
        assertEquals(1, viewModel.uiState.value.selectedAppCount)
    }

    @Test
    fun enableCommitmentLock_requiresConsentAndUpdatesUiState() = runTest(testDispatcher) {
        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        testScheduler.advanceUntilIdle()

        // Without consent
        val resultNoConsent = viewModel.enableCommitmentLock(consentConfirmed = false)
        assertFalse(resultNoConsent)
        assertFalse(viewModel.uiState.value.commitmentState.isLocked)

        // With affirmative consent
        val resultWithConsent = viewModel.enableCommitmentLock(consentConfirmed = true)
        assertTrue(resultWithConsent)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.commitmentState.isLocked)
    }

    @Test
    fun requestAndCancelUnlock_updatesUiState() = runTest(testDispatcher) {
        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        viewModel.enableCommitmentLock(consentConfirmed = true)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.requestUnlock())
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.commitmentState.pendingUnlock)

        assertTrue(viewModel.cancelUnlockRequest())
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.commitmentState.pendingUnlock)
    }

    @Test
    fun debugClearCommitmentState_resetsCommitmentImmediately() = runTest(testDispatcher) {
        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        viewModel.enableCommitmentLock(consentConfirmed = true)
        viewModel.requestUnlock()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.commitmentState.isLocked)

        viewModel.debugClearCommitmentState()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.commitmentState.isLocked)
        assertFalse(viewModel.uiState.value.commitmentState.pendingUnlock)
    }

    @Test
    fun openSettingsActions_executeSafely() {
        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)

        // Ensure opening system settings intents does not throw exceptions
        viewModel.openUsageAccessSettings(context)
        viewModel.openAccessibilitySettings(context)
        assertNotNull(viewModel)
    }

    @Test
    fun loadDashboardData_withPermission_populatesHeroMetricAndSevenDayChart() = runTest(testDispatcher) {
        val records = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Tue 9", "Tue", 2L, totalScreenTimeMs = 7_200_000L, hasRecord = true),
            DailyUsageRecord("Wed 10", "Wed", 3L, totalScreenTimeMs = 5_400_000L, hasRecord = true),
            DailyUsageRecord("Thu 11", "Thu", 4L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Fri 12", "Fri", 5L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Sat 13", "Sat", 6L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Today", "Sun", 7L, totalScreenTimeMs = 13_320_000L, hasRecord = true, isCurrentDay = true)
        )
        val usageRepo = FakeFullUsageStatsRepository(
            hasPermission = true,
            todayTotalMs = 13_320_000L, // 3h 42m
            sevenDayRecords = records
        )

        val viewModel = MainViewModel(
            protectionHealthRepository = fakeHealthRepo,
            selectedAppsRepository = selectedAppsRepo,
            commitmentLockRepository = fakeCommitmentRepo,
            usageStatsRepository = usageRepo
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasUsagePermission)
        assertFalse(state.isUsageLoading)
        assertEquals(13_320_000L, state.todayScreenTimeMs)
        assertEquals("3h 42m", state.formattedTodayScreenTime)

        val chartState = state.sevenDayChartState
        assertTrue(chartState is SevenDayChartState.Success)
        val successChart = chartState as SevenDayChartState.Success
        assertEquals(4, successChart.recordedDayCount)
        assertTrue(successChart.isHistoryIncomplete)
    }

    @Test
    fun loadDashboardData_withoutPermission_setsPermissionRequiredError() = runTest(testDispatcher) {
        val usageRepo = FakeFullUsageStatsRepository(hasPermission = false)

        val viewModel = MainViewModel(
            protectionHealthRepository = fakeHealthRepo,
            selectedAppsRepository = selectedAppsRepo,
            commitmentLockRepository = fakeCommitmentRepo,
            usageStatsRepository = usageRepo
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasUsagePermission)
        assertFalse(state.isUsageLoading)
        assertTrue(state.sevenDayChartState is SevenDayChartState.Error)
        assertEquals(
            "Usage Access permission required",
            (state.sevenDayChartState as SevenDayChartState.Error).message
        )
    }

    @Test
    fun loadDashboardData_withSelectedApps_mapsUsageLimitsAndCumulativeStats() = runTest(testDispatcher) {
        val records = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Tue 9", "Tue", 2L, totalScreenTimeMs = 7_200_000L, hasRecord = true),
            DailyUsageRecord("Wed 10", "Wed", 3L, totalScreenTimeMs = 5_400_000L, hasRecord = true),
            DailyUsageRecord("Today", "Thu", 4L, totalScreenTimeMs = 10_000_000L, hasRecord = true, isCurrentDay = true)
        )
        val usageRepo = FakeFullUsageStatsRepository(
            hasPermission = true,
            todayTotalMs = 10_000_000L,
            sevenDayRecords = records,
            todayUsageMap = mutableMapOf(
                "com.instagram.android" to 2_700_000L, // 45m
                "com.google.android.youtube" to 1_800_000L // 30m
            ),
            cumulativeUsageMap = mutableMapOf(
                "com.instagram.android" to 12_000_000L, // 3h 20m
                "com.google.android.youtube" to 9_000_000L // 2h 30m
            )
        )
        val appDiscoveryRepo = FakeAppDiscoveryRepository(
            apps = listOf(
                DiscoveredApp("com.instagram.android", "Instagram", null),
                DiscoveredApp("com.google.android.youtube", "YouTube", null)
            )
        )
        val dailyLimitRepo = InMemoryDailyLimitRepository()
        dailyLimitRepo.setLimitForPackage("com.instagram.android", 1_800_000L) // 30m limit
        dailyLimitRepo.setLimitForPackage("com.google.android.youtube", 3_600_000L) // 60m limit

        selectedAppsRepo.setSelectedPackages(setOf("com.instagram.android", "com.google.android.youtube"))

        val viewModel = MainViewModel(
            protectionHealthRepository = fakeHealthRepo,
            selectedAppsRepository = selectedAppsRepo,
            commitmentLockRepository = fakeCommitmentRepo,
            usageStatsRepository = usageRepo,
            appDiscoveryRepository = appDiscoveryRepo,
            dailyLimitRepository = dailyLimitRepo
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.monitoredApps.size)

        val firstApp = state.monitoredApps[0]
        assertEquals("Instagram", firstApp.appName)
        assertEquals("com.instagram.android", firstApp.packageName)
        assertEquals(2_700_000L, firstApp.todayUsageMs)
        assertEquals(1_800_000L, firstApp.dailyLimitMs)
        assertEquals("45m", firstApp.formattedTodayUsage)
        assertEquals("30m daily", firstApp.formattedLimit)
        assertEquals("3h 20m (4d of data)", firstApp.formattedCumulative)

        val secondApp = state.monitoredApps[1]
        assertEquals("YouTube", secondApp.appName)
        assertEquals("30m", secondApp.formattedTodayUsage)
        assertEquals("1h 0m daily", secondApp.formattedLimit)
        assertEquals("2h 30m (4d of data)", secondApp.formattedCumulative)

        // Total daily limit summed
        assertEquals(5_400_000L, state.totalDailyLimitMs)
    }

    @Test
    fun loadDashboardData_populatesTopAppsDescendingMaxFiveAndFiltersZeroUsage() = runTest(testDispatcher) {
        val discoveredApps = listOf(
            DiscoveredApp("pkg.zero", "Zero App"),
            DiscoveredApp("pkg.one", "One App"),
            DiscoveredApp("pkg.two", "Two App"),
            DiscoveredApp("pkg.three", "Three App"),
            DiscoveredApp("pkg.four", "Four App"),
            DiscoveredApp("pkg.five", "Five App"),
            DiscoveredApp("pkg.six", "Six App")
        )
        val usageRepo = FakeFullUsageStatsRepository(
            hasPermission = true,
            todayUsageMap = mutableMapOf(
                "pkg.zero" to 0L,
                "pkg.one" to 10_000L,
                "pkg.two" to 50_000L,
                "pkg.three" to 20_000L,
                "pkg.four" to 70_000L,
                "pkg.five" to 30_000L,
                "pkg.six" to 60_000L
            )
        )
        val appDiscoveryRepo = FakeAppDiscoveryRepository(discoveredApps)
        selectedAppsRepo.setSelectedPackages(setOf("pkg.four", "pkg.two"))

        val viewModel = MainViewModel(
            protectionHealthRepository = fakeHealthRepo,
            selectedAppsRepository = selectedAppsRepo,
            commitmentLockRepository = fakeCommitmentRepo,
            usageStatsRepository = usageRepo,
            appDiscoveryRepository = appDiscoveryRepo
        )
        testScheduler.advanceUntilIdle()

        val topApps = viewModel.uiState.value.topApps
        // Zero usage app excluded, remaining 6 apps capped at 5
        assertEquals(5, topApps.size)
        // Descending order: four (70k), six (60k), two (50k), five (30k), three (20k)
        assertEquals("pkg.four", topApps[0].packageName)
        assertTrue(topApps[0].isMonitored)
        assertEquals(70_000L, topApps[0].todayUsageMs)

        assertEquals("pkg.six", topApps[1].packageName)
        assertFalse(topApps[1].isMonitored)
        assertEquals(60_000L, topApps[1].todayUsageMs)

        assertEquals("pkg.two", topApps[2].packageName)
        assertTrue(topApps[2].isMonitored)
        assertEquals(50_000L, topApps[2].todayUsageMs)

        assertEquals("pkg.five", topApps[3].packageName)
        assertFalse(topApps[3].isMonitored)

        assertEquals("pkg.three", topApps[4].packageName)
        assertFalse(topApps[4].isMonitored)

        // Toggle monitoring for pkg.six
        viewModel.toggleAppMonitoring("pkg.six")
        testScheduler.advanceUntilIdle()
        assertTrue(selectedAppsRepo.isSelected("pkg.six"))
    }

    @Test
    fun loadDashboardData_unresolvableAppLabels_fallBackToUnknownApp() = runTest(testDispatcher) {
        val discoveredApps = listOf(
            DiscoveredApp("pkg.blank", "   "),
            DiscoveredApp("pkg.matches", "pkg.matches")
        )
        val usageRepo = FakeFullUsageStatsRepository(
            hasPermission = true,
            todayUsageMap = mutableMapOf(
                "pkg.blank" to 10_000L,
                "pkg.matches" to 20_000L,
                "pkg.uninstalled" to 30_000L
            )
        )
        val appDiscoveryRepo = FakeAppDiscoveryRepository(discoveredApps)
        selectedAppsRepo.setSelectedPackages(setOf("pkg.uninstalled"))

        val viewModel = MainViewModel(
            protectionHealthRepository = fakeHealthRepo,
            selectedAppsRepository = selectedAppsRepo,
            commitmentLockRepository = fakeCommitmentRepo,
            usageStatsRepository = usageRepo,
            appDiscoveryRepository = appDiscoveryRepo
        )
        testScheduler.advanceUntilIdle()

        val topApps = viewModel.uiState.value.topApps
        assertEquals(3, topApps.size)
        assertEquals("Unknown app", topApps[0].appName) // pkg.uninstalled (30k)
        assertEquals("Unknown app", topApps[1].appName) // pkg.matches (20k)
        assertEquals("Unknown app", topApps[2].appName) // pkg.blank (10k)
    }

    @Test
    fun mainUiState_protectionSetupPredicates_evaluateAuthoritatively() {
        // Step 1: Apps selected
        assertFalse(MainUiState(selectedAppCount = 0).isStep1AppsSelected)
        assertTrue(MainUiState(selectedAppCount = 1).isStep1AppsSelected)

        // Step 2 & enforcedAppCount: Limits configured
        val appsWithNoLimits = listOf(
            MonitoredAppUsage(packageName = "app1", appName = "App1", dailyLimitMs = null),
            MonitoredAppUsage(packageName = "app2", appName = "App2", dailyLimitMs = 0L)
        )
        val stateNoLimits = MainUiState(monitoredApps = appsWithNoLimits)
        assertEquals(0, stateNoLimits.enforcedAppCount)
        assertFalse(stateNoLimits.isStep2LimitsConfigured)

        val appsWithLimits = listOf(
            MonitoredAppUsage(packageName = "app1", appName = "App1", dailyLimitMs = 1800000L),
            MonitoredAppUsage(packageName = "app2", appName = "App2", dailyLimitMs = null)
        )
        val stateWithLimits = MainUiState(monitoredApps = appsWithLimits)
        assertEquals(1, stateWithLimits.enforcedAppCount)
        assertTrue(stateWithLimits.isStep2LimitsConfigured)

        // Step 3: Accessibility active only when PROTECTED
        assertTrue(MainUiState(healthState = ProtectionHealthState.PROTECTED).isStep3AccessibilityActive)
        assertFalse(MainUiState(healthState = ProtectionHealthState.USAGE_ACCESS_REQUIRED).isStep3AccessibilityActive)
        assertFalse(MainUiState(healthState = ProtectionHealthState.ACCESSIBILITY_REQUIRED).isStep3AccessibilityActive)
        assertFalse(MainUiState(healthState = ProtectionHealthState.ACCESSIBILITY_INTERRUPTED).isStep3AccessibilityActive)
        assertFalse(MainUiState(healthState = ProtectionHealthState.DEGRADED).isStep3AccessibilityActive)

        // Setup complete requires all 3 steps
        val incompleteState1 = MainUiState(
            selectedAppCount = 1,
            monitoredApps = appsWithNoLimits,
            healthState = ProtectionHealthState.PROTECTED
        )
        assertFalse(incompleteState1.isSetupComplete)

        val incompleteState2 = MainUiState(
            selectedAppCount = 1,
            monitoredApps = appsWithLimits,
            healthState = ProtectionHealthState.ACCESSIBILITY_REQUIRED
        )
        assertFalse(incompleteState2.isSetupComplete)

        val completeState = MainUiState(
            selectedAppCount = 1,
            monitoredApps = appsWithLimits,
            healthState = ProtectionHealthState.PROTECTED
        )
        assertTrue(completeState.isSetupComplete)
    }

    private class FakeProtectionHealthRepository : ProtectionHealthRepository {
        private val _healthState = MutableStateFlow(ProtectionHealthState.DEGRADED)
        override val healthState: StateFlow<ProtectionHealthState> = _healthState.asStateFlow()

        fun setHealth(state: ProtectionHealthState) {
            _healthState.value = state
        }

        override fun refreshHealth(context: Context) {
            // No-op for fake
        }

        override fun calculateCurrentHealth(
            hasUsagePermission: Boolean,
            accessibilityState: AccessibilityServiceState
        ): ProtectionHealthState {
            return _healthState.value
        }
    }

    private class FakeFullUsageStatsRepository(
        var hasPermission: Boolean = true,
        var todayTotalMs: Long = 0L,
        var sevenDayRecords: List<DailyUsageRecord> = emptyList(),
        val todayUsageMap: MutableMap<String, Long> = mutableMapOf(),
        val cumulativeUsageMap: MutableMap<String, Long> = mutableMapOf()
    ) : UsageStatsRepository {
        override fun hasUsagePermission(): Boolean = hasPermission

        override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> {
            if (!hasPermission) return Result.failure(SecurityException("Permission denied"))
            return Result.success(packageNames.associateWith { todayUsageMap[it] ?: 0L })
        }

        override fun getTodayTotalUsageMs(): Result<Long> {
            if (!hasPermission) return Result.failure(SecurityException("Permission denied"))
            return Result.success(todayTotalMs)
        }

        override fun getSevenDayDailyUsage(): Result<List<DailyUsageRecord>> {
            if (!hasPermission) return Result.failure(SecurityException("Permission denied"))
            return Result.success(sevenDayRecords)
        }

        override fun getMultiDayUsageForPackages(packageNames: Set<String>, daysCount: Int): Result<Map<String, Long>> {
            if (!hasPermission) return Result.failure(SecurityException("Permission denied"))
            return Result.success(packageNames.associateWith { cumulativeUsageMap[it] ?: 0L })
        }
    }

    private class FakeAppDiscoveryRepository(
        val apps: List<DiscoveredApp> = emptyList()
    ) : AppDiscoveryRepository {
        override suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>> = Result.success(apps)
    }
}
