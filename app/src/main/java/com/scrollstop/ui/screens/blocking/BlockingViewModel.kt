package com.scrollstop.ui.screens.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine
import com.scrollstop.domain.util.UsageFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the POC-05 Blocking UI.
 */
data class BlockingUiState(
    val packageName: String = "",
    val appName: String = "",
    val usedDurationMs: Long = 0L,
    val limitDurationMs: Long = 0L,
    val formattedUsage: String = "0 min",
    val formattedLimit: String = "0 min",
    val limitState: LimitState = LimitState.LIMIT_REACHED,
    val isLoading: Boolean = true
)

/**
 * ViewModel managing data for the POC-05 Blocking UI screen.
 * Queries trusted local repositories (UsageStatsRepository, DailyLimitRepository, AppDiscoveryRepository)
 * and evaluates rules using UsageRuleEngine.
 */
class BlockingViewModel(
    val packageName: String,
    private val usageStatsRepository: UsageStatsRepository,
    private val dailyLimitRepository: DailyLimitRepository,
    private val appDiscoveryRepository: AppDiscoveryRepository,
    private val usageRuleEngine: UsageRuleEngine = UsageRuleEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockingUiState(packageName = packageName))
    val uiState: StateFlow<BlockingUiState> = _uiState.asStateFlow()

    init {
        loadBlockingData()
    }

    fun loadBlockingData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val discoveredApps = appDiscoveryRepository.getDiscoveredApps().getOrDefault(emptyList())
            val discovered = discoveredApps.find { it.packageName == packageName }
            val appLabel = discovered?.appLabel ?: packageName

            val usageResult = usageStatsRepository.getTodayUsageForPackages(setOf(packageName))
            val usedMs = usageResult.getOrNull()?.get(packageName) ?: 0L

            val limitMs = dailyLimitRepository.getLimitForPackage(packageName)

            val eval = usageRuleEngine.evaluate(
                packageName = packageName,
                usedDurationMs = usedMs,
                limitDurationMs = limitMs
            )

            _uiState.value = BlockingUiState(
                packageName = packageName,
                appName = appLabel,
                usedDurationMs = eval.usedDurationMs,
                limitDurationMs = eval.limitDurationMs,
                formattedUsage = UsageFormatter.formatDuration(eval.usedDurationMs),
                formattedLimit = UsageFormatter.formatDuration(eval.limitDurationMs),
                limitState = eval.limitState,
                isLoading = false
            )
        }
    }
}
