package com.esp.ff

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sqrt

data class PlayerData(
    val wx: Float, val wy: Float, val wz: Float,   // posición mundo
    val hp: Float, val maxHp: Float,
    val isEnemy: Boolean,
    val name: String,
    val isTarget: Boolean = false                   // aimbot lo apunta
)

class ESPView(ctx: Context) : View(ctx) {

    var players: List<PlayerData> = emptyList()
    var vpMatrix: FloatArray      = FloatArray(16)
    var aimbotOn: Boolean         = false
    var aimbotFov: Float          = 60f
    private var sw = 0f
    private var sh = 0f

    private val pBox     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val pTarget  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220,0,255,220); style = Paint.Style.STROKE; strokeWidth = 2.5f }
    private val pHpBg    = Paint().apply { color = Color.argb(160,20,20,20); style = Paint.Style.FILL }
    private val pHpFg    = Paint().apply { style = Paint.Style.FILL }
    private val pName    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f; setShadowLayer(3f,1f,1f,Color.BLACK) }
    private val pDist    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180,200,200,200); textSize = 24f }
    private val pFovRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120,0,220,255); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val pCross   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200,255,255,255); strokeWidth = 1.5f }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { sw=w.toFloat(); sh=h.toFloat() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val vm = vpMatrix
        if (vm.all { it == 0f }) return

        // crosshair + FOV ring
        val cx = sw * 0.5f
        val cy = sh * 0.5f
        canvas.drawLine(cx-12,cy, cx+12,cy, pCross)
        canvas.drawLine(cx,cy-12, cx,cy+12, pCross)
        if (aimbotOn) {
            // approximate screen-space FOV radius from angle
            val fovPx = (sh * 0.5f) * (aimbotFov / 90f)
            canvas.drawCircle(cx, cy, fovPx, pFovRing)
        }

        for (p in players) {
            val foot = w2s(p.wx, p.wy,        p.wz, vm) ?: continue
            val head = w2s(p.wx, p.wy + 1.7f, p.wz, vm) ?: continue

            val boxH = foot.second - head.second
            if (boxH < 4f) continue
            val boxW = boxH * 0.4f
            val bcx  = (foot.first + head.first) * 0.5f

            val left   = bcx - boxW * 0.5f
            val right  = bcx + boxW * 0.5f
            val top    = head.second
            val bottom = foot.second

            // Box
            if (p.isTarget) {
                canvas.drawRect(left, top, right, bottom, pTarget)
            } else {
                pBox.color = if (p.isEnemy) Color.argb(220,255,60,60) else Color.argb(160,60,220,60)
                canvas.drawRect(left, top, right, bottom, pBox)
            }

            // Health bar
            val hpFrac = (p.hp / p.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val bx = left - 6f
            canvas.drawRect(bx-4f, top, bx, bottom, pHpBg)
            pHpFg.color = Color.rgb((255*(1f-hpFrac)).toInt(), (255*hpFrac).toInt(), 0)
            canvas.drawRect(bx-4f, bottom - boxH*hpFrac, bx, bottom, pHpFg)

            // Name
            if (p.name.isNotEmpty()) {
                val tw = pName.measureText(p.name)
                canvas.drawText(p.name, bcx - tw*0.5f, top - 4f, pName)
            }

            // Distance (relativa a cámara, aproximada)
            val d = sqrt(p.wx*p.wx + p.wy*p.wy + p.wz*p.wz)
            val ds = "%.0fm".format(d)
            canvas.drawText(ds, bcx - pDist.measureText(ds)*0.5f, bottom+20f, pDist)
        }
    }

    // Row-major VP matrix → screen pixel. Idéntico al CS2 ESP.
    private fun w2s(x: Float, y: Float, z: Float, vm: FloatArray): Pair<Float,Float>? {
        val cx = vm[0]*x + vm[1]*y + vm[2]*z  + vm[3]
        val cy = vm[4]*x + vm[5]*y + vm[6]*z  + vm[7]
        val cw = vm[12]*x + vm[13]*y + vm[14]*z + vm[15]
        if (cw < 0.001f) return null
        val sx = sw*0.5f*(1f + cx/cw)
        val sy = sh*0.5f*(1f - cy/cw)
        if (sx<0||sx>sw||sy<0||sy>sh) return null
        return Pair(sx, sy)
    }
}
