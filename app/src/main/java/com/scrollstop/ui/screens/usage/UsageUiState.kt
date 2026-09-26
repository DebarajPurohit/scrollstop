package com.scrollstop.ui.screens.usage

import com.scrollstop.domain.model.AppUsageInfo

/**
 * UI State for the Today's Usage Tracking Screen.
 */
sealed interface UsageUiState {
    /** Permission is not granted. User must be prompted to grant Usage Access in Settings. */
    data object PermissionRequired : UsageUiState

    /** Usage tracking calculation in progress. */
    data object Loading : UsageUiState

    /** No applications selected for tracking. */
    data object EmptySelection : UsageUiState

    /**
     * Usage data successfully loaded for selected applications.
     * @property usageList List of [AppUsageInfo] for selected apps.
     */
    data class Success(
        val usageList: List<AppUsageInfo> = emptyList()
    ) : UsageUiState

    /**
     * State representing failure during usage stats query.
     * @property errorMessage User-facing explanation of the failure.
     */
    data class Error(
        val errorMessage: String
    ) : UsageUiState
}
