package com.esp.ff

object MemReader {
    init { System.loadLibrary("memread") }

    // --- process attach ---
    external fun findPid(pkgName: String): Int
    external fun openMem(pid: Int): Boolean
    external fun getModuleBase(pid: Int, modName: String): Long

    // --- primitive reads ---
    external fun readInt(addr: Long): Int
    external fun readLong(addr: Long): Long
    external fun readFloat(addr: Long): Float
    external fun readFloats(addr: Long, count: Int): FloatArray
    external fun readString(addr: Long): String         // raw char*
    external fun readUnityString(strPtr: Long): String  // System.String (UTF-16)

    // --- convenience ---
    fun readPtr(addr: Long): Long = readLong(addr)
    fun readBool(addr: Long): Boolean = readInt(addr) != 0
    fun readVec3(addr: Long): Triple<Float, Float, Float> {
        val f = readFloats(addr, 3)
        return Triple(f[0], f[1], f[2])
    }
}
