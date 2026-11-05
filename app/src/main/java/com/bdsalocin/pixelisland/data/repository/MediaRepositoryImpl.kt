package com.bdsalocin.pixelisland.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

            // Enregistrer sur toutes les sessions audio actives (Spotify, Myspoti, YouTube Music, etc.)
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
        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            val initialControllers = sessionManager?.getActiveSessions(componentName)
            attachCallbacks(initialControllers)
            trySend(resolveBestTrack())
        } catch (_: Exception) {
            trySend(null)
        }

        awaitClose {
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

        // Trouver en priorité le lecteur en cours de lecture
        val playingController = controllers.firstOrNull { controller ->
            val state = controller.playbackState?.state
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
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
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        // Si rien n'est en lecture et qu'il n'y a pas de métadonnées, ne rien renvoyer
        if (!isPlaying && metadata == null) return null

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Lecture audio"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: ""
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L

        return MediaTrack(
            title = title,
            artist = artist,
            albumArt = albumArt,
            isPlaying = isPlaying,
            durationMs = duration,
            positionMs = position
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
