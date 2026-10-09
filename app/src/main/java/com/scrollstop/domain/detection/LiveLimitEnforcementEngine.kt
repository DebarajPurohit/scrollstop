package com.scrollstop.domain.detection

import android.os.SystemClock
import android.util.Log
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.EnforcementTriggerRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Deterministic engine responsible for monitoring foreground restricted applications
 * and evaluating live limit-crossing breaches (POC-05A).
 *
 * Architecture & Compliance:
 * - Scoped STRICTLY to the currently foreground selected restricted app.
 * - Does NOT maintain an independent usage timer or counter.
 * - Queries [UsageStatsRepository] as the single source of truth for accumulated usage.
 * - Delegates all limit evaluations to [UsageRuleEngine].
 * - Cancels immediately when the restricted app is no longer foreground.
 * - Cancels immediately after a blocking trigger is emitted.
 * - Avoids duplicate monitors upon repeated window events for the same package.
 */
class LiveLimitEnforcementEngine(
    private val selectedAppsRepository: SelectedAppsRepository,
    private val dailyLimitRepository: DailyLimitRepository,
    private val usageStatsRepository: UsageStatsRepository,
    private val usageRuleEngine: UsageRuleEngine = UsageRuleEngine(),
    private val enforcementTriggerRepository: EnforcementTriggerRepository,
    private val selfPackageName: String = DEFAULT_SELF_PACKAGE,
    val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val isTransientPackage: (String) -> Boolean = { pkg ->
        pkg.equals("android", ignoreCase = true) ||
        pkg.equals("com.android.systemui", ignoreCase = true)
    },
    var onTriggerEmitted: ((EnforcementTrigger) -> Unit)? = null
) {
    companion object {
        const val DEFAULT_SELF_PACKAGE = "com.scrollstop"
        /**
         * Sensible check interval: 5 seconds.
         * Rationale:
         * 1. Responsiveness: Enforces limit breach within 0-5 seconds of crossing.
         * 2. Battery & Performance: Strictly active ONLY while restricted app is foreground;
         *    at 12 queries per minute, CPU impact is negligible (< 1ms IPC).
         * 3. Zero overhead: 0 queries/timers when in unselected apps, Stop Doom Scroll, or launcher.
         */
        const val DEFAULT_CHECK_INTERVAL_MS = 5_000L
        private const val TAG = "ScrollStopDebug"
    }

    private val lock = Any()
    private var monitoringJob: Job? = null

    @Volatile
    var currentMonitoredPackage: String? = null
        private set

    val isMonitoring: Boolean
        get() = synchronized(lock) { monitoringJob?.isActive == true }

    /**
     * Notifies the engine that the foreground package has changed or emitted a window state event.
     */
    fun onForegroundPackageChanged(packageName: String?) {
        var immediateTrigger: EnforcementTrigger? = null

        synchronized(lock) {
            if (packageName.isNullOrBlank()) {
                stopMonitoringLocked()
                return
            }

            val pkg = packageName.trim()

            // Ignore self package (Stop Doom Scroll dashboard / blocking screen)
            if (pkg.equals(selfPackageName, ignoreCase = true)) {
                stopMonitoringLocked()
                return
            }

            // If an active restricted app is currently being monitored, check if the incoming window
            // event belongs to a transient system overlay or keyboard (IME). If so, ignore the transient
            // window without stopping the continuous monitor for the active app.
            if (currentMonitoredPackage != null && isTransientPackage(pkg)) {
                safeLogD(TAG, "LIVE_MONITOR_TRANSIENT_SYSTEM_EVENT pkg=$pkg activeMonitor=$currentMonitoredPackage")
                return
            }

            val isSelected = selectedAppsRepository.isSelected(pkg)
            enforcementTriggerRepository.recordDetection(pkg, isSelected)

            // Stop monitoring if new foreground package is unselected
            if (!isSelected) {
                safeLogD(TAG, "LIVE_MONITOR_UNSELECTED package=$pkg")
                stopMonitoringLocked()
                return
            }

            // If already monitoring this exact package and job is active, do not duplicate
            if (currentMonitoredPackage == pkg && monitoringJob?.isActive == true) {
                safeLogD(TAG, "LIVE_MONITOR_ALREADY_ACTIVE package=$pkg")
                return
            }

            // Cancel any monitoring for a different previous package
            stopMonitoringLocked()

            // Immediate check (POC-05 behavior for already-over-limit apps)
            val usageResult = usageStatsRepository.getTodayUsageForPackages(setOf(pkg))
            val usedMs = usageResult.getOrNull()?.get(pkg) ?: 0L
            val limitMs = dailyLimitRepository.getLimitForPackage(pkg)
            val evaluation = usageRuleEngine.evaluate(pkg, usedMs, limitMs)
            val willTrigger = evaluation.limitState == LimitState.LIMIT_REACHED
            val timestamp = timeProvider()

            safeLogD(TAG, "LIVE_INITIAL_EVAL time=$timestamp package=$pkg usedMs=$usedMs limitMs=$limitMs state=${evaluation.limitState} trigger=$willTrigger")
            enforcementTriggerRepository.recordRuleEvaluation(pkg, usedMs, limitMs, evaluation.limitState)
            enforcementTriggerRepository.recordMonitoringCycle(
                timestamp = timestamp,
                packageName = pkg,
                isSelected = true,
                isMonitoringActive = !willTrigger,
                currentUsageMs = usedMs,
                configuredLimitMs = limitMs,
                ruleResult = evaluation.limitState,
                triggerEmitted = willTrigger,
                launchAttempted = willTrigger
            )

            if (willTrigger) {
                val trigger = EnforcementTrigger(
                    packageName = pkg,
                    reason = LimitState.LIMIT_REACHED,
                    detectedAtElapsedRealtimeMs = timestamp
                )
                safeLogD(TAG, "IMMEDIATE_LIMIT_REACHED package=$pkg")
                enforcementTriggerRepository.emitTrigger(trigger)
                immediateTrigger = trigger
                return@synchronized
            }

            // App is within limit: start continuous foreground monitoring (POC-05A)
            safeLogD(TAG, "STARTING_LIVE_MONITORING package=$pkg")
            startMonitoringLocked(pkg)
        }

        immediateTrigger?.let { trigger ->
            onTriggerEmitted?.invoke(trigger)
        }
    }

    private fun startMonitoringLocked(pkg: String) {
        currentMonitoredPackage = pkg
        monitoringJob = scope.launch(dispatcher) {
            while (isActive) {
                delay(checkIntervalMs)
                if (!isActive) break

                var reachedTrigger: EnforcementTrigger? = null
                synchronized(lock) {
                    if (currentMonitoredPackage != pkg) return@launch

                    if (!selectedAppsRepository.isSelected(pkg)) {
                        stopMonitoringLocked()
                        return@launch
                    }

                    // Query authoritative UsageStatsManager
                    val usageResult = usageStatsRepository.getTodayUsageForPackages(setOf(pkg))
                    val currentUsageMs = usageResult.getOrNull()?.get(pkg) ?: 0L
                    val currentLimitMs = dailyLimitRepository.getLimitForPackage(pkg)
                    val evaluation = usageRuleEngine.evaluate(pkg, currentUsageMs, currentLimitMs)
                    val willTrigger = evaluation.limitState == LimitState.LIMIT_REACHED
                    val timestamp = timeProvider()

                    safeLogD(TAG, "LIVE_POLL_EVAL time=$timestamp package=$pkg usedMs=$currentUsageMs limitMs=$currentLimitMs state=${evaluation.limitState} trigger=$willTrigger launch=$willTrigger")
                    enforcementTriggerRepository.recordRuleEvaluation(pkg, currentUsageMs, currentLimitMs, evaluation.limitState)
                    enforcementTriggerRepository.recordMonitoringCycle(
                        timestamp = timestamp,
                        packageName = pkg,
                        isSelected = true,
                        isMonitoringActive = !willTrigger,
                        currentUsageMs = currentUsageMs,
                        configuredLimitMs = currentLimitMs,
                        ruleResult = evaluation.limitState,
                        triggerEmitted = willTrigger,
                        launchAttempted = willTrigger
                    )

                    if (willTrigger) {
                        val trigger = EnforcementTrigger(
                            packageName = pkg,
                            reason = LimitState.LIMIT_REACHED,
                            detectedAtElapsedRealtimeMs = timestamp
                        )
                        safeLogD(TAG, "LIVE_BREACH_DETECTED package=$pkg")
                        enforcementTriggerRepository.emitTrigger(trigger)
                        reachedTrigger = trigger
                        stopMonitoringLocked()
                    }
                }

                val finalTrigger = reachedTrigger
                if (finalTrigger != null) {
                    onTriggerEmitted?.invoke(finalTrigger)
                    break
                }
            }
        }
    }

    /**
     * Stops and cancels any active foreground monitoring.
     */
    fun stopMonitoring() {
        synchronized(lock) {
            stopMonitoringLocked()
        }
    }

    private fun stopMonitoringLocked() {
        if (currentMonitoredPackage != null || monitoringJob != null) {
            safeLogD(TAG, "STOPPED_LIVE_MONITORING previous=$currentMonitoredPackage")
        }
        currentMonitoredPackage = null
        monitoringJob?.cancel()
        monitoringJob = null
    }

    private fun safeLogD(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (e: Throwable) {
            println("$tag: $message")
        }
    }
}
