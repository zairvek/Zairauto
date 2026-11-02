package com.esp.ff

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sqrt

data class PlayerData(
    val wx: Float, val wy: Float, val wz: Float,
    val hp: Float, val maxHp: Float,
    val isEnemy: Boolean,
    val name: String
)

class ESPView(ctx: Context) : View(ctx) {

    var players: List<PlayerData> = emptyList()
    var vpMatrix: FloatArray = FloatArray(16)       // row-major VP from camera
    private var sw = 0f
    private var sh = 0f

    private val paintBox   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val paintHpBg  = Paint().apply { color = Color.argb(160, 20, 20, 20); style = Paint.Style.FILL }
    private val paintHpFg  = Paint().apply { style = Paint.Style.FILL }
    private val paintText  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f }
    private val paintDist  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 200, 200, 200); textSize = 24f }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        sw = w.toFloat(); sh = h.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        // clear to transparent
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val vm = vpMatrix
        if (vm.all { it == 0f }) return

        for (p in players) {
            val foot = w2s(p.wx, p.wy, p.wz, vm) ?: continue
            // head ≈ foot + ~1.7m. Free Fire units are metres (Unity), so +1.7 on Y axis.
            val head = w2s(p.wx, p.wy + 1.7f, p.wz, vm) ?: continue

            val boxH = foot.second - head.second
            if (boxH < 4f) continue
            val boxW = boxH * 0.4f
            val cx   = (foot.first + head.first) * 0.5f

            val left   = cx - boxW * 0.5f
            val right  = cx + boxW * 0.5f
            val top    = head.second
            val bottom = foot.second

            // Box color: enemy = red, ally = green
            paintBox.color = if (p.isEnemy) Color.argb(220, 255, 60, 60)
                             else           Color.argb(180, 60, 220, 60)
            canvas.drawRect(left, top, right, bottom, paintBox)

            // Health bar (left side)
            val hpFrac  = (p.hp / p.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val barX    = left - 6f
            val fillTop = bottom - boxH * hpFrac
            canvas.drawRect(barX - 4f, top, barX, bottom, paintHpBg)
            paintHpFg.color = Color.rgb(
                (255 * (1f - hpFrac)).toInt(),
                (255 * hpFrac).toInt(), 0
            )
            canvas.drawRect(barX - 4f, fillTop, barX, bottom, paintHpFg)

            // Name
            if (p.name.isNotEmpty()) {
                val tw = paintText.measureText(p.name)
                canvas.drawText(p.name, cx - tw * 0.5f, top - 4f, paintText)
            }

            // Distance
            val dist = sqrt(p.wx * p.wx + p.wy * p.wy + p.wz * p.wz)   // relative to cam — caller sets relative coords
            val distStr = "%.0fm".format(dist)
            val dw = paintDist.measureText(distStr)
            canvas.drawText(distStr, cx - dw * 0.5f, bottom + 20f, paintDist)
        }
    }

    // Row-major VP matrix W2S (same formula as CS2 / Source 2 — Unity VP is equivalent)
    private fun w2s(x: Float, y: Float, z: Float, vm: FloatArray): Pair<Float, Float>? {
        val cx = vm[0]*x + vm[1]*y + vm[2]*z  + vm[3]
        val cy = vm[4]*x + vm[5]*y + vm[6]*z  + vm[7]
        val cw = vm[12]*x + vm[13]*y + vm[14]*z + vm[15]
        if (cw < 0.001f) return null
        val sx = sw * 0.5f * (1f + cx / cw)
        val sy = sh * 0.5f * (1f - cy / cw)
        if (sx < 0f || sx > sw || sy < 0f || sy > sh) return null
        return Pair(sx, sy)
    }
}
