package com.bdsalocin.pixelisland.domain.usecase

import com.bdsalocin.pixelisland.domain.repository.MediaRepository

class SkipPreviousUseCase(
    private val repository: MediaRepository
) {
    suspend operator fun invoke() = repository.skipToPrevious()
}
