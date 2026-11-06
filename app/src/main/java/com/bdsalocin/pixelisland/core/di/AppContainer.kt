package com.bdsalocin.pixelisland.core.di

import android.app.KeyguardManager
import android.content.Context
import com.bdsalocin.pixelisland.data.repository.BiometricRepositoryImpl
import com.bdsalocin.pixelisland.data.repository.DisplayCutoutRepositoryImpl
import com.bdsalocin.pixelisland.data.repository.MediaRepositoryImpl
import com.bdsalocin.pixelisland.domain.repository.BiometricRepository
import com.bdsalocin.pixelisland.domain.repository.DisplayCutoutRepository
import com.bdsalocin.pixelisland.domain.repository.MediaRepository
import com.bdsalocin.pixelisland.domain.usecase.CalculateCutoutAnchorUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveActiveMediaUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveUnlockStateUseCase
import com.bdsalocin.pixelisland.domain.usecase.TogglePlaybackUseCase
import com.bdsalocin.pixelisland.presentation.island.IslandViewModel

class AppContainer(private val context: Context) {

    // Repositories
    val mediaRepository: MediaRepository by lazy {
        MediaRepositoryImpl(context)
    }

    val biometricRepository: BiometricRepository by lazy {
        BiometricRepositoryImpl(context)
    }

    val displayCutoutRepository: DisplayCutoutRepository by lazy {
        DisplayCutoutRepositoryImpl(context)
    }

    val keyguardManager: KeyguardManager? by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    // UseCases
    val observeActiveMediaUseCase by lazy {
        ObserveActiveMediaUseCase(mediaRepository)
    }

    val observeUnlockStateUseCase by lazy {
        ObserveUnlockStateUseCase(biometricRepository)
    }

    val calculateCutoutAnchorUseCase by lazy {
        CalculateCutoutAnchorUseCase(displayCutoutRepository)
    }

    val togglePlaybackUseCase by lazy {
        TogglePlaybackUseCase(mediaRepository)
    }

    val skipNextUseCase by lazy {
        com.bdsalocin.pixelisland.domain.usecase.SkipNextUseCase(mediaRepository)
    }

    val skipPreviousUseCase by lazy {
        com.bdsalocin.pixelisland.domain.usecase.SkipPreviousUseCase(mediaRepository)
    }

    val openMusicAppUseCase by lazy {
        com.bdsalocin.pixelisland.domain.usecase.OpenMusicAppUseCase(mediaRepository)
    }

    val seekToUseCase by lazy {
        com.bdsalocin.pixelisland.domain.usecase.SeekToUseCase(mediaRepository)
    }

    // ViewModel partagé
    val islandViewModel by lazy {
        IslandViewModel(
            observeActiveMediaUseCase = observeActiveMediaUseCase,
            observeUnlockStateUseCase = observeUnlockStateUseCase,
            calculateCutoutAnchorUseCase = calculateCutoutAnchorUseCase,
            togglePlaybackUseCase = togglePlaybackUseCase,
            skipNextUseCase = skipNextUseCase,
            skipPreviousUseCase = skipPreviousUseCase,
            openMusicAppUseCase = openMusicAppUseCase,
            seekToUseCase = seekToUseCase,
            keyguardManager = keyguardManager
        )
    }
}
