package com.bdsalocin.pixelisland.domain.usecase

import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.domain.repository.BiometricRepository
import kotlinx.coroutines.flow.Flow

class ObserveUnlockStateUseCase(
    private val repository: BiometricRepository
) {
    operator fun invoke(): Flow<BiometricStatus> = repository.observeUnlockEvents()
}
