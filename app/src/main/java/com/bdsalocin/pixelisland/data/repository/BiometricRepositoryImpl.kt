package com.bdsalocin.pixelisland.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.domain.repository.BiometricRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class BiometricRepositoryImpl(
    private val context: Context
) : BiometricRepository {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var scanTimeoutJob: Job? = null
    private var dismissJob: Job? = null

    override fun observeUnlockEvents(): Flow<BiometricStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        dismissJob?.cancel()
                        scanTimeoutJob?.cancel()
                        trySend(BiometricStatus.SCANNING)
                        scanTimeoutJob = scope.launch {
                            delay(3500)
                            trySend(BiometricStatus.IDLE)
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        scanTimeoutJob?.cancel()
                        dismissJob?.cancel()
                        trySend(BiometricStatus.SUCCESS)
                        // Transition douce : affichage du cadenas ouvert (350ms) puis passage fluide à la musique
                        dismissJob = scope.launch {
                            delay(350)
                            trySend(BiometricStatus.IDLE)
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        scanTimeoutJob?.cancel()
                        dismissJob?.cancel()
                        trySend(BiometricStatus.IDLE)
                    }
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
            scanTimeoutJob?.cancel()
            dismissJob?.cancel()
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
}
