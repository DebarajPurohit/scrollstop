package com.scrollstop.ui.screens.limits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.CommitmentLockRepository
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.model.CommitmentLockState
import com.scrollstop.domain.model.DiscoveredApp
import com.scrollstop.domain.rules.CommitmentRuleEngine
import com.scrollstop.domain.rules.UsageRuleEngine
import com.scrollstop.domain.util.UsageFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Unified ViewModel for the Limits & App Management experience.
 *
 * Coordinates:
 * - App discovery catalog
 * - Monitored app selection status
 * - Foreground usage tracking & daily limits evaluation
 * - Search by label and package name (case-insensitive)
 * - Category filter derivation (ALL, CONTROLLED, SOCIAL_VIDEO)
 * - Commitment Lock rules enforcement (immediate tightening, staged relaxation, cancellations)
 */
class LimitsViewModel(
    private val appDiscoveryRepository: AppDiscoveryRepository,
    private val selectedAppsRepository: SelectedAppsRepository = InMemorySelectedAppsRepository(),
    private val dailyLimitRepository: DailyLimitRepository = InMemoryDailyLimitRepository(),
    private val usageStatsRepository: UsageStatsRepository? = null,
    private val usageRuleEngine: UsageRuleEngine = UsageRuleEngine(),
    private val commitmentLockRepository: CommitmentLockRepository? = null,
    private val commitmentRuleEngine: CommitmentRuleEngine = CommitmentRuleEngine()
) : ViewModel() {

    sealed interface LimitChangeResult {
        data object AppliedImmediately : LimitChangeResult
        data class DeferredToMidnight(val targetLimitMs: Long) : LimitChangeResult
    }

    private val _uiState = MutableStateFlow<LimitsUiState>(LimitsUiState.Loading)
    val uiState: StateFlow<LimitsUiState> = _uiState.asStateFlow()

    private var rawDiscoveredApps = listOf<DiscoveredApp>()
    private var todayUsageMap = mapOf<String, Long>()
    private var currentSearchQuery = ""
    private var activeFilter = LimitsFilter.ALL
    private var currentNotice: String? = null

    init {
        loadData()
        observeSelectedApps()
        observeCommitmentState()
        observeLimits()
    }

    private fun observeSelectedApps() {
        viewModelScope.launch {
            selectedAppsRepository.selectedPackageNames.collect {
                if (_uiState.value is LimitsUiState.Success) {
                    updateSuccessState()
                }
            }
        }
    }

    private fun observeCommitmentState() {
        commitmentLockRepository?.let { repo ->
            viewModelScope.launch {
                repo.commitmentState.collect {
                    if (_uiState.value is LimitsUiState.Success) {
                        updateSuccessState()
                    }
                }
            }
        }
    }

    private fun observeLimits() {
        viewModelScope.launch {
            dailyLimitRepository.limitsFlow.collect {
                if (_uiState.value is LimitsUiState.Success) {
                    updateSuccessState()
                }
            }
        }
    }

    /**
     * Loads or reloads app catalogue and usage statistics.
     */
    fun loadData() {
        viewModelScope.launch {
            if (usageStatsRepository != null && !usageStatsRepository.hasUsagePermission()) {
                _uiState.value = LimitsUiState.PermissionRequired
                return@launch
            }

            _uiState.value = LimitsUiState.Loading

            appDiscoveryRepository.getDiscoveredApps()
                .onSuccess { discovered ->
                    rawDiscoveredApps = discovered
                    if (discovered.isEmpty()) {
                        _uiState.value = LimitsUiState.Empty()
                    } else {
                        loadUsageAndBuildState()
                    }
                }
                .onFailure { exception ->
                    _uiState.value = LimitsUiState.Error(
                        errorMessage = exception.localizedMessage ?: "Failed to discover applications."
                    )
                }
        }
    }

    fun refresh() = loadData()
    fun retry() = loadData()

    private suspend fun loadUsageAndBuildState() {
        if (usageStatsRepository != null) {
            val allPackages = rawDiscoveredApps.map { it.packageName }.toSet()
            usageStatsRepository.getTodayUsageForPackages(allPackages)
                .onSuccess { usage ->
                    todayUsageMap = usage
                    updateSuccessState()
                }
                .onFailure { exception ->
                    if (exception is SecurityException) {
                        _uiState.value = LimitsUiState.PermissionRequired
                    } else {
                        todayUsageMap = emptyMap()
                        updateSuccessState()
                    }
                }
        } else {
            todayUsageMap = emptyMap()
            updateSuccessState()
        }
    }

    /**
     * Updates the active search query and recalculates the filtered application catalogue.
     * Search matches display name or package name case-insensitively.
     */
    fun onSearchQueryChange(query: String) {
        currentSearchQuery = query
        updateSuccessState()
    }

    /**
     * Updates the active filter category (ALL, CONTROLLED, SOCIAL_VIDEO).
     */
    fun onFilterSelect(filter: LimitsFilter) {
        activeFilter = filter
        updateSuccessState()
    }

    /**
     * Toggles monitoring/selection status for the given application.
     *
     * Commitment Lock semantics:
     * - Adding an app applies immediately (tightening).
     * - Removing an app while locked is blocked immediately and staged for midnight reset (relaxation).
     * - If the app is already staged for removal, toggling it again cancels scheduled removal.
     */
    fun toggleAppSelection(packageName: String) {
        val isCurrentlySelected = selectedAppsRepository.isSelected(packageName)
        val commitmentState = commitmentLockRepository?.commitmentState?.value ?: CommitmentLockState()
        val isLocked = commitmentState.isLocked

        if (isCurrentlySelected && isLocked) {
            val isStaged = commitmentState.pendingRemovedPackages.contains(packageName)
            if (isStaged) {
                commitmentLockRepository?.cancelStagedAppRemoval(packageName)
                currentNotice = "Cancelled scheduled removal for this app."
            } else {
                commitmentLockRepository?.stageAppRemoval(packageName)
                currentNotice = "Commitment Lock is active. Removing protected apps will take effect at next midnight (12:00 AM)."
            }
        } else {
            selectedAppsRepository.toggleAppSelection(packageName)
            if (commitmentState.pendingRemovedPackages.contains(packageName)) {
                commitmentLockRepository?.cancelStagedAppRemoval(packageName)
            }
            currentNotice = null
        }
        updateSuccessState()
    }

    /**
     * Configures a daily limit in milliseconds for a package.
     *
     * Commitment Lock semantics:
     * - Reducing or matching limit (tightening) applies immediately.
     * - Increasing limit while locked (relaxation) is staged until next midnight reset.
     */
    fun setDailyLimit(packageName: String, limitMs: Long): LimitChangeResult {
        val currentLimit = dailyLimitRepository.getLimitForPackage(packageName)
        val commitmentState = commitmentLockRepository?.commitmentState?.value ?: CommitmentLockState()

        return if (commitmentRuleEngine.canChangeLimitImmediately(currentLimit, limitMs, commitmentState)) {
            dailyLimitRepository.setLimitForPackage(packageName, limitMs)
            if (commitmentState.pendingIncreasedLimits.containsKey(packageName)) {
                commitmentLockRepository?.cancelStagedLimitIncrease(packageName)
            }
            currentNotice = null
            updateSuccessState()
            LimitChangeResult.AppliedImmediately
        } else {
            commitmentLockRepository?.stageLimitIncrease(packageName, limitMs)
            currentNotice = "Limit increase will take effect at next midnight (12:00 AM) to protect your commitment."
            updateSuccessState()
            LimitChangeResult.DeferredToMidnight(limitMs)
        }
    }

    /**
     * Explicitly cancels a previously staged application removal.
     */
    fun cancelStagedRemoval(packageName: String): Boolean {
        val cancelled = commitmentLockRepository?.cancelStagedAppRemoval(packageName) ?: false
        if (cancelled) {
            currentNotice = "Cancelled scheduled removal for this app."
        }
        updateSuccessState()
        return cancelled
    }

    /**
     * Explicitly cancels a previously staged daily limit increase.
     */
    fun cancelStagedLimitIncrease(packageName: String): Boolean {
        val cancelled = commitmentLockRepository?.cancelStagedLimitIncrease(packageName) ?: false
        if (cancelled) {
            currentNotice = "Cancelled scheduled limit change for this app."
        }
        updateSuccessState()
        return cancelled
    }

    /**
     * Clears any active banner notice.
     */
    fun clearNotice() {
        currentNotice = null
        updateSuccessState()
    }

    private fun updateSuccessState() {
        if (rawDiscoveredApps.isEmpty()) {
            _uiState.value = LimitsUiState.Empty()
            return
        }

        val selectedSet = selectedAppsRepository.getSelectedPackages()
        val commitmentState = commitmentLockRepository?.commitmentState?.value ?: CommitmentLockState()
        val isLocked = commitmentState.isLocked
        val pendingRemovals = commitmentState.pendingRemovedPackages
        val pendingLimits = commitmentState.pendingIncreasedLimits

        val allItems = rawDiscoveredApps.map { app ->
            val isSelected = selectedSet.contains(app.packageName)
            val usedMs = todayUsageMap[app.packageName] ?: 0L
            val limitMs = dailyLimitRepository.getLimitForPackage(app.packageName)
            val evaluation = usageRuleEngine.evaluate(
                packageName = app.packageName,
                usedDurationMs = usedMs,
                limitDurationMs = limitMs
            )
            val isPendingRemoval = isLocked && pendingRemovals.contains(app.packageName)
            val pendingLimitMs = if (isLocked) pendingLimits[app.packageName] else null
            val formattedPending = pendingLimitMs?.let { UsageFormatter.formatDuration(it) }

            LimitsAppItem(
                packageName = app.packageName,
                appLabel = app.appLabel,
                icon = app.icon,
                isSelected = isSelected,
                totalTimeInForegroundMs = evaluation.usedDurationMs,
                formattedUsage = UsageFormatter.formatDuration(evaluation.usedDurationMs),
                limitDurationMs = evaluation.limitDurationMs,
                formattedLimit = UsageFormatter.formatDuration(evaluation.limitDurationMs),
                remainingDurationMs = evaluation.remainingDurationMs,
                formattedRemaining = UsageFormatter.formatDuration(evaluation.remainingDurationMs),
                limitState = evaluation.limitState,
                isPendingRemoval = isPendingRemoval,
                pendingLimitIncreaseMs = pendingLimitMs,
                formattedPendingLimit = formattedPending,
                category = app.category
            )
        }

        // 1. Filter by category
        val categoryFiltered = when (activeFilter) {
            LimitsFilter.ALL -> allItems
            LimitsFilter.CONTROLLED -> allItems.filter { it.isSelected }
            LimitsFilter.SOCIAL_VIDEO -> allItems.filter { it.isSocialOrVideo }
        }

        // 2. Filter by search query (case-insensitive on label or package name)
        val trimmedQuery = currentSearchQuery.trim()
        val finalFiltered = if (trimmedQuery.isEmpty()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { item ->
                item.appLabel.contains(trimmedQuery, ignoreCase = true) ||
                    item.packageName.contains(trimmedQuery, ignoreCase = true)
            }
        }

        _uiState.value = LimitsUiState.Success(
            apps = finalFiltered,
            searchQuery = currentSearchQuery,
            activeFilter = activeFilter,
            isCommitmentLocked = isLocked,
            commitmentState = commitmentState,
            lockedNotice = currentNotice,
            selectedCount = selectedSet.size,
            totalDiscoveredCount = rawDiscoveredApps.size
        )
    }
}
