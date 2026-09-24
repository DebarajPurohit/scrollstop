package com.scrollstop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scrollstop.navigation.AppNavigation
import com.scrollstop.ui.theme.StopDoomScrollTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StopDoomScrollTheme {
                AppNavigation()
            }
        }
    }
}
