package com.scrollstop.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.scrollstop.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SharedPreferences]-backed persistent implementation of [ThemeRepository].
 *
 * Persists the user's manual theme preference across activity lifecycles, app restarts,
 * and device reboots. Defaults to [ThemeMode.SYSTEM].
 */
class SharedPreferencesThemeRepository(
    context: Context,
    prefName: String = PREF_NAME
) : ThemeRepository {

    companion object {
        const val PREF_NAME = "scrollstop_theme_prefs"
        const val KEY_THEME_MODE = "theme_mode"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    private val _themeModeFlow = MutableStateFlow(readFromPrefs())
    override val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private fun readFromPrefs(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    override fun getThemeMode(): ThemeMode {
        return _themeModeFlow.value
    }

    override fun setThemeMode(themeMode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, themeMode.name).apply()
        _themeModeFlow.value = themeMode
    }
}
