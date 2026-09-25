package com.scrollstop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.scrollstop.data.repository.PackageManagerAppDiscoveryRepository
import com.scrollstop.ui.screens.MainScreen
import com.scrollstop.ui.screens.appselection.AppSelectionScreen
import com.scrollstop.ui.screens.appselection.AppSelectionViewModel

/**
 * Screen destinations for the Stop Doom Scroll application.
 */
sealed interface Screen {
    data object Main : Screen
    data object AppSelection : Screen
}

/**
 * Navigation entry point for the Stop Doom Scroll application.
 */
@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    val context = LocalContext.current.applicationContext

    when (currentScreen) {
        Screen.Main -> {
            MainScreen(
                onNavigateToAppSelection = {
                    currentScreen = Screen.AppSelection
                }
            )
        }
        Screen.AppSelection -> {
            val repository = remember(context) {
                PackageManagerAppDiscoveryRepository(context)
            }
            val viewModel = remember(repository) {
                AppSelectionViewModel(repository)
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
    }
}
