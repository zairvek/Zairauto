package com.esp.ff

// Dump con Il2CppDumper → grep dump.cs por los nombres de clase abajo.
// adb pull libGameAssembly.so + global-metadata.dat del APK, luego:
//   Il2CppDumper.exe libGameAssembly.so global-metadata.dat out/

const val FF_PACKAGE   = "com.dts.freefireth"   // MAX = com.dts.freefiremax
const val GAMEASSEMBLY = "libGameAssembly.so"

object Off {

    // ── Punteros estáticos (libBase + X = dirección del static fields ptr) ───
    // dump.cs: buscar "class FightingFighterManager" o "BattleRoyalePlayerManager"
    // luego el campo static: "FightingFighterManager m_pInstance" o similar
    const val PLAYER_MANAGER_STATIC = 0x0L          // FILL

    // dump.cs: buscar "class LocalPlayer" o "SelfFighter" o campo static en FightingFighterManager
    const val LOCAL_PLAYER_STATIC   = 0x0L          // FILL

    // dump.cs: "class BaseCameraController" o "ThirdPersonCameraController"
    const val CAMERA_CTRL_STATIC    = 0x0L          // FILL

    // ── Player List ──────────────────────────────────────────────────────────
    const val PM_PLAYER_LIST        = 0x20L          // FILL: offset a List<Fighter> dentro del manager
    const val LIST_ITEMS            = 0x10L          // List<T>._items → object[]
    const val LIST_SIZE             = 0x18L          // List<T>._size  → int32

    // ── Per-player (FightingFighter / CharacterState) ────────────────────────
    // dump.cs: buscar la clase del fighter, sus fields con [FieldOffset(0xXX)]
    const val PLAYER_IS_ALIVE       = 0x0L           // FILL: bool
    const val PLAYER_IS_ENEMY       = 0x0L           // FILL: bool  (o derivar de team != local team)
    const val PLAYER_HP             = 0x0L           // FILL: float
    const val PLAYER_HP_MAX         = 0x0L           // FILL: float
    const val PLAYER_NAME_PTR       = 0x0L           // FILL: System.String*
    const val PLAYER_TRANSFORM_COMP = 0x10L          // Component.m_CachedPtr (estándar Unity)

    // ── Transform (nativo Unity) ─────────────────────────────────────────────
    // Offset dentro del native transform object hacia la posición world (Vec3 x,y,z floats)
    // Unity 2019.4 arm64: prueba 0x54, 0x78, 0x90 — congelar XY en CE-Android y buscar
    const val NATIVE_TRANSFORM_POS  = 0x54L          // FILL: Vec3 world position

    // ── Cámara / ángulos de apuntado ─────────────────────────────────────────
    // dump.cs: BaseCameraController o similar, buscar fields "yaw" / "pitch" / "eulerAngles"
    // O busca UnityEngine.Camera → m_CachedPtr → native cam → yaw/pitch
    const val CAM_CACHED_PTR        = 0x10L          // Component.m_CachedPtr
    const val NATIVE_CAM_VP         = 0x0L           // FILL: float[16] view-proj matrix (para W2S)

    // Para el aimbot: ángulos escritos en el controlador de cámara
    // dump.cs: buscar "m_fYawAngle", "yaw", "m_horizontalAngle" en el camera controller
    const val CAM_CTRL_CACHED_PTR   = 0x10L
    const val CAM_CTRL_YAW          = 0x0L           // FILL: float (write aquí para aim H)
    const val CAM_CTRL_PITCH        = 0x0L           // FILL: float (write aquí para aim V)
}
