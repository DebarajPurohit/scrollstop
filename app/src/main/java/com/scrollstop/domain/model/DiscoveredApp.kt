package com.scrollstop.domain.model

import android.graphics.drawable.Drawable

/**
 * Represents an application discovered on the user's device that can be launched from the launcher.
 *
 * @property packageName Unique Android package identifier (e.g. "com.example.app")
 * @property appLabel Human-readable display name of the application (e.g. "Social App")
 * @property icon Optional Drawable application icon
 * @property isSelected Whether this application is currently selected by the user for control
 */
data class DiscoveredApp(
    val packageName: String,
    val appLabel: String,
    val icon: Drawable? = null,
    val isSelected: Boolean = false
)
