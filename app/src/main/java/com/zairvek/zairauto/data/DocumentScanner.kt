package com.zairvek.zairauto.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.LruCache
import java.text.Normalizer
import java.util.Locale

/**
 * Lista carpetas usando DocumentsContract directamente (mucho más rápido que DocumentFile,
 * que hace una consulta por cada propiedad de cada archivo) y mantiene un índice en memoria
 * para la búsqueda y la búsqueda por voz.
 */
class DocumentScanner(private val context: Context) {

    data class Listing(val dirs: List<DocNode>, val media: List<DocNode>, val coverUri: String?)

    data class Indexed(val node: DocNode, val parentUri: String, val parentName: String)

    private class Cached(val listing: Listing, val at: Long)

    private val cache = LruCache<String, Cached>(64)
    @Volatile private var index: List<Indexed>? = null
    @Volatile private var indexAt = 0L

    fun rootDocUri(treeUri: String): Uri {
        val tree = Uri.parse(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    }

    fun list(dirUri: String, includeVideos: Boolean, force: Boolean = false): Listing {
        val key = "$dirUri|$includeVideos"
        val now = System.currentTimeMillis()
        if (!force) cache.get(key)?.takeIf { now - it.at < CACHE_MS }?.let { return it.listing }

        val dir = Uri.parse(dirUri)
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getDocumentId(dir))
        }.getOrNull() ?: return Listing(emptyList(), emptyList(), null)
        val dirs = mutableListOf<DocNode>()
        val media = mutableListOf<DocNode>()
        var cover: Pair<Int, String>? = null
        runCatching {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    val mime = c.getString(2) ?: ""
                    val uri = DocumentsContract.buildDocumentUriUsingTree(dir, id).toString()
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    when {
                        mime == Document.MIME_TYPE_DIR -> dirs += DocNode(uri, name, mime, true, 0)
                        else -> {
                            val effective = effectiveMime(name, mime)
                            if (effective.startsWith("audio/") || (includeVideos && effective.startsWith("video/"))) {
                                media += DocNode(uri, name, effective, false, size)
                            } else if (effective.startsWith("image/")) {
                                val rank = coverRank(name)
                                if (rank < (cover?.first ?: Int.MAX_VALUE)) cover = rank to uri
                            }
                        }
                    }
                }
            }
        }
        val listing = Listing(
            dirs.sortedWith(NaturalOrder),
            media.sortedWith(NaturalOrder),
            cover?.second,
        )
        cache.put(key, Cached(listing, now))
        return listing
    }

    /** Consulta un único documento (por ejemplo, un favorito cuya carpeta no está en caché). */
    fun node(uri: String): DocNode? = runCatching {
        context.contentResolver.query(Uri.parse(uri), PROJECTION, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val name = c.getString(1) ?: return@use null
            val mime = effectiveMime(name, c.getString(2) ?: "")
            DocNode(uri, name, mime, mime == Document.MIME_TYPE_DIR, if (c.isNull(3)) 0 else c.getLong(3))
        }
    }.getOrNull()

    fun invalidate() {
        cache.evictAll()
        index = null
    }

    /** Índice plano de todos los archivos multimedia bajo las carpetas raíz (se reconstruye cada 10 min). */
    fun index(roots: List<RootFolder>, includeVideos: Boolean): List<Indexed> {
        val now = System.currentTimeMillis()
        index?.takeIf { now - indexAt < INDEX_MS }?.let { return it }
        val out = ArrayList<Indexed>()
        val queue = ArrayDeque<Triple<String, String, Int>>()
        roots.forEach { queue += Triple(rootDocUri(it.treeUri).toString(), it.name, 0) }
        while (queue.isNotEmpty() && out.size < MAX_INDEX) {
            val (dir, dirName, depth) = queue.removeFirst()
            val l = list(dir, includeVideos)
            l.media.forEach { out += Indexed(it, dir, dirName) }
            if (depth < MAX_DEPTH) l.dirs.forEach { queue += Triple(it.uri, it.name, depth + 1) }
        }
        index = out
        indexAt = now
        return out
    }

    fun search(query: String, roots: List<RootFolder>, includeVideos: Boolean, limit: Int = 200): List<Indexed> {
        val tokens = normalize(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return index(roots, includeVideos)
            .asSequence()
            .map { it to normalize(it.node.name + " " + it.parentName) }
            .filter { (_, hay) -> tokens.all { hay.contains(it) } }
            .sortedByDescending { (it, _) -> tokens.count { t -> normalize(it.node.name).contains(t) } }
            .take(limit)
            .map { it.first }
            .toList()
    }

    companion object {
        private const val CACHE_MS = 60_000L
        private const val INDEX_MS = 10 * 60_000L
        private const val MAX_INDEX = 20_000
        private const val MAX_DEPTH = 12

        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )

        private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "wma", "mka", "amr", "aiff", "ape", "alac")
        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "m4v", "wmv", "flv")

        /** Muchos proveedores devuelven application/octet-stream para FLAC/MKV/OPUS: deducimos por extensión. */
        fun effectiveMime(name: String, mime: String): String {
            if (mime.startsWith("audio/") || mime.startsWith("video/") || mime.startsWith("image/") || mime == Document.MIME_TYPE_DIR) return mime
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return when (ext) {
                in AUDIO_EXT -> "audio/$ext"
                in VIDEO_EXT -> "video/$ext"
                "jpg", "jpeg", "png", "webp" -> "image/$ext"
                else -> mime
            }
        }

        private fun coverRank(name: String): Int {
            val n = name.lowercase(Locale.ROOT).substringBeforeLast('.')
            return when {
                n == "cover" -> 0
                n == "folder" -> 1
                n == "front" -> 2
                n.startsWith("albumart") -> 3
                n.contains("cover") -> 4
                else -> 100
            }
        }

        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        /** "Track 2" antes que "Track 10", ignorando mayúsculas y acentos. */
        val NaturalOrder = Comparator<DocNode> { a, b -> naturalCompare(a.name, b.name) }

        fun naturalCompare(a: String, b: String): Int {
            val x = a.lowercase(Locale.ROOT)
            val y = b.lowercase(Locale.ROOT)
            var i = 0
            var j = 0
            while (i < x.length && j < y.length) {
                val cx = x[i]
                val cy = y[j]
                if (cx.isDigit() && cy.isDigit()) {
                    val si = i
                    val sj = j
                    while (i < x.length && x[i].isDigit()) i++
                    while (j < y.length && y[j].isDigit()) j++
                    val nx = x.substring(si, i).trimStart('0')
                    val ny = y.substring(sj, j).trimStart('0')
                    if (nx.length != ny.length) return nx.length - ny.length
                    val c = nx.compareTo(ny)
                    if (c != 0) return c
                } else {
                    if (cx != cy) return cx.compareTo(cy)
                    i++
                    j++
                }
            }
            return (x.length - i) - (y.length - j)
        }

        /** "01_mi_cancion.mp3" -> "01 mi cancion" */
        fun prettyName(fileName: String): String =
            fileName.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { fileName }
    }
}
