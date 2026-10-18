package com.scrollstop.navigation

import androidx.activity.compose.BackHandler
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
import com.scrollstop.ui.components.BottomNavDestination
import com.scrollstop.ui.components.ScrollStopBottomNav
import com.scrollstop.ui.components.ThemeSelectionDialog
import com.scrollstop.ui.screens.MainScreen
import com.scrollstop.ui.screens.MainViewModel
import com.scrollstop.ui.screens.accessibility.AccessibilityConsentScreen
import com.scrollstop.ui.screens.accessibility.AccessibilityConsentViewModel
import com.scrollstop.ui.screens.appselection.AppSelectionScreen
import com.scrollstop.ui.screens.appselection.AppSelectionViewModel
import com.scrollstop.ui.screens.blocking.BlockingScreen
import com.scrollstop.ui.screens.blocking.BlockingViewModel
import com.scrollstop.ui.screens.limits.LimitsScreen
import com.scrollstop.ui.screens.limits.LimitsViewModel
import com.scrollstop.ui.screens.usage.UsageScreen
import com.scrollstop.ui.screens.usage.UsageViewModel

/**
 * Screen destinations for the Stop Doom Scroll application.
 */
sealed interface Screen {
    data object Main : Screen
    data object Limits : Screen
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
    var previousScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var showThemeDialog by remember { mutableStateOf(false) }
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
        ServiceLocator.getAppDiscoveryRepository(context)
    }
    val themeRepository = remember(context) {
        ServiceLocator.getThemeRepository(context)
    }

    val mainViewModel = remember(
        protectionHealthRepository,
        selectedAppsRepository,
        commitmentLockRepository,
        usageStatsRepository,
        appDiscoveryRepository,
        dailyLimitRepository
    ) {
        MainViewModel(
            protectionHealthRepository = protectionHealthRepository,
            selectedAppsRepository = selectedAppsRepository,
            commitmentLockRepository = commitmentLockRepository,
            usageStatsRepository = usageStatsRepository,
            appDiscoveryRepository = appDiscoveryRepository,
            dailyLimitRepository = dailyLimitRepository
        )
    }

    val limitsViewModel = remember(
        appDiscoveryRepository,
        selectedAppsRepository,
        dailyLimitRepository,
        usageStatsRepository,
        commitmentLockRepository
    ) {
        LimitsViewModel(
            appDiscoveryRepository = appDiscoveryRepository,
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            usageStatsRepository = usageStatsRepository,
            usageRuleEngine = ServiceLocator.getUsageRuleEngine(),
            commitmentLockRepository = commitmentLockRepository,
            commitmentRuleEngine = ServiceLocator.getCommitmentRuleEngine()
        )
    }

    // Refresh protection health, check midnight transitions, and clean up expired midnight triggers when resuming to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enforcementTriggerRepository.checkAndExpireStaleTriggers()
                commitmentLockRepository.checkAndApplyMidnightTransitions()
                mainViewModel.refreshDashboard(context)
                limitsViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen is Screen.Limits) {
            limitsViewModel.refresh()
        } else if (currentScreen is Screen.Main) {
            mainViewModel.refreshDashboard(context)
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
                onRefresh = {
                    mainViewModel.refreshDashboard(context)
                },
                onNavigateToAppSelection = {
                    previousScreen = Screen.Main
                    currentScreen = Screen.Limits
                },
                onNavigateToUsage = {
                    previousScreen = Screen.Main
                    currentScreen = Screen.UsageTracking
                },
                onNavigateToLimits = {
                    previousScreen = Screen.Main
                    currentScreen = Screen.Limits
                },
                onNavigateToAccessibilityConsent = {
                    previousScreen = Screen.Main
                    currentScreen = Screen.AccessibilityConsent
                },
                onNavigateToSettings = {
                    showThemeDialog = true
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
        is Screen.Limits -> {
            BackHandler {
                currentScreen = Screen.Main
            }

            LimitsScreen(
                viewModel = limitsViewModel,
                onNavigateBack = {
                    currentScreen = Screen.Main
                },
                onRequestUsagePermission = {
                    mainViewModel.openUsageAccessSettings(context)
                },
                bottomBar = {
                    ScrollStopBottomNav(
                        currentDestination = BottomNavDestination.LIMITS,
                        onDestinationSelected = { destination ->
                            when (destination) {
                                BottomNavDestination.HOME -> {
                                    currentScreen = Screen.Main
                                }
                                BottomNavDestination.LIMITS -> {
                                    // Already on Limits destination
                                }
                                BottomNavDestination.HISTORY -> {
                                    currentScreen = Screen.Main
                                }
                                BottomNavDestination.SETTINGS -> {
                                    showThemeDialog = true
                                }
                            }
                        }
                    )
                }
            )
        }
        is Screen.AppSelection -> {
            BackHandler {
                currentScreen = previousScreen
            }

            val viewModel = remember(appDiscoveryRepository, selectedAppsRepository, commitmentLockRepository) {
                AppSelectionViewModel(appDiscoveryRepository, selectedAppsRepository, commitmentLockRepository)
            }
            val uiState by viewModel.uiState.collectAsState()

            AppSelectionScreen(
                uiState = uiState,
                onToggleAppSelection = viewModel::toggleAppSelection,
                onRetry = viewModel::loadApps,
                onNavigateBack = {
                    currentScreen = previousScreen
                }
            )
        }
        is Screen.UsageTracking -> {
            BackHandler {
                currentScreen = Screen.Main
            }

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
                    previousScreen = Screen.UsageTracking
                    currentScreen = Screen.AppSelection
                },
                onNavigateBack = {
                    currentScreen = Screen.Main
                },
                onSetDailyLimit = { pkg, limitMs ->
                    viewModel.setDailyLimit(pkg, limitMs)
                },
                onRemoveApp = { pkg ->
                    viewModel.removeMonitoredApp(pkg)
                }
            )
        }
        is Screen.AccessibilityConsent -> {
            BackHandler {
                currentScreen = Screen.Main
            }

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

    if (showThemeDialog) {
        val currentThemeMode by themeRepository.themeModeFlow.collectAsState()
        ThemeSelectionDialog(
            currentThemeMode = currentThemeMode,
            onThemeSelected = { mode ->
                themeRepository.setThemeMode(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}
