package com.scrollstop.data.repository

import com.scrollstop.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for managing and observing the active [ThemeMode] preference.
 */
interface ThemeRepository {
    /**
     * Observable [StateFlow] emitting the currently configured [ThemeMode].
     */
    val themeModeFlow: StateFlow<ThemeMode>

    /**
     * Retrieves the currently persisted [ThemeMode].
     */
    fun getThemeMode(): ThemeMode

    /**
     * Updates and persists the [ThemeMode] preference.
     */
    fun setThemeMode(themeMode: ThemeMode)
}
