package com.zairvek.zairauto.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zairvek.zairauto.ui.Artwork

@Composable
fun MediaRow(
    title: String,
    subtitle: String?,
    artwork: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote,
    playing: Boolean = false,
    favorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(artwork, fallback = fallbackIcon)
            if (playing) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd).size(20.dp)) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (favorite != null && onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = if (favorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Guía paso a paso: el mayor dolor de cabeza de Fermata era conseguir que apareciera en Android Auto. */
@Composable
fun AndroidAutoGuideDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activar en Android Auto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Android Auto solo muestra apps instaladas desde Play Store. Para apps instaladas desde un APK hay que permitir \"fuentes desconocidas\" una única vez:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                GuideStep(1, "Abre los ajustes de Android Auto (botón de abajo).")
                GuideStep(2, "Baja hasta \"Versión\" y tócala 10 veces hasta que aparezca el aviso de modo desarrollador.")
                GuideStep(3, "Menú ⋮ (arriba a la derecha) → \"Ajustes de desarrollador\" → activa \"Fuentes desconocidas\".")
                GuideStep(4, "Vuelve atrás → \"Personalizar menú de aplicaciones\" y asegúrate de que Zairauto está marcada.")
                GuideStep(5, "Desconecta y vuelve a conectar el móvil al coche.")
                Text(
                    "Zairauto usa la API oficial de apps multimedia, así que no se rompe cuando Android Auto se actualiza.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { openAndroidAutoSettings(ctx) }) { Text("Abrir Android Auto") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun GuideStep(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private const val AA_PACKAGE = "com.google.android.projection.gearhead"

fun openAndroidAutoSettings(ctx: Context) {
    val candidates = listOfNotNull(
        Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(AA_PACKAGE),
        ctx.packageManager.getLaunchIntentForPackage(AA_PACKAGE),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$AA_PACKAGE")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$AA_PACKAGE")),
    )
    for (intent in candidates) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}
