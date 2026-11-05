package com.bdsalocin.pixelisland.data.repository

import android.content.Context
import android.view.Surface
import android.view.WindowManager
import com.bdsalocin.pixelisland.domain.model.CutoutGeometry
import com.bdsalocin.pixelisland.domain.repository.DisplayCutoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DisplayCutoutRepositoryImpl(
    private val context: Context
) : DisplayCutoutRepository {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val _cutoutGeometry = MutableStateFlow(calculateCurrentGeometry(Surface.ROTATION_0))

    override fun observeCutoutGeometry(): Flow<CutoutGeometry> = _cutoutGeometry.asStateFlow()

    override fun refreshCutoutGeometry(rotation: Int) {
        _cutoutGeometry.value = calculateCurrentGeometry(rotation)
    }

    private fun calculateCurrentGeometry(rotation: Int): CutoutGeometry {
        // Coordonnées matérielles de découpe pour Google Pixel 8 Pro
        // En portrait (0°), le capteur est centré en haut.
        // En paysage (90° ou 270°), il se trouve sur la bordure correspondante.
        val defaultDiameterDp = 28
        val density = context.resources.displayMetrics.density
        val diameterPx = (defaultDiameterDp * density).toInt()

        return CutoutGeometry(
            centerX = 0, // Centré via la gravité WindowManager
            centerY = 0,
            diameter = diameterPx,
            rotation = rotation,
            isPhysicalAnchorMaintained = true
        )
    }
}
