package com.scrollstop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.ui.screens.MainScreen
import com.scrollstop.ui.theme.StopDoomScrollTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainScreen_rendersTitleDescriptionAndStatusCard() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen()
            }
        }

        // Verify title exists and is displayed
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()

        // Verify tagline exists and is displayed
        composeTestRule.onNodeWithText("Set your limit. When your time is up, we stop you.").assertIsDisplayed()

        // Verify development build status card title exists and is displayed
        composeTestRule.onNodeWithText("Development Build").assertIsDisplayed()

        // Verify development build status card body exists and is displayed
        composeTestRule.onNodeWithText("Core protection is being built.").assertIsDisplayed()
    }
}
