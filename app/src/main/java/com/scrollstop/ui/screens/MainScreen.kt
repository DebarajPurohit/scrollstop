package com.scrollstop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrollstop.R
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.theme.StopDoomScrollTheme

@Composable
fun MainScreen(
    uiState: MainUiState = MainUiState(),
    onNavigateToAppSelection: () -> Unit = {},
    onNavigateToUsage: () -> Unit = {},
    onNavigateToAccessibilityConsent: () -> Unit = {},
    onGrantUsageAccess: () -> Unit = {},
    onEnableAccessibility: () -> Unit = {},
    onEnableCommitmentLock: (Boolean) -> Unit = {},
    onRequestUnlock: () -> Unit = {},
    onCancelUnlock: () -> Unit = {},
    onDebugResetCommitmentLock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showConsentDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showUnlockDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showVisualPreviewDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showVisualPreviewDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showVisualPreviewDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.scrollstop.ui.preview.HomeDashboardPreviewScreen(
                onClose = { showVisualPreviewDialog = false }
            )
        }
    }

    if (showConsentDialog) {
        CommitmentConsentDialog(
            onDismiss = { showConsentDialog = false },
            onConfirmConsent = {
                onEnableCommitmentLock(true)
                showConsentDialog = false
            }
        )
    }

    if (showUnlockDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = {
                Text(
                    text = "Request Unlock for Midnight?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Commitment Lock is designed to stop impulsive bypassing. Unlocking will take effect at the next local midnight reset (12:00 AM). Your limits remain strictly enforced today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRequestUnlock()
                        showUnlockDialog = false
                    }
                ) {
                    Text("Schedule Unlock at Midnight")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnlockDialog = false }) {
                    Text("Keep Committed")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Application Title
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        contentDescription = "Stop Doom Scroll App Heading"
                    }
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Short Product Tagline / Description
                Text(
                    text = stringResource(id = R.string.app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .semantics {
                            contentDescription = "App description: Set your limit. When your time is up, we stop you."
                        }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Protection Health & Recovery Banner Card (Phase 4 Reliability Milestone)
                ProtectionStatusCard(
                    healthState = uiState.healthState,
                    selectedAppCount = uiState.selectedAppCount,
                    onGrantUsageAccess = onGrantUsageAccess,
                    onEnableAccessibility = onEnableAccessibility
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Commitment Lock Status Card
                CommitmentStatusCard(
                    commitmentState = uiState.commitmentState,
                    onEnableClick = { showConsentDialog = true },
                    onRequestUnlockClick = { showUnlockDialog = true },
                    onCancelUnlockClick = onCancelUnlock
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Primary Action Button: App Discovery & Selection
                Button(
                    onClick = onNavigateToAppSelection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .semantics {
                            contentDescription = "Choose Apps to Control Button"
                        }
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_choose_apps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Secondary Action Button: Usage Stats Tracking
                OutlinedButton(
                    onClick = onNavigateToUsage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .semantics {
                            contentDescription = "View Today's Usage Button"
                        }
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_view_usage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action Button: Accessibility Service Permission & Disclosure
                OutlinedButton(
                    onClick = onNavigateToAccessibilityConsent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .semantics {
                            contentDescription = "Accessibility Permission & Disclosure Button"
                        }
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_accessibility_disclosure),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Development Status Section Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Development Status Card"
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = stringResource(id = R.string.dev_status_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = stringResource(id = R.string.dev_status_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Developer test-only reset control (excluded from release builds)
                        if (com.scrollstop.BuildConfig.DEBUG) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showVisualPreviewDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "View Proposed Home Dashboard Preview Button" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "[PREVIEW] View Proposed Home Dashboard",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onDebugResetCommitmentLock,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Debug Reset Commitment Lock Action" },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    text = "[DEBUG TEST] Reset Commitment Lock",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionStatusCard(
    healthState: ProtectionHealthState,
    selectedAppCount: Int,
    onGrantUsageAccess: () -> Unit,
    onEnableAccessibility: () -> Unit
) {
    val isProtected = healthState == ProtectionHealthState.PROTECTED
    val borderColor = if (isProtected) {
        Color(0xFF2E7D32)
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Protection Health Card"
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isProtected) {
                Color(0xFFE8F5E9)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (healthState) {
                        ProtectionHealthState.PROTECTED -> stringResource(R.string.health_status_protected)
                        ProtectionHealthState.USAGE_ACCESS_REQUIRED -> stringResource(R.string.health_status_usage_required)
                        ProtectionHealthState.ACCESSIBILITY_REQUIRED -> stringResource(R.string.health_status_accessibility_required)
                        ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> stringResource(R.string.health_status_accessibility_interrupted)
                        ProtectionHealthState.DEGRADED -> stringResource(R.string.health_status_degraded)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isProtected) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        contentDescription = "Protection Status Text"
                    }
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = when (healthState) {
                    ProtectionHealthState.PROTECTED -> stringResource(R.string.health_desc_protected, selectedAppCount)
                    ProtectionHealthState.USAGE_ACCESS_REQUIRED -> stringResource(R.string.health_desc_usage_required)
                    ProtectionHealthState.ACCESSIBILITY_REQUIRED -> stringResource(R.string.health_desc_accessibility_required)
                    ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> stringResource(R.string.health_desc_accessibility_interrupted)
                    ProtectionHealthState.DEGRADED -> stringResource(R.string.health_desc_degraded)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Direct recovery button if action is needed
            if (!isProtected) {
                Spacer(modifier = Modifier.height(8.dp))
                when (healthState) {
                    ProtectionHealthState.USAGE_ACCESS_REQUIRED,
                    ProtectionHealthState.DEGRADED -> {
                        Button(
                            onClick = onGrantUsageAccess,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .semantics {
                                    contentDescription = "Grant Usage Access Recovery Button"
                                }
                        ) {
                            Text(
                                text = stringResource(R.string.btn_grant_usage_access),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    ProtectionHealthState.ACCESSIBILITY_REQUIRED -> {
                        Button(
                            onClick = onEnableAccessibility,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .semantics {
                                    contentDescription = "Enable Accessibility Recovery Button"
                                }
                        ) {
                            Text(
                                text = stringResource(R.string.btn_enable_accessibility),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> {
                        Button(
                            onClick = onEnableAccessibility,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .semantics {
                                    contentDescription = "Re-enable Protection Recovery Button"
                                }
                        ) {
                            Text(
                                text = stringResource(R.string.btn_re_enable_protection),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun CommitmentStatusCard(
    commitmentState: com.scrollstop.domain.model.CommitmentLockState,
    onEnableClick: () -> Unit,
    onRequestUnlockClick: () -> Unit,
    onCancelUnlockClick: () -> Unit
) {
    val isLocked = commitmentState.isLocked
    val isPendingUnlock = commitmentState.pendingUnlock
    val borderColor = if (isLocked) Color(0xFF1976D2) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Commitment Lock Card" },
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.2.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isLocked) "COMMITMENT LOCK: ACTIVE" else "COMMITMENT LOCK: INACTIVE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isLocked) Color(0xFF0D47A1) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = when {
                    isLocked && isPendingUnlock -> "Unlock scheduled for next midnight (12:00 AM). Current limits remain enforced today."
                    isLocked -> "Limits and apps are locked today. Relaxations require waiting until next midnight."
                    else -> "Lock your daily limits to prevent impulsive relaxation today."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            when {
                isLocked && isPendingUnlock -> {
                    OutlinedButton(
                        onClick = onCancelUnlockClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(text = "Cancel Unlock Request", style = MaterialTheme.typography.labelMedium)
                    }
                }
                isLocked -> {
                    OutlinedButton(
                        onClick = onRequestUnlockClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(text = "Request Unlock for Midnight", style = MaterialTheme.typography.labelMedium)
                    }
                }
                else -> {
                    Button(
                        onClick = onEnableClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(text = "Enable Commitment Lock", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitmentConsentDialog(
    onDismiss: () -> Unit,
    onConfirmConsent: () -> Unit
) {
    var isConfirmed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Commit to Your Daily Limits",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Commitment Lock is designed to prevent impulsive overrides:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• Once locked, you cannot increase daily limits or remove protected apps today.\n" +
                           "• Any request to relax restrictions requires waiting until the next local midnight reset (12:00 AM).\n" +
                           "• Adding apps or tightening limits is always allowed immediately.\n" +
                           "• Midnight resets your daily usage, not the commitment. Your commitment stays active until you explicitly request to unlock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = isConfirmed,
                        onCheckedChange = { isConfirmed = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I understand and commit to my limits until midnight",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmConsent,
                enabled = isConfirmed
            ) {
                Text("I Commit")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    StopDoomScrollTheme {
        MainScreen()
    }
}
