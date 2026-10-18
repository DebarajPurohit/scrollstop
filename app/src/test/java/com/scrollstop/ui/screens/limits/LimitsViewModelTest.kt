package com.scrollstop.ui.screens.limits

import com.scrollstop.FakeCommitmentLockRepository
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.DailyLimitRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.data.repository.SelectedAppsRepository
import com.scrollstop.data.repository.UsageStatsRepository
import com.scrollstop.domain.model.DiscoveredApp
import com.scrollstop.domain.rules.LimitState
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
class LimitsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleApps = listOf(
        DiscoveredApp(packageName = "com.instagram.android", appLabel = "Instagram"),
        DiscoveredApp(packageName = "com.google.android.youtube", appLabel = "YouTube"),
        DiscoveredApp(packageName = "com.zhiliaoapp.musically", appLabel = "TikTok"),
        DiscoveredApp(packageName = "com.twitter.android", appLabel = "X")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init with empty discovery emits Empty state`() = runTest {
        val discoveryRepo = FakeAppDiscoveryRepository(Result.success(emptyList()))
        val viewModel = LimitsViewModel(appDiscoveryRepository = discoveryRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected LimitsUiState.Empty but got $state", state is LimitsUiState.Empty)
    }

    @Test
    fun `init when discovery fails emits Error state`() = runTest {
        val discoveryRepo = FakeAppDiscoveryRepository(Result.failure(RuntimeException("PackageManager unavailable")))
        val viewModel = LimitsViewModel(appDiscoveryRepository = discoveryRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected LimitsUiState.Error but got $state", state is LimitsUiState.Error)
        assertEquals("PackageManager unavailable", (state as LimitsUiState.Error).errorMessage)
    }

    @Test
    fun `init when permission denied emits PermissionRequired state`() = runTest {
        val discoveryRepo = FakeAppDiscoveryRepository(Result.success(sampleApps))
        val usageRepo = FakeUsageStatsRepository(hasPermission = false)
        val viewModel = LimitsViewModel(
            appDiscoveryRepository = discoveryRepo,
            usageStatsRepository = usageRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected LimitsUiState.PermissionRequired but got $state", state is LimitsUiState.PermissionRequired)
    }

    @Test
    fun `init loads discovered apps with usage and limits successfully`() = runTest {
        val discoveryRepo = FakeAppDiscoveryRepository(Result.success(sampleApps))
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf("com.instagram.android", "com.google.android.youtube"))
        }
        val dailyLimitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage("com.instagram.android", 30 * 60 * 1000L) // 30m
            setLimitForPackage("com.google.android.youtube", 60 * 60 * 1000L) // 1h
        }
        val usageRepo = FakeUsageStatsRepository(
            hasPermission = true,
            usageMap = mapOf(
                "com.instagram.android" to 15 * 60 * 1000L, // 15m
                "com.google.android.youtube" to 75 * 60 * 1000L // 1h 15m (exceeded)
            )
        )

        val viewModel = LimitsViewModel(
            appDiscoveryRepository = discoveryRepo,
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = dailyLimitRepo,
            usageStatsRepository = usageRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LimitsUiState.Success)
        val success = state as LimitsUiState.Success
        assertEquals(4, success.apps.size)
        assertEquals(2, success.selectedCount)
        assertEquals(4, success.totalDiscoveredCount)

        val instagram = success.apps.first { it.packageName == "com.instagram.android" }
        assertTrue(instagram.isSelected)
        assertEquals("15 min", instagram.formattedUsage)
        assertEquals("30 min", instagram.formattedLimit)
        assertEquals(LimitState.WITHIN_LIMIT, instagram.limitState)

        val youtube = success.apps.first { it.packageName == "com.google.android.youtube" }
        assertTrue(youtube.isSelected)
        assertEquals("1 hr 15 min", youtube.formattedUsage)
        assertEquals("1 hr", youtube.formattedLimit)
        assertEquals(LimitState.LIMIT_REACHED, youtube.limitState)

        val tiktok = success.apps.first { it.packageName == "com.zhiliaoapp.musically" }
        assertFalse(tiktok.isSelected)
    }

    @Test
    fun `search by app name substring filters correctly`() = runTest {
        val viewModel = createDefaultViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChange("Insta")

        val state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(1, state.apps.size)
        assertEquals("Instagram", state.apps.first().appLabel)
    }

    @Test
    fun `search by package name substring filters correctly`() = runTest {
        val viewModel = createDefaultViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // TikTok package name is "com.zhiliaoapp.musically"
        viewModel.onSearchQueryChange("zhiliaoapp")

        val state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(1, state.apps.size)
        assertEquals("TikTok", state.apps.first().appLabel)
    }

    @Test
    fun `search is case-insensitive for both display name and package name`() = runTest {
        val viewModel = createDefaultViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChange("YOUTUBE")
        var state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(1, state.apps.size)
        assertEquals("YouTube", state.apps.first().appLabel)

        viewModel.onSearchQueryChange("google.android")
        state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(1, state.apps.size)
        assertEquals("YouTube", state.apps.first().appLabel)
    }

    @Test
    fun `search with leading and trailing whitespace trims correctly`() = runTest {
        val viewModel = createDefaultViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChange("   TikTok   ")
        val state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(1, state.apps.size)
        assertEquals("TikTok", state.apps.first().appLabel)
    }

    @Test
    fun `filter CONTROLLED shows only monitored applications`() = runTest {
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf("com.instagram.android", "com.twitter.android"))
        }
        val viewModel = createDefaultViewModel(selectedRepo = selectedRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelect(LimitsFilter.CONTROLLED)

        val state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(2, state.apps.size)
        assertTrue(state.apps.all { it.isSelected })
        assertTrue(state.apps.any { it.packageName == "com.instagram.android" })
        assertTrue(state.apps.any { it.packageName == "com.twitter.android" })
    }

    @Test
    fun `filter ALL shows all discovered applications`() = runTest {
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf("com.instagram.android"))
        }
        val viewModel = createDefaultViewModel(selectedRepo = selectedRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelect(LimitsFilter.CONTROLLED)
        var state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(1, state.apps.size)

        viewModel.onFilterSelect(LimitsFilter.ALL)
        state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(4, state.apps.size)
    }

    @Test
    fun `filter SOCIAL_VIDEO returns empty when category metadata is unsupported`() = runTest {
        val viewModel = createDefaultViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelect(LimitsFilter.SOCIAL_VIDEO)

        val state = viewModel.uiState.value as LimitsUiState.Success
        assertTrue(state.apps.isEmpty())
        assertTrue(state.isFilteredEmpty)
    }

    @Test
    fun `filter SOCIAL_VIDEO filters to social and video categorized applications`() = runTest {
        val apps = listOf(
            DiscoveredApp("com.instagram.android", "Instagram", category = android.content.pm.ApplicationInfo.CATEGORY_SOCIAL),
            DiscoveredApp("com.google.android.youtube", "YouTube", category = android.content.pm.ApplicationInfo.CATEGORY_VIDEO),
            DiscoveredApp("com.calculator.app", "Calculator", category = android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED),
            DiscoveredApp("com.game.app", "Game", category = android.content.pm.ApplicationInfo.CATEGORY_GAME)
        )
        val viewModel = createDefaultViewModel(apps = apps)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelect(LimitsFilter.SOCIAL_VIDEO)

        val state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(2, state.apps.size)
        assertTrue(state.apps.any { it.packageName == "com.instagram.android" })
        assertTrue(state.apps.any { it.packageName == "com.google.android.youtube" })
        assertFalse(state.apps.any { it.packageName == "com.calculator.app" })
        assertFalse(state.apps.any { it.packageName == "com.game.app" })
    }

    @Test
    fun `combined search and filter work together`() = runTest {
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf("com.instagram.android", "com.google.android.youtube"))
        }
        val viewModel = createDefaultViewModel(selectedRepo = selectedRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelect(LimitsFilter.CONTROLLED)
        viewModel.onSearchQueryChange("tube")

        val state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(1, state.apps.size)
        assertEquals("YouTube", state.apps.first().appLabel)
    }

    @Test
    fun `selection toggle when unlocked updates selection immediately`() = runTest {
        val selectedRepo = InMemorySelectedAppsRepository()
        val viewModel = createDefaultViewModel(selectedRepo = selectedRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle on
        viewModel.toggleAppSelection("com.instagram.android")
        var state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(1, state.selectedCount)
        assertTrue(selectedRepo.isSelected("com.instagram.android"))
        assertTrue(state.apps.first { it.packageName == "com.instagram.android" }.isSelected)

        // Toggle off
        viewModel.toggleAppSelection("com.instagram.android")
        state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(0, state.selectedCount)
        assertFalse(selectedRepo.isSelected("com.instagram.android"))
        assertFalse(state.apps.first { it.packageName == "com.instagram.android" }.isSelected)
    }

    @Test
    fun `when commitment locked - adding app is permitted immediately`() = runTest {
        val selectedRepo = InMemorySelectedAppsRepository()
        val commitmentRepo = FakeCommitmentLockRepository().apply {
            enableCommitmentLock(consentConfirmed = true)
        }
        val viewModel = createDefaultViewModel(
            selectedRepo = selectedRepo,
            commitmentRepo = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleAppSelection("com.instagram.android")

        val state = viewModel.uiState.value as LimitsUiState.Success
        assertTrue(state.isCommitmentLocked)
        assertEquals(1, state.selectedCount)
        assertTrue(selectedRepo.isSelected("com.instagram.android"))
        assertTrue(state.apps.first { it.packageName == "com.instagram.android" }.isSelected)
        assertNull(state.lockedNotice)
    }

    @Test
    fun `when commitment locked - removing app is blocked and staged for midnight`() = runTest {
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf("com.instagram.android"))
        }
        val commitmentRepo = FakeCommitmentLockRepository().apply {
            enableCommitmentLock(consentConfirmed = true)
        }
        val viewModel = createDefaultViewModel(
            selectedRepo = selectedRepo,
            commitmentRepo = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Attempt removal
        viewModel.toggleAppSelection("com.instagram.android")

        val state = viewModel.uiState.value as LimitsUiState.Success
        // Still selected today
        assertTrue(selectedRepo.isSelected("com.instagram.android"))
        val insta = state.apps.first { it.packageName == "com.instagram.android" }
        assertTrue(insta.isSelected)
        assertTrue(insta.isPendingRemoval)
        assertNotNull(state.lockedNotice)

        // Toggling again cancels scheduled removal
        viewModel.toggleAppSelection("com.instagram.android")
        val stateAfterCancel = viewModel.uiState.value as LimitsUiState.Success
        assertFalse(stateAfterCancel.apps.first { it.packageName == "com.instagram.android" }.isPendingRemoval)
    }

    @Test
    fun `cancelStagedRemoval explicitly cancels pending removal`() = runTest {
        val selectedRepo = InMemorySelectedAppsRepository().apply {
            setSelectedPackages(setOf("com.instagram.android"))
        }
        val commitmentRepo = FakeCommitmentLockRepository().apply {
            enableCommitmentLock(consentConfirmed = true)
            stageAppRemoval("com.instagram.android")
        }
        val viewModel = createDefaultViewModel(
            selectedRepo = selectedRepo,
            commitmentRepo = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val beforeState = viewModel.uiState.value as LimitsUiState.Success
        assertTrue(beforeState.apps.first { it.packageName == "com.instagram.android" }.isPendingRemoval)

        val cancelled = viewModel.cancelStagedRemoval("com.instagram.android")
        assertTrue(cancelled)

        val afterState = viewModel.uiState.value as LimitsUiState.Success
        assertFalse(afterState.apps.first { it.packageName == "com.instagram.android" }.isPendingRemoval)
    }

    @Test
    fun `setDailyLimit when unlocked applies immediately`() = runTest {
        val dailyLimitRepo = InMemoryDailyLimitRepository()
        dailyLimitRepo.setLimitForPackage("com.instagram.android", 30 * 60 * 1000L)
        val viewModel = createDefaultViewModel(dailyLimitRepo = dailyLimitRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.setDailyLimit("com.instagram.android", 45 * 60 * 1000L)
        assertEquals(LimitsViewModel.LimitChangeResult.AppliedImmediately, result)
        assertEquals(45 * 60 * 1000L, dailyLimitRepo.getLimitForPackage("com.instagram.android"))

        val state = viewModel.uiState.value as LimitsUiState.Success
        val insta = state.apps.first { it.packageName == "com.instagram.android" }
        assertEquals("45 min", insta.formattedLimit)
    }

    @Test
    fun `when commitment locked - tightening limit applies immediately`() = runTest {
        val dailyLimitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage("com.instagram.android", 30 * 60 * 1000L)
        }
        val commitmentRepo = FakeCommitmentLockRepository().apply {
            enableCommitmentLock(consentConfirmed = true)
        }
        val viewModel = createDefaultViewModel(
            dailyLimitRepo = dailyLimitRepo,
            commitmentRepo = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Tighten from 30m to 15m
        val result = viewModel.setDailyLimit("com.instagram.android", 15 * 60 * 1000L)
        assertEquals(LimitsViewModel.LimitChangeResult.AppliedImmediately, result)
        assertEquals(15 * 60 * 1000L, dailyLimitRepo.getLimitForPackage("com.instagram.android"))

        val state = viewModel.uiState.value as LimitsUiState.Success
        val insta = state.apps.first { it.packageName == "com.instagram.android" }
        assertEquals("15 min", insta.formattedLimit)
        assertNull(insta.pendingLimitIncreaseMs)
    }

    @Test
    fun `when commitment locked - relaxing limit is staged until midnight`() = runTest {
        val dailyLimitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage("com.instagram.android", 30 * 60 * 1000L)
        }
        val commitmentRepo = FakeCommitmentLockRepository().apply {
            enableCommitmentLock(consentConfirmed = true)
        }
        val viewModel = createDefaultViewModel(
            dailyLimitRepo = dailyLimitRepo,
            commitmentRepo = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Attempt relaxation from 30m to 60m
        val result = viewModel.setDailyLimit("com.instagram.android", 60 * 60 * 1000L)
        assertTrue(result is LimitsViewModel.LimitChangeResult.DeferredToMidnight)
        assertEquals(60 * 60 * 1000L, (result as LimitsViewModel.LimitChangeResult.DeferredToMidnight).targetLimitMs)

        // Current limit remains 30m
        assertEquals(30 * 60 * 1000L, dailyLimitRepo.getLimitForPackage("com.instagram.android"))

        val state = viewModel.uiState.value as LimitsUiState.Success
        val insta = state.apps.first { it.packageName == "com.instagram.android" }
        assertEquals("30 min", insta.formattedLimit)
        assertEquals(60 * 60 * 1000L, insta.pendingLimitIncreaseMs)
        assertEquals("1 hr", insta.formattedPendingLimit)
        assertNotNull(state.lockedNotice)
    }

    @Test
    fun `cancelStagedLimitIncrease cancels staged limit increase`() = runTest {
        val dailyLimitRepo = InMemoryDailyLimitRepository().apply {
            setLimitForPackage("com.instagram.android", 30 * 60 * 1000L)
        }
        val commitmentRepo = FakeCommitmentLockRepository().apply {
            enableCommitmentLock(consentConfirmed = true)
            stageLimitIncrease("com.instagram.android", 60 * 60 * 1000L)
        }
        val viewModel = createDefaultViewModel(
            dailyLimitRepo = dailyLimitRepo,
            commitmentRepo = commitmentRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val beforeState = viewModel.uiState.value as LimitsUiState.Success
        val beforeInsta = beforeState.apps.first { it.packageName == "com.instagram.android" }
        assertEquals(60 * 60 * 1000L, beforeInsta.pendingLimitIncreaseMs)

        val cancelled = viewModel.cancelStagedLimitIncrease("com.instagram.android")
        assertTrue(cancelled)

        val afterState = viewModel.uiState.value as LimitsUiState.Success
        val afterInsta = afterState.apps.first { it.packageName == "com.instagram.android" }
        assertNull(afterInsta.pendingLimitIncreaseMs)
        assertNull(afterInsta.formattedPendingLimit)
    }

    @Test
    fun `refresh reloads data without duplicates or stale entries`() = runTest {
        val discoveryRepo = FakeAppDiscoveryRepository(Result.success(sampleApps))
        val viewModel = LimitsViewModel(appDiscoveryRepository = discoveryRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(4, state.apps.size)

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value as LimitsUiState.Success
        assertEquals(4, state.apps.size)
        // Verify no duplicate package names
        assertEquals(4, state.apps.map { it.packageName }.distinct().size)
    }

    // --- Helpers ---

    private fun createDefaultViewModel(
        apps: List<DiscoveredApp> = sampleApps,
        selectedRepo: SelectedAppsRepository = InMemorySelectedAppsRepository(),
        dailyLimitRepo: DailyLimitRepository = InMemoryDailyLimitRepository(),
        commitmentRepo: FakeCommitmentLockRepository? = null,
        usageRepo: UsageStatsRepository? = FakeUsageStatsRepository(hasPermission = true)
    ): LimitsViewModel {
        return LimitsViewModel(
            appDiscoveryRepository = FakeAppDiscoveryRepository(Result.success(apps)),
            selectedAppsRepository = selectedRepo,
            dailyLimitRepository = dailyLimitRepo,
            usageStatsRepository = usageRepo,
            commitmentLockRepository = commitmentRepo
        )
    }

    private class FakeAppDiscoveryRepository(
        private val result: Result<List<DiscoveredApp>>
    ) : AppDiscoveryRepository {
        override suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>> = result
    }

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
}
