package com.scrollstop.ui.screens.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.model.AppUsageInfo
import com.scrollstop.domain.model.DiscoveredApp
import com.scrollstop.domain.rules.UsageRuleEngine
import com.scrollstop.domain.util.UsageFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for querying today's usage statistics for selected apps
 * and evaluating daily limit rules.
 */
class UsageViewModel(
    private val usageStatsRepository: UsageStatsRepository,
    private val appDiscoveryRepository: AppDiscoveryRepository,
    private val selectedAppsRepository: SelectedAppsRepository,
    private val dailyLimitRepository: DailyLimitRepository = InMemoryDailyLimitRepository(),
    private val usageRuleEngine: UsageRuleEngine = UsageRuleEngine(),
    private val commitmentLockRepository: com.scrollstop.data.repository.CommitmentLockRepository? = null,
    private val commitmentRuleEngine: com.scrollstop.domain.rules.CommitmentRuleEngine = com.scrollstop.domain.rules.CommitmentRuleEngine()
) : ViewModel() {

    sealed interface LimitChangeResult {
        data object AppliedImmediately : LimitChangeResult
        data class DeferredToMidnight(val targetLimitMs: Long) : LimitChangeResult
    }

    private val _uiState = MutableStateFlow<UsageUiState>(UsageUiState.Loading)
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    init {
        refreshUsage()
        viewModelScope.launch {
            selectedAppsRepository.selectedPackageNames.collect {
                refreshUsage()
            }
        }
    }

    /**
     * Removes an application from monitoring or stages removal if Commitment Lock is active.
     * When Commitment Lock is active:
     * - Immediate removal is blocked (relaxation), staged for next midnight.
     * When Commitment Lock is inactive:
     * - Removed immediately from [SelectedAppsRepository].
     */
    fun removeMonitoredApp(packageName: String): Boolean {
        val commitmentState = commitmentLockRepository?.commitmentState?.value
        val isLocked = commitmentState?.isLocked == true

        return if (isLocked) {
            val isStaged = commitmentState?.pendingRemovedPackages?.contains(packageName) == true
            val staged = if (!isStaged) {
                commitmentLockRepository?.stageAppRemoval(packageName) ?: false
            } else true
            refreshUsage()
            staged
        } else {
            selectedAppsRepository.toggleAppSelection(packageName)
            refreshUsage()
            true
        }
    }

    /**
     * Configures a daily limit in milliseconds for a package and refreshes usage evaluation.
     * When Commitment Lock is active:
     * - Reducing or matching limit takes effect immediately (tightening).
     * - Increasing limit is deferred until next midnight reset (relaxation).
     */
    fun setDailyLimit(packageName: String, limitMs: Long): LimitChangeResult {
        val currentLimit = dailyLimitRepository.getLimitForPackage(packageName)
        val commitmentState = commitmentLockRepository?.commitmentState?.value ?: com.scrollstop.domain.model.CommitmentLockState()

        return if (commitmentRuleEngine.canChangeLimitImmediately(currentLimit, limitMs, commitmentState)) {
            dailyLimitRepository.setLimitForPackage(packageName, limitMs)
            if (commitmentState.pendingIncreasedLimits.containsKey(packageName)) {
                commitmentLockRepository?.cancelStagedLimitIncrease(packageName)
            }
            refreshUsage()
            LimitChangeResult.AppliedImmediately
        } else {
            commitmentLockRepository?.stageLimitIncrease(packageName, limitMs)
            refreshUsage()
            LimitChangeResult.DeferredToMidnight(limitMs)
        }
    }

    /**
     * Checks permission and re-queries today's foreground usage stats for selected applications,
     * evaluating daily limit rules via [UsageRuleEngine].
     */
    fun refreshUsage() {
        viewModelScope.launch {
            if (!usageStatsRepository.hasUsagePermission()) {
                _uiState.value = UsageUiState.PermissionRequired
                return@launch
            }

            val selectedPackages = selectedAppsRepository.getSelectedPackages()
            if (selectedPackages.isEmpty()) {
                _uiState.value = UsageUiState.EmptySelection
                return@launch
            }

            _uiState.value = UsageUiState.Loading

            // Discover apps to resolve display labels & icons for selected package names
            val discoveredAppsResult = appDiscoveryRepository.getDiscoveredApps()
            val discoveredAppMap: Map<String, DiscoveredApp> = discoveredAppsResult.getOrDefault(emptyList())
                .associateBy { it.packageName }

            val usageResult = usageStatsRepository.getTodayUsageForPackages(selectedPackages)
            usageResult
                .onSuccess { usageMap ->
                    val usageList = selectedPackages
                        .map { packageName ->
                            val discovered = discoveredAppMap[packageName]
                            val label = discovered?.appLabel ?: packageName
                            val icon = discovered?.icon
                            val durationMs = usageMap[packageName] ?: 0L
                            val limitMs = dailyLimitRepository.getLimitForPackage(packageName)

                            // Evaluate rule using deterministic domain Rules Engine
                            val evaluation = usageRuleEngine.evaluate(
                                packageName = packageName,
                                usedDurationMs = durationMs,
                                limitDurationMs = limitMs
                            )

                            AppUsageInfo(
                                packageName = packageName,
                                appLabel = label,
                                icon = icon,
                                totalTimeInForegroundMs = evaluation.usedDurationMs,
                                formattedUsage = UsageFormatter.formatDuration(evaluation.usedDurationMs),
                                limitDurationMs = evaluation.limitDurationMs,
                                remainingDurationMs = evaluation.remainingDurationMs,
                                limitState = evaluation.limitState,
                                formattedLimit = UsageFormatter.formatDuration(evaluation.limitDurationMs),
                                formattedRemaining = UsageFormatter.formatDuration(evaluation.remainingDurationMs)
                            )
                        }
                        // Sort deterministically by label
                        .sortedBy { it.appLabel.lowercase() }

                    val commitmentState = commitmentLockRepository?.commitmentState?.value
                    val pendingLimits = commitmentState?.pendingIncreasedLimits ?: emptyMap()
                    val pendingRemovals = commitmentState?.pendingRemovedPackages ?: emptySet()
                    val isLocked = commitmentState?.isLocked == true

                    _uiState.value = UsageUiState.Success(
                        usageList = usageList,
                        pendingIncreasedLimits = pendingLimits,
                        pendingRemovalPackages = pendingRemovals,
                        isCommitmentLocked = isLocked
                    )
                }
                .onFailure { exception ->
                    if (exception is SecurityException) {
                        _uiState.value = UsageUiState.PermissionRequired
                    } else {
                        _uiState.value = UsageUiState.Error(
                            errorMessage = exception.localizedMessage ?: "Failed to load usage data"
                        )
                    }
                }
        }
    }
}

