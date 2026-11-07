package com.scrollstop.ui.screens.welcome

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.OnboardingRepository
import com.scrollstop.data.repository.SharedPreferencesOnboardingRepository
import com.scrollstop.navigation.AppNavigation
import com.scrollstop.ui.screens.permission.UsageAccessGateScreen
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WelcomeAndUsageGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var appOpsManager: AppOpsManager
    private lateinit var onboardingRepository: SharedPreferencesOnboardingRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        onboardingRepository = SharedPreferencesOnboardingRepository(context, "test_onboarding_prefs").apply {
            resetForTesting()
        }
        ServiceLocator.resetForTesting(
            onboardingRepo = onboardingRepository
        )
    }

    // --- WELCOME SCREEN TESTS ---

    @Test
    fun welcomeScreen_rendersAppIdentityAndMotivationalCopy_lightTheme() {
        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
                WelcomeScreen(
                    onContinueWithGoogle = {},
                    onContinueAsGuest = {}
                )
            }
        }

        composeTestRule.onNodeWithText("STOP DOOM SCROLL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reclaim your focus.\nBreak the scroll loop.").assertIsDisplayed()
        composeTestRule.onNodeWithText("100% Local-First • Zero Cloud Sync").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue with Google").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue as Guest").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop Doom Scroll works completely offline. Sign-in is optional.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun welcomeScreen_rendersAppIdentityAndMotivationalCopy_darkTheme() {
        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
                WelcomeScreen(
                    onContinueWithGoogle = {},
                    onContinueAsGuest = {}
                )
            }
        }

        composeTestRule.onNodeWithText("STOP DOOM SCROLL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue with Google").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue as Guest").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun welcomeScreen_clickingContinueAsGuest_invokesCallback() {
        var guestClicked = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                WelcomeScreen(
                    onContinueWithGoogle = {},
                    onContinueAsGuest = { guestClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Continue as Guest").performScrollTo().performClick()
        assertTrue("onContinueAsGuest must be invoked on click", guestClicked)
    }

    @Test
    fun welcomeScreen_clickingContinueWithGoogle_invokesCallback() {
        var googleClicked = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                WelcomeScreen(
                    onContinueWithGoogle = { googleClicked = true },
                    onContinueAsGuest = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Continue with Google").performScrollTo().performClick()
        assertTrue("onContinueWithGoogle must be invoked on click", googleClicked)
    }

    // --- USAGE ACCESS GATE SCREEN TESTS ---

    @Test
    fun usageAccessGateScreen_rendersEducationalCardsAndActions_whenPermissionMissing() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageAccessGateScreen(
                    hasPermission = false,
                    onOpenSettings = {},
                    onRecheckPermission = {},
                    onPermissionGranted = {}
                )
            }
        }

        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Real limits require\naccurate measurement.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Why It's Needed").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Powers Your Dashboard").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("100% Local-First Privacy").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Separate from Accessibility").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant Usage Access").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Recheck Permission").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun usageAccessGateScreen_clickingGrantUsageAccess_invokesOpenSettings() {
        var settingsOpened = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageAccessGateScreen(
                    hasPermission = false,
                    onOpenSettings = { settingsOpened = true },
                    onRecheckPermission = {},
                    onPermissionGranted = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Grant Usage Access").performScrollTo().performClick()
        assertTrue("onOpenSettings must be invoked when Grant Usage Access is clicked", settingsOpened)
    }

    @Test
    fun usageAccessGateScreen_clickingRecheckPermission_invokesRecheckCallback() {
        var rechecked = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageAccessGateScreen(
                    hasPermission = false,
                    onOpenSettings = {},
                    onRecheckPermission = { rechecked = true },
                    onPermissionGranted = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Recheck Permission").performScrollTo().performClick()
        assertTrue("onRecheckPermission must be invoked when Recheck Permission is clicked", rechecked)
    }

    @Test
    fun usageAccessGateScreen_showsGuidanceBanner_afterAttemptingGrantWithoutPermission() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageAccessGateScreen(
                    hasPermission = false,
                    onOpenSettings = {},
                    onRecheckPermission = {},
                    onPermissionGranted = {}
                )
            }
        }

        // Initially guidance banner is not shown
        composeTestRule.onNodeWithText("Recheck Permission").performScrollTo().performClick()
        // After failed check, guidance banner appears
        composeTestRule.onNodeWithText("Permission not detected yet. In Android Settings, find Stop Doom Scroll in the list and toggle 'Permit usage access' on.").performScrollTo().assertIsDisplayed()
    }

    // --- REPOSITORY TESTS ---

    @Test
    fun onboardingRepository_defaultsToUncompleted_forNewUser() {
        assertFalse(onboardingRepository.hasCompletedWelcome())
        assertEquals(OnboardingRepository.SESSION_TYPE_GUEST, onboardingRepository.getSessionType())
    }

    @Test
    fun onboardingRepository_persistsWelcomeCompletionAndSessionType() {
        onboardingRepository.setWelcomeCompleted(true, OnboardingRepository.SESSION_TYPE_GOOGLE)
        assertTrue(onboardingRepository.hasCompletedWelcome())
        assertEquals(OnboardingRepository.SESSION_TYPE_GOOGLE, onboardingRepository.getSessionType())

        onboardingRepository.setWelcomeCompleted(true, OnboardingRepository.SESSION_TYPE_GUEST)
        assertTrue(onboardingRepository.hasCompletedWelcome())
        assertEquals(OnboardingRepository.SESSION_TYPE_GUEST, onboardingRepository.getSessionType())
    }

    // --- INTEGRATED NAVIGATION TESTS ---

    @Test
    fun navigationFlow_initialLaunch_routesToWelcome_whenOnboardingNotCompleted() {
        // Disallow usage permission
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )
        onboardingRepository.resetForTesting()

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Welcome screen must be shown on initial launch
        composeTestRule.onNodeWithText("STOP DOOM SCROLL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue as Guest").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun navigationFlow_welcomeCompleted_routesToUsageGate_whenPermissionNotGranted() {
        // Disallow usage permission
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )
        onboardingRepository.setWelcomeCompleted(true, OnboardingRepository.SESSION_TYPE_GUEST)

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Must be on mandatory Usage Access Gate screen!
        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant Usage Access").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun navigationFlow_returningUserWithPermission_routesDirectlyToMain() {
        // Allow usage permission
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )
        onboardingRepository.setWelcomeCompleted(true, OnboardingRepository.SESSION_TYPE_GUEST)

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        // Main dashboard must be displayed!
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set your limit. When your time is up, we stop you.").assertIsDisplayed()
    }

    @Test
    fun navigationFlow_clickingGuestOnWelcome_transitionsToUsageGate_whenPermissionMissing() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )
        onboardingRepository.resetForTesting()

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        composeTestRule.onNodeWithText("Continue as Guest").performScrollTo().performClick()
        // Must immediately transition to Usage Access Gate screen
        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        assertTrue(onboardingRepository.hasCompletedWelcome())
    }

    @Test
    fun navigationFlow_clickingGoogleOnWelcome_transitionsToUsageGate_whenPermissionMissing() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )
        onboardingRepository.resetForTesting()

        composeTestRule.setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }

        composeTestRule.onNodeWithText("Continue with Google").performScrollTo().performClick()
        // Must immediately transition to Usage Access Gate screen
        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        assertTrue(onboardingRepository.hasCompletedWelcome())
        assertEquals(OnboardingRepository.SESSION_TYPE_GOOGLE, onboardingRepository.getSessionType())
    }
}

