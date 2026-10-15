package com.zairvek.zairauto.playback

/**
 * Esquema de identificadores del árbol de navegación.
 *
 *  @root, @folders, @favs, @recent, @streams, @shuffle  -> nodos fijos
 *  d|<uriCarpeta>                                         -> carpeta
 *  f|<uriCarpetaPadre>|<uriArchivo>                       -> archivo (al pulsarlo se encola toda su carpeta)
 *  s|<id>                                                 -> emisora
 *  x|<uri>                                                -> archivo abierto desde otra app
 *
 * Los hijos de Favoritos/Recientes llevan un prefijo de contexto ("F>" / "R>") para que, al
 * pulsar uno, la cola sea la lista de favoritos y no la carpeta del archivo. "Z>" delante de
 * una carpeta pide reproducirla en orden aleatorio.
 * Los URIs de documentos nunca contienen '|' sin escapar, así que es un separador seguro.
 */
object MediaIds {
    const val ROOT = "@root"
    const val RECENT_ROOT = "@recent_root"
    const val FOLDERS = "@folders"
    const val FAVORITES = "@favs"
    const val RECENTS = "@recent"
    const val STREAMS = "@streams"
    const val SHUFFLE_ALL = "@shuffle"

    const val CTX_FAVORITES = "F>"
    const val CTX_RECENTS = "R>"
    const val CTX_SHUFFLED = "Z>"

    fun dir(uri: String) = "d|$uri"
    fun file(parentUri: String, uri: String) = "f|$parentUri|$uri"
    fun stream(id: String) = "s|$id"
    fun external(uri: String) = "x|$uri"

    sealed interface Ref
    data class Dir(val uri: String) : Ref
    data class File(val parentUri: String, val uri: String) : Ref
    data class StreamRef(val id: String) : Ref
    data class External(val uri: String) : Ref
    data class Fixed(val id: String) : Ref

    /** Quita el prefijo de contexto y devuelve el id canónico. */
    fun canonical(id: String) = id.removePrefix(CTX_FAVORITES).removePrefix(CTX_RECENTS).removePrefix(CTX_SHUFFLED)

    fun parse(id: String): Ref? {
        val c = canonical(id)
        if (c.startsWith("@")) return Fixed(c)
        val kind = c.substringBefore('|', "")
        val rest = c.substringAfter('|', "")
        return when (kind) {
            "d" -> Dir(rest)
            "f" -> File(rest.substringBefore('|'), rest.substringAfter('|'))
            "s" -> StreamRef(rest)
            "x" -> External(rest)
            else -> null
        }
    }

    /** URI reproducible del id (para guardar posiciones), o null si es un nodo navegable. */
    fun playableUri(id: String): String? = when (val r = parse(id)) {
        is File -> r.uri
        is External -> r.uri
        else -> null
    }
}
