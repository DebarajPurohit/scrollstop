package com.scrollstop.ui.screens.appselection

import com.scrollstop.domain.model.DiscoveredApp

/**
 * UI State for the App Selection Screen.
 */
sealed interface AppSelectionUiState {
    /** State representing asynchronous app discovery in progress. */
    data object Loading : AppSelectionUiState

    /**
     * State representing successful app discovery.
     * @property apps List of discovered apps with their current selection status.
     * @property selectedCount Total number of apps currently selected.
     */
    data class Success(
        val apps: List<DiscoveredApp> = emptyList(),
        val selectedCount: Int = apps.count { it.isSelected }
    ) : AppSelectionUiState

    /**
     * State representing a failure during app discovery.
     * @property errorMessage User-friendly message explaining the error.
     */
    data class Error(
        val errorMessage: String
    ) : AppSelectionUiState
}
