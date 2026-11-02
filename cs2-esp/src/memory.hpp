#pragma once
#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <TlHelp32.h>
#include <string>

struct Memory {
    HANDLE   proc   = nullptr;
    uintptr_t client = 0;

    bool attach() {
        DWORD pid = findPid(L"cs2.exe");
        if (!pid) return false;
        proc = OpenProcess(PROCESS_VM_READ, FALSE, pid);
        if (!proc) return false;
        client = getModule(pid, L"client.dll");
        return client != 0;
    }

    template<typename T>
    T read(uintptr_t addr) {
        T val{};
        if (addr > 0x1000)
            ReadProcessMemory(proc, (LPCVOID)addr, &val, sizeof(T), nullptr);
        return val;
    }

    bool readBuf(uintptr_t addr, void* buf, size_t sz) {
        if (addr < 0x1000) return false;
        return ReadProcessMemory(proc, (LPCVOID)addr, buf, sz, nullptr) != 0;
    }

    std::string readStr(uintptr_t addr, size_t maxLen = 64) {
        if (addr < 0x1000) return {};
        char buf[128]{};
        SIZE_T n = maxLen < sizeof(buf) ? maxLen : sizeof(buf) - 1;
        ReadProcessMemory(proc, (LPCVOID)addr, buf, n, nullptr);
        return std::string(buf);
    }

private:
    static DWORD findPid(const wchar_t* name) {
        HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        PROCESSENTRY32W pe{};
        pe.dwSize = sizeof(pe);
        DWORD pid = 0;
        if (Process32FirstW(snap, &pe)) {
            do {
                if (!wcscmp(pe.szExeFile, name)) { pid = pe.th32ProcessID; break; }
            } while (Process32NextW(snap, &pe));
        }
        CloseHandle(snap);
        return pid;
    }

    static uintptr_t getModule(DWORD pid, const wchar_t* name) {
        HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
        MODULEENTRY32W me{};
        me.dwSize = sizeof(me);
        uintptr_t base = 0;
        if (Module32FirstW(snap, &me)) {
            do {
                if (!wcscmp(me.szModule, name)) { base = (uintptr_t)me.modBaseAddr; break; }
            } while (Module32NextW(snap, &me));
        }
        CloseHandle(snap);
        return base;
    }
};
