package com.scrollstop.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.CommitmentLockRepository
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.ProtectionHealthRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.model.CommitmentLockState
import com.scrollstop.domain.model.MonitoredAppUsage
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.components.SevenDayChartState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI State for the Main Dashboard screen.
 */
data class MainUiState(
    val healthState: ProtectionHealthState = ProtectionHealthState.DEGRADED,
    val selectedAppCount: Int = 0,
    val commitmentState: CommitmentLockState = CommitmentLockState(),
    val todayScreenTimeMs: Long = 0L,
    val hasUsagePermission: Boolean = false,
    val isUsageLoading: Boolean = true,
    val usageErrorMessage: String? = null,
    val sevenDayChartState: SevenDayChartState = SevenDayChartState.Loading,
    val monitoredApps: List<MonitoredAppUsage> = emptyList(),
    val totalDailyLimitMs: Long? = null
) {
    val formattedTodayScreenTime: String
        get() {
            val totalMinutes = todayScreenTimeMs / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

/**
 * Internal holder for dashboard usage state.
 */
internal data class DashboardUsageData(
    val todayScreenTimeMs: Long = 0L,
    val hasUsagePermission: Boolean = false,
    val isUsageLoading: Boolean = true,
    val usageErrorMessage: String? = null,
    val sevenDayChartState: SevenDayChartState = SevenDayChartState.Loading,
    val monitoredApps: List<MonitoredAppUsage> = emptyList(),
    val totalDailyLimitMs: Long? = null
)

/**
 * ViewModel managing Main Dashboard state, protection health reporting, real usage data querying,
 * and recovery flows.
 */
class MainViewModel(
    private val protectionHealthRepository: ProtectionHealthRepository,
    private val selectedAppsRepository: SelectedAppsRepository,
    private val commitmentLockRepository: CommitmentLockRepository? = null,
    private val usageStatsRepository: UsageStatsRepository? = null,
    private val appDiscoveryRepository: AppDiscoveryRepository? = null,
    private val dailyLimitRepository: DailyLimitRepository? = null
) : ViewModel() {

    private val fallbackCommitmentFlow = MutableStateFlow(CommitmentLockState())
    private val dashboardUsageDataFlow = MutableStateFlow(DashboardUsageData())

    val uiState: StateFlow<MainUiState> = combine(
        protectionHealthRepository.healthState,
        selectedAppsRepository.selectedPackageNames,
        commitmentLockRepository?.commitmentState ?: fallbackCommitmentFlow,
        dashboardUsageDataFlow
    ) { health, selectedApps, commitment, usageData ->
        MainUiState(
            healthState = health,
            selectedAppCount = selectedApps.size,
            commitmentState = commitment,
            todayScreenTimeMs = usageData.todayScreenTimeMs,
            hasUsagePermission = usageData.hasUsagePermission,
            isUsageLoading = usageData.isUsageLoading,
            usageErrorMessage = usageData.usageErrorMessage,
            sevenDayChartState = usageData.sevenDayChartState,
            monitoredApps = usageData.monitoredApps,
            totalDailyLimitMs = usageData.totalDailyLimitMs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState(
            healthState = protectionHealthRepository.healthState.value,
            selectedAppCount = selectedAppsRepository.getSelectedPackages().size,
            commitmentState = commitmentLockRepository?.commitmentState?.value ?: CommitmentLockState()
        )
    )

    init {
        loadDashboardData()
        viewModelScope.launch {
            selectedAppsRepository.selectedPackageNames.collect {
                loadDashboardData()
            }
        }
    }

    /**
     * Loads authoritative usage data from [UsageStatsRepository], [AppDiscoveryRepository],
     * and [DailyLimitRepository].
     */
    fun loadDashboardData() {
        viewModelScope.launch {
            val usageRepo = usageStatsRepository
            if (usageRepo == null) {
                dashboardUsageDataFlow.value = DashboardUsageData(
                    isUsageLoading = false,
                    hasUsagePermission = false,
                    sevenDayChartState = SevenDayChartState.Error("Usage repository unavailable")
                )
                return@launch
            }

            if (!usageRepo.hasUsagePermission()) {
                dashboardUsageDataFlow.value = DashboardUsageData(
                    isUsageLoading = false,
                    hasUsagePermission = false,
                    sevenDayChartState = SevenDayChartState.Error("Usage Access permission required")
                )
                return@launch
            }

            dashboardUsageDataFlow.value = dashboardUsageDataFlow.value.copy(isUsageLoading = true)

            // 1. Query today's total phone screen time
            val todayTotalResult = usageRepo.getTodayTotalUsageMs()
            val todayTotalMs = todayTotalResult.getOrDefault(0L)

            // 2. Query 7-day daily usage for chart
            val sevenDayResult = usageRepo.getSevenDayDailyUsage()
            val chartState = sevenDayResult.fold(
                onSuccess = { records ->
                    SevenDayChartState.Success(days = records)
                },
                onFailure = { err ->
                    SevenDayChartState.Error(
                        message = err.message ?: "Failed to query 7-day usage",
                        onRetry = { loadDashboardData() }
                    )
                }
            )

            // 3. Query monitored apps
            val selectedPackages = selectedAppsRepository.getSelectedPackages()
            val monitoredList = if (selectedPackages.isNotEmpty()) {
                val discoveredApps = appDiscoveryRepository?.getDiscoveredApps()?.getOrDefault(emptyList()) ?: emptyList()
                val discoveredMap = discoveredApps.associateBy { it.packageName }

                val todayUsageMap = usageRepo.getTodayUsageForPackages(selectedPackages).getOrDefault(emptyMap())
                val cumulativeUsageMap = usageRepo.getMultiDayUsageForPackages(selectedPackages, 7).getOrDefault(emptyMap())
                val recordedDays = if (chartState is SevenDayChartState.Success) chartState.recordedDayCount else 7

                selectedPackages.map { pkg ->
                    val app = discoveredMap[pkg]
                    val limitMs = dailyLimitRepository?.getLimitForPackage(pkg)
                    MonitoredAppUsage(
                        packageName = pkg,
                        appName = app?.appLabel ?: pkg,
                        icon = app?.icon,
                        todayUsageMs = todayUsageMap[pkg] ?: 0L,
                        dailyLimitMs = limitMs,
                        cumulativeUsageMs = cumulativeUsageMap[pkg] ?: (todayUsageMap[pkg] ?: 0L),
                        observationDays = recordedDays
                    )
                }.sortedByDescending { it.todayUsageMs }
            } else {
                emptyList()
            }

            val totalLimit = if (monitoredList.isNotEmpty()) {
                val limits = monitoredList.mapNotNull { it.dailyLimitMs }
                if (limits.isNotEmpty()) limits.sum() else null
            } else null

            dashboardUsageDataFlow.value = DashboardUsageData(
                todayScreenTimeMs = todayTotalMs,
                hasUsagePermission = true,
                isUsageLoading = false,
                usageErrorMessage = null,
                sevenDayChartState = chartState,
                monitoredApps = monitoredList,
                totalDailyLimitMs = totalLimit
            )
        }
    }

    fun enableCommitmentLock(consentConfirmed: Boolean): Boolean {
        return commitmentLockRepository?.enableCommitmentLock(consentConfirmed) ?: false
    }

    fun requestUnlock(): Boolean {
        return commitmentLockRepository?.requestUnlock() ?: false
    }

    fun cancelUnlockRequest(): Boolean {
        return commitmentLockRepository?.cancelUnlockRequest() ?: false
    }

    fun debugClearCommitmentState() {
        commitmentLockRepository?.debugClearCommitmentState()
    }

    fun refreshDashboard(context: Context) {
        commitmentLockRepository?.checkAndApplyMidnightTransitions()
        protectionHealthRepository.refreshHealth(context)
        loadDashboardData()
    }

    fun refreshHealth(context: Context) {
        refreshDashboard(context)
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
