@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zairvek.zairauto.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.data.DocNode
import com.zairvek.zairauto.data.DocumentScanner
import com.zairvek.zairauto.data.Entry
import com.zairvek.zairauto.data.Stream
import com.zairvek.zairauto.provider.ArtworkProvider

/**
 * Construye el árbol que ven Android Auto, el panel multimedia del sistema y la propia app.
 * Todas las funciones son bloqueantes: llamarlas fuera del hilo principal.
 */
class MediaTree(private val context: Context) {
    private val library get() = Graph.library
    private val scanner get() = Graph.scanner
    private val settings get() = Graph.settings.value

    // ---------- Navegación ----------

    fun root(): MediaItem = browsable(MediaIds.ROOT, "Zairauto", icon = "library")

    fun rootExtras(): Bundle = Bundle().apply {
        putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED, true)
        putBoolean(EXTRA_SEARCH_SUPPORTED, true)
        putInt(EXTRA_BROWSABLE_HINT, if (settings.gridFolders) STYLE_GRID else STYLE_LIST)
        putInt(EXTRA_PLAYABLE_HINT, STYLE_LIST)
    }

    fun children(parentId: String): List<MediaItem> = when (val ref = MediaIds.parse(parentId)) {
        is MediaIds.Fixed -> when (ref.id) {
            MediaIds.ROOT -> listOf(
                browsable(MediaIds.FOLDERS, "Biblioteca", icon = "folder"),
                browsable(MediaIds.FAVORITES, "Favoritos", icon = "star", style = STYLE_LIST),
                browsable(MediaIds.RECENTS, "Recientes", icon = "history", style = STYLE_LIST),
                browsable(MediaIds.STREAMS, "Radio", icon = "radio", style = STYLE_GRID),
            )
            MediaIds.RECENT_ROOT -> library.recents.value.take(1).mapNotNull { entryItem(it, "") }
            MediaIds.FOLDERS -> folders()
            MediaIds.FAVORITES -> library.favorites.value.mapNotNull { entryItem(it, MediaIds.CTX_FAVORITES) }
            MediaIds.RECENTS -> library.recents.value.mapNotNull { entryItem(it, MediaIds.CTX_RECENTS) }
            MediaIds.STREAMS -> library.streams.value.map { streamItem(it) }
            else -> emptyList()
        }
        is MediaIds.Dir -> dirChildren(ref.uri)
        else -> emptyList()
    }

    private fun folders(): List<MediaItem> {
        val roots = library.accessibleRoots()
        val shuffle = if (roots.isEmpty()) emptyList() else listOf(
            playable(MediaIds.SHUFFLE_ALL, "Mezclar todo", "Toda tu biblioteca en aleatorio", ArtworkProvider.icon(context, "shuffle"))
        )
        // Con una sola carpeta mostramos su contenido directamente: un toque menos en el coche.
        if (roots.size == 1) return shuffle + dirChildren(scanner.rootDocUri(roots[0].treeUri).toString())
        return shuffle + roots.map { browsable(MediaIds.dir(scanner.rootDocUri(it.treeUri).toString()), it.name, icon = "folder") }
    }

    private fun dirChildren(dirUri: String): List<MediaItem> {
        val l = scanner.list(dirUri, settings.showVideos)
        val cover = l.coverUri?.let { ArtworkProvider.file(context, it) }
        return l.dirs.map { browsable(MediaIds.dir(it.uri), it.name, icon = "folder") } +
            l.media.map { fileItem(it, dirUri, cover) }
    }

    fun item(id: String): MediaItem? = when (val ref = MediaIds.parse(id)) {
        is MediaIds.Fixed -> when (ref.id) {
            MediaIds.ROOT -> root()
            MediaIds.SHUFFLE_ALL -> playable(MediaIds.SHUFFLE_ALL, "Mezclar todo", null, ArtworkProvider.icon(context, "shuffle"))
            else -> children(MediaIds.ROOT).firstOrNull { it.mediaId == ref.id }
        }
        is MediaIds.Dir -> scanner.node(ref.uri)?.let { browsable(id, it.name, icon = "folder") }
        is MediaIds.File, is MediaIds.External, is MediaIds.StreamRef -> resolveOne(id)
        null -> null
    }

    fun search(query: String): List<MediaItem> {
        val q = DocumentScanner.normalize(query)
        val streams = library.streams.value.filter { DocumentScanner.normalize(it.name).contains(q) }.map { streamItem(it) }
        val files = scanner.search(query, library.accessibleRoots(), settings.showVideos).map {
            fileItem(it.node, it.parentUri, null, subtitle = it.parentName)
        }
        return streams + files
    }

    // ---------- Resolución para reproducir ----------

    /** Convierte lo que pide un controlador (Android Auto, voz, la app) en una cola reproducible. */
    fun resolveForPlayback(requested: List<MediaItem>, startIndex: Int, startPositionMs: Long): MediaItemsWithStartPosition {
        if (requested.size == 1) {
            val req = requested[0]
            val query = req.requestMetadata.searchQuery
            if (query != null || req.mediaId.isEmpty()) return fromSearch(query.orEmpty())
            expandSingle(req.mediaId)?.let { (items, index) ->
                return withStartPosition(items, index, startPositionMs)
            }
        }
        val items = requested.mapNotNull { resolveOne(it.mediaId) ?: it.takeIf { m -> m.localConfiguration != null } }
        val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        return withStartPosition(items, index, startPositionMs)
    }

    /** Un único elemento pulsado: encolamos su carpeta, la lista de favoritos, o la biblioteca entera. */
    private fun expandSingle(id: String): Pair<List<MediaItem>, Int>? {
        if (id.startsWith(MediaIds.CTX_FAVORITES)) {
            val canonical = MediaIds.canonical(id)
            val items = library.favorites.value.mapNotNull { resolveOne(it.mediaId) }
            val index = items.indexOfFirst { it.mediaId == canonical }
            return if (index >= 0) items to index else null
        }
        if (id.startsWith(MediaIds.CTX_SHUFFLED)) {
            val ref = MediaIds.parse(id) as? MediaIds.Dir ?: return null
            return dirQueue(ref.uri).shuffled().takeIf { it.isNotEmpty() }?.let { it to 0 }
        }
        return when (val ref = MediaIds.parse(id)) {
            is MediaIds.Fixed -> if (ref.id == MediaIds.SHUFFLE_ALL) shuffleAll() to 0 else null
            is MediaIds.Dir -> dirQueue(ref.uri).takeIf { it.isNotEmpty() }?.let { it to 0 }
            is MediaIds.File -> {
                if (ref.parentUri.isEmpty()) return null
                val items = dirQueue(ref.parentUri)
                val index = items.indexOfFirst { MediaIds.playableUri(it.mediaId) == ref.uri }
                if (index >= 0) items to index else null
            }
            else -> null
        }
    }

    private fun dirQueue(dirUri: String): List<MediaItem> {
        val l = scanner.list(dirUri, settings.showVideos)
        val cover = l.coverUri?.let { ArtworkProvider.file(context, it) }
        return l.media.map { fileItem(it, dirUri, cover).withUri(it.uri, it.mime) }
    }

    fun resolveOne(id: String): MediaItem? = when (val ref = MediaIds.parse(id)) {
        is MediaIds.File -> {
            val node = if (ref.parentUri.isNotEmpty()) {
                scanner.list(ref.parentUri, true).media.firstOrNull { it.uri == ref.uri }
            } else null
            val cover = if (ref.parentUri.isNotEmpty()) scanner.list(ref.parentUri, true).coverUri else null
            (node ?: scanner.node(ref.uri))?.let {
                fileItem(it, ref.parentUri, cover?.let { c -> ArtworkProvider.file(context, c) }).withUri(it.uri, it.mime)
            }
        }
        is MediaIds.StreamRef -> library.stream(ref.id)?.let { streamItem(it).withUri(it.url, null) }
        is MediaIds.External -> MediaItem.Builder()
            .setMediaId(MediaIds.canonical(id))
            .setUri(ref.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(Uri.parse(ref.uri).lastPathSegment?.substringAfterLast('/') ?: "Archivo").setIsPlayable(true).setIsBrowsable(false).build())
            .build()
        else -> null
    }

    private fun shuffleAll(): List<MediaItem> =
        scanner.index(library.accessibleRoots(), false).shuffled().take(MAX_SHUFFLE).map {
            fileItem(it.node, it.parentUri, null, subtitle = it.parentName).withUri(it.node.uri, it.node.mime)
        }

    private fun fromSearch(query: String): MediaItemsWithStartPosition {
        if (query.isBlank()) {
            restoreLastQueue()?.let { return it }
            return MediaItemsWithStartPosition(shuffleAll(), 0, C.TIME_UNSET)
        }
        val q = DocumentScanner.normalize(query)
        library.streams.value.firstOrNull { DocumentScanner.normalize(it.name).contains(q) }?.let { s ->
            return MediaItemsWithStartPosition(listOf(streamItem(s).withUri(s.url, null)), 0, C.TIME_UNSET)
        }
        val hits = scanner.search(query, library.accessibleRoots(), false)
        if (hits.isEmpty()) return MediaItemsWithStartPosition(shuffleAll(), 0, C.TIME_UNSET)
        // Si la mayoría de resultados están en la misma carpeta (un álbum, un artista) reproducimos esa carpeta.
        val topParent = hits.groupingBy { it.parentUri }.eachCount().maxByOrNull { it.value }
        if (topParent != null && topParent.value >= 3) {
            return MediaItemsWithStartPosition(dirQueue(topParent.key), 0, C.TIME_UNSET)
        }
        val items = hits.map { fileItem(it.node, it.parentUri, null, subtitle = it.parentName).withUri(it.node.uri, it.node.mime) }
        return MediaItemsWithStartPosition(items, 0, C.TIME_UNSET)
    }

    fun restoreLastQueue(): MediaItemsWithStartPosition? {
        val saved = library.loadQueue() ?: return null
        val items = saved.mediaIds.mapNotNull { resolveOne(it) }
        if (items.isEmpty()) return null
        val target = saved.mediaIds[saved.index]
        val index = items.indexOfFirst { it.mediaId == target }.coerceAtLeast(0)
        return MediaItemsWithStartPosition(items, index, saved.positionMs)
    }

    private fun withStartPosition(items: List<MediaItem>, index: Int, requested: Long): MediaItemsWithStartPosition {
        var pos = requested
        if (pos == C.TIME_UNSET && settings.rememberPosition) {
            items.getOrNull(index)?.let { pos = library.savedPosition(it.mediaId).takeIf { p -> p > 0 } ?: C.TIME_UNSET }
        }
        return MediaItemsWithStartPosition(items, index, pos)
    }

    // ---------- Construcción de MediaItems ----------

    private fun entryItem(e: Entry, ctx: String): MediaItem? {
        val ref = MediaIds.parse(e.mediaId) ?: return null
        val art = e.artworkUri?.let(Uri::parse) ?: ArtworkProvider.icon(context, if (ref is MediaIds.StreamRef) "radio" else "music")
        return playable(ctx + e.mediaId, e.title, e.subtitle, art)
    }

    fun fileItem(node: DocNode, parentUri: String, cover: Uri?, subtitle: String? = null): MediaItem {
        val art = cover ?: ArtworkProvider.icon(context, if (node.isVideo) "movie" else "music")
        return playable(
            MediaIds.file(parentUri, node.uri),
            DocumentScanner.prettyName(node.name),
            subtitle,
            art,
            if (node.isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC,
        )
    }

    fun streamItem(s: Stream): MediaItem = playable(
        MediaIds.stream(s.id), s.name, "Radio en directo",
        s.logo?.let(Uri::parse) ?: ArtworkProvider.icon(context, "radio"),
        MediaMetadata.MEDIA_TYPE_RADIO_STATION,
    )

    private fun browsable(id: String, title: String, icon: String, style: Int? = null): MediaItem {
        val extras = Bundle().apply {
            putInt(EXTRA_BROWSABLE_HINT, style ?: if (settings.gridFolders) STYLE_GRID else STYLE_LIST)
            putInt(EXTRA_PLAYABLE_HINT, style ?: STYLE_LIST)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setArtworkUri(ArtworkProvider.icon(context, icon))
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun playable(id: String, title: String, subtitle: String?, art: Uri?, type: Int = MediaMetadata.MEDIA_TYPE_MUSIC) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title)
                    .setArtist(subtitle)
                    .setSubtitle(subtitle)
                    .setArtworkUri(art)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(type)
                    .build()
            )
            .build()

    /** Añade el URI real. Para streams HLS sin extensión .m3u8 en la URL forzamos el tipo. */
    private fun MediaItem.withUri(uri: String, mime: String?): MediaItem = buildUpon().setUri(uri).apply {
        if (mime == null && uri.contains("m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
    }.build()

    companion object {
        private const val MAX_SHUFFLE = 500

        // Claves de estilo de contenido reconocidas por Android Auto / AAOS.
        const val EXTRA_CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val EXTRA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        const val EXTRA_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val EXTRA_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val STYLE_LIST = 1
        const val STYLE_GRID = 2
    }
}
