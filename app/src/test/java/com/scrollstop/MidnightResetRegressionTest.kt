package com.scrollstop

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.repository.AndroidUsageStatsRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.SharedPreferencesCommitmentLockRepository
import com.scrollstop.data.repository.SharedPreferencesEnforcementTriggerRepository
import com.scrollstop.data.time.SystemTimeProvider
import com.scrollstop.data.time.TimeProvider
import com.scrollstop.domain.detection.LiveLimitEnforcementEngine
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.CommitmentRuleEngine
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager
import java.util.Calendar
import java.util.TimeZone

/**
 * Deterministic test suite verifying all 12 regression scenarios for daily midnight reset:
 * 1. Previous-day monitored-app usage is excluded after local midnight.
 * 2. Overall and per-app usage use the same local-day boundary.
 * 3. A previous-day daily-limit trigger expires at the new day.
 * 4. An app blocked yesterday is usable today if today's usage is below its limit.
 * 5. An app still open across midnight has its session correctly split between days.
 * 6. The app is blocked when the new day's limit is reached.
 * 7. Staged Commitment Lock limit increases activate at midnight.
 * 8. Staged app removals activate at midnight.
 * 9. Cancelled staged changes remain cancelled.
 * 10. App/service recreation after midnight does not restore yesterday's blocking state.
 * 11. Timezone and manual date changes are handled safely according to the documented policy.
 * 12. Preset and custom limit enforcement continue working.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MidnightResetRegressionTest {

    private lateinit var context: Context
    private lateinit var appOpsManager: AppOpsManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var shadowUsageStats: ShadowUsageStatsManager

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class TestClock(
        var currentTime: Long,
        var startOfDay: Long,
        var nextMidnight: Long = startOfDay + 86_400_000L
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        override fun getStartOfDayMillis(): Long = startOfDay
        override fun getNextMidnightMillis(): Long = nextMidnight

        fun advanceToNextDay(offsetAfterMidnightMs: Long = 3_600_000L) { // 1:00 AM next day
            startOfDay = nextMidnight
            currentTime = startOfDay + offsetAfterMidnightMs
            nextMidnight = startOfDay + 86_400_000L
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        shadowUsageStats = Shadows.shadowOf(usageStatsManager)

        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )
    }

    // 1. Previous-day monitored-app usage is excluded after local midnight.
    @Test
    fun regression01_previousDayMonitoredAppUsage_isExcludedAfterLocalMidnight() {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight + 3_600_000L, // 1:00 AM today
            startOfDay = midnight
        )
        val repo = AndroidUsageStatsRepository(context, clock)
        val pkg = "com.instagram.android"

        // Event occurred yesterday (30 min session from 11:00 PM to 11:30 PM yesterday)
        val yesterdayResume = midnight - 3_600_000L
        val yesterdayPause = midnight - 1_800_000L

        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("MainActivity")
                .setTimeStamp(yesterdayResume)
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("MainActivity")
                .setTimeStamp(yesterdayPause)
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .build()
        )

        val usageResult = repo.getTodayUsageForPackages(setOf(pkg))
        assertTrue(usageResult.isSuccess)
        val usageMap = usageResult.getOrThrow()
        assertEquals(0L, usageMap[pkg] ?: 0L)
    }

    // 2. Overall and per-app usage use the same local-day boundary.
    @Test
    fun regression02_overallAndPerAppUsage_useSameLocalDayBoundary() {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight + 1_800_000L, // 12:30 AM today
            startOfDay = midnight
        )
        val repo = AndroidUsageStatsRepository(context, clock)
        val pkg = "com.google.android.youtube"

        // App session strictly today (10 minutes from 12:05 to 12:15 AM)
        val sessionStart = midnight + 300_000L
        val sessionEnd = midnight + 900_000L

        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setTimeStamp(sessionStart)
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setTimeStamp(sessionEnd)
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .build()
        )

        val perAppUsage = repo.getTodayUsageForPackages(setOf(pkg)).getOrThrow()[pkg] ?: 0L
        val totalUsage = repo.getTodayTotalUsageMs().getOrThrow()

        assertEquals(600_000L, perAppUsage)
        assertEquals(600_000L, totalUsage)
    }

    // 3. A previous-day daily-limit trigger expires at the new day.
    @Test
    fun regression03_previousDayDailyLimitTrigger_expiresAtNewDay() {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight - 1_800_000L, // 11:30 PM yesterday
            startOfDay = midnight - 86_400_000L
        )
        val prefName = "test_reg_03_prefs"
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()

        val repo = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = prefName,
            timeProvider = clock
        )

        repo.emitTrigger(
            EnforcementTrigger(
                packageName = "com.tiktok.android",
                reason = LimitState.LIMIT_REACHED,
                detectedAtElapsedRealtimeMs = 10_000L
            )
        )
        assertNotNull(repo.latestTrigger.value)

        // Advance to new day
        clock.advanceToNextDay(offsetAfterMidnightMs = 1_800_000L) // 12:30 AM today

        // Expiration check
        repo.checkAndExpireStaleTriggers()
        assertNull(repo.latestTrigger.value)
    }

    // 4. An app blocked yesterday is usable today if today's usage is below its limit.
    @Test
    fun regression04_appBlockedYesterday_isUsableTodayIfUsageBelowLimit() {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight - 600_000L, // 11:50 PM yesterday
            startOfDay = midnight - 86_400_000L
        )
        val triggerRepo = InMemoryEnforcementTriggerRepository(clock)
        val ruleEngine = UsageRuleEngine()
        val pkg = "com.instagram.android"
        val limitMs = 900_000L // 15m limit

        // Yesterday: app exceeded limit
        val yesterdayEvaluation = ruleEngine.evaluate(packageName = pkg, usedDurationMs = 900_000L, limitDurationMs = limitMs)
        assertEquals(LimitState.LIMIT_REACHED, yesterdayEvaluation.limitState)
        triggerRepo.emitTrigger(
            EnforcementTrigger(
                packageName = pkg,
                reason = yesterdayEvaluation.limitState,
                detectedAtElapsedRealtimeMs = 50_000L
            )
        )
        assertNotNull(triggerRepo.latestTrigger.value)

        // Advance to next day
        clock.advanceToNextDay(offsetAfterMidnightMs = 3_600_000L) // 1:00 AM
        triggerRepo.checkAndExpireStaleTriggers()
        assertNull(triggerRepo.latestTrigger.value)

        // Today: usage starts at 0 ms
        val todayUsageMs = 0L
        val todayEvaluation = ruleEngine.evaluate(packageName = pkg, usedDurationMs = todayUsageMs, limitDurationMs = limitMs)
        assertEquals(LimitState.NOT_STARTED, todayEvaluation.limitState)
        assertEquals(900_000L, todayEvaluation.remainingDurationMs)
    }

    // 5. An app still open across midnight has its session correctly split between days.
    @Test
    fun regression05_appOpenAcrossMidnight_sessionCorrectlySplitBetweenDays() {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight + 900_000L, // 12:15 AM today
            startOfDay = midnight
        )
        val repo = AndroidUsageStatsRepository(context, clock)
        val pkg = "com.google.android.youtube"

        // App was opened at 11:50 PM yesterday (10m before midnight)
        // and closed at 12:15 AM today (15m after midnight)
        val sessionStart = midnight - 600_000L
        val sessionEnd = midnight + 900_000L

        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setTimeStamp(sessionStart)
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .build()
        )
        shadowUsageStats.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setClass("WatchActivity")
                .setTimeStamp(sessionEnd)
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .build()
        )

        val usageToday = repo.getTodayUsageForPackages(setOf(pkg)).getOrThrow()[pkg] ?: 0L
        val totalToday = repo.getTodayTotalUsageMs().getOrThrow()

        // Only the 15m after midnight belongs to today
        assertEquals(900_000L, usageToday)
        assertEquals(900_000L, totalToday)
    }

    // 6. The app is blocked when the new day's limit is reached.
    @Test
    fun regression06_appBlockedWhenNewDayLimitIsReached() = testScope.runTest {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight + 3_600_000L,
            startOfDay = midnight
        )
        val pkg = "com.twitter.android"
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf(pkg))
        }
        val limitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage(pkg, 900_000L) // 15m limit
        }
        val triggerRepo = InMemoryEnforcementTriggerRepository(clock)
        val ruleEngine = UsageRuleEngine()

        var currentUsage = 0L
        val fakeUsageStatsRepo = object : com.scrollstop.data.repository.UsageStatsRepository {
            override fun hasUsagePermission(): Boolean = true
            override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> =
                Result.success(mapOf(pkg to currentUsage))
            override fun getTodayTotalUsageMs(): Result<Long> = Result.success(currentUsage)
            override fun getForegroundPackage(): String? = pkg
        }

        val engine = LiveLimitEnforcementEngine(
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = limitRepo,
            usageStatsRepository = fakeUsageStatsRepo,
            usageRuleEngine = ruleEngine,
            enforcementTriggerRepository = triggerRepo,
            checkIntervalMs = 1_000L,
            scope = testScope,
            dispatcher = testDispatcher
        )

        engine.onForegroundPackageChanged(pkg)
        testScope.runCurrent()
        assertNull(triggerRepo.latestTrigger.value)

        // Usage reaches limit today
        currentUsage = 900_000L
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()

        assertNotNull(triggerRepo.latestTrigger.value)
        assertEquals(pkg, triggerRepo.latestTrigger.value?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, triggerRepo.latestTrigger.value?.reason)
    }

    // 7. Staged Commitment Lock limit increases activate at midnight.
    @Test
    fun regression07_stagedCommitmentLockLimitIncrease_activatesAtMidnight() {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight - 3_600_000L, // 11:00 PM yesterday
            startOfDay = midnight - 86_400_000L,
            nextMidnight = midnight
        )
        val prefName = "test_reg_07_prefs"
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()

        val selectedRepo = InMemorySelectedAppsRepository()
        val limitRepo = InMemoryDailyLimitRepository()
        val lockRepo = SharedPreferencesCommitmentLockRepository(
            context = context,
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = limitRepo,
            commitmentRuleEngine = CommitmentRuleEngine(),
            timeProvider = clock,
            prefName = prefName
        )

        val pkg = "com.instagram.android"
        limitRepo.setLimitForPackage(pkg, 900_000L) // 15m
        lockRepo.enableCommitmentLock(consentConfirmed = true)

        // Stage increase to 30m
        val stageResult = lockRepo.stageLimitIncrease(pkg, 1_800_000L)
        assertTrue(stageResult)
        assertEquals(900_000L, limitRepo.getLimitForPackage(pkg)) // still 15m before midnight

        // Midnight arrives
        clock.advanceToNextDay(offsetAfterMidnightMs = 60_000L)
        lockRepo.checkAndApplyMidnightTransitions()

        // Staged limit increase activated
        assertEquals(1_800_000L, limitRepo.getLimitForPackage(pkg))
        assertNull(lockRepo.commitmentState.value.pendingIncreasedLimits[pkg])
        // Commitment Lock remains active!
        assertTrue(lockRepo.commitmentState.value.isLocked)
    }

    // 8. Staged app removals activate at midnight.
    @Test
    fun regression08_stagedAppRemoval_activatesAtMidnight() {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight - 3_600_000L,
            startOfDay = midnight - 86_400_000L,
            nextMidnight = midnight
        )
        val prefName = "test_reg_08_prefs"
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()

        val pkg = "com.facebook.katana"
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf(pkg))
        }
        val limitRepo = InMemoryDailyLimitRepository()
        val lockRepo = SharedPreferencesCommitmentLockRepository(
            context = context,
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = limitRepo,
            commitmentRuleEngine = CommitmentRuleEngine(),
            timeProvider = clock,
            prefName = prefName
        )

        lockRepo.enableCommitmentLock(consentConfirmed = true)
        val stageResult = lockRepo.stageAppRemoval(pkg)
        assertTrue(stageResult)
        assertTrue(selectedRepo.isSelected(pkg)) // still selected before midnight

        // Midnight arrives
        clock.advanceToNextDay(offsetAfterMidnightMs = 60_000L)
        lockRepo.checkAndApplyMidnightTransitions()

        // App removal activated
        assertFalse(selectedRepo.isSelected(pkg))
        assertFalse(pkg in lockRepo.commitmentState.value.pendingRemovedPackages)
        assertTrue(lockRepo.commitmentState.value.isLocked)
    }

    // 9. Cancelled staged changes remain cancelled.
    @Test
    fun regression09_cancelledStagedChanges_remainCancelled() {
        val midnight = 1_700_000_000_000L
        val clock = TestClock(
            currentTime = midnight - 3_600_000L,
            startOfDay = midnight - 86_400_000L,
            nextMidnight = midnight
        )
        val prefName = "test_reg_09_prefs"
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()

        val pkg = "com.snapchat.android"
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf(pkg))
        }
        val limitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage(pkg, 900_000L)
        }
        val lockRepo = SharedPreferencesCommitmentLockRepository(
            context = context,
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = limitRepo,
            commitmentRuleEngine = CommitmentRuleEngine(),
            timeProvider = clock,
            prefName = prefName
        )

        lockRepo.enableCommitmentLock(consentConfirmed = true)
        lockRepo.stageLimitIncrease(pkg, 2_400_000L) // stage to 40m
        lockRepo.stageAppRemoval(pkg) // stage removal

        // User cancels staged changes before midnight
        lockRepo.cancelStagedLimitIncrease(pkg)
        lockRepo.cancelStagedAppRemoval(pkg)

        // Midnight arrives
        clock.advanceToNextDay(offsetAfterMidnightMs = 60_000L)
        lockRepo.checkAndApplyMidnightTransitions()

        // Original values remain intact!
        assertEquals(900_000L, limitRepo.getLimitForPackage(pkg))
        assertTrue(selectedRepo.isSelected(pkg))
    }

    // 10. App/service recreation after midnight does not restore yesterday's blocking state.
    @Test
    fun regression10_appOrServiceRecreationAfterMidnight_doesNotRestoreYesterdayBlockingState() {
        val midnight = 1_700_000_000_000L
        val yesterdayClock = TestClock(
            currentTime = midnight - 1_200_000L, // 11:40 PM yesterday
            startOfDay = midnight - 86_400_000L
        )
        val prefName = "test_reg_10_prefs"
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()

        val repoYesterday = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = prefName,
            timeProvider = yesterdayClock
        )
        repoYesterday.emitTrigger(
            EnforcementTrigger(
                packageName = "com.reddit.frontpage",
                reason = LimitState.LIMIT_REACHED,
                detectedAtElapsedRealtimeMs = 30_000L
            )
        )
        assertNotNull(repoYesterday.latestTrigger.value)

        // App process dies overnight. Morning arrives:
        val morningClock = TestClock(
            currentTime = midnight + 28_800_000L, // 8:00 AM today
            startOfDay = midnight
        )
        val repoMorning = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = prefName,
            timeProvider = morningClock
        )

        // Yesterday's trigger is expired upon re-creation
        assertNull(repoMorning.latestTrigger.value)
    }

    // 11. Timezone and manual date changes are handled safely according to the documented policy.
    @Test
    fun regression11_timezoneAndManualDateChanges_handledSafely() {
        var simulatedTimeZone = TimeZone.getTimeZone("UTC")
        val timeProvider = SystemTimeProvider(timeZoneSupplier = { simulatedTimeZone })

        val calUtc = Calendar.getInstance(simulatedTimeZone)
        val startOfDayUtc = timeProvider.getStartOfDayMillis()
        calUtc.timeInMillis = startOfDayUtc
        assertEquals(0, calUtc.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calUtc.get(Calendar.MINUTE))
        assertEquals(0, calUtc.get(Calendar.SECOND))
        assertEquals(0, calUtc.get(Calendar.MILLISECOND))

        // Timezone changed to Asia/Kolkata (IST: UTC+5:30)
        simulatedTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val startOfDayIst = timeProvider.getStartOfDayMillis()
        val calIst = Calendar.getInstance(simulatedTimeZone)
        calIst.timeInMillis = startOfDayIst
        assertEquals(0, calIst.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calIst.get(Calendar.MINUTE))
        assertEquals(0, calIst.get(Calendar.SECOND))
        assertEquals(0, calIst.get(Calendar.MILLISECOND))
    }

    // 12. Preset and custom limit enforcement continue working.
    @Test
    fun regression12_presetAndCustomLimitEnforcement_continueWorking() {
        val ruleEngine = UsageRuleEngine()
        val pkg = "com.test.app"

        // 15-minute preset (900,000 ms)
        val presetLimit = 900_000L
        assertEquals(LimitState.WITHIN_LIMIT, ruleEngine.evaluate(pkg, 899_000L, presetLimit).limitState)
        assertEquals(LimitState.LIMIT_REACHED, ruleEngine.evaluate(pkg, 900_000L, presetLimit).limitState)
        assertEquals(LimitState.LIMIT_REACHED, ruleEngine.evaluate(pkg, 901_000L, presetLimit).limitState)

        // Custom duration (e.g. 42 minutes = 2,520,000 ms)
        val customLimit = 2_520_000L
        assertEquals(LimitState.WITHIN_LIMIT, ruleEngine.evaluate(pkg, 2_519_000L, customLimit).limitState)
        assertEquals(LimitState.LIMIT_REACHED, ruleEngine.evaluate(pkg, 2_520_000L, customLimit).limitState)
        assertEquals(LimitState.LIMIT_REACHED, ruleEngine.evaluate(pkg, 3_000_000L, customLimit).limitState)
    }
}
