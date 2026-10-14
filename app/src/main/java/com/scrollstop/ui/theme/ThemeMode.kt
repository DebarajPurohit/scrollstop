package com.scrollstop.ui.theme

/**
 * Supported theme modes for Stop Doom Scroll.
 *
 * Follows the visual architecture defined in UI_SPEC.md:
 * - [SYSTEM]: Follows Android system dark/light configuration (default).
 * - [LIGHT]: Forces the approved Slate/Emerald Light theme.
 * - [DARK]: Forces the approved Slate/Emerald Dark theme.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
