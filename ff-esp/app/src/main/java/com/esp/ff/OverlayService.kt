package com.esp.ff

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.WindowManager

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var espView: ESPView
    private lateinit var ctrlView: ControlView

    private val handler = Handler(Looper.getMainLooper())
    private var libBase  = 0L
    private var pid      = -1

    // Config — modified by ControlView callbacks (main thread, no lock needed)
    private var espEnabled  = true
    private var aimbotOn    = false
    private var aimbotFov   = 60f
    private var aimbotSmooth = 0.20f

    private val tick = object : Runnable {
        override fun run() {
            try { readAndDraw() } catch (_: Exception) {}
            handler.postDelayed(this, 16)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // Transparent full-screen ESP layer (click-through)
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

        // Small touchable control panel (top-left corner)
        ctrlView = ControlView(this,
            onEspToggle    = { espEnabled   = it },
            onAimbotToggle = { v -> aimbotOn = v; espView.aimbotOn = v },
            onFovChange    = { v -> aimbotFov = v; espView.aimbotFov = v },
            onSmoothChange = { aimbotSmooth = it }
        )
        val ctrlParams = WindowManager.LayoutParams(
            220, 200,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START; x = 20; y = 80 }
        wm.addView(ctrlView, ctrlParams)

        attach()
        handler.post(tick)
    }

    private fun attach() {
        pid = MemReader.findPid(FF_PACKAGE)
        if (pid < 0) return
        if (!MemReader.openMem(pid)) return
        libBase = MemReader.getModuleBase(pid, GAMEASSEMBLY)
    }

    private fun readAndDraw() {
        if (pid < 0 || libBase == 0L) { attach(); return }

        // ── VP matrix ───────────────────────────────────────
        val camStaticPtr = MemReader.readPtr(libBase + Off.CAMERA_CTRL_STATIC)
        val camInstance  = MemReader.readPtr(camStaticPtr)
        val nativeCam    = MemReader.readPtr(camInstance + Off.CAM_CACHED_PTR)
        val vm           = MemReader.readFloats(nativeCam + Off.NATIVE_CAM_VP, 16)
        espView.vpMatrix = vm

        // Camera world position: read from camera's transform
        val camPos = MemReader.readVec3(nativeCam + Off.NATIVE_TRANSFORM_POS)
        val camX = camPos[0]; val camY = camPos[1]; val camZ = camPos[2]

        // Camera forward from VP matrix row 2 (negated because view space -Z = forward)
        // vm[8], vm[9], vm[10] = -forward in the view matrix portion
        val fwdX = -vm[8]; val fwdY = -vm[9]; val fwdZ = -vm[10]

        // ── Current aim angles (for aimbot write-back) ───────
        val camCtrlStatic = MemReader.readPtr(libBase + Off.CAMERA_CTRL_STATIC)
        val camCtrlInst   = MemReader.readPtr(camCtrlStatic)
        val ctrlNative    = MemReader.readPtr(camCtrlInst + Off.CAM_CTRL_CACHED_PTR)
        val curYaw        = MemReader.readFloat(ctrlNative + Off.CAM_CTRL_YAW)
        val curPitch      = MemReader.readFloat(ctrlNative + Off.CAM_CTRL_PITCH)

        // ── Player list ──────────────────────────────────────
        val pmStaticPtr  = MemReader.readPtr(libBase + Off.PLAYER_MANAGER_STATIC)
        val pmInstance   = MemReader.readPtr(pmStaticPtr)
        val listPtr      = MemReader.readPtr(pmInstance + Off.PM_PLAYER_LIST)
        val itemsPtr     = MemReader.readPtr(listPtr + Off.LIST_ITEMS)
        val count        = MemReader.readInt((listPtr + Off.LIST_SIZE)).coerceIn(0, 32)

        val players = mutableListOf<PlayerData>()
        val enemyPos = mutableListOf<Float>()    // flat {x,y,z,...} para findBestTarget

        for (i in 0 until count) {
            val ptr = MemReader.readPtr(itemsPtr + 0x20 + i * 8L)
            if (ptr < 0x1000) continue
            if (!MemReader.readBool(ptr + Off.PLAYER_IS_ALIVE)) continue

            val hp    = MemReader.readFloat(ptr + Off.PLAYER_HP)
            val maxHp = MemReader.readFloat(ptr + Off.PLAYER_HP_MAX).coerceAtLeast(1f)
            if (hp <= 0f) continue

            val isEnemy = MemReader.readBool(ptr + Off.PLAYER_IS_ENEMY)

            val tfComp   = MemReader.readPtr(ptr + Off.PLAYER_TRANSFORM_COMP)
            val nativeTf = MemReader.readPtr(tfComp + Off.PLAYER_TRANSFORM_COMP)
            val pos      = MemReader.readVec3(nativeTf + Off.NATIVE_TRANSFORM_POS)
            if (pos[0] == 0f && pos[1] == 0f && pos[2] == 0f) continue

            val namePtr = MemReader.readPtr(ptr + Off.PLAYER_NAME_PTR)
            val name    = MemReader.readUnityString(namePtr)

            players += PlayerData(pos[0]-camX, pos[1]-camY, pos[2]-camZ, hp, maxHp, isEnemy, name)
            if (isEnemy) { enemyPos += pos[0]; enemyPos += pos[1]+1.4f; enemyPos += pos[2] } // head
        }

        // ── Aimbot ───────────────────────────────────────────
        var targetIdx = -1
        if (aimbotOn && enemyPos.size >= 3) {
            val posArr   = enemyPos.toFloatArray()
            val enemyCnt = posArr.size / 3
            targetIdx = MemReader.findBestTarget(
                posArr, enemyCnt,
                camX, camY, camZ,
                fwdX, fwdY, fwdZ,
                aimbotFov
            )
            if (targetIdx >= 0) {
                val tx = posArr[targetIdx*3+0]
                val ty = posArr[targetIdx*3+1]
                val tz = posArr[targetIdx*3+2]
                val angles = MemReader.calcAim(
                    camX, camY, camZ, tx, ty, tz,
                    curYaw, curPitch, aimbotSmooth
                )
                // Write new angles to camera controller
                MemReader.writeFloat(ctrlNative + Off.CAM_CTRL_YAW,   angles[0])
                MemReader.writeFloat(ctrlNative + Off.CAM_CTRL_PITCH, angles[1])
            }
        }

        // Mark aimbot target for ESP highlight
        val finalList = players.mapIndexed { i, p ->
            p.copy(isTarget = aimbotOn && i == targetIdx)
        }

        if (espEnabled) {
            espView.players = finalList
            espView.postInvalidate()
        } else {
            espView.players = emptyList()
            espView.postInvalidate()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        wm.removeView(espView)
        wm.removeView(ctrlView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification {
        val ch = "esp"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "ESP", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, ch)
            .setContentTitle("FF ESP+AIM activo")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }
}
