package com.scrollstop.ui.screens.limits

import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.scrollstop.domain.model.CommitmentLockState
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LimitsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleAppItem1 = LimitsAppItem(
        packageName = "com.instagram.android",
        appLabel = "Instagram",
        isSelected = true,
        totalTimeInForegroundMs = 15 * 60 * 1000L,
        formattedUsage = "15 min",
        limitDurationMs = 30 * 60 * 1000L,
        formattedLimit = "30 min",
        remainingDurationMs = 15 * 60 * 1000L,
        formattedRemaining = "15 min",
        limitState = LimitState.WITHIN_LIMIT,
        category = ApplicationInfo.CATEGORY_SOCIAL
    )

    private val sampleAppItem2 = LimitsAppItem(
        packageName = "com.google.android.youtube",
        appLabel = "YouTube",
        isSelected = false,
        totalTimeInForegroundMs = 0L,
        formattedUsage = "0 min",
        limitDurationMs = 60 * 60 * 1000L,
        formattedLimit = "1 hr",
        remainingDurationMs = 60 * 60 * 1000L,
        formattedRemaining = "1 hr",
        limitState = LimitState.NOT_STARTED,
        category = ApplicationInfo.CATEGORY_VIDEO
    )

    @Test
    fun loadingState_displaysLoadingIndicator() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = LimitsUiState.Loading,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading applications and limits").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discovering installed applications...").assertIsDisplayed()
    }

    @Test
    fun permissionRequiredState_displaysGrantButton() {
        var grantClicked = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = LimitsUiState.PermissionRequired,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {},
                    onRequestUsagePermission = { grantClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Usage Access Required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant Usage Access").assertIsDisplayed().performClick()
        assertTrue(grantClicked)
    }

    @Test
    fun errorState_displaysErrorMessageAndRetryButton() {
        var retried = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = LimitsUiState.Error("Discovery service disconnected"),
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = { retried = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Unable to Load Limits").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discovery service disconnected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun emptyCatalogState_displaysEmptyMessage() {
        var refreshed = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = LimitsUiState.Empty(),
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = { refreshed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("No Applications Discovered").assertIsDisplayed()
        composeTestRule.onNodeWithText("Refresh").assertIsDisplayed().performClick()
        assertTrue(refreshed)
    }

    @Test
    fun searchAndFilterControls_invokeCallbacks() {
        var searchedQuery: String? = null
        var selectedFilter: LimitsFilter? = null

        val successState = LimitsUiState.Success(
            apps = listOf(sampleAppItem1, sampleAppItem2),
            searchQuery = "",
            activeFilter = LimitsFilter.ALL,
            selectedCount = 1,
            totalDiscoveredCount = 2
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = { searchedQuery = it },
                    onFilterSelect = { selectedFilter = it },
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        // Test search input
        composeTestRule.onNodeWithContentDescription("Search apps input")
            .assertIsDisplayed()
            .performTextInput("Insta")
        assertEquals("Insta", searchedQuery)

        // Test filter chip selection
        composeTestRule.onNodeWithContentDescription("Filter Controlled apps (1)")
            .assertIsDisplayed()
            .performClick()
        assertEquals(LimitsFilter.CONTROLLED, selectedFilter)

        composeTestRule.onNodeWithContentDescription("Filter Social and Video apps")
            .assertIsDisplayed()
            .performClick()
        assertEquals(LimitsFilter.SOCIAL_VIDEO, selectedFilter)
    }

    @Test
    fun emptySearchResults_displaysContextualMessageAndClearAction() {
        var cleared = false

        val successState = LimitsUiState.Success(
            apps = emptyList(),
            searchQuery = "nonexistent",
            activeFilter = LimitsFilter.ALL,
            selectedCount = 1,
            totalDiscoveredCount = 2
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = { if (it.isEmpty()) cleared = true },
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No matching apps found").assertIsDisplayed()
        composeTestRule.onNodeWithText("No installed applications matched \"nonexistent\".").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear search query").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(cleared)
    }

    @Test
    fun emptyControlledFilter_displaysHelpfulEmptyMessage() {
        val successState = LimitsUiState.Success(
            apps = emptyList(),
            searchQuery = "",
            activeFilter = LimitsFilter.CONTROLLED,
            selectedCount = 0,
            totalDiscoveredCount = 5
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No controlled applications").assertIsDisplayed()
        composeTestRule.onNodeWithText("You haven't selected any apps to monitor yet. Switch to 'All' to choose apps to protect yourself from.").assertIsDisplayed()
    }

    @Test
    fun emptySocialVideoFilter_displaysHonestCategoryMessage() {
        val successState = LimitsUiState.Success(
            apps = emptyList(),
            searchQuery = "",
            activeFilter = LimitsFilter.SOCIAL_VIDEO,
            selectedCount = 0,
            totalDiscoveredCount = 5
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No Social & Video apps detected").assertIsDisplayed()
        composeTestRule.onNodeWithText("No applications on this device were classified as Social or Video by Android system metadata.").assertIsDisplayed()
    }

    @Test
    fun appRow_displaysDetailsAndInvokesSelectionToggle() {
        var toggledPackage: String? = null

        val successState = LimitsUiState.Success(
            apps = listOf(sampleAppItem1),
            selectedCount = 1,
            totalDiscoveredCount = 1
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = { toggledPackage = it },
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.instagram.android").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today: 15 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Limit: 30 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remaining: 15 min").assertIsDisplayed()

        val checkbox = composeTestRule.onNodeWithContentDescription("Toggle monitoring for Instagram")
        checkbox.assertIsDisplayed().assertIsOn().performClick()
        assertEquals("com.instagram.android", toggledPackage)
    }

    @Test
    fun appRow_whenStagedRemovalAndLimit_displaysBadges() {
        val stagedItem = sampleAppItem1.copy(
            isPendingRemoval = true,
            pendingLimitIncreaseMs = 45 * 60 * 1000L,
            formattedPendingLimit = "45 min"
        )

        val successState = LimitsUiState.Success(
            apps = listOf(stagedItem),
            selectedCount = 1,
            totalDiscoveredCount = 1,
            isCommitmentLocked = true
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Removal at midnight").assertIsDisplayed()
        composeTestRule.onNodeWithText("New limit at midnight: 45 min").assertIsDisplayed()
    }

    @Test
    fun clickingEditLimit_opensLimitConfigurationModal() {
        val successState = LimitsUiState.Success(
            apps = listOf(sampleAppItem1),
            selectedCount = 1,
            totalDiscoveredCount = 1
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Edit limit for Instagram")
            .assertIsDisplayed()
            .performClick()

        // Modal is opened
        composeTestRule.onNodeWithText("Preset Limits").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Cancel without saving").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun commitmentLockBanner_displaysLockedAndUnlockedStates() {
        // Locked State
        val lockedState = LimitsUiState.Success(
            apps = listOf(sampleAppItem1),
            isCommitmentLocked = true
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = lockedState,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Commitment Lock Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Locked").assertIsDisplayed()
    }

    @Test
    fun lightTheme_rendersWithoutCrashing() {
        val successState = LimitsUiState.Success(apps = listOf(sampleAppItem1))

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()
    }

    @Test
    fun darkTheme_rendersWithoutCrashing() {
        val successState = LimitsUiState.Success(apps = listOf(sampleAppItem1))

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
                LimitsScreen(
                    uiState = successState,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = {},
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()
    }

    @Test
    fun cancellingStagedRemoval_fromAppRow_callsCallback() {
        var cancelledPkg: String? = null
        val state = LimitsUiState.Success(
            apps = listOf(sampleAppItem1.copy(isPendingRemoval = true)),
            isCommitmentLocked = true
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitsScreen(
                    uiState = state,
                    onSearchQueryChange = {},
                    onFilterSelect = {},
                    onToggleAppSelection = {},
                    onSetDailyLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = {},
                    onCancelStagedRemoval = { cancelledPkg = it },
                    onClearNotice = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Cancel scheduled removal for Instagram")
            .assertIsDisplayed()
            .performClick()

        assertEquals("com.instagram.android", cancelledPkg)
    }
}
