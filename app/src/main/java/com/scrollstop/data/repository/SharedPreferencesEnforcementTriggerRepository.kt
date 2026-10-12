package com.scrollstop.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.scrollstop.data.time.SystemTimeProvider
import com.scrollstop.data.time.TimeProvider
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SharedPreferences]-backed persistent implementation of [EnforcementTriggerRepository].
 *
 * Ensures emitted [EnforcementTrigger] state and diagnostic event trace persist across
 * background service events, process restarts, activity backgrounding, and screen recreation.
 *
 * Midnight Boundary Handling:
 * Evaluates triggers against [timeProvider.getStartOfDayMillis()]. If a persisted trigger was emitted
 * on a previous calendar day, it is automatically cleared to respect the daily midnight limit reset.
 */
class SharedPreferencesEnforcementTriggerRepository(
    context: Context,
    prefName: String = PREF_NAME,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) : EnforcementTriggerRepository {

    companion object {
        const val PREF_NAME = "scrollstop_enforcement_trigger_prefs"
        private const val KEY_PACKAGE_NAME = "trigger_package_name"
        private const val KEY_REASON = "trigger_reason"
        private const val KEY_TIMESTAMP_MS = "trigger_timestamp_ms"
        private const val KEY_WALL_CLOCK_TIME_MS = "trigger_wall_clock_time_ms"

        private const val KEY_DIAG_EVENT_PKG = "diag_event_pkg"
        private const val KEY_DIAG_DETECTION_PKG = "diag_detection_pkg"
        private const val KEY_DIAG_DETECTION_SELECTED = "diag_detection_selected"
        private const val KEY_DIAG_RULE_PKG = "diag_rule_pkg"
        private const val KEY_DIAG_RULE_STATE = "diag_rule_state"
        private const val KEY_DIAG_RULE_USED_MS = "diag_rule_used_ms"
        private const val KEY_DIAG_RULE_LIMIT_MS = "diag_rule_limit_ms"

        private const val KEY_DIAG_MONITOR_TIMESTAMP = "diag_monitor_timestamp"
        private const val KEY_DIAG_MONITOR_PKG = "diag_monitor_pkg"
        private const val KEY_DIAG_MONITOR_SELECTED = "diag_monitor_selected"
        private const val KEY_DIAG_MONITOR_ACTIVE = "diag_monitor_active"
        private const val KEY_DIAG_MONITOR_USAGE_MS = "diag_monitor_usage_ms"
        private const val KEY_DIAG_MONITOR_LIMIT_MS = "diag_monitor_limit_ms"
        private const val KEY_DIAG_MONITOR_RULE_STATE = "diag_monitor_rule_state"
        private const val KEY_DIAG_MONITOR_TRIGGER_EMITTED = "diag_monitor_trigger_emitted"
        private const val KEY_DIAG_MONITOR_LAUNCH_ATTEMPTED = "diag_monitor_launch_attempted"

        private const val TAG = "ScrollStopDebug"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    private val _triggerFlow = MutableSharedFlow<EnforcementTrigger>(replay = 1)
    override val triggerFlow: SharedFlow<EnforcementTrigger> = _triggerFlow.asSharedFlow()

    private val _latestTrigger = MutableStateFlow<EnforcementTrigger?>(readTriggerFromPrefs())
    override val latestTrigger: StateFlow<EnforcementTrigger?> = _latestTrigger.asStateFlow()

    private val _diagnosticInfo = MutableStateFlow<DiagnosticInfo>(readDiagnosticFromPrefs())
    override val diagnosticInfo: StateFlow<DiagnosticInfo> = _diagnosticInfo.asStateFlow()

    init {
        _latestTrigger.value?.let { trigger ->
            _triggerFlow.tryEmit(trigger)
        }
    }

    private fun readTriggerFromPrefs(): EnforcementTrigger? {
        val packageName = prefs.getString(KEY_PACKAGE_NAME, null) ?: return null
        if (packageName.isBlank()) return null

        val wallClockTime = prefs.getLong(KEY_WALL_CLOCK_TIME_MS, 0L)
        // If the trigger was recorded prior to start of today, it has expired on midnight boundary
        if (wallClockTime > 0L && wallClockTime < timeProvider.getStartOfDayMillis()) {
            prefs.edit()
                .remove(KEY_PACKAGE_NAME)
                .remove(KEY_REASON)
                .remove(KEY_TIMESTAMP_MS)
                .remove(KEY_WALL_CLOCK_TIME_MS)
                .apply()
            return null
        }

        val reasonStr = prefs.getString(KEY_REASON, LimitState.LIMIT_REACHED.name)
        val reason = try {
            LimitState.valueOf(reasonStr ?: LimitState.LIMIT_REACHED.name)
        } catch (e: Exception) {
            LimitState.LIMIT_REACHED
        }

        val timestamp = prefs.getLong(KEY_TIMESTAMP_MS, 0L)

        return EnforcementTrigger(
            packageName = packageName,
            reason = reason,
            detectedAtElapsedRealtimeMs = timestamp
        )
    }

    private fun readDiagnosticFromPrefs(): DiagnosticInfo {
        val eventPkg = prefs.getString(KEY_DIAG_EVENT_PKG, null)
        val detectionPkg = prefs.getString(KEY_DIAG_DETECTION_PKG, null)
        val detectionSelected = if (prefs.contains(KEY_DIAG_DETECTION_SELECTED)) {
            prefs.getBoolean(KEY_DIAG_DETECTION_SELECTED, false)
        } else null

        val rulePkg = prefs.getString(KEY_DIAG_RULE_PKG, null)
        val ruleStateStr = prefs.getString(KEY_DIAG_RULE_STATE, null)
        val ruleState = if (ruleStateStr != null) {
            try { LimitState.valueOf(ruleStateStr) } catch (e: Exception) { null }
        } else null

        val usedMs = prefs.getLong(KEY_DIAG_RULE_USED_MS, 0L)
        val limitMs = prefs.getLong(KEY_DIAG_RULE_LIMIT_MS, 0L)

        val monTimestamp = prefs.getLong(KEY_DIAG_MONITOR_TIMESTAMP, 0L)
        val monPkg = prefs.getString(KEY_DIAG_MONITOR_PKG, null)
        val monSelected = if (prefs.contains(KEY_DIAG_MONITOR_SELECTED)) {
            prefs.getBoolean(KEY_DIAG_MONITOR_SELECTED, false)
        } else null
        val monActive = prefs.getBoolean(KEY_DIAG_MONITOR_ACTIVE, false)
        val monUsageMs = prefs.getLong(KEY_DIAG_MONITOR_USAGE_MS, 0L)
        val monLimitMs = prefs.getLong(KEY_DIAG_MONITOR_LIMIT_MS, 0L)
        val monRuleStateStr = prefs.getString(KEY_DIAG_MONITOR_RULE_STATE, null)
        val monRuleState = if (monRuleStateStr != null) {
            try { LimitState.valueOf(monRuleStateStr) } catch (e: Exception) { null }
        } else null
        val monTriggerEmitted = prefs.getBoolean(KEY_DIAG_MONITOR_TRIGGER_EMITTED, false)
        val monLaunchAttempted = prefs.getBoolean(KEY_DIAG_MONITOR_LAUNCH_ATTEMPTED, false)

        return DiagnosticInfo(
            lastEventPackage = eventPkg,
            lastDetectionPackage = detectionPkg,
            lastDetectionSelected = detectionSelected,
            lastRulePackage = rulePkg,
            lastRuleState = ruleState,
            lastRuleUsedMs = usedMs,
            lastRuleLimitMs = limitMs,
            lastMonitoringTimestamp = monTimestamp,
            lastMonitoringPackage = monPkg,
            lastMonitoringSelected = monSelected,
            lastMonitoringActive = monActive,
            lastMonitoringUsageMs = monUsageMs,
            lastMonitoringLimitMs = monLimitMs,
            lastMonitoringRuleState = monRuleState,
            lastMonitoringTriggerEmitted = monTriggerEmitted,
            lastMonitoringLaunchAttempted = monLaunchAttempted
        )
    }

    override fun emitTrigger(trigger: EnforcementTrigger) {
        val nowWallClock = timeProvider.currentTimeMillis()
        prefs.edit()
            .putString(KEY_PACKAGE_NAME, trigger.packageName)
            .putString(KEY_REASON, trigger.reason.name)
            .putLong(KEY_TIMESTAMP_MS, trigger.detectedAtElapsedRealtimeMs)
            .putLong(KEY_WALL_CLOCK_TIME_MS, nowWallClock)
            .apply()

        safeLogD(TAG, "TRIGGER_PERSISTED package=${trigger.packageName} wallClock=$nowWallClock")

        _latestTrigger.value = trigger
        _triggerFlow.tryEmit(trigger)
    }

    override fun clearLatestTrigger() {
        prefs.edit()
            .remove(KEY_PACKAGE_NAME)
            .remove(KEY_REASON)
            .remove(KEY_TIMESTAMP_MS)
            .remove(KEY_WALL_CLOCK_TIME_MS)
            .apply()

        safeLogD(TAG, "TRIGGER_CLEARED")

        _latestTrigger.value = null
    }

    override fun checkAndExpireStaleTriggers() {
        val wallClockTime = prefs.getLong(KEY_WALL_CLOCK_TIME_MS, 0L)
        if (wallClockTime > 0L && wallClockTime < timeProvider.getStartOfDayMillis()) {
            safeLogD(TAG, "TRIGGER_EXPIRED_MIDNIGHT wallClock=$wallClockTime midnight=${timeProvider.getStartOfDayMillis()}")
            clearLatestTrigger()
        }
    }

    override fun recordEvent(packageName: String) {
        prefs.edit().putString(KEY_DIAG_EVENT_PKG, packageName).apply()
        _diagnosticInfo.value = _diagnosticInfo.value.copy(lastEventPackage = packageName)
    }

    override fun recordDetection(packageName: String, isSelected: Boolean) {
        prefs.edit()
            .putString(KEY_DIAG_DETECTION_PKG, packageName)
            .putBoolean(KEY_DIAG_DETECTION_SELECTED, isSelected)
            .apply()
        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            lastDetectionPackage = packageName,
            lastDetectionSelected = isSelected
        )
    }

    override fun recordRuleEvaluation(packageName: String, usedMs: Long, limitMs: Long, state: LimitState) {
        prefs.edit()
            .putString(KEY_DIAG_RULE_PKG, packageName)
            .putString(KEY_DIAG_RULE_STATE, state.name)
            .putLong(KEY_DIAG_RULE_USED_MS, usedMs)
            .putLong(KEY_DIAG_RULE_LIMIT_MS, limitMs)
            .apply()

        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            lastRulePackage = packageName,
            lastRuleState = state,
            lastRuleUsedMs = usedMs,
            lastRuleLimitMs = limitMs
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
        prefs.edit()
            .putLong(KEY_DIAG_MONITOR_TIMESTAMP, timestamp)
            .putString(KEY_DIAG_MONITOR_PKG, packageName)
            .putBoolean(KEY_DIAG_MONITOR_SELECTED, isSelected)
            .putBoolean(KEY_DIAG_MONITOR_ACTIVE, isMonitoringActive)
            .putLong(KEY_DIAG_MONITOR_USAGE_MS, currentUsageMs)
            .putLong(KEY_DIAG_MONITOR_LIMIT_MS, configuredLimitMs)
            .putString(KEY_DIAG_MONITOR_RULE_STATE, ruleResult.name)
            .putBoolean(KEY_DIAG_MONITOR_TRIGGER_EMITTED, triggerEmitted)
            .putBoolean(KEY_DIAG_MONITOR_LAUNCH_ATTEMPTED, launchAttempted)
            .apply()

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

    private fun safeLogD(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (e: Throwable) {
            println("$tag: $message")
        }
    }
}
