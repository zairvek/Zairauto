package com.zairvek.zairauto.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bandFreqsHz: List<Int> = emptyList(),
    val levels: List<Int> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val presets: List<String> = emptyList(),
    val preset: Int = -1,
    val bass: Int = 0,
    val bassAvailable: Boolean = false,
    val loudness: Int = 0,
    val loudnessAvailable: Boolean = false,
)

/**
 * Ecualizador, refuerzo de graves y amplificador de volumen (útil en coches con poca potencia).
 * Se aplica sobre la sesión de audio del reproductor; la configuración se guarda y se reaplica.
 */
class AudioEffects(context: Context) {
    private val prefs = context.getSharedPreferences("effects", Context.MODE_PRIVATE)
    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loud: LoudnessEnhancer? = null

    private val _state = MutableStateFlow(
        EqState(
            enabled = prefs.getBoolean("enabled", false),
            preset = prefs.getInt("preset", -1),
            bass = prefs.getInt("bass", 0),
            loudness = prefs.getInt("loud", 0),
        )
    )
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun attach(sessionId: Int) {
        release()
        if (sessionId <= 0) return
        val s = _state.value
        eq = runCatching { Equalizer(0, sessionId) }.onFailure { Log.w(TAG, "Equalizer", it) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()?.takeIf { it.strengthSupported }
        loud = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

        val e = eq
        var levels = emptyList<Int>()
        var freqs = emptyList<Int>()
        var presets = emptyList<String>()
        var range = shortArrayOf(-1500, 1500)
        if (e != null) {
            val n = e.numberOfBands.toInt()
            range = e.bandLevelRange
            freqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            presets = (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }
            val saved = prefs.getString("levels", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
            levels = if (saved != null && saved.size == n) saved else List(n) { 0 }
        }
        _state.value = s.copy(
            available = e != null,
            bandFreqsHz = freqs,
            levels = levels,
            minLevel = range[0].toInt(),
            maxLevel = range[1].toInt(),
            presets = presets,
            bassAvailable = bassBoost != null,
            loudnessAvailable = loud != null,
        )
        apply()
    }

    private fun apply() {
        val s = _state.value
        runCatching {
            eq?.let { e ->
                if (s.preset >= 0 && s.preset < s.presets.size) e.usePreset(s.preset.toShort())
                else s.levels.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.toShort()) }
                e.enabled = s.enabled
            }
            bassBoost?.let { b ->
                b.setStrength(s.bass.toShort())
                b.enabled = s.enabled && s.bass > 0
            }
            loud?.let { l ->
                l.setTargetGain(s.loudness)
                l.enabled = s.enabled && s.loudness > 0
            }
        }.onFailure { Log.w(TAG, "apply", it) }
    }

    fun setEnabled(on: Boolean) = edit { it.copy(enabled = on) }

    fun setBand(index: Int, level: Int) = edit { s ->
        s.copy(levels = s.levels.toMutableList().also { it[index] = level }, preset = -1)
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        runCatching { e.usePreset(index.toShort()) }
        val levels = (0 until e.numberOfBands).map { e.getBandLevel(it.toShort()).toInt() }
        edit { it.copy(preset = index, levels = levels) }
    }

    fun reset() = edit { s -> s.copy(levels = List(s.levels.size) { 0 }, preset = -1, bass = 0, loudness = 0) }

    fun setBass(v: Int) = edit { it.copy(bass = v.coerceIn(0, 1000)) }

    fun setLoudness(v: Int) = edit { it.copy(loudness = v.coerceIn(0, 2000)) }

    private fun edit(block: (EqState) -> EqState) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putInt("preset", s.preset)
            .putString("levels", s.levels.joinToString(","))
            .putInt("bass", s.bass)
            .putInt("loud", s.loudness)
            .apply()
        apply()
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loud?.release() }
        eq = null
        bassBoost = null
        loud = null
    }

    private companion object {
        const val TAG = "AudioEffects"
    }
}
