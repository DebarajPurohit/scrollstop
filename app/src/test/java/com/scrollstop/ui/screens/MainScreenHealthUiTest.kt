package com.scrollstop.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.data.repository.ThemeRepository
import com.scrollstop.domain.model.CommitmentLockState
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
        val apps = listOf(
            MonitoredAppUsage(packageName = "com.example.app1", appName = "App 1", todayUsageMs = 1000L, dailyLimitMs = 1800000L),
            MonitoredAppUsage(packageName = "com.example.app2", appName = "App 2", todayUsageMs = 1000L, dailyLimitMs = 1800000L),
            MonitoredAppUsage(packageName = "com.example.app3", appName = "App 3", todayUsageMs = 1000L, dailyLimitMs = 1800000L)
        )
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        selectedAppCount = 3,
                        monitoredApps = apps
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("PROTECTION ACTIVE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Enforcing daily limits for 3 selected apps.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mainScreen_isStep3AccessibilityActive_evaluatesEveryHealthState() {
        // Accessibility is complete ONLY when healthState confirms the service is active (PROTECTED)
        assertTrue(
            MainUiState(healthState = ProtectionHealthState.PROTECTED).isStep3AccessibilityActive
        )
        // USAGE_ACCESS_REQUIRED must never count as Accessibility completion
        org.junit.Assert.assertFalse(
            MainUiState(healthState = ProtectionHealthState.USAGE_ACCESS_REQUIRED).isStep3AccessibilityActive
        )
        // ACCESSIBILITY_REQUIRED, ACCESSIBILITY_INTERRUPTED and DEGRADED must remain incomplete
        org.junit.Assert.assertFalse(
            MainUiState(healthState = ProtectionHealthState.ACCESSIBILITY_REQUIRED).isStep3AccessibilityActive
        )
        org.junit.Assert.assertFalse(
            MainUiState(healthState = ProtectionHealthState.ACCESSIBILITY_INTERRUPTED).isStep3AccessibilityActive
        )
        org.junit.Assert.assertFalse(
            MainUiState(healthState = ProtectionHealthState.DEGRADED).isStep3AccessibilityActive
        )
    }

    @Test
    fun mainScreen_selectedAppsWithoutLimits_doesNotShowProtected() {
        var configureLimitsClicked = false
        val appsWithoutLimits = listOf(
            MonitoredAppUsage(packageName = "com.example.app1", appName = "App 1", todayUsageMs = 1000L, dailyLimitMs = null),
            MonitoredAppUsage(packageName = "com.example.app2", appName = "App 2", todayUsageMs = 2000L, dailyLimitMs = 0L)
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        selectedAppCount = 2,
                        monitoredApps = appsWithoutLimits
                    ),
                    onNavigateToLimits = { configureLimitsClicked = true }
                )
            }
        }

        // Must NOT show PROTECTION ACTIVE
        composeTestRule.onNode(hasText("PROTECTION ACTIVE")).assertDoesNotExist()
        // Must show SETUP REQUIRED
        composeTestRule.onNodeWithText("SETUP REQUIRED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Configure daily limits for your selected apps to enable protection.").performScrollTo().assertIsDisplayed()
        // Must show Configure Limits CTA and trigger navigation
        composeTestRule.onNodeWithContentDescription("Configure Limits Setup Button").performScrollTo().performClick()
        assertTrue(configureLimitsClicked)
    }

    @Test
    fun mainScreen_monitoredAppCount_reflectsEnforcedAppsOnly() {
        val mixedApps = listOf(
            MonitoredAppUsage(packageName = "com.example.app1", appName = "App 1", todayUsageMs = 1000L, dailyLimitMs = 1800000L),
            MonitoredAppUsage(packageName = "com.example.app2", appName = "App 2", todayUsageMs = 2000L, dailyLimitMs = null),
            MonitoredAppUsage(packageName = "com.example.app3", appName = "App 3", todayUsageMs = 500L, dailyLimitMs = 0L)
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        selectedAppCount = 3,
                        monitoredApps = mixedApps
                    )
                )
            }
        }

        // Selected app count is 3, but enforced count is 1. Banner must reflect enforced app count (1)
        composeTestRule.onNodeWithText("PROTECTION ACTIVE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Enforcing daily limits for 1 selected apps.").performScrollTo().assertIsDisplayed()
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

        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Grant Usage Access Recovery Button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Grant Usage Access Recovery Button").performScrollTo().performClick()
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

        composeTestRule.onNodeWithText("PROTECTION INTERRUPTED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Re-enable Protection Recovery Button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Re-enable Protection Recovery Button").performScrollTo().performClick()
        assertTrue(clicked)
    }

    @Test
    fun mainScreen_rendersTodayScreenTimeAndChartAndMonitoredApps() {
        var limitsClicked = false
        val records = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 3_600_000L, hasRecord = true),
            DailyUsageRecord("Today", "Tue", 2L, totalScreenTimeMs = 13_320_000L, hasRecord = true, isCurrentDay = true)
        )
        val topApps = listOf(
            TopAppUsageItem(
                packageName = "com.instagram.android",
                appName = "Instagram",
                todayUsageMs = 2_700_000L, // 45m
                isMonitored = true
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
                        topApps = topApps
                    ),
                    onNavigateToLimits = { limitsClicked = true }
                )
            }
        }

        // Verify Hero Metric card
        composeTestRule.onNodeWithText("Total phone usage today").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Total phone usage today: 3h 42m").performScrollTo().assertIsDisplayed()

        // Verify 7-Day chart renders
        composeTestRule.onNodeWithText("7-Day Screen Time").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Mon").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Today: 3h 42m").performScrollTo().assertIsDisplayed()

        // Verify Top Apps Today list
        composeTestRule.onNodeWithText("Top Apps Today").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Instagram").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("45m").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Monitored").performScrollTo().assertIsDisplayed()

        // Verify view all apps button
        composeTestRule.onNodeWithText("View All Apps").performScrollTo().performClick()
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
    fun mainScreen_topAppsToday_unmonitoredApp_clickingAddInvokesCallback() {
        var addedPackage: String? = null
        val topApps = listOf(
            TopAppUsageItem(
                packageName = "com.facebook.katana",
                appName = "Facebook",
                todayUsageMs = 120_000L,
                isMonitored = false
            )
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        topApps = topApps
                    ),
                    onAddApp = { addedPackage = it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add Facebook to Monitored Apps").performScrollTo().performClick()
        assertEquals("com.facebook.katana", addedPackage)
    }

    @Test
    fun mainScreen_topAppsToday_emptyState_rendersEmptyMessage() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        topApps = emptyList()
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("No application usage recorded today").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Apps will appear here once used today.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mainScreen_topAppsToday_rendersTopFiveAppsAndMonitoredBadge() {
        val topApps = listOf(
            TopAppUsageItem(packageName = "com.app.one", appName = "App One", todayUsageMs = 3_600_000L, isMonitored = true),
            TopAppUsageItem(packageName = "com.app.two", appName = "App Two", todayUsageMs = 1_800_000L, isMonitored = false),
            TopAppUsageItem(packageName = "com.app.three", appName = "App Three", todayUsageMs = 900_000L, isMonitored = true)
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        topApps = topApps
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("Top Apps Today").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("App One").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1h 0m").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("App Two").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("30m").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("App Three").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("15m").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mainScreen_accessibilityRequired_rendersInactiveWarningAndEnableButton() {
        var enableAccessibilityClicked = false
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.ACCESSIBILITY_REQUIRED,
                        selectedAppCount = 1
                    ),
                    onNavigateToAccessibilityConsent = { enableAccessibilityClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("ACCESSIBILITY REQUIRED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Accessibility required — app blocking is inactive.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Enable Accessibility Button").performScrollTo().performClick()
        assertTrue(enableAccessibilityClicked)
    }

    @Test
    fun mainScreen_protectionStatusCard_zeroSelectedApps_showsSetupRequired() {
        var configureLimitsClicked = false
        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        selectedAppCount = 0
                    ),
                    onNavigateToLimits = { configureLimitsClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("SETUP REQUIRED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose distracting apps to control and configure daily limits.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Configure Limits Setup Button").performScrollTo().performClick()
        assertTrue(configureLimitsClicked)
    }

    @Test
    fun mainScreen_commitmentLock_withStagedRelaxations_rendersPendingChanges() {
        val commitmentState = CommitmentLockState(
            isLocked = true,
            pendingUnlock = true,
            pendingRemovedPackages = setOf("com.insta.app"),
            pendingIncreasedLimits = mapOf("com.yt.app" to 3_600_000L)
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        selectedAppCount = 2,
                        commitmentState = commitmentState
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("Pending Changes at Midnight Reset (12:00 AM):").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("• Unlock Commitment Lock").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("• Removal from protection: com.insta.app").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("• Limit increase: com.yt.app → 1h 0m daily").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel Unlock Request").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mainScreen_heroMetric_rendersWithoutComparisonBadges() {
        val records = listOf(
            DailyUsageRecord("Mon 8", "Mon", 1L, totalScreenTimeMs = 7_200_000L, hasRecord = true), // 2h
            DailyUsageRecord("Today", "Tue", 2L, totalScreenTimeMs = 3_600_000L, hasRecord = true, isCurrentDay = true) // 1h
        )

        composeTestRule.setContent {
            StopDoomScrollTheme {
                MainScreen(
                    uiState = MainUiState(
                        healthState = ProtectionHealthState.PROTECTED,
                        hasUsagePermission = true,
                        isUsageLoading = false,
                        todayScreenTimeMs = 3_600_000L,
                        sevenDayChartState = SevenDayChartState.Success(days = records)
                    )
                )
            }
        }

        composeTestRule.onNodeWithText("Total phone usage today").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Total phone usage today: 1h 0m").performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(hasText("30m under daily avg")).assertDoesNotExist()
        composeTestRule.onNode(hasText("Tracking active")).assertDoesNotExist()
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
