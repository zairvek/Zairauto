package com.zairvek.zairauto.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Estado persistente de la biblioteca: carpetas, favoritos, recientes, emisoras,
 * posiciones guardadas y la última cola de reproducción.
 */
class LibraryStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val queueFile = File(context.filesDir, "last_queue.json")

    private val _roots = MutableStateFlow(loadList("roots") { RootFolder(it.getString("u"), it.optString("n")) })
    val roots: StateFlow<List<RootFolder>> = _roots.asStateFlow()

    private val _favorites = MutableStateFlow(loadList("favorites", Entry::fromJson))
    val favorites: StateFlow<List<Entry>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(loadList("recents", Entry::fromJson))
    val recents: StateFlow<List<Entry>> = _recents.asStateFlow()

    private val _streams = MutableStateFlow(loadList("streams", Stream::fromJson))
    val streams: StateFlow<List<Stream>> = _streams.asStateFlow()

    // ---- Carpetas ----

    fun addRoot(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = displayNameOf(treeUri)
        _roots.update { list -> (list.filterNot { it.treeUri == treeUri.toString() } + RootFolder(treeUri.toString(), name)) }
        saveRoots()
    }

    fun removeRoot(root: RootFolder) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(root.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _roots.update { it - root }
        saveRoots()
    }

    /** Carpetas cuyo permiso sigue vigente (el usuario puede revocarlo desde Ajustes del sistema). */
    fun accessibleRoots(): List<RootFolder> {
        val granted = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        return _roots.value.filter { it.treeUri in granted }
    }

    private fun displayNameOf(treeUri: Uri): String {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return runCatching {
            context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':').ifEmpty { "Almacenamiento" }
    }

    private fun saveRoots() = saveList("roots", _roots.value) { JSONObject().put("u", it.treeUri).put("n", it.name) }

    // ---- Favoritos ----

    fun isFavorite(mediaId: String) = _favorites.value.any { it.mediaId == mediaId }

    fun toggleFavorite(entry: Entry): Boolean {
        val nowFav = !isFavorite(entry.mediaId)
        _favorites.update { list -> if (nowFav) listOf(entry) + list else list.filterNot { it.mediaId == entry.mediaId } }
        saveList("favorites", _favorites.value, Entry::toJson)
        return nowFav
    }

    // ---- Recientes ----

    fun addRecent(entry: Entry) {
        _recents.update { list -> (listOf(entry) + list.filterNot { it.mediaId == entry.mediaId }).take(MAX_RECENTS) }
        saveList("recents", _recents.value, Entry::toJson)
    }

    fun clearRecents() {
        _recents.value = emptyList()
        saveList("recents", emptyList(), Entry::toJson)
    }

    // ---- Emisoras ----

    fun stream(id: String) = _streams.value.firstOrNull { it.id == id }

    fun addStream(name: String, url: String, logo: String? = null): Stream {
        val s = Stream(UUID.randomUUID().toString().take(8), name.ifBlank { url }, url.trim(), logo)
        _streams.update { it + s }
        saveList("streams", _streams.value, Stream::toJson)
        return s
    }

    fun addStreams(items: List<Pair<String, String>>): Int {
        val existing = _streams.value.map { it.url }.toSet()
        val fresh = items.filter { it.second !in existing }.distinctBy { it.second }
            .map { Stream(UUID.randomUUID().toString().take(8), it.first.ifBlank { it.second }, it.second) }
        _streams.update { it + fresh }
        saveList("streams", _streams.value, Stream::toJson)
        return fresh.size
    }

    fun removeStream(s: Stream) {
        _streams.update { it.filterNot { x -> x.id == s.id } }
        saveList("streams", _streams.value, Stream::toJson)
    }

    // ---- Posición por archivo (audiolibros, podcasts, vídeos largos) ----

    fun savedPosition(mediaUri: String): Long = prefs.getLong("pos:$mediaUri", 0L)

    fun savePosition(mediaUri: String, positionMs: Long) {
        prefs.edit().apply { if (positionMs > 0) putLong("pos:$mediaUri", positionMs) else remove("pos:$mediaUri") }.apply()
    }

    // ---- Última cola ----

    data class SavedQueue(val mediaIds: List<String>, val index: Int, val positionMs: Long)

    fun saveQueue(mediaIds: List<String>) {
        runCatching { queueFile.writeText(JSONArray(mediaIds).toString()) }
    }

    fun saveQueuePosition(index: Int, positionMs: Long) {
        prefs.edit().putInt("q_index", index).putLong("q_pos", positionMs).apply()
    }

    fun loadQueue(): SavedQueue? {
        val ids = runCatching {
            val arr = JSONArray(queueFile.readText())
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val index = prefs.getInt("q_index", 0).coerceIn(0, ids.lastIndex)
        return SavedQueue(ids, index, prefs.getLong("q_pos", 0L))
    }

    // ---- utilidades ----

    private fun <T> loadList(key: String, parse: (JSONObject) -> T): List<T> =
        runCatching { JSONArray(prefs.getString(key, "[]")).mapObjects(parse) }.getOrDefault(emptyList())

    private fun <T> saveList(key: String, list: List<T>, toJson: (T) -> JSONObject) {
        prefs.edit().putString(key, JSONArray().apply { list.forEach { put(toJson(it)) } }.toString()).apply()
    }

    companion object {
        const val MAX_RECENTS = 40
    }
}
