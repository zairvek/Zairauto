@file:OptIn(ExperimentalMaterial3Api::class)

package com.zairvek.zairauto.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.data.Entry
import com.zairvek.zairauto.data.RootFolder
import com.zairvek.zairauto.playback.MediaIds
import com.zairvek.zairauto.ui.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Crumb(val mediaId: String, val title: String)

private val CrumbsSaver = listSaver<List<Crumb>, String>(
    save = { list -> list.flatMap { listOf(it.mediaId, it.title) } },
    restore = { flat -> flat.chunked(2).map { Crumb(it[0], it[1]) } },
)

@Composable
fun LibraryScreen(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val roots by Graph.library.roots.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    var crumbs by rememberSaveable(stateSaver = CrumbsSaver) { mutableStateOf(emptyList<Crumb>()) }
    var query by rememberSaveable { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { Graph.library.addRoot(uri) }
                .onFailure { Toast.makeText(ctx, "No se pudo añadir la carpeta", Toast.LENGTH_SHORT).show() }
        }
    }

    BackHandler(enabled = crumbs.isNotEmpty() || query.isNotEmpty()) {
        if (query.isNotEmpty()) query = "" else crumbs = crumbs.dropLast(1)
    }

    Column(Modifier.fillMaxSize()) {
        if (crumbs.isEmpty()) {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
                Text("Biblioteca", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                SearchField(query, onChange = { query = it })
            }
        } else {
            Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { crumbs = crumbs.dropLast(1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
                Column(Modifier.weight(1f)) {
                    Text(crumbs.last().title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (crumbs.size > 1) {
                        Text(
                            crumbs.dropLast(1).joinToString(" › ") { it.title },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        when {
            query.isNotBlank() -> SearchResults(vm, query, player.mediaId)
            crumbs.isEmpty() -> RootsList(
                roots = roots,
                onOpen = { root -> crumbs = listOf(Crumb(MediaIds.dir(Graph.scanner.rootDocUri(root.treeUri).toString()), root.name)) },
                onAdd = { picker.launch(null) },
                onShuffle = { vm.play(MediaIds.SHUFFLE_ALL) },
            )
            else -> DirectoryView(
                vm = vm,
                connected = player.connected,
                mediaId = crumbs.last().mediaId,
                currentMediaId = player.mediaId,
                onOpenDir = { item -> crumbs = crumbs + Crumb(item.mediaId, item.mediaMetadata.title?.toString() ?: "") },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Buscar canciones, carpetas, emisoras…") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Clear, "Borrar") } },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RootsList(roots: List<RootFolder>, onOpen: (RootFolder) -> Unit, onAdd: () -> Unit, onShuffle: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (roots.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.FolderOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Todavía no hay carpetas", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Elige las carpetas donde tienes música, audiolibros, podcasts o vídeos. Puedes añadir varias, incluida la tarjeta SD o un USB.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onAdd) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Añadir carpeta")
                    }
                }
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onShuffle) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Mezclar todo")
                    }
                    OutlinedButton(onClick = onAdd) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Añadir carpeta")
                    }
                }
            }
            items(roots, key = { it.treeUri }) { root ->
                var menu by remember { mutableStateOf(false) }
                MediaRow(
                    title = root.name,
                    subtitle = "Carpeta",
                    artwork = null,
                    fallbackIcon = Icons.Rounded.Folder,
                    onClick = { onOpen(root) },
                    trailing = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Opciones") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Quitar de la biblioteca") }, onClick = {
                                    menu = false
                                    Graph.library.removeRoot(root)
                                })
                            }
                        }
                    },
                )
            }
        }
        item {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    "Privacidad: Zairauto solo puede leer las carpetas que elijas aquí. No necesita permiso de acceso a todo el almacenamiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DirectoryView(
    vm: PlayerViewModel,
    connected: Boolean,
    mediaId: String,
    currentMediaId: String?,
    onOpenDir: (MediaItem) -> Unit,
) {
    var items by remember(mediaId) { mutableStateOf<List<MediaItem>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val favorites by Graph.library.favorites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(mediaId, connected, reload) {
        if (connected) {
            items = vm.children(mediaId)
            refreshing = false
        }
    }

    val list = items
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val playable = list.filter { it.mediaMetadata.isPlayable == true }
    val currentUri = currentMediaId?.let(MediaIds::playableUri)

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                Graph.scanner.invalidate()
                reload++
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (playable.isNotEmpty()) item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.playInOrder(playable.first().mediaId) }) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Reproducir")
                    }
                    FilledTonalButton(onClick = { vm.playShuffled(mediaId) }) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Aleatorio")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${playable.size} archivos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            if (list.isEmpty()) item {
                Text(
                    "Esta carpeta no tiene audio ni vídeo.",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.mediaId }) { item ->
                MediaItemRow(item, vm, favorites.map { it.mediaId }.toSet(), currentUri, onOpenDir)
            }
        }
    }
}

@Composable
private fun MediaItemRow(
    item: MediaItem,
    vm: PlayerViewModel,
    favoriteIds: Set<String>,
    currentUri: String?,
    onOpenDir: (MediaItem) -> Unit,
) {
    val md = item.mediaMetadata
    val title = md.title?.toString() ?: ""
    if (md.isBrowsable == true) {
        MediaRow(title = title, subtitle = null, artwork = null, fallbackIcon = Icons.Rounded.Folder, onClick = { onOpenDir(item) })
        return
    }
    val canonical = MediaIds.canonical(item.mediaId)
    val uri = MediaIds.playableUri(canonical)
    MediaRow(
        title = title,
        subtitle = md.artist?.toString(),
        artwork = md.artworkUri,
        fallbackIcon = when (md.mediaType) {
            MediaMetadata.MEDIA_TYPE_VIDEO -> Icons.Rounded.Movie
            MediaMetadata.MEDIA_TYPE_RADIO_STATION -> Icons.Rounded.Radio
            else -> Icons.Rounded.MusicNote
        },
        playing = uri != null && uri == currentUri,
        favorite = canonical in favoriteIds,
        onToggleFavorite = {
            Graph.library.toggleFavorite(Entry(canonical, title, md.artist?.toString(), md.artworkUri?.toString()))
        },
        onClick = { vm.play(item.mediaId) },
    )
}

@Composable
private fun SearchResults(vm: PlayerViewModel, query: String, currentMediaId: String?) {
    var results by remember { mutableStateOf<List<MediaItem>?>(null) }
    val favorites by Graph.library.favorites.collectAsStateWithLifecycle()
    LaunchedEffect(query) {
        delay(300) // espera a que el usuario deje de escribir
        results = null
        results = vm.search(query)
    }
    val list = results
    Column {
        if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (list != null && list.isEmpty()) item {
                Text(
                    "Sin resultados para \"$query\"",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list.orEmpty(), key = { it.mediaId }) { item ->
                MediaItemRow(item, vm, favorites.map { it.mediaId }.toSet(), currentMediaId?.let(MediaIds::playableUri)) {}
            }
        }
    }
}
