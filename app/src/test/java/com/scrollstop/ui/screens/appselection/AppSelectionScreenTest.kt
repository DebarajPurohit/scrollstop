package com.scrollstop.ui.screens.appselection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.domain.model.DiscoveredApp
import com.scrollstop.ui.theme.StopDoomScrollTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppSelectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appSelectionScreen_rendersTitleSubtitleAndApps() {
        val testApps = listOf(
            DiscoveredApp("com.social.network", "Social Network", isSelected = false),
            DiscoveredApp("com.video.platform", "Video Platform", isSelected = true)
        )
        val uiState = AppSelectionUiState.Success(apps = testApps, selectedCount = 1)

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppSelectionScreen(
                    uiState = uiState,
                    onToggleAppSelection = {},
                    onRetry = {},
                    onNavigateBack = {}
                )
            }
        }

        // Verify title & subtitle are displayed
        composeTestRule.onNodeWithText("Choose apps to control").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select the apps you want Stop Doom Scroll to limit.").assertIsDisplayed()

        // Verify selection counter
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()

        // Verify app labels are displayed
        composeTestRule.onNodeWithText("Social Network").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.social.network").assertIsDisplayed()
        composeTestRule.onNodeWithText("Video Platform").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.video.platform").assertIsDisplayed()
    }

    @Test
    fun appSelectionScreen_clickingAppRow_invokesToggleCallback() {
        val testApps = listOf(
            DiscoveredApp("com.social.network", "Social Network", isSelected = false)
        )
        val uiState = AppSelectionUiState.Success(apps = testApps, selectedCount = 0)
        var toggledPackage: String? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppSelectionScreen(
                    uiState = uiState,
                    onToggleAppSelection = { pkg -> toggledPackage = pkg },
                    onRetry = {},
                    onNavigateBack = {}
                )
            }
        }

        // Click app row
        composeTestRule.onNodeWithText("Social Network").performClick()

        assertEquals("com.social.network", toggledPackage)
    }

    @Test
    fun appSelectionScreen_emptyState_rendersEmptyMessage() {
        val uiState = AppSelectionUiState.Success(apps = emptyList(), selectedCount = 0)

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppSelectionScreen(
                    uiState = uiState,
                    onToggleAppSelection = {},
                    onRetry = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No launchable applications found.").assertIsDisplayed()
    }
}
