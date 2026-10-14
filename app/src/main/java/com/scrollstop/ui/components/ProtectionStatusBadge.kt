package com.scrollstop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import com.scrollstop.ui.theme.extendedColors

/**
 * Visual severity/status level for generic badges.
 */
enum class BadgeSeverity {
    SUCCESS,
    WARNING,
    ERROR,
    COMMITMENT,
    NEUTRAL
}

/**
 * Standardized status badge / pill component.
 *
 * Implements accessible semantic color containers, clear text labels, and visual
 * indicators so status never relies on color alone.
 *
 * @param text Status label text to display.
 * @param severity Semantic status severity level.
 * @param modifier Custom modifier.
 * @param onClick Optional click callback. When non-null, ensures >= 48dp minimum touch target.
 * @param contentDescription Optional custom accessibility announcement.
 */
@Composable
fun ProtectionStatusBadge(
    text: String,
    severity: BadgeSeverity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    val extendedColors = MaterialTheme.extendedColors

    val (containerColor, contentColor, borderColor) = when (severity) {
        BadgeSeverity.SUCCESS -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
        BadgeSeverity.WARNING -> Triple(
            extendedColors.warningContainer,
            extendedColors.onWarningContainer,
            extendedColors.warning.copy(alpha = 0.45f)
        )
        BadgeSeverity.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        )
        BadgeSeverity.COMMITMENT -> Triple(
            extendedColors.commitmentContainer,
            extendedColors.onCommitmentContainer,
            extendedColors.commitment.copy(alpha = 0.45f)
        )
        BadgeSeverity.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
    }

    val shape = RoundedCornerShape(12.dp)
    val a11yDesc = contentDescription ?: "$text status badge"

    val baseModifier = if (onClick != null) {
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
    } else {
        modifier
    }

    Box(
        modifier = baseModifier
            .semantics { this.contentDescription = a11yDesc },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(containerColor)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Status Dot
            Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(color = contentColor)
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

/**
 * Convenience overload that maps system [ProtectionHealthState] to [ProtectionStatusBadge].
 */
@Composable
fun ProtectionStatusBadge(
    healthState: ProtectionHealthState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (label, severity) = when (healthState) {
        ProtectionHealthState.PROTECTED -> "Protected" to BadgeSeverity.SUCCESS
        ProtectionHealthState.ACCESSIBILITY_REQUIRED -> "Accessibility Required" to BadgeSeverity.WARNING
        ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> "Service Interrupted" to BadgeSeverity.ERROR
        ProtectionHealthState.USAGE_ACCESS_REQUIRED -> "Usage Access Required" to BadgeSeverity.ERROR
        ProtectionHealthState.DEGRADED -> "Protection Offline" to BadgeSeverity.ERROR
    }

    ProtectionStatusBadge(
        text = label,
        severity = severity,
        modifier = modifier,
        onClick = onClick,
        contentDescription = "System protection state: $label"
    )
}

// =========================================================================
// Previews
// =========================================================================

@Preview(name = "Badge Severities Light Theme", showBackground = true)
@Composable
private fun ProtectionStatusBadgeLightPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProtectionStatusBadge(text = "Protected", severity = BadgeSeverity.SUCCESS)
            ProtectionStatusBadge(text = "Nearing limit", severity = BadgeSeverity.WARNING)
            ProtectionStatusBadge(text = "Limit reached", severity = BadgeSeverity.ERROR)
            ProtectionStatusBadge(text = "Locked", severity = BadgeSeverity.COMMITMENT)
        }
    }
}

@Preview(name = "Badge Severities Dark Theme", showBackground = true)
@Composable
private fun ProtectionStatusBadgeDarkPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProtectionStatusBadge(text = "Protected", severity = BadgeSeverity.SUCCESS)
            ProtectionStatusBadge(text = "Nearing limit", severity = BadgeSeverity.WARNING)
            ProtectionStatusBadge(text = "Limit reached", severity = BadgeSeverity.ERROR)
            ProtectionStatusBadge(text = "Locked", severity = BadgeSeverity.COMMITMENT)
        }
    }
}
