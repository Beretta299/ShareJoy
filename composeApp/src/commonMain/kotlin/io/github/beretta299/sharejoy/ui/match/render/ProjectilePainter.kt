package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.beretta299.sharejoy.game.Ammo
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal fun DrawScope.drawProjectile(
    view: WorldView, wx: Float, wy: Float, angle: Float, radius: Float, ammo: Ammo, seed: Int, time: Float, vx: Float, vy: Float,
) {
    val c = Offset(view.x(wx), view.y(wy))
    if (c.x < -100f || c.x > size.width + 100f) return
    val r = radius * view.scale
    val speed = sqrt(vx * vx + vy * vy)
    if (speed > 6f) {
        // Motion streak.
        val tail = Offset(c.x - vx / speed * r * 3.2f, c.y + vy / speed * r * 3.2f)
        drawLine(Brush.linearGradient(listOf(Color(0x55FFFFFF), Color.Transparent), c, tail), c, tail, r * 1.5f, StrokeCap.Round)
    }
    drawCircle(Color(0x40000000), r, c + Offset(r * 0.18f, r * 0.2f))
    val lightC = c + Offset(-r * 0.35f, -r * 0.4f)
    when (ammo) {
        Ammo.STONE, Ammo.SPLIT -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFC9C3B6), Color(0xFF8B867B), Color(0xFF55514A)), center = lightC, radius = r * 1.7f), r, c)
            for (i in 0 until 5) {
                val a = angle + i * 1.3f + hash(seed + i) * 2f
                val d = r * (0.25f + hash(seed + i * 3) * 0.5f)
                drawCircle(Color(0x44201C18), r * (0.08f + hash(seed + i * 5) * 0.1f), c + Offset(cos(a) * d, -sin(a) * d))
            }
        }
        Ammo.IRON -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF9AA3AE), Color(0xFF3B4148), Color(0xFF16191D)), center = lightC, radius = r * 1.6f), r, c)
            drawCircle(Color(0x99FFFFFF), r * 0.16f, lightC)
            val a = angle
            drawLine(Color(0x55000000), c + Offset(cos(a) * r * 0.9f, -sin(a) * r * 0.9f), c - Offset(cos(a) * r * 0.9f, -sin(a) * r * 0.9f), r * 0.12f)
        }
        Ammo.KEG -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFA9703A), Color(0xFF6B4320), Color(0xFF3B230F)), center = lightC, radius = r * 1.7f), r, c)
            for (o in listOf(-0.45f, 0.45f)) {
                val nx = cos(angle + 1.5708f)
                val ny = -sin(angle + 1.5708f)
                val tx = cos(angle)
                val ty = -sin(angle)
                val m = c + Offset(nx * r * o, ny * r * o)
                val half = r * sqrt(1f - o * o)
                drawLine(Color(0xFF2B2B2E), m - Offset(tx * half, ty * half), m + Offset(tx * half, ty * half), r * 0.16f)
            }
            val spark = c + Offset(cos(angle + 1.2f) * r, -sin(angle + 1.2f) * r)
            drawCircle(Color(0xFFFFC14D), r * (0.22f + 0.1f * sin(time * 40f)), spark)
            drawCircle(Color(0xFFFFF4C2), r * 0.1f, spark)
        }
    }
    drawCircle(Color(0xCC14100C), r, c, style = Stroke(max(1.2f, r * 0.09f)))
}
