package com.scrollstop.data.repository

import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Diagnostic tracking model for development build live event chain tracing.
 */
data class DiagnosticInfo(
    val lastEventPackage: String? = null,
    val lastDetectionPackage: String? = null,
    val lastDetectionSelected: Boolean? = null,
    val lastRulePackage: String? = null,
    val lastRuleState: LimitState? = null,
    val lastRuleUsedMs: Long = 0L,
    val lastRuleLimitMs: Long = 0L,
    // Phase 3 Live Monitoring Cycle Diagnostics
    val lastMonitoringTimestamp: Long = 0L,
    val lastMonitoringPackage: String? = null,
    val lastMonitoringSelected: Boolean? = null,
    val lastMonitoringActive: Boolean = false,
    val lastMonitoringUsageMs: Long = 0L,
    val lastMonitoringLimitMs: Long = 0L,
    val lastMonitoringRuleState: LimitState? = null,
    val lastMonitoringTriggerEmitted: Boolean = false,
    val lastMonitoringLaunchAttempted: Boolean = false
)

/**
 * Repository interface for emitting, persisting, and observing internal [EnforcementTrigger] events
 * and end-to-end diagnostic tracing info.
 */
interface EnforcementTriggerRepository {
    val triggerFlow: SharedFlow<EnforcementTrigger>
    val latestTrigger: StateFlow<EnforcementTrigger?>
    val diagnosticInfo: StateFlow<DiagnosticInfo>

    fun emitTrigger(trigger: EnforcementTrigger)
    fun clearLatestTrigger()

    fun recordEvent(packageName: String)
    fun recordDetection(packageName: String, isSelected: Boolean)
    fun recordRuleEvaluation(packageName: String, usedMs: Long, limitMs: Long, state: LimitState)
    fun recordMonitoringCycle(
        timestamp: Long,
        packageName: String,
        isSelected: Boolean,
        isMonitoringActive: Boolean,
        currentUsageMs: Long,
        configuredLimitMs: Long,
        ruleResult: LimitState,
        triggerEmitted: Boolean,
        launchAttempted: Boolean
    )
}

/**
 * In-memory implementation of [EnforcementTriggerRepository] for unit testing.
 */
class InMemoryEnforcementTriggerRepository : EnforcementTriggerRepository {

    private val _triggerFlow = MutableSharedFlow<EnforcementTrigger>(replay = 1)
    override val triggerFlow: SharedFlow<EnforcementTrigger> = _triggerFlow.asSharedFlow()

    private val _latestTrigger = MutableStateFlow<EnforcementTrigger?>(null)
    override val latestTrigger: StateFlow<EnforcementTrigger?> = _latestTrigger.asStateFlow()

    private val _diagnosticInfo = MutableStateFlow(DiagnosticInfo())
    override val diagnosticInfo: StateFlow<DiagnosticInfo> = _diagnosticInfo.asStateFlow()

    override fun emitTrigger(trigger: EnforcementTrigger) {
        _latestTrigger.value = trigger
        _triggerFlow.tryEmit(trigger)
    }

    override fun clearLatestTrigger() {
        _latestTrigger.value = null
    }

    override fun recordEvent(packageName: String) {
        _diagnosticInfo.value = _diagnosticInfo.value.copy(lastEventPackage = packageName)
    }

    override fun recordDetection(packageName: String, isSelected: Boolean) {
        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            lastDetectionPackage = packageName,
            lastDetectionSelected = isSelected
        )
    }

    override fun recordRuleEvaluation(packageName: String, usedMs: Long, limitMs: Long, state: LimitState) {
        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            lastRulePackage = packageName,
            lastRuleUsedMs = usedMs,
            lastRuleLimitMs = limitMs,
            lastRuleState = state
        )
    }

    override fun recordMonitoringCycle(
        timestamp: Long,
        packageName: String,
        isSelected: Boolean,
        isMonitoringActive: Boolean,
        currentUsageMs: Long,
        configuredLimitMs: Long,
        ruleResult: LimitState,
        triggerEmitted: Boolean,
        launchAttempted: Boolean
    ) {
        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            lastMonitoringTimestamp = timestamp,
            lastMonitoringPackage = packageName,
            lastMonitoringSelected = isSelected,
            lastMonitoringActive = isMonitoringActive,
            lastMonitoringUsageMs = currentUsageMs,
            lastMonitoringLimitMs = configuredLimitMs,
            lastMonitoringRuleState = ruleResult,
            lastMonitoringTriggerEmitted = triggerEmitted,
            lastMonitoringLaunchAttempted = launchAttempted
        )
    }
}
