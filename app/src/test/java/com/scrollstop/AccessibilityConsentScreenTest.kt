package com.scrollstop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.domain.model.AccessibilityServiceState
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.ui.screens.accessibility.AccessibilityConsentScreen
import com.scrollstop.ui.screens.accessibility.AccessibilityConsentUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AccessibilityConsentScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `test prominent disclosure UI displays content correctly and handles grant click`() {
        var grantClicked = false
        var backClicked = false

        val state = AccessibilityConsentUiState(
            serviceState = AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED,
            latestTrigger = null
        )

        composeTestRule.setContent {
            MaterialTheme {
                AccessibilityConsentScreen(
                    uiState = state,
                    onGrantConsent = { grantClicked = true },
                    onNavigateBack = { backClicked = true }
                )
            }
        }

        // Verify status text
        composeTestRule.onNodeWithText("ACCESSIBILITY NOT GRANTED").assertIsDisplayed()

        // Verify grant button click
        composeTestRule.onNodeWithContentDescription("Grant Accessibility Permission Button")
            .assertIsDisplayed()
            .performClick()

        assertTrue(grantClicked)

        // Verify back button click
        composeTestRule.onNodeWithContentDescription("Back Button")
            .assertIsDisplayed()
            .performClick()

        assertTrue(backClicked)
    }

    @Test
    fun `test prominent disclosure UI displays active state and enforcement trigger info`() {
        val state = AccessibilityConsentUiState(
            serviceState = AccessibilityServiceState.ACCESSIBILITY_ACTIVE,
            latestTrigger = EnforcementTrigger(
                packageName = "com.google.android.youtube",
                reason = LimitState.LIMIT_REACHED
            )
        )

        composeTestRule.setContent {
            MaterialTheme {
                AccessibilityConsentScreen(
                    uiState = state,
                    onGrantConsent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("ACCESSIBILITY ACTIVE").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Internal Enforcement Trigger Detection Info")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
