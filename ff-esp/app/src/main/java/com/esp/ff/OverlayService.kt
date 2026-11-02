package com.esp.ff

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.WindowManager

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var espView: ESPView
    private val handler = Handler(Looper.getMainLooper())
    private var libBase = 0L
    private var pid     = -1

    private val readLoop = object : Runnable {
        override fun run() {
            try { tick() } catch (_: Exception) {}
            handler.postDelayed(this, 16)   // ~60 fps read rate
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        espView = ESPView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(espView, params)

        attach()
        handler.post(readLoop)
    }

    private fun attach() {
        pid = MemReader.findPid(FF_PACKAGE)
        if (pid < 0) return
        if (!MemReader.openMem(pid)) return
        libBase = MemReader.getModuleBase(pid, GAMEASSEMBLY)
    }

    private fun tick() {
        // Re-attach if process restarted
        if (pid < 0 || libBase == 0L) { attach(); return }

        // ── Read camera VP matrix ─────────────────────────────
        val camStaticPtr  = MemReader.readPtr(libBase + Off.CAMERA_STATIC)
        val camInstance   = MemReader.readPtr(camStaticPtr)          // static Camera.main
        val nativeCam     = MemReader.readPtr(camInstance + Off.CAMERA_CACHED_PTR)
        val vm = MemReader.readFloats(nativeCam + Off.NATIVE_CAMERA_VP, 16)
        espView.vpMatrix = vm

        // Camera world position (first 3 floats of last column in a standard VP matrix)
        // Used to compute relative distance for each player.
        val camX = vm[12]; val camY = vm[13]; val camZ = vm[14]     // approximation

        // ── Read player list ──────────────────────────────────
        val pmStaticPtr  = MemReader.readPtr(libBase + Off.PLAYER_MANAGER_STATIC)
        val pmInstance   = MemReader.readPtr(pmStaticPtr)
        val listPtr      = MemReader.readPtr(pmInstance + Off.PM_PLAYER_LIST)
        val itemsPtr     = MemReader.readPtr(listPtr + Off.LIST_ITEMS)
        val count        = MemReader.readInt((listPtr + Off.LIST_SIZE).toLong()).coerceIn(0, 32)

        val result = mutableListOf<PlayerData>()
        for (i in 0 until count) {
            val playerPtr = MemReader.readPtr(itemsPtr + 0x20 + i * 8L)  // object[] data starts at +0x20
            if (playerPtr < 0x1000) continue
            if (!MemReader.readBool(playerPtr + Off.PLAYER_IS_ALIVE)) continue

            val hp    = MemReader.readFloat(playerPtr + Off.PLAYER_HP)
            val maxHp = MemReader.readFloat(playerPtr + Off.PLAYER_HP_MAX).coerceAtLeast(1f)
            if (hp <= 0f) continue

            val isEnemy = MemReader.readBool(playerPtr + Off.PLAYER_IS_ENEMY)

            // Transform → native object → position
            val transformComp  = MemReader.readPtr(playerPtr + Off.COMPONENT_TRANSFORM)
            val nativeTransform = MemReader.readPtr(transformComp + Off.TRANSFORM_CACHED_PTR)
            val (wx, wy, wz)   = MemReader.readVec3(nativeTransform + Off.NATIVE_TRANSFORM_POS)
            if (wx == 0f && wy == 0f && wz == 0f) continue

            val nameStrPtr = MemReader.readPtr(playerPtr + Off.PLAYER_NAME_PTR)
            val name = MemReader.readUnityString(nameStrPtr)

            // Pass position relative to camera so ESPView can compute distance
            result += PlayerData(wx - camX, wy - camY, wz - camZ, hp, maxHp, isEnemy, name)
        }

        espView.players = result
        espView.postInvalidate()
    }

    override fun onDestroy() {
        handler.removeCallbacks(readLoop)
        wm.removeView(espView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "esp_svc"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "ESP", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("FF ESP running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }
}
