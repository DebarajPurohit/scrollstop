package com.scrollstop.navigation

import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.AppDiscoveryRepository
import com.scrollstop.data.repository.InMemoryDailyLimitRepository
import com.scrollstop.data.repository.InMemoryEnforcementTriggerRepository
import com.scrollstop.data.repository.InMemorySelectedAppsRepository
import com.scrollstop.domain.model.DiscoveredApp
import com.scrollstop.ui.theme.StopDoomScrollTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val selectedAppsRepository = InMemorySelectedAppsRepository()
    private val dailyLimitRepository = InMemoryDailyLimitRepository()
    private val enforcementTriggerRepository = InMemoryEnforcementTriggerRepository()

    private val sampleDiscoveredApps = listOf(
        DiscoveredApp(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            category = ApplicationInfo.CATEGORY_SOCIAL
        ),
        DiscoveredApp(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            category = ApplicationInfo.CATEGORY_VIDEO
        ),
        DiscoveredApp(
            packageName = "com.example.utility",
            appLabel = "Utility Tool",
            category = ApplicationInfo.CATEGORY_UNDEFINED
        )
    )

    private class FakeAppDiscoveryRepository(
        private val apps: List<DiscoveredApp>
    ) : AppDiscoveryRepository {
        override suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>> = Result.success(apps)
    }

    @Before
    fun setUp() {
        ServiceLocator.resetForTesting(
            selectedAppsRepo = selectedAppsRepository,
            dailyLimitRepo = dailyLimitRepository,
            triggerRepo = enforcementTriggerRepository,
            appDiscoveryRepo = FakeAppDiscoveryRepository(sampleDiscoveredApps)
        )
    }

    @After
    fun tearDown() {
        ServiceLocator.resetForTesting()
    }

    @Test
    fun startDestination_isHomeDashboard() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()
        composeTestRule.onNodeWithText("Home").assertIsSelected()
    }

    @Test
    fun bottomNav_navigateToLimits_andBackToHome() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Initially on Home
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()

        // Tap Limits bottom nav item
        composeTestRule.onNodeWithText("Limits").performClick()

        // Verify Limits & Apps screen is displayed and selected
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Limits").assertIsSelected()
        composeTestRule.onNodeWithText("Home").assertIsNotSelected()

        // Tap Home bottom nav item
        composeTestRule.onNodeWithText("Home").performClick()

        // Verify back on Home dashboard
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()
        composeTestRule.onNodeWithText("Home").assertIsSelected()
        composeTestRule.onNodeWithText("Limits").assertIsNotSelected()
    }

    @Test
    fun topBarBackArrow_onLimitsScreen_returnsToHome() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Tap Limits
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()

        // Tap back arrow in top bar
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        // Verify returned to Home dashboard
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()
    }

    @Test
    fun repeatedTapOnLimits_maintainsDestinationWithoutDuplication() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Tap Limits once
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()

        // Tap Limits again (re-selection)
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Limits").assertIsSelected()
    }

    @Test
    fun bottomNav_historyTabFromLimits_navigatesToHome() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Navigate to Limits
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()

        // Tap History bottom nav item
        composeTestRule.onNodeWithText("History").performClick()

        // Navigates to Home where 7-day usage history chart lives
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()
    }

    @Test
    fun bottomNav_settingsFromLimits_opensThemeDialog() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Navigate to Limits
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()

        // Tap Settings bottom nav item
        composeTestRule.onNodeWithText("Settings").performClick()

        // Verify Theme & Appearance dialog is displayed
        composeTestRule.onNodeWithText("Theme & Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("System default").assertIsDisplayed()

        // Dismiss dialog
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.onNode(hasText("Theme & Appearance")).assertDoesNotExist()
    }

    @Test
    fun homeDashboard_addAppsAction_navigatesToLimits() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // On Home, scroll down to Monitored Applications section and click Choose Apps
        composeTestRule.onNodeWithContentDescription("Choose Apps to Control Button")
            .performScrollTo()
            .performClick()

        // Verifies navigating to dedicated Limits & Apps destination
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Limits").assertIsSelected()
    }

    @Test
    fun homeDashboard_configureLimitsAction_navigatesToLimits() {
        // Pre-select an app so "Configure Limits & Add Apps" button is shown
        selectedAppsRepository.setSelectedPackages(setOf("com.instagram.android"))

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Scroll to Configure Limits & Add Apps button and click
        composeTestRule.onNodeWithContentDescription("Configure Limits and Add Apps Button")
            .performScrollTo()
            .performClick()

        // Verifies landing on Limits & Apps screen
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Limits").assertIsSelected()
    }

    @Test
    fun homeDashboard_accessibilityDisclosureAction_navigatesToConsent() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Scroll to and click Accessibility disclosure button
        composeTestRule.onNodeWithContentDescription("Accessibility Permission & Disclosure Button")
            .performScrollTo()
            .performClick()

        // Verifies navigating to Accessibility Consent Screen
        composeTestRule.onNodeWithText("Accessibility Service Prominent Disclosure").assertIsDisplayed()

        // Navigate back to Home
        composeTestRule.onNodeWithContentDescription("Back Button").performClick()
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()
    }

    @Test
    fun blockingDestination_initialBlockedPackage_rendersBlockingScreen() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation(initialBlockedPackage = "com.test.distracting")
            }
        }

        // Directly opens Blocking Screen
        composeTestRule.onNodeWithText("Time's up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go to Dashboard").assertIsDisplayed()
    }

    @Test
    fun limitsScreen_statePreserved_whenNavigatingToHomeAndBack() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Navigate to Limits
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()

        // Type a search query
        composeTestRule.onNodeWithContentDescription("Search apps input")
            .performClick()
            .performTextInput("Insta")

        // Instagram matches, YouTube does not
        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeTestRule.onNode(hasText("YouTube")).assertDoesNotExist()

        // Navigate to Home
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()

        // Navigate back to Limits
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()

        // Verify search input was preserved
        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeTestRule.onNode(hasText("YouTube")).assertDoesNotExist()
    }

    @Test
    fun limitsScreen_openLimitEditorModal_andSaveLimit() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Navigate to Limits
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()

        // Tap "Set limit" on Instagram row
        composeTestRule.onNodeWithContentDescription("Edit limit for Instagram")
            .performClick()

        // Modal is open
        composeTestRule.onNodeWithText("Preset Limits").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Preset 45 min").performClick()

        // Save
        composeTestRule.onNodeWithContentDescription("Save limit")
            .performScrollTo()
            .performClick()

        // Verify limit is saved in repository
        assertEquals(45 * 60 * 1000L, dailyLimitRepository.getLimitForPackage("com.instagram.android"))
    }

    @Test
    fun limitsScreen_toggleAppSelection_updatesRepository() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Navigate to Limits
        composeTestRule.onNodeWithText("Limits").performClick()
        composeTestRule.onNodeWithText("Limits & Apps").assertIsDisplayed()

        // Toggle Instagram selection
        composeTestRule.onNodeWithContentDescription("Toggle monitoring for Instagram")
            .performClick()

        // Verify selected in repository
        assertTrue(selectedAppsRepository.isSelected("com.instagram.android"))
    }
}
