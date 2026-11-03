package com.esp.ff

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.*

class MemReader {

    private var rCh:  FileChannel? = null
    private var rwCh: FileChannel? = null

    // ── process ──────────────────────────────────────────────────────────────
    fun findPid(pkgName: String): Int {
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            try {
                val cmd = File(dir, "cmdline").readText().trimEnd('\u0000')
                if (cmd == pkgName || cmd.startsWith("$pkgName:")) return pid
            } catch (_: Exception) {}
        }
        return -1
    }

    fun openMem(pid: Int): Boolean = try {
        rCh  = RandomAccessFile("/proc/$pid/mem", "r" ).channel
        rwCh = RandomAccessFile("/proc/$pid/mem", "rw").channel
        true
    } catch (_: Exception) { false }

    fun getModuleBase(pid: Int, modName: String): Long {
        try {
            File("/proc/$pid/maps").forEachLine { line ->
                if (line.contains(modName) && line.contains("r-xp")) {
                    val addr = line.substringBefore('-').toLongOrNull(16)
                    if (addr != null) return addr
                }
            }
        } catch (_: Exception) {}
        return 0L
    }

    // ── low-level read ────────────────────────────────────────────────────────
    private fun buf(addr: Long, size: Int): ByteBuffer? {
        val ch = rCh ?: return null
        val b  = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        return try { ch.read(b, addr); b.flip(); b } catch (_: Exception) { null }
    }

    // ── reads ────────────────────────────────────────────────────────────────
    fun readInt(addr: Long):   Int   = buf(addr, 4)?.int   ?: 0
    fun readLong(addr: Long):  Long  = buf(addr, 8)?.long  ?: 0L
    fun readFloat(addr: Long): Float = buf(addr, 4)?.float ?: 0f
    fun readPtr(addr: Long):   Long  = readLong(addr)
    fun readBool(addr: Long):  Boolean = readInt(addr) != 0

    fun readFloats(addr: Long, count: Int): FloatArray {
        val b = buf(addr, count * 4) ?: return FloatArray(count)
        return FloatArray(count) { b.float }
    }

    fun readUnityString(strPtr: Long): String {
        val len = readInt(strPtr + 0x10).coerceIn(0, 64)
        if (len <= 0) return ""
        val b = buf(strPtr + 0x14, len * 2) ?: return ""
        return String(CharArray(len) { b.short.toInt().and(0xFFFF).toChar() })
    }

    // ── writes ────────────────────────────────────────────────────────────────
    fun writeFloat(addr: Long, value: Float): Boolean {
        val ch = rwCh ?: return false
        val b  = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).also { it.putFloat(value); it.flip() }
        return try { ch.write(b, addr); true } catch (_: Exception) { false }
    }

    fun writeInt(addr: Long, value: Int): Boolean {
        val ch = rwCh ?: return false
        val b  = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).also { it.putInt(value); it.flip() }
        return try { ch.write(b, addr); true } catch (_: Exception) { false }
    }

    // ── aimbot helpers ────────────────────────────────────────────────────────
    fun findBestTarget(
        positions: FloatArray, count: Int,
        cx: Float, cy: Float, cz: Float,
        fx: Float, fy: Float, fz: Float,
        fovDeg: Float
    ): Int {
        val fovCos = cos(Math.toRadians(fovDeg.toDouble())).toFloat()
        var bestIdx = -1
        var bestDot = fovCos
        for (i in 0 until count) {
            val dx = positions[i*3]   - cx
            val dy = positions[i*3+1] - cy
            val dz = positions[i*3+2] - cz
            val len = sqrt(dx*dx + dy*dy + dz*dz)
            if (len < 0.001f) continue
            val dot = (dx*fx + dy*fy + dz*fz) / len
            if (dot > bestDot) { bestDot = dot; bestIdx = i }
        }
        return bestIdx
    }

    fun calcAim(
        camX: Float, camY: Float, camZ: Float,
        tgX: Float,  tgY: Float,  tgZ: Float,
        curYaw: Float, curPitch: Float, smooth: Float
    ): FloatArray {
        val dx = tgX - camX; val dy = tgY - camY; val dz = tgZ - camZ
        val dist2d = sqrt(dx*dx + dz*dz)
        val tYaw   = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        val tPitch = Math.toDegrees(atan2((-dy).toDouble(), dist2d.toDouble())).toFloat()

        var dY = tYaw   - curYaw;   while (dY >  180f) dY -= 360f; while (dY < -180f) dY += 360f
        var dP = tPitch - curPitch; while (dP >  180f) dP -= 360f; while (dP < -180f) dP += 360f
        return floatArrayOf(curYaw + dY * smooth, curPitch + dP * smooth)
    }
}
