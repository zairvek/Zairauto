@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zairvek.zairauto.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairvek.zairauto.BuildConfig
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.data.ThemeMode
import com.zairvek.zairauto.playback.EqState
import com.zairvek.zairauto.ui.PlayerViewModel
import java.util.Locale

@Composable
fun SettingsScreen(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val s by Graph.settings.state.collectAsStateWithLifecycle()
    val eq by Graph.effects.state.collectAsStateWithLifecycle()
    var guide by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
            )
        }

        item {
            Section("Apariencia", Icons.Rounded.Palette) {
                Text("Tema", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val options = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro")
                    options.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.theme == mode,
                            onClick = { Graph.settings.update { it.copy(theme = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
                SwitchRow("Colores de tu fondo de pantalla", "Material You (Android 12+)", s.dynamicColor) { v -> Graph.settings.update { it.copy(dynamicColor = v) } }
                SwitchRow("Negro puro", "Ahorra batería en pantallas OLED con el tema oscuro", s.amoled) { v -> Graph.settings.update { it.copy(amoled = v) } }
            }
        }

        item {
            Section("Reproducción", Icons.Rounded.PlayCircle) {
                SwitchRow("Recordar posición", "Audiolibros, podcasts y vídeos de más de 15 min siguen donde los dejaste", s.rememberPosition) { v ->
                    Graph.settings.update { it.copy(rememberPosition = v) }
                }
                SwitchRow("Saltar silencios", "Recorta los silencios largos (ideal para podcasts)", s.skipSilence) { v ->
                    Graph.settings.update { it.copy(skipSilence = v) }
                }
                SwitchRow("Incluir vídeos", "En el coche se reproducen solo como audio", s.showVideos) { v ->
                    Graph.settings.update { it.copy(showVideos = v) }
                }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Velocidad", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.ROOT, "%.2fx", s.speed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = s.speed,
                    onValueChange = { v -> Graph.settings.update { it.copy(speed = (v * 20).toInt() / 20f) } },
                    valueRange = 0.5f..2.5f,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item { EqualizerSection(eq) }

        item {
            Section("Android Auto", Icons.Rounded.DirectionsCar) {
                SwitchRow("Carpetas en cuadrícula", "Muestra las carpetas como mosaico en la pantalla del coche", s.gridFolders) { v ->
                    Graph.settings.update { it.copy(gridFolders = v) }
                }
                ClickRow("Cómo activar Zairauto en Android Auto", "Guía paso a paso") { guide = true }
            }
        }

        item {
            Section("Biblioteca", Icons.Rounded.LibraryMusic) {
                ClickRow("Volver a escanear", "Detecta archivos nuevos en tus carpetas") {
                    Graph.scanner.invalidate()
                    vm.rescan()
                    Toast.makeText(ctx, "Biblioteca actualizada", Toast.LENGTH_SHORT).show()
                }
                ClickRow("Borrar historial", "Vacía la lista de recientes") {
                    Graph.library.clearRecents()
                    Toast.makeText(ctx, "Historial borrado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        item {
            Section("Acerca de", Icons.Rounded.Info) {
                Text(
                    "Zairauto ${BuildConfig.VERSION_NAME}\nReproductor multimedia para el coche inspirado en Fermata Auto, reescrito desde cero con Jetpack Compose y Media3.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (guide) AndroidAutoGuideDialog { guide = false }
}

@Composable
private fun EqualizerSection(eq: EqState) {
    val fx = Graph.effects
    Section("Ecualizador", Icons.Rounded.Equalizer) {
        if (!eq.available) {
            Text(
                "Reproduce algo para activar el ecualizador. Algunos móviles no permiten efectos de audio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Section
        }
        SwitchRow("Activar efectos", null, eq.enabled) { fx.setEnabled(it) }
        if (!eq.enabled) return@Section

        if (eq.presets.isNotEmpty()) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                eq.presets.forEachIndexed { i, name ->
                    FilterChip(selected = eq.preset == i, onClick = { fx.usePreset(i) }, label = { Text(name) })
                }
            }
        }
        eq.bandFreqsHz.forEachIndexed { i, hz ->
            val level = eq.levels.getOrElse(i) { 0 }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                Slider(
                    value = level.toFloat(),
                    onValueChange = { fx.setBand(i, it.toInt()) },
                    valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    String.format(Locale.ROOT, "%+.0f dB", level / 100f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
        if (eq.bassAvailable) {
            LabeledSlider("Graves", "${eq.bass / 10}%", eq.bass.toFloat(), 0f..1000f) { fx.setBass(it.toInt()) }
        }
        if (eq.loudnessAvailable) {
            LabeledSlider("Amplificar volumen", String.format(Locale.ROOT, "+%.1f dB", eq.loudness / 100f), eq.loudness.toFloat(), 0f..2000f) {
                fx.setLoudness(it.toInt())
            }
        }
        TextButton(onClick = fx::reset, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Restablecer") }
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(bottom = 8.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value = current, onValueChange = onChange, valueRange = range, modifier = Modifier.padding(horizontal = 16.dp))
}
