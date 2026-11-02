package com.esp.ff

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

// Pequeño panel flotante con toggles ESP/Aimbot + slider FOV.
// Arrastrable — toca el panel y muévelo.
class ControlView(ctx: Context,
                  private val onEspToggle:     (Boolean) -> Unit,
                  private val onAimbotToggle:  (Boolean) -> Unit,
                  private val onFovChange:     (Float)   -> Unit,
                  private val onSmoothChange:  (Float)   -> Unit) : View(ctx) {

    var espOn    = true
    var aimbotOn = false
    var fov      = 60f
    var smooth   = 0.20f

    private val W = 220f
    private val H = 200f

    private val pBg   = Paint().apply { color = Color.argb(200,15,15,25) }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 26f }
    private val pOn   = Paint().apply { color = Color.argb(220,0,200,80);  style = Paint.Style.FILL }
    private val pOff  = Paint().apply { color = Color.argb(180,80,80,80);  style = Paint.Style.FILL }
    private val pSliderBg = Paint().apply { color = Color.argb(140,50,50,70); style = Paint.Style.FILL }
    private val pSliderFg = Paint().apply { color = Color.argb(220,0,180,230); style = Paint.Style.FILL }

    private var dragDx = 0f; private var dragDy = 0f; private var dragging = false

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(W.toInt(), H.toInt())

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(0f,0f,W,H,16f,16f, pBg)

        // ESP toggle
        drawToggle(canvas, "ESP",    espOn,    10f, 12f)
        // Aimbot toggle
        drawToggle(canvas, "AIM",    aimbotOn, 120f,12f)

        // FOV slider (aimbot FOV 20..120)
        canvas.drawText("FOV %.0f°".format(fov), 12f, 80f, pText)
        drawSlider(canvas, 10f, 88f, W-20f, 14f, (fov-20f)/100f)

        // Smooth slider (0.05..0.5)
        canvas.drawText("Smooth %.2f".format(smooth), 12f, 130f, pText)
        drawSlider(canvas, 10f, 138f, W-20f, 14f, (smooth-0.05f)/0.45f)

        canvas.drawText("≡ drag", W-70f, H-8f, pText.apply { textSize=20f; alpha=120 })
    }

    private fun drawToggle(c: Canvas, label: String, on: Boolean, x: Float, y: Float) {
        c.drawRoundRect(x, y, x+90f, y+36f, 8f, 8f, if (on) pOn else pOff)
        pText.textSize = 24f; pText.alpha = 255
        c.drawText(if (on) "■ $label" else "□ $label", x+8f, y+26f, pText)
    }

    private fun drawSlider(c: Canvas, x: Float, y: Float, w: Float, h: Float, frac: Float) {
        c.drawRoundRect(x,y,x+w,y+h, 4f,4f, pSliderBg)
        c.drawRoundRect(x,y,x+w*frac.coerceIn(0f,1f),y+h, 4f,4f, pSliderFg)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                dragDx = e.rawX - x; dragDy = e.rawY - y; dragging = true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                x = e.rawX - dragDx; y = e.rawY - dragDy
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return true
                dragging = false
                val lx = e.x; val ly = e.y
                // ESP toggle tap
                if (lx in 10f..100f && ly in 12f..48f) { espOn = !espOn; onEspToggle(espOn); invalidate() }
                // Aimbot toggle tap
                if (lx in 120f..210f && ly in 12f..48f) { aimbotOn = !aimbotOn; onAimbotToggle(aimbotOn); invalidate() }
                // FOV slider tap
                if (ly in 88f..102f) { fov = 20f + ((lx-10f)/(W-20f)).coerceIn(0f,1f)*100f; onFovChange(fov); invalidate() }
                // Smooth slider tap
                if (ly in 138f..152f) { smooth = 0.05f + ((lx-10f)/(W-20f)).coerceIn(0f,1f)*0.45f; onSmoothChange(smooth); invalidate() }
            }
        }
        return true
    }
}
