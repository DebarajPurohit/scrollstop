package com.scrollstop.data.repository

/**
 * Repository interface managing the initial Welcome onboarding state and session identity.
 */
interface OnboardingRepository {
    fun hasCompletedWelcome(): Boolean
    fun setWelcomeCompleted(completed: Boolean, sessionType: String = SESSION_TYPE_GUEST)
    fun hasCompletedAccessibilityOnboarding(): Boolean = false
    fun setAccessibilityOnboardingCompleted(completed: Boolean) {}
    fun getSessionType(): String
    fun resetForTesting()

    companion object {
        const val SESSION_TYPE_GUEST = "GUEST"
        const val SESSION_TYPE_STANDARD = "STANDARD"
        @Deprecated("Replaced with SESSION_TYPE_STANDARD to avoid misleading authentication implying", ReplaceWith("SESSION_TYPE_STANDARD"))
        const val SESSION_TYPE_GOOGLE = "GOOGLE"
    }
}

