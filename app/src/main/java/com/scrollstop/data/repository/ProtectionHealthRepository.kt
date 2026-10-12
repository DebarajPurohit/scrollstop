package com.scrollstop.data.repository

import android.content.Context
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.service.RestrictedAppAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Authoritative repository providing real-time evaluation and reporting of overall Protection Health.
 *
 * Integrates both Usage Access capability and Accessibility Service state into a single,
 * observable source of truth for the app's enforcement readiness.
 */
interface ProtectionHealthRepository {
    val healthState: StateFlow<ProtectionHealthState>
    fun refreshHealth(context: Context)
    fun calculateCurrentHealth(hasUsagePermission: Boolean, accessibilityState: AccessibilityServiceState): ProtectionHealthState
}

/**
 * Android implementation of [ProtectionHealthRepository].
 */
class AndroidProtectionHealthRepository(
    private val usageStatsRepository: UsageStatsRepository,
    private val accessibilityHealthRepository: AccessibilityServiceHealthRepository,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : ProtectionHealthRepository {

    private val _healthState = MutableStateFlow(
        calculateCurrentHealth(
            usageStatsRepository.hasUsagePermission(),
            accessibilityHealthRepository.serviceState.value
        )
    )
    override val healthState: StateFlow<ProtectionHealthState> = _healthState.asStateFlow()

    init {
        // Automatically re-evaluate health whenever accessibility service state emits a change
        coroutineScope.launch {
            accessibilityHealthRepository.serviceState.collectLatest { accState ->
                val hasUsage = usageStatsRepository.hasUsagePermission()
                _healthState.value = calculateCurrentHealth(hasUsage, accState)
            }
        }
    }

    override fun calculateCurrentHealth(
        hasUsagePermission: Boolean,
        accessibilityState: AccessibilityServiceState
    ): ProtectionHealthState {
        return when {
            hasUsagePermission && accessibilityState == AccessibilityServiceState.ACCESSIBILITY_ACTIVE ->
                ProtectionHealthState.PROTECTED

            !hasUsagePermission && accessibilityState != AccessibilityServiceState.ACCESSIBILITY_ACTIVE ->
                ProtectionHealthState.DEGRADED

            !hasUsagePermission ->
                ProtectionHealthState.USAGE_ACCESS_REQUIRED

            accessibilityState == AccessibilityServiceState.ACCESSIBILITY_INTERRUPTED ->
                ProtectionHealthState.ACCESSIBILITY_INTERRUPTED

            else ->
                ProtectionHealthState.ACCESSIBILITY_REQUIRED
        }
    }

    override fun refreshHealth(context: Context) {
        if (accessibilityHealthRepository.serviceState.value != AccessibilityServiceState.ACCESSIBILITY_ACTIVE) {
            accessibilityHealthRepository.refreshState(context, RestrictedAppAccessibilityService::class.java)
        }
        val hasUsage = usageStatsRepository.hasUsagePermission()
        val accState = accessibilityHealthRepository.serviceState.value
        _healthState.value = calculateCurrentHealth(hasUsage, accState)
    }
}
