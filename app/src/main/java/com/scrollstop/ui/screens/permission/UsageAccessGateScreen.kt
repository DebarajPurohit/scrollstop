package com.scrollstop.ui.screens.permission

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode

/**
 * Dedicated, mandatory Usage Access permission gate screen.
 *
 * Implements strict gating architecture from UI_SPEC.md §3.2 and USER_FLOWS.md §1:
 * - Cannot be skipped or bypassed.
 * - Deep-links to system Settings.ACTION_USAGE_ACCESS_SETTINGS.
 * - Automatically rechecks permission state on ON_RESUME.
 * - Provides explicit "Recheck Permission" action.
 * - Clearly explains why Usage Access is needed, local-only processing, and separation from Accessibility.
 */
@Composable
fun UsageAccessGateScreen(
    hasPermission: Boolean,
    onOpenSettings: () -> Unit,
    onRecheckPermission: () -> Unit,
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasAttemptedGrant by remember { mutableStateOf(false) }

    // Auto-verify on ON_RESUME when returning from Android System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRecheckPermission()
                if (hasPermission) {
                    onPermissionGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Hero Gate Emblem (Data Shield in Emerald)
                GateShieldBadge()

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "USAGE ACCESS REQUIRED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Real limits require\naccurate measurement.",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    lineHeight = 38.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "To know when your daily allowance is reached, Stop Doom Scroll needs Android permission to read app active time.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Diagnostic guidance banner if user attempted grant but permission is still missing
                if (hasAttemptedGrant && !hasPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 20.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = "Permission not detected yet. In Android Settings, find Stop Doom Scroll in the list and toggle 'Permit usage access' on.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Educational cards breakdown
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PermissionPointCard(
                        icon = "⏱️",
                        title = "Why It's Needed",
                        description = "Measures foreground screen time for your chosen apps so limits can trigger reliably when your time is up."
                    )

                    PermissionPointCard(
                        icon = "📊",
                        title = "Powers Your Dashboard",
                        description = "Calculates your honest 7-day usage charts, remaining-time countdowns, and daily baseline trends."
                    )

                    PermissionPointCard(
                        icon = "🔒",
                        title = "100% Local-First Privacy",
                        description = "Usage stats are analyzed and stored exclusively on your device. We never upload, sync, or transmit your app activity."
                    )

                    PermissionPointCard(
                        icon = "🛡️",
                        title = "Separate from Accessibility",
                        description = "Usage Access only reads active time. The Accessibility Service is a separate permission used strictly for blocking."
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Action: Open System Settings
                Button(
                    onClick = {
                        hasAttemptedGrant = true
                        onOpenSettings()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Grant Usage Access Button"
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Grant Usage Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Secondary Action: Recheck Permission
                OutlinedButton(
                    onClick = {
                        onRecheckPermission()
                        if (hasPermission) {
                            onPermissionGranted()
                        } else {
                            hasAttemptedGrant = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Recheck Permission Button"
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = "Recheck Permission",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Usage Access is required to measure time and enforce limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PermissionPointCard(
    icon: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun GateShieldBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(38.dp)) {
            val strokeWidth = 2.5.dp.toPx()
            val emeraldColor = Color(0xFF10B981)

            // Outer shield
            val path = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.08f)
                lineTo(size.width * 0.88f, size.height * 0.24f)
                lineTo(size.width * 0.88f, size.height * 0.56f)
                cubicTo(
                    size.width * 0.88f, size.height * 0.80f,
                    size.width * 0.5f, size.height * 0.94f,
                    size.width * 0.5f, size.height * 0.94f
                )
                cubicTo(
                    size.width * 0.5f, size.height * 0.94f,
                    size.width * 0.12f, size.height * 0.80f,
                    size.width * 0.12f, size.height * 0.56f
                )
                lineTo(size.width * 0.12f, size.height * 0.24f)
                close()
            }
            drawPath(
                path = path,
                color = emeraldColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Inner chart bars
            val barW = 3.dp.toPx()
            val baseLine = size.height * 0.72f
            // Bar 1
            drawLine(
                color = emeraldColor,
                start = Offset(size.width * 0.35f, baseLine),
                end = Offset(size.width * 0.35f, baseLine - 10.dp.toPx()),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
            // Bar 2
            drawLine(
                color = emeraldColor,
                start = Offset(size.width * 0.50f, baseLine),
                end = Offset(size.width * 0.50f, baseLine - 16.dp.toPx()),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
            // Bar 3
            drawLine(
                color = emeraldColor,
                start = Offset(size.width * 0.65f, baseLine),
                end = Offset(size.width * 0.65f, baseLine - 12.dp.toPx()),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun UsageAccessGateScreenLightPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
        UsageAccessGateScreen(
            hasPermission = false,
            onOpenSettings = {},
            onRecheckPermission = {},
            onPermissionGranted = {}
        )
    }
}

@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UsageAccessGateScreenDarkPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
        UsageAccessGateScreen(
            hasPermission = false,
            onOpenSettings = {},
            onRecheckPermission = {},
            onPermissionGranted = {}
        )
    }
}

