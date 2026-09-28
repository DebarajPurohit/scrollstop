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
    private val usageRuleEngine: UsageRuleEngine = UsageRuleEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UsageUiState>(UsageUiState.Loading)
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    init {
        refreshUsage()
    }

    /**
     * Configures a daily limit in milliseconds for a package and refreshes usage evaluation.
     */
    fun setDailyLimit(packageName: String, limitMs: Long) {
        dailyLimitRepository.setLimitForPackage(packageName, limitMs)
        refreshUsage()
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

                    _uiState.value = UsageUiState.Success(usageList = usageList)
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

