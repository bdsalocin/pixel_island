package com.bdsalocin.pixelisland.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =============================================================================
// 1. Palette Sombre Nocturne (OLED Slate et Gris Pixel)
// =============================================================================

val PixelDarkBackground = Color(0xFF0B0F19)
val PixelDarkOnBackground = Color(0xFFF1F5F9)

val PixelDarkSurface = Color(0xFF111827)
val PixelDarkOnSurface = Color(0xFFF8FAFC)
val PixelDarkSurfaceVariant = Color(0xFF1F2937)
val PixelDarkOnSurfaceVariant = Color(0xFF94A3B8)

val PixelDarkSurfaceContainer = Color(0xFF161F30)
val PixelDarkSurfaceContainerLow = Color(0xFF0F1522)
val PixelDarkSurfaceContainerLowest = Color(0xFF070A10)
val PixelDarkSurfaceContainerHigh = Color(0xFF1E293B)
val PixelDarkSurfaceContainerHighest = Color(0xFF27354A)

val PixelDarkSurfaceBright = Color(0xFF2D3B4E)
val PixelDarkSurfaceDim = Color(0xFF0E131E)

val PixelDarkPrimary = Color(0xFF34D399)
val PixelDarkOnPrimary = Color(0xFF022C22)
val PixelDarkPrimaryContainer = Color(0xFF064E3B)
val PixelDarkOnPrimaryContainer = Color(0xFFA7F3D0)

val PixelDarkSecondary = Color(0xFF94A3B8)
val PixelDarkOnSecondary = Color(0xFF0F172A)
val PixelDarkSecondaryContainer = Color(0xFF1E293B)
val PixelDarkOnSecondaryContainer = Color(0xFFE2E8F0)

val PixelDarkTertiary = Color(0xFF38BDF8)
val PixelDarkOnTertiary = Color(0xFF082F49)
val PixelDarkTertiaryContainer = Color(0xFF0C4A6E)
val PixelDarkOnTertiaryContainer = Color(0xFFBAE6FD)

val PixelDarkError = Color(0xFFF87171)
val PixelDarkOnError = Color(0xFF450A0A)
val PixelDarkErrorContainer = Color(0xFF7F1D1D)
val PixelDarkOnErrorContainer = Color(0xFFFECACA)

val PixelDarkOutline = Color(0xFF334155)
val PixelDarkOutlineVariant = Color(0xFF1E293B)
val PixelDarkScrim = Color(0xFF000000)

// =============================================================================
// 2. Palette Claire (Secours Diurne)
// =============================================================================

val PixelLightBackground = Color(0xFFF8FAFC)
val PixelLightOnBackground = Color(0xFF0F172A)

val PixelLightSurface = Color(0xFFFFFFFF)
val PixelLightOnSurface = Color(0xFF0F172A)
val PixelLightSurfaceVariant = Color(0xFFF1F5F9)
val PixelLightOnSurfaceVariant = Color(0xFF475569)

val PixelLightSurfaceContainer = Color(0xFFE2E8F0)
val PixelLightSurfaceContainerLow = Color(0xFFEDF2F7)
val PixelLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val PixelLightSurfaceContainerHigh = Color(0xFFCBD5E1)
val PixelLightSurfaceContainerHighest = Color(0xFF94A3B8)

val PixelLightSurfaceBright = Color(0xFFFFFFFF)
val PixelLightSurfaceDim = Color(0xFFE2E8F0)

val PixelLightPrimary = Color(0xFF059669)
val PixelLightOnPrimary = Color(0xFFFFFFFF)
val PixelLightPrimaryContainer = Color(0xFFA7F3D0)
val PixelLightOnPrimaryContainer = Color(0xFF022C22)

val PixelLightSecondary = Color(0xFF475569)
val PixelLightOnSecondary = Color(0xFFFFFFFF)
val PixelLightSecondaryContainer = Color(0xFFE2E8F0)
val PixelLightOnSecondaryContainer = Color(0xFF0F172A)

val PixelLightTertiary = Color(0xFF0284C7)
val PixelLightOnTertiary = Color(0xFFFFFFFF)
val PixelLightTertiaryContainer = Color(0xFFBAE6FD)
val PixelLightOnTertiaryContainer = Color(0xFF082F49)

val PixelLightError = Color(0xFFDC2626)
val PixelLightOnError = Color(0xFFFFFFFF)
val PixelLightErrorContainer = Color(0xFFFEE2E2)
val PixelLightOnErrorContainer = Color(0xFF7F1D1D)

val PixelLightOutline = Color(0xFF94A3B8)
val PixelLightOutlineVariant = Color(0xFFCBD5E1)
val PixelLightScrim = Color(0xFF000000)

// =============================================================================
// 3. Jetons Semantiques d'Etat (Coches de Confirmation et Alertes)
// =============================================================================

val StateConfirmedGreen = Color(0xFF34D399)
val StateConfirmedOnGreen = Color(0xFF022C22)
val StateConfirmedGreenContainer = Color(0xFF064E3B)
val StateConfirmedOnGreenContainer = Color(0xFFA7F3D0)

val StatePendingNeutral = Color(0xFF64748B)
val StatePendingOnNeutral = Color(0xFFF1F5F9)
val StatePendingNeutralContainer = Color(0xFF1E293B)
val StatePendingOnNeutralContainer = Color(0xFF94A3B8)

// =============================================================================
// 4. Modele d'Extension Semantique pour Jetpack Compose
// =============================================================================

@Immutable
data class PixelIslandExtendedColors(
    val stateConfirmed: Color = StateConfirmedGreen,
    val onStateConfirmed: Color = StateConfirmedOnGreen,
    val stateConfirmedContainer: Color = StateConfirmedGreenContainer,
    val onStateConfirmedContainer: Color = StateConfirmedOnGreenContainer,
    val statePending: Color = StatePendingNeutral,
    val onStatePending: Color = StatePendingOnNeutral,
    val statePendingContainer: Color = StatePendingNeutralContainer,
    val onStatePendingContainer: Color = StatePendingOnNeutralContainer
)

val LocalPixelIslandExtendedColors = staticCompositionLocalOf {
    PixelIslandExtendedColors()
}
