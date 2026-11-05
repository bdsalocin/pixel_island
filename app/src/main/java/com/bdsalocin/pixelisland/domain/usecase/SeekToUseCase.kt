package com.bdsalocin.pixelisland.domain.usecase

import com.bdsalocin.pixelisland.domain.repository.MediaRepository

class SeekToUseCase(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(positionMs: Long) {
        repository.seekTo(positionMs)
    }
}
