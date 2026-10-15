package com.zairvek.zairauto.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Lee etiquetas ID3/Vorbis/MP4 del archivo que va a sonar (título real, artista, álbum, carátula). */
object TagReader {
    data class Tags(val title: String?, val artist: String?, val album: String?, val hasArt: Boolean)

    fun read(context: Context, uri: Uri): Tags? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            Tags(
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.ifEmpty { null },
                artist = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))?.trim()?.ifEmpty { null },
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.ifEmpty { null },
                hasArt = r.embeddedPicture != null,
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}
