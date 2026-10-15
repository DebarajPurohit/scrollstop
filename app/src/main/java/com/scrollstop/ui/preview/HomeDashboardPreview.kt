package com.scrollstop.ui.preview

import android.content.res.Configuration
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.ThemeRepository
import com.scrollstop.domain.model.DailyUsageRecord
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
import com.scrollstop.ui.theme.extendedColors

/**
 * Preview data model representing an application row on the proposed Home dashboard.
 */
data class PreviewMonitoredApp(
    val appName: String,
    val packageName: String,
    val todayUsageMs: Long,
    val dailyLimitMs: Long?,
    val cumulativeUsageMs: Long,
    val observationDays: Int
) {
    val formattedTodayUsage: String = formatDuration(todayUsageMs)
    val formattedLimit: String = dailyLimitMs?.let { "${formatDuration(it)} daily" } ?: "No limit"
    val formattedCumulative: String = "${formatDuration(cumulativeUsageMs)} (${observationDays}d of data)"
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / (1000 * 60)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Realistic sample data for Home dashboard preview.
 * Clearly marked as sample/preview data; never represented as actual device metrics.
 */
object PreviewSampleData {
    // 4 of 7 days recorded (demonstrates history-building and non-fabrication rule)
    val incompleteSevenDayRecords: List<DailyUsageRecord> = listOf(
        DailyUsageRecord("Fri 5", "Fri", 1L, totalScreenTimeMs = 0L, hasRecord = false),
        DailyUsageRecord("Sat 6", "Sat", 2L, totalScreenTimeMs = 0L, hasRecord = false),
        DailyUsageRecord("Sun 7", "Sun", 3L, totalScreenTimeMs = 0L, hasRecord = false),
        DailyUsageRecord("Mon 8", "Mon", 4L, totalScreenTimeMs = 9_900_000L, hasRecord = true), // 2h 45m
        DailyUsageRecord("Tue 9", "Tue", 5L, totalScreenTimeMs = 15_000_000L, hasRecord = true), // 4h 10m
        DailyUsageRecord("Wed 10", "Wed", 6L, totalScreenTimeMs = 11_700_000L, hasRecord = true), // 3h 15m
        DailyUsageRecord("Today", "Thu", 7L, totalScreenTimeMs = 13_320_000L, hasRecord = true, isCurrentDay = true) // 3h 42m
    )

    // Full 7 days recorded (demonstrates mature history state)
    val fullSevenDayRecords: List<DailyUsageRecord> = listOf(
        DailyUsageRecord("Fri 5", "Fri", 1L, totalScreenTimeMs = 11_400_000L, hasRecord = true), // 3h 10m
        DailyUsageRecord("Sat 6", "Sat", 2L, totalScreenTimeMs = 17_400_000L, hasRecord = true), // 4h 50m
        DailyUsageRecord("Sun 7", "Sun", 3L, totalScreenTimeMs = 15_600_000L, hasRecord = true), // 4h 20m
        DailyUsageRecord("Mon 8", "Mon", 4L, totalScreenTimeMs = 9_900_000L, hasRecord = true), // 2h 45m
        DailyUsageRecord("Tue 9", "Tue", 5L, totalScreenTimeMs = 15_000_000L, hasRecord = true), // 4h 10m
        DailyUsageRecord("Wed 10", "Wed", 6L, totalScreenTimeMs = 11_700_000L, hasRecord = true), // 3h 15m
        DailyUsageRecord("Today", "Thu", 7L, totalScreenTimeMs = 13_320_000L, hasRecord = true, isCurrentDay = true) // 3h 42m
    )

    val sampleApps: List<PreviewMonitoredApp> = listOf(
        PreviewMonitoredApp(
            appName = "Instagram",
            packageName = "com.instagram.android",
            todayUsageMs = 2_700_000L, // 45m
            dailyLimitMs = 1_800_000L, // 30m limit (Exceeded: 150%)
            cumulativeUsageMs = 12_000_000L, // 3h 20m
            observationDays = 4
        ),
        PreviewMonitoredApp(
            appName = "YouTube",
            packageName = "com.google.android.youtube",
            todayUsageMs = 2_280_000L, // 38m
            dailyLimitMs = 2_700_000L, // 45m limit (Warning: 84%)
            cumulativeUsageMs = 11_400_000L, // 3h 10m
            observationDays = 4
        ),
        PreviewMonitoredApp(
            appName = "TikTok",
            packageName = "com.zhiliaoapp.musically",
            todayUsageMs = 720_000L, // 12m
            dailyLimitMs = 1_800_000L, // 30m limit (Normal: 40%)
            cumulativeUsageMs = 3_900_000L, // 1h 05m
            observationDays = 4
        ),
        PreviewMonitoredApp(
            appName = "Reddit",
            packageName = "com.reddit.frontpage",
            todayUsageMs = 1_320_000L, // 22m
            dailyLimitMs = null, // Unrestricted / No limit
            cumulativeUsageMs = 6_300_000L, // 1h 45m
            observationDays = 4
        )
    )
}

/**
 * Proposed Home Dashboard Screen (Isolated Visual Preview).
 *
 * Implements the approved UI_SPEC.md requirements:
 * 1. Polished top bar and protection status badge.
 * 2. Today's total phone usage hero metric.
 * 3. 7-day bar chart with incomplete history indicator (non-fabrication rule).
 * 4. App breakdown list with today's usage, limits, and 7-day cumulative totals.
 * 5. Persistent 4-destination bottom navigation.
 * 6. Slate/Emerald premium minimalist hierarchy without generic button stacks.
 *
 * @param onClose Optional callback to dismiss when displayed in an isolated preview overlay.
 */
@Composable
fun HomeDashboardPreviewScreen(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    themeRepository: ThemeRepository? = null,
    previewThemeMode: ThemeMode? = null
) {
    val context = LocalContext.current.applicationContext
    val resolvedThemeRepo = remember(context, themeRepository) {
        themeRepository ?: ServiceLocator.getThemeRepository(context)
    }
    val persistedThemeMode by resolvedThemeRepo.themeModeFlow.collectAsState()
    val activeThemeMode = previewThemeMode ?: persistedThemeMode
    var showThemeDialog by remember { mutableStateOf(false) }

    var simulateFullHistory by remember { mutableStateOf(false) }
    var currentNavDestination by remember { mutableStateOf(BottomNavDestination.HOME) }
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }

    val activeRecords = if (simulateFullHistory) {
        PreviewSampleData.fullSevenDayRecords
    } else {
        PreviewSampleData.incompleteSevenDayRecords
    }

    val chartState = SevenDayChartState.Success(
        days = activeRecords,
        selectedDayIndex = selectedDayIndex
    )

    if (showThemeDialog) {
        AlertDialog(
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
                        val isSelected = activeThemeMode == mode
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

    StopDoomScrollTheme(themeMode = activeThemeMode) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                Column {
                    // Preview Mode Banner with Theme Mode Selector
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "PREVIEW MOCKUP • SAMPLE DATA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )

                                if (onClose != null) {
                                    Text(
                                        text = "Close Preview",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.error), RoundedCornerShape(4.dp))
                                            .clickable(onClick = onClose)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .semantics {
                                                role = Role.Button
                                                contentDescription = "Close preview screen"
                                            }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Accessible Theme Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Theme Mode:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        ThemeMode.SYSTEM to "System",
                                        ThemeMode.LIGHT to "Light",
                                        ThemeMode.DARK to "Dark"
                                    ).forEach { (mode, label) ->
                                        val isSelected = activeThemeMode == mode
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable { resolvedThemeRepo.setThemeMode(mode) }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                                .semantics {
                                                    this.role = Role.RadioButton
                                                    this.selected = isSelected
                                                    this.contentDescription = "Switch to $label theme"
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ScrollStopTopBar(
                        title = "Stop Doom Scroll",
                        subtitle = "Intentional digital balance",
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
                }
            },
        bottomBar = {
            ScrollStopBottomNav(
                currentDestination = currentNavDestination,
                onDestinationSelected = { currentNavDestination = it }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // =============================================================
                // 1. Protection Health Card
                // =============================================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Protection status card: Active and protected" },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProtectionStatusBadge(healthState = ProtectionHealthState.PROTECTED)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Active Protection",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "3 apps monitored • Accessibility enforcing limits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // =============================================================
                // 2. Today's Hero Screen Time Metric
                // =============================================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Today's screen time: 3 hours 42 minutes" },
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
                            ProtectionStatusBadge(
                                text = "48m under target",
                                severity = BadgeSeverity.SUCCESS
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "3h 42m",
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
                // 3. 7-Day Total Device Usage Bar Chart
                // =============================================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SevenDayUsageChart(
                        state = chartState,
                        onSelectDay = { record ->
                            selectedDayIndex = activeRecords.indexOf(record).takeIf { it >= 0 }
                        }
                    )

                    // Interactive Preview Toggle for History Completeness
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (simulateFullHistory) "Simulating: Full 7 Days" else "Simulating: 4 of 7 Days (History Building)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = if (simulateFullHistory) "Show 4d Partial" else "Show 7d Full",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                                .clickable { simulateFullHistory = !simulateFullHistory }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Toggle history simulation state"
                                }
                        )
                    }
                }

                // =============================================================
                // 4. Monitored Applications Breakdown List
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
                                text = "4 Monitored",
                                severity = BadgeSeverity.NEUTRAL
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        PreviewSampleData.sampleApps.forEachIndexed { index, app ->
                            AppUsageRowItem(app = app)
                            if (index < PreviewSampleData.sampleApps.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Purposeful CTA Button
                        OutlinedButton(
                            onClick = { /* Navigates to Limits screen in Phase 3 */ },
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

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
}

/**
 * Individual application usage row item adhering to UI_SPEC.md.
 */
@Composable
private fun AppUsageRowItem(
    app: PreviewMonitoredApp,
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
                    drawable = null,
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

// =============================================================================
// Android Studio Compose Previews
// =============================================================================

@Preview(
    name = "Home Dashboard — Light Theme",
    showBackground = true,
    widthDp = 390,
    heightDp = 844
)
@Composable
fun HomeDashboardPreviewLight() {
    HomeDashboardPreviewScreen(previewThemeMode = ThemeMode.LIGHT)
}

@Preview(
    name = "Home Dashboard — Dark Theme",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    widthDp = 390,
    heightDp = 844
)
@Composable
fun HomeDashboardPreviewDark() {
    HomeDashboardPreviewScreen(previewThemeMode = ThemeMode.DARK)
}

@Preview(
    name = "Home Dashboard — Large Font (1.3x)",
    fontScale = 1.3f,
    showBackground = true,
    widthDp = 390,
    heightDp = 844
)
@Composable
fun HomeDashboardPreviewLargeFont() {
    HomeDashboardPreviewScreen(previewThemeMode = ThemeMode.LIGHT)
}
