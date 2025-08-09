package com.example.pxplayer.util

import android.Manifest
import android.os.Build

object PermissionUtils {
    /**
     * List of Bluetooth permissions required for Android 12 (API 31) and above.
     */
    val bluetoothPermissionsSAndAbove = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    /**
     * List of Bluetooth permissions for older versions.
     */
    val bluetoothPermissionsLegacy = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN
    )
}
