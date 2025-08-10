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
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    // --- FIX: Re-apply fullscreen whenever the app is resumed ---
    override fun onResume() {
        super.onResume()
        setFullscreen()
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
