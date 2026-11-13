package com.scrollstop.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed implementation of [OnboardingRepository].
 */
class SharedPreferencesOnboardingRepository(
    context: Context,
    prefName: String = PREF_NAME
) : OnboardingRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    override fun hasCompletedWelcome(): Boolean = prefs.getBoolean(KEY_WELCOME_COMPLETED, false)

    override fun setWelcomeCompleted(completed: Boolean, sessionType: String) {
        prefs.edit()
            .putBoolean(KEY_WELCOME_COMPLETED, completed)
            .putString(KEY_SESSION_TYPE, sessionType)
            .apply()
    }

    override fun hasCompletedAccessibilityOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ACCESSIBILITY_ONBOARDING_COMPLETED, false)
    }

    override fun setAccessibilityOnboardingCompleted(completed: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ACCESSIBILITY_ONBOARDING_COMPLETED, completed)
            .apply()
    }

    override fun getSessionType(): String {
        return prefs.getString(KEY_SESSION_TYPE, OnboardingRepository.SESSION_TYPE_GUEST)
            ?: OnboardingRepository.SESSION_TYPE_GUEST
    }

    override fun resetForTesting() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val PREF_NAME = "scrollstop_onboarding_prefs"
        private const val KEY_WELCOME_COMPLETED = "key_welcome_completed"
        private const val KEY_ACCESSIBILITY_ONBOARDING_COMPLETED = "key_accessibility_onboarding_completed"
        private const val KEY_SESSION_TYPE = "key_session_type"
    }
}

