package com.scrollstop.ui.screens.usage

import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.model.DiscoveredApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refreshUsage when permission not granted sets PermissionRequired state`() {
        val usageRepo = FakeUsageStatsRepository(hasPermission = false)
        val discoveryRepo = FakeAppDiscoveryRepository()
        val selectedRepo = InMemorySelectedAppsRepository()
        selectedRepo.setSelectedPackages(setOf("com.instagram.android"))

        val viewModel = UsageViewModel(usageRepo, discoveryRepo, selectedRepo)

        assertTrue(viewModel.uiState.value is UsageUiState.PermissionRequired)
    }

    @Test
    fun `refreshUsage when no apps selected sets EmptySelection state`() {
        val usageRepo = FakeUsageStatsRepository(hasPermission = true)
        val discoveryRepo = FakeAppDiscoveryRepository()
        val selectedRepo = InMemorySelectedAppsRepository()

        val viewModel = UsageViewModel(usageRepo, discoveryRepo, selectedRepo)

        assertTrue(viewModel.uiState.value is UsageUiState.EmptySelection)
    }

    @Test
    fun `refreshUsage maps selected app with usage correctly`() {
        val usageRepo = FakeUsageStatsRepository(
            hasPermission = true,
            usageMap = mapOf(
                "com.instagram.android" to (23 * 60 + 41) * 1000L,
                "com.google.android.youtube" to (8 * 60 + 12) * 1000L,
                "com.unrelated.app" to 99999L
            )
        )
        val discoveryRepo = FakeAppDiscoveryRepository(
            discovered = listOf(
                DiscoveredApp("com.instagram.android", "Instagram"),
                DiscoveredApp("com.google.android.youtube", "YouTube")
            )
        )
        val selectedRepo = InMemorySelectedAppsRepository()
        selectedRepo.setSelectedPackages(setOf("com.instagram.android", "com.google.android.youtube"))

        val viewModel = UsageViewModel(usageRepo, discoveryRepo, selectedRepo)

        val state = viewModel.uiState.value
        assertTrue(state is UsageUiState.Success)

        val successState = state as UsageUiState.Success
        assertEquals(2, successState.usageList.size)

        // Sorted by label: Instagram, YouTube
        val instagram = successState.usageList[0]
        assertEquals("Instagram", instagram.appLabel)
        assertEquals("com.instagram.android", instagram.packageName)
        assertEquals((23 * 60 + 41) * 1000L, instagram.totalTimeInForegroundMs)
        assertEquals("23 min 41 sec", instagram.formattedUsage)

        val youtube = successState.usageList[1]
        assertEquals("YouTube", youtube.appLabel)
        assertEquals("com.google.android.youtube", youtube.packageName)
        assertEquals((8 * 60 + 12) * 1000L, youtube.totalTimeInForegroundMs)
        assertEquals("8 min 12 sec", youtube.formattedUsage)
    }

    @Test
    fun `refreshUsage maps selected app with zero usage and ignores unselected apps`() {
        val usageRepo = FakeUsageStatsRepository(
            hasPermission = true,
            usageMap = mapOf(
                "com.instagram.android" to 0L,
                "com.unrelated.app" to 500000L
            )
        )
        val discoveryRepo = FakeAppDiscoveryRepository(
            discovered = listOf(
                DiscoveredApp("com.instagram.android", "Instagram"),
                DiscoveredApp("com.unrelated.app", "Unrelated App")
            )
        )
        val selectedRepo = InMemorySelectedAppsRepository()
        selectedRepo.setSelectedPackages(setOf("com.instagram.android"))

        val viewModel = UsageViewModel(usageRepo, discoveryRepo, selectedRepo)

        val state = viewModel.uiState.value as UsageUiState.Success
        assertEquals(1, state.usageList.size)

        val item = state.usageList[0]
        assertEquals("Instagram", item.appLabel)
        assertEquals("0 min", item.formattedUsage)
        assertEquals(0L, item.totalTimeInForegroundMs)
    }

    @Test
    fun `refreshUsage handles missing package in UsageStats safely as zero usage`() {
        val usageRepo = FakeUsageStatsRepository(
            hasPermission = true,
            usageMap = emptyMap() // missing entry
        )
        val discoveryRepo = FakeAppDiscoveryRepository(
            discovered = listOf(DiscoveredApp("com.example.missing", "Missing App"))
        )
        val selectedRepo = InMemorySelectedAppsRepository()
        selectedRepo.setSelectedPackages(setOf("com.example.missing"))

        val viewModel = UsageViewModel(usageRepo, discoveryRepo, selectedRepo)

        val state = viewModel.uiState.value as UsageUiState.Success
        assertEquals(1, state.usageList.size)
        assertEquals("0 min", state.usageList[0].formattedUsage)
        assertEquals(0L, state.usageList[0].totalTimeInForegroundMs)
    }

    // --- Fake Test Helper Classes ---

    private class FakeUsageStatsRepository(
        private val hasPermission: Boolean,
        private val usageMap: Map<String, Long> = emptyMap()
    ) : UsageStatsRepository {
        override fun hasUsagePermission(): Boolean = hasPermission

        override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> {
            if (!hasPermission) return Result.failure(SecurityException("Permission denied"))
            val result = packageNames.associateWith { pkg -> usageMap[pkg] ?: 0L }
            return Result.success(result)
        }
    }

    private class FakeAppDiscoveryRepository(
        private val discovered: List<DiscoveredApp> = emptyList()
    ) : AppDiscoveryRepository {
        override suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>> {
            return Result.success(discovered)
        }
    }
}
