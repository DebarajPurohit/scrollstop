package com.scrollstop.domain.model

/**
 * Health and permission state of the AccessibilityService foreground app detection service.
 */
enum class AccessibilityServiceState {
    /** Accessibility service is not enabled in Android Settings or permission is missing. */
    ACCESSIBILITY_NOT_GRANTED,

    /** Accessibility service is enabled, connected, and actively monitoring window state events. */
    ACCESSIBILITY_ACTIVE,

    /** Accessibility service was connected but has been interrupted or paused by system/user. */
    ACCESSIBILITY_INTERRUPTED
}
