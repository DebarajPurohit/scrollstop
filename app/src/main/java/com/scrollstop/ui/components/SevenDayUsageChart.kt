package com.scrollstop.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.scrollstop.domain.model.DailyUsageRecord
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import com.scrollstop.ui.theme.extendedColors

/**
 * UI State for the 7-day usage chart.
 */
sealed interface SevenDayChartState {
    data object Loading : SevenDayChartState
    data class Error(val message: String, val onRetry: (() -> Unit)? = null) : SevenDayChartState
    data class Success(
        val days: List<DailyUsageRecord>,
        val selectedDayIndex: Int? = null
    ) : SevenDayChartState {
        val recordedDayCount: Int = days.count { it.hasRecord }
        val isHistoryIncomplete: Boolean = recordedDayCount < 7
        val averageDailyTimeMs: Long = if (recordedDayCount > 0) {
            days.filter { it.hasRecord }.map { it.totalScreenTimeMs }.average().toLong()
        } else 0L

        val formattedAverage: String
            get() {
                val totalMinutes = averageDailyTimeMs / (1000 * 60)
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            }

        val totalRecordedTimeMs: Long = days.filter { it.hasRecord }.sumOf { it.totalScreenTimeMs }
    }
}

/**
 * Standardized 7-Day Total Device Usage Bar Chart.
 *
 * Implements strict data integrity rules from UI_SPEC.md:
 * - Recorded days render solid proportional bars with exact duration.
 * - Unrecorded/missing days render distinct muted dashed/outline bars with "No data" label.
 *   Missing days are NEVER displayed or fabricated as 0-minute usage.
 * - History status badge ("X of 7 days recorded") displayed when history is incomplete.
 * - Daily baseline average is computed strictly across valid recorded days.
 * - Interactive tap selection with >= 48dp touch targets per day column.
 *
 * @param state Active [SevenDayChartState].
 * @param onSelectDay Callback when a day column is tapped.
 * @param modifier Custom modifier.
 */
@Composable
fun SevenDayUsageChart(
    state: SevenDayChartState,
    onSelectDay: (DailyUsageRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (state) {
                is SevenDayChartState.Loading -> {
                    ChartLoadingView()
                }
                is SevenDayChartState.Error -> {
                    ChartErrorView(message = state.message, onRetry = state.onRetry)
                }
                is SevenDayChartState.Success -> {
                    ChartSuccessView(
                        state = state,
                        onSelectDay = onSelectDay
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartSuccessView(
    state: SevenDayChartState.Success,
    onSelectDay: (DailyUsageRecord) -> Unit
) {
    val extendedColors = MaterialTheme.extendedColors

    // Header: Metric + Baseline / Incomplete Status
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "7-Day Screen Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (state.recordedDayCount > 0) {
                Text(
                    text = "Daily avg (${state.recordedDayCount}d): ${state.formattedAverage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No recorded history yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // History Status Badge if incomplete
        if (state.isHistoryIncomplete) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(extendedColors.warningContainer)
                    .border(1.dp, extendedColors.warning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${state.recordedDayCount} of 7 days recorded",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = extendedColors.onWarningContainer
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Chart Bars Area
    val maxUsageMs = state.days
        .filter { it.hasRecord }
        .maxOfOrNull { it.totalScreenTimeMs }
        ?.coerceAtLeast(1L) ?: 1L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 140.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        state.days.forEachIndexed { index, record ->
            val isSelected = state.selectedDayIndex == index

            DayBarColumn(
                record = record,
                maxUsageMs = maxUsageMs,
                isSelected = isSelected,
                onClick = { onSelectDay(record) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayBarColumn(
    record: DailyUsageRecord,
    maxUsageMs: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val a11yDescription = if (record.hasRecord) {
        "${record.dayLabel}: ${record.formattedDuration}"
    } else {
        "${record.dayLabel}: No data recorded"
    }

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 24.dp),
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                this.role = Role.Tab
                this.selected = isSelected
                this.contentDescription = a11yDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Duration or "No data" badge on top
        if (record.hasRecord) {
            Text(
                text = record.formattedDuration,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = if (record.isCurrentDay || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (record.isCurrentDay || isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "No data",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bar representation
        Box(
            modifier = Modifier
                .height(90.dp)
                .width(28.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (record.hasRecord) {
                // Proportional solid bar (or subtle 3% baseline for genuine zero usage)
                val ratio = if (record.totalScreenTimeMs > 0L) {
                    (record.totalScreenTimeMs.toFloat() / maxUsageMs.toFloat()).coerceIn(0.08f, 1.0f)
                } else {
                    0.03f // Subtle baseline cap for genuine zero usage
                }
                val barColor = if (record.isCurrentDay) {
                    MaterialTheme.colorScheme.primary
                } else if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.secondary
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(ratio)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(barColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.onBackground,
                                    RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                            } else Modifier
                        )
                )
            } else {
                // Muted dashed outline bar (NOT zero height or 0 min!)
                val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    val stroke = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = stroke
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Day of Week Label
        Text(
            text = if (record.isCurrentDay) "Today" else record.dayOfWeek,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (record.isCurrentDay || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                record.isCurrentDay || isSelected -> MaterialTheme.colorScheme.primary
                !record.hasRecord -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChartLoadingView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Loading usage history...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartErrorView(
    message: String,
    onRetry: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Retry")
            }
        }
    }
}

// =========================================================================
// Previews
// =========================================================================

@Preview(name = "Chart Incomplete History Light Theme", showBackground = true)
@Composable
private fun SevenDayUsageChartIncompletePreview() {
    val sampleDays = listOf(
        DailyUsageRecord(dayLabel = "Mon 8", dayOfWeek = "Mon", dateEpochMillis = 1L, hasRecord = false),
        DailyUsageRecord(dayLabel = "Tue 9", dayOfWeek = "Tue", dateEpochMillis = 2L, hasRecord = false),
        DailyUsageRecord(dayLabel = "Wed 10", dayOfWeek = "Wed", dateEpochMillis = 3L, hasRecord = false),
        DailyUsageRecord(dayLabel = "Thu 11", dayOfWeek = "Thu", dateEpochMillis = 4L, hasRecord = false),
        DailyUsageRecord(dayLabel = "Fri 12", dayOfWeek = "Fri", dateEpochMillis = 5L, totalScreenTimeMs = 5_400_000L, hasRecord = true),
        DailyUsageRecord(dayLabel = "Sat 13", dayOfWeek = "Sat", dateEpochMillis = 6L, totalScreenTimeMs = 9_000_000L, hasRecord = true),
        DailyUsageRecord(dayLabel = "Today", dayOfWeek = "Sun", dateEpochMillis = 7L, totalScreenTimeMs = 7_200_000L, hasRecord = true, isCurrentDay = true)
    )

    StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
        SevenDayUsageChart(
            state = SevenDayChartState.Success(days = sampleDays),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
    name = "Chart Complete History Dark Theme",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun SevenDayUsageChartDarkPreview() {
    val sampleDays = listOf(
        DailyUsageRecord(dayLabel = "Mon 8", dayOfWeek = "Mon", dateEpochMillis = 1L, totalScreenTimeMs = 6_000_000L, hasRecord = true),
        DailyUsageRecord(dayLabel = "Tue 9", dayOfWeek = "Tue", dateEpochMillis = 2L, totalScreenTimeMs = 8_200_000L, hasRecord = true),
        DailyUsageRecord(dayLabel = "Wed 10", dayOfWeek = "Wed", dateEpochMillis = 3L, totalScreenTimeMs = 4_500_000L, hasRecord = true),
        DailyUsageRecord(dayLabel = "Thu 11", dayOfWeek = "Thu", dateEpochMillis = 4L, totalScreenTimeMs = 7_000_000L, hasRecord = true),
        DailyUsageRecord(dayLabel = "Fri 12", dayOfWeek = "Fri", dateEpochMillis = 5L, totalScreenTimeMs = 5_400_000L, hasRecord = true),
        DailyUsageRecord(dayLabel = "Sat 13", dayOfWeek = "Sat", dateEpochMillis = 6L, totalScreenTimeMs = 9_000_000L, hasRecord = true),
        DailyUsageRecord(dayLabel = "Today", dayOfWeek = "Sun", dateEpochMillis = 7L, totalScreenTimeMs = 7_200_000L, hasRecord = true, isCurrentDay = true)
    )

    StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
        SevenDayUsageChart(
            state = SevenDayChartState.Success(days = sampleDays),
            modifier = Modifier.padding(16.dp)
        )
    }
}
