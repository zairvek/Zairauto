@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zairvek.zairauto.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.R
import com.zairvek.zairauto.data.Entry
import com.zairvek.zairauto.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio de reproducción. Expone la biblioteca a Android Auto mediante la API oficial
 * de apps multimedia (MediaLibraryService), así que no depende de trucos que Google rompe
 * con cada actualización de Android Auto.
 */
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var tree: MediaTree

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO
    private val library get() = Graph.library
    private val settings get() = Graph.settings.value

    private val searchCache = mutableMapOf<String, List<MediaItem>>()
    private var sleepJob: Job? = null
    private var sleepAtElapsed = 0L
    private var consecutiveErrors = 0
    private var tagJob: Job? = null
    private var lastMediaId: String? = null

    override fun onCreate() {
        super.onCreate()
        tree = MediaTree(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.addListener(PlayerListener())

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()

        attachEffects(player.audioSessionId)
        updateCustomLayout()
        publishExtras()
        observeState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si no está sonando nada al cerrar la app desde recientes, liberamos todo.
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        persistPosition()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        )
        Graph.effects.release()
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ---------------- Estado observado ----------------

    private fun observeState() {
        scope.launch {
            Graph.settings.state.collect {
                player.skipSilenceEnabled = it.skipSilence
                player.setPlaybackSpeed(it.speed)
            }
        }
        scope.launch {
            Graph.settings.state.map { it.gridFolders to it.showVideos }.distinctUntilChanged().drop(1).collect {
                Graph.scanner.invalidate()
                session.notifyChildrenChanged(MediaIds.ROOT, 4, null)
                session.notifyChildrenChanged(MediaIds.FOLDERS, Int.MAX_VALUE, null)
            }
        }
        scope.launch {
            library.favorites.drop(1).collect {
                session.notifyChildrenChanged(MediaIds.FAVORITES, it.size, null)
                updateCustomLayout()
            }
        }
        scope.launch { library.recents.drop(1).collect { session.notifyChildrenChanged(MediaIds.RECENTS, it.size, null) } }
        scope.launch { library.streams.drop(1).collect { session.notifyChildrenChanged(MediaIds.STREAMS, it.size, null) } }
        scope.launch {
            library.roots.drop(1).collect {
                Graph.scanner.invalidate()
                session.notifyChildrenChanged(MediaIds.FOLDERS, Int.MAX_VALUE, null)
            }
        }
        // Guardado periódico de la posición (por si el sistema mata el proceso al apagar el coche).
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) persistPosition()
            }
        }
    }

    private fun attachEffects(sessionId: Int) {
        Graph.effects.attach(sessionId)
        // Permite que ecualizadores externos (Wavelet, etc.) se enganchen a nuestra sesión.
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        )
    }

    private fun persistPosition() {
        if (player.mediaItemCount == 0) return
        val item = player.currentMediaItem ?: return
        val pos = player.currentPosition
        library.saveQueuePosition(player.currentMediaItemIndex, pos)
        val duration = player.duration
        if (settings.rememberPosition && duration != C.TIME_UNSET && duration >= LONG_MEDIA_MS && !player.isCurrentMediaItemLive) {
            // Al terminar (o casi) olvidamos la posición para que la próxima vez empiece desde el principio.
            library.savePosition(item.mediaId, if (pos > duration - 30_000) 0 else pos)
        }
    }

    // ---------------- Botones personalizados (Android Auto, notificación) ----------------

    private fun updateCustomLayout() {
        val current = player.currentMediaItem
        val fav = current != null && library.isFavorite(MediaIds.canonical(current.mediaId))
        val buttons = buildList {
            add(
                CommandButton.Builder()
                    .setDisplayName(if (fav) "Quitar de favoritos" else "Añadir a favoritos")
                    .setIconResId(if (fav) R.drawable.ic_star else R.drawable.ic_star_border)
                    .setSessionCommand(SessionCommand(CMD_FAVORITE, Bundle.EMPTY))
                    .build()
            )
            add(
                CommandButton.Builder()
                    .setDisplayName("Aleatorio")
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
                    .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                    .build()
            )
            add(
                CommandButton.Builder()
                    .setDisplayName("Repetir")
                    .setIconResId(
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
                            else -> R.drawable.ic_repeat
                        }
                    )
                    .setSessionCommand(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
                    .build()
            )
            add(
                CommandButton.Builder()
                    .setDisplayName("Atrás 10 s")
                    .setIconResId(R.drawable.ic_replay)
                    .setSessionCommand(SessionCommand(CMD_REPLAY, Bundle.EMPTY))
                    .build()
            )
        }
        session.setCustomLayout(buttons)
    }

    private fun publishExtras() {
        session.setSessionExtras(Bundle().apply {
            putLong(EXTRA_SLEEP_AT_ELAPSED, sleepAtElapsed)
            putBoolean(EXTRA_SLEEP_END_OF_ITEM, player.pauseAtEndOfMediaItems)
        })
    }

    private fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepAtElapsed = 0
        player.pauseAtEndOfMediaItems = false
        when {
            minutes == SLEEP_END_OF_ITEM -> player.pauseAtEndOfMediaItems = true
            minutes > 0 -> {
                sleepAtElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L
                sleepJob = scope.launch {
                    delay(minutes * 60_000L - FADE_MS)
                    // Bajamos el volumen poco a poco antes de pausar.
                    val steps = 20
                    repeat(steps) { i ->
                        player.volume = 1f - (i + 1f) / steps
                        delay(FADE_MS / steps)
                    }
                    player.pause()
                    player.volume = 1f
                    sleepAtElapsed = 0
                    publishExtras()
                }
            }
        }
        publishExtras()
    }

    private fun toggleFavoriteOfCurrent() {
        val item = player.currentMediaItem ?: return
        val md = player.mediaMetadata
        library.toggleFavorite(
            Entry(
                mediaId = MediaIds.canonical(item.mediaId),
                title = (md.title ?: item.mediaMetadata.title ?: "Sin título").toString(),
                subtitle = (md.artist ?: item.mediaMetadata.artist)?.toString(),
                artworkUri = (md.artworkUri ?: item.mediaMetadata.artworkUri)?.toString(),
            )
        )
    }

    // ---------------- Etiquetas (título/artista/carátula reales) ----------------

    private fun enrichCurrentAndNext() {
        tagJob?.cancel()
        tagJob = scope.launch {
            listOf(player.currentMediaItemIndex, player.nextMediaItemIndex)
                .filter { it != C.INDEX_UNSET && it < player.mediaItemCount }
                .forEach { index -> enrich(index) }
        }
    }

    private suspend fun enrich(index: Int) {
        val item = player.getMediaItemAt(index)
        if (item.mediaMetadata.extras?.getBoolean(EXTRA_TAGGED) == true) return
        val uri = MediaIds.playableUri(item.mediaId) ?: return
        val tags = withContext(io) { TagReader.read(this@PlaybackService, Uri.parse(uri)) } ?: return
        // La cola puede haber cambiado mientras leíamos.
        if (index >= player.mediaItemCount || player.getMediaItemAt(index).mediaId != item.mediaId) return
        val md = item.mediaMetadata.buildUpon()
            .setExtras(Bundle().apply { putBoolean(EXTRA_TAGGED, true) })
        tags.title?.let { md.setTitle(it).setDisplayTitle(it) }
        val subtitle = listOfNotNull(tags.artist, tags.album).joinToString(" · ").ifEmpty { null }
        if (tags.artist != null) md.setArtist(tags.artist)
        if (tags.album != null) md.setAlbumTitle(tags.album)
        if (subtitle != null) md.setSubtitle(subtitle)
        if (tags.hasArt) md.setArtworkUri(com.zairvek.zairauto.provider.ArtworkProvider.embedded(this, uri))
        // replaceMediaItem con el mismo URI solo actualiza metadatos: no corta la reproducción.
        player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(md.build()).build())
    }

    // ---------------- Listener del reproductor ----------------

    private inner class PlayerListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return
            val sameItem = mediaItem.mediaId == lastMediaId && reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            lastMediaId = mediaItem.mediaId
            if (sameItem) {
                // Solo cambió la información (etiquetas leídas); no es una pista nueva.
                updateCustomLayout()
                return
            }
            consecutiveErrors = 0
            val md = mediaItem.mediaMetadata
            library.addRecent(
                Entry(
                    mediaId = MediaIds.canonical(mediaItem.mediaId),
                    title = (md.title ?: "Sin título").toString(),
                    subtitle = md.artist?.toString(),
                    artworkUri = md.artworkUri?.toString(),
                )
            )
            // Reanudar archivos largos (audiolibros, podcasts) donde se quedaron.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                if (settings.rememberPosition) {
                    val saved = library.savedPosition(mediaItem.mediaId)
                    if (saved > 0) player.seekTo(saved)
                }
            }
            library.saveQueuePosition(player.currentMediaItemIndex, 0)
            updateCustomLayout()
            enrichCurrentAndNext()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
                scope.launch(io) { library.saveQueue(ids) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) persistPosition()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && player.pauseAtEndOfMediaItems) {
                player.pauseAtEndOfMediaItems = false
                publishExtras()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateCustomLayout()

        override fun onRepeatModeChanged(repeatMode: Int) = updateCustomLayout()

        override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEffects(audioSessionId)

        /**
         * Un archivo dañado o no soportado no debe detener la música en mitad del viaje:
         * saltamos al siguiente (con un límite para no entrar en bucle si fallan todos).
         */
        override fun onPlayerError(error: PlaybackException) {
            val skippable = error.errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
            if (skippable && player.hasNextMediaItem() && consecutiveErrors < MAX_SKIPS) {
                consecutiveErrors++
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    // ---------------- Callback de la sesión ----------------

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(CMD_REPLAY, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .add(SessionCommand(CMD_RESCAN, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            // Al abrir la app, mostramos lo último que sonaba (sin empezar a reproducir).
            if (controller.packageName == packageName && player.mediaItemCount == 0) {
                scope.launch {
                    val restored = withContext(io) { tree.restoreLastQueue() } ?: return@launch
                    if (player.mediaItemCount != 0) return@launch
                    player.setMediaItems(restored.mediaItems, restored.startIndex, restored.startPositionMs)
                    player.prepare()
                }
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = LibraryParams.Builder().setExtras(tree.rootExtras()).build()
            val root = if (params?.isRecent == true) {
                MediaItem.Builder().setMediaId(MediaIds.RECENT_ROOT)
                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
                    .build()
            } else tree.root()
            return Futures.immediateFuture(LibraryResult.ofItem(root, extras))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(io) {
            tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(io) {
            LibraryResult.ofItemList(paginate(tree.children(parentId), page, pageSize), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future(io) {
            val results = tree.search(query)
            withContext(Dispatchers.Main) {
                searchCache[query] = results
                session.notifySearchResultChanged(browser, query, results.size, params)
            }
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(io) {
            val results = withContext(Dispatchers.Main) { searchCache[query] } ?: tree.search(query)
            LibraryResult.ofItemList(paginate(results, page, pageSize), params)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future(io) {
            tree.resolveForPlayback(mediaItems, startIndex, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future(io) {
            mediaItems.mapNotNull { tree.resolveOne(it.mediaId) ?: it.takeIf { m -> m.localConfiguration != null } }.toMutableList()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future(io) {
            tree.restoreLastQueue() ?: throw UnsupportedOperationException("Nada que reanudar")
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_FAVORITE -> toggleFavoriteOfCurrent()
                CMD_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                CMD_REPEAT -> player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                CMD_REPLAY -> player.seekBack()
                CMD_SLEEP -> setSleepTimer(args.getInt(ARG_MINUTES, 0))
                CMD_RESCAN -> {
                    Graph.scanner.invalidate()
                    this@PlaybackService.session.notifyChildrenChanged(MediaIds.FOLDERS, Int.MAX_VALUE, null)
                }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun paginate(list: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return list
        val from = page.toLong() * pageSize
        if (from >= list.size) return emptyList()
        return list.subList(from.toInt(), minOf(list.size, (from + pageSize).toInt()))
    }

    companion object {
        const val CMD_FAVORITE = "zair.favorite"
        const val CMD_SHUFFLE = "zair.shuffle"
        const val CMD_REPEAT = "zair.repeat"
        const val CMD_REPLAY = "zair.replay"
        const val CMD_SLEEP = "zair.sleep"
        const val CMD_RESCAN = "zair.rescan"
        const val ARG_MINUTES = "minutes"
        const val SLEEP_END_OF_ITEM = -1

        const val EXTRA_SLEEP_AT_ELAPSED = "sleepAt"
        const val EXTRA_SLEEP_END_OF_ITEM = "sleepEoi"
        private const val EXTRA_TAGGED = "zair.tagged"

        private const val LONG_MEDIA_MS = 15 * 60_000L
        private const val FADE_MS = 20_000L
        private const val MAX_SKIPS = 5
    }
}
