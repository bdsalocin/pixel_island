package com.bdsalocin.pixelisland.domain.model

data class CutoutGeometry(
    val centerX: Int,
    val centerY: Int,
    val diameter: Int,
    val rotation: Int, // Surface.ROTATION_0, ROTATION_90, etc.
    val isPhysicalAnchorMaintained: Boolean = true
) {
    /**
     * Calcule la gravité de la fenêtre flottante selon l'orientation de l'écran.
     * En mode paysage 90°, le capteur physique est sur le bord gauche de l'écran logique.
     * En mode paysage 270°, le capteur physique est sur le bord droit de l'écran logique.
     */
    fun computeWindowGravity(): Int {
        return when (rotation) {
            0 -> android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            1 -> android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            2 -> android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            3 -> android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            else -> android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        }
    }
}
