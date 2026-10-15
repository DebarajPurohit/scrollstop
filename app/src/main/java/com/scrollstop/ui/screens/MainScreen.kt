package com.scrollstop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollstop.R
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.ThemeRepository
import com.scrollstop.domain.model.MonitoredAppUsage
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.components.AppIconThumbnail
import com.scrollstop.ui.components.BadgeSeverity
import com.scrollstop.ui.components.BottomNavDestination
import com.scrollstop.ui.components.ProtectionStatusBadge
import com.scrollstop.ui.components.ScrollStopBottomNav
import com.scrollstop.ui.components.ScrollStopTopBar
import com.scrollstop.ui.components.SevenDayChartState
import com.scrollstop.ui.components.SevenDayUsageChart
import com.scrollstop.ui.components.UsageProgressBar
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Production Home Dashboard Screen.
 *
 * Implements the approved UI_SPEC.md requirements (Phase 3A):
 * 1. App title and Settings navigation entry.
 * 2. Protection status based on actual permission/service health—never hardcoded.
 * 3. Today's total phone usage hero metric based on available usage data.
 * 4. Seven-day total phone-usage chart with honest incomplete-history handling.
 * 5. Monitored application list showing today's usage and multi-day cumulative observation window.
 * 6. Dynamic usage progress/limit indicators backed by real configured limits.
 * 7. Persistent navigation bar with non-inert, purposeful destinations.
 * 8. Correct System, Light, and Dark appearance using the existing theme engine.
 */
@Composable
fun MainScreen(
    uiState: MainUiState = MainUiState(),
    onNavigateToAppSelection: () -> Unit = {},
    onNavigateToUsage: () -> Unit = {},
    onNavigateToLimits: () -> Unit = onNavigateToUsage,
    onNavigateToAccessibilityConsent: () -> Unit = {},
    onGrantUsageAccess: () -> Unit = {},
    onEnableAccessibility: () -> Unit = {},
    onEnableCommitmentLock: (Boolean) -> Unit = {},
    onRequestUnlock: () -> Unit = {},
    onCancelUnlock: () -> Unit = {},
    onDebugResetCommitmentLock: () -> Unit = {},
    themeRepository: ThemeRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val resolvedThemeRepo = remember(context, themeRepository) {
        themeRepository ?: ServiceLocator.getThemeRepository(context)
    }
    val currentThemeMode by resolvedThemeRepo.themeModeFlow.collectAsState()

    var showConsentDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showVisualPreviewDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

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

    if (showThemeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "Theme & Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose your preferred theme for Stop Doom Scroll:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        Triple(ThemeMode.SYSTEM, "System default", "Follows device dark/light setting"),
                        Triple(ThemeMode.LIGHT, "Light", "Clean Slate/Emerald palette on white"),
                        Triple(ThemeMode.DARK, "Dark", "Deep Slate/Emerald dark mode")
                    ).forEach { (mode, title, description) ->
                        val isSelected = currentThemeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    resolvedThemeRepo.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    resolvedThemeRepo.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Done")
                }
            }
        )
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
        modifier = modifier.fillMaxSize(),
        topBar = {
            ScrollStopTopBar(
                title = stringResource(id = R.string.app_name),
                subtitle = stringResource(id = R.string.app_description),
                actions = {
                    IconButton(
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Theme and Appearance Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            ScrollStopBottomNav(
                currentDestination = BottomNavDestination.HOME,
                onDestinationSelected = { destination ->
                    when (destination) {
                        BottomNavDestination.HOME -> {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        }
                        BottomNavDestination.LIMITS -> {
                            onNavigateToLimits()
                        }
                        BottomNavDestination.HISTORY -> {
                            coroutineScope.launch {
                                // Smoothly scroll to 7-day usage chart
                                scrollState.animateScrollTo(400)
                            }
                        }
                        BottomNavDestination.SETTINGS -> {
                            showThemeDialog = true
                        }
                    }
                }
            )
        }
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // =============================================================
                // 1. Protection Health Card (Honest Real Health Status)
                // =============================================================
                ProtectionStatusCard(
                    healthState = uiState.healthState,
                    selectedAppCount = uiState.selectedAppCount,
                    onGrantUsageAccess = onGrantUsageAccess,
                    onEnableAccessibility = onEnableAccessibility
                )

                // =============================================================
                // 2. Commitment Lock Status Card
                // =============================================================
                CommitmentStatusCard(
                    commitmentState = uiState.commitmentState,
                    onEnableClick = { showConsentDialog = true },
                    onRequestUnlockClick = { showUnlockDialog = true },
                    onCancelUnlockClick = onCancelUnlock
                )

                // =============================================================
                // 3. Today's Screen Time Hero Metric Card
                // =============================================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Today's screen time hero metric: ${uiState.formattedTodayScreenTime}"
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TODAY'S SCREEN TIME",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )

                            when {
                                !uiState.hasUsagePermission -> {
                                    ProtectionStatusBadge(
                                        text = "Permission Required",
                                        severity = BadgeSeverity.WARNING
                                    )
                                }
                                uiState.isUsageLoading -> {
                                    ProtectionStatusBadge(
                                        text = "Calculating…",
                                        severity = BadgeSeverity.NEUTRAL
                                    )
                                }
                                uiState.totalDailyLimitMs != null -> {
                                    val remainingMs = uiState.totalDailyLimitMs - uiState.todayScreenTimeMs
                                    if (remainingMs >= 0) {
                                        ProtectionStatusBadge(
                                            text = "${MonitoredAppUsage.formatDuration(remainingMs)} under target",
                                            severity = BadgeSeverity.SUCCESS
                                        )
                                    } else {
                                        ProtectionStatusBadge(
                                            text = "Limit exceeded",
                                            severity = BadgeSeverity.ERROR
                                        )
                                    }
                                }
                                else -> {
                                    ProtectionStatusBadge(
                                        text = "Tracking active",
                                        severity = BadgeSeverity.SUCCESS
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (uiState.isUsageLoading) {
                                "Calculating…"
                            } else if (!uiState.hasUsagePermission) {
                                "Permission Required"
                            } else {
                                uiState.formattedTodayScreenTime
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Total phone screen time across all applications today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // =============================================================
                // 4. 7-Day Total Device Usage Bar Chart
                // =============================================================
                SevenDayUsageChart(
                    state = uiState.sevenDayChartState,
                    onSelectDay = { /* Day inspected */ }
                )

                // =============================================================
                // 5. Monitored Applications Breakdown List
                // =============================================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monitored Applications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ProtectionStatusBadge(
                                text = "${uiState.monitoredApps.size} Monitored",
                                severity = if (uiState.monitoredApps.isNotEmpty()) BadgeSeverity.NEUTRAL else BadgeSeverity.WARNING
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (uiState.monitoredApps.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No applications monitored yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select distracting apps to set daily limits and protect your screen time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onNavigateToAppSelection,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .semantics {
                                            contentDescription = "Choose Apps to Control Button"
                                        }
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.btn_choose_apps),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            uiState.monitoredApps.forEachIndexed { index, app ->
                                AppUsageRowItem(app = app)
                                if (index < uiState.monitoredApps.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = onNavigateToLimits,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Configure Limits and Add Apps Button"
                                    },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = "Configure Limits & Add Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // 6. Secondary Navigation Quick Actions
                // =============================================================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                }

                // =============================================================
                // 7. Developer & Debug Controls (Strictly Debug-Only)
                // =============================================================
                if (com.scrollstop.BuildConfig.DEBUG) {
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
                                .padding(12.dp),
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
                                    text = "[PREVIEW] View Isolated Mockup Preview",
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

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Individual application usage row item adhering to UI_SPEC.md.
 */
@Composable
private fun AppUsageRowItem(
    app: MonitoredAppUsage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AppIconThumbnail(
                    drawable = app.icon,
                    appName = app.appName,
                    packageName = app.packageName,
                    size = 40.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = app.formattedCumulative,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = app.formattedTodayUsage,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = app.formattedLimit,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.dailyLimitMs != null && app.todayUsageMs >= app.dailyLimitMs) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        UsageProgressBar(
            usedMs = app.todayUsageMs,
            limitMs = app.dailyLimitMs,
            showPercent = true,
            height = 6.dp
        )
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
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
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
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ProtectionStatusBadge(healthState = healthState)
                    Spacer(modifier = Modifier.width(8.dp))
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
                        color = if (isProtected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics {
                            contentDescription = "Protection Status Text"
                        }
                    )
                }

                if (isProtected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (healthState) {
                    ProtectionHealthState.PROTECTED -> stringResource(R.string.health_desc_protected, selectedAppCount)
                    ProtectionHealthState.USAGE_ACCESS_REQUIRED -> stringResource(R.string.health_desc_usage_required)
                    ProtectionHealthState.ACCESSIBILITY_REQUIRED -> stringResource(R.string.health_desc_accessibility_required)
                    ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> stringResource(R.string.health_desc_accessibility_interrupted)
                    ProtectionHealthState.DEGRADED -> stringResource(R.string.health_desc_degraded)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Direct recovery button if action is needed
            if (!isProtected) {
                Spacer(modifier = Modifier.height(10.dp))
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
                                .height(40.dp)
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
                                .height(40.dp)
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
    val borderColor = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Commitment Lock Card" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLocked) "COMMITMENT LOCK: ACTIVE" else "COMMITMENT LOCK: INACTIVE",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                ProtectionStatusBadge(
                    text = if (isLocked) "Active" else "Inactive",
                    severity = if (isLocked) BadgeSeverity.COMMITMENT else BadgeSeverity.NEUTRAL
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    isLocked && isPendingUnlock -> "Unlock scheduled for next midnight (12:00 AM). Current limits remain enforced today."
                    isLocked -> "Limits and apps are locked today. Relaxations require waiting until next midnight."
                    else -> "Lock your daily limits to prevent impulsive relaxation today."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLocked && isPendingUnlock -> {
                    OutlinedButton(
                        onClick = onCancelUnlockClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Text(text = "Cancel Unlock Request", style = MaterialTheme.typography.labelMedium)
                    }
                }
                isLocked -> {
                    OutlinedButton(
                        onClick = onRequestUnlockClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Text(text = "Request Unlock for Midnight", style = MaterialTheme.typography.labelMedium)
                    }
                }
                else -> {
                    Button(
                        onClick = onEnableClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
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
    var isConfirmed by remember { mutableStateOf(false) }

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
