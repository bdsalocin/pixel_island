package com.bdsalocin.pixelisland.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.bdsalocin.pixelisland.data.service.MusicNotificationListenerService
import com.bdsalocin.pixelisland.domain.model.MediaTrack
import com.bdsalocin.pixelisland.domain.repository.MediaRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MediaRepositoryImpl(
    private val context: Context
) : MediaRepository {

    private val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var activeController: MediaController? = null
    private val registeredControllers = mutableSetOf<MediaController>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun observeCurrentTrack(): Flow<MediaTrack?> = callbackFlow {
        val controllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                findAndEmitActiveTrack()
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                findAndEmitActiveTrack()
            }

            override fun onSessionDestroyed() {
                findAndEmitActiveTrack()
            }

            private fun findAndEmitActiveTrack() {
                val bestTrack = resolveBestTrack()
                trySend(bestTrack)
            }
        }

        fun attachCallbacks(controllers: List<MediaController>?) {
            if (controllers.isNullOrEmpty() && registeredControllers.isNotEmpty()) {
                // Conserver les écouteurs existants si la liste renvoyée est temporairement vide
                return
            }
            // Détacher les anciens callbacks
            registeredControllers.forEach { controller ->
                try {
                    controller.unregisterCallback(controllerCallback)
                } catch (_: Exception) {}
            }
            registeredControllers.clear()

            // Enregistrer sur toutes les sessions audio actives avec le MainLooper
            controllers?.forEach { controller ->
                try {
                    controller.registerCallback(controllerCallback, mainHandler)
                    registeredControllers.add(controller)
                } catch (_: Exception) {}
            }
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachCallbacks(controllers)
            trySend(resolveBestTrack())
        }

        val componentName = ComponentName(context, MusicNotificationListenerService::class.java)

        // Écouter également les événements de notifications (ex. YouTube ou lecteur vidéo qui démarre)
        MusicNotificationListenerService.onNotificationChangeListener = {
            mainHandler.post {
                val currentControllers = try {
                    sessionManager?.getActiveSessions(componentName)
                } catch (_: Exception) {
                    null
                }
                attachCallbacks(currentControllers)
                trySend(resolveBestTrack())
            }
        }

        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsListener, componentName, mainHandler)
            val initialControllers = sessionManager?.getActiveSessions(componentName)
            attachCallbacks(initialControllers)
            trySend(resolveBestTrack())
        } catch (_: Exception) {
            trySend(null)
        }

        // Synchronisation périodique de sécurité (toutes les secondes) pour ne jamais perdre l'état de lecture
        val pollJob = launch {
            while (true) {
                delay(1000)
                try {
                    val currentControllers = try {
                        sessionManager?.getActiveSessions(componentName)
                    } catch (_: Exception) {
                        null
                    }
                    if (!currentControllers.isNullOrEmpty() && registeredControllers.isEmpty()) {
                        attachCallbacks(currentControllers)
                    }
                    trySend(resolveBestTrack())
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            pollJob.cancel()
            MusicNotificationListenerService.onNotificationChangeListener = null
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            registeredControllers.forEach { controller ->
                try {
                    controller.unregisterCallback(controllerCallback)
                } catch (_: Exception) {}
            }
            registeredControllers.clear()
        }
    }

    private fun resolveBestTrack(): MediaTrack? {
        val componentName = ComponentName(context, MusicNotificationListenerService::class.java)
        val controllers = try {
            sessionManager?.getActiveSessions(componentName) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // Trouver en priorité absolue le lecteur en cours de lecture
        val playingController = controllers.firstOrNull { controller ->
            val state = controller.playbackState?.state
            state == PlaybackState.STATE_PLAYING || 
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_CONNECTING ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING
        } ?: controllers.firstOrNull { controller ->
            val state = controller.playbackState?.state
            audioManager?.isMusicActive == true &&
                state != PlaybackState.STATE_STOPPED &&
                state != PlaybackState.STATE_NONE &&
                (controller.metadata != null || controller.playbackState != null)
        } ?: controllers.firstOrNull { controller ->
            val state = controller.playbackState?.state
            state == PlaybackState.STATE_PAUSED &&
                controller.metadata != null &&
                MusicNotificationListenerService.hasActiveMediaNotification(controller.packageName)
        }

        activeController = playingController
        return extractCurrentTrack(playingController)
    }

    private fun extractCurrentTrack(controller: MediaController?): MediaTrack? {
        if (controller == null) return null
        val playbackState = controller.playbackState ?: return null
        val stateVal = playbackState.state

        // Exclure formellement les sessions arrêtées ou fermées
        if (stateVal == PlaybackState.STATE_STOPPED || stateVal == PlaybackState.STATE_NONE) {
            return null
        }

        val metadata = controller.metadata
        val isHardwareAudioActive = audioManager?.isMusicActive == true

        val isPlaying = stateVal == PlaybackState.STATE_PLAYING ||
                stateVal == PlaybackState.STATE_BUFFERING ||
                stateVal == PlaybackState.STATE_CONNECTING ||
                stateVal == PlaybackState.STATE_FAST_FORWARDING ||
                stateVal == PlaybackState.STATE_REWINDING ||
                (isHardwareAudioActive && stateVal != PlaybackState.STATE_PAUSED)

        // Si l'application est en pause mais que sa notification a été fermée ou retirée, ne rien renvoyer
        if (!isPlaying && !MusicNotificationListenerService.hasActiveMediaNotification(controller.packageName)) {
            return null
        }

        // Si rien n'est en lecture et aucun état de pause valide, ne rien renvoyer
        if (!isPlaying && stateVal != PlaybackState.STATE_PAUSED) {
            return null
        }

        // Si aucun métadonnée et aucun son actif, ignorer
        if (metadata == null && !isHardwareAudioActive) {
            return null
        }

        val appName = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(controller.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            "Lecture multimédia"
        }

        val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        
        val title = if (!rawTitle.isNullOrBlank()) rawTitle else appName

        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: appName

        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val rawPosition = playbackState.position
        val position = if (isPlaying && playbackState.lastPositionUpdateTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
            val speed = if (playbackState.playbackSpeed > 0f) playbackState.playbackSpeed else 1f
            val calculated = rawPosition + (elapsed * speed).toLong()
            if (duration > 0L) calculated.coerceIn(0L, duration) else calculated.coerceAtLeast(0L)
        } else {
            rawPosition
        }

        return MediaTrack(
            title = title,
            artist = artist,
            albumArt = albumArt,
            isPlaying = isPlaying,
            durationMs = duration,
            positionMs = position,
            appName = appName
        )
    }

    private fun getOrResolveActiveController(): MediaController? {
        return activeController ?: run {
            resolveBestTrack()
            activeController
        }
    }

    override suspend fun playPause() {
        try {
            val controller = getOrResolveActiveController()
            val playbackState = controller?.playbackState?.state
            if (playbackState == PlaybackState.STATE_PLAYING) {
                controller?.transportControls?.pause()
            } else {
                controller?.transportControls?.play()
            }
        } catch (_: Exception) {}
    }

    override suspend fun skipToNext() {
        try {
            getOrResolveActiveController()?.transportControls?.skipToNext()
        } catch (_: Exception) {}
    }

    override suspend fun skipToPrevious() {
        try {
            getOrResolveActiveController()?.transportControls?.skipToPrevious()
        } catch (_: Exception) {}
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            getOrResolveActiveController()?.transportControls?.seekTo(positionMs)
        } catch (_: Exception) {}
    }

    override suspend fun openMusicApp() {
        val controller = getOrResolveActiveController()
        controller?.let { c ->
            c.sessionActivity?.let { pendingIntent ->
                try {
                    pendingIntent.send()
                    return
                } catch (_: Exception) {}
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(c.packageName)
            launchIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(it)
                    return
                } catch (_: Exception) {}
            }
        }
        val defaultIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(defaultIntent)
        } catch (_: Exception) {}
    }
}
