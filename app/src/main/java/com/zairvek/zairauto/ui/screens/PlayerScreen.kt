@file:OptIn(ExperimentalMaterial3Api::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zairvek.zairauto.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.playback.MediaIds
import com.zairvek.zairauto.playback.PlaybackService
import com.zairvek.zairauto.ui.Artwork
import com.zairvek.zairauto.ui.PlayerUiState
import com.zairvek.zairauto.ui.PlayerViewModel
import com.zairvek.zairauto.ui.formatTime
import java.util.Locale

@Composable
fun MiniPlayer(s: PlayerUiState, vm: PlayerViewModel, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(s.artworkUri, size = 44.dp, corner = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    s.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                PlayPauseIcon(s, vm, Modifier.size(44.dp))
                IconButton(onClick = { vm.next() }) { Icon(Icons.Rounded.SkipNext, "Siguiente") }
            }
            if (!s.isLive && s.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
    }
}

@Composable
private fun PlayPauseIcon(s: PlayerUiState, vm: PlayerViewModel, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (s.buffering) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp)
        IconButton(onClick = vm::togglePlay) {
            Icon(if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (s.isPlaying) "Pausa" else "Reproducir")
        }
    }
}

@Composable
fun PlayerScreen(vm: PlayerViewModel, s: PlayerUiState, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val favorites by Graph.library.favorites.collectAsStateWithLifecycle()
    val isFav = s.mediaId != null && favorites.any { it.mediaId == MediaIds.canonical(s.mediaId) }
    var showQueue by remember { mutableStateOf(false) }

    Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.85f), cs.background, cs.background)))
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar", modifier = Modifier.size(32.dp)) }
                    Text(
                        if (s.isLive) "EN DIRECTO" else "REPRODUCIENDO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Cola") }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    if (s.isVideo) {
                        VideoSurface(vm, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
                    } else {
                        val scale by animateFloatAsState(if (s.isPlaying) 1f else 0.88f, label = "art")
                        Artwork(s.artworkUri, size = side * 0.92f, corner = 28.dp, modifier = Modifier.scale(scale))
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        s.subtitle?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = vm::toggleFavoriteCurrent) {
                        Icon(
                            if (isFav) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            if (isFav) "Quitar de favoritos" else "Añadir a favoritos",
                            tint = if (isFav) cs.secondary else cs.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (s.isLive) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
                        Surface(color = cs.error, shape = RoundedCornerShape(50)) {
                            Text("● EN DIRECTO", color = cs.onError, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                } else {
                    SeekBar(s, vm)
                }

                s.error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall) }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.toggleShuffle() }) {
                        Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = if (s.shuffle) cs.primary else cs.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, "Anterior", modifier = Modifier.size(40.dp))
                    }
                    Box(contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = vm::togglePlay,
                            modifier = Modifier.size(80.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary),
                        ) {
                            Icon(if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (s.isPlaying) "Pausa" else "Reproducir", modifier = Modifier.size(44.dp))
                        }
                        if (s.buffering) CircularProgressIndicator(Modifier.size(88.dp), strokeWidth = 3.dp)
                    }
                    IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipNext, "Siguiente", modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { vm.cycleRepeat() }) {
                        Icon(
                            if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            "Repetir",
                            tint = if (s.repeatMode != Player.REPEAT_MODE_OFF) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.seekBack() }, enabled = !s.isLive) { Icon(Icons.Rounded.Replay10, "Atrás 10 segundos") }
                    SpeedChip(s, vm)
                    SleepChip(s, vm)
                    IconButton(onClick = { vm.seekForward() }, enabled = !s.isLive) { Icon(Icons.Rounded.Forward30, "Adelante 30 segundos") }
                }
            }
        }
    }

    if (showQueue) QueueSheet(s, vm) { showQueue = false }
}

@Composable
private fun SeekBar(s: PlayerUiState, vm: PlayerViewModel) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = s.durationMs.coerceAtLeast(1)
    val value = dragging ?: (s.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Column {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { vm.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = s.durationMs > 0,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime((value * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("-" + formatTime(s.durationMs - (value * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpeedChip(s: PlayerUiState, vm: PlayerViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(String.format(Locale.ROOT, "%.2gx", s.speed)) },
            leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp)) },
            enabled = !s.isLive,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { sp ->
                DropdownMenuItem(text = { Text(String.format(Locale.ROOT, "%.2fx", sp)) }, onClick = {
                    open = false
                    vm.setSpeed(sp)
                })
            }
        }
    }
}

@Composable
private fun SleepChip(s: PlayerUiState, vm: PlayerViewModel) {
    var open by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(vm.sleepRemainingMs(s)) }
    LaunchedEffect(s.sleepAtElapsed) {
        while (true) {
            remaining = vm.sleepRemainingMs(s)
            if (remaining <= 0) break
            kotlinx.coroutines.delay(1000)
        }
    }
    val label = when {
        s.sleepEndOfItem -> "Fin de pista"
        remaining > 0 -> formatTime(remaining)
        else -> "Dormir"
    }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = {
                Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp), tint = if (remaining > 0 || s.sleepEndOfItem) MaterialTheme.colorScheme.primary else Color.Unspecified)
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(15, 30, 45, 60, 90).forEach { m ->
                DropdownMenuItem(text = { Text("$m minutos") }, onClick = {
                    open = false
                    vm.setSleepTimer(m)
                })
            }
            DropdownMenuItem(text = { Text("Al terminar la pista") }, onClick = {
                open = false
                vm.setSleepTimer(PlaybackService.SLEEP_END_OF_ITEM)
            })
            if (remaining > 0 || s.sleepEndOfItem) {
                DropdownMenuItem(text = { Text("Desactivar") }, leadingIcon = { Icon(Icons.Rounded.Close, null) }, onClick = {
                    open = false
                    vm.setSleepTimer(0)
                })
            }
        }
    }
}

@Composable
private fun VideoSurface(vm: PlayerViewModel, modifier: Modifier) {
    val browser = vm.browser ?: return
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                player = browser
            }
        },
        onRelease = { it.player = null },
        modifier = modifier.background(Color.Black),
    )
}

@Composable
private fun QueueSheet(s: PlayerUiState, vm: PlayerViewModel, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (s.currentIndex - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Cola · ${s.queue.size} pistas",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyColumn(state = listState) {
            itemsIndexed(s.queue, key = { i, q -> "$i-${q.mediaId}" }) { i, q ->
                MediaRow(
                    title = q.title,
                    subtitle = q.subtitle,
                    artwork = null,
                    playing = i == s.currentIndex,
                    onClick = { vm.skipTo(i) },
                    trailing = {
                        if (i != s.currentIndex) {
                            IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Rounded.Close, "Quitar de la cola") }
                        }
                    },
                )
            }
        }
    }
}
