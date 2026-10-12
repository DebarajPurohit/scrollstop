package com.scrollstop.ui.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.FakeCommitmentLockRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.ProtectionHealthRepository
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.domain.model.ProtectionHealthState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelTest {

    private lateinit var context: Context
    private lateinit var fakeHealthRepo: FakeProtectionHealthRepository
    private lateinit var selectedAppsRepo: InMemorySelectedAppsRepository
    private lateinit var fakeCommitmentRepo: FakeCommitmentLockRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeHealthRepo = FakeProtectionHealthRepository()
        selectedAppsRepo = InMemorySelectedAppsRepository()
        fakeCommitmentRepo = FakeCommitmentLockRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_reflectsHealthAndSelectedAppCount() = runTest(testDispatcher) {
        fakeHealthRepo.setHealth(ProtectionHealthState.PROTECTED)
        selectedAppsRepo.setSelectedPackages(setOf("com.amazon.mShop.android.shopping", "com.google.android.youtube"))

        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProtectionHealthState.PROTECTED, state.healthState)
        assertEquals(2, state.selectedAppCount)
        assertFalse(state.commitmentState.isLocked)
    }

    @Test
    fun uiState_updatesWhenHealthChanges() = runTest(testDispatcher) {
        fakeHealthRepo.setHealth(ProtectionHealthState.PROTECTED)
        selectedAppsRepo.setSelectedPackages(setOf("com.instagram.android"))

        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        testScheduler.advanceUntilIdle()
        assertEquals(ProtectionHealthState.PROTECTED, viewModel.uiState.value.healthState)

        // Health drops to USAGE_ACCESS_REQUIRED
        fakeHealthRepo.setHealth(ProtectionHealthState.USAGE_ACCESS_REQUIRED)
        testScheduler.advanceUntilIdle()

        assertEquals(ProtectionHealthState.USAGE_ACCESS_REQUIRED, viewModel.uiState.value.healthState)
        assertEquals(1, viewModel.uiState.value.selectedAppCount)
    }

    @Test
    fun enableCommitmentLock_requiresConsentAndUpdatesUiState() = runTest(testDispatcher) {
        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        testScheduler.advanceUntilIdle()

        // Without consent
        val resultNoConsent = viewModel.enableCommitmentLock(consentConfirmed = false)
        assertFalse(resultNoConsent)
        assertFalse(viewModel.uiState.value.commitmentState.isLocked)

        // With affirmative consent
        val resultWithConsent = viewModel.enableCommitmentLock(consentConfirmed = true)
        assertTrue(resultWithConsent)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.commitmentState.isLocked)
    }

    @Test
    fun requestAndCancelUnlock_updatesUiState() = runTest(testDispatcher) {
        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        viewModel.enableCommitmentLock(consentConfirmed = true)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.requestUnlock())
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.commitmentState.pendingUnlock)

        assertTrue(viewModel.cancelUnlockRequest())
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.commitmentState.pendingUnlock)
    }

    @Test
    fun debugClearCommitmentState_resetsCommitmentImmediately() = runTest(testDispatcher) {
        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)
        viewModel.enableCommitmentLock(consentConfirmed = true)
        viewModel.requestUnlock()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.commitmentState.isLocked)

        viewModel.debugClearCommitmentState()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.commitmentState.isLocked)
        assertFalse(viewModel.uiState.value.commitmentState.pendingUnlock)
    }

    @Test
    fun openSettingsActions_executeSafely() {
        val viewModel = MainViewModel(fakeHealthRepo, selectedAppsRepo, fakeCommitmentRepo)

        // Ensure opening system settings intents does not throw exceptions
        viewModel.openUsageAccessSettings(context)
        viewModel.openAccessibilitySettings(context)
        assertNotNull(viewModel)
    }

    private class FakeProtectionHealthRepository : ProtectionHealthRepository {
        private val _healthState = MutableStateFlow(ProtectionHealthState.DEGRADED)
        override val healthState: StateFlow<ProtectionHealthState> = _healthState.asStateFlow()

        fun setHealth(state: ProtectionHealthState) {
            _healthState.value = state
        }

        override fun refreshHealth(context: Context) {
            // No-op for fake
        }

        override fun calculateCurrentHealth(
            hasUsagePermission: Boolean,
            accessibilityState: AccessibilityServiceState
        ): ProtectionHealthState {
            return _healthState.value
        }
    }
}
