package com.bdsalocin.pixelisland.data.repository

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.domain.repository.BiometricRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BiometricRepositoryImpl(
    private val context: Context,
    private val keyguardManager: KeyguardManager? = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
) : BiometricRepository {

    private val displayManager: DisplayManager? =
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var wakeJob: Job? = null
    private var scanPollingJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var dismissJob: Job? = null
    private var currentStatus: BiometricStatus = BiometricStatus.IDLE

    override fun observeUnlockEvents(): Flow<BiometricStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        wakeJob?.cancel()
                        dismissJob?.cancel()
                        scanPollingJob?.cancel()
                        scanTimeoutJob?.cancel()

                        val isKeyguardLocked = keyguardManager?.isKeyguardLocked == true
                        if (!isKeyguardLocked) {
                            // R1: Affichage du cadenas exclusivement si l'appareil est formellement verrouillé
                            currentStatus = BiometricStatus.IDLE
                            trySend(BiometricStatus.IDLE)
                            return
                        }

                        // Attendre que la dalle OLED soit physiquement allumée (Display.STATE_ON)
                        // et que le pipeline VSYNC 120 Hz soit actif avant de déployer le carré noir,
                        // afin d'éliminer tout sursaut lié au réveil matériel par le bouton Power.
                        wakeJob = scope.launch {
                            val waitStart = SystemClock.uptimeMillis()
                            while (isActive && (SystemClock.uptimeMillis() - waitStart) < 240L) {
                                val displayState = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.state
                                if (displayState == Display.STATE_ON) {
                                    break
                                }
                                delay(16)
                            }
                            // Laisser quelques trames VSYNC (65ms) stabiliser le rafraîchissement de l'écran
                            delay(65)

                            val kmInitial = keyguardManager
                            if (kmInitial == null || !kmInitial.isKeyguardLocked) {
                                currentStatus = BiometricStatus.IDLE
                                trySend(BiometricStatus.IDLE)
                                return@launch
                            }

                            // R2: Déploiement fluide à 120 FPS du carré noir 96dp avec le cadenas fermé
                            currentStatus = BiometricStatus.SCANNING
                            trySend(BiometricStatus.SCANNING)

                            // Surveillance de l'authentification biométrique (visage Google ou empreinte)
                            scanPollingJob = scope.launch {
                                // Laisser le carré noir finir son ouverture (~240ms) et marquer une courte
                                // pause stable avec le cadenas verrouillé avant d'animer l'ouverture en direct
                                delay(420)
                                while (isActive) {
                                    val km = keyguardManager
                                    if (km == null || !km.isKeyguardLocked) {
                                        triggerDismissal()
                                        break
                                    }

                                    if (!km.isDeviceLocked && currentStatus != BiometricStatus.SUCCESS) {
                                        scanTimeoutJob?.cancel()
                                        currentStatus = BiometricStatus.SUCCESS
                                        trySend(BiometricStatus.SUCCESS)
                                    }
                                    delay(40)
                                }
                            }

                            scanTimeoutJob = scope.launch {
                                delay(4500)
                                if (currentStatus == BiometricStatus.SCANNING) {
                                    currentStatus = BiometricStatus.IDLE
                                    trySend(BiometricStatus.IDLE)
                                }
                            }
                        }
                    }

                    Intent.ACTION_USER_PRESENT -> {
                        wakeJob?.cancel()
                        scanPollingJob?.cancel()
                        scanTimeoutJob?.cancel()
                        triggerDismissal()
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        wakeJob?.cancel()
                        scanPollingJob?.cancel()
                        scanTimeoutJob?.cancel()
                        dismissJob?.cancel()
                        currentStatus = BiometricStatus.IDLE
                        trySend(BiometricStatus.IDLE)
                    }
                }
            }

            private fun triggerDismissal() {
                if (dismissJob?.isActive == true || currentStatus == BiometricStatus.IDLE) return
                dismissJob = scope.launch {
                    currentStatus = BiometricStatus.IDLE
                    trySend(BiometricStatus.IDLE)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        awaitClose {
            wakeJob?.cancel()
            scanPollingJob?.cancel()
            scanTimeoutJob?.cancel()
            dismissJob?.cancel()
            currentStatus = BiometricStatus.IDLE
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
}
