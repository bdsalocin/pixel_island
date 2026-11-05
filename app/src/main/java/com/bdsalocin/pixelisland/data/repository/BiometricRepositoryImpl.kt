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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class BiometricRepositoryImpl(
    private val context: Context
) : BiometricRepository {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun observeUnlockEvents(): Flow<BiometricStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        trySend(BiometricStatus.SCANNING)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        trySend(BiometricStatus.SUCCESS)
                        // Disparaître en douceur après validation
                        scope.launch {
                            delay(1400)
                            trySend(BiometricStatus.IDLE)
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
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
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
}
