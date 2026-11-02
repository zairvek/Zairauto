#pragma once
#include <cstdint>

// Update from: https://github.com/a2x/cs2-dumper (re-dump each patch)
namespace off {
    // client.dll
    inline constexpr uintptr_t dwEntityList  = 0x19DEB88;
    inline constexpr uintptr_t dwViewMatrix  = 0x19E2E60;
    inline constexpr uintptr_t dwLocalPlayer = 0x1843A58; // CCSPlayerController*

    namespace pawn {
        inline constexpr uintptr_t m_iHealth        = 0x344;  // int32
        inline constexpr uintptr_t m_iTeamNum       = 0x3CB;  // uint8
        inline constexpr uintptr_t m_lifeState      = 0x338;  // uint8, 0=alive
        inline constexpr uintptr_t m_pGameSceneNode = 0x328;  // CGameSceneNode*
    }
    namespace scene {
        inline constexpr uintptr_t m_vecAbsOrigin = 0xD8;  // Vec3 (foot pos)
        inline constexpr uintptr_t m_bDormant     = 0xE9;  // uint8
    }
    namespace ctrl {
        inline constexpr uintptr_t m_hPlayerPawn  = 0x7E4; // CHandle<C_CSPlayerPawn>
        inline constexpr uintptr_t m_szPlayerName = 0x650; // CUtlString (char* at offset 0)
    }
}
