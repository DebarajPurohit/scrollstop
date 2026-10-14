package com.scrollstop.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesThemeRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        context.getSharedPreferences("test_theme_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `default theme mode is SYSTEM when no preference is stored`() {
        val repo = SharedPreferencesThemeRepository(context, prefName = "test_theme_prefs")
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
        assertEquals(ThemeMode.SYSTEM, repo.themeModeFlow.value)
    }

    @Test
    fun `setting theme mode updates getter, flow, and persists to SharedPreferences`() {
        val repo = SharedPreferencesThemeRepository(context, prefName = "test_theme_prefs")

        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.getThemeMode())
        assertEquals(ThemeMode.DARK, repo.themeModeFlow.value)

        // Reload from prefs (simulating app restart)
        val reloadedRepo = SharedPreferencesThemeRepository(context, prefName = "test_theme_prefs")
        assertEquals(ThemeMode.DARK, reloadedRepo.getThemeMode())
        assertEquals(ThemeMode.DARK, reloadedRepo.themeModeFlow.value)

        // Switch to LIGHT
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.getThemeMode())
        assertEquals(ThemeMode.LIGHT, repo.themeModeFlow.value)

        val reloadedLightRepo = SharedPreferencesThemeRepository(context, prefName = "test_theme_prefs")
        assertEquals(ThemeMode.LIGHT, reloadedLightRepo.getThemeMode())
        assertEquals(ThemeMode.LIGHT, reloadedLightRepo.themeModeFlow.value)

        // Switch back to SYSTEM
        repo.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
        assertEquals(ThemeMode.SYSTEM, repo.themeModeFlow.value)

        val reloadedSystemRepo = SharedPreferencesThemeRepository(context, prefName = "test_theme_prefs")
        assertEquals(ThemeMode.SYSTEM, reloadedSystemRepo.getThemeMode())
        assertEquals(ThemeMode.SYSTEM, reloadedSystemRepo.themeModeFlow.value)
    }

    @Test
    fun `invalid or corrupted stored value falls back safely to SYSTEM`() {
        val prefs = context.getSharedPreferences("test_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(SharedPreferencesThemeRepository.KEY_THEME_MODE, "INVALID_THEME_VALUE").commit()

        val repo = SharedPreferencesThemeRepository(context, prefName = "test_theme_prefs")
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
        assertEquals(ThemeMode.SYSTEM, repo.themeModeFlow.value)
    }
}
