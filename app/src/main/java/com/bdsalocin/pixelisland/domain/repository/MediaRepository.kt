package com.bdsalocin.pixelisland.domain.repository

import com.bdsalocin.pixelisland.domain.model.MediaTrack
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeCurrentTrack(): Flow<MediaTrack?>
    suspend fun playPause()
    suspend fun skipToNext()
    suspend fun skipToPrevious()
    suspend fun openMusicApp()
}
