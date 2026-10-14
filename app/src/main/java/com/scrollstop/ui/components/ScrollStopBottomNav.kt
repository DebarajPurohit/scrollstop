package com.scrollstop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode

/**
 * The four primary top-level navigation destinations specified in UI_SPEC.md Section 2.
 */
enum class BottomNavDestination(val label: String) {
    HOME("Home"),
    LIMITS("Limits"),
    HISTORY("History"),
    SETTINGS("Settings")
}

/**
 * Standardized persistent Bottom Navigation Bar for Stop Doom Scroll.
 *
 * Provides four distinct destinations (Home, Limits, History, Settings)
 * with accessible touch targets (>= 48dp), semantic theme colors, and
 * WCAG-compliant contrast.
 *
 * @param currentDestination Currently active [BottomNavDestination].
 * @param onDestinationSelected Callback triggered when a tab is selected.
 * @param modifier Custom modifier.
 */
@Composable
fun ScrollStopBottomNav(
    currentDestination: BottomNavDestination,
    onDestinationSelected: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline
            ),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        BottomNavDestination.entries.forEach { destination ->
            val isSelected = destination == currentDestination
            NavigationBarItem(
                selected = isSelected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    BottomNavIcon(
                        destination = destination,
                        isSelected = isSelected,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
                modifier = Modifier.semantics {
                    role = Role.Tab
                    selected = isSelected
                    contentDescription = "${destination.label} tab"
                }
            )
        }
    }
}

/**
 * Clean vector iconography for bottom navigation tabs.
 */
@Composable
private fun BottomNavIcon(
    destination: BottomNavDestination,
    isSelected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = if (isSelected) 2.2.dp.toPx() else 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        when (destination) {
            BottomNavDestination.HOME -> {
                // House outline
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.15f) // Roof peak
                    lineTo(w * 0.88f, h * 0.48f) // Right eaves
                    lineTo(w * 0.78f, h * 0.48f)
                    lineTo(w * 0.78f, h * 0.88f) // Right wall
                    lineTo(w * 0.22f, h * 0.88f) // Floor
                    lineTo(w * 0.22f, h * 0.48f) // Left wall
                    lineTo(w * 0.12f, h * 0.48f) // Left eaves
                    close()
                }
                drawPath(
                    path = path,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            BottomNavDestination.LIMITS -> {
                // Shield / Slider outline
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.85f, h * 0.25f)
                    lineTo(w * 0.85f, h * 0.55f)
                    cubicTo(w * 0.85f, h * 0.78f, w * 0.5f, h * 0.90f, w * 0.5f, h * 0.90f)
                    cubicTo(w * 0.5f, h * 0.90f, w * 0.15f, h * 0.78f, w * 0.15f, h * 0.55f)
                    lineTo(w * 0.15f, h * 0.25f)
                    close()
                }
                drawPath(
                    path = path,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Center horizontal notch
                drawLine(
                    color = tint,
                    start = Offset(w * 0.35f, h * 0.52f),
                    end = Offset(w * 0.65f, h * 0.52f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            BottomNavDestination.HISTORY -> {
                // 3-Bar Chart
                val barW = w * 0.18f
                // Bar 1 (left - low)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.16f, h * 0.52f),
                    size = Size(barW, h * 0.36f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                // Bar 2 (middle - tall)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.41f, h * 0.22f),
                    size = Size(barW, h * 0.66f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                // Bar 3 (right - medium)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.66f, h * 0.38f),
                    size = Size(barW, h * 0.50f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
            BottomNavDestination.SETTINGS -> {
                // Gear outline
                drawCircle(
                    color = tint,
                    radius = w * 0.26f,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
                drawCircle(
                    color = tint,
                    radius = w * 0.09f,
                    center = center,
                    style = Stroke(width = strokeWidth * 0.8f)
                )
                // 4 cog indicators (top, bottom, left, right)
                val spokeLen = w * 0.10f
                drawLine(
                    color = tint,
                    start = Offset(center.x, center.y - w * 0.26f),
                    end = Offset(center.x, center.y - w * 0.26f - spokeLen),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(center.x, center.y + w * 0.26f),
                    end = Offset(center.x, center.y + w * 0.26f + spokeLen),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(center.x - w * 0.26f, center.y),
                    end = Offset(center.x - w * 0.26f - spokeLen, center.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(center.x + w * 0.26f, center.y),
                    end = Offset(center.x + w * 0.26f + spokeLen, center.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// =========================================================================
// Previews
// =========================================================================

@Preview(name = "BottomNav Light Theme", showBackground = true)
@Composable
private fun ScrollStopBottomNavLightPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
        ScrollStopBottomNav(
            currentDestination = BottomNavDestination.HOME,
            onDestinationSelected = {}
        )
    }
}

@Preview(name = "BottomNav Dark Theme", showBackground = true)
@Composable
private fun ScrollStopBottomNavDarkPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
        ScrollStopBottomNav(
            currentDestination = BottomNavDestination.LIMITS,
            onDestinationSelected = {}
        )
    }
}
