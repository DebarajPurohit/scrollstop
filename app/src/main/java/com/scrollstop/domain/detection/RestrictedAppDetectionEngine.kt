package com.scrollstop.domain.detection

import android.view.accessibility.AccessibilityEvent
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine

/**
 * Pure, deterministic detection engine that evaluates foreground app window events.
 *
 * Responsibilities:
 * - Filter events to [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
 * - Inspect ONLY package name string
 * - Validate against user-selected restricted applications
 * - Query today's accumulated usage via [UsageStatsRepository]
 * - Delegate rule evaluation to [UsageRuleEngine]
 * - Produce internal [EnforcementTrigger] ONLY when limit state is [LimitState.LIMIT_REACHED]
 *
 * This engine explicitly DOES NOT:
 * - Inspect window text or view hierarchy
 * - Read content descriptions, messages, passwords, or keystrokes
 * - Perform UI blocking or navigation
 * - Maintain continuous background timers
 */
class RestrictedAppDetectionEngine(
    private val selectedAppsRepository: SelectedAppsRepository,
    private val dailyLimitRepository: DailyLimitRepository,
    private val usageStatsRepository: UsageStatsRepository,
    private val usageRuleEngine: UsageRuleEngine = UsageRuleEngine(),
    private val selfPackageName: String = DEFAULT_SELF_PACKAGE
) {
    companion object {
        const val DEFAULT_SELF_PACKAGE = "com.scrollstop"
    }

    /**
     * Processes a window event given a package name and event type.
     *
     * @param packageName Package identifier of the foreground window event.
     * @param eventType Accessibility event type integer (e.g. [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]).
     * @param elapsedRealtimeMs Monotonic timestamp for detection tracking.
     * @return [EnforcementTrigger] if selected package has reached its limit, otherwise `null`.
     */
    fun processEvent(
        packageName: String?,
        eventType: Int,
        elapsedRealtimeMs: Long = 0L
    ): EnforcementTrigger? {
        // 1. Ignore events that are not TYPE_WINDOW_STATE_CHANGED
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return null
        }

        // 2. Ignore null or blank package names
        if (packageName.isNullOrBlank()) {
            return null
        }

        val pkg = packageName.trim()

        // 3. Ignore self package (Stop Doom Scroll)
        if (pkg.equals(selfPackageName, ignoreCase = true)) {
            return null
        }

        // 4. Ignore unselected packages
        if (!selectedAppsRepository.isSelected(pkg)) {
            return null
        }

        // 5. Retrieve usage stats for the detected package
        val usageResult = usageStatsRepository.getTodayUsageForPackages(setOf(pkg))
        val usedMs = usageResult.getOrNull()?.get(pkg) ?: 0L

        // 6. Retrieve configured limit for the package
        val limitMs = dailyLimitRepository.getLimitForPackage(pkg)

        // 7. Evaluate rule state via UsageRuleEngine
        val evaluation = usageRuleEngine.evaluate(
            packageName = pkg,
            usedDurationMs = usedMs,
            limitDurationMs = limitMs
        )

        // 8. Emit trigger ONLY if limit state is LIMIT_REACHED
        return if (evaluation.limitState == LimitState.LIMIT_REACHED) {
            EnforcementTrigger(
                packageName = pkg,
                reason = LimitState.LIMIT_REACHED,
                detectedAtElapsedRealtimeMs = elapsedRealtimeMs
            )
        } else {
            null
        }
    }
}
