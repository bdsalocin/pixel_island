package com.bdsalocin.pixelisland.presentation.island

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val seekToUseCase: SeekToUseCase
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
                        // Vient tout juste de passer de Lecture à Pause : délai de grâce de 5 secondes
                        lastWasPlaying = false
                        _uiState.update { 
                            it.copy(mediaTrack = track, isPausedGraceActive = true) 
                        }
                        pauseDismissJob?.cancel()
                        pauseDismissJob = viewModelScope.launch {
                            delay(5000)
                            _uiState.update { 
                                it.copy(isPausedGraceActive = false, isExpanded = false) 
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
                _uiState.update { it.copy(biometricStatus = status) }
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
        _uiState.update { it.copy(isExpanded = !it.isExpanded) }
    }

    fun expandIsland() {
        if (!_uiState.value.isExpanded) {
            _uiState.update { it.copy(isExpanded = true) }
        }
    }

    fun collapseIsland() {
        if (_uiState.value.isExpanded) {
            _uiState.update { it.copy(isExpanded = false) }
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
