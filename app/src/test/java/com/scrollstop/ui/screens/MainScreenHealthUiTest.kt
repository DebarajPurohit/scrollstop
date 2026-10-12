package com.scrollstop.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.theme.StopDoomScrollTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainScreenHealthUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainScreen_protectedState_rendersActiveBanner() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        selectedAppCount = 3
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("PROTECTION ACTIVE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enforcing daily limits for 3 selected apps.").assertIsDisplayed()
    }

    @Test
    fun mainScreen_usageAccessRequired_rendersWarningAndRecoveryButton() {
        var clicked = false
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.USAGE_ACCESS_REQUIRED,
                        selectedAppCount = 2
                    ),
                    onGrantUsageAccess = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Grant Usage Access Recovery Button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Grant Usage Access Recovery Button").performClick()
        assertTrue(clicked)
    }

    @Test
    fun mainScreen_accessibilityInterrupted_rendersInterruptedAndRecoveryButton() {
        var clicked = false
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.ACCESSIBILITY_INTERRUPTED,
                        selectedAppCount = 1
                    ),
                    onEnableAccessibility = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("PROTECTION INTERRUPTED").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Re-enable Protection Recovery Button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Re-enable Protection Recovery Button").performClick()
        assertTrue(clicked)
    }
}
