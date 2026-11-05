package com.bdsalocin.pixelisland.presentation.island

import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.domain.model.CutoutGeometry
import com.bdsalocin.pixelisland.domain.model.MediaTrack

data class IslandUiState(
    val mediaTrack: MediaTrack? = null,
    val biometricStatus: BiometricStatus = BiometricStatus.IDLE,
    val cutoutGeometry: CutoutGeometry? = null,
    val isExpanded: Boolean = false
) {
    val isMusicActive: Boolean get() = mediaTrack?.isPlaying == true
    val isFaceUnlockActive: Boolean get() = biometricStatus == BiometricStatus.SCANNING || biometricStatus == BiometricStatus.SUCCESS
}
