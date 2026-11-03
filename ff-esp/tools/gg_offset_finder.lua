--[[
  gg_offset_finder.lua — Encuentra offsets de Free Fire en tiempo real.

  CÓMO USARLO (solo celular):
  1. Instalar GameGuardian desde la web oficial.
  2. Abrir Free Fire → entrar a partida.
  3. Abrir GG → menú ☰ → "Ejecutar script" → seleccionar este archivo.
  4. El script imprime los offsets; anotarlos y pegarlos en Offsets.kt.

  NOTA: Este script escanea memoria para encontrar valores conocidos.
  Los offsets varían por versión del juego — re-ejecutar tras cada update.
--]]

-- ────────────────────────────────────────────────────────────────────────────
-- Configuración
-- ────────────────────────────────────────────────────────────────────────────
local GS_PACKAGE = "com.dts.freefireth"   -- MAX: com.dts.freefiremax

-- HP típico al empezar partida (ajustar si se escanea diferente momento)
local KNOWN_HP     = 200   -- HP máximo típico de FF
local KNOWN_HP_MIN = 195   -- margen inferior para búsqueda

-- ────────────────────────────────────────────────────────────────────────────
-- helpers
-- ────────────────────────────────────────────────────────────────────────────
local function log(msg)
    gg.toast(tostring(msg))
    print(tostring(msg))
end

local function hex(n)
    return string.format("0x%X", n)
end

-- ────────────────────────────────────────────────────────────────────────────
-- 1. Buscar HP del jugador local
-- ────────────────────────────────────────────────────────────────────────────
local function findHPOffset()
    log("Buscando HP = " .. KNOWN_HP .. "...")

    -- Primera búsqueda: buscar valor exacto como float
    gg.clearResults()
    gg.searchNumber(tostring(KNOWN_HP), gg.TYPE_FLOAT)
    local res1 = gg.getResults(500)

    if #res1 == 0 then
        log("No encontrado. Probando rango " .. KNOWN_HP_MIN .. "-" .. KNOWN_HP)
        gg.clearResults()
        gg.searchNumber(KNOWN_HP_MIN .. "~" .. KNOWN_HP, gg.TYPE_FLOAT)
        res1 = gg.getResults(500)
    end

    log("Candidatos HP: " .. #res1)
    if #res1 == 0 then
        log("ERROR: no encontré HP. Abrí inventario para bajar HP antes?")
        return nil
    end

    -- Ahora tomar daño y buscar el valor que bajó
    local before = {}
    for _, r in ipairs(res1) do before[r.address] = r.value end

    log("Toma daño ahora! (esperando 8 seg)...")
    gg.sleep(8000)

    gg.searchNumber("1~" .. (KNOWN_HP - 1), gg.TYPE_FLOAT)
    local res2 = gg.getResults(200)

    -- Filtrar: solo los que estaban en res1 y bajaron
    local matches = {}
    for _, r in ipairs(res2) do
        if before[r.address] then
            table.insert(matches, r)
        end
    end

    log("Tras daño, candidatos: " .. #matches)
    if #matches == 0 then return nil end

    -- El primero suele ser el correcto
    local hpAddr = matches[1].address
    log("HP address: " .. hex(hpAddr))
    return hpAddr
end

-- ────────────────────────────────────────────────────────────────────────────
-- 2. Desde hpAddr, encontrar el objeto jugador y más offsets
-- ────────────────────────────────────────────────────────────────────────────
local function explorePlayerObject(hpAddr)
    log("Explorando objeto jugador desde: " .. hex(hpAddr))

    -- Leer 0x200 bytes alrededor de hpAddr para encontrar la base del objeto
    -- La base del objeto suele ser alineada a 0x10 bytes; buscar hacia atrás
    local results = {}

    -- buscar patrón: leer offset -0x200 a +0x200 en pasos de 4
    local function readf(addr) return gg.readMemory(addr, 4) end
    local function readi(addr) return gg.readMemory(addr, 4) end

    -- HP suele estar a un offset fijo del inicio de la clase (0x1C0 ~ 0x2A0 típico FF)
    -- Buscar un float = maxHP cerca de hpAddr
    local maxHpAddr = nil
    for off = -0x100, 0x100, 4 do
        local v = gg.readMemory(hpAddr + off, 4)
        if v and v == KNOWN_HP * 1.0 then
            maxHpAddr = hpAddr + off
            log("maxHP candidato en offset " .. hex(off) .. " desde HP")
            break
        end
    end

    -- Leer alrededor buscando bool isAlive (value=1) cerca de HP
    -- isAlive suele ser 1 byte, offset cercano a HP
    local isAliveAddr = nil
    for off = -0x50, 0x50, 1 do
        local v = gg.readMemory(hpAddr + off, 1)
        if v and (v == 1 or v == 0) then
            -- Candidato: verificar que justo antes hay HP-like values
            -- (heurística simple: es cerca de HP y vale 1)
            if v == 1 then
                isAliveAddr = hpAddr + off
                -- no hacer break, queda el último 1 más cercano
            end
        end
    end

    results.hpAddr     = hpAddr
    results.maxHpAddr  = maxHpAddr
    results.isAliveAddr = isAliveAddr

    return results
end

-- ────────────────────────────────────────────────────────────────────────────
-- 3. Buscar posición XYZ (frente a la cámara → mover y re-escanear)
-- ────────────────────────────────────────────────────────────────────────────
local function findPositionOffsets()
    log("Para encontrar posición:")
    log("1. Parate en un lugar específico")
    log("2. Esperando 5s para primera lectura...")
    gg.sleep(5000)

    -- Buscar tu coordenada X en memoria (en FF suele estar entre -5000 y 5000)
    -- La estrategia: buscar un float en rango plausible, mover, re-buscar
    gg.clearResults()
    gg.searchNumber("-5000~5000", gg.TYPE_FLOAT)
    local c1 = gg.getResults(2000)
    local snap1 = {}
    for _, r in ipairs(c1) do snap1[r.address] = r.value end

    log("Candidatos posición: " .. #c1 .. ". Movete! (10s)")
    gg.sleep(10000)

    gg.searchNumber("-5000~5000", gg.TYPE_FLOAT)
    local c2 = gg.getResults(2000)

    local changed = {}
    for _, r in ipairs(c2) do
        if snap1[r.address] and math.abs(r.value - snap1[r.address]) > 0.5 then
            table.insert(changed, r)
        end
    end

    log("Coordenadas que cambiaron: " .. #changed)
    if #changed > 0 then
        for i = 1, math.min(3, #changed) do
            log("  Candidato " .. i .. ": " .. hex(changed[i].address) .. " = " .. changed[i].value)
        end
    end

    gg.clearResults()
    return changed
end

-- ────────────────────────────────────────────────────────────────────────────
-- 4. Output final
-- ────────────────────────────────────────────────────────────────────────────
local function printSummary(hpAddr, posAddrs)
    local out = "\n===== OFFSETS ENCONTRADOS =====\n"
    if hpAddr then
        out = out .. "PLAYER_HP address: " .. hex(hpAddr) .. "\n"
        out = out .. "(Calcula el offset desde la base del objeto jugador manualmente)\n"
    end
    if posAddrs and #posAddrs > 0 then
        out = out .. "\nCoordenadas XYZ candidatas:\n"
        for i = 1, math.min(6, #posAddrs) do
            out = out .. "  " .. hex(posAddrs[i].address) .. " = " .. posAddrs[i].value .. "\n"
        end
    end
    out = out .. "\n===== FIN =====\n"
    out = out .. "Usa estas direcciones con un pointer scan en GG\n"
    out = out .. "para obtener el offset desde libGameAssembly.so"
    log(out)
    print(out)
end

-- ────────────────────────────────────────────────────────────────────────────
-- MAIN
-- ────────────────────────────────────────────────────────────────────────────
log("=== FF Offset Finder ===")
log("Asegurate de estar en partida con HP lleno")
gg.sleep(2000)

-- Paso 1: HP
local hpAddr = findHPOffset()

-- Paso 2: explorar objeto si encontramos HP
if hpAddr then
    local obj = explorePlayerObject(hpAddr)
    log("maxHP addr: " .. (obj.maxHpAddr and hex(obj.maxHpAddr) or "no encontrado"))
    log("isAlive addr: " .. (obj.isAliveAddr and hex(obj.isAliveAddr) or "no encontrado"))
end

-- Paso 3: posición
local posAddrs = findPositionOffsets()

-- Paso 4: resumen
printSummary(hpAddr, posAddrs)
log("Script terminado. Anota los valores y pégalos en Offsets.kt")
