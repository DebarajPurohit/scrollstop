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
import com.scrollstop.data.repository.SharedPreferencesSelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.rules.UsageRuleEngine

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

    fun getEnforcementTriggerRepository(): EnforcementTriggerRepository {
        return enforcementTriggerRepository ?: synchronized(this) {
            enforcementTriggerRepository ?: InMemoryEnforcementTriggerRepository().also {
                enforcementTriggerRepository = it
            }
        }
    }

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

    fun resetForTesting(
        selectedAppsRepo: SelectedAppsRepository? = null,
        dailyLimitRepo: DailyLimitRepository? = null,
        usageStatsRepo: UsageStatsRepository? = null,
        triggerRepo: EnforcementTriggerRepository? = null,
        healthRepo: AccessibilityServiceHealthRepository? = null,
        ruleEngine: UsageRuleEngine? = null
    ) {
        selectedAppsRepository = selectedAppsRepo
        dailyLimitRepository = dailyLimitRepo
        usageStatsRepository = usageStatsRepo
        enforcementTriggerRepository = triggerRepo
        accessibilityHealthRepository = healthRepo
        usageRuleEngine = ruleEngine
    }
}
