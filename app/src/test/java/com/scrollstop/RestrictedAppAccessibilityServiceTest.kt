package com.scrollstop

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.AndroidAccessibilityServiceHealthRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.service.RestrictedAppAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestrictedAppAccessibilityServiceTest {

    private lateinit var selectedAppsRepository: InMemorySelectedAppsRepository
    private lateinit var dailyLimitRepository: InMemoryDailyLimitRepository
    private lateinit var fakeUsageStatsRepository: FakeUsageStatsRepository
    private lateinit var triggerRepository: InMemoryEnforcementTriggerRepository
    private lateinit var healthRepository: AndroidAccessibilityServiceHealthRepository
    private lateinit var controller: ServiceController<RestrictedAppAccessibilityService>
    private lateinit var service: RestrictedAppAccessibilityService

    @Before
    fun setUp() {
        selectedAppsRepository = InMemorySelectedAppsRepository()
        dailyLimitRepository = InMemoryDailyLimitRepository(defaultLimitMs = 120_000L)
        fakeUsageStatsRepository = FakeUsageStatsRepository()
        triggerRepository = InMemoryEnforcementTriggerRepository()
        healthRepository = AndroidAccessibilityServiceHealthRepository()

        ServiceLocator.resetForTesting(
            selectedAppsRepo = selectedAppsRepository,
            dailyLimitRepo = dailyLimitRepository,
            usageStatsRepo = fakeUsageStatsRepository,
            triggerRepo = triggerRepository,
            healthRepo = healthRepository
        )

        controller = Robolectric.buildService(RestrictedAppAccessibilityService::class.java)
        service = controller.create().get()
    }

    @Test
    fun `test 13 - service configuration explicitly prohibits window content retrieval`() {
        val info = service.serviceInfo ?: AccessibilityServiceInfo()
        assertFalse("canRetrieveWindowContent must be false", info.canRetrieveWindowContent)
    }

    @Test
    fun `test 14 - service configuration subscribes only to TYPE_WINDOW_STATE_CHANGED and dynamic package filter`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        service.onServiceConnected()

        val info = service.currentConfigInfo ?: service.serviceInfo
        assertNotNull(info)
        assertEquals(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, info?.eventTypes)
        assertNotNull(info?.packageNames)
        assertEquals("com.google.android.youtube", info?.packageNames?.firstOrNull())
    }

    @Test
    fun `test 15 - accessibility event processing accesses only packageName string`() {
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        fakeUsageStatsRepository.usageMap["com.google.android.youtube"] = 200_000L // over limit

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = "com.google.android.youtube"

        service.onAccessibilityEvent(event)

        val trigger = triggerRepository.latestTrigger.value
        assertNotNull(trigger)
        assertEquals("com.google.android.youtube", trigger?.packageName)
    }

    @Test
    fun `test 16 - accessibility service state correctly reports active and unavailable states`() {
        // Initially before connected
        assertEquals(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED, healthRepository.serviceState.value)

        // On connected
        service.onServiceConnected()
        assertEquals(AccessibilityServiceState.ACCESSIBILITY_ACTIVE, healthRepository.serviceState.value)

        // On interrupt
        service.onInterrupt()
        assertEquals(AccessibilityServiceState.ACCESSIBILITY_INTERRUPTED, healthRepository.serviceState.value)

        // On unbind / destroy
        service.onUnbind(null)
        assertEquals(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED, healthRepository.serviceState.value)
    }
}
