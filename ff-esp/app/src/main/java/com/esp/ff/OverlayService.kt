package com.esp.ff

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.Gravity
import android.view.WindowManager

class OverlayService : Service() {

    private lateinit var wm:       WindowManager
    private lateinit var espView:  ESPView
    private lateinit var ctrlView: ControlView

    private val handler  = Handler(Looper.getMainLooper())
    private var libBase  = 0L
    private var pid      = -1

    private var espEnabled   = true
    private var aimbotOn     = false
    private var aimbotFov    = 60f
    private var aimbotSmooth = 0.20f

    // Cached VP matrix so the ESP can draw even between read ticks
    private var vpCache = FloatArray(16)

    private val tick = object : Runnable {
        override fun run() {
            try { readTick() } catch (_: Exception) {}
            handler.postDelayed(this, 16)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotif())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        espView = ESPView(this)
        wm.addView(espView, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ))

        ctrlView = ControlView(this,
            onEspToggle    = { espEnabled  = it },
            onAimbotToggle = { v -> aimbotOn = v; espView.aimbotOn = v },
            onFovChange    = { v -> aimbotFov = v; espView.aimbotFov = v },
            onSmoothChange = { aimbotSmooth = it }
        )
        wm.addView(ctrlView, WindowManager.LayoutParams(220, 200,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).also { it.gravity = Gravity.TOP or Gravity.START; it.x = 20; it.y = 80 })

        attach()
        handler.post(tick)
    }

    private fun attach() {
        pid = MemReader.findPid(FF_PACKAGE)
        if (pid < 0) return
        if (!MemReader.openMem(pid)) return
        libBase = MemReader.getModuleBase(pid, GAMEASSEMBLY)
    }

    // ── IL2CPP static field access ──────────────────────────────────────────
    // typeInfoOff: libBase + typeInfoOff = Il2CppClass*
    // fieldOff: offset within StaticFields struct
    private fun getStaticInstance(typeInfoOff: Long, fieldOff: Long): Long {
        val classPtr  = MemReader.readPtr(libBase + typeInfoOff)
        if (classPtr < 0x1000) return 0L
        val sfPtr     = MemReader.readPtr(classPtr + IL2CPP_STATIC_FIELDS)
        if (sfPtr < 0x1000) return 0L
        return MemReader.readPtr(sfPtr + fieldOff)
    }

    // ── Transform → world position ──────────────────────────────────────────
    // Reads position using Option A (transform ref field) with fallback to Option B (direct Vec3).
    private fun getPlayerPos(playerPtr: Long): FloatArray? {
        // Option B: direct cached Vector3
        if (Off.PLAYER_POS_DIRECT != 0L) {
            val p = MemReader.readFloats(playerPtr + Off.PLAYER_POS_DIRECT, 3)
            if (p[0] != 0f || p[1] != 0f || p[2] != 0f) return p
        }

        // Option A: Transform component → native ptr → position
        if (Off.PLAYER_TRANSFORM == 0L) return null
        val transformRef = MemReader.readPtr(playerPtr + Off.PLAYER_TRANSFORM)
        if (transformRef < 0x1000) return null
        val nativeTf = MemReader.readPtr(transformRef + Off.COMPONENT_NATIVE)
        if (nativeTf < 0x1000) return null
        val pos = MemReader.readFloats(nativeTf + Off.NATIVE_POS, 3)
        return if (pos[0] == 0f && pos[1] == 0f && pos[2] == 0f) null else pos
    }

    // ── Camera ─────────────────────────────────────────────────────────────
    private fun getCamData(): Triple<FloatArray, FloatArray, Long>? {
        // Returns: (VP matrix float[16], camPos float[3], ctrlNativePtr)
        val camInst = getStaticInstance(Off.CAM_TYPE_INFO, Off.CAM_STATIC_INSTANCE)
        if (camInst < 0x1000) return null
        val nativeCam = MemReader.readPtr(camInst + Off.COMPONENT_NATIVE)
        if (nativeCam < 0x1000) return null

        val vp  = if (Off.NATIVE_CAM_VP != 0L) MemReader.readFloats(nativeCam + Off.NATIVE_CAM_VP, 16)
                  else vpCache  // stale but better than nothing
        val pos = MemReader.readFloats(nativeCam + Off.NATIVE_POS, 3)
        return Triple(vp, pos, nativeCam)
    }

    // ── Main tick ───────────────────────────────────────────────────────────
    private fun readTick() {
        if (pid < 0 || libBase == 0L) { attach(); return }

        // Camera
        val camData = getCamData()
        val vm      = camData?.first ?: FloatArray(16)
        val camPos  = camData?.second ?: FloatArray(3)
        val ctrlPtr = camData?.third ?: 0L
        if (vm.any { it != 0f }) vpCache = vm
        espView.vpMatrix = vpCache

        // Camera forward from view matrix rows (row 2 = -forward in OpenGL/Unity)
        val fwdX = -vpCache[8]; val fwdY = -vpCache[9]; val fwdZ = -vpCache[10]
        val curYaw   = if (ctrlPtr > 0L && Off.CAM_YAW   != 0L) MemReader.readFloat(ctrlPtr + Off.CAM_YAW)   else 0f
        val curPitch = if (ctrlPtr > 0L && Off.CAM_PITCH != 0L) MemReader.readFloat(ctrlPtr + Off.CAM_PITCH) else 0f

        // Player list
        val pmInst   = getStaticInstance(Off.PM_TYPE_INFO, Off.PM_STATIC_INSTANCE)
        if (pmInst < 0x1000) return
        val listPtr  = MemReader.readPtr(pmInst + Off.PM_PLAYER_LIST)
        val itemsPtr = MemReader.readPtr(listPtr + Off.LIST_ITEMS)
        val count    = MemReader.readInt(listPtr + Off.LIST_SIZE).coerceIn(0, 32)

        val players   = mutableListOf<PlayerData>()
        val enemyPosFlat = mutableListOf<Float>()

        for (i in 0 until count) {
            val ptr = MemReader.readPtr(itemsPtr + 0x20 + i * 8L)
            if (ptr < 0x1000) continue
            if (Off.PLAYER_IS_ALIVE != 0L && !MemReader.readBool(ptr + Off.PLAYER_IS_ALIVE)) continue
            val hp    = if (Off.PLAYER_HP     != 0L) MemReader.readFloat(ptr + Off.PLAYER_HP)     else 100f
            val maxHp = if (Off.PLAYER_HP_MAX != 0L) MemReader.readFloat(ptr + Off.PLAYER_HP_MAX) else 100f
            if (hp <= 0f) continue

            val isEnemy = when {
                Off.PLAYER_IS_ENEMY != 0L -> MemReader.readBool(ptr + Off.PLAYER_IS_ENEMY)
                else -> true  // assume all visible = enemies if no team field
            }

            val pos = getPlayerPos(ptr) ?: continue
            val namePtr = if (Off.PLAYER_NAME_PTR != 0L) MemReader.readPtr(ptr + Off.PLAYER_NAME_PTR) else 0L
            val name    = if (namePtr > 0x1000) MemReader.readUnityString(namePtr) else ""

            val relX = pos[0] - camPos[0]
            val relY = pos[1] - camPos[1]
            val relZ = pos[2] - camPos[2]

            players += PlayerData(relX, relY, relZ, hp, maxHp, isEnemy, name)
            if (isEnemy) {
                enemyPosFlat += pos[0]; enemyPosFlat += pos[1] + 1.4f; enemyPosFlat += pos[2]
            }
        }

        // Aimbot
        var targetIdx = -1
        if (aimbotOn && ctrlPtr > 0L && Off.CAM_YAW != 0L && enemyPosFlat.size >= 3) {
            val posArr = enemyPosFlat.toFloatArray()
            targetIdx  = MemReader.findBestTarget(
                posArr, posArr.size / 3,
                camPos[0], camPos[1], camPos[2],
                fwdX, fwdY, fwdZ,
                aimbotFov
            )
            if (targetIdx >= 0) {
                val tx = posArr[targetIdx*3]; val ty = posArr[targetIdx*3+1]; val tz = posArr[targetIdx*3+2]
                val a  = MemReader.calcAim(camPos[0],camPos[1],camPos[2], tx,ty,tz, curYaw,curPitch, aimbotSmooth)
                MemReader.writeFloat(ctrlPtr + Off.CAM_YAW,   a[0])
                MemReader.writeFloat(ctrlPtr + Off.CAM_PITCH, a[1])
            }
        }

        val final = players.mapIndexed { i, p -> p.copy(isTarget = aimbotOn && i == targetIdx) }
        espView.players = if (espEnabled) final else emptyList()
        espView.postInvalidate()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        wm.removeView(espView); wm.removeView(ctrlView)
        super.onDestroy()
    }
    override fun onBind(i: Intent?) = null

    private fun buildNotif(): Notification {
        val ch = "esp_ch"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "ESP", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, ch).setContentTitle("FF ESP activo")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
    }
}
