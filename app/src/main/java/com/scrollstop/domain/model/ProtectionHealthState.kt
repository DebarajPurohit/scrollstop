package com.scrollstop.domain.model

/**
 * Unified health state representing the complete protection readiness of Stop Doom Scroll.
 *
 * Protection is only fully active when BOTH Usage Access (to measure usage) and
 * AccessibilityService (to detect and stop restricted apps) are operating correctly.
 */
enum class ProtectionHealthState {
    /** Both Usage Access is granted and Accessibility Service is actively connected. */
    PROTECTED,

    /** Usage Access permission is missing or revoked, but Accessibility Service is active. */
    USAGE_ACCESS_REQUIRED,

    /** Usage Access is granted, but Accessibility Service is disabled or not granted. */
    ACCESSIBILITY_REQUIRED,

    /** Usage Access is granted, but Accessibility Service was interrupted by system or OEM. */
    ACCESSIBILITY_INTERRUPTED,

    /** Both Usage Access and Accessibility Service are missing/disabled. */
    DEGRADED
}
