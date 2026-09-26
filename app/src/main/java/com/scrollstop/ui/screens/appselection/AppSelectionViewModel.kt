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
    private val selectedAppsRepository: SelectedAppsRepository = InMemorySelectedAppsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppSelectionUiState>(AppSelectionUiState.Loading)
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    private var rawDiscoveredApps = listOf<DiscoveredApp>()

    init {
        loadApps()
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
     */
    fun toggleAppSelection(packageName: String) {
        selectedAppsRepository.toggleAppSelection(packageName)
        updateSuccessState()
    }

    private fun updateSuccessState() {
        val selectedSet = selectedAppsRepository.getSelectedPackages()
        val updatedList = rawDiscoveredApps.map { app ->
            app.copy(isSelected = selectedSet.contains(app.packageName))
        }
        _uiState.value = AppSelectionUiState.Success(
            apps = updatedList,
            selectedCount = selectedSet.size
        )
    }
}
