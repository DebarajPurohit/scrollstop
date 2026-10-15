package com.scrollstop.domain.model

import android.graphics.drawable.Drawable

/**
 * Domain representation of an application monitored on the Home Dashboard.
 *
 * Implements approved UI_SPEC.md requirements (Section 3.3.5):
 * - Today's usage duration
 * - Configured daily limit (or null / "No limit")
 * - Cumulative usage with explicit observation window annotation (e.g. "(7d)" or "(4d of data)")
 * - Non-fabrication: Never invents usage or observation windows.
 */
data class MonitoredAppUsage(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val todayUsageMs: Long = 0L,
    val dailyLimitMs: Long? = null,
    val cumulativeUsageMs: Long = 0L,
    val observationDays: Int = 0
) {
    val formattedTodayUsage: String = formatDuration(todayUsageMs)
    val formattedLimit: String = dailyLimitMs?.let { "${formatDuration(it)} daily" } ?: "No limit"
    val formattedCumulative: String = if (observationDays >= 7) {
        "${formatDuration(cumulativeUsageMs)} (7d)"
    } else if (observationDays > 0) {
        "${formatDuration(cumulativeUsageMs)} (${observationDays}d of data)"
    } else {
        formatDuration(cumulativeUsageMs)
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val totalMinutes = ms / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
    }
}
