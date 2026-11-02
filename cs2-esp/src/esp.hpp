#pragma once
#include "memory.hpp"
#include "offsets.hpp"
#include "math.hpp"
#include <imgui.h>
#include <vector>
#include <cstdio>

struct Player {
    Vec3 pos;       // foot (m_vecAbsOrigin)
    int  hp   = 0;
    int  team = 0;
    char name[64]{};
    bool valid = false;
};

// Read one player controller at entity-list slot i.
// Returns Player with valid=false if slot is empty/dead/dormant.
inline Player readPlayer(Memory& mem, uintptr_t eList, int i) {
    Player p{};

    uintptr_t chunk = mem.read<uintptr_t>(eList + 8 + (i >> 9) * 8);
    if (!chunk) return p;
    uintptr_t ctrl  = mem.read<uintptr_t>(chunk + (i & 0x1FF) * 0x78);
    if (!ctrl) return p;

    // Controller → pawn handle → pawn
    uint32_t  hPawn    = mem.read<uint32_t>(ctrl + off::ctrl::m_hPlayerPawn);
    int       pawnIdx  = (int)(hPawn & 0x7FFF);
    uintptr_t pChunk   = mem.read<uintptr_t>(eList + 8 + (pawnIdx >> 9) * 8);
    if (!pChunk) return p;
    uintptr_t pawn     = mem.read<uintptr_t>(pChunk + (pawnIdx & 0x1FF) * 0x78);
    if (!pawn) return p;

    // Alive check
    if (mem.read<uint8_t>(pawn + off::pawn::m_lifeState) != 0) return p;

    p.hp   = mem.read<int>(pawn    + off::pawn::m_iHealth);
    p.team = mem.read<uint8_t>(pawn + off::pawn::m_iTeamNum);
    if (p.hp <= 0 || p.hp > 100) return p;

    // Position
    uintptr_t sn = mem.read<uintptr_t>(pawn + off::pawn::m_pGameSceneNode);
    if (!sn) return p;
    if (mem.read<uint8_t>(sn + off::scene::m_bDormant)) return p;
    p.pos = mem.read<Vec3>(sn + off::scene::m_vecAbsOrigin);

    // Name: CUtlString → dereference pointer → read chars
    uintptr_t namePtr = mem.read<uintptr_t>(ctrl + off::ctrl::m_szPlayerName);
    mem.readBuf(namePtr, p.name, sizeof(p.name) - 1);

    p.valid = true;
    return p;
}

struct ESPCfg {
    bool enabled   = true;
    bool box       = true;
    bool healthBar = true;
    bool names     = true;
    bool distance  = true;
    bool teamCheck = true;
    float maxDist  = 120.f; // metres
};

inline void drawESP(const std::vector<Player>& players,
                    Vec3 localPos, int localTeam,
                    const float* vm, float sw, float sh,
                    const ESPCfg& cfg)
{
    if (!cfg.enabled) return;
    ImDrawList* dl = ImGui::GetBackgroundDrawList();

    for (const auto& p : players) {
        if (!p.valid) continue;
        if (cfg.teamCheck && p.team == localTeam) continue;

        float d = dist3d(localPos, p.pos);
        if (d > cfg.maxDist) continue;

        // foot + head screen positions (head ≈ foot + 72 HU)
        Vec2 sfoot, shead;
        Vec3 headPos = { p.pos.x, p.pos.y, p.pos.z + 72.f };
        if (!w2s(vm, p.pos,   sfoot, sw, sh)) continue;
        if (!w2s(vm, headPos, shead, sw, sh)) continue;

        float boxH = sfoot.y - shead.y;
        if (boxH < 4.f) continue;
        float boxW = boxH * 0.4f;
        float cx   = (sfoot.x + shead.x) * 0.5f;

        // T = orange/yellow, CT = red-ish
        ImU32 col = (p.team == 2)
            ? IM_COL32(255, 170, 40, 255)   // T
            : IM_COL32(255, 70,  70, 255);  // CT

        if (cfg.box) {
            dl->AddRect({ cx - boxW*0.5f, shead.y },
                        { cx + boxW*0.5f, sfoot.y }, col, 0.f, 0, 1.5f);
        }

        if (cfg.healthBar) {
            float bx  = cx - boxW*0.5f - 5.f;
            float fill = boxH * (p.hp / 100.f);
            dl->AddRectFilled({ bx-3.f, shead.y }, { bx, sfoot.y },
                              IM_COL32(20, 20, 20, 180));
            int r = (int)(255 * (1.f - p.hp/100.f));
            int g = (int)(255 * (p.hp/100.f));
            dl->AddRectFilled({ bx-3.f, sfoot.y - fill }, { bx, sfoot.y },
                              IM_COL32(r, g, 0, 220));
        }

        if (cfg.names && p.name[0]) {
            ImVec2 ts = ImGui::CalcTextSize(p.name);
            dl->AddText({ cx - ts.x*0.5f, shead.y - ts.y - 2.f },
                        IM_COL32(255, 255, 255, 220), p.name);
        }

        if (cfg.distance) {
            char buf[24];
            snprintf(buf, sizeof(buf), "%.0fm", d);
            ImVec2 ts = ImGui::CalcTextSize(buf);
            dl->AddText({ cx - ts.x*0.5f, sfoot.y + 2.f },
                        IM_COL32(190, 190, 190, 180), buf);
        }
    }
}
