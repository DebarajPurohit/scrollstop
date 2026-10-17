package com.scrollstop.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.data.repository.ThemeRepository
import com.scrollstop.domain.model.DailyUsageRecord
import com.scrollstop.domain.model.MonitoredAppUsage
import com.scrollstop.domain.model.ProtectionHealthState
import com.scrollstop.ui.components.SevenDayChartState
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
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

    @Test
    fun mainScreen_rendersTodayScreenTimeAndChartAndMonitoredApps() {
        var limitsClicked = false
        val records = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Today", "Tue", 2L, totalScreenTimeMs = 13_320_000L, hasRecord = true, isCurrentDay = true)
        )
        val monitoredApps = listOf(
            MonitoredAppUsage(
                packageName = "com.instagram.android",
                appName = "Instagram",
                todayUsageMs = 2_700_000L, // 45m
                dailyLimitMs = 1_800_000L, // 30m
                cumulativeUsageMs = 12_000_000L,
                observationDays = 2
            )
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        selectedAppCount = 1,
                        hasUsagePermission = true,
                        isUsageLoading = false,
                        todayScreenTimeMs = 13_320_000L,
                        sevenDayChartState = SevenDayChartState.Success(days = records),
                        monitoredApps = monitoredApps,
                        totalDailyLimitMs = 1_800_000L
                    ),
                    onNavigateToLimits = { limitsClicked = true }
                )
            }
        }

        // Verify Hero Metric card
        composeTestRule.onNodeWithText("TODAY'S SCREEN TIME").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Today's screen time hero metric: 3h 42m").performScrollTo().assertIsDisplayed()

        // Verify 7-Day chart renders
        composeTestRule.onNodeWithText("7-Day Screen Time").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Mon").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Today: 3h 42m").performScrollTo().assertIsDisplayed()

        // Verify Monitored Apps list
        composeTestRule.onNodeWithText("Monitored Applications").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Instagram").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("45m").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("30m daily").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("3h 20m (2d of data)").performScrollTo().assertIsDisplayed()

        // Verify configure limits button
        composeTestRule.onNodeWithText("Configure Limits & Add Apps").performScrollTo().performClick()
        assertTrue(limitsClicked)
    }

    @Test
    fun mainScreen_settingsDialogOpensAndSelectsTheme() {
        val fakeThemeRepo = FakeThemeRepo(ThemeMode.SYSTEM)

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        selectedAppCount = 1
                    ),
                    themeRepository = fakeThemeRepo
                )
            }
        }

        // Click settings icon in TopBar
        composeTestRule.onNodeWithContentDescription("Theme and Appearance Settings").performClick()

        // Verify Theme & Appearance dialog opens
        composeTestRule.onNodeWithText("Theme & Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("System default").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clean Slate/Emerald palette on white").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deep Slate/Emerald dark mode").assertIsDisplayed()

        // Select Dark mode from dialog
        composeTestRule.onNodeWithText("Deep Slate/Emerald dark mode").performClick()
        assertEquals(ThemeMode.DARK, fakeThemeRepo.getThemeMode())
    }

    @Test
    fun mainScreen_refreshButtonTriggersRefreshCallback() {
        var refreshed = false
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED
                    ),
                    onRefresh = { refreshed = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Refresh Dashboard Usage").performClick()
        assertTrue(refreshed)
    }

    @Test
    fun mainScreen_addMonitoredAppsButtonNavigatesToAppSelection() {
        var addAppsClicked = false
        val monitoredApps = listOf(
            MonitoredAppUsage(
                packageName = "com.facebook.katana",
                appName = "Facebook",
                todayUsageMs = 120_000L,
                dailyLimitMs = 120_000L,
                cumulativeUsageMs = 120_000L,
                observationDays = 1
            )
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        monitoredApps = monitoredApps
                    ),
                    onNavigateToAppSelection = { addAppsClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add Monitored Apps Button").performScrollTo().performClick()
        assertTrue(addAppsClicked)
    }

    private class FakeThemeRepo(initialMode: ThemeMode = ThemeMode.SYSTEM) : ThemeRepository {
        private val _flow = MutableStateFlow(initialMode)
        override val themeModeFlow: StateFlow<ThemeMode> = _flow
        override fun getThemeMode(): ThemeMode = _flow.value
        override fun setThemeMode(themeMode: ThemeMode) {
            _flow.value = themeMode
        }
    }
}
