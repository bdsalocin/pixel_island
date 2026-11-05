package com.bdsalocin.pixelisland.presentation.island

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bdsalocin.pixelisland.domain.usecase.CalculateCutoutAnchorUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveActiveMediaUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveUnlockStateUseCase
import com.bdsalocin.pixelisland.domain.usecase.OpenMusicAppUseCase
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
    private val openMusicAppUseCase: OpenMusicAppUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState = _uiState.asStateFlow()

    private var pauseDismissJob: Job? = null

    init {
        observeMedia()
        observeBiometrics()
        observeCutout()
    }

    private fun observeMedia() {
        viewModelScope.launch {
            observeActiveMediaUseCase().collect { track ->
                if (track?.isPlaying == true) {
                    pauseDismissJob?.cancel()
                    _uiState.update { it.copy(mediaTrack = track, isPausedGraceActive = false) }
                } else if (track != null) {
                    // Piste en pause : maintenir l'affichage pendant 6 secondes de grâce
                    _uiState.update { it.copy(mediaTrack = track, isPausedGraceActive = true) }
                    pauseDismissJob?.cancel()
                    pauseDismissJob = viewModelScope.launch {
                        delay(6000)
                        _uiState.update { it.copy(isPausedGraceActive = false) }
                    }
                } else {
                    pauseDismissJob?.cancel()
                    _uiState.update { it.copy(mediaTrack = null, isPausedGraceActive = false) }
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
        _uiState.update { it.copy(isExpanded = !it.isExpanded) }
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
}
