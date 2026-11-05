package com.bdsalocin.pixelisland.domain.repository

import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import kotlinx.coroutines.flow.Flow

interface BiometricRepository {
    fun observeUnlockEvents(): Flow<BiometricStatus>
}
