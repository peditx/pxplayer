// File: data/TrackInfo.kt
package com.example.pxplayer.data

import android.net.Uri

/**
 * Represents the metadata of the currently playing track.
 */
data class TrackInfo(
    val title: String = "Waiting for music...",
    val artist: String = "No device connected",
    val album: String = "",
    val albumArtUri: Uri? = null,
    val duration: Long = 0L
)

