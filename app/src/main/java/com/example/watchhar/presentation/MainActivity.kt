package com.example.watchhar.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.watchhar.presentation.theme.WatchHARTheme
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "Sensor Test"
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WatchHARTheme {
                var isPermissionGranted by remember { mutableStateOf(false) }

                if (!isPermissionGranted) {
                    PermissionRequestScreen(
                        onPermissionGranted = {
                            Log.d(TAG, "Permissions granted. Switching to MainScreen.")
                            isPermissionGranted = true
                        }
                    )
                } else {
                    MainScreen()
                }
            }
        }
    }
}
