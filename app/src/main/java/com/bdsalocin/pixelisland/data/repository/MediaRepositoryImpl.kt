package com.bdsalocin.pixelisland.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.bdsalocin.pixelisland.data.service.MusicNotificationListenerService
import com.bdsalocin.pixelisland.domain.model.MediaTrack
import com.bdsalocin.pixelisland.domain.repository.MediaRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MediaRepositoryImpl(
    private val context: Context
) : MediaRepository {

    private val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var activeController: MediaController? = null
    private val registeredControllers = mutableSetOf<MediaController>()

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
            // Détacher les anciens callbacks
            registeredControllers.forEach { controller ->
                try {
                    controller.unregisterCallback(controllerCallback)
                } catch (_: Exception) {}
            }
            registeredControllers.clear()

            // Enregistrer sur toutes les sessions audio actives (Spotify, Myspoti, YouTube Music, Chrome, etc.)
            controllers?.forEach { controller ->
                try {
                    controller.registerCallback(controllerCallback)
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
            val currentControllers = try {
                sessionManager?.getActiveSessions(componentName)
            } catch (_: Exception) {
                null
            }
            attachCallbacks(currentControllers)
            trySend(resolveBestTrack())
        }

        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            val initialControllers = sessionManager?.getActiveSessions(componentName)
            attachCallbacks(initialControllers)
            trySend(resolveBestTrack())
        } catch (_: Exception) {
            trySend(null)
        }

        awaitClose {
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
            audioManager?.isMusicActive == true && (controller.metadata != null || controller.playbackState != null)
        } ?: controllers.firstOrNull { controller ->
            controller.playbackState?.state == PlaybackState.STATE_PAUSED && controller.metadata != null
        } ?: controllers.firstOrNull { controller ->
            controller.metadata != null
        }

        activeController = playingController
        return extractCurrentTrack(playingController)
    }

    private fun extractCurrentTrack(controller: MediaController?): MediaTrack? {
        if (controller == null) return null
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val stateVal = playbackState?.state
        val isHardwareAudioActive = audioManager?.isMusicActive == true

        val isPlaying = stateVal == PlaybackState.STATE_PLAYING ||
                stateVal == PlaybackState.STATE_BUFFERING ||
                stateVal == PlaybackState.STATE_CONNECTING ||
                stateVal == PlaybackState.STATE_FAST_FORWARDING ||
                stateVal == PlaybackState.STATE_REWINDING ||
                (isHardwareAudioActive && stateVal != PlaybackState.STATE_PAUSED && stateVal != PlaybackState.STATE_STOPPED)

        val appName = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(controller.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            "Lecture multimédia"
        }

        // Si rien n'est en lecture, pas de son matériel actif et aucun titre, ne rien renvoyer
        if (!isPlaying && !isHardwareAudioActive && metadata == null && stateVal != PlaybackState.STATE_PAUSED) {
            return null
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
        val position = playbackState?.position ?: 0L

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

    override suspend fun playPause() {
        val playbackState = activeController?.playbackState?.state
        if (playbackState == PlaybackState.STATE_PLAYING) {
            activeController?.transportControls?.pause()
        } else {
            activeController?.transportControls?.play()
        }
    }

    override suspend fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    override suspend fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    override suspend fun openMusicApp() {
        try {
            val sessionIntent = activeController?.sessionActivity
            if (sessionIntent != null) {
                sessionIntent.send()
            } else {
                val packageName = activeController?.packageName
                if (packageName != null) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
