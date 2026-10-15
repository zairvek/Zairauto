package com.zairvek.zairauto.data

import org.json.JSONArray
import org.json.JSONObject

/** Carpeta raíz elegida por el usuario mediante el selector del sistema (SAF). */
data class RootFolder(val treeUri: String, val name: String)

/** Elemento guardado en Favoritos o Recientes. */
data class Entry(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUri: String? = null,
) {
    fun toJson() = JSONObject().apply {
        put("id", mediaId)
        put("t", title)
        subtitle?.let { put("s", it) }
        artworkUri?.let { put("a", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = Entry(
            mediaId = o.getString("id"),
            title = o.optString("t"),
            subtitle = o.optString("s").ifEmpty { null },
            artworkUri = o.optString("a").ifEmpty { null },
        )
    }
}

/** Emisora de radio o stream por URL (incluye HLS). */
data class Stream(val id: String, val name: String, val url: String, val logo: String? = null) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("n", name)
        put("u", url)
        logo?.let { put("l", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = Stream(
            id = o.getString("id"),
            name = o.optString("n"),
            url = o.getString("u"),
            logo = o.optString("l").ifEmpty { null },
        )
    }
}

/** Archivo o carpeta listada dentro de una carpeta raíz. */
data class DocNode(
    val uri: String,
    val name: String,
    val mime: String,
    val isDir: Boolean,
    val size: Long,
) {
    val isVideo get() = mime.startsWith("video/")
}

internal inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { runCatching { block(it) }.getOrNull() } }
