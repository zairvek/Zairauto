package com.zairvek.zairauto.data

/** Lector tolerante de listas M3U / M3U8 / PLS para importar emisoras. */
object M3uParser {
    data class Item(val name: String, val url: String)

    fun parse(text: String): List<Item> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.any { it.startsWith("[playlist]", ignoreCase = true) }) return parsePls(lines)
        val out = mutableListOf<Item>()
        var pendingName: String? = null
        for (line in lines) {
            when {
                line.startsWith("#EXTINF", ignoreCase = true) ->
                    pendingName = line.substringAfter(',', "").trim().ifEmpty { null }
                line.startsWith("#") -> Unit
                isUrl(line) -> {
                    out += Item(pendingName ?: line.substringAfterLast('/').ifEmpty { line }, line)
                    pendingName = null
                }
            }
        }
        return out
    }

    private fun parsePls(lines: List<String>): List<Item> {
        val files = mutableMapOf<String, String>()
        val titles = mutableMapOf<String, String>()
        for (l in lines) {
            val key = l.substringBefore('=').trim()
            val value = l.substringAfter('=', "").trim()
            when {
                key.startsWith("File", true) -> files[key.drop(4)] = value
                key.startsWith("Title", true) -> titles[key.drop(5)] = value
            }
        }
        return files.toSortedMap(compareBy { it.toIntOrNull() ?: 0 })
            .filterValues(::isUrl)
            .map { (n, url) -> Item(titles[n] ?: url, url) }
    }

    private fun isUrl(s: String) = s.startsWith("http://", true) || s.startsWith("https://", true) ||
        s.startsWith("rtsp://", true) || s.startsWith("rtmp://", true)
}
