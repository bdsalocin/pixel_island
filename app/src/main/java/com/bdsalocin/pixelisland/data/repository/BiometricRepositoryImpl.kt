package com.bdsalocin.pixelisland.data.repository

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.domain.repository.BiometricRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var scanPollingJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var dismissJob: Job? = null
    private var currentStatus: BiometricStatus = BiometricStatus.IDLE

    override fun observeUnlockEvents(): Flow<BiometricStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
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

                        // R2: À l'allumage de l'écran verrouillé, cadenas 96x96 dp fermé (ou ouvert si conteneur déjà déverrouillé)
                        if (keyguardManager?.isDeviceLocked == false) {
                            currentStatus = BiometricStatus.SUCCESS
                            trySend(BiometricStatus.SUCCESS)
                        } else {
                            currentStatus = BiometricStatus.SCANNING
                            trySend(BiometricStatus.SCANNING)
                        }

                        // Surveillance de l'authentification biométrique (reconnaissance faciale ou empreinte digitale)
                        scanPollingJob = scope.launch {
                            while (isActive) {
                                val km = keyguardManager
                                if (km == null || !km.isKeyguardLocked) {
                                    // Disparition du Keyguard -> passage vers le bureau
                                    triggerDismissal()
                                    break
                                }

                                if (!km.isDeviceLocked && currentStatus != BiometricStatus.SUCCESS) {
                                    // Authentification réussie: l'anse s'ouvre avec animation élastique
                                    scanTimeoutJob?.cancel()
                                    currentStatus = BiometricStatus.SUCCESS
                                    trySend(BiometricStatus.SUCCESS)
                                    // Reste visible en position ouverte en attendant le balayage vers le haut
                                }
                                delay(50)
                            }
                        }

                        scanTimeoutJob = scope.launch {
                            delay(4000)
                            if (currentStatus == BiometricStatus.SCANNING) {
                                currentStatus = BiometricStatus.IDLE
                                trySend(BiometricStatus.IDLE)
                            }
                        }
                    }

                    Intent.ACTION_USER_PRESENT -> {
                        scanPollingJob?.cancel()
                        scanTimeoutJob?.cancel()
                        triggerDismissal()
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        scanPollingJob?.cancel()
                        scanTimeoutJob?.cancel()
                        dismissJob?.cancel()
                        currentStatus = BiometricStatus.IDLE
                        trySend(BiometricStatus.IDLE)
                    }
                }
            }

            private fun triggerDismissal() {
                if (dismissJob?.isActive == true) return
                val wasSuccess = (currentStatus == BiometricStatus.SUCCESS)
                dismissJob = scope.launch {
                    if (!wasSuccess) {
                        currentStatus = BiometricStatus.SUCCESS
                        trySend(BiometricStatus.SUCCESS)
                        delay(250)
                    } else {
                        delay(60)
                    }
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
            scanPollingJob?.cancel()
            scanTimeoutJob?.cancel()
            dismissJob?.cancel()
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
}
