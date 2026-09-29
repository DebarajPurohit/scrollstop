package com.scrollstop

import android.view.accessibility.AccessibilityEvent
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.detection.RestrictedAppDetectionEngine
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.domain.rules.UsageRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FakeUsageStatsRepository : UsageStatsRepository {
    var hasPermission: Boolean = true
    val usageMap = mutableMapOf<String, Long>()

    override fun hasUsagePermission(): Boolean = hasPermission

    override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> {
        if (!hasPermission) {
            return Result.failure(SecurityException("No permission"))
        }
        val result = packageNames.associateWith { usageMap[it] ?: 0L }
        return Result.success(result)
    }
}

class RestrictedAppDetectionEngineTest {

    private lateinit var selectedAppsRepository: InMemorySelectedAppsRepository
    private lateinit var dailyLimitRepository: InMemoryDailyLimitRepository
    private lateinit var fakeUsageStatsRepository: FakeUsageStatsRepository
    private lateinit var usageRuleEngine: UsageRuleEngine
    private lateinit var detectionEngine: RestrictedAppDetectionEngine

    @Before
    fun setUp() {
        selectedAppsRepository = InMemorySelectedAppsRepository()
        dailyLimitRepository = InMemoryDailyLimitRepository(defaultLimitMs = 120_000L) // 2 minutes
        fakeUsageStatsRepository = FakeUsageStatsRepository()
        usageRuleEngine = UsageRuleEngine()

        detectionEngine = RestrictedAppDetectionEngine(
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            usageStatsRepository = fakeUsageStatsRepository,
            usageRuleEngine = usageRuleEngine,
            selfPackageName = "com.scrollstop"
        )
    }

    @Test
    fun `test 1 - null package produces no enforcement trigger`() {
        val trigger = detectionEngine.processEvent(
            packageName = null,
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        assertNull(trigger)
    }

    @Test
    fun `test 2 - wrong event type produces no enforcement trigger`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 150_000L // over limit

        val trigger = detectionEngine.processEvent(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_VIEW_CLICKED
        )
        assertNull(trigger)
    }

    @Test
    fun `test 3 - empty or blank package produces no enforcement trigger`() {
        val triggerEmpty = detectionEngine.processEvent(
            packageName = "",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        val triggerBlank = detectionEngine.processEvent(
            packageName = "   ",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        assertNull(triggerEmpty)
        assertNull(triggerBlank)
    }

    @Test
    fun `test 4 - self package com scrollstop is ignored`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.scrollstop"))
        fakeUsageStatsRepository.usageMap["com.scrollstop"] = 300_000L // over limit

        val trigger = detectionEngine.processEvent(
            packageName = "com.scrollstop",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        assertNull(trigger)
    }

    @Test
    fun `test 5 - unselected package produces no enforcement trigger`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.instagram.android"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 300_000L // YouTube is over limit but NOT selected

        val trigger = detectionEngine.processEvent(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        assertNull(trigger)
    }

    @Test
    fun `test 6 - selected package with NOT_STARTED state produces no enforcement trigger`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 0L // 0 ms usage

        val trigger = detectionEngine.processEvent(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        assertNull(trigger)
    }

    @Test
    fun `test 7 - selected package with WITHIN_LIMIT state produces no enforcement trigger`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 60_000L // 1 minute (limit is 2 min)

        val trigger = detectionEngine.processEvent(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        assertNull(trigger)
    }

    @Test
    fun `test 8 - selected package exactly at limit produces enforcement trigger`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 120_000L // Exactly 2 min

        val trigger = detectionEngine.processEvent(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            elapsedRealtimeMs = 12345L
        )

        assertNotNull(trigger)
        assertEquals("com.google.android.youtube", trigger?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, trigger?.reason)
        assertEquals(12345L, trigger?.detectedAtElapsedRealtimeMs)
    }

    @Test
    fun `test 9 - selected package with usage over limit produces enforcement trigger`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 9_129_000L // 2 hr 32 min (physical test value)

        val trigger = detectionEngine.processEvent(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )

        assertNotNull(trigger)
        assertEquals("com.google.android.youtube", trigger?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, trigger?.reason)
    }

    @Test
    fun `test 10 - repeated identical events evaluate deterministically without duplicate runaway loops`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 150_000L

        val trigger1 = detectionEngine.processEvent("com.google.android.youtube", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, 100L)
        val trigger2 = detectionEngine.processEvent("com.google.android.youtube", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, 200L)

        assertNotNull(trigger1)
        assertNotNull(trigger2)
        assertEquals("com.google.android.youtube", trigger1?.packageName)
        assertEquals("com.google.android.youtube", trigger2?.packageName)
    }

    @Test
    fun `test 11 - multiple selected apps are evaluated independently`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube", "com.instagram.android"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 150_000L // over limit
        fakeUsageStatsRepository.usageMap["com.instagram.android"] = 30_000L // under limit

        val triggerYouTube = detectionEngine.processEvent("com.google.android.youtube", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        val triggerInstagram = detectionEngine.processEvent("com.instagram.android", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        assertNotNull(triggerYouTube)
        assertEquals("com.google.android.youtube", triggerYouTube?.packageName)

        assertNull(triggerInstagram)
    }

    @Test
    fun `test 12 - changing selected-app set is respected dynamically`() {
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 150_000L

        // Initially not selected
        assertNull(detectionEngine.processEvent("com.google.android.youtube", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))

        // Select YouTube
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        val triggerAfterSelect = detectionEngine.processEvent("com.google.android.youtube", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        assertNotNull(triggerAfterSelect)

        // Deselect YouTube
        selectedAppsRepository.setSelectedPackages(emptySet())
        assertNull(detectionEngine.processEvent("com.google.android.youtube", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
    }
}
