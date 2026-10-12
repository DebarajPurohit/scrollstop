package com.scrollstop.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.domain.model.ProtectionHealthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProtectionHealthRepositoryTest {

    private lateinit var context: Context
    private lateinit var fakeUsageRepo: FakeUsageStatsRepository
    private lateinit var fakeAccessibilityRepo: FakeAccessibilityServiceHealthRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeUsageRepo = FakeUsageStatsRepository()
        fakeAccessibilityRepo = FakeAccessibilityServiceHealthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun calculateCurrentHealth_bothActive_returnsProtected() {
        val repo = AndroidProtectionHealthRepository(fakeUsageRepo, fakeAccessibilityRepo, testScope)
        val state = repo.calculateCurrentHealth(
            hasUsagePermission = true,
            accessibilityState = AccessibilityServiceState.ACCESSIBILITY_ACTIVE
        )
        assertEquals(ProtectionHealthState.PROTECTED, state)
    }

    @Test
    fun calculateCurrentHealth_usageRevoked_accessibilityActive_returnsUsageAccessRequired() {
        val repo = AndroidProtectionHealthRepository(fakeUsageRepo, fakeAccessibilityRepo, testScope)
        val state = repo.calculateCurrentHealth(
            hasUsagePermission = false,
            accessibilityState = AccessibilityServiceState.ACCESSIBILITY_ACTIVE
        )
        assertEquals(ProtectionHealthState.USAGE_ACCESS_REQUIRED, state)
    }

    @Test
    fun calculateCurrentHealth_usageGranted_accessibilityDisabled_returnsAccessibilityRequired() {
        val repo = AndroidProtectionHealthRepository(fakeUsageRepo, fakeAccessibilityRepo, testScope)
        val state = repo.calculateCurrentHealth(
            hasUsagePermission = true,
            accessibilityState = AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED
        )
        assertEquals(ProtectionHealthState.ACCESSIBILITY_REQUIRED, state)
    }

    @Test
    fun calculateCurrentHealth_usageGranted_accessibilityInterrupted_returnsAccessibilityInterrupted() {
        val repo = AndroidProtectionHealthRepository(fakeUsageRepo, fakeAccessibilityRepo, testScope)
        val state = repo.calculateCurrentHealth(
            hasUsagePermission = true,
            accessibilityState = AccessibilityServiceState.ACCESSIBILITY_INTERRUPTED
        )
        assertEquals(ProtectionHealthState.ACCESSIBILITY_INTERRUPTED, state)
    }

    @Test
    fun calculateCurrentHealth_bothDisabled_returnsDegraded() {
        val repo = AndroidProtectionHealthRepository(fakeUsageRepo, fakeAccessibilityRepo, testScope)
        val state = repo.calculateCurrentHealth(
            hasUsagePermission = false,
            accessibilityState = AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED
        )
        assertEquals(ProtectionHealthState.DEGRADED, state)
    }

    @Test
    fun healthState_reactsToAccessibilityStateChanges() = runTest(testDispatcher) {
        fakeUsageRepo.hasPermission = true
        fakeAccessibilityRepo.updateState(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED)

        val repo = AndroidProtectionHealthRepository(fakeUsageRepo, fakeAccessibilityRepo, CoroutineScope(Dispatchers.Unconfined))
        assertEquals(ProtectionHealthState.ACCESSIBILITY_REQUIRED, repo.healthState.value)

        // Simulate user enabling accessibility service
        fakeAccessibilityRepo.updateState(AccessibilityServiceState.ACCESSIBILITY_ACTIVE)
        testScheduler.advanceUntilIdle()
        assertEquals(ProtectionHealthState.PROTECTED, repo.healthState.value)

        // Simulate OEM interrupting service
        fakeAccessibilityRepo.updateState(AccessibilityServiceState.ACCESSIBILITY_INTERRUPTED)
        testScheduler.advanceUntilIdle()
        assertEquals(ProtectionHealthState.ACCESSIBILITY_INTERRUPTED, repo.healthState.value)
    }

    @Test
    fun refreshHealth_updatesStateFromCurrentEnvironment() {
        fakeUsageRepo.hasPermission = false
        fakeAccessibilityRepo.updateState(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED)

        val repo = AndroidProtectionHealthRepository(fakeUsageRepo, fakeAccessibilityRepo, testScope)
        assertEquals(ProtectionHealthState.DEGRADED, repo.healthState.value)

        // Permissions restored
        fakeUsageRepo.hasPermission = true
        fakeAccessibilityRepo.updateState(AccessibilityServiceState.ACCESSIBILITY_ACTIVE)

        repo.refreshHealth(context)
        assertEquals(ProtectionHealthState.PROTECTED, repo.healthState.value)
    }

    private class FakeUsageStatsRepository : UsageStatsRepository {
        var hasPermission: Boolean = false

        override fun hasUsagePermission(): Boolean = hasPermission

        override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> {
            return if (hasPermission) {
                Result.success(packageNames.associateWith { 0L })
            } else {
                Result.failure(SecurityException("Permission denied"))
            }
        }
    }

    private class FakeAccessibilityServiceHealthRepository : AccessibilityServiceHealthRepository {
        private val _serviceState = MutableStateFlow(AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED)
        override val serviceState: StateFlow<AccessibilityServiceState> = _serviceState.asStateFlow()

        override fun isServiceEnabledInSettings(context: Context, serviceClass: Class<*>): Boolean {
            return _serviceState.value == AccessibilityServiceState.ACCESSIBILITY_ACTIVE
        }

        override fun updateState(newState: AccessibilityServiceState) {
            _serviceState.value = newState
        }

        override fun refreshState(context: Context, serviceClass: Class<*>) {
            // No-op for fake
        }
    }
}
