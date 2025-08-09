package com.example.pxplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.pxplayer.service.BluetoothSinkService
import com.example.pxplayer.ui.theme.PxplayerTheme
import com.example.pxplayer.ui.view.PlayerScreen
import com.example.pxplayer.ui.viewmodel.PlayerViewModel
import com.example.pxplayer.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check if all permissions are granted
            if (permissions.values.all { it }) {
                startSinkService()
            } else {
                // Handle permission denial gracefully in a real app
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionsLauncher.launch(PermissionUtils.bluetoothPermissionsSAndAbove)
        } else {
            // For older versions, permissions are granted at install time, but it's good practice to check
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
}
