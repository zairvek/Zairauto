package com.zairvek.zairauto.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.data.Entry
import com.zairvek.zairauto.playback.MediaIds
import com.zairvek.zairauto.ui.Artwork
import com.zairvek.zairauto.ui.PlayerUiState
import com.zairvek.zairauto.ui.PlayerViewModel
import com.zairvek.zairauto.ui.SectionTitle
import com.zairvek.zairauto.ui.Tab
import java.util.Calendar

@Composable
fun HomeScreen(vm: PlayerViewModel, player: PlayerUiState, onOpenPlayer: () -> Unit, onGoTo: (Tab) -> Unit) {
    val favorites by Graph.library.favorites.collectAsStateWithLifecycle()
    val recents by Graph.library.recents.collectAsStateWithLifecycle()
    val roots by Graph.library.roots.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    var guide by remember { mutableStateOf(false) }
    val currentCanonical = player.mediaId?.let(MediaIds::canonical)

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                Text(greeting(), style = MaterialTheme.typography.headlineLarge)
                Text("¿Qué suena hoy en la carretera?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!settings.setupDismissed) item {
            AndroidAutoCard(onSetup = { guide = true }, onDismiss = { Graph.settings.update { it.copy(setupDismissed = true) } })
        }

        if (roots.isEmpty()) item {
            Card(
                onClick = { onGoTo(Tab.LIBRARY) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CreateNewFolder, null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Añade tu música", style = MaterialTheme.typography.titleMedium)
                        Text("Elige una carpeta con canciones, audiolibros o vídeos.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (player.hasMedia) item { NowPlayingHero(player, vm, onOpenPlayer) }

        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Mezclar todo", Icons.Rounded.Shuffle, Modifier.weight(1f), enabled = roots.isNotEmpty()) { vm.play(MediaIds.SHUFFLE_ALL) }
                QuickAction("Radio", Icons.Rounded.Radio, Modifier.weight(1f)) { onGoTo(Tab.RADIO) }
            }
        }

        if (favorites.isNotEmpty()) {
            item { SectionTitle("Favoritos") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favorites, key = { it.mediaId }) { e ->
                        FavoriteCard(e, playing = e.mediaId == currentCanonical) { vm.play(MediaIds.CTX_FAVORITES + e.mediaId) }
                    }
                }
            }
        }

        if (recents.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Escuchado recientemente", Modifier.weight(1f))
                    TextButton(onClick = { Graph.library.clearRecents() }, modifier = Modifier.padding(end = 8.dp, top = 16.dp)) { Text("Borrar") }
                }
            }
            items(recents.take(15), key = { "r" + it.mediaId }) { e ->
                MediaRow(
                    title = e.title,
                    subtitle = e.subtitle,
                    artwork = e.artworkUri?.let(Uri::parse),
                    playing = e.mediaId == currentCanonical,
                    favorite = favorites.any { it.mediaId == e.mediaId },
                    onToggleFavorite = { Graph.library.toggleFavorite(e) },
                    onClick = { vm.play(MediaIds.CTX_RECENTS + e.mediaId) },
                )
            }
        }
    }

    if (guide) AndroidAutoGuideDialog { guide = false }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
private fun AndroidAutoCard(onSetup: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.DirectionsCar, null, modifier = Modifier.size(32.dp).padding(top = 2.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Úsala en tu coche", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Configura Android Auto en 1 minuto para ver Zairauto en la pantalla del coche.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onSetup) { Text("Ver cómo") }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Ocultar") }
        }
    }
}

@Composable
private fun NowPlayingHero(player: PlayerUiState, vm: PlayerViewModel, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(player.artworkUri, size = 84.dp, corner = 16.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (player.isPlaying) "SONANDO AHORA" else "CONTINUAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                )
                Text(player.title, style = MaterialTheme.typography.titleLarge, color = cs.onPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                player.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onPrimary.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FilledIconButton(
                onClick = vm::togglePlay,
                modifier = Modifier.size(56.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = cs.onPrimary, contentColor = cs.primary),
            ) {
                Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FavoriteCard(e: Entry, playing: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Artwork(
            e.artworkUri?.let(Uri::parse),
            size = 124.dp,
            corner = 16.dp,
            fallback = if (e.mediaId.startsWith("s|")) Icons.Rounded.Radio else Icons.Rounded.MusicNote,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            e.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        e.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
