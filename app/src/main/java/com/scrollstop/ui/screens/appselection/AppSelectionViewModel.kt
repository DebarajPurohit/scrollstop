package com.scrollstop.ui.screens.appselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.domain.model.DiscoveredApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing the discovery and selection state of applications.
 */
class AppSelectionViewModel(
    private val repository: AppDiscoveryRepository,
    private val selectedAppsRepository: SelectedAppsRepository = InMemorySelectedAppsRepository(),
    private val commitmentLockRepository: com.scrollstop.data.repository.CommitmentLockRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppSelectionUiState>(AppSelectionUiState.Loading)
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    private var rawDiscoveredApps = listOf<DiscoveredApp>()
    private var currentNotice: String? = null

    init {
        loadApps()
        observeCommitmentState()
    }

    private fun observeCommitmentState() {
        commitmentLockRepository?.let { repo ->
            viewModelScope.launch {
                repo.commitmentState.collect {
                    if (_uiState.value is AppSelectionUiState.Success) {
                        updateSuccessState()
                    }
                }
            }
        }
    }

    /**
     * Triggers discovery of installed launchable applications.
     */
    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = AppSelectionUiState.Loading
            repository.getDiscoveredApps()
                .onSuccess { apps ->
                    rawDiscoveredApps = apps
                    updateSuccessState()
                }
                .onFailure { exception ->
                    _uiState.value = AppSelectionUiState.Error(
                        errorMessage = exception.localizedMessage ?: "Unknown discovery error"
                    )
                }
        }
    }

    /**
     * Toggles selection status for a specific application package.
     * When Commitment Lock is active:
     * - Adding an app is allowed immediately (tightening).
     * - Removing an app is blocked immediately and deferred to next midnight reset.
     */
    fun toggleAppSelection(packageName: String) {
        val isCurrentlySelected = selectedAppsRepository.isSelected(packageName)
        val commitmentState = commitmentLockRepository?.commitmentState?.value
        val isLocked = commitmentState?.isLocked == true

        if (isCurrentlySelected && isLocked) {
            // Cannot unselect immediately. Toggle staged removal for next midnight.
            val isStaged = commitmentState?.pendingRemovedPackages?.contains(packageName) == true
            if (isStaged) {
                commitmentLockRepository?.cancelStagedAppRemoval(packageName)
                currentNotice = "Cancelled scheduled removal for this app."
            } else {
                commitmentLockRepository?.stageAppRemoval(packageName)
                currentNotice = "Commitment Lock is active. Removing protected apps will take effect at next midnight (12:00 AM)."
            }
        } else {
            selectedAppsRepository.toggleAppSelection(packageName)
            // If the app was staged for removal, cancel staged removal since it's now explicitly selected
            if (commitmentState?.pendingRemovedPackages?.contains(packageName) == true) {
                commitmentLockRepository?.cancelStagedAppRemoval(packageName)
            }
            currentNotice = null
        }
        updateSuccessState()
    }

    fun clearNotice() {
        currentNotice = null
        updateSuccessState()
    }

    private fun updateSuccessState() {
        val selectedSet = selectedAppsRepository.getSelectedPackages()
        val commitmentState = commitmentLockRepository?.commitmentState?.value
        val updatedList = rawDiscoveredApps.map { app ->
            app.copy(isSelected = selectedSet.contains(app.packageName))
        }
        _uiState.value = AppSelectionUiState.Success(
            apps = updatedList,
            selectedCount = selectedSet.size,
            isCommitmentLocked = commitmentState?.isLocked == true,
            lockedNotice = currentNotice,
            pendingRemovalPackages = commitmentState?.pendingRemovedPackages ?: emptySet()
        )
    }
}
