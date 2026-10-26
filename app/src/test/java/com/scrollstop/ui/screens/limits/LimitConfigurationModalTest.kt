package com.scrollstop.ui.screens.limits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.scrollstop.domain.rules.LimitState
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LimitConfigurationModalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testAppItem = LimitsAppItem(
        packageName = "com.instagram.android",
        appLabel = "Instagram",
        isSelected = true,
        totalTimeInForegroundMs = 15 * 60 * 1000L,
        formattedUsage = "15 min",
        limitDurationMs = 30 * 60 * 1000L,
        formattedLimit = "30 min",
        remainingDurationMs = 15 * 60 * 1000L,
        formattedRemaining = "15 min",
        limitState = LimitState.WITHIN_LIMIT
    )

    @Test
    fun selectingEachPreset_updatesSelectedLimitPreview() {
        var savedLimit: Long? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    showDebugPresets = false,
                    onDismiss = {},
                    onSaveLimit = { _, limitMs -> savedLimit = limitMs }
                )
            }
        }

        // Header check
        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current Daily Limit").assertIsDisplayed()

        // 1. Select 15 min
        composeTestRule.onNodeWithContentDescription("Preset 15 min").performClick()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 15 min").assertIsDisplayed()

        // 2. Select 45 min
        composeTestRule.onNodeWithContentDescription("Preset 45 min").performClick()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 45 min").assertIsDisplayed()

        // 3. Select 1 hour
        composeTestRule.onNodeWithContentDescription("Preset 1 hour").performClick()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 1 hr").assertIsDisplayed()

        // 4. Select 1.5 hours
        composeTestRule.onNodeWithContentDescription("Preset 1.5 hours").performClick()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 1 hr 30 min").assertIsDisplayed()

        // 5. Select 2 hours
        composeTestRule.onNodeWithContentDescription("Preset 2 hours").performClick()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 2 hr").assertIsDisplayed()

        // Save 2 hours
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().performClick()
        assertEquals(120 * 60 * 1000L, savedLimit)
    }

    @Test
    fun debugPreset_omittedWhenShowDebugPresetsIsFalse() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    showDebugPresets = false,
                    onDismiss = {},
                    onSaveLimit = { _, _ -> }
                )
            }
        }
        composeTestRule.onNode(hasContentDescription("Preset 2 min (Test)")).assertDoesNotExist()
    }

    @Test
    fun debugPreset_renderedWhenShowDebugPresetsIsTrue() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    showDebugPresets = true,
                    onDismiss = {},
                    onSaveLimit = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Preset 2 min (Test)").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 2 min").assertIsDisplayed()
    }

    @Test
    fun enteringAndValidatingCustomDurations() {
        var savedLimit: Long? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    showDebugPresets = false,
                    onDismiss = {},
                    onSaveLimit = { _, limitMs -> savedLimit = limitMs }
                )
            }
        }

        // Open Custom duration section
        composeTestRule.onNodeWithContentDescription("Custom duration option").performClick()
        composeTestRule.onNodeWithContentDescription("Custom duration inputs").assertIsDisplayed()

        // Enter 0 hours and 0 minutes -> validation error
        val hoursInput = composeTestRule.onNodeWithContentDescription("Custom duration hours input")
        val minutesInput = composeTestRule.onNodeWithContentDescription("Custom duration minutes input")

        hoursInput.performTextClearance()
        hoursInput.performTextInput("0")
        minutesInput.performTextClearance()
        minutesInput.performTextInput("0")

        composeTestRule.onNodeWithText("Daily limit must be at least 1 minute.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().assertIsNotEnabled()

        // Enter 25 hours -> validation error
        hoursInput.performTextClearance()
        hoursInput.performTextInput("25")
        composeTestRule.onNodeWithText("Daily limit cannot exceed 24 hours.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().assertIsNotEnabled()

        // Enter valid 1 hour 45 minutes
        hoursInput.performTextClearance()
        hoursInput.performTextInput("1")
        minutesInput.performTextClearance()
        minutesInput.performTextInput("45")

        composeTestRule.onNodeWithContentDescription("Selected new limit value: 1 hr 45 min").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().assertIsEnabled().performClick()

        assertEquals((105 * 60 * 1000L), savedLimit)
    }

    @Test
    fun savingValidLimit_invokesCallbackWithPackageAndDuration() {
        var savedPackage: String? = null
        var savedDuration: Long? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    onDismiss = {},
                    onSaveLimit = { pkg, limitMs ->
                        savedPackage = pkg
                        savedDuration = limitMs
                    }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Preset 45 min").performClick()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().performClick()

        assertEquals("com.instagram.android", savedPackage)
        assertEquals(45 * 60 * 1000L, savedDuration)
    }

    @Test
    fun cancellingWithoutSaving_invokesDismissCallback() {
        var dismissed = false
        var saved = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    onDismiss = { dismissed = true },
                    onSaveLimit = { _, _ -> saved = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Cancel without saving").performScrollTo().performClick()

        assertTrue(dismissed)
        assertFalse(saved)
    }

    @Test
    fun tighteningLimit_underCommitmentLock_displaysImmediateEffectNotice() {
        // Current limit 60 min (1 hr), commitment locked
        val appItem = testAppItem.copy(limitDurationMs = 60 * 60 * 1000L, formattedLimit = "1 hr")

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = appItem,
                    isCommitmentLocked = true,
                    onDismiss = {},
                    onSaveLimit = { _, _ -> }
                )
            }
        }

        // Select tighter limit 15 min
        composeTestRule.onNodeWithContentDescription("Preset 15 min").performClick()

        composeTestRule.onNodeWithText(
            "Decreasing your limit strengthens your protection and takes effect immediately."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun relaxingLimit_underCommitmentLock_displaysStagedMidnightNotice() {
        // Current limit 15 min, commitment locked
        val appItem = testAppItem.copy(limitDurationMs = 15 * 60 * 1000L, formattedLimit = "15 min")

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = appItem,
                    isCommitmentLocked = true,
                    onDismiss = {},
                    onSaveLimit = { _, _ -> }
                )
            }
        }

        // Select relaxed limit 45 min
        composeTestRule.onNodeWithContentDescription("Preset 45 min").performClick()

        composeTestRule.onNodeWithText(
            "Commitment Lock is active. Increasing your daily limit will take effect at next midnight (12:00 AM) to protect your commitment."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancellingStagedRelaxation_invokesCancellationCallback() {
        var cancelledPackage: String? = null
        val appItemWithStaged = testAppItem.copy(
            pendingLimitIncreaseMs = 60 * 60 * 1000L,
            formattedPendingLimit = "1 hr"
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = appItemWithStaged,
                    isCommitmentLocked = true,
                    onDismiss = {},
                    onSaveLimit = { _, _ -> },
                    onCancelStagedLimitIncrease = { pkg -> cancelledPackage = pkg }
                )
            }
        }

        composeTestRule.onNodeWithText("Pending Limit Increase").assertIsDisplayed()
        composeTestRule.onNodeWithText("A new limit of 1 hr is scheduled to take effect at next midnight.").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Cancel Staged Limit Increase").performClick()
        assertEquals("com.instagram.android", cancelledPackage)
    }

    @Test
    fun handlingSaveError_displaysErrorMessage_withoutLosingUserSelection() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    saveErrorMessage = "Storage write failure. Please retry.",
                    onDismiss = {},
                    onSaveLimit = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Storage write failure. Please retry.").performScrollTo().assertIsDisplayed()

        // Select 45 min
        composeTestRule.onNodeWithContentDescription("Preset 45 min").performClick()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 45 min").performScrollTo().assertIsDisplayed()

        // Error message still visible
        composeTestRule.onNodeWithText("Storage write failure. Please retry.").performScrollTo().assertIsDisplayed()
        // Save button is enabled and ready to retry
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().assertIsEnabled()
    }

    @Test
    fun loadingState_disablesButtons_andShowsProgressIndicator() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    isLoading = true,
                    onDismiss = {},
                    onSaveLimit = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Saving daily limit").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Cancel without saving").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun lightTheme_rendersWithoutCrashing() {
        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    isCommitmentLocked = true,
                    onDismiss = {},
                    onSaveLimit = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
    }

    @Test
    fun darkTheme_rendersWithoutCrashing() {
        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    isCommitmentLocked = true,
                    onDismiss = {},
                    onSaveLimit = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
    }

    @Test
    fun equivalentPresetAndCustomDuration_produceIdenticalEffectiveLimit() {
        var savedLimit: Long? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    showDebugPresets = false,
                    onDismiss = {},
                    onSaveLimit = { _, limitMs -> savedLimit = limitMs }
                )
            }
        }

        // 1. Select 15 min preset
        composeTestRule.onNodeWithContentDescription("Preset 15 min").performClick()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 15 min").assertIsDisplayed()

        // 2. Switch to Custom mode - should preserve the 15 min value
        composeTestRule.onNodeWithContentDescription("Custom duration option").performClick()
        composeTestRule.onNodeWithContentDescription("Custom duration inputs").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Selected new limit value: 15 min").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().performClick()
        assertEquals(15 * 60 * 1000L, savedLimit)
    }

    @Test
    fun enteringShortCustomDuration_3_minutes_savesExactDuration() {
        var savedLimit: Long? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    showDebugPresets = false,
                    onDismiss = {},
                    onSaveLimit = { _, limitMs -> savedLimit = limitMs }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Custom duration option").performClick()
        val hoursInput = composeTestRule.onNodeWithContentDescription("Custom duration hours input")
        val minutesInput = composeTestRule.onNodeWithContentDescription("Custom duration minutes input")

        hoursInput.performTextClearance()
        hoursInput.performTextInput("0")
        minutesInput.performTextClearance()
        minutesInput.performTextInput("3")

        composeTestRule.onNodeWithContentDescription("Selected new limit value: 3 min").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().performClick()

        assertEquals(3 * 60 * 1000L, savedLimit)
    }

    @Test
    fun enteringBoundaryCustomDurations_1_minute_and_24_hours() {
        var savedLimit: Long? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                LimitConfigurationContent(
                    appItem = testAppItem,
                    showDebugPresets = false,
                    onDismiss = {},
                    onSaveLimit = { _, limitMs -> savedLimit = limitMs }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Custom duration option").performClick()
        val hoursInput = composeTestRule.onNodeWithContentDescription("Custom duration hours input")
        val minutesInput = composeTestRule.onNodeWithContentDescription("Custom duration minutes input")

        // 1 minute (minimum valid)
        hoursInput.performTextClearance()
        hoursInput.performTextInput("0")
        minutesInput.performTextClearance()
        minutesInput.performTextInput("1")

        composeTestRule.onNodeWithContentDescription("Selected new limit value: 1 min").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().assertIsEnabled()

        // 60 minutes in minutes input -> error
        minutesInput.performTextClearance()
        minutesInput.performTextInput("60")
        composeTestRule.onNodeWithText("Minutes must be between 0 and 59.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().assertIsNotEnabled()

        // 24 hours (maximum valid)
        hoursInput.performTextClearance()
        hoursInput.performTextInput("24")
        minutesInput.performTextClearance()
        minutesInput.performTextInput("0")

        composeTestRule.onNodeWithContentDescription("Selected new limit value: 24 hr").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save limit").performScrollTo().assertIsEnabled().performClick()

        assertEquals(24 * 60 * 60 * 1000L, savedLimit)
    }
}
