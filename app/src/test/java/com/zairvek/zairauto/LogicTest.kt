package com.zairvek.zairauto

import com.zairvek.zairauto.data.DocumentScanner
import com.zairvek.zairauto.data.M3uParser
import com.zairvek.zairauto.playback.MediaIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test
    fun naturalOrderSortsNumbersByValue() {
        val names = listOf("Track 10.mp3", "track 2.mp3", "Track 1.mp3", "Intro.mp3", "Track 02b.mp3")
        val sorted = names.sortedWith { a, b -> DocumentScanner.naturalCompare(a, b) }
        assertEquals(listOf("Intro.mp3", "Track 1.mp3", "track 2.mp3", "Track 02b.mp3", "Track 10.mp3"), sorted)
    }

    @Test
    fun normalizeIgnoresAccentsAndCase() {
        assertEquals("cancion de amor", DocumentScanner.normalize("Canción_de-AMOR!"))
    }

    @Test
    fun effectiveMimeFallsBackToExtension() {
        assertEquals("audio/flac", DocumentScanner.effectiveMime("song.FLAC", "application/octet-stream"))
        assertEquals("video/mkv", DocumentScanner.effectiveMime("movie.mkv", ""))
        assertEquals("audio/mpeg", DocumentScanner.effectiveMime("a.mp3", "audio/mpeg"))
    }

    @Test
    fun parsesExtendedM3u() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="x",Radio Uno
            http://example.com/uno.mp3
            #EXTINF:-1,Radio Dos
            https://example.com/dos.m3u8
            not-a-url
        """.trimIndent()
        val items = M3uParser.parse(text)
        assertEquals(listOf("Radio Uno", "Radio Dos"), items.map { it.name })
        assertEquals("https://example.com/dos.m3u8", items[1].url)
    }

    @Test
    fun parsesPls() {
        val text = "[playlist]\nFile1=http://a.com/s\nTitle1=Alpha\nFile2=http://b.com/s\nNumberOfEntries=2"
        val items = M3uParser.parse(text)
        assertEquals(2, items.size)
        assertEquals("Alpha", items[0].name)
        assertEquals("http://b.com/s", items[1].name)
    }

    @Test
    fun mediaIdsRoundTrip() {
        val parent = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FRock"
        val file = "$parent%2Fsong%7C1.mp3"
        val id = MediaIds.file(parent, file)
        val ref = MediaIds.parse(MediaIds.CTX_FAVORITES + id) as MediaIds.File
        assertEquals(parent, ref.parentUri)
        assertEquals(file, ref.uri)
        assertEquals(id, MediaIds.canonical(MediaIds.CTX_RECENTS + id))
        assertEquals(file, MediaIds.playableUri(id))
        assertTrue(MediaIds.parse(MediaIds.SHUFFLE_ALL) is MediaIds.Fixed)
        assertEquals(MediaIds.Dir(parent), MediaIds.parse(MediaIds.CTX_SHUFFLED + MediaIds.dir(parent)))
    }
}
