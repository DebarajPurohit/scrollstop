package com.scrollstop.ui.screens.usage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.scrollstop.domain.model.AppUsageInfo
import com.scrollstop.ui.theme.StopDoomScrollTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `UsageScreen displays PermissionRequired state card and grant button`() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageScreen(
                    uiState = UsageUiState.PermissionRequired,
                    onRefresh = {},
                    onNavigateToAppSelection = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Usage Access Required").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Stop Doom Scroll needs Usage Access to measure how long you use the apps you choose to control."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant Usage Access").assertIsDisplayed()
    }

    @Test
    fun `UsageScreen displays Loading state indicator`() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageScreen(
                    uiState = UsageUiState.Loading,
                    onRefresh = {},
                    onNavigateToAppSelection = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading today's usage statistics").assertIsDisplayed()
    }

    @Test
    fun `UsageScreen displays EmptySelection state card`() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageScreen(
                    uiState = UsageUiState.EmptySelection,
                    onRefresh = {},
                    onNavigateToAppSelection = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No applications selected for usage tracking.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose Apps to Control").assertIsDisplayed()
    }

    @Test
    fun `UsageScreen displays Success state list with usage formatting`() {
        val usageList = listOf(
            AppUsageInfo(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                totalTimeInForegroundMs = 1421000L,
                formattedUsage = "23 min 41 sec"
            ),
            AppUsageInfo(
                packageName = "com.google.android.youtube",
                appLabel = "YouTube",
                totalTimeInForegroundMs = 0L,
                formattedUsage = "0 min"
            )
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageScreen(
                    uiState = UsageUiState.Success(usageList = usageList),
                    onRefresh = {},
                    onNavigateToAppSelection = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.instagram.android").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today's usage: 23 min 41 sec").assertIsDisplayed()

        composeTestRule.onNodeWithText("YouTube").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.google.android.youtube").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today's usage: 0 min").assertIsDisplayed()
    }
}
