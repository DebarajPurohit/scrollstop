package com.scrollstop.domain.util

import java.util.Locale

/**
 * Utility for formatting duration in milliseconds into human-readable strings.
 */
object UsageFormatter {

    /**
     * Formats duration in milliseconds to a human-readable duration string.
     *
     * Examples:
     * - 0 ms -> "0 min"
     * - 12,000 ms -> "12 sec"
     * - 492,000 ms -> "8 min 12 sec"
     * - 1,421,000 ms -> "23 min 41 sec"
     * - 1,628,000 ms -> "27 min 08 sec"
     * - 3,600,000 ms -> "1 hr"
     * - 4,500,000 ms -> "1 hr 15 min"
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) {
            return "0 min"
        }

        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 && seconds > 0 -> {
                String.format(Locale.US, "%d hr %d min %02d sec", hours, minutes, seconds)
            }
            hours > 0 && minutes > 0 -> {
                String.format(Locale.US, "%d hr %d min", hours, minutes)
            }
            hours > 0 -> {
                String.format(Locale.US, "%d hr", hours)
            }
            minutes > 0 && seconds > 0 -> {
                String.format(Locale.US, "%d min %02d sec", minutes, seconds)
            }
            minutes > 0 -> {
                String.format(Locale.US, "%d min", minutes)
            }
            else -> {
                String.format(Locale.US, "%d sec", seconds)
            }
        }
    }
}
