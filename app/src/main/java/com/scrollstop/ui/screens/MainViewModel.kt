package com.scrollstop.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollstop.data.repository.ProtectionHealthRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.domain.model.ProtectionHealthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * UI State for the Main Dashboard screen.
 */
data class MainUiState(
    val healthState: ProtectionHealthState = ProtectionHealthState.DEGRADED,
    val selectedAppCount: Int = 0,
    val commitmentState: com.scrollstop.domain.model.CommitmentLockState = com.scrollstop.domain.model.CommitmentLockState()
)

/**
 * ViewModel managing Main Dashboard state, protection health reporting, and recovery flows.
 */
class MainViewModel(
    private val protectionHealthRepository: ProtectionHealthRepository,
    private val selectedAppsRepository: SelectedAppsRepository,
    private val commitmentLockRepository: com.scrollstop.data.repository.CommitmentLockRepository? = null
) : ViewModel() {

    private val fallbackCommitmentFlow = kotlinx.coroutines.flow.MutableStateFlow(com.scrollstop.domain.model.CommitmentLockState())

    val uiState: StateFlow<MainUiState> = combine(
        protectionHealthRepository.healthState,
        selectedAppsRepository.selectedPackageNames,
        commitmentLockRepository?.commitmentState ?: fallbackCommitmentFlow
    ) { health, selectedApps, commitment ->
        MainUiState(
            healthState = health,
            selectedAppCount = selectedApps.size,
            commitmentState = commitment
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState(
            healthState = protectionHealthRepository.healthState.value,
            selectedAppCount = selectedAppsRepository.getSelectedPackages().size,
            commitmentState = commitmentLockRepository?.commitmentState?.value ?: com.scrollstop.domain.model.CommitmentLockState()
        )
    )

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

    fun refreshHealth(context: Context) {
        commitmentLockRepository?.checkAndApplyMidnightTransitions()
        protectionHealthRepository.refreshHealth(context)
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
