@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zairvek.zairauto.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.playback.MediaIds
import com.zairvek.zairauto.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(val index: Int, val mediaId: String, val title: String, val subtitle: String?)

data class PlayerUiState(
    val connected: Boolean = false,
    val hasMedia: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isVideo: Boolean = false,
    val isLive: Boolean = false,
    val speed: Float = 1f,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = 0,
    val sleepAtElapsed: Long = 0,
    val sleepEndOfItem: Boolean = false,
    val error: String? = null,
)

/** Conecta la UI con el servicio de reproducción a través de un MediaBrowser de Media3. */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    var browser: MediaBrowser? = null
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private val controllerListener = object : MediaBrowser.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
    }

    init {
        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val b = MediaBrowser.Builder(app, token).setListener(controllerListener).buildAsync().await()
            browser = b
            b.addListener(listener)
            refresh()
            while (isActive) {
                delay(if (b.isPlaying) 250 else 1000)
                _state.update { it.copy(positionMs = b.currentPosition.coerceAtLeast(0)) }
            }
        }
    }

    private fun refresh() {
        val b = browser ?: return
        val item = b.currentMediaItem
        val md = b.mediaMetadata
        val queue = (0 until b.mediaItemCount).map { i ->
            val m = b.getMediaItemAt(i)
            QueueEntry(i, m.mediaId, m.mediaMetadata.title?.toString() ?: "Sin título", m.mediaMetadata.artist?.toString())
        }
        val extras = b.sessionExtras
        _state.value = PlayerUiState(
            connected = true,
            hasMedia = item != null,
            mediaId = item?.mediaId,
            title = (md.title ?: md.displayTitle ?: "").toString(),
            subtitle = (md.artist ?: md.subtitle)?.toString(),
            artworkUri = md.artworkUri,
            isPlaying = b.isPlaying,
            buffering = b.playbackState == Player.STATE_BUFFERING,
            positionMs = b.currentPosition.coerceAtLeast(0),
            durationMs = b.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            shuffle = b.shuffleModeEnabled,
            repeatMode = b.repeatMode,
            isVideo = md.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO || b.videoSize.width > 0,
            isLive = b.isCurrentMediaItemLive || md.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION,
            speed = b.playbackParameters.speed,
            queue = queue,
            currentIndex = b.currentMediaItemIndex,
            sleepAtElapsed = extras.getLong(PlaybackService.EXTRA_SLEEP_AT_ELAPSED),
            sleepEndOfItem = extras.getBoolean(PlaybackService.EXTRA_SLEEP_END_OF_ITEM),
            error = b.playerError?.let { "No se pudo reproducir: ${it.errorCodeName}" },
        )
    }

    // ---- Acciones ----

    fun play(mediaId: String) {
        val b = browser ?: return
        b.setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
        b.prepare()
        b.play()
    }

    /** Búsqueda por voz: el servicio decide qué poner (canción, carpeta, emisora o todo en aleatorio). */
    fun playFromSearch(query: String) {
        val b = browser ?: return
        b.setMediaItem(
            MediaItem.Builder()
                .setMediaId("")
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(query).build())
                .build()
        )
        b.prepare()
        b.play()
    }

    fun playUri(uri: Uri) {
        val b = browser ?: return
        b.setMediaItem(MediaItem.Builder().setMediaId(MediaIds.external(uri.toString())).setUri(uri).build())
        b.prepare()
        b.play()
    }

    /** Reproduce desde el principio y en orden (desactiva el aleatorio). */
    fun playInOrder(mediaId: String) {
        browser?.shuffleModeEnabled = false
        play(mediaId)
    }

    /** La carpeta llega ya barajada desde el servicio, así que también empieza por una pista al azar. */
    fun playShuffled(dirMediaId: String) {
        browser?.shuffleModeEnabled = false
        play(MediaIds.CTX_SHUFFLED + dirMediaId)
    }

    fun togglePlay() {
        val b = browser ?: return
        if (b.playbackState == Player.STATE_IDLE) b.prepare()
        if (b.isPlaying) b.pause() else b.play()
    }

    fun next() = browser?.seekToNext()
    fun previous() = browser?.seekToPrevious()
    fun seekTo(ms: Long) = browser?.seekTo(ms)
    fun seekBack() = browser?.seekBack()
    fun seekForward() = browser?.seekForward()
    fun skipTo(index: Int) = browser?.let { it.seekToDefaultPosition(index); it.play() }
    fun removeFromQueue(index: Int) = browser?.removeMediaItem(index)

    fun toggleShuffle() = browser?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = browser?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = Graph.settings.update { it.copy(speed = speed) }

    fun toggleFavoriteCurrent() = command(PlaybackService.CMD_FAVORITE)

    fun setSleepTimer(minutes: Int) = command(PlaybackService.CMD_SLEEP, Bundle().apply { putInt(PlaybackService.ARG_MINUTES, minutes) })

    fun rescan() = command(PlaybackService.CMD_RESCAN)

    private fun command(action: String, args: Bundle = Bundle.EMPTY) {
        browser?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    fun sleepRemainingMs(s: PlayerUiState): Long =
        if (s.sleepAtElapsed > 0) (s.sleepAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0) else 0

    // ---- Navegación ----

    suspend fun children(parentId: String): List<MediaItem> {
        val b = browser ?: return emptyList()
        return runCatching { b.getChildren(parentId, 0, Int.MAX_VALUE, null).await().value?.toList() }.getOrNull().orEmpty()
    }

    suspend fun search(query: String): List<MediaItem> {
        val b = browser ?: return emptyList()
        return runCatching {
            b.search(query, null).await()
            b.getSearchResult(query, 0, Int.MAX_VALUE, null).await().value?.toList()
        }.getOrNull().orEmpty()
    }

    override fun onCleared() {
        browser?.release()
        super.onCleared()
    }
}
