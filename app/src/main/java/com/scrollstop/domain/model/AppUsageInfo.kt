package com.scrollstop.domain.model

import android.graphics.drawable.Drawable

import com.scrollstop.domain.rules.LimitState

/**
 * Represents today's foreground usage and limit evaluation for an application.
 *
 * @property packageName Unique Android package identifier (e.g. "com.instagram.android")
 * @property appLabel Human-readable display name of the application (e.g. "Instagram")
 * @property icon Optional Drawable application icon
 * @property totalTimeInForegroundMs Duration spent in foreground today in milliseconds
 * @property formattedUsage Human-readable formatted string of usage duration (e.g. "23 min 41 sec", "0 min")
 * @property limitDurationMs Configured daily limit in milliseconds
 * @property remainingDurationMs Remaining duration allowance in milliseconds
 * @property limitState Evaluated deterministic limit state ([LimitState.NOT_STARTED], [LimitState.WITHIN_LIMIT], [LimitState.LIMIT_REACHED])
 * @property formattedLimit Formatted daily limit string (e.g. "2 min")
 * @property formattedRemaining Formatted remaining allowance string (e.g. "1 min 30 sec", "0 min")
 */
data class AppUsageInfo(
    val packageName: String,
    val appLabel: String,
    val icon: Drawable? = null,
    val totalTimeInForegroundMs: Long = 0L,
    val formattedUsage: String = "0 min",
    val limitDurationMs: Long = 120_000L,
    val remainingDurationMs: Long = 120_000L,
    val limitState: LimitState = LimitState.NOT_STARTED,
    val formattedLimit: String = "2 min",
    val formattedRemaining: String = "2 min"
)

