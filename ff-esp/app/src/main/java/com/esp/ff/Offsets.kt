package com.esp.ff

// ─────────────────────────────────────────────────────────────
// Dump offsets with Il2CppDumper:
//   adb root
//   adb pull /data/app/$(adb shell pm path com.dts.freefireth | cut -d: -f2 | xargs dirname)/lib/arm64/libGameAssembly.so .
//   adb pull /data/app/.../assets/bin/Data/Managed/Metadata/global-metadata.dat .
//   Il2CppDumper.exe libGameAssembly.so global-metadata.dat out/
// Then grep out/dump.cs for the class/field names below.
// ─────────────────────────────────────────────────────────────

const val FF_PACKAGE = "com.dts.freefireth"       // Global; MAX = com.dts.freefiremax
const val GAMEASSEMBLY = "libGameAssembly.so"

object Off {
    // ── Static class pointers ─────────────────────────────────
    // Il2CppDumper: "TypeDefinitionIndex" next to class name in dump.il
    // or use: grep -n "FightingFighterManager" out/dump.cs
    //         then find the static field offset in the same block.

    // Offset within libGameAssembly.so to the static-fields pointer of the player manager.
    // Look for: class FightingFighterManager (or BattleRoyalePlayerManager) → static fields
    const val PLAYER_MANAGER_STATIC = 0x0L          // FILL: libBase + this = &staticFields ptr

    // ── Player Manager → Player List ─────────────────────────
    const val PM_PLAYER_LIST         = 0x20L         // FILL: List<FightingFighter> or Array
    const val LIST_ITEMS             = 0x10L         // List._items  → object[]
    const val LIST_SIZE              = 0x18L         // List._size   → int32

    // ── Per-player object ─────────────────────────────────────
    // From dump.cs: class FightingFighter (or CharacterState)
    const val PLAYER_IS_ALIVE        = 0x00L         // FILL: bool
    const val PLAYER_IS_ENEMY        = 0x00L         // FILL: bool  (or derive from team id)
    const val PLAYER_HP              = 0x00L         // FILL: float or int
    const val PLAYER_HP_MAX          = 0x00L         // FILL: float or int
    const val PLAYER_NAME_PTR        = 0x00L         // FILL: System.String*

    // Unity Component → Transform
    const val COMPONENT_TRANSFORM    = 0x10L         // UnityEngine.Component::m_CachedPtr offset
    // Native Transform → world position (varies by Unity version)
    // In Unity 2019.4: Transform → TransformData* at +0x38 → localPosition Vec3 at +0x00
    // Check by freezing a known XYZ coord and scanning with GDB/Frida.
    const val TRANSFORM_CACHED_PTR   = 0x10L         // Component.m_CachedPtr → native obj
    const val NATIVE_TRANSFORM_POS   = 0x54L         // FILL: Vec3 (x,y,z floats) in native obj

    // ── Camera VP matrix ─────────────────────────────────────
    // Unity Camera.main → m_CachedPtr → native camera → VP matrix
    // In dump.cs: class Camera, look for "ViewProjectionMatrix" getter
    // Or: Camera static field "current" / singleton instance
    const val CAMERA_STATIC          = 0x0L          // FILL: libBase + this = Camera singleton ptr
    const val CAMERA_CACHED_PTR      = 0x10L         // Component.m_CachedPtr
    const val NATIVE_CAMERA_VP       = 0x00L         // FILL: float[16] view-projection matrix
}
