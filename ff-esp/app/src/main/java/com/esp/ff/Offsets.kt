package com.esp.ff

// ── Cómo llenar este archivo ──────────────────────────────────────────────
//
// 1. Correr tools/dump_parser.py → genera este archivo automáticamente.
//    Si los nombres de clase no matchean, completar manualmente con dump.cs.
//
// 2. Para los TypeInfo offsets, correr tools/frida_statics.js.
//    Output: "FightingFighterManager TypeInfo: 0x..."  ← ese valor va aquí.
//
// 3. Para NATIVE_TRANSFORM_POS: freezar XY en GameGuardian mientras te movés
//    y hacer pointer scan desde la base del jugador.
//
// ─────────────────────────────────────────────────────────────────────────

const val FF_PACKAGE   = "com.dts.freefireth"  // MAX = com.dts.freefiremax
const val GAMEASSEMBLY = "libGameAssembly.so"

// Offset de static_fields dentro de Il2CppClass (arm64).
// Unity 2019.4 = 0xB8.  Si no funciona probar 0xC0, 0xD0.
const val IL2CPP_STATIC_FIELDS = 0xB8L

object Off {

    // ── TypeInfo offsets — libBase + X = Il2CppClass* ────────────────────
    // Fuente: frida_statics.js output ó il2cpp.h comentario "TypeInfo: 0x..."
    const val PM_TYPE_INFO     = 0x0L  // FILL: FightingFighterManager (u otro player manager)
    const val CAM_TYPE_INFO    = 0x0L  // FILL: BaseCameraController (u otro camera ctrl)

    // ── Static field offsets dentro del StaticFields struct ───────────────
    // Fuente: dump.cs bloque "// Static fields:" → valor // 0xXX
    const val PM_STATIC_INSTANCE  = 0x0L  // FILL: m_pInstance o similar (manager singleton)
    const val CAM_STATIC_INSTANCE = 0x0L  // FILL: camera controller singleton

    // ── PlayerManager → player list ────────────────────────────────────────
    // Fuente: dump.cs, campo List<FightingFighter> dentro del manager
    const val PM_PLAYER_LIST      = 0x0L  // FILL: offset del List<> dentro del manager
    const val LIST_ITEMS          = 0x10L // List<T>._items (Unity estándar, no cambiar)
    const val LIST_SIZE           = 0x18L // List<T>._size

    // ── Per-player ──────────────────────────────────────────────────────────
    // Fuente: dump.cs, clase FightingFighter (o la que matcheó)
    const val PLAYER_HP           = 0x0L  // FILL: float currentHp
    const val PLAYER_HP_MAX       = 0x0L  // FILL: float maxHp
    const val PLAYER_IS_ALIVE     = 0x0L  // FILL: bool isAlive / !isDead
    const val PLAYER_IS_ENEMY     = 0x0L  // FILL: bool isEnemy (o usar PLAYER_TEAM)
    const val PLAYER_TEAM         = 0x0L  // FILL: int teamId (alternativa a IS_ENEMY)
    const val PLAYER_NAME_PTR     = 0x0L  // FILL: System.String* (playerName)

    // Opción A: el script guarda un Transform cachedado como campo
    const val PLAYER_TRANSFORM    = 0x0L  // FILL: Transform component ref (campo en el script)

    // Opción B: el script tiene Vector3 de posición directo (más raro pero posible)
    const val PLAYER_POS_DIRECT   = 0x0L  // FILL: Vector3 worldPosition (si existe)

    // ── Unity Component / Transform ─────────────────────────────────────────
    // Constante Unity arm64: Component.m_CachedPtr = 0x10
    const val COMPONENT_NATIVE    = 0x10L
    // Offset del Vec3 dentro del native Transform object.
    // Valores a probar: 0x54, 0x64, 0x78, 0x90
    const val NATIVE_POS          = 0x54L  // FILL/verificar

    // ── Camera controller ───────────────────────────────────────────────────
    // Fuente: dump.cs, clase BaseCameraController
    const val CAM_YAW             = 0x0L  // FILL: float yaw (horizontal angle)
    const val CAM_PITCH           = 0x0L  // FILL: float pitch (vertical angle)
    // VP matrix float[16] dentro del native camera para W2S.
    // Si es 0: el overlay calcula W2S desde rotación+posición (menos preciso).
    const val NATIVE_CAM_VP       = 0x0L  // FILL opcional
}
