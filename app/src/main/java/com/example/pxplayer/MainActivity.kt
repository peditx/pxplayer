package com.example.pxplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
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

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    // Launcher for Bluetooth permissions
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                // After getting permissions, request to be discoverable
                requestDiscoverable()
            }
        }

    // Launcher for the discoverable request
    private val requestDiscoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_CANCELED) {
                // User agreed to be discoverable, now start the service
                startSinkService()
            } else {
                // User declined, you might want to show a message
                // For now, we still start the service
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

    override fun onResume() {
        super.onResume()
        setFullscreen()
    }

    private fun requestDiscoverable() {
        // Create an intent to request the user to make the device discoverable for 300 seconds
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        requestDiscoverableLauncher.launch(discoverableIntent)
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
