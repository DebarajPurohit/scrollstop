package com.scrollstop.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SharedPreferences]-backed persistent implementation of [DailyLimitRepository].
 *
 * Persists user-configured daily limits (defaulting to temporary 2-minute POC limit = 120,000ms).
 */
class SharedPreferencesDailyLimitRepository(
    context: Context,
    private val defaultLimitMs: Long = InMemoryDailyLimitRepository.DEFAULT_POC_LIMIT_MS,
    prefName: String = PREF_NAME
) : DailyLimitRepository {

    companion object {
        const val PREF_NAME = "scrollstop_daily_limit_prefs"
        private const val KEY_LIMIT_PREFIX = "limit_ms_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    private val _limits = MutableStateFlow<Map<String, Long>>(readFromPrefs())
    override val limitsFlow: StateFlow<Map<String, Long>> = _limits.asStateFlow()

    private fun readFromPrefs(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_LIMIT_PREFIX) && value is Long) {
                val pkg = key.removePrefix(KEY_LIMIT_PREFIX)
                result[pkg] = value
            }
        }
        return result
    }

    override fun getLimitForPackage(packageName: String): Long {
        val stored = _limits.value[packageName]
        if (stored != null) return stored

        val prefKey = KEY_LIMIT_PREFIX + packageName
        if (prefs.contains(prefKey)) {
            return prefs.getLong(prefKey, defaultLimitMs)
        }
        return defaultLimitMs
    }

    override fun setLimitForPackage(packageName: String, limitMs: Long) {
        val current = _limits.value.toMutableMap()
        current[packageName] = limitMs
        prefs.edit().putLong(KEY_LIMIT_PREFIX + packageName, limitMs).apply()
        _limits.value = current
    }

    override fun getAllLimits(): Map<String, Long> {
        return _limits.value
    }
}
