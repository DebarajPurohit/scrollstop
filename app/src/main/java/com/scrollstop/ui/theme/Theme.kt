package com.scrollstop.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Extended semantic color tokens that augment Material 3 [ColorScheme]
 * with domain-specific tokens defined in UI_SPEC.md (Commitment Lock, Warning, etc.).
 */
@Immutable
data class ExtendedColors(
    val commitment: Color,
    val onCommitment: Color,
    val commitmentContainer: Color,
    val onCommitmentContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

val LightExtendedColors = ExtendedColors(
    commitment = LightCommitment,
    onCommitment = Color.White,
    commitmentContainer = Blue100,
    onCommitmentContainer = Blue950,
    warning = LightWarning,
    onWarning = Color.White,
    warningContainer = Amber100,
    onWarningContainer = Amber950
)

val DarkExtendedColors = ExtendedColors(
    commitment = DarkCommitment,
    onCommitment = Slate950,
    commitmentContainer = Blue950,
    onCommitmentContainer = Blue100,
    warning = DarkWarning,
    onWarning = Slate950,
    warningContainer = Amber950,
    onWarningContainer = Amber100
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * Direct accessor to [ExtendedColors] from [MaterialTheme].
 */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

/**
 * Convenience accessor for commitment lock color.
 */
val ColorScheme.commitment: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current.commitment

/**
 * Convenience accessor for warning state color.
 */
val ColorScheme.warning: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current.warning

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = LightTextPrimary,
    tertiary = DarkCommitment,
    onTertiary = LightTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondary,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnPrimary,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    onSecondary = Color.White,
    tertiary = LightCommitment,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondary,
    outline = LightOutline,
    error = LightError,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

/**
 * Main application theme wrapper for Stop Doom Scroll.
 *
 * - Defaults to [ThemeMode.SYSTEM] (following device setting).
 * - Disables dynamic wallpaper coloring by default to preserve the brand Slate/Emerald palette.
 * - Supports explicit [ThemeMode.LIGHT] and [ThemeMode.DARK] overrides.
 */
@Composable
fun StopDoomScrollTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = if (themeMode != ThemeMode.SYSTEM) {
        themeMode == ThemeMode.DARK
    } else {
        darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (isDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// =========================================================================
// Visual Verification Preview Components
// =========================================================================

@Composable
private fun ThemePalettePreviewContent(themeLabel: String) {
    val extended = MaterialTheme.extendedColors
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Stop Doom Scroll — $themeLabel",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Semantic Slate/Emerald Palette Verification",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Primary Action Button
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Primary Action (Emerald)")
            }

            // Surface Card with SurfaceVariant
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Surface Container",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Secondary text on surface",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Semantic Status Cards (Commitment, Warning, Error)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(extended.commitmentContainer, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Commitment",
                        color = extended.onCommitmentContainer,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(extended.warningContainer, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Warning",
                        color = extended.onWarningContainer,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Error Alert",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Preview(name = "Light Theme Preview", showBackground = true)
@Composable
fun LightThemePreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
        ThemePalettePreviewContent(themeLabel = "Light Theme")
    }
}

@Preview(name = "Dark Theme Preview", showBackground = true)
@Composable
fun DarkThemePreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
        ThemePalettePreviewContent(themeLabel = "Dark Theme")
    }
}
