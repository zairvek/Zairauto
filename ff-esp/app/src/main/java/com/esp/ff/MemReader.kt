package com.esp.ff

object MemReader {
    init { System.loadLibrary("memread") }

    // ── proceso ──────────────────────────────────────────────
    external fun findPid(pkgName: String): Int
    external fun openMem(pid: Int): Boolean          // abre rdonly + rdwr
    external fun getModuleBase(pid: Int, modName: String): Long

    // ── lecturas ─────────────────────────────────────────────
    external fun readInt(addr: Long): Int
    external fun readLong(addr: Long): Long
    external fun readFloat(addr: Long): Float
    external fun readFloats(addr: Long, count: Int): FloatArray
    external fun readUnityString(strPtr: Long): String

    fun readPtr(addr: Long): Long = readLong(addr)
    fun readBool(addr: Long): Boolean = readInt(addr) != 0
    fun readVec3(addr: Long): FloatArray = readFloats(addr, 3)

    // ── escrituras ───────────────────────────────────────────
    external fun writeFloat(addr: Long, value: Float): Boolean
    external fun writeInt(addr: Long, value: Int): Boolean

    // ── aimbot ───────────────────────────────────────────────
    // positions: float[count*3] {x0,y0,z0, x1,y1,z1, ...} posiciones mundo de enemigos
    // cx/cy/cz: posición cámara,  fx/fy/fz: forward de la cámara
    // fovDeg: FOV del aimbot (angulo máximo desde crosshair)
    // retorna índice del mejor target o -1
    external fun findBestTarget(
        positions: FloatArray, count: Int,
        cx: Float, cy: Float, cz: Float,
        fx: Float, fy: Float, fz: Float,
        fovDeg: Float
    ): Int

    // retorna float[2] = {newYaw, newPitch} con suavizado aplicado
    external fun calcAim(
        camX: Float, camY: Float, camZ: Float,
        tgX:  Float, tgY:  Float, tgZ:  Float,
        curYaw: Float, curPitch: Float,
        smooth: Float
    ): FloatArray
}
