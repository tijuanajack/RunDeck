package com.rundeck.app.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhoneMediaState(
    val accessEnabled: Boolean = false,
    val available: Boolean = false,
    val playing: Boolean = false,
    val source: String = "PHONE",
    val title: String = "NO MEDIA",
    val artist: String = "",
)

class PhoneMediaController(context: Context) {
    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(appContext, RunDeckNotificationListenerService::class.java)
    private val _state = MutableStateFlow(PhoneMediaState(accessEnabled = hasAccess()))
    val state: StateFlow<PhoneMediaState> = _state.asStateFlow()
    private var controller: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        selectController(it.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(playbackState: PlaybackState?) = publish(controller)
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish(controller)
        override fun onSessionDestroyed() {
            controller?.unregisterCallback(this)
            controller = null
            refresh()
        }
    }

    fun start() {
        refresh()
        if (hasAccess()) {
            runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listener) }
        }
    }

    fun refresh() {
        val enabled = hasAccess()
        if (!enabled) {
            controller?.unregisterCallback(callback)
            controller = null
            _state.value = PhoneMediaState(accessEnabled = false)
            return
        }
        runCatching { sessionManager.getActiveSessions(listener) }
            .onSuccess(::selectController)
            .onFailure { _state.value = PhoneMediaState(accessEnabled = true) }
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun playPause() {
        val controls = controller?.transportControls ?: return
        if (_state.value.playing) controls.pause() else controls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun close() {
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun selectController(controllers: List<MediaController>) {
        val selected = controllers
            .filter { it.playbackState != null || it.metadata != null }
            .maxByOrNull { if (it.playbackState?.state == PlaybackState.STATE_PLAYING) 1 else 0 }
        if (selected != controller) {
            controller?.unregisterCallback(callback)
            controller = selected
            selected?.registerCallback(callback)
        }
        publish(selected)
    }

    private fun publish(selected: MediaController?) {
        if (selected == null) {
            _state.value = PhoneMediaState(accessEnabled = true)
            return
        }
        val metadata = selected.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "NO MEDIA"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val playback = selected.playbackState?.state
        _state.value = PhoneMediaState(
            accessEnabled = true,
            available = metadata != null,
            playing = playback == PlaybackState.STATE_PLAYING,
            source = selected.packageName.displayName(),
            title = title.displayText(max = 40),
            artist = artist.displayText(max = 32),
        )
    }

    private fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)

    private fun String.displayName(): String = substringAfterLast('.')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        .displayText(max = 16)

    private fun String.displayText(max: Int): String = asSequence()
        .map { if (it.code in 32..126) it else ' ' }
        .joinToString(separator = "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "UNKNOWN" }
        .take(max)
}
