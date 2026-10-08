package com.scrollstop

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.SharedPreferencesEnforcementTriggerRepository
import com.scrollstop.domain.detection.RestrictedAppDetectionEngine
import com.scrollstop.domain.model.DiscoveredApp
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine
import com.scrollstop.service.RestrictedAppAccessibilityService
import com.scrollstop.ui.screens.blocking.BlockingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class FakeAppDiscoveryRepository : AppDiscoveryRepository {
    var apps = listOf(
        DiscoveredApp(packageName = "com.google.android.youtube", appLabel = "YouTube"),
        DiscoveredApp(packageName = "com.android.chrome", appLabel = "Google Chrome")
    )
    override suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>> {
        return Result.success(apps)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class POC05BlockingEnforcementLoopTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var selectedAppsRepository: InMemorySelectedAppsRepository
    private lateinit var dailyLimitRepository: InMemoryDailyLimitRepository
    private lateinit var fakeUsageStatsRepository: FakeUsageStatsRepository
    private lateinit var triggerRepository: InMemoryEnforcementTriggerRepository
    private lateinit var usageRuleEngine: UsageRuleEngine
    private lateinit var appDiscoveryRepository: FakeAppDiscoveryRepository
    private lateinit var service: RestrictedAppAccessibilityService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        selectedAppsRepository = InMemorySelectedAppsRepository()
        dailyLimitRepository = InMemoryDailyLimitRepository(defaultLimitMs = 120_000L) // 2 minutes (120s)
        fakeUsageStatsRepository = FakeUsageStatsRepository()
        triggerRepository = InMemoryEnforcementTriggerRepository()
        usageRuleEngine = UsageRuleEngine()
        appDiscoveryRepository = FakeAppDiscoveryRepository()

        ServiceLocator.resetForTesting(
            selectedAppsRepo = selectedAppsRepository,
            dailyLimitRepo = dailyLimitRepository,
            usageStatsRepo = fakeUsageStatsRepository,
            triggerRepo = triggerRepository,
            ruleEngine = usageRuleEngine
        )

        val controller = Robolectric.buildService(RestrictedAppAccessibilityService::class.java)
        service = controller.create().get()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test 1 - LIMIT_REACHED generates enforcement trigger and launches blocking UI`() {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 180_000L // 3 minutes > 2 minute limit

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = pkg
        service.onAccessibilityEvent(event)

        val trigger = triggerRepository.latestTrigger.value
        assertNotNull("LIMIT_REACHED must produce an EnforcementTrigger", trigger)
        assertEquals(pkg, trigger?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, trigger?.reason)

        val nextStartedIntent: Intent? = shadowOf(service).nextStartedActivity
        assertNotNull("Service must launch blocking UI activity intent when limit is reached", nextStartedIntent)
        assertEquals(MainActivity::class.java.name, nextStartedIntent?.component?.className)
        assertEquals(pkg, nextStartedIntent?.getStringExtra(MainActivity.EXTRA_BLOCKED_PACKAGE))
    }

    @Test
    fun `test 2 - WITHIN_LIMIT does not generate trigger or launch blocking UI`() {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 60_000L // 1 minute < 2 minute limit

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = pkg
        service.onAccessibilityEvent(event)

        val trigger = triggerRepository.latestTrigger.value
        assertNull("WITHIN_LIMIT must NOT produce an EnforcementTrigger", trigger)
        assertNull("WITHIN_LIMIT must NOT launch blocking UI", shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `test 3 - NOT_STARTED does not generate trigger or launch blocking UI`() {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 0L // 0 usage

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = pkg
        service.onAccessibilityEvent(event)

        val trigger = triggerRepository.latestTrigger.value
        assertNull("NOT_STARTED must NOT produce an EnforcementTrigger", trigger)
        assertNull("NOT_STARTED must NOT launch blocking UI", shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `test 4 - Unselected app does not generate trigger or launch blocking UI`() {
        val pkg = "com.android.chrome" // Not selected
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap[pkg] = 300_000L // 5 minutes

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = pkg
        service.onAccessibilityEvent(event)

        val trigger = triggerRepository.latestTrigger.value
        assertNull("Unselected app must NOT produce an EnforcementTrigger", trigger)
        assertNull("Unselected app must NOT launch blocking UI", shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `test 5 - Repeated LIMIT_REACHED event blocks repeatedly on each launch`() {
        val pkg = "com.google.android.youtube"
        selectedAppsRepository.setSelectedPackages(setOf(pkg))
        fakeUsageStatsRepository.usageMap[pkg] = 200_000L

        val event1 = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event1.packageName = pkg
        service.onAccessibilityEvent(event1)

        val intent1 = shadowOf(service).nextStartedActivity
        assertNotNull("First launch must block", intent1)

        // Clear trigger & launch intent, simulate opening YouTube a second time
        triggerRepository.clearLatestTrigger()
        val event2 = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event2.packageName = pkg
        service.onAccessibilityEvent(event2)

        val intent2 = shadowOf(service).nextStartedActivity
        assertNotNull("Repeated launch must block again", intent2)
        assertEquals(pkg, intent2?.getStringExtra(MainActivity.EXTRA_BLOCKED_PACKAGE))
    }

    @Test
    fun `test 6 - Multiple selected packages block correctly with independent package details`() {
        val pkg1 = "com.google.android.youtube"
        val pkg2 = "com.android.chrome"
        selectedAppsRepository.setSelectedPackages(setOf(pkg1, pkg2))

        fakeUsageStatsRepository.usageMap[pkg1] = 150_000L // Over limit (2 min)
        fakeUsageStatsRepository.usageMap[pkg2] = 300_000L // Over limit (2 min)

        // Open YouTube
        val event1 = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event1.packageName = pkg1
        service.onAccessibilityEvent(event1)
        val intent1 = shadowOf(service).nextStartedActivity
        assertEquals(pkg1, intent1?.getStringExtra(MainActivity.EXTRA_BLOCKED_PACKAGE))

        // Open Chrome
        val event2 = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event2.packageName = pkg2
        service.onAccessibilityEvent(event2)
        val intent2 = shadowOf(service).nextStartedActivity
        assertEquals(pkg2, intent2?.getStringExtra(MainActivity.EXTRA_BLOCKED_PACKAGE))
    }

    @Test
    fun `test 7 - BlockingViewModel displays correct app label, usage, and limit from trusted sources`() {
        val pkg = "com.google.android.youtube"
        fakeUsageStatsRepository.usageMap[pkg] = 150_000L // 2 min 30 sec
        dailyLimitRepository.setLimitForPackage(pkg, 120_000L) // 2 min

        val viewModel = BlockingViewModel(
            packageName = pkg,
            usageStatsRepository = fakeUsageStatsRepository,
            dailyLimitRepository = dailyLimitRepository,
            appDiscoveryRepository = appDiscoveryRepository,
            usageRuleEngine = usageRuleEngine
        )

        val state = viewModel.uiState.value
        assertEquals(pkg, state.packageName)
        assertEquals("YouTube", state.appName)
        assertEquals(150_000L, state.usedDurationMs)
        assertEquals(120_000L, state.limitDurationMs)
        assertEquals("2 min 30 sec", state.formattedUsage)
        assertEquals("2 min", state.formattedLimit)
        assertEquals(LimitState.LIMIT_REACHED, state.limitState)
    }

    @Test
    fun `test 8 - SharedPreferencesEnforcementTriggerRepository persists trigger state across lifecycle`() {
        val repo = SharedPreferencesEnforcementTriggerRepository(context, "test_trigger_prefs")
        repo.clearLatestTrigger()

        val trigger = com.scrollstop.domain.model.EnforcementTrigger(
            packageName = "com.google.android.youtube",
            reason = LimitState.LIMIT_REACHED,
            detectedAtElapsedRealtimeMs = 123456L
        )

        repo.emitTrigger(trigger)
        assertEquals("com.google.android.youtube", repo.latestTrigger.value?.packageName)

        // Re-instantiate repository (simulating app restart)
        val newRepo = SharedPreferencesEnforcementTriggerRepository(context, "test_trigger_prefs")
        assertEquals("com.google.android.youtube", newRepo.latestTrigger.value?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, newRepo.latestTrigger.value?.reason)
    }

    @Test
    fun `test 9 - DetectionEngine preserves single source of usage truth without duplicate timers`() {
        val detectionEngine = RestrictedAppDetectionEngine(
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            usageStatsRepository = fakeUsageStatsRepository,
            usageRuleEngine = usageRuleEngine,
            selfPackageName = "com.scrollstop"
        )

        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 130_000L // over 120,000 limit

        val trigger = detectionEngine.processEvent(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )

        assertNotNull(trigger)
        assertEquals("com.google.android.youtube", trigger?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, trigger?.reason)
    }
}
