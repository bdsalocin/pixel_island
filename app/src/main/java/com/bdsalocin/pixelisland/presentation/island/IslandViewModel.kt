package com.bdsalocin.pixelisland.presentation.island

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bdsalocin.pixelisland.domain.usecase.CalculateCutoutAnchorUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveActiveMediaUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveUnlockStateUseCase
import com.bdsalocin.pixelisland.domain.usecase.SkipNextUseCase
import com.bdsalocin.pixelisland.domain.usecase.SkipPreviousUseCase
import com.bdsalocin.pixelisland.domain.usecase.TogglePlaybackUseCase
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
    private val skipPreviousUseCase: SkipPreviousUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeMedia()
        observeBiometrics()
        observeCutout()
    }

    private fun observeMedia() {
        viewModelScope.launch {
            observeActiveMediaUseCase().collect { track ->
                _uiState.update { it.copy(mediaTrack = track) }
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
}
