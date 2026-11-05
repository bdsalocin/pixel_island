package com.bdsalocin.pixelisland.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.domain.repository.BiometricRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BiometricRepositoryImpl(
    private val context: Context
) : BiometricRepository {

    override fun observeUnlockEvents(): Flow<BiometricStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        trySend(BiometricStatus.SCANNING)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        trySend(BiometricStatus.SUCCESS)
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

        context.registerReceiver(receiver, filter)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}
