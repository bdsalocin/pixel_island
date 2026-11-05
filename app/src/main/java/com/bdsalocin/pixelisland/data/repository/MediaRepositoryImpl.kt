package com.bdsalocin.pixelisland.data.repository

import android.content.ComponentName
import android.content.Context
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

    override fun observeCurrentTrack(): Flow<MediaTrack?> = callbackFlow {
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateActiveController(controllers?.firstOrNull())
            trySend(extractCurrentTrack(activeController))
        }

        val componentName = ComponentName(context, MusicNotificationListenerService::class.java)
        try {
            sessionManager?.addOnActiveSessionsChangedListener(listener, componentName)
            val initialControllers = sessionManager?.getActiveSessions(componentName)
            updateActiveController(initialControllers?.firstOrNull())
            trySend(extractCurrentTrack(activeController))
        } catch (_: SecurityException) {
            trySend(null)
        }

        awaitClose {
            sessionManager?.removeOnActiveSessionsChangedListener(listener)
        }
    }

    private fun updateActiveController(controller: MediaController?) {
        activeController = controller
    }

    private fun extractCurrentTrack(controller: MediaController?): MediaTrack? {
        if (controller == null) return null
        val metadata = controller.metadata ?: return null
        val playbackState = controller.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Titre inconnu"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Artiste inconnu"
        val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
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
}
