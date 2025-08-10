package com.example.pxplayer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.pxplayer.R
import com.example.pxplayer.data.BandLevel
import com.example.pxplayer.data.ConnectionStatus
import com.example.pxplayer.data.EqSettings
import com.example.pxplayer.data.PlayerState
import com.example.pxplayer.data.TrackInfo
import com.example.pxplayer.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@SuppressLint("MissingPermission", "SoonBlockedPrivateApi")
class BluetoothSinkService : MediaSessionService() {

    private val binder = LocalBinder()
    private var mediaSession: MediaSession? = null
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var a2dpSinkProfile: BluetoothProfile? = null
    private var avrcpControllerProfile: BluetoothProfile? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionTrackerJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()

    // --- NEW: Flow for paired devices list ---
    private val _pairedDevicesList = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevicesList = _pairedDevicesList.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothSinkService = this@BluetoothSinkService
    }

    override fun onCreate() {
        super.onCreate()
        initialize()
    }
    
    private fun initialize() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter
        
        initializeNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID, createNotification("Waiting for connection..."))

        setBluetoothClassAsCarStereo()
        makeDiscoverable()

        initializeMediaSession()
        connectToBluetoothProfiles()
        registerBroadcastReceivers()
        initializeAudioAndEq()
    }
    
    private fun setBluetoothClassAsCarStereo() {
        try {
            val constructor = BluetoothClass::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            constructor.isAccessible = true
            val carAudioClass = constructor.newInstance(0x200404)
            val setClassMethod = bluetoothAdapter::class.java.getMethod("setBluetoothClass", BluetoothClass::class.java)
            val result = setClassMethod.invoke(bluetoothAdapter, carAudioClass) as Boolean
            if (result) {
                Log.d("PxPlayerService", "Bluetooth class successfully set to Car Stereo.")
            } else {
                Log.e("PxPlayerService", "Failed to set Bluetooth class.")
            }
        } catch (e: Exception) {
            Log.e("PxPlayerService", "Error setting Bluetooth class via reflection", e)
        }
    }
    
    private fun makeDiscoverable() {
        try {
            val scanModeDiscoverable = 23 
            val setScanModeMethod = bluetoothAdapter::class.java.getMethod("setScanMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val result = setScanModeMethod.invoke(bluetoothAdapter, scanModeDiscoverable, 300) as Boolean
            if (result) {
                Log.d("PxPlayerService", "Device set to discoverable.")
            } else {
                Log.e("PxPlayerService", "Failed to set discoverable mode.")
            }
        } catch (e: Exception) {
            Log.e("PxPlayerService", "Error setting discoverable mode via reflection", e)
        }
    }

    // --- NEW: Public methods to be called from ViewModel ---
    fun fetchPairedDevices() {
        _pairedDevicesList.value = bluetoothAdapter.bondedDevices.toList()
    }

    fun connect(device: BluetoothDevice) {
        Log.d("PxPlayerService", "Attempting to connect to ${device.name}")
        _playerState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
        try {
            // Use reflection to call the hidden connect method on the A2DP Sink profile
            val connectMethod = a2dpSinkProfile!!::class.java.getMethod("connect", BluetoothDevice::class.java)
            val result = connectMethod.invoke(a2dpSinkProfile, device) as Boolean
            if (!result) {
                Log.e("PxPlayerService", "Connect method returned false.")
                _playerState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED) }
            }
        } catch (e: Exception) {
            Log.e("PxPlayerService", "Error connecting via reflection", e)
            _playerState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED) }
        }
    }

    private fun initializeMediaSession() {
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                _playerState.update {
                    it.copy(trackInfo = TrackInfo(
                        title = mediaMetadata.title?.toString() ?: "Unknown Title",
                        artist = mediaMetadata.artist?.toString() ?: "Unknown Artist",
                        album = mediaMetadata.albumTitle?.toString() ?: "Unknown Album",
                        albumArtUri = mediaMetadata.artworkUri,
                        duration = player.duration.coerceAtLeast(0)
                    ))
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPositionTracker() else stopPositionTracker()
            }
        })
    }

    private fun connectToBluetoothProfiles() {
        bluetoothAdapter.getProfileProxy(applicationContext, profileListener, Constants.A2DP_SINK_PROFILE)
        bluetoothAdapter.getProfileProxy(applicationContext, profileListener, Constants.AVRCP_CONTROLLER_PROFILE)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                Constants.A2DP_SINK_PROFILE -> {
                    a2dpSinkProfile = proxy
                    Log.d("PxPlayerService", "A2DP Sink Profile connected.")
                    if (proxy.connectedDevices.isNotEmpty()) {
                        val device = proxy.connectedDevices.first()
                        Log.d("PxPlayerService", "Found already connected device: ${device.name}")
                        _playerState.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.CONNECTED,
                                trackInfo = it.trackInfo.copy(artist = device.name ?: "Connected Device")
                            )
                        }
                    }
                }
                Constants.AVRCP_CONTROLLER_PROFILE -> {
                    avrcpControllerProfile = proxy
                    Log.d("PxPlayerService", "AVRCP Controller Profile connected.")
                }
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                Constants.A2DP_SINK_PROFILE -> a2dpSinkProfile = null
                Constants.AVRCP_CONTROLLER_PROFILE -> avrcpControllerProfile = null
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d("PxPlayerService", "Received action: $action")
            
            if (action == "android.bluetooth.a2dpsink.profile.action.CONNECTION_STATE_CHANGED") {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                Log.d("PxPlayerService", "A2DP Sink state changed to: $state for device ${device?.name}")

                when (state) {
                    BluetoothProfile.STATE_CONNECTING -> _playerState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
                    BluetoothProfile.STATE_CONNECTED -> {
                        _playerState.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.CONNECTED,
                                trackInfo = it.trackInfo.copy(artist = device?.name ?: "Connected Device")
                            )
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> _playerState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.DISCONNECTED,
                            isPlaying = false,
                            trackInfo = TrackInfo()
                        )
                    }
                }
            }
        }
    }
    
    private fun registerBroadcastReceivers() {
        val intentFilter = IntentFilter("android.bluetooth.a2dpsink.profile.action.CONNECTION_STATE_CHANGED")
        registerReceiver(broadcastReceiver, intentFilter)
    }

    private fun initializeAudioAndEq() {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(bufferSize)
            .setSessionId(0) // Use system-wide audio session
            .build()
        
        try {
            equalizer = Equalizer(0, audioTrack!!.audioSessionId)
            val bands = (0 until equalizer!!.numberOfBands).map { band ->
                BandLevel(
                    band = band.toShort(),
                    level = equalizer!!.getBandLevel(band.toShort()),
                    centerFreq = equalizer!!.getCenterFreq(band.toShort()),
                    minLevel = equalizer!!.bandLevelRange[0],
                    maxLevel = equalizer!!.bandLevelRange[1]
                )
            }
            _playerState.update { it.copy(eqSettings = EqSettings(enabled = equalizer!!.enabled, bands = bands)) }
        } catch (e: Exception) {
            Log.e("PxPlayerService", "Failed to create Equalizer", e)
        }
    }

    private fun startPositionTracker() {
        stopPositionTracker()
        positionTrackerJob = serviceScope.launch {
            while (isActive) {
                _playerState.update { it.copy(currentPosition = mediaSession?.player?.currentPosition ?: 0) }
                delay(1000)
            }
        }
    }

    private fun stopPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
    }

    // --- Public Control Methods ---
    fun togglePlayPause() = mediaSession?.player?.playWhenReady?.let { mediaSession?.player?.playWhenReady = !it }
    fun skipToNext() = mediaSession?.player?.seekToNext()
    fun skipToPrevious() = mediaSession?.player?.seekToPrevious()
    fun seekTo(position: Long) = mediaSession?.player?.seekTo(position)
    fun setEqEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _playerState.update { it.copy(eqSettings = it.eqSettings.copy(enabled = enabled)) }
    }
    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
        _playerState.update { state ->
            val updatedBands = state.eqSettings.bands.map { if (it.band == band) it.copy(level = level) else it }
            state.copy(eqSettings = state.eqSettings.copy(bands = updatedBands))
        }
    }

    // --- Service Overrides & Notification ---
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onBind(intent: Intent?): IBinder = binder
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(broadcastReceiver)
        mediaSession?.release()
        audioTrack?.release()
        equalizer?.release()
        bluetoothAdapter.closeProfileProxy(Constants.A2DP_SINK_PROFILE, a2dpSinkProfile)
        bluetoothAdapter.closeProfileProxy(Constants.AVRCP_CONTROLLER_PROFILE, avrcpControllerProfile)
    }

    private inner class MediaSessionCallback : MediaSession.Callback {}

    private fun initializeNotificationChannel() {
        val channel = NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID, Constants.NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("pxplayer Active")
            .setSmallIcon(R.drawable.ic_album_placeholder)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
