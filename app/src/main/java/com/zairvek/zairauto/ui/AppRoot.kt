package com.zairvek.zairauto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairvek.zairauto.Graph
import com.zairvek.zairauto.ui.screens.HomeScreen
import com.zairvek.zairauto.ui.screens.LibraryScreen
import com.zairvek.zairauto.ui.screens.MiniPlayer
import com.zairvek.zairauto.ui.screens.PlayerScreen
import com.zairvek.zairauto.ui.screens.RadioScreen
import com.zairvek.zairauto.ui.screens.SettingsScreen
import com.zairvek.zairauto.ui.theme.ZairTheme

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Inicio", Icons.Outlined.Home, Icons.Rounded.Home),
    LIBRARY("Biblioteca", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    RADIO("Radio", Icons.Outlined.Radio, Icons.Rounded.Radio),
    SETTINGS("Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun ZairApp(vm: PlayerViewModel) {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    ZairTheme(settings) {
        val player by vm.state.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
        var playerOpen by rememberSaveable { mutableStateOf(false) }
        val stateHolder = rememberSaveableStateHolder()

        BackHandler(enabled = playerOpen) { playerOpen = false }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    Column {
                        AnimatedVisibility(
                            visible = player.hasMedia,
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ) {
                            MiniPlayer(player, vm, onOpen = { playerOpen = true })
                        }
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    // Conserva el estado de cada pestaña (carpeta abierta, búsqueda...) al cambiar de una a otra.
                    stateHolder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(vm, player, onOpenPlayer = { playerOpen = true }, onGoTo = { tab = it })
                        Tab.LIBRARY -> LibraryScreen(vm)
                        Tab.RADIO -> RadioScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                    }
                }
            }

            AnimatedVisibility(
                visible = playerOpen && player.hasMedia,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                PlayerScreen(vm, player, onClose = { playerOpen = false })
            }
        }
    }
}
