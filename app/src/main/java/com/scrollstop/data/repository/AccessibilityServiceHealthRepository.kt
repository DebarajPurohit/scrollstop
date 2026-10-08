package com.scrollstop.data.repository

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.scrollstop.domain.model.AccessibilityServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository interface for observing Accessibility Service status and health.
 */
interface AccessibilityServiceHealthRepository {
    val serviceState: StateFlow<AccessibilityServiceState>
    fun isServiceEnabledInSettings(context: Context, serviceClass: Class<*>): Boolean
    fun updateState(newState: AccessibilityServiceState)
    fun refreshState(context: Context, serviceClass: Class<*>)
}

/**
 * Android implementation of [AccessibilityServiceHealthRepository].
 */
class AndroidAccessibilityServiceHealthRepository : AccessibilityServiceHealthRepository {

    private val _serviceState = MutableStateFlow(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED)
    override val serviceState: StateFlow<AccessibilityServiceState> = _serviceState.asStateFlow()

    override fun isServiceEnabledInSettings(context: Context, serviceClass: Class<*>): Boolean {
        return try {
            val expectedComponentName = ComponentName(context, serviceClass)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    override fun updateState(newState: AccessibilityServiceState) {
        _serviceState.value = newState
    }

    override fun refreshState(context: Context, serviceClass: Class<*>) {
        val isEnabled = isServiceEnabledInSettings(context, serviceClass)
        _serviceState.value = if (isEnabled) {
            AccessibilityServiceState.ACCESSIBILITY_ACTIVE
        } else {
            AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED
        }
    }
}
