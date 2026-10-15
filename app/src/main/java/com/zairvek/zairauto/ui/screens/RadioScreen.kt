package com.zairvek.zairauto.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.data.Entry
import com.zairvek.zairauto.data.M3uParser
import com.zairvek.zairauto.data.Stream
import com.zairvek.zairauto.playback.MediaIds
import com.zairvek.zairauto.ui.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun RadioScreen(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val streams by Graph.library.streams.collectAsStateWithLifecycle()
    val favorites by Graph.library.favorites.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var addDialog by remember { mutableStateOf(false) }
    var urlImportDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun import(text: String?) {
        val items = M3uParser.parse(text.orEmpty())
        val added = Graph.library.addStreams(items.map { it.name to it.url })
        Toast.makeText(ctx, if (items.isEmpty()) "No se encontraron emisoras en la lista" else "$added emisoras importadas", Toast.LENGTH_SHORT).show()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            import(text)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.statusBarsPadding().padding(top = 20.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Radio", style = MaterialTheme.typography.headlineLarge)
                        Text("Emisoras y streams en directo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Importar") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Importar archivo M3U / PLS") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                                onClick = {
                                    menu = false
                                    filePicker.launch(arrayOf("*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Importar lista desde URL") },
                                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                onClick = {
                                    menu = false
                                    urlImportDialog = true
                                },
                            )
                        }
                    }
                }
            }

            if (streams.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Radio, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin emisoras todavía", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Añade la URL de cualquier radio online (MP3, AAC u HLS) o importa una lista M3U. En radio-browser.info encontrarás miles de emisoras.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(streams, key = { it.id }) { s ->
                val id = MediaIds.stream(s.id)
                StationCard(
                    stream = s,
                    playing = player.mediaId == id,
                    favorite = favorites.any { it.mediaId == id },
                    onPlay = { vm.play(id) },
                    onFavorite = { Graph.library.toggleFavorite(Entry(id, s.name, "Radio en directo", s.logo)) },
                    onDelete = { Graph.library.removeStream(s) },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { addDialog = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Añadir emisora") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (addDialog) {
        TwoFieldDialog(
            title = "Nueva emisora",
            firstLabel = "Nombre",
            secondLabel = "URL del stream",
            onDismiss = { addDialog = false },
            onConfirm = { name, url ->
                addDialog = false
                if (url.startsWith("http", true)) Graph.library.addStream(name, url)
                else Toast.makeText(ctx, "La URL debe empezar por http:// o https://", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (urlImportDialog) {
        TwoFieldDialog(
            title = "Importar lista desde URL",
            firstLabel = null,
            secondLabel = "URL de la lista .m3u / .pls",
            onDismiss = { urlImportDialog = false },
            onConfirm = { _, url ->
                urlImportDialog = false
                scope.launch {
                    val text = withContext(Dispatchers.IO) { download(url) }
                    if (text == null) Toast.makeText(ctx, "No se pudo descargar la lista", Toast.LENGTH_SHORT).show() else import(text)
                }
            },
        )
    }
}

private fun download(url: String): String? = runCatching {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 15_000
    conn.instanceFollowRedirects = true
    try {
        conn.inputStream.bufferedReader().use { r ->
            // Las listas son texto; cortamos en 2 MB por si la URL es en realidad un stream de audio.
            val buf = CharArray(2 * 1024 * 1024)
            val n = r.read(buf)
            if (n <= 0) "" else String(buf, 0, n)
        }
    } finally {
        conn.disconnect()
    }
}.getOrNull()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationCard(
    stream: Stream,
    playing: Boolean,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val palette = remember(stream.id) {
        val hue = (stream.name.hashCode() and 0x7fffffff) % 360
        listOf(Color.hsv(hue.toFloat(), 0.55f, 0.85f), Color.hsv(((hue + 50) % 360).toFloat(), 0.65f, 0.55f))
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceContainerHigh)
            .combinedClickable(onClick = onPlay, onLongClick = { menu = true }),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f).background(Brush.linearGradient(palette))) {
            if (stream.logo != null) {
                AsyncImage(stream.logo, null, modifier = Modifier.fillMaxSize().padding(16.dp))
            } else {
                Text(
                    stream.name.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (playing) {
                Icon(
                    Icons.Rounded.GraphicEq, "Sonando",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(22.dp),
                )
            }
            if (favorite) {
                Icon(Icons.Rounded.Star, "Favorita", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                stream.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (playing) cs.primary else cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Opciones") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (favorite) "Quitar de favoritos" else "Añadir a favoritos") }, onClick = {
                        menu = false
                        onFavorite()
                    })
                    DropdownMenuItem(text = { Text("Eliminar") }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
    }
}

@Composable
private fun TwoFieldDialog(
    title: String,
    firstLabel: String?,
    secondLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (firstLabel != null) {
                    OutlinedTextField(first, { first = it }, label = { Text(firstLabel) }, singleLine = true)
                }
                OutlinedTextField(
                    second, { second = it },
                    label = { Text(secondLabel) },
                    singleLine = true,
                    placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(first.trim(), second.trim()) }, enabled = second.isNotBlank()) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
