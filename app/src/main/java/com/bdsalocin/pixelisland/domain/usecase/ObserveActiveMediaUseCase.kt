package com.bdsalocin.pixelisland.domain.usecase

import com.bdsalocin.pixelisland.domain.model.MediaTrack
import com.bdsalocin.pixelisland.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

class ObserveActiveMediaUseCase(
    private val repository: MediaRepository
) {
    operator fun invoke(): Flow<MediaTrack?> = repository.observeCurrentTrack()
}
