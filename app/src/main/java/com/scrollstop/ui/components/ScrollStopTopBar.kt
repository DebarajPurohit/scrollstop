package com.scrollstop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode

/**
 * Standardized premium minimalist Top App Bar for Stop Doom Scroll.
 *
 * Ensures WCAG accessibility compliance with >= 48dp touch targets and scalable typography.
 *
 * @param title Primary headline string.
 * @param subtitle Optional secondary descriptor below the headline.
 * @param onNavigateBack Optional back navigation callback. When non-null, renders a back chevron.
 * @param actions Optional trailing actions (e.g. settings icon, theme toggle, refresh).
 * @param modifier Custom modifier.
 * @param containerColor TopBar container background color (defaults to surface).
 * @param titleContentColor TopBar title text color (defaults to onSurface).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollStopTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleContentColor
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                // Minimum 48x48dp touch target
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(bounded = true, radius = 24.dp),
                            role = Role.Button,
                            onClick = onNavigateBack
                        )
                        .semantics {
                            contentDescription = "Navigate back"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BackChevronIcon(color = titleContentColor)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = titleContentColor
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Clean bespoke vector chevron icon for back navigation.
 */
@Composable
private fun BackChevronIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth = 2.5.dp.toPx()
        // Draw left-pointing chevron: from (13, 4) -> (6, 10) -> (13, 16)
        val w = size.width
        val h = size.height
        val startX = w * 0.65f
        val midX = w * 0.32f
        val topY = h * 0.22f
        val midY = h * 0.50f
        val bottomY = h * 0.78f

        drawLine(
            color = color,
            start = Offset(startX, topY),
            end = Offset(midX, midY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(midX, midY),
            end = Offset(startX, bottomY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

// =========================================================================
// Previews
// =========================================================================

@Preview(name = "TopBar Light Theme", showBackground = true)
@Composable
private fun ScrollStopTopBarLightPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
        ScrollStopTopBar(
            title = "Stop Doom Scroll",
            subtitle = "Active Protection",
            onNavigateBack = {}
        )
    }
}

@Preview(name = "TopBar Dark Theme", showBackground = true)
@Composable
private fun ScrollStopTopBarDarkPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
        ScrollStopTopBar(
            title = "Limits & Apps",
            subtitle = "Configure daily allowances",
            onNavigateBack = {}
        )
    }
}
