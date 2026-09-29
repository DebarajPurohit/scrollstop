package com.scrollstop.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.AccessibilityServiceHealthRepository
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.EnforcementTriggerRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.detection.RestrictedAppDetectionEngine
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.domain.rules.UsageRuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Narrow, deterministic AccessibilityService designed strictly to detect when a user-selected
 * restricted application is opened in the foreground.
 *
 * Privacy & Security Compliance:
 * - This service is NOT an accessibility tool for users with disabilities.
 * - DOES NOT request or inspect window text, node hierarchy, messages, passwords, or keystrokes.
 * - DOES NOT perform autonomous gestures or screen interaction.
 * - Uses narrow package filtering and [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] events only.
 * - Delegates usage tracking to [UsageStatsRepository] and rule evaluation to [UsageRuleEngine].
 */
class RestrictedAppAccessibilityService : AccessibilityService() {

    private lateinit var selectedAppsRepository: SelectedAppsRepository
    private lateinit var dailyLimitRepository: DailyLimitRepository
    private lateinit var usageStatsRepository: UsageStatsRepository
    private lateinit var enforcementTriggerRepository: EnforcementTriggerRepository
    private lateinit var healthRepository: AccessibilityServiceHealthRepository
    private lateinit var detectionEngine: RestrictedAppDetectionEngine

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        selectedAppsRepository = ServiceLocator.getSelectedAppsRepository(this)
        dailyLimitRepository = ServiceLocator.getDailyLimitRepository(this)
        usageStatsRepository = ServiceLocator.getUsageStatsRepository(this)
        enforcementTriggerRepository = ServiceLocator.getEnforcementTriggerRepository()
        healthRepository = ServiceLocator.getAccessibilityHealthRepository()
        val ruleEngine = ServiceLocator.getUsageRuleEngine()

        detectionEngine = RestrictedAppDetectionEngine(
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            usageStatsRepository = usageStatsRepository,
            usageRuleEngine = ruleEngine,
            selfPackageName = packageName
        )
    }

    public override fun onServiceConnected() {
        super.onServiceConnected()
        healthRepository.updateState(AccessibilityServiceState.ACCESSIBILITY_ACTIVE)

        updateServiceConfig(selectedAppsRepository.getSelectedPackages())

        // Dynamically update package filtering when selected packages change
        serviceScope.launch {
            selectedAppsRepository.selectedPackageNames.collectLatest { selectedSet ->
                updateServiceConfig(selectedSet)
            }
        }
    }

    var currentConfigInfo: AccessibilityServiceInfo? = null
        private set

    private fun updateServiceConfig(selectedPackages: Set<String>) {
        val info = serviceInfo ?: currentConfigInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100L
        info.flags = AccessibilityServiceInfo.DEFAULT

        // Window content inspection is prohibited by accessibility_service_config.xml (android:canRetrieveWindowContent="false")

        // Narrow package filtering where practical
        if (selectedPackages.isNotEmpty()) {
            info.packageNames = selectedPackages.toTypedArray()
        } else {
            // Placeholder array when no apps are selected to prevent subscribing to all system apps
            info.packageNames = arrayOf("com.scrollstop.dummy_filter_placeholder")
        }

        currentConfigInfo = info
        setServiceInfo(info)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val eventPkg = event.packageName?.toString() ?: return

        val trigger = detectionEngine.processEvent(
            packageName = eventPkg,
            eventType = event.eventType,
            elapsedRealtimeMs = SystemClock.elapsedRealtime()
        )

        if (trigger != null) {
            enforcementTriggerRepository.emitTrigger(trigger)
        }
    }

    override fun onInterrupt() {
        healthRepository.updateState(AccessibilityServiceState.ACCESSIBILITY_INTERRUPTED)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        healthRepository.updateState(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        healthRepository.updateState(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED)
        serviceScope.cancel()
        super.onDestroy()
    }
}
