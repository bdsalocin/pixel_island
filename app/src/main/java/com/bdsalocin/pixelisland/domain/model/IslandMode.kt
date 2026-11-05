package com.bdsalocin.pixelisland.domain.model

sealed interface IslandMode {
    data object Idle : IslandMode
    data class FaceUnlock(val isUnlocked: Boolean) : IslandMode
    data class MusicPlaying(
        val track: MediaTrack,
        val isExpanded: Boolean = false
    ) : IslandMode
    data class NotificationAlert(
        val appName: String,
        val message: String
    ) : IslandMode
}
