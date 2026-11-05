package com.bdsalocin.pixelisland.domain.repository

import com.bdsalocin.pixelisland.domain.model.CutoutGeometry
import kotlinx.coroutines.flow.Flow

interface DisplayCutoutRepository {
    fun observeCutoutGeometry(): Flow<CutoutGeometry>
    fun refreshCutoutGeometry(rotation: Int)
}
