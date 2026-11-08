package com.scrollstop.ui.screens.welcome

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
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
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
                    onGetStarted = {},
                    onContinueAsGuest = {}
                )
            }
        }

        composeTestRule.onNodeWithText("STOP DOOM SCROLL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reclaim your focus.\nBreak the scroll loop.").assertIsDisplayed()
        composeTestRule.onNodeWithText("100% Local-First • Zero Cloud Sync").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Get Started").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue as Guest").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop Doom Scroll works completely offline. No account required.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun welcomeScreen_rendersAppIdentityAndMotivationalCopy_darkTheme() {
        composeTestRule.setContent {
            StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
                WelcomeScreen(
                    onGetStarted = {},
                    onContinueAsGuest = {}
                )
            }
        }

        composeTestRule.onNodeWithText("STOP DOOM SCROLL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Get Started").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue as Guest").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun welcomeScreen_clickingContinueAsGuest_invokesCallback() {
        var guestClicked = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                WelcomeScreen(
                    onGetStarted = {},
                    onContinueAsGuest = { guestClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Continue as Guest").performScrollTo().performClick()
        assertTrue("onContinueAsGuest must be invoked on click", guestClicked)
    }

    @Test
    fun welcomeScreen_clickingGetStarted_invokesCallback() {
        var getStartedClicked = false

        composeTestRule.setContent {
            StopDoomScrollTheme {
                WelcomeScreen(
                    onGetStarted = { getStartedClicked = true },
                    onContinueAsGuest = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Get Started").performScrollTo().performClick()
        assertTrue("onGetStarted must be invoked on click", getStartedClicked)
    }

    // --- USAGE ACCESS GATE SCREEN TESTS ---

    @Test
    fun usageAccessGateScreen_rendersEducationalCardsAndActions_whenPermissionMissing() {
        composeTestRule.setContent {
            StopDoomScrollTheme {
                UsageAccessGateScreen(
                    onOpenSettings = {},
                    onRecheckPermission = { false }
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
                    onOpenSettings = { settingsOpened = true },
                    onRecheckPermission = { false }
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
                    onOpenSettings = {},
                    onRecheckPermission = {
                        rechecked = true
                        false
                    }
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
                    onOpenSettings = {},
                    onRecheckPermission = { false }
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
        onboardingRepository.setWelcomeCompleted(true, OnboardingRepository.SESSION_TYPE_STANDARD)
        assertTrue(onboardingRepository.hasCompletedWelcome())
        assertEquals(OnboardingRepository.SESSION_TYPE_STANDARD, onboardingRepository.getSessionType())

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
    fun navigationFlow_clickingGetStartedOnWelcome_transitionsToUsageGate_whenPermissionMissing() {
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

        composeTestRule.onNodeWithText("Get Started").performScrollTo().performClick()
        // Must immediately transition to Usage Access Gate screen
        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        assertTrue(onboardingRepository.hasCompletedWelcome())
        assertEquals(OnboardingRepository.SESSION_TYPE_STANDARD, onboardingRepository.getSessionType())
    }

    // --- REGRESSION TESTS: DEFECT A (BACK NAVIGATION) & DEFECT B (PERMISSION LIFECYCLE) ---

    @Test
    fun backPress_fromUsageGate_duringFirstTimeOnboarding_returnsToWelcome() {
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

        // Click Get Started on Welcome
        composeTestRule.onNodeWithText("Get Started").performScrollTo().performClick()
        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        assertTrue(onboardingRepository.hasCompletedWelcome())

        // Press back from Usage Gate
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

        // Must return to Welcome and reset welcome completion
        composeTestRule.onNodeWithText("STOP DOOM SCROLL").assertIsDisplayed()
        assertFalse(onboardingRepository.hasCompletedWelcome())
    }

    @Test
    fun backPress_fromUsageGate_afterPermissionRevocation_doesNotRouteToWelcomeOrHome() {
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

        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()

        // For returning user with revoked permission, gate BackHandler must be disabled
        assertFalse(
            "BackHandler must not be enabled when gate did not originate from Welcome",
            composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
        )

        // Calling back press must not route to Welcome or Main
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        assertTrue(onboardingRepository.hasCompletedWelcome())
    }

    @Test
    fun recheckPermission_whenPermissionMissing_displaysGuidanceBannerAndRemainsOnGate() {
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

        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recheck Permission").performScrollTo().performClick()

        // Guidance banner appears and user stays on gate
        composeTestRule.onNodeWithText("Permission not detected yet. In Android Settings, find Stop Doom Scroll in the list and toggle 'Permit usage access' on.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
    }

    @Test
    fun recheckPermission_whenPermissionGranted_navigatesToMainWithoutGuidanceBanner() {
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

        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()

        // Grant permission in system
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        composeTestRule.onNodeWithText("Recheck Permission").performScrollTo().performClick()

        // Must smoothly navigate to Main dashboard
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set your limit. When your time is up, we stop you.").assertIsDisplayed()
    }

    @Test
    fun onResume_withPermissionGranted_automaticallyRoutesToMain() {
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

        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()

        // User granted permission in Android Settings and returns to app
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        // Simulate returning to foreground (ON_RESUME)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        // AppNavigation ON_RESUME observer routes directly to Main
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set your limit. When your time is up, we stop you.").assertIsDisplayed()
    }

    @Test
    fun onResume_withPermissionRevoked_redirectsToGate() {
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

        // On Main dashboard
        composeTestRule.onNodeWithText("Stop Doom Scroll").assertIsDisplayed()

        // Permission revoked while app was backgrounded
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        // App returns to foreground
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        // Must redirect to mandatory Usage Access Gate
        composeTestRule.onNodeWithText("USAGE ACCESS REQUIRED").assertIsDisplayed()
    }
}

