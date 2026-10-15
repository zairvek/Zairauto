package com.zairvek.zairauto

import android.app.Application
import com.zairvek.zairauto.data.AppSettings
import com.zairvek.zairauto.data.LibraryStore
import com.zairvek.zairauto.data.DocumentScanner
import com.zairvek.zairauto.playback.AudioEffects

class ZairApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Dependencias compartidas por la UI y el servicio de reproducción (mismo proceso). */
object Graph {
    lateinit var library: LibraryStore
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var scanner: DocumentScanner
        private set
    lateinit var effects: AudioEffects
        private set

    fun init(app: Application) {
        library = LibraryStore(app)
        settings = AppSettings(app)
        scanner = DocumentScanner(app)
        effects = AudioEffects(app)
    }
}
