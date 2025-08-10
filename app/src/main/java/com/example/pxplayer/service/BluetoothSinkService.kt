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
import com.example.pxplayer.data.*
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
    // --- NEW: Add a handle for the PAN (Tethering) profile ---
    private var panProfile: BluetoothProfile? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionTrackerJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()

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
        startForeground(Constants.NOTIFICATION_ID, createNotification("Ready to connect"))

        setBluetoothClassAsCarStereo()
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
            setClassMethod.invoke(bluetoothAdapter, carAudioClass)
        } catch (e: Exception) {
            Log.e("PxPlayerService", "Error setting Bluetooth class", e)
        }
    }

    private fun setA2dpSinkPriority(profile: BluetoothProfile) {
        try {
            val priorityOn = 100 // PRIORITY_ON
            val setPriorityMethod = profile::class.java.getMethod("setPriority", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
            for (device in bluetoothAdapter.bondedDevices) {
                setPriorityMethod.invoke(profile, device, priorityOn)
            }
            Log.d("PxPlayerService", "A2DP Sink profile priority set to ON.")
        } catch (e: Exception) {
            Log.e("PxPlayerService", "Error setting A2DP Sink priority", e)
        }
    }

    // --- NEW CRITICAL METHOD: Forcibly disable the PAN (Tethering) profile ---
    private fun disablePanProfile(profile: BluetoothProfile) {
        try {
            val priorityOff = 0 // PRIORITY_OFF
            val setPriorityMethod = profile::class.java.getMethod("setPriority", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
            for (device in bluetoothAdapter.bondedDevices) {
                setPriorityMethod.invoke(profile, device, priorityOff)
            }
            Log.d("PxPlayerService", "PAN (Tethering) profile priority set to OFF.")
        } catch (e: Exception) {
            Log.e("PxPlayerService", "Error disabling PAN profile", e)
        }
    }

    private fun initializeMediaSession() {
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).setCallback(MediaSessionCallback()).build()
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
        // --- NEW: Also get the PAN profile so we can disable it ---
        bluetoothAdapter.getProfileProxy(applicationContext, profileListener, Constants.PAN_PROFILE)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                Constants.A2DP_SINK_PROFILE -> {
                    a2dpSinkProfile = proxy
                    Log.d("PxPlayerService", "A2DP Sink Profile connected.")
                    setA2dpSinkPriority(proxy)
                    
                    if (proxy.connectedDevices.isNotEmpty()) {
                        val device = proxy.connectedDevices.first()
                        _playerState.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.CONNECTED,
                                trackInfo = it.trackInfo.copy(artist = device.name ?: "Connected Device")
                            )
                        }
                    }
                }
                Constants.AVRCP_CONTROLLER_PROFILE -> avrcpControllerProfile = proxy
                // --- NEW: When PAN profile is ready, disable it ---
                Constants.PAN_PROFILE -> {
                    panProfile = proxy
                    Log.d("PxPlayerService", "PAN Profile connected.")
                    disablePanProfile(proxy)
                }
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                Constants.A2DP_SINK_PROFILE -> a2dpSinkProfile = null
                Constants.AVRCP_CONTROLLER_PROFILE -> avrcpControllerProfile = null
                Constants.PAN_PROFILE -> panProfile = null
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == "android.bluetooth.a2dpsink.profile.action.CONNECTION_STATE_CHANGED") {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
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
                        it.copy(connectionStatus = ConnectionStatus.DISCONNECTED, isPlaying = false, trackInfo = TrackInfo())
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
            .setSessionId(0)
            .build()
        try {
            equalizer = Equalizer(0, audioTrack!!.audioSessionId)
            val bands = (0 until equalizer!!.numberOfBands).map { band ->
                BandLevel(band.toShort(), equalizer!!.getBandLevel(band.toShort()), equalizer!!.getCenterFreq(band.toShort()), equalizer!!.bandLevelRange[0], equalizer!!.bandLevelRange[1])
            }
            _playerState.update { it.copy(eqSettings = EqSettings(equalizer!!.enabled, bands)) }
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
        bluetoothAdapter.closeProfileProxy(Constants.PAN_PROFILE, panProfile)
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
