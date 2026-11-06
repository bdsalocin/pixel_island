package com.bdsalocin.pixelisland.presentation.island

import android.app.KeyguardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.domain.usecase.CalculateCutoutAnchorUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveActiveMediaUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveUnlockStateUseCase
import com.bdsalocin.pixelisland.domain.usecase.OpenMusicAppUseCase
import com.bdsalocin.pixelisland.domain.usecase.SeekToUseCase
import com.bdsalocin.pixelisland.domain.usecase.SkipNextUseCase
import com.bdsalocin.pixelisland.domain.usecase.SkipPreviousUseCase
import com.bdsalocin.pixelisland.domain.usecase.TogglePlaybackUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class IslandViewModel(
    private val observeActiveMediaUseCase: ObserveActiveMediaUseCase,
    private val observeUnlockStateUseCase: ObserveUnlockStateUseCase,
    private val calculateCutoutAnchorUseCase: CalculateCutoutAnchorUseCase,
    private val togglePlaybackUseCase: TogglePlaybackUseCase,
    private val skipNextUseCase: SkipNextUseCase,
    private val skipPreviousUseCase: SkipPreviousUseCase,
    private val openMusicAppUseCase: OpenMusicAppUseCase,
    private val seekToUseCase: SeekToUseCase,
    private val keyguardManager: KeyguardManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState = _uiState.asStateFlow()

    private var lastWasPlaying = false
    private var pauseDismissJob: Job? = null
    private var lastToggleTime = 0L

    init {
        observeMedia()
        observeBiometrics()
        observeCutout()
    }

    private fun observeMedia() {
        viewModelScope.launch {
            observeActiveMediaUseCase().collect { track ->
                if (track?.isPlaying == true) {
                    lastWasPlaying = true
                    pauseDismissJob?.cancel()
                    _uiState.update { 
                        it.copy(mediaTrack = track, isPausedGraceActive = false) 
                    }
                } else if (track != null) {
                    // Piste en pause
                    if (lastWasPlaying) {
                        // Vient tout juste de passer de Lecture à Pause : délai de grâce de 5 secondes si non déployé
                        lastWasPlaying = false
                        _uiState.update { 
                            it.copy(mediaTrack = track, isPausedGraceActive = true) 
                        }
                        pauseDismissJob?.cancel()
                        if (!_uiState.value.isExpanded) {
                            pauseDismissJob = viewModelScope.launch {
                                delay(5000)
                                if (!_uiState.value.isExpanded) {
                                    _uiState.update { 
                                        it.copy(isPausedGraceActive = false) 
                                    }
                                }
                            }
                        }
                    } else {
                        // Déjà en pause : mettre à jour les métadonnées sans relancer le décompte
                        _uiState.update { 
                            it.copy(mediaTrack = track) 
                        }
                    }
                } else {
                    // Aucune musique active (application fermée ou arrêtée) : disparition immédiate
                    lastWasPlaying = false
                    pauseDismissJob?.cancel()
                    _uiState.update { 
                        it.copy(mediaTrack = null, isPausedGraceActive = false, isExpanded = false) 
                    }
                }
            }
        }
    }

    private fun observeBiometrics() {
        viewModelScope.launch {
            observeUnlockStateUseCase().collect { status ->
                val isLocked = keyguardManager?.isKeyguardLocked == true
                if (status != BiometricStatus.IDLE && _uiState.value.biometricStatus == BiometricStatus.IDLE) {
                    if (!isLocked) {
                        // R1: L'affichage du cadenas ne doit s'activer que si l'appareil est formellement verrouillé
                        return@collect
                    }
                }
                _uiState.update { current ->
                    val unlocked = if (status == BiometricStatus.SCANNING) {
                        false
                    } else {
                        (!isLocked && keyguardManager != null) || status == BiometricStatus.SUCCESS || current.isDeviceUnlocked
                    }
                    current.copy(
                        biometricStatus = status,
                        isDeviceUnlocked = unlocked
                    )
                }
            }
        }
    }

    private fun observeCutout() {
        viewModelScope.launch {
            calculateCutoutAnchorUseCase().collect { geometry ->
                _uiState.update { it.copy(cutoutGeometry = geometry) }
            }
        }
    }

    fun toggleExpand() {
        val now = System.currentTimeMillis()
        if (now - lastToggleTime < 250) return
        lastToggleTime = now
        if (_uiState.value.isExpanded) {
            collapseIsland()
        } else {
            expandIsland()
        }
    }

    fun expandIsland() {
        if (!_uiState.value.isExpanded) {
            pauseDismissJob?.cancel()
            _uiState.update { it.copy(isExpanded = true, isPausedGraceActive = false) }
        }
    }

    fun collapseIsland() {
        if (_uiState.value.isExpanded) {
            _uiState.update { it.copy(isExpanded = false) }
            val track = _uiState.value.mediaTrack
            if (track != null && !track.isPlaying) {
                _uiState.update { it.copy(isPausedGraceActive = true) }
                pauseDismissJob?.cancel()
                pauseDismissJob = viewModelScope.launch {
                    delay(5000)
                    if (!_uiState.value.isExpanded) {
                        _uiState.update { it.copy(isPausedGraceActive = false) }
                    }
                }
            }
        }
    }

    fun onPlayPauseClicked() {
        viewModelScope.launch {
            togglePlaybackUseCase()
        }
    }

    fun onSkipNextClicked() {
        viewModelScope.launch {
            skipNextUseCase()
        }
    }

    fun onSkipPreviousClicked() {
        viewModelScope.launch {
            skipPreviousUseCase()
        }
    }

    fun onOpenMusicAppClicked() {
        viewModelScope.launch {
            openMusicAppUseCase()
        }
    }

    fun onSeek(positionMs: Long) {
        viewModelScope.launch {
            seekToUseCase(positionMs)
        }
    }
}
