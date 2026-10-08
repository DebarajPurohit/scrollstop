package com.scrollstop.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
 * Ensures emitted [EnforcementTrigger] state persists across background service events,
 * process restarts, activity backgrounding, and screen recreation.
 */
class SharedPreferencesEnforcementTriggerRepository(
    context: Context,
    prefName: String = PREF_NAME
) : EnforcementTriggerRepository {

    companion object {
        const val PREF_NAME = "scrollstop_enforcement_trigger_prefs"
        private const val KEY_PACKAGE_NAME = "trigger_package_name"
        private const val KEY_REASON = "trigger_reason"
        private const val KEY_TIMESTAMP_MS = "trigger_timestamp_ms"
        private const val TAG = "ScrollStopDebug"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    private val _triggerFlow = MutableSharedFlow<EnforcementTrigger>(replay = 1)
    override val triggerFlow: SharedFlow<EnforcementTrigger> = _triggerFlow.asSharedFlow()

    private val _latestTrigger = MutableStateFlow<EnforcementTrigger?>(readFromPrefs())
    override val latestTrigger: StateFlow<EnforcementTrigger?> = _latestTrigger.asStateFlow()

    init {
        _latestTrigger.value?.let { trigger ->
            _triggerFlow.tryEmit(trigger)
        }
    }

    private fun readFromPrefs(): EnforcementTrigger? {
        val packageName = prefs.getString(KEY_PACKAGE_NAME, null) ?: return null
        if (packageName.isBlank()) return null

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

    override fun emitTrigger(trigger: EnforcementTrigger) {
        prefs.edit()
            .putString(KEY_PACKAGE_NAME, trigger.packageName)
            .putString(KEY_REASON, trigger.reason.name)
            .putLong(KEY_TIMESTAMP_MS, trigger.detectedAtElapsedRealtimeMs)
            .apply()

        Log.d(TAG, "EnforcementTrigger emitted and persisted for package: ${trigger.packageName}")

        _latestTrigger.value = trigger
        _triggerFlow.tryEmit(trigger)
    }

    override fun clearLatestTrigger() {
        prefs.edit()
            .remove(KEY_PACKAGE_NAME)
            .remove(KEY_REASON)
            .remove(KEY_TIMESTAMP_MS)
            .apply()

        Log.d(TAG, "EnforcementTrigger cleared from repository")

        _latestTrigger.value = null
    }
}
