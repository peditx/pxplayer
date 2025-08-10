package com.example.pxplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.pxplayer.service.BluetoothSinkService
import com.example.pxplayer.ui.theme.PxplayerTheme
import com.example.pxplayer.ui.view.PlayerScreen
import com.example.pxplayer.ui.viewmodel.PlayerViewModel
import com.example.pxplayer.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                startSinkService()
            } else {
                // Handle permission denial gracefully in a real app
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- FIX: Set fullscreen immersive mode ---
        setFullscreen()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionsLauncher.launch(PermissionUtils.bluetoothPermissionsSAndAbove)
        } else {
            requestPermissionsLauncher.launch(PermissionUtils.bluetoothPermissionsLegacy)
        }

        setContent {
            PxplayerTheme {
                PlayerScreen(viewModel = viewModel)
            }
        }
    }

    private fun startSinkService() {
        viewModel.onPermissionsGranted()
        Intent(this, BluetoothSinkService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun setFullscreen() {
        // This tells the app to draw behind the system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // This controller allows us to hide the system bars
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // Hide the status bar and navigation bar
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // Allow the user to swipe to show the system bars for a moment
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
