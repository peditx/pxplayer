// File: data/PlayerState.kt
package com.example.pxplayer.data

/**
 * Represents the complete state of the player UI.
 */
data class PlayerState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val isPlaying: Boolean = false,
    val trackInfo: TrackInfo = TrackInfo(),
    val currentPosition: Long = 0L,
    val eqSettings: EqSettings = EqSettings()
)

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED
}

/**
 * Represents the equalizer settings.
 */
data class EqSettings(
    val enabled: Boolean = false,
    val bands: List<BandLevel> = emptyList()
)

data class BandLevel(
    val band: Short,
    val level: Short,
    val centerFreq: Int, // in milliHertz
    val minLevel: Short, // in millibels
    val maxLevel: Short  // in millibels
)
