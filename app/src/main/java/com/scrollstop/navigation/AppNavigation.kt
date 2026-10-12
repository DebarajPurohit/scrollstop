package com.scrollstop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.scrollstop.data.di.ServiceLocator
import com.scrollstop.data.repository.PackageManagerAppDiscoveryRepository
import com.scrollstop.ui.screens.MainScreen
import com.scrollstop.ui.screens.MainViewModel
import com.scrollstop.ui.screens.accessibility.AccessibilityConsentScreen
import com.scrollstop.ui.screens.accessibility.AccessibilityConsentViewModel
import com.scrollstop.ui.screens.appselection.AppSelectionScreen
import com.scrollstop.ui.screens.appselection.AppSelectionViewModel
import com.scrollstop.ui.screens.blocking.BlockingScreen
import com.scrollstop.ui.screens.blocking.BlockingViewModel
import com.scrollstop.ui.screens.usage.UsageScreen
import com.scrollstop.ui.screens.usage.UsageViewModel

/**
 * Screen destinations for the Stop Doom Scroll application.
 */
sealed interface Screen {
    data object Main : Screen
    data object AppSelection : Screen
    data object UsageTracking : Screen
    data object AccessibilityConsent : Screen
    data class Blocking(val packageName: String) : Screen
}

/**
 * Navigation entry point for the Stop Doom Scroll application.
 */
@Composable
fun AppNavigation(
    initialBlockedPackage: String? = null,
    onClearBlockedPackage: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedAppsRepository = remember(context) {
        ServiceLocator.getSelectedAppsRepository(context)
    }
    val dailyLimitRepository = remember(context) {
        ServiceLocator.getDailyLimitRepository(context)
    }
    val usageStatsRepository = remember(context) {
        ServiceLocator.getUsageStatsRepository(context)
    }
    val enforcementTriggerRepository = remember(context) {
        ServiceLocator.getEnforcementTriggerRepository(context)
    }
    val healthRepository = remember {
        ServiceLocator.getAccessibilityHealthRepository()
    }
    val protectionHealthRepository = remember(context) {
        ServiceLocator.getProtectionHealthRepository(context)
    }
    val commitmentLockRepository = remember(context) {
        ServiceLocator.getCommitmentLockRepository(context)
    }
    val appDiscoveryRepository = remember(context) {
        PackageManagerAppDiscoveryRepository(context)
    }

    val mainViewModel = remember(protectionHealthRepository, selectedAppsRepository, commitmentLockRepository) {
        MainViewModel(protectionHealthRepository, selectedAppsRepository, commitmentLockRepository)
    }

    // Refresh protection health, check midnight transitions, and clean up expired midnight triggers when resuming to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enforcementTriggerRepository.checkAndExpireStaleTriggers()
                commitmentLockRepository.checkAndApplyMidnightTransitions()
                mainViewModel.refreshHealth(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val latestTrigger by enforcementTriggerRepository.latestTrigger.collectAsState()

    LaunchedEffect(initialBlockedPackage) {
        if (!initialBlockedPackage.isNullOrBlank()) {
            currentScreen = Screen.Blocking(initialBlockedPackage)
        }
    }

    LaunchedEffect(latestTrigger) {
        val triggerPkg = latestTrigger?.packageName
        if (!triggerPkg.isNullOrBlank()) {
            currentScreen = Screen.Blocking(triggerPkg)
        }
    }

    when (val screen = currentScreen) {
        is Screen.Main -> {
            val mainUiState by mainViewModel.uiState.collectAsState()

            MainScreen(
                uiState = mainUiState,
                onNavigateToAppSelection = {
                    currentScreen = Screen.AppSelection
                },
                onNavigateToUsage = {
                    currentScreen = Screen.UsageTracking
                },
                onNavigateToAccessibilityConsent = {
                    currentScreen = Screen.AccessibilityConsent
                },
                onGrantUsageAccess = {
                    mainViewModel.openUsageAccessSettings(context)
                },
                onEnableAccessibility = {
                    mainViewModel.openAccessibilitySettings(context)
                },
                onEnableCommitmentLock = { confirmed ->
                    mainViewModel.enableCommitmentLock(confirmed)
                },
                onRequestUnlock = {
                    mainViewModel.requestUnlock()
                },
                onCancelUnlock = {
                    mainViewModel.cancelUnlockRequest()
                },
                onDebugResetCommitmentLock = {
                    mainViewModel.debugClearCommitmentState()
                }
            )
        }
        is Screen.AppSelection -> {
            val viewModel = remember(appDiscoveryRepository, selectedAppsRepository, commitmentLockRepository) {
                AppSelectionViewModel(appDiscoveryRepository, selectedAppsRepository, commitmentLockRepository)
            }
            val uiState by viewModel.uiState.collectAsState()

            AppSelectionScreen(
                uiState = uiState,
                onToggleAppSelection = viewModel::toggleAppSelection,
                onRetry = viewModel::loadApps,
                onNavigateBack = {
                    currentScreen = Screen.Main
                }
            )
        }
        is Screen.UsageTracking -> {
            val viewModel = remember(usageStatsRepository, appDiscoveryRepository, selectedAppsRepository, dailyLimitRepository, commitmentLockRepository) {
                UsageViewModel(
                    usageStatsRepository = usageStatsRepository,
                    appDiscoveryRepository = appDiscoveryRepository,
                    selectedAppsRepository = selectedAppsRepository,
                    dailyLimitRepository = dailyLimitRepository,
                    commitmentLockRepository = commitmentLockRepository
                )
            }
            val uiState by viewModel.uiState.collectAsState()

            UsageScreen(
                uiState = uiState,
                onRefresh = viewModel::refreshUsage,
                onNavigateToAppSelection = {
                    currentScreen = Screen.AppSelection
                },
                onNavigateBack = {
                    currentScreen = Screen.Main
                },
                onSetDailyLimit = { pkg, limitMs ->
                    viewModel.setDailyLimit(pkg, limitMs)
                }
            )
        }
        is Screen.AccessibilityConsent -> {
            val viewModel = remember(healthRepository, enforcementTriggerRepository) {
                AccessibilityConsentViewModel(healthRepository, enforcementTriggerRepository)
            }
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(context) {
                viewModel.checkServiceHealth(context)
            }

            AccessibilityConsentScreen(
                uiState = uiState,
                onGrantConsent = {
                    viewModel.openAccessibilitySettings(context)
                },
                onNavigateBack = {
                    currentScreen = Screen.Main
                }
            )
        }
        is Screen.Blocking -> {
            val viewModel = remember(screen.packageName, usageStatsRepository, dailyLimitRepository, appDiscoveryRepository) {
                BlockingViewModel(
                    packageName = screen.packageName,
                    usageStatsRepository = usageStatsRepository,
                    dailyLimitRepository = dailyLimitRepository,
                    appDiscoveryRepository = appDiscoveryRepository,
                    usageRuleEngine = ServiceLocator.getUsageRuleEngine()
                )
            }
            val uiState by viewModel.uiState.collectAsState()

            BlockingScreen(
                uiState = uiState,
                onNavigateToDashboard = {
                    enforcementTriggerRepository.clearLatestTrigger()
                    onClearBlockedPackage()
                    currentScreen = Screen.Main
                }
            )
        }
    }
}
