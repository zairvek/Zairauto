package com.esp.ff

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }

        val status = TextView(this).apply { textSize = 16f; text = "Checking permissions…" }
        val btnOverlay = Button(this).apply { text = "Grant Overlay Permission" }
        val btnStart   = Button(this).apply { text = "Start ESP" }
        val btnStop    = Button(this).apply { text = "Stop ESP" }

        layout.addView(status)
        layout.addView(btnOverlay)
        layout.addView(btnStart)
        layout.addView(btnStop)
        setContentView(layout)

        btnOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                       Uri.parse("package:$packageName"))
            )
        }

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                status.text = "Overlay permission required — tap the button above."
                return@setOnClickListener
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            status.text = "ESP started. Grant root in Magisk if prompted."
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            status.text = "ESP stopped."
        }

        status.text = if (Settings.canDrawOverlays(this))
            "Overlay permission: OK.  Tap Start ESP."
        else
            "Overlay permission missing — tap Grant first."
    }
}
