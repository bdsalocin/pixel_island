package com.bdsalocin.pixelisland.presentation.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val THEME_OVERLAY_SETTING_KEY = "theme_customization_overlay_packages"

val DefaultIslandAccentColor = Color(0xFF34D399)

/**
 * Garantit que la couleur d'accentuation reste parfaitement lisible sur le fond noir OLED (#000000)
 * de la capsule et offre un contraste net avec le trou de serrure noir du cadenas Face ID.
 */
fun ensureContrastOnBlack(
    candidate: Color,
    fallback: Color = DefaultIslandAccentColor
): Color {
    if (candidate == Color.Unspecified || candidate.alpha < 0.5f) {
        return fallback
    }
    val opaque = candidate.copy(alpha = 1f)
    return if (opaque.luminance() < 0.14f) {
        lerp(opaque, Color.White, 0.45f)
    } else {
        opaque
    }
}

/**
 * Extrait la couleur d'accentuation Material You (Monet) active dans « Fond d'écran et style »
 * sur Google Pixel (Android 12 à 14+). Utilise un contexte de configuration neuf afin de ne jamais
 * lire un cache de ressources obsolète après un changement de palette à chaud.
 */
fun resolveDeviceAccentColor(context: Context): Color {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return try {
            val baseContext = context.applicationContext ?: context
            val freshConfig = Configuration(baseContext.resources.configuration)
            val freshContext = baseContext.createConfigurationContext(freshConfig)
            val dynamicPrimary = dynamicDarkColorScheme(freshContext).primary
            ensureContrastOnBlack(dynamicPrimary)
        } catch (_: Exception) {
            DefaultIslandAccentColor
        }
    }
    return DefaultIslandAccentColor
}

/**
 * Observe en direct la couleur système choisie par l'utilisateur dans « Fond d'écran et style »
 * et met à jour instantanément le cadenas Face ID ainsi que l'égaliseur musical sans redémarrage.
 */
@Composable
fun rememberDeviceAccentColor(themeRefreshVersion: Int = 0): Color {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    var localRefreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        var burstJob: Job? = null

        fun triggerLiveRefreshBurst() {
            localRefreshTick++
            burstJob?.cancel()
            burstJob = coroutineScope.launch {
                // SystemUI applique la surcouche RRO Monet de manière asynchrone (~100 à 600 ms)
                delay(150)
                localRefreshTick++
                delay(250)
                localRefreshTick++
                delay(450)
                localRefreshTick++
            }
        }

        val themeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                triggerLiveRefreshBurst()
            }
        }

        @Suppress("DEPRECATION")
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(themeReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(themeReceiver, filter)
            }
        } catch (_: Exception) {}

        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                triggerLiveRefreshBurst()
            }
        }

        val registeredObserver = try {
            val uri = Settings.Secure.getUriFor(THEME_OVERLAY_SETTING_KEY)
            if (uri != null) {
                context.contentResolver.registerContentObserver(uri, false, settingsObserver)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }

        onDispose {
            burstJob?.cancel()
            try {
                context.unregisterReceiver(themeReceiver)
            } catch (_: Exception) {}
            if (registeredObserver) {
                try {
                    context.contentResolver.unregisterContentObserver(settingsObserver)
                } catch (_: Exception) {}
            }
        }
    }

    val targetColor = remember(context, configuration, themeRefreshVersion, localRefreshTick) {
        resolveDeviceAccentColor(context)
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "DeviceAccentColorTransition"
    )

    return animatedColor
}
