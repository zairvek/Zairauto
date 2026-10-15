package com.zairvek.zairauto.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val skipSilence: Boolean = false,
    val speed: Float = 1f,
    val rememberPosition: Boolean = true,
    val gridFolders: Boolean = false,
    val showVideos: Boolean = true,
    val setupDismissed: Boolean = false,
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val value get() = _state.value

    fun update(block: (SettingsState) -> SettingsState) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamic", s.dynamicColor)
            .putBoolean("amoled", s.amoled)
            .putBoolean("skipSilence", s.skipSilence)
            .putFloat("speed", s.speed)
            .putBoolean("rememberPos", s.rememberPosition)
            .putBoolean("grid", s.gridFolders)
            .putBoolean("videos", s.showVideos)
            .putBoolean("setupDismissed", s.setupDismissed)
            .apply()
    }

    private fun load() = SettingsState(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamic", true),
        amoled = prefs.getBoolean("amoled", false),
        skipSilence = prefs.getBoolean("skipSilence", false),
        speed = prefs.getFloat("speed", 1f),
        rememberPosition = prefs.getBoolean("rememberPos", true),
        gridFolders = prefs.getBoolean("grid", false),
        showVideos = prefs.getBoolean("videos", true),
        setupDismissed = prefs.getBoolean("setupDismissed", false),
    )
}
