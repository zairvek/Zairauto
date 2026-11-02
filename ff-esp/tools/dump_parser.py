"""
dump_parser.py — parsea dump.cs + il2cpp.h de Il2CppDumper y genera Offsets.kt listo.

Uso:
  python dump_parser.py  --dump out/dump.cs  --il2cpp out/il2cpp.h  --out Offsets.kt

Prerequisitos:
  1. adb root
  2. adb pull $(adb shell pm path com.dts.freefireth | cut -d: -f2)  freefire.apk
  3. descomprimir: lib/arm64-v8a/libGameAssembly.so
                   assets/bin/Data/Managed/Metadata/global-metadata.dat
  4. Il2CppDumper.exe libGameAssembly.so global-metadata.dat out/
  5. python dump_parser.py --dump out/dump.cs --il2cpp out/il2cpp.h
"""

import re, argparse, sys
from pathlib import Path

# ── nombres de clase candidatos ──────────────────────────────────────────────
PM_NAMES     = ["FightingFighterManager","BattleRoyalePlayerManager","FighterManager",
                "GamePlayerManager","BattleManager","CharacterManager"]
PLAYER_NAMES = ["FightingFighter","BattleCharacter","BaseCharacter","CharacterState",
                "SelfFighter","PlayerCharacter"]
CAM_NAMES    = ["BaseCameraController","ThirdPersonCameraController","CameraController",
                "CameraManager","FightingCameraController","GameCameraController"]
LOCAL_NAMES  = ["LocalPlayerManager","SelfManager","LocalFighter"]

# ── nombres de campo por categoría ───────────────────────────────────────────
FIELD_HP         = re.compile(r"(?i)^(m_f?hp|currenthp|hp|currhp|health|m_fhpnow)$")
FIELD_HP_MAX     = re.compile(r"(?i)^(m_f?maxhp|maxhp|hpmax|maxhealth|m_fhpmax)$")
FIELD_ALIVE      = re.compile(r"(?i)^(m_balive|isalive|balive|alive|m_bisdead|isdead)$")
FIELD_ENEMY      = re.compile(r"(?i)^(m_bisenemy|isenemy|bisenemy|isenemy|enemy)$")
FIELD_NAME       = re.compile(r"(?i)^(m_strname|playername|name|m_nickname|nickname|m_name)$")
FIELD_TEAM       = re.compile(r"(?i)^(m_iteam|teamid|team|m_team|campid)$")
FIELD_TRANSFORM  = re.compile(r"(?i)^(transform|m_transform|cachedtransform|m_cachedtransform)$")
FIELD_POS        = re.compile(r"(?i)^(position|pos|m_pos|worldpos|m_worldpos|worldposition)$")
FIELD_LIST       = re.compile(r"(?i)^(m_lstfighter|fighterlist|playerlist|m_listfighter|allplayers|players|m_lstcharacter)$")
FIELD_INSTANCE   = re.compile(r"(?i)^(m_pinstance|instance|m_instance|_instance|singleton|m_singleton|s_instance)$")
FIELD_YAW        = re.compile(r"(?i)^(yaw|m_fyaw|horizontalangle|m_horizontalangle|m_fyawangle|_yaw|yawangle)$")
FIELD_PITCH      = re.compile(r"(?i)^(pitch|m_fpitch|verticalangle|m_verticalangle|m_fpitchangle|_pitch|pitchangle)$")

# ── parser dump.cs ─────────────────────────────────────────────────────────

class ClassInfo:
    def __init__(self, name):
        self.name   = name
        self.fields = {}        # field_name → offset (hex string)
        self.static_fields = {}

    def add_field(self, name, offset, is_static):
        d = self.static_fields if is_static else self.fields
        try:
            d[name] = int(offset, 16)
        except ValueError:
            pass


def parse_dump(path: str) -> dict[str, ClassInfo]:
    classes: dict[str, ClassInfo] = {}
    current: ClassInfo | None = None
    in_static_block = False

    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            line = line.rstrip()

            # new class
            m = re.match(r'\s*(?:public|private|internal|sealed|abstract)?\s*(?:class|struct)\s+(\w+)', line)
            if m:
                current = ClassInfo(m.group(1))
                classes[current.name] = current
                in_static_block = False
                continue

            if current is None:
                continue

            # static block marker
            if "// Static fields" in line:
                in_static_block = True
                continue
            if "// Methods" in line or "// Properties" in line:
                in_static_block = False
                continue
            # end of class
            if re.match(r'^}', line):
                in_static_block = False
                continue

            # field with offset comment: SomeType fieldName; // 0xXX
            m = re.match(r'\s*(?:public|private|protected|internal)?\s*(?:static\s+)?(?:readonly\s+)?\S+\s+(\w+)\s*;\s*//\s*(0x[0-9a-fA-F]+)', line)
            if m:
                fname, foff = m.group(1), m.group(2)
                is_static = "static" in line.split(";")[0]
                current.add_field(fname, foff, is_static or in_static_block)

    return classes


# ── parser il2cpp.h (extrae TypeInfo offsets) ─────────────────────────────────

def parse_il2cpp_h(path: str) -> dict[str, int]:
    """Returns classname → TypeInfo offset (int)."""
    typeinfos: dict[str, int] = {}
    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            # // TypeInfo: 0x1234560   or  // Offset: 0x...
            m = re.search(r'TypeInfo:\s*(0x[0-9a-fA-F]+)', line)
            if m:
                offset = int(m.group(1), 16)
                # Next struct definition after this line carries the class name
                # We associate the last TypeInfo seen with the next struct
                _pending_offset = offset
                continue

            m2 = re.match(r'struct\s+(\w+)\s*[:{]', line)
            if m2 and '_pending_offset' in dir():
                typeinfos[m2.group(1)] = _pending_offset
                del _pending_offset

    return typeinfos


def best_match(names: list[str], classes: dict[str, ClassInfo]) -> ClassInfo | None:
    for n in names:
        if n in classes:
            return classes[n]
    # fuzzy: case-insensitive substring
    nl = [n.lower() for n in names]
    for cn, ci in classes.items():
        if any(p in cn.lower() for p in nl):
            return ci
    return None


def find_field(ci: ClassInfo, pattern: re.Pattern) -> tuple[str, int] | None:
    for name, off in {**ci.fields, **ci.static_fields}.items():
        if pattern.match(name):
            return name, off
    return None


def gen_kotlin(pm, player, cam, local, typeinfos) -> str:
    def ti(cls): return f"0x{typeinfos.get(cls.name, 0):X}L" if cls else "0x0L  // FILL"
    def fi(cls, pat, label):
        if cls:
            r = find_field(cls, pat)
            if r: return f"0x{r[1]:X}L  // {r[0]}"
        return f"0x0L  // FILL: {label}"

    pm_list    = fi(pm,     FIELD_LIST,      "player list field")
    pm_inst    = fi(pm,     FIELD_INSTANCE,  "instance/singleton field")
    pl_hp      = fi(player, FIELD_HP,        "current HP float")
    pl_hpmax   = fi(player, FIELD_HP_MAX,    "max HP float")
    pl_alive   = fi(player, FIELD_ALIVE,     "isAlive bool")
    pl_enemy   = fi(player, FIELD_ENEMY,     "isEnemy bool")
    pl_name    = fi(player, FIELD_NAME,      "player name System.String*")
    pl_team    = fi(player, FIELD_TEAM,      "team id int")
    pl_tf      = fi(player, FIELD_TRANSFORM, "Transform component")
    pl_pos     = fi(player, FIELD_POS,       "cached Vector3 position (if exists)")
    cam_yaw    = fi(cam,    FIELD_YAW,       "camera yaw float")
    cam_pitch  = fi(cam,    FIELD_PITCH,     "camera pitch float")

    return f'''package com.esp.ff

// GENERATED by dump_parser.py — verifica cada valor antes de usar.
// Il2CppDumper version Unity: actualiza IL2CPP_STATIC_FIELDS_OFF si no funciona.

const val FF_PACKAGE   = "com.dts.freefireth"
const val GAMEASSEMBLY = "libGameAssembly.so"

// Unity 2019.4 arm64: offset de static_fields dentro de Il2CppClass.
// Prueba 0xB8; si falla, prueba 0xC0 o 0xD0.
const val IL2CPP_STATIC_FIELDS_OFF = 0xB8L

object Off {{

    // ── TypeInfo offsets (libBase + X = &Il2CppClass*) ─────────────────
    // Fuente: il2cpp.h comentario "TypeInfo: 0x..."
    const val PM_TYPE_INFO     = {ti(pm)}    // PlayerManager
    const val PLAYER_TYPE_INFO = {ti(player)}  // FightingFighter / player class
    const val CAM_TYPE_INFO    = {ti(cam)}   // CameraController

    // ── Static field offsets dentro de StaticFields struct ──────────────
    // Fuente: dump.cs "// Static fields:" bloque, valor // 0xXX
    const val PM_STATIC_INSTANCE  = {pm_inst}
    const val CAM_STATIC_INSTANCE = 0x0L  // FILL: camera controller singleton

    // ── PlayerManager → player list ─────────────────────────────────────
    const val PM_PLAYER_LIST      = {pm_list}
    const val LIST_ITEMS          = 0x10L   // List<T>._items  (Unity estándar)
    const val LIST_SIZE           = 0x18L   // List<T>._size

    // ── Per-player fields ────────────────────────────────────────────────
    const val PLAYER_IS_ALIVE     = {pl_alive}
    const val PLAYER_IS_ENEMY     = {pl_enemy}
    const val PLAYER_TEAM         = {pl_team}
    const val PLAYER_HP           = {pl_hp}
    const val PLAYER_HP_MAX       = {pl_hpmax}
    const val PLAYER_NAME_PTR     = {pl_name}
    const val PLAYER_TRANSFORM    = {pl_tf}
    const val PLAYER_POS_CACHED   = {pl_pos}   // si el script cachea posición directo

    // ── Unity Component / Transform ──────────────────────────────────────
    // Component.m_CachedPtr = 0x10 (constante Unity arm64)
    const val COMPONENT_NATIVE_PTR = 0x10L
    // Offset del Vec3 world position dentro del native Transform object
    // Prueba: 0x54, 0x78, 0x90 (freezar XY en GG/CE y hacer pointer scan)
    const val NATIVE_TRANSFORM_POS = 0x54L  // FILL/verificar

    // ── Camera controller ────────────────────────────────────────────────
    const val CAM_YAW             = {cam_yaw}
    const val CAM_PITCH           = {cam_pitch}
    // VP matrix (float[16]) dentro del native camera object para W2S
    // Si no lo encontrás, el ESP recalcula desde rotación+FOV
    const val NATIVE_CAM_VP       = 0x0L   // FILL opcional
}}
'''


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump",   required=True, help="ruta a dump.cs")
    ap.add_argument("--il2cpp", default=None,  help="ruta a il2cpp.h (opcional)")
    ap.add_argument("--out",    default="Offsets.kt", help="archivo de salida")
    args = ap.parse_args()

    print(f"[*] Parseando {args.dump}...")
    classes = parse_dump(args.dump)
    print(f"    {len(classes)} clases encontradas.")

    typeinfos = {}
    if args.il2cpp:
        print(f"[*] Parseando {args.il2cpp}...")
        typeinfos = parse_il2cpp_h(args.il2cpp)
        print(f"    {len(typeinfos)} TypeInfo offsets.")

    pm     = best_match(PM_NAMES,     classes)
    player = best_match(PLAYER_NAMES, classes)
    cam    = best_match(CAM_NAMES,    classes)
    local  = best_match(LOCAL_NAMES,  classes)

    print(f"[*] PlayerManager  → {pm.name     if pm     else 'NO ENCONTRADO'}")
    print(f"[*] PlayerClass    → {player.name if player else 'NO ENCONTRADO'}")
    print(f"[*] CameraCtrl     → {cam.name    if cam    else 'NO ENCONTRADO'}")

    if player:
        print("\n[*] Campos del player encontrados:")
        for pat, label in [(FIELD_HP,"hp"),(FIELD_ALIVE,"alive"),(FIELD_TRANSFORM,"transform"),(FIELD_POS,"pos")]:
            r = find_field(player, pat)
            print(f"    {label:12s} → {('0x%X' % r[1] + ' (' + r[0] + ')') if r else 'no encontrado'}")

    kt = gen_kotlin(pm, player, cam, local, typeinfos)
    Path(args.out).write_text(kt, encoding="utf-8")
    print(f"\n[+] Offsets.kt generado → {args.out}")
    print("    Copia al proyecto: ff-esp/app/src/main/java/com/esp/ff/Offsets.kt")
    print("    Verifica los valores marcados FILL antes de buildear.")

if __name__ == "__main__":
    main()
