package com.bdsalocin.pixelisland.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// =============================================================================
// 1. Definition des ColorScheme Sombre et Clair
// =============================================================================

val PixelDarkColorScheme: ColorScheme = darkColorScheme(
    primary = PixelDarkPrimary,
    onPrimary = PixelDarkOnPrimary,
    primaryContainer = PixelDarkPrimaryContainer,
    onPrimaryContainer = PixelDarkOnPrimaryContainer,
    secondary = PixelDarkSecondary,
    onSecondary = PixelDarkOnSecondary,
    secondaryContainer = PixelDarkSecondaryContainer,
    onSecondaryContainer = PixelDarkOnSecondaryContainer,
    tertiary = PixelDarkTertiary,
    onTertiary = PixelDarkOnTertiary,
    tertiaryContainer = PixelDarkTertiaryContainer,
    onTertiaryContainer = PixelDarkOnTertiaryContainer,
    background = PixelDarkBackground,
    onBackground = PixelDarkOnBackground,
    surface = PixelDarkSurface,
    onSurface = PixelDarkOnSurface,
    surfaceVariant = PixelDarkSurfaceVariant,
    onSurfaceVariant = PixelDarkOnSurfaceVariant,
    surfaceContainer = PixelDarkSurfaceContainer,
    surfaceContainerLow = PixelDarkSurfaceContainerLow,
    surfaceContainerLowest = PixelDarkSurfaceContainerLowest,
    surfaceContainerHigh = PixelDarkSurfaceContainerHigh,
    surfaceContainerHighest = PixelDarkSurfaceContainerHighest,
    surfaceBright = PixelDarkSurfaceBright,
    surfaceDim = PixelDarkSurfaceDim,
    error = PixelDarkError,
    onError = PixelDarkOnError,
    errorContainer = PixelDarkErrorContainer,
    onErrorContainer = PixelDarkOnErrorContainer,
    outline = PixelDarkOutline,
    outlineVariant = PixelDarkOutlineVariant,
    scrim = PixelDarkScrim
)

val PixelLightColorScheme: ColorScheme = lightColorScheme(
    primary = PixelLightPrimary,
    onPrimary = PixelLightOnPrimary,
    primaryContainer = PixelLightPrimaryContainer,
    onPrimaryContainer = PixelLightOnPrimaryContainer,
    secondary = PixelLightSecondary,
    onSecondary = PixelLightOnSecondary,
    secondaryContainer = PixelLightSecondaryContainer,
    onSecondaryContainer = PixelLightOnSecondaryContainer,
    tertiary = PixelLightTertiary,
    onTertiary = PixelLightOnTertiary,
    tertiaryContainer = PixelLightTertiaryContainer,
    onTertiaryContainer = PixelLightOnTertiaryContainer,
    background = PixelLightBackground,
    onBackground = PixelLightOnBackground,
    surface = PixelLightSurface,
    onSurface = PixelLightOnSurface,
    surfaceVariant = PixelLightSurfaceVariant,
    onSurfaceVariant = PixelLightOnSurfaceVariant,
    surfaceContainer = PixelLightSurfaceContainer,
    surfaceContainerLow = PixelLightSurfaceContainerLow,
    surfaceContainerLowest = PixelLightSurfaceContainerLowest,
    surfaceContainerHigh = PixelLightSurfaceContainerHigh,
    surfaceContainerHighest = PixelLightSurfaceContainerHighest,
    surfaceBright = PixelLightSurfaceBright,
    surfaceDim = PixelLightSurfaceDim,
    error = PixelLightError,
    onError = PixelLightOnError,
    errorContainer = PixelLightErrorContainer,
    onErrorContainer = PixelLightOnErrorContainer,
    outline = PixelLightOutline,
    outlineVariant = PixelLightOutlineVariant,
    scrim = PixelLightScrim
)

// =============================================================================
// 2. Formes Expressives Material 3 (Arrondis Calibres)
// =============================================================================

val PixelIslandCardShape = RoundedCornerShape(22.dp)
val PixelIslandButtonShape = RoundedCornerShape(24.dp)
val PixelIslandPillShape = RoundedCornerShape(20.dp)
val PixelIslandSquircleShape = RoundedCornerShape(28.dp)
val PixelIslandBadgeShape = RoundedCornerShape(12.dp)

val PixelIslandShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = PixelIslandCardShape,         // 22.dp pour les cartes expressives
    extraLarge = PixelIslandButtonShape   // 24.dp pour les boutons d'action
)

// =============================================================================
// 3. Theme Composable Principal : PixelIslandTheme
// =============================================================================

@Composable
fun PixelIslandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (_: Throwable) {
                if (darkTheme) PixelDarkColorScheme else PixelLightColorScheme
            }
        }
        darkTheme -> PixelDarkColorScheme
        else -> PixelLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    val extendedColors = if (darkTheme) {
        PixelIslandExtendedColors(
            stateConfirmed = StateConfirmedGreen,
            onStateConfirmed = StateConfirmedOnGreen,
            stateConfirmedContainer = StateConfirmedGreenContainer,
            onStateConfirmedContainer = StateConfirmedOnGreenContainer,
            statePending = StatePendingNeutral,
            onStatePending = StatePendingOnNeutral,
            statePendingContainer = StatePendingNeutralContainer,
            onStatePendingContainer = StatePendingOnNeutralContainer
        )
    } else {
        PixelIslandExtendedColors(
            stateConfirmed = StateConfirmedGreen,
            onStateConfirmed = StateConfirmedOnGreen,
            stateConfirmedContainer = StateConfirmedGreenContainer,
            onStateConfirmedContainer = StateConfirmedOnGreenContainer,
            statePending = StatePendingNeutral,
            onStatePending = Color(0xFFFFFFFF),
            statePendingContainer = Color(0xFFE2E8F0),
            onStatePendingContainer = Color(0xFF475569)
        )
    }

    CompositionLocalProvider(LocalPixelIslandExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = PixelIslandShapes,
            typography = Typography(),
            content = content
        )
    }
}

// =============================================================================
// 4. Accesseur Semantique Etendu pour MaterialTheme
// =============================================================================

val MaterialTheme.extendedColors: PixelIslandExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPixelIslandExtendedColors.current
