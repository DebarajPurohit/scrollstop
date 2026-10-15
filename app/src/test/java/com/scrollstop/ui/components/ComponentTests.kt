package com.scrollstop.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.domain.model.DailyUsageRecord
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.theme.StopDoomScrollTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComponentTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // ScrollStopTopBar Tests
    // =========================================================================

    @Test
    fun topBar_rendersTitleAndSubtitleAndInvokesBack() {
        var backClicked = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                ScrollStopTopBar(
                    title = "Test Screen",
                    subtitle = "Test Subtitle",
                    onNavigateBack = { backClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Test Screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Subtitle").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed().performClick()

        assertTrue(backClicked)
    }

    // =========================================================================
    // ScrollStopBottomNav Tests
    // =========================================================================

    @Test
    fun bottomNav_displaysAllFourDestinationsAndHandlesSelection() {
        var selectedDestination: BottomNavDestination? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                ScrollStopBottomNav(
                    currentDestination = BottomNavDestination.HOME,
                    onDestinationSelected = { selectedDestination = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Limits").assertIsDisplayed()
        composeTestRule.onNodeWithText("History").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        // Home is selected
        composeTestRule.onNodeWithContentDescription("Home tab").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Limits tab").assertIsNotSelected()

        // Click Limits tab
        composeTestRule.onNodeWithContentDescription("Limits tab").performClick()
        assertEquals(BottomNavDestination.LIMITS, selectedDestination)
    }

    // =========================================================================
    // AppIconThumbnail Tests
    // =========================================================================

    @Test
    fun appIconThumbnail_rendersInitialLetterWhenDrawableNull() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppIconThumbnail(
                    drawable = null,
                    appName = "Instagram",
                    packageName = "com.instagram.android"
                )
            }
        }

        composeTestRule.onNodeWithText("I").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Instagram application icon").assertIsDisplayed()
    }

    // =========================================================================
    // UsageProgressBar Tests
    // =========================================================================

    @Test
    fun usageProgressBar_rendersWithinLimitAndPercent() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageProgressBar(
                    usedMs = 60_000L,
                    limitMs = 120_000L,
                    showPercent = true
                )
            }
        }

        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Usage progress: 50%").assertIsDisplayed()
    }

    @Test
    fun usageProgressBar_rendersUnrestrictedStateWhenLimitNull() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageProgressBar(
                    usedMs = 60_000L,
                    limitMs = null,
                    showPercent = true
                )
            }
        }

        composeTestRule.onNodeWithText("No limit").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Unrestricted application usage").assertIsDisplayed()
    }

    @Test
    fun usageProgressBar_rendersExceededStateWhenUsedExceedsLimit() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageProgressBar(
                    usedMs = 150_000L,
                    limitMs = 120_000L,
                    showPercent = true
                )
            }
        }

        composeTestRule.onNodeWithText("125%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Usage progress: 125%").assertIsDisplayed()
    }

    @Test
    fun usageProgressBar_rendersReachedStateWhenLimitZeroOrNegative() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageProgressBar(
                    usedMs = 10_000L,
                    limitMs = 0L,
                    showPercent = true
                )
            }
        }

        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Usage progress: 100%").assertIsDisplayed()
    }

    // =========================================================================
    // ProtectionStatusBadge Tests
    // =========================================================================

    @Test
    fun protectionStatusBadge_mapsProtectionHealthStateCorrectly() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                ProtectionStatusBadge(healthState = ProtectionHealthState.PROTECTED)
            }
        }

        composeTestRule.onNodeWithText("Protected").assertIsDisplayed()
    }

    @Test
    fun protectionStatusBadge_invokesClickCallback() {
        var clicked = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                ProtectionStatusBadge(
                    text = "Accessibility Required",
                    severity = BadgeSeverity.WARNING,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Accessibility Required").performClick()
        assertTrue(clicked)
    }

    // =========================================================================
    // SevenDayUsageChart Tests
    // =========================================================================

    @Test
    fun sevenDayUsageChart_completeHistoryCalculatesStatsAndRendersDays() {
        val days = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Tue 9", "Tue", 2L, totalScreenTimeMs = 7_200_000L, hasRecord = true),
            DailyUsageRecord("Wed 10", "Wed", 3L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Thu 11", "Thu", 4L, totalScreenTimeMs = 7_200_000L, hasRecord = true),
            DailyUsageRecord("Fri 12", "Fri", 5L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Sat 13", "Sat", 6L, totalScreenTimeMs = 7_200_000L, hasRecord = true),
            DailyUsageRecord("Today", "Sun", 7L, totalScreenTimeMs = 3_600_000L, hasRecord = true, isCurrentDay = true)
        )

        val state = SevenDayChartState.Success(days = days)
        assertEquals(7, state.recordedDayCount)
        assertFalse(state.isHistoryIncomplete)
        assertEquals("1h 25m", state.formattedAverage)

        var selectedDay: DailyUsageRecord? = null

        composeTestRule.setContent {
            StopDoomScrollTheme {
                SevenDayUsageChart(
                    state = state,
                    onSelectDay = { selectedDay = it }
                )
            }
        }

        composeTestRule.onNodeWithText("7-Day Screen Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily avg (7d): 1h 25m").assertIsDisplayed()

        // Click a day
        composeTestRule.onNodeWithContentDescription("Tue 9: 2h 0m").performClick()
        assertEquals("Tue 9", selectedDay?.dayLabel)
    }

    @Test
    fun sevenDayUsageChart_incompleteHistoryHonorsNonFabricationRule() {
        val days = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Tue 9", "Tue", 2L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Wed 10", "Wed", 3L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Thu 11", "Thu", 4L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Fri 12", "Fri", 5L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Sat 13", "Sat", 6L, totalScreenTimeMs = 7_200_000L, hasRecord = true),
            DailyUsageRecord("Today", "Sun", 7L, totalScreenTimeMs = 3_600_000L, hasRecord = true, isCurrentDay = true)
        )

        val state = SevenDayChartState.Success(days = days)
        assertEquals(3, state.recordedDayCount)
        assertTrue(state.isHistoryIncomplete)
        // Average strictly across recorded days: (1h + 2h + 1h) / 3 = 1h 20m
        assertEquals("1h 20m", state.formattedAverage)

        composeTestRule.setContent {
            StopDoomScrollTheme {
                SevenDayUsageChart(state = state)
            }
        }

        // History status badge
        composeTestRule.onNodeWithText("3 of 7 days recorded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily avg (3d): 1h 20m").assertIsDisplayed()

        // Missing days must announce "No data recorded"
        composeTestRule.onNodeWithContentDescription("Mon 8: No data recorded").assertIsDisplayed()
    }

    @Test
    fun sevenDayUsageChart_genuineZeroUsageDistinguishedFromMissingData() {
        val days = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 0L, hasRecord = true),
            DailyUsageRecord("Tue 9", "Tue", 2L, totalScreenTimeMs = 0L, hasRecord = false)
        )

        val state = SevenDayChartState.Success(days = days)
        assertEquals(1, state.recordedDayCount)
        assertTrue(state.isHistoryIncomplete)
        assertEquals("0m", state.formattedAverage)

        composeTestRule.setContent {
            StopDoomScrollTheme {
                SevenDayUsageChart(state = state)
            }
        }

        // Genuine zero recorded day displays 0m and accessibility text with 0m
        composeTestRule.onNodeWithContentDescription("Mon 8: 0m").assertIsDisplayed()
        // Missing day displays No data recorded
        composeTestRule.onNodeWithContentDescription("Tue 9: No data recorded").assertIsDisplayed()
    }

    @Test
    fun sevenDayUsageChart_errorStateDisplaysMessageAndRetry() {
        var retryTriggered = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                SevenDayUsageChart(
                    state = SevenDayChartState.Error(
                        message = "Failed to query usage stats",
                        onRetry = { retryTriggered = true }
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to query usage stats").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue(retryTriggered)
    }
}
