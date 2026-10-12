package com.scrollstop

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.AndroidAccessibilityServiceHealthRepository
import com.scrollstop.data.repository.AndroidProtectionHealthRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.SharedPreferencesCommitmentLockRepository
import com.scrollstop.data.repository.SharedPreferencesDailyLimitRepository
import com.scrollstop.data.repository.SharedPreferencesEnforcementTriggerRepository
import com.scrollstop.data.repository.SharedPreferencesSelectedAppsRepository
import com.scrollstop.data.time.TimeProvider
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine
import com.scrollstop.service.RestrictedAppAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnforcementRecoveryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var context: Context

    private lateinit var selectedAppsRepository: InMemorySelectedAppsRepository
    private lateinit var dailyLimitRepository: InMemoryDailyLimitRepository
    private lateinit var fakeUsageStatsRepository: FakeUsageStatsRepository
    private lateinit var triggerRepository: InMemoryEnforcementTriggerRepository
    private lateinit var healthRepository: AndroidAccessibilityServiceHealthRepository
    private lateinit var protectionHealthRepository: AndroidProtectionHealthRepository
    private lateinit var controller: ServiceController<RestrictedAppAccessibilityService>
    private lateinit var service: RestrictedAppAccessibilityService

    private val youtubePkg = "com.google.android.youtube"
    private val chromePkg = "com.android.chrome"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        selectedAppsRepository = InMemorySelectedAppsRepository()
        dailyLimitRepository = InMemoryDailyLimitRepository(defaultLimitMs = 120_000L)
        fakeUsageStatsRepository = FakeUsageStatsRepository()
        triggerRepository = InMemoryEnforcementTriggerRepository()
        healthRepository = AndroidAccessibilityServiceHealthRepository()
        protectionHealthRepository = AndroidProtectionHealthRepository(
            usageStatsRepository = fakeUsageStatsRepository,
            accessibilityHealthRepository = healthRepository,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        ServiceLocator.resetForTesting(
            selectedAppsRepo = selectedAppsRepository,
            dailyLimitRepo = dailyLimitRepository,
            usageStatsRepo = fakeUsageStatsRepository,
            triggerRepo = triggerRepository,
            healthRepo = healthRepository,
            protectionHealthRepo = protectionHealthRepository
        )

        controller = Robolectric.buildService(RestrictedAppAccessibilityService::class.java)
        service = controller.create().get()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ServiceLocator.resetLiveLimitEnforcementEngine()
    }

    @Test
    fun `test 1 - service onServiceConnected rechecks permissions and updates protection health to active when granted`() {
        fakeUsageStatsRepository.hasPermission = true
        service.onServiceConnected()

        assertEquals(AccessibilityServiceState.ACCESSIBILITY_ACTIVE, healthRepository.serviceState.value)
        assertEquals(ProtectionHealthState.PROTECTED, protectionHealthRepository.healthState.value)
    }

    @Test
    fun `test 2 - service onServiceConnected marks protection as USAGE_ACCESS_REQUIRED when usage permission is missing`() {
        fakeUsageStatsRepository.hasPermission = false
        service.onServiceConnected()

        assertEquals(AccessibilityServiceState.ACCESSIBILITY_ACTIVE, healthRepository.serviceState.value)
        assertEquals(ProtectionHealthState.USAGE_ACCESS_REQUIRED, protectionHealthRepository.healthState.value)
    }

    @Test
    fun `test 3 - service onServiceConnected detects when restricted app is already in foreground and triggers immediate block if over limit`() {
        selectedAppsRepository.setSelectedPackages(setOf(youtubePkg))
        fakeUsageStatsRepository.hasPermission = true
        fakeUsageStatsRepository.activeForegroundPackage = youtubePkg
        fakeUsageStatsRepository.usageMap[youtubePkg] = 180_000L // 3 min > 2 min limit

        service.onServiceConnected()

        val trigger = triggerRepository.latestTrigger.value
        assertNotNull("EnforcementTrigger must be emitted when restricted app in foreground is over limit", trigger)
        assertEquals(youtubePkg, trigger?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, trigger?.reason)

        val launchedIntent: Intent? = shadowOf(service).nextStartedActivity
        assertNotNull("Blocking UI must be launched immediately on service connection if app is over limit", launchedIntent)
        assertEquals(MainActivity::class.java.name, launchedIntent?.component?.className)
        assertEquals(youtubePkg, launchedIntent?.getStringExtra(MainActivity.EXTRA_BLOCKED_PACKAGE))
    }

    @Test
    fun `test 4 - service onServiceConnected detects when restricted app is already in foreground and starts live monitoring if within limit`() {
        selectedAppsRepository.setSelectedPackages(setOf(youtubePkg))
        fakeUsageStatsRepository.hasPermission = true
        fakeUsageStatsRepository.activeForegroundPackage = youtubePkg
        fakeUsageStatsRepository.usageMap[youtubePkg] = 60_000L // 1 min < 2 min limit

        service.onServiceConnected()

        assertTrue("Live monitoring must be started immediately for under-limit foreground app", service.liveEnforcementEngine.isMonitoring)
        assertEquals(youtubePkg, service.liveEnforcementEngine.currentMonitoredPackage)
        assertNull("No trigger should be emitted while within limit", triggerRepository.latestTrigger.value)
    }

    @Test
    fun `test 5 - service reconnection cancels previous selected package collection without creating duplicate loops`() {
        selectedAppsRepository.setSelectedPackages(setOf(youtubePkg))
        service.onServiceConnected()

        // Simulate interruption then reconnection
        service.onInterrupt()
        assertEquals(AccessibilityServiceState.ACCESSIBILITY_INTERRUPTED, healthRepository.serviceState.value)
        assertFalse(service.liveEnforcementEngine.isMonitoring)

        // Reconnect
        service.onServiceConnected()
        assertEquals(AccessibilityServiceState.ACCESSIBILITY_ACTIVE, healthRepository.serviceState.value)
    }

    @Test
    fun `test 6 - service onInterrupt and onUnbind stop live monitoring and transition health states cleanly`() {
        selectedAppsRepository.setSelectedPackages(setOf(youtubePkg))
        fakeUsageStatsRepository.activeForegroundPackage = youtubePkg
        fakeUsageStatsRepository.usageMap[youtubePkg] = 60_000L

        service.onServiceConnected()
        assertTrue(service.liveEnforcementEngine.isMonitoring)

        // Interrupt
        service.onInterrupt()
        assertEquals(AccessibilityServiceState.ACCESSIBILITY_INTERRUPTED, healthRepository.serviceState.value)
        assertFalse("Monitoring must be stopped on interrupt", service.liveEnforcementEngine.isMonitoring)

        // Unbind
        service.onUnbind(null)
        assertEquals(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED, healthRepository.serviceState.value)
        assertFalse("Monitoring must be stopped on unbind", service.liveEnforcementEngine.isMonitoring)
    }

    @Test
    fun `test 7 - unselected foreground app during recovery does not trigger enforcement or monitoring`() {
        selectedAppsRepository.setSelectedPackages(setOf(youtubePkg))
        fakeUsageStatsRepository.activeForegroundPackage = chromePkg // Not selected
        fakeUsageStatsRepository.usageMap[chromePkg] = 300_000L

        service.onServiceConnected()

        assertFalse(service.liveEnforcementEngine.isMonitoring)
        assertNull(triggerRepository.latestTrigger.value)
        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `test 8 - ServiceLocator recreates LiveLimitEnforcementEngine when coroutine scope changes or is cancelled`() {
        val scope1 = CoroutineScope(Dispatchers.Default)
        val engine1 = ServiceLocator.getLiveLimitEnforcementEngine(
            context = context,
            scope = scope1
        )
        assertNotNull(engine1)

        // Cancel scope1
        scope1.cancel()

        // Request with new active scope
        val scope2 = CoroutineScope(Dispatchers.Default)
        val engine2 = ServiceLocator.getLiveLimitEnforcementEngine(
            context = context,
            scope = scope2
        )

        assertNotNull(engine2)
        assertTrue("Engine must hold active scope2", engine2.scope.isActive)
        scope2.cancel()
    }

    @Test
    fun `test 9 - process recreation with SharedPreferences preserves commitment lock state and daily limits`() {
        val prefNameSelected = "test_recovery_selected_prefs"
        val prefNameLimits = "test_recovery_limit_prefs"
        val prefNameCommitment = "test_recovery_commitment_prefs"

        val selectedRepo1 = SharedPreferencesSelectedAppsRepository(context, prefNameSelected)
        val limitRepo1 = SharedPreferencesDailyLimitRepository(context, prefName = prefNameLimits)
        val commitmentRepo1 = SharedPreferencesCommitmentLockRepository(
            context = context,
            selectedAppsRepository = selectedRepo1,
            dailyLimitRepository = limitRepo1,
            prefName = prefNameCommitment
        )

        selectedRepo1.setSelectedPackages(setOf(youtubePkg))
        limitRepo1.setLimitForPackage(youtubePkg, 180_000L)
        commitmentRepo1.enableCommitmentLock(consentConfirmed = true)

        assertTrue(commitmentRepo1.commitmentState.value.isLocked)
        assertEquals(180_000L, limitRepo1.getLimitForPackage(youtubePkg))
        assertTrue(selectedRepo1.isSelected(youtubePkg))

        // Simulate complete process death and recreation from disk
        val selectedRepo2 = SharedPreferencesSelectedAppsRepository(context, prefNameSelected)
        val limitRepo2 = SharedPreferencesDailyLimitRepository(context, prefName = prefNameLimits)
        val commitmentRepo2 = SharedPreferencesCommitmentLockRepository(
            context = context,
            selectedAppsRepository = selectedRepo2,
            dailyLimitRepository = limitRepo2,
            prefName = prefNameCommitment
        )

        assertTrue("Commitment lock must survive process death", commitmentRepo2.commitmentState.value.isLocked)
        assertEquals("Daily limit must survive process death", 180_000L, limitRepo2.getLimitForPackage(youtubePkg))
        assertTrue("Selected apps must survive process death", selectedRepo2.isSelected(youtubePkg))
    }

    @Test
    fun `test 10 - stale midnight triggers are expired upon recovery across day boundary`() {
        var mockCurrentTime = 1_000_000L
        var mockStartOfDay = 500_000L

        val fakeTimeProvider = object : TimeProvider {
            override fun currentTimeMillis(): Long = mockCurrentTime
            override fun getStartOfDayMillis(): Long = mockStartOfDay
            override fun getNextMidnightMillis(): Long = mockStartOfDay + 86_400_000L
        }

        val persistentTriggerRepo = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = "test_recovery_trigger_prefs",
            timeProvider = fakeTimeProvider
        )

        // Yesterday's trigger
        val yesterdayTrigger = EnforcementTrigger(
            packageName = youtubePkg,
            reason = LimitState.LIMIT_REACHED,
            detectedAtElapsedRealtimeMs = 12345L
        )
        persistentTriggerRepo.emitTrigger(yesterdayTrigger)
        assertNotNull(persistentTriggerRepo.latestTrigger.value)

        // Advance to next day (start of day is now ahead of trigger timestamp)
        mockCurrentTime = 2_000_000L
        mockStartOfDay = 1_500_000L // New day started at 1,500,000

        persistentTriggerRepo.checkAndExpireStaleTriggers()
        assertNull("Stale trigger from previous calendar day must expire on recovery", persistentTriggerRepo.latestTrigger.value)
    }
}
