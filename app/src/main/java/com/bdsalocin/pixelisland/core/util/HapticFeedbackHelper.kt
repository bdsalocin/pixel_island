package com.bdsalocin.pixelisland.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Gestionnaire de retour haptique haute fidelite type Taptic Engine pour Pixel Island.
 * Optimise pour Google Pixel 8 Pro sous Android 14.
 */
object HapticFeedbackHelper {

    /**
     * Vibration tactile subtile (style Taptic Engine) pour l'appui long sur la pilule compacte.
     */
    fun performTapticFeedback(context: Context, view: View? = null) {
        val viewFeedbackHandled = try {
            view?.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            ) ?: false
        } catch (_: Exception) {
            false
        }

        if (!viewFeedbackHandled) {
            try {
                val vibrator = getVibrator(context)
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(20L)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Vibration tactile de confirmation pour la fermeture de la carte vers l'ilot compact.
     */
    fun performConfirmationFeedback(context: Context, view: View? = null) {
        val viewFeedbackHandled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view?.performHapticFeedback(
                    HapticFeedbackConstants.CONFIRM,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                ) ?: false
            } else {
                view?.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                ) ?: false
            }
        } catch (_: Exception) {
            false
        }

        if (!viewFeedbackHandled) {
            try {
                val vibrator = getVibrator(context)
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(30L)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
