package com.scrollstop.data.di

import android.content.Context
import com.scrollstop.data.repository.AccessibilityServiceHealthRepository
import com.scrollstop.data.repository.AndroidAccessibilityServiceHealthRepository
import com.scrollstop.data.repository.AndroidUsageStatsRepository
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.EnforcementTriggerRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.SharedPreferencesDailyLimitRepository
import com.scrollstop.data.repository.SharedPreferencesEnforcementTriggerRepository
import com.scrollstop.data.repository.SharedPreferencesSelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.rules.UsageRuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive

/**
 * Lightweight Thread-safe Service Locator providing access to shared repository instances.
 */
object ServiceLocator {
    @Volatile
    private var selectedAppsRepository: SelectedAppsRepository? = null

    @Volatile
    private var dailyLimitRepository: DailyLimitRepository? = null

    @Volatile
    private var usageStatsRepository: UsageStatsRepository? = null

    @Volatile
    private var enforcementTriggerRepository: EnforcementTriggerRepository? = null

    @Volatile
    private var accessibilityHealthRepository: AccessibilityServiceHealthRepository? = null

    @Volatile
    private var usageRuleEngine: UsageRuleEngine? = null

    fun getSelectedAppsRepository(context: Context): SelectedAppsRepository {
        return selectedAppsRepository ?: synchronized(this) {
            selectedAppsRepository ?: SharedPreferencesSelectedAppsRepository(context.applicationContext).also {
                selectedAppsRepository = it
            }
        }
    }

    fun getDailyLimitRepository(context: Context): DailyLimitRepository {
        return dailyLimitRepository ?: synchronized(this) {
            dailyLimitRepository ?: SharedPreferencesDailyLimitRepository(context.applicationContext).also {
                dailyLimitRepository = it
            }
        }
    }

    fun getUsageStatsRepository(context: Context): UsageStatsRepository {
        return usageStatsRepository ?: synchronized(this) {
            usageStatsRepository ?: AndroidUsageStatsRepository(context.applicationContext).also {
                usageStatsRepository = it
            }
        }
    }

    fun getEnforcementTriggerRepository(context: Context? = null): EnforcementTriggerRepository {
        return enforcementTriggerRepository ?: synchronized(this) {
            enforcementTriggerRepository ?: if (context != null) {
                SharedPreferencesEnforcementTriggerRepository(context.applicationContext).also {
                    enforcementTriggerRepository = it
                }
            } else {
                InMemoryEnforcementTriggerRepository().also {
                    enforcementTriggerRepository = it
                }
            }
        }
    }

    @Volatile
    private var liveLimitEnforcementEngine: com.scrollstop.domain.detection.LiveLimitEnforcementEngine? = null

    fun getAccessibilityHealthRepository(): AccessibilityServiceHealthRepository {
        return accessibilityHealthRepository ?: synchronized(this) {
            accessibilityHealthRepository ?: AndroidAccessibilityServiceHealthRepository().also {
                accessibilityHealthRepository = it
            }
        }
    }

    fun getUsageRuleEngine(): UsageRuleEngine {
        return usageRuleEngine ?: synchronized(this) {
            usageRuleEngine ?: UsageRuleEngine().also {
                usageRuleEngine = it
            }
        }
    }

    fun getLiveLimitEnforcementEngine(
        context: Context,
        scope: CoroutineScope,
        checkIntervalMs: Long = com.scrollstop.domain.detection.LiveLimitEnforcementEngine.DEFAULT_CHECK_INTERVAL_MS,
        isTransientPackage: (String) -> Boolean = { pkg ->
            com.scrollstop.util.ImePackageDetector(context).isImeOrTransient(pkg)
        },
        onTriggerEmitted: ((com.scrollstop.domain.model.EnforcementTrigger) -> Unit)? = null
    ): com.scrollstop.domain.detection.LiveLimitEnforcementEngine {
        val existing = liveLimitEnforcementEngine
        if (existing != null && scope.isActive) {
            existing.isTransientPackage = isTransientPackage
            if (onTriggerEmitted != null) {
                existing.onTriggerEmitted = onTriggerEmitted
            }
            return existing
        }
        return synchronized(this) {
            val current = liveLimitEnforcementEngine
            if (current != null && scope.isActive) {
                current.isTransientPackage = isTransientPackage
                if (onTriggerEmitted != null) {
                    current.onTriggerEmitted = onTriggerEmitted
                }
                current
            } else {
                current?.stopMonitoring()
                com.scrollstop.domain.detection.LiveLimitEnforcementEngine(
                    selectedAppsRepository = getSelectedAppsRepository(context),
                    dailyLimitRepository = getDailyLimitRepository(context),
                    usageStatsRepository = getUsageStatsRepository(context),
                    usageRuleEngine = getUsageRuleEngine(),
                    enforcementTriggerRepository = getEnforcementTriggerRepository(context),
                    selfPackageName = context.packageName,
                    checkIntervalMs = checkIntervalMs,
                    scope = scope,
                    isTransientPackage = isTransientPackage,
                    onTriggerEmitted = onTriggerEmitted
                ).also {
                    liveLimitEnforcementEngine = it
                }
            }
        }
    }

    fun resetLiveLimitEnforcementEngine() {
        synchronized(this) {
            liveLimitEnforcementEngine?.stopMonitoring()
            liveLimitEnforcementEngine = null
        }
    }

    fun resetForTesting(
        selectedAppsRepo: SelectedAppsRepository? = null,
        dailyLimitRepo: DailyLimitRepository? = null,
        usageStatsRepo: UsageStatsRepository? = null,
        triggerRepo: EnforcementTriggerRepository? = null,
        healthRepo: AccessibilityServiceHealthRepository? = null,
        ruleEngine: UsageRuleEngine? = null,
        liveEngine: com.scrollstop.domain.detection.LiveLimitEnforcementEngine? = null
    ) {
        selectedAppsRepository = selectedAppsRepo
        dailyLimitRepository = dailyLimitRepo
        usageStatsRepository = usageStatsRepo
        enforcementTriggerRepository = triggerRepo
        accessibilityHealthRepository = healthRepo
        usageRuleEngine = ruleEngine
        liveLimitEnforcementEngine = liveEngine
    }
}
