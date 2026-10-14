package com.scrollstop.domain.model

/**
 * Immutable model representing device or application usage for a single calendar day
 * in the 7-day retrospective chart.
 *
 * Data Integrity Rules (UI_SPEC.md Section 1 & Section 3.3.3):
 * - [hasRecord] is true if operating system usage stats were recorded for this day.
 * - [hasRecord] is false if this day predates installation or history is unavailable.
 * - Missing history MUST NOT be fabricated or rendered as 0 minutes of actual usage.
 *
 * @property dayLabel Short display label for chart axis (e.g. "Mon 7", "Tue 8", "Today")
 * @property dayOfWeek Abbreviated day of week (e.g. "Mon", "Tue")
 * @property dateEpochMillis Start of calendar day timestamp (UTC/Local midnight)
 * @property totalScreenTimeMs Total recorded screen time in milliseconds (0 if missing)
 * @property hasRecord Whether valid operating system usage data was recorded for this day
 * @property isCurrentDay Whether this record corresponds to the current calendar day ("Today")
 */
data class DailyUsageRecord(
    val dayLabel: String,
    val dayOfWeek: String,
    val dateEpochMillis: Long,
    val totalScreenTimeMs: Long = 0L,
    val hasRecord: Boolean = true,
    val isCurrentDay: Boolean = false
) {
    /**
     * Formats the duration into a human-readable string (e.g. "2h 15m", "45m", or "No data").
     */
    val formattedDuration: String
        get() {
            if (!hasRecord) return "No data"
            val totalMinutes = totalScreenTimeMs / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}
