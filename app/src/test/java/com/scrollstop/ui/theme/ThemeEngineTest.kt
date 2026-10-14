package com.scrollstop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeEngineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `light color tokens exactly match UI_SPEC approved Slate and Emerald palette`() {
        assertEquals(Color(0xFFF8FAFC), LightBackground)
        assertEquals(Color(0xFFFFFFFF), LightSurface)
        assertEquals(Color(0xFFF1F5F9), LightSurfaceVariant)
        assertEquals(Color(0xFFE2E8F0), LightOutline)
        assertEquals(Color(0xFF059669), LightPrimary)
        assertEquals(Color(0xFFFFFFFF), LightOnPrimary)
        assertEquals(Color(0xFF475569), LightSecondary)
        assertEquals(Color(0xFF0F172A), LightTextPrimary)
        assertEquals(Color(0xFF2563EB), LightCommitment)
        assertEquals(Color(0xFFD97706), LightWarning)
        assertEquals(Color(0xFFDC2626), LightError)
        assertEquals(Color(0xFFFEE2E2), LightErrorContainer)
        assertEquals(Color(0xFF991B1B), LightOnErrorContainer)
    }

    @Test
    fun `dark color tokens exactly match UI_SPEC approved Slate and Emerald palette`() {
        assertEquals(Color(0xFF0B0F19), DarkBackground)
        assertEquals(Color(0xFF111827), DarkSurface)
        assertEquals(Color(0xFF1E293B), DarkSurfaceVariant)
        assertEquals(Color(0xFF334155), DarkOutline)
        assertEquals(Color(0xFF10B981), DarkPrimary)
        assertEquals(Color(0xFF022C22), DarkOnPrimary)
        assertEquals(Color(0xFF94A3B8), DarkSecondary)
        assertEquals(Color(0xFFF8FAFC), DarkTextPrimary)
        assertEquals(Color(0xFF3B82F6), DarkCommitment)
        assertEquals(Color(0xFFF59E0B), DarkWarning)
        assertEquals(Color(0xFFEF4444), DarkError)
        assertEquals(Color(0xFF450A0A), DarkErrorContainer)
        assertEquals(Color(0xFFFCA5A5), DarkOnErrorContainer)
    }

    @Test
    fun `light color scheme maps approved tokens to Material 3 slots`() {
        assertEquals(LightPrimary, LightColorScheme.primary)
        assertEquals(LightOnPrimary, LightColorScheme.onPrimary)
        assertEquals(LightSecondary, LightColorScheme.secondary)
        assertEquals(LightCommitment, LightColorScheme.tertiary)
        assertEquals(LightBackground, LightColorScheme.background)
        assertEquals(LightTextPrimary, LightColorScheme.onBackground)
        assertEquals(LightSurface, LightColorScheme.surface)
        assertEquals(LightTextPrimary, LightColorScheme.onSurface)
        assertEquals(LightSurfaceVariant, LightColorScheme.surfaceVariant)
        assertEquals(LightSecondary, LightColorScheme.onSurfaceVariant)
        assertEquals(LightOutline, LightColorScheme.outline)
        assertEquals(LightError, LightColorScheme.error)
        assertEquals(LightErrorContainer, LightColorScheme.errorContainer)
        assertEquals(LightOnErrorContainer, LightColorScheme.onErrorContainer)
    }

    @Test
    fun `dark color scheme maps approved tokens to Material 3 slots`() {
        assertEquals(DarkPrimary, DarkColorScheme.primary)
        assertEquals(DarkOnPrimary, DarkColorScheme.onPrimary)
        assertEquals(DarkSecondary, DarkColorScheme.secondary)
        assertEquals(DarkCommitment, DarkColorScheme.tertiary)
        assertEquals(DarkBackground, DarkColorScheme.background)
        assertEquals(DarkTextPrimary, DarkColorScheme.onBackground)
        assertEquals(DarkSurface, DarkColorScheme.surface)
        assertEquals(DarkTextPrimary, DarkColorScheme.onSurface)
        assertEquals(DarkSurfaceVariant, DarkColorScheme.surfaceVariant)
        assertEquals(DarkSecondary, DarkColorScheme.onSurfaceVariant)
        assertEquals(DarkOutline, DarkColorScheme.outline)
        assertEquals(DarkError, DarkColorScheme.error)
        assertEquals(DarkErrorContainer, DarkColorScheme.errorContainer)
        assertEquals(DarkOnErrorContainer, DarkColorScheme.onErrorContainer)
    }

    @Test
    fun `extended colors provide domain semantic tokens for light and dark`() {
        assertEquals(LightCommitment, LightExtendedColors.commitment)
        assertEquals(Color.White, LightExtendedColors.onCommitment)
        assertEquals(LightWarning, LightExtendedColors.warning)

        assertEquals(DarkCommitment, DarkExtendedColors.commitment)
        assertEquals(Slate950, DarkExtendedColors.onCommitment)
        assertEquals(DarkWarning, DarkExtendedColors.warning)
    }

    @Test
    fun `StopDoomScrollTheme with LIGHT mode provides light color scheme and light extended colors`() {
        var observedColorScheme: ColorScheme? = null
        var observedExtendedColors: ExtendedColors? = null

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
                observedColorScheme = MaterialTheme.colorScheme
                observedExtendedColors = MaterialTheme.extendedColors
            }
        }

        assertEquals(LightPrimary, observedColorScheme?.primary)
        assertEquals(LightBackground, observedColorScheme?.background)
        assertEquals(LightSurface, observedColorScheme?.surface)
        assertEquals(LightCommitment, observedExtendedColors?.commitment)
        assertEquals(LightWarning, observedExtendedColors?.warning)
    }

    @Test
    fun `StopDoomScrollTheme with DARK mode provides dark color scheme and dark extended colors`() {
        var observedColorScheme: ColorScheme? = null
        var observedExtendedColors: ExtendedColors? = null

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
                observedColorScheme = MaterialTheme.colorScheme
                observedExtendedColors = MaterialTheme.extendedColors
            }
        }

        assertEquals(DarkPrimary, observedColorScheme?.primary)
        assertEquals(DarkBackground, observedColorScheme?.background)
        assertEquals(DarkSurface, observedColorScheme?.surface)
        assertEquals(DarkCommitment, observedExtendedColors?.commitment)
        assertEquals(DarkWarning, observedExtendedColors?.warning)
    }

    @Test
    fun `StopDoomScrollTheme with SYSTEM mode and darkTheme false provides light color scheme`() {
        var lightModeScheme: ColorScheme? = null

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.SYSTEM, darkTheme = false) {
                lightModeScheme = MaterialTheme.colorScheme
            }
        }
        assertEquals(LightPrimary, lightModeScheme?.primary)
        assertEquals(LightBackground, lightModeScheme?.background)
    }

    @Test
    fun `StopDoomScrollTheme with SYSTEM mode and darkTheme true provides dark color scheme`() {
        var darkModeScheme: ColorScheme? = null

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.SYSTEM, darkTheme = true) {
                darkModeScheme = MaterialTheme.colorScheme
            }
        }
        assertEquals(DarkPrimary, darkModeScheme?.primary)
        assertEquals(DarkBackground, darkModeScheme?.background)
    }
}
