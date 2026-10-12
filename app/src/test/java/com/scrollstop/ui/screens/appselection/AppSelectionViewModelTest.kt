package com.scrollstop.ui.screens.appselection

import com.scrollstop.FakeCommitmentLockRepository
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.domain.model.DiscoveredApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class AppSelectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsDiscoveredAppsSuccessfully() = runTest {
        val fakeApps = listOf(
            DiscoveredApp("com.social.app", "Social App"),
            DiscoveredApp("com.video.app", "Video App")
        )
        val fakeRepo = FakeAppDiscoveryRepository(Result.success(fakeApps))
        val viewModel = AppSelectionViewModel(fakeRepo)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AppSelectionUiState.Success)
        val successState = state as AppSelectionUiState.Success
        assertEquals(2, successState.apps.size)
        assertEquals(0, successState.selectedCount)
    }

    @Test
    fun toggleAppSelection_updatesSelectedStateAndCount() = runTest {
        val fakeApps = listOf(
            DiscoveredApp("com.social.app", "Social App"),
            DiscoveredApp("com.video.app", "Video App")
        )
        val fakeRepo = FakeAppDiscoveryRepository(Result.success(fakeApps))
        val viewModel = AppSelectionViewModel(fakeRepo)

        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle selection for com.social.app
        viewModel.toggleAppSelection("com.social.app")

        var state = viewModel.uiState.value as AppSelectionUiState.Success
        assertEquals(1, state.selectedCount)
        assertTrue(state.apps.first { it.packageName == "com.social.app" }.isSelected)
        assertFalse(state.apps.first { it.packageName == "com.video.app" }.isSelected)

        // Toggle off selection for com.social.app
        viewModel.toggleAppSelection("com.social.app")

        state = viewModel.uiState.value as AppSelectionUiState.Success
        assertEquals(0, state.selectedCount)
        assertFalse(state.apps.first { it.packageName == "com.social.app" }.isSelected)
    }

    @Test
    fun loadApps_emitsErrorStateOnFailure() = runTest {
        val fakeRepo = FakeAppDiscoveryRepository(Result.failure(RuntimeException("Discovery failed")))
        val viewModel = AppSelectionViewModel(fakeRepo)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AppSelectionUiState.Error)
        assertEquals("Discovery failed", (state as AppSelectionUiState.Error).errorMessage)
    }

    @Test
    fun `when commitment locked - adding app is permitted immediately`() = runTest {
        val fakeApps = listOf(
            DiscoveredApp("com.social.app", "Social App"),
            DiscoveredApp("com.video.app", "Video App")
        )
        val selectedRepo = InMemorySelectedAppsRepository()
        val commitmentRepo = FakeCommitmentLockRepository()
        commitmentRepo.enableCommitmentLock(consentConfirmed = true)

        val viewModel = AppSelectionViewModel(
            repository = FakeAppDiscoveryRepository(Result.success(fakeApps)),
            selectedAppsRepository = selectedRepo,
            commitmentLockRepository = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Add app
        viewModel.toggleAppSelection("com.social.app")
        val state = viewModel.uiState.value as AppSelectionUiState.Success
        assertTrue(state.isCommitmentLocked)
        assertEquals(1, state.selectedCount)
        assertTrue(state.apps.first { it.packageName == "com.social.app" }.isSelected)
        assertNull(state.lockedNotice)
    }

    @Test
    fun `when commitment locked - removing app is blocked and staged for midnight`() = runTest {
        val fakeApps = listOf(
            DiscoveredApp("com.social.app", "Social App"),
            DiscoveredApp("com.video.app", "Video App")
        )
        val selectedRepo = InMemorySelectedAppsRepository()
        selectedRepo.setSelectedPackages(setOf("com.social.app"))

        val commitmentRepo = FakeCommitmentLockRepository()
        commitmentRepo.enableCommitmentLock(consentConfirmed = true)

        val viewModel = AppSelectionViewModel(
            repository = FakeAppDiscoveryRepository(Result.success(fakeApps)),
            selectedAppsRepository = selectedRepo,
            commitmentLockRepository = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Attempt to remove com.social.app while locked
        viewModel.toggleAppSelection("com.social.app")

        val state = viewModel.uiState.value as AppSelectionUiState.Success
        // App is still selected!
        assertTrue(selectedRepo.isSelected("com.social.app"))
        assertTrue(state.apps.first { it.packageName == "com.social.app" }.isSelected)
        // Staged for removal
        assertTrue(state.pendingRemovalPackages.contains("com.social.app"))
        assertNotNull(state.lockedNotice)

        // Toggling again cancels scheduled removal
        viewModel.toggleAppSelection("com.social.app")
        val stateAfterCancel = viewModel.uiState.value as AppSelectionUiState.Success
        assertFalse(stateAfterCancel.pendingRemovalPackages.contains("com.social.app"))
    }

    private class FakeAppDiscoveryRepository(
        private val result: Result<List<DiscoveredApp>>
    ) : AppDiscoveryRepository {
        override suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>> = result
    }
}
