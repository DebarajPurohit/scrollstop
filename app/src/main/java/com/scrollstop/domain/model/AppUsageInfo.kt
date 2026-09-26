package com.scrollstop.domain.model

import android.graphics.drawable.Drawable

/**
 * Represents today's foreground usage for an application.
 *
 * @property packageName Unique Android package identifier (e.g. "com.instagram.android")
 * @property appLabel Human-readable display name of the application (e.g. "Instagram")
 * @property icon Optional Drawable application icon
 * @property totalTimeInForegroundMs Duration spent in foreground today in milliseconds
 * @property formattedUsage Human-readable formatted string of usage duration (e.g. "23 min 41 sec", "0 min")
 */
data class AppUsageInfo(
    val packageName: String,
    val appLabel: String,
    val icon: Drawable? = null,
    val totalTimeInForegroundMs: Long = 0L,
    val formattedUsage: String = "0 min"
)
