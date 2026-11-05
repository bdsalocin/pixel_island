package com.bdsalocin.pixelisland.domain.model

import android.graphics.Bitmap

data class MediaTrack(
    val title: String,
    val artist: String,
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L
)
