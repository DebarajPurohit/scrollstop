package com.scrollstop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import com.scrollstop.ui.theme.extendedColors

/**
 * Standardized usage progress bar displaying consumption relative to configured limit.
 *
 * Implements the approved UI_SPEC.md color coding:
 * - Within limit (<80%): [MaterialTheme.colorScheme.primary] (Emerald)
 * - Nearing limit (>=80% and <100%): [MaterialTheme.extendedColors.warning] (Amber)
 * - Limit reached (>=100%): [MaterialTheme.colorScheme.error] (Red)
 * - Unrestricted / No limit: [MaterialTheme.colorScheme.secondary]
 *
 * @param usedMs Duration used in milliseconds.
 * @param limitMs Configured daily limit in milliseconds, or null if unrestricted.
 * @param modifier Custom modifier.
 * @param height Height of the progress bar track (defaults to 6dp).
 * @param showPercent Whether to display an inline percentage label.
 */
@Composable
fun UsageProgressBar(
    usedMs: Long,
    limitMs: Long?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    showPercent: Boolean = false
) {
    val extendedColors = MaterialTheme.extendedColors
    val isConfigured = limitMs != null
    val ratio = when {
        !isConfigured -> 0f
        limitMs!! <= 0L -> 1.0f // A configured zero-minute limit is reached immediately
        else -> (usedMs.toFloat() / limitMs.toFloat()).coerceAtLeast(0f)
    }
    val clampedProgress = ratio.coerceIn(0f, 1f)

    val progressColor: Color = when {
        !isConfigured -> MaterialTheme.colorScheme.secondary
        ratio >= 1.0f -> MaterialTheme.colorScheme.error
        ratio >= 0.8f -> extendedColors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val cornerShape = RoundedCornerShape(height / 2)

    val percentText = when {
        !isConfigured -> "No limit"
        limitMs!! <= 0L -> "100%"
        else -> "${(ratio * 100).toInt()}%"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clampedProgress, 0f..1f)
                contentDescription = if (isConfigured) {
                    "Usage progress: $percentText"
                } else {
                    "Unrestricted application usage"
                }
            }
    ) {
        if (showPercent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = percentText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = progressColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(cornerShape)
                .background(trackColor)
        ) {
            if (clampedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clampedProgress)
                        .fillMaxHeight()
                        .clip(cornerShape)
                        .background(progressColor)
                )
            }
        }
    }
}

// =========================================================================
// Previews
// =========================================================================

@Preview(name = "Progress Bar States Light Theme", showBackground = true)
@Composable
private fun UsageProgressBarLightPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            // Normal (<80%)
            UsageProgressBar(usedMs = 30_000L, limitMs = 120_000L, showPercent = true)
            // Warning (>=80%)
            UsageProgressBar(usedMs = 100_000L, limitMs = 120_000L, showPercent = true)
            // Limit Reached (>=100%)
            UsageProgressBar(usedMs = 130_000L, limitMs = 120_000L, showPercent = true)
            // No limit
            UsageProgressBar(usedMs = 50_000L, limitMs = null, showPercent = true)
        }
    }
}

@Preview(name = "Progress Bar States Dark Theme", showBackground = true)
@Composable
private fun UsageProgressBarDarkPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            UsageProgressBar(usedMs = 30_000L, limitMs = 120_000L, showPercent = true)
            UsageProgressBar(usedMs = 100_000L, limitMs = 120_000L, showPercent = true)
            UsageProgressBar(usedMs = 130_000L, limitMs = 120_000L, showPercent = true)
        }
    }
}
