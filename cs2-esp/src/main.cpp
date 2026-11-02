#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <d3d11.h>
#include <dwmapi.h>
#include <imgui.h>
#include <imgui_impl_dx11.h>
#include <imgui_impl_win32.h>
#include <vector>

#include "memory.hpp"
#include "offsets.hpp"
#include "math.hpp"
#include "esp.hpp"

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "dwmapi.lib")

static ID3D11Device*           g_dev  = nullptr;
static ID3D11DeviceContext*    g_ctx  = nullptr;
static IDXGISwapChain*         g_swap = nullptr;
static ID3D11RenderTargetView* g_rtv  = nullptr;
static HWND                    g_hwnd = nullptr;
static int                     g_sw, g_sh;

extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(HWND, UINT, WPARAM, LPARAM);

static LRESULT CALLBACK WndProc(HWND hw, UINT msg, WPARAM wp, LPARAM lp) {
    if (ImGui_ImplWin32_WndProcHandler(hw, msg, wp, lp)) return TRUE;
    if (msg == WM_DESTROY) { PostQuitMessage(0); return 0; }
    return DefWindowProcA(hw, msg, wp, lp);
}

static bool initOverlay() {
    g_sw = GetSystemMetrics(SM_CXSCREEN);
    g_sh = GetSystemMetrics(SM_CYSCREEN);

    WNDCLASSEXA wc{};
    wc.cbSize        = sizeof(wc);
    wc.lpfnWndProc   = WndProc;
    wc.hInstance     = GetModuleHandleA(nullptr);
    wc.lpszClassName = "cs2esp_overlay";
    RegisterClassExA(&wc);

    // WS_EX_TRANSPARENT: click-through when menu closed.
    // Colorkey RGB(0,0,0) → transparent pixels.
    g_hwnd = CreateWindowExA(
        WS_EX_TOPMOST | WS_EX_TRANSPARENT | WS_EX_LAYERED | WS_EX_NOACTIVATE,
        "cs2esp_overlay", "",
        WS_POPUP | WS_VISIBLE,
        0, 0, g_sw, g_sh,
        nullptr, nullptr, wc.hInstance, nullptr
    );
    if (!g_hwnd) return false;

    SetLayeredWindowAttributes(g_hwnd, RGB(0, 0, 0), 0, LWA_COLORKEY);

    MARGINS m{ -1, -1, -1, -1 };
    DwmExtendFrameIntoClientArea(g_hwnd, &m);

    DXGI_SWAP_CHAIN_DESC scd{};
    scd.BufferDesc.Width  = (UINT)g_sw;
    scd.BufferDesc.Height = (UINT)g_sh;
    scd.BufferDesc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
    scd.SampleDesc.Count  = 1;
    scd.BufferUsage       = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    scd.BufferCount       = 2;
    scd.OutputWindow      = g_hwnd;
    scd.Windowed          = TRUE;
    scd.SwapEffect        = DXGI_SWAP_EFFECT_DISCARD;

    D3D_FEATURE_LEVEL fl;
    if (FAILED(D3D11CreateDeviceAndSwapChain(
            nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0,
            nullptr, 0, D3D11_SDK_VERSION,
            &scd, &g_swap, &g_dev, &fl, &g_ctx)))
        return false;

    ID3D11Texture2D* bb = nullptr;
    g_swap->GetBuffer(0, __uuidof(ID3D11Texture2D), (void**)&bb);
    g_dev->CreateRenderTargetView(bb, nullptr, &g_rtv);
    bb->Release();

    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    io.LogFilename = nullptr;

    ImGui::StyleColorsDark();
    // Slightly transparent menu windows so the colorkey black BG shows through
    ImGui::GetStyle().WindowBgAlpha = 0.88f;

    ImGui_ImplWin32_Init(g_hwnd);
    ImGui_ImplDX11_Init(g_dev, g_ctx);
    return true;
}

static void cleanup() {
    ImGui_ImplDX11_Shutdown();
    ImGui_ImplWin32_Shutdown();
    ImGui::DestroyContext();
    if (g_rtv)  g_rtv->Release();
    if (g_swap) g_swap->Release();
    if (g_ctx)  g_ctx->Release();
    if (g_dev)  g_dev->Release();
    DestroyWindow(g_hwnd);
}

static void toggleClickthrough(bool passthrough) {
    LONG ex = GetWindowLongA(g_hwnd, GWL_EXSTYLE);
    if (passthrough) ex |=  WS_EX_TRANSPARENT;
    else             ex &= ~WS_EX_TRANSPARENT;
    SetWindowLongA(g_hwnd, GWL_EXSTYLE, ex);
}

int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int) {
    Memory mem;
    if (!mem.attach()) {
        MessageBoxA(nullptr,
            "cs2.exe not found.\nLaunch CS2 then run cs2esp.",
            "cs2esp", MB_ICONERROR);
        return 1;
    }

    if (!initOverlay()) {
        MessageBoxA(nullptr, "D3D11 overlay init failed.", "cs2esp", MB_ICONERROR);
        return 1;
    }

    ESPCfg cfg;
    bool   menuOpen = false;
    float  vm[16]{};
    std::vector<Player> players;

    MSG msg{};
    while (true) {
        while (PeekMessageA(&msg, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&msg);
            DispatchMessageA(&msg);
            if (msg.message == WM_QUIT) goto done;
        }

        // INSERT → toggle menu + click-through
        if (GetAsyncKeyState(VK_INSERT) & 1) {
            menuOpen = !menuOpen;
            toggleClickthrough(!menuOpen);
        }
        // END → exit
        if (GetAsyncKeyState(VK_END) & 1) break;

        // ---- read memory ----
        uintptr_t eList = mem.read<uintptr_t>(mem.client + off::dwEntityList);

        mem.readBuf(mem.client + off::dwViewMatrix, vm, sizeof(vm));

        // Local player position + team
        Vec3 localPos{};
        int  localTeam = 0;
        uintptr_t localCtrl = mem.read<uintptr_t>(mem.client + off::dwLocalPlayer);
        if (localCtrl) {
            uint32_t  lh  = mem.read<uint32_t>(localCtrl + off::ctrl::m_hPlayerPawn);
            int       li  = (int)(lh & 0x7FFF);
            uintptr_t lc  = mem.read<uintptr_t>(eList + 8 + (li >> 9) * 8);
            uintptr_t lp  = lc ? mem.read<uintptr_t>(lc + (li & 0x1FF) * 0x78) : 0;
            if (lp) {
                uintptr_t sn = mem.read<uintptr_t>(lp + off::pawn::m_pGameSceneNode);
                if (sn) localPos = mem.read<Vec3>(sn + off::scene::m_vecAbsOrigin);
                localTeam = mem.read<uint8_t>(lp + off::pawn::m_iTeamNum);
            }
        }

        // Player slots 1-64 (0 = world entity)
        players.clear();
        for (int i = 1; i <= 64; i++)
            players.push_back(readPlayer(mem, eList, i));

        // ---- render ----
        ImGui_ImplDX11_NewFrame();
        ImGui_ImplWin32_NewFrame();
        ImGui::NewFrame();

        drawESP(players, localPos, localTeam, vm, (float)g_sw, (float)g_sh, cfg);

        if (menuOpen) {
            ImGui::SetNextWindowPos({ 20.f, 20.f }, ImGuiCond_Once);
            ImGui::SetNextWindowSize({ 270.f, 300.f }, ImGuiCond_Once);
            ImGui::Begin("cs2esp  [INSERT=toggle | END=exit]", nullptr,
                         ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoScrollbar);

            ImGui::SeparatorText("ESP");
            ImGui::Checkbox("Enabled",    &cfg.enabled);
            ImGui::Checkbox("Box",        &cfg.box);
            ImGui::Checkbox("Health Bar", &cfg.healthBar);
            ImGui::Checkbox("Names",      &cfg.names);
            ImGui::Checkbox("Distance",   &cfg.distance);
            ImGui::Separator();
            ImGui::Checkbox("Team Check", &cfg.teamCheck);
            ImGui::SliderFloat("Max dist (m)", &cfg.maxDist, 10.f, 200.f, "%.0f");

            ImGui::End();
        }

        ImGui::Render();
        float clear[4] = { 0.f, 0.f, 0.f, 0.f };
        g_ctx->OMSetRenderTargets(1, &g_rtv, nullptr);
        g_ctx->ClearRenderTargetView(g_rtv, clear);
        ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
        g_swap->Present(1, 0);
    }
done:
    cleanup();
    return 0;
}
