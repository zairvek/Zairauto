package com.zairvek.zairauto.ui

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.data.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val player: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ZairApp(player) }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Abrir un audio/vídeo o una lista M3U desde el gestor de archivos u otra app. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
            whenConnected { player.playFromSearch(query) }
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        val type = intent.type ?: contentResolver.getType(uri).orEmpty()
        lifecycleScope.launch {
            val path = uri.lastPathSegment.orEmpty().lowercase()
            val isPlaylist = type.contains("mpegurl") || type.contains("scpls") || path.endsWith(".m3u") || path.endsWith(".pls")
            if (isPlaylist) {
                val text = withContext(Dispatchers.IO) {
                    runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
                }
                val added = Graph.library.addStreams(M3uParser.parse(text.orEmpty()).map { it.name to it.url })
                Toast.makeText(this@MainActivity, "$added emisoras importadas", Toast.LENGTH_SHORT).show()
                return@launch
            }
            whenConnected { player.playUri(uri) }
        }
    }

    /** Espera (hasta 5 s) a que el MediaBrowser esté conectado al servicio. */
    private fun whenConnected(action: () -> Unit) {
        lifecycleScope.launch {
            repeat(50) {
                if (player.browser != null) {
                    action()
                    return@launch
                }
                delay(100)
            }
        }
    }
}
