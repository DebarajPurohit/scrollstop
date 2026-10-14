package com.scrollstop.data.repository

import com.scrollstop.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hermetic in-memory implementation of [ThemeRepository] for testing and preview environments.
 */
class InMemoryThemeRepository(
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM
) : ThemeRepository {

    private val _themeModeFlow = MutableStateFlow(initialThemeMode)
    override val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    override fun getThemeMode(): ThemeMode = _themeModeFlow.value

    override fun setThemeMode(themeMode: ThemeMode) {
        _themeModeFlow.value = themeMode
    }
}
