package com.bdsalocin.pixelisland.domain.usecase

import com.bdsalocin.pixelisland.domain.repository.MediaRepository

class SkipNextUseCase(
    private val repository: MediaRepository
) {
    suspend operator fun invoke() = repository.skipToNext()
}
