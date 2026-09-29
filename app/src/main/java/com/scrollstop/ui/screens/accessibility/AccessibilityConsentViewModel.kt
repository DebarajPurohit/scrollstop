package com.scrollstop.ui.screens.accessibility

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollstop.data.repository.AccessibilityServiceHealthRepository
import com.scrollstop.data.repository.EnforcementTriggerRepository
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.service.RestrictedAppAccessibilityService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AccessibilityConsentUiState(
    val serviceState: AccessibilityServiceState = AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED,
    val latestTrigger: EnforcementTrigger? = null
)

class AccessibilityConsentViewModel(
    private val healthRepository: AccessibilityServiceHealthRepository,
    private val triggerRepository: EnforcementTriggerRepository
) : ViewModel() {

    val uiState: StateFlow<AccessibilityConsentUiState> = combine(
        healthRepository.serviceState,
        triggerRepository.latestTrigger
    ) { healthState, trigger ->
        AccessibilityConsentUiState(
            serviceState = healthState,
            latestTrigger = trigger
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccessibilityConsentUiState()
    )

    fun checkServiceHealth(context: Context) {
        healthRepository.refreshState(context, RestrictedAppAccessibilityService::class.java)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings if accessibility settings fails
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
