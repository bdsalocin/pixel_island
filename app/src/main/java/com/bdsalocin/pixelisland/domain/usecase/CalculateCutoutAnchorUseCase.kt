package com.bdsalocin.pixelisland.domain.usecase

import com.bdsalocin.pixelisland.domain.model.CutoutGeometry
import com.bdsalocin.pixelisland.domain.repository.DisplayCutoutRepository
import kotlinx.coroutines.flow.Flow

class CalculateCutoutAnchorUseCase(
    private val repository: DisplayCutoutRepository
) {
    operator fun invoke(): Flow<CutoutGeometry> = repository.observeCutoutGeometry()
    
    fun onConfigurationChanged(rotation: Int) {
        repository.refreshCutoutGeometry(rotation)
    }
}
