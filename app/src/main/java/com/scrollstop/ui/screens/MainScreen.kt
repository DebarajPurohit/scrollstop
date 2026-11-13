package com.scrollstop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.scrollstop.ui.components.ThemeSelectionDialog
import com.scrollstop.ui.components.UsageProgressBar
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.extendedColors
import kotlinx.coroutines.launch

/**
 * Production Home Dashboard Screen.
 *
 * Implements approved UI_SPEC.md requirements:
 * 1. App title, motivational subtitle, and Settings navigation entry.
 * 2. Protection status based on actual permission/service health—never hardcoded.
 * 3. Guided Protection Setup contextual checklist when protection is not fully configured.
 * 4. Today's total phone usage hero metric based on authoritative usage data.
 * 5. Seven-day total phone-usage chart with honest incomplete-history handling.
 * 6. Monitored application list showing today's usage, multi-day cumulative observation window,
 *    and pending midnight change badges.
 * 7. Commitment Lock status card with pending midnight relaxations display.
 * 8. Persistent 4-tab navigation bar with non-inert, purposeful destinations.
 * 9. Correct System, Light, and Dark appearance using theme engine tokens.
 */
@Composable
fun MainScreen(
    uiState: MainUiState = MainUiState(),
    onNavigateToAppSelection: () -> Unit = {},
    onNavigateToUsage: () -> Unit = {},
    onNavigateToLimits: () -> Unit = onNavigateToUsage,
    onNavigateToAccessibilityConsent: () -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null,
    onGrantUsageAccess: () -> Unit = {},
    onEnableAccessibility: () -> Unit = {},
    onEnableCommitmentLock: (Boolean) -> Unit = {},
    onRequestUnlock: () -> Unit = {},
    onCancelUnlock: () -> Unit = {},
    onDebugResetCommitmentLock: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onAddApp: (String) -> Unit = {},
    themeRepository: ThemeRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        onRefresh()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val resolvedThemeRepo = remember(context, themeRepository) {
        themeRepository ?: ServiceLocator.getThemeRepository(context)
    }
    val currentThemeMode by resolvedThemeRepo.themeModeFlow.collectAsState()

    var showConsentDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showVisualPreviewDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val openSettings = onNavigateToSettings ?: { showThemeDialog = true }

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
        ThemeSelectionDialog(
            currentThemeMode = currentThemeMode,
            onThemeSelected = { mode ->
                resolvedThemeRepo.setThemeMode(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
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
                subtitle = null,
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Refresh Dashboard Usage" }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh Dashboard Usage",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = openSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Theme and Appearance Settings" }
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
                                scrollState.animateScrollTo(400)
                            }
                        }
                        BottomNavDestination.SETTINGS -> {
                            openSettings()
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
                // 1. Today's Screen Time Hero Metric Card
                // =============================================================
                HeroScreenTimeCard(uiState = uiState)

                // =============================================================
                // 2. 7-Day Total Device Usage Bar Chart
                // =============================================================
                SevenDayUsageChart(
                    state = uiState.sevenDayChartState,
                    onSelectDay = { /* Day inspected */ }
                )

                // =============================================================
                // 3. Top Five Apps Today Breakdown
                // =============================================================
                TopAppsTodayCard(
                    uiState = uiState,
                    onAddApp = onAddApp,
                    onViewAll = onNavigateToLimits
                )

                // =============================================================
                // 4. Commitment Lock Compact Card
                // =============================================================
                CommitmentStatusCard(
                    commitmentState = uiState.commitmentState,
                    healthState = uiState.healthState,
                    onEnableClick = { showConsentDialog = true },
                    onRequestUnlockClick = { showUnlockDialog = true },
                    onCancelUnlockClick = onCancelUnlock
                )

                // =============================================================
                // 5. Compact Protection Health Card
                // =============================================================
                CompactProtectionHealthCard(
                    healthState = uiState.healthState,
                    selectedAppCount = uiState.selectedAppCount,
                    enforcedAppCount = uiState.enforcedAppCount,
                    onGrantUsageAccess = onGrantUsageAccess,
                    onEnableAccessibility = onEnableAccessibility,
                    onNavigateToAccessibilityConsent = onNavigateToAccessibilityConsent,
                    onNavigateToLimits = onNavigateToLimits
                )

                // =============================================================
                // 6. Developer & Debug Controls (Strictly Debug-Only)
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
                                    .height(48.dp)
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
                                    .height(48.dp)
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
 * Today's Total Phone Usage Hero Metric Card.
 */
@Composable
private fun HeroScreenTimeCard(
    uiState: MainUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Total phone usage today: ${uiState.formattedTodayScreenTime}"
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
                    text = "Total phone usage today",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
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
                    else -> {
                        // Comparison badges, limit exceeded, within limit, overall device allowance removed as approved
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
}

/**
 * Top five apps by today's usage card.
 */
@Composable
private fun TopAppsTodayCard(
    uiState: MainUiState,
    onAddApp: (String) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Top Apps Today Card" },
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
                    text = "Top Apps Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (uiState.topApps.isNotEmpty()) {
                    ProtectionStatusBadge(
                        text = "${uiState.topApps.size} active",
                        severity = BadgeSeverity.NEUTRAL
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.topApps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No application usage recorded today",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Apps will appear here once used today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                uiState.topApps.forEachIndexed { index, app ->
                    TopAppUsageRowItem(
                        app = app,
                        onAdd = { onAddApp(app.packageName) }
                    )
                    if (index < uiState.topApps.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onViewAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "View All Apps Button"
                    },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "View All Apps",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Single application usage row in Top Apps Today list.
 */
@Composable
private fun TopAppUsageRowItem(
    app: TopAppUsageItem,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                    text = MonitoredAppUsage.formatDuration(app.todayUsageMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (app.isMonitored) {
            ProtectionStatusBadge(
                text = "Monitored",
                severity = BadgeSeverity.SUCCESS
            )
        } else {
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier
                    .height(48.dp)
                    .semantics {
                        contentDescription = "Add ${app.appName} to Monitored Apps"
                    },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "+ Add",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CompactProtectionHealthCard(
    healthState: ProtectionHealthState,
    selectedAppCount: Int,
    enforcedAppCount: Int,
    onGrantUsageAccess: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onNavigateToAccessibilityConsent: () -> Unit = onEnableAccessibility,
    onNavigateToLimits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isProtected = healthState == ProtectionHealthState.PROTECTED && enforcedAppCount > 0
    val isSetupRequired = (healthState == ProtectionHealthState.PROTECTED && enforcedAppCount == 0) ||
        (healthState == ProtectionHealthState.DEGRADED && selectedAppCount == 0)

    val borderColor = when {
        isProtected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        healthState == ProtectionHealthState.ACCESSIBILITY_REQUIRED -> MaterialTheme.extendedColors.warning
        isSetupRequired -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Protection Health Card"
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isProtected -> MaterialTheme.colorScheme.surface
                isSetupRequired -> MaterialTheme.colorScheme.surface
                healthState == ProtectionHealthState.ACCESSIBILITY_REQUIRED -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
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
                    when {
                        isProtected -> ProtectionStatusBadge(healthState = healthState)
                        healthState == ProtectionHealthState.ACCESSIBILITY_REQUIRED -> ProtectionStatusBadge(
                            text = "ACCESSIBILITY REQUIRED",
                            severity = BadgeSeverity.WARNING
                        )
                        healthState == ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> ProtectionStatusBadge(
                            text = "INTERRUPTED",
                            severity = BadgeSeverity.ERROR
                        )
                        isSetupRequired -> ProtectionStatusBadge(
                            text = "Setup Required",
                            severity = BadgeSeverity.WARNING
                        )
                        else -> ProtectionStatusBadge(healthState = healthState)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            healthState == ProtectionHealthState.ACCESSIBILITY_REQUIRED -> "Accessibility required — app blocking is inactive."
                            isProtected -> stringResource(R.string.health_status_protected)
                            isSetupRequired -> stringResource(R.string.health_status_degraded)
                            healthState == ProtectionHealthState.USAGE_ACCESS_REQUIRED -> stringResource(R.string.health_status_usage_required)
                            healthState == ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> stringResource(R.string.health_status_accessibility_interrupted)
                            else -> stringResource(R.string.health_status_degraded)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isProtected -> MaterialTheme.colorScheme.onSurface
                            healthState == ProtectionHealthState.ACCESSIBILITY_REQUIRED -> MaterialTheme.colorScheme.onSurface
                            isSetupRequired -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.error
                        },
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
                text = when {
                    healthState == ProtectionHealthState.ACCESSIBILITY_REQUIRED -> "Enable the Accessibility Service to detect app switches and enforce limits."
                    isProtected -> stringResource(R.string.health_desc_protected, enforcedAppCount)
                    isSetupRequired -> when {
                        selectedAppCount == 0 -> "Choose distracting apps to control and configure daily limits."
                        healthState == ProtectionHealthState.PROTECTED && enforcedAppCount == 0 -> "Configure daily limits for your selected apps to enable protection."
                        else -> stringResource(R.string.health_desc_degraded)
                    }
                    healthState == ProtectionHealthState.USAGE_ACCESS_REQUIRED -> stringResource(R.string.health_desc_usage_required)
                    healthState == ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> stringResource(R.string.health_desc_accessibility_interrupted)
                    else -> stringResource(R.string.health_desc_degraded)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Direct recovery or setup button if action is needed
            if (!isProtected) {
                Spacer(modifier = Modifier.height(10.dp))
                when {
                    healthState == ProtectionHealthState.ACCESSIBILITY_REQUIRED -> {
                        Button(
                            onClick = onNavigateToAccessibilityConsent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .semantics {
                                    contentDescription = "Enable Accessibility Button"
                                }
                        ) {
                            Text(
                                text = "Enable Accessibility",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    isSetupRequired && selectedAppCount == 0 -> {
                        Button(
                            onClick = onNavigateToLimits,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .semantics {
                                    contentDescription = "Configure Limits Setup Button"
                                }
                        ) {
                            Text(
                                text = "Configure Limits",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    isSetupRequired && healthState == ProtectionHealthState.PROTECTED && enforcedAppCount == 0 -> {
                        Button(
                            onClick = onNavigateToLimits,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .semantics {
                                    contentDescription = "Configure Limits Setup Button"
                                }
                        ) {
                            Text(
                                text = "Configure Limits",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    healthState == ProtectionHealthState.USAGE_ACCESS_REQUIRED ||
                    (healthState == ProtectionHealthState.DEGRADED && selectedAppCount > 0) -> {
                        Button(
                            onClick = onGrantUsageAccess,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
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
                    healthState == ProtectionHealthState.ACCESSIBILITY_INTERRUPTED -> {
                        Button(
                            onClick = onEnableAccessibility,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
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
                }
            }
        }
    }
}

@Composable
private fun CommitmentStatusCard(
    commitmentState: com.scrollstop.domain.model.CommitmentLockState,
    healthState: ProtectionHealthState = ProtectionHealthState.PROTECTED,
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

            if (healthState == ProtectionHealthState.ACCESSIBILITY_REQUIRED || healthState == ProtectionHealthState.ACCESSIBILITY_INTERRUPTED) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: Real-time limit enforcement and app blocking require the Accessibility Service to be enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.warning
                )
            }

            // Staged relaxation changes section
            if (commitmentState.isRelaxationPending) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Pending Changes at Midnight Reset (12:00 AM):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.extendedColors.warning
                        )
                        if (commitmentState.pendingUnlock) {
                            Text(
                                text = "• Unlock Commitment Lock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        commitmentState.pendingRemovedPackages.forEach { pkg ->
                            Text(
                                text = "• Removal from protection: $pkg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        commitmentState.pendingIncreasedLimits.forEach { (pkg, newLimit) ->
                            Text(
                                text = "• Limit increase: $pkg → ${MonitoredAppUsage.formatDuration(newLimit)} daily",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                isLocked && isPendingUnlock -> {
                    OutlinedButton(
                        onClick = onCancelUnlockClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(text = "Cancel Unlock Request", style = MaterialTheme.typography.labelMedium)
                    }
                }
                isLocked -> {
                    OutlinedButton(
                        onClick = onRequestUnlockClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(text = "Request Unlock for Midnight", style = MaterialTheme.typography.labelMedium)
                    }
                }
                else -> {
                    Button(
                        onClick = onEnableClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
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
