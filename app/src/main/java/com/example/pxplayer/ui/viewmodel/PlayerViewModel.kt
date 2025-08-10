package com.example.pxplayer.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pxplayer.data.PlayerState
import com.example.pxplayer.service.BluetoothSinkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerState())
    val uiState = _uiState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    // --- NEW: StateFlow for discovered devices and scanning status ---
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private var service: BluetoothSinkService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as BluetoothSinkService.LocalBinder
            service = serviceBinder.getService()
            isBound = true
            observeServiceState()
            observeDeviceLists()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            service = null
        }
    }

    fun onPermissionsGranted() {
        Intent(getApplication(), BluetoothSinkService::class.java).also { intent ->
            getApplication<Application>().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            service?.playerState?.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    private fun observeDeviceLists() {
        viewModelScope.launch {
            service?.pairedDevicesList?.collect { devices ->
                _pairedDevices.value = devices
            }
        }
        viewModelScope.launch {
            service?.discoveredDevicesList?.collect { devices ->
                _discoveredDevices.value = devices
            }
        }
        viewModelScope.launch {
            service?.isScanning?.collect { scanning ->
                _isScanning.value = scanning
            }
        }
    }
    
    // --- NEW: Methods to control discovery and pairing ---
    fun fetchPairedDevices() = service?.fetchPairedDevices()
    fun startDiscovery() = service?.startDiscovery()
    fun cancelDiscovery() = service?.cancelDiscovery()
    fun pairDevice(device: BluetoothDevice) = service?.createBond(device)
    fun connectToDevice(device: BluetoothDevice) = service?.connect(device)

    // --- UI Event Handlers ---
    fun onPlayPauseClick() = service?.togglePlayPause()
    fun onNextClick() = service?.skipToNext()
    fun onPreviousClick() = service?.skipToPrevious()
    fun onSeek(position: Float) = service?.seekTo(position.toLong())
    fun setEqEnabled(enabled: Boolean) = service?.setEqEnabled(enabled)
    fun setBandLevel(band: Short, level: Short) = service?.setBandLevel(band, level)

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(connection)
            isBound = false
        }
    }
}
