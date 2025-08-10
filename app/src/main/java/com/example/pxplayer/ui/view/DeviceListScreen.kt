package com.example.pxplayer.ui.view

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("MissingPermission")
@Composable
fun DeviceListScreen(
    pairedDevices: List<BluetoothDevice>,
    discoveredDevices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit,
    isConnecting: Boolean,
    isScanning: Boolean,
    onScanClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Bluetooth Devices",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onScanClicked, enabled = !isConnecting) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Scan")
                }
            }
        }

        if (isConnecting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (pairedDevices.isNotEmpty()) {
                    item {
                        Text("Paired Devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(pairedDevices) { device ->
                        DeviceItem(device = device, isPaired = true, onClick = { onDeviceClick(device) })
                        Divider()
                    }
                }

                val newDevices = discoveredDevices.filter { it.bondState != BluetoothDevice.BOND_BONDED }
                if (newDevices.isNotEmpty()) {
                    item {
                        Text("Available Devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    }
                    items(newDevices) { device ->
                        DeviceItem(device = device, isPaired = false, onClick = { onDeviceClick(device) })
                        Divider()
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, isPaired: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: "Unknown Device",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = device.address,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isPaired) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Paired",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
