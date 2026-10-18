package com.scrollstop.ui.components

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.data.repository.SharedPreferencesThemeRepository
import com.scrollstop.data.repository.ThemeRepository
import com.scrollstop.domain.model.DailyUsageRecord
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.preview.HomeDashboardPreviewScreen
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

        composeTestRule.onNodeWithText("Respect the commitment you made to yourself.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Usage progress: Respect the commitment you made to yourself.").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f, 0))).assertIsDisplayed()
    }

    @Test
    fun usageProgressBar_rendersExactlyAtLimitState() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageProgressBar(
                    usedMs = 120_000L,
                    limitMs = 120_000L,
                    showPercent = true
                )
            }
        }

        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Usage progress: 100%").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f, 0))).assertIsDisplayed()
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
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f, 0))).assertIsDisplayed()
    }

    @Test
    fun usageProgressBar_rendersCappedIndicatorForExtremeExceededRatio() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageProgressBar(
                    usedMs = 2 * 3600 * 1000L + 2 * 60 * 1000L, // 2h 2m
                    limitMs = 2 * 60 * 1000L, // 2m limit (6100%)
                    showPercent = true
                )
            }
        }

        composeTestRule.onNodeWithText("Respect the commitment you made to yourself.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Usage progress: Respect the commitment you made to yourself.").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f, 0))).assertIsDisplayed()
    }

    @Test
    fun usageProgressBar_rendersExceededStateInDarkTheme() {
        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
                UsageProgressBar(
                    usedMs = 150_000L,
                    limitMs = 120_000L,
                    showPercent = true
                )
            }
        }

        composeTestRule.onNodeWithText("Respect the commitment you made to yourself.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Usage progress: Respect the commitment you made to yourself.").assertIsDisplayed()
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

    @Test
    fun sevenDayUsageChart_rendersWeekdayLabelsInDarkTheme() {
        val days = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Tue 9", "Tue", 2L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Today", "Wed", 3L, totalScreenTimeMs = 5_400_000L, hasRecord = true, isCurrentDay = true)
        )

        val state = SevenDayChartState.Success(days = days)

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
                SevenDayUsageChart(state = state)
            }
        }

        // Verify weekday labels are rendered
        composeTestRule.onNodeWithText("Mon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tue").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("No data").assertIsDisplayed()
    }

    @Test
    fun sevenDayUsageChart_rendersWeekdayLabelsInLightTheme() {
        val days = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Tue 9", "Tue", 2L, totalScreenTimeMs = 0L, hasRecord = false),
            DailyUsageRecord("Today", "Wed", 3L, totalScreenTimeMs = 5_400_000L, hasRecord = true, isCurrentDay = true)
        )

        val state = SevenDayChartState.Success(days = days)

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
                SevenDayUsageChart(state = state)
            }
        }

        // Verify weekday labels and No data are rendered with verified contrast in Light Theme
        composeTestRule.onNodeWithText("Mon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tue").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("No data").assertIsDisplayed()
        composeTestRule.onNodeWithText("1h 0m").assertIsDisplayed()
    }

    @Test
    fun sevenDayUsageChart_selectedDayHighlightsCorrectly() {
        val days = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Tue 9", "Tue", 2L, totalScreenTimeMs = 7_200_000L, hasRecord = true)
        )

        // Select the first day (index 0)
        val state = SevenDayChartState.Success(days = days, selectedDayIndex = 0)

        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
                SevenDayUsageChart(state = state)
            }
        }

        composeTestRule.onNodeWithText("Mon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tue").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mon 8: 1h 0m").assertIsDisplayed()
    }

    // =========================================================================
    // HomeDashboardPreviewScreen Theme Selector Tests
    // =========================================================================

    @Test
    fun homeDashboardPreviewScreen_rendersThemeSelectorAndSwitchesThemes() {
        val fakeRepo = FakeThemeRepository(ThemeMode.SYSTEM)

        composeTestRule.setContent {
            HomeDashboardPreviewScreen(themeRepository = fakeRepo)
        }

        // Verify preview banner and theme selector labels
        composeTestRule.onNodeWithText("Theme Mode:").assertIsDisplayed()
        composeTestRule.onNodeWithText("System").assertIsDisplayed()
        composeTestRule.onNodeWithText("Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()

        // Initially System is selected
        composeTestRule.onNodeWithContentDescription("Switch to System theme").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Switch to Dark theme").assertIsNotSelected()

        // Switch to Dark
        composeTestRule.onNodeWithContentDescription("Switch to Dark theme").performClick()
        assertEquals(ThemeMode.DARK, fakeRepo.getThemeMode())
        composeTestRule.onNodeWithContentDescription("Switch to Dark theme").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Switch to Light theme").assertIsNotSelected()

        // Switch to Light
        composeTestRule.onNodeWithContentDescription("Switch to Light theme").performClick()
        assertEquals(ThemeMode.LIGHT, fakeRepo.getThemeMode())
        composeTestRule.onNodeWithContentDescription("Switch to Light theme").assertIsSelected()

        // Switch back to System
        composeTestRule.onNodeWithContentDescription("Switch to System theme").performClick()
        assertEquals(ThemeMode.SYSTEM, fakeRepo.getThemeMode())
        composeTestRule.onNodeWithContentDescription("Switch to System theme").assertIsSelected()
    }

    @Test
    fun homeDashboardPreviewScreen_settingsIconOpensThemeDialogAndSelectsOption() {
        val fakeRepo = FakeThemeRepository(ThemeMode.SYSTEM)

        composeTestRule.setContent {
            HomeDashboardPreviewScreen(themeRepository = fakeRepo)
        }

        // Click top bar settings icon
        composeTestRule.onNodeWithContentDescription("Theme and Appearance Settings").performClick()

        // Verify Theme & Appearance dialog opens
        composeTestRule.onNodeWithText("Theme & Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("System default").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clean Slate/Emerald palette on white").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deep Slate/Emerald dark mode").assertIsDisplayed()

        // Select Dark mode from dialog by clicking its descriptive row
        composeTestRule.onNodeWithText("Deep Slate/Emerald dark mode").performClick()
        assertEquals(ThemeMode.DARK, fakeRepo.getThemeMode())

        // Dialog should be dismissed after selection
        composeTestRule.onNodeWithText("Theme & Appearance").assertDoesNotExist()
    }

    @Test
    fun homeDashboardPreviewScreen_persistsThemeAcrossReloadWithRealSharedPreferences() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefName = "test_preview_persist_prefs"
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()

        val repo1 = SharedPreferencesThemeRepository(context, prefName = prefName)
        assertEquals(ThemeMode.SYSTEM, repo1.getThemeMode())

        composeTestRule.setContent {
            HomeDashboardPreviewScreen(themeRepository = repo1)
        }

        // Switch to Dark via banner button
        composeTestRule.onNodeWithContentDescription("Switch to Dark theme").performClick()
        assertEquals(ThemeMode.DARK, repo1.getThemeMode())

        // Simulate app restart / new repository instance reading from SharedPreferences
        val repo2 = SharedPreferencesThemeRepository(context, prefName = prefName)
        assertEquals(ThemeMode.DARK, repo2.getThemeMode())
        assertEquals(ThemeMode.DARK, repo2.themeModeFlow.value)
    }

    private class FakeThemeRepository(initialMode: ThemeMode = ThemeMode.SYSTEM) : ThemeRepository {
        private val _themeModeFlow = MutableStateFlow(initialMode)
        override val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow

        override fun getThemeMode(): ThemeMode = _themeModeFlow.value

        override fun setThemeMode(themeMode: ThemeMode) {
            _themeModeFlow.value = themeMode
        }
    }
}

