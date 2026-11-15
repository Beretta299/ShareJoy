package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawSky(view: WorldView, time: Float) {
    val horizon = view.y(0f)
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF2F5F9F), 0.5f to Color(0xFF86B9E2), 0.8f to Color(0xFFE9DDC0), 1f to Color(0xFFF4E3BC),
            startY = min(0f, horizon - size.height * 1.2f), endY = horizon,
        ),
    )
    // Sun with a wide glow.
    val sun = Offset(view.ax + size.width * 0.24f - view.ref * view.scale * 0.03f, horizon - size.height * 0.46f)
    val unit = min(size.width, size.height)
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFF3C4), Color.Transparent), center = sun, radius = unit * 0.6f), unit * 0.6f, sun)
    drawCircle(Color(0xFFFFF6D8), unit * 0.06f, sun)

    // Clouds drift slowly; far layers move less than the camera.
    for (i in 0 until 9) {
        val wx = -130f + i * 31f + hash(i) * 14f + time * 0.35f
        val px = view.ax + (wx - view.ref) * view.scale * 0.16f
        val py = horizon - size.height * (0.3f + hash(i + 40) * 0.36f)
        val s = view.scale * (0.7f + hash(i + 80) * 0.7f)
        for (k in 0 until 5) {
            val ox = (k - 2) * 1.5f * s
            val ry = s * (0.75f + hash(i * 7 + k) * 0.5f)
            drawOval(Color.White.copy(alpha = 0.5f), Offset(px + ox - s * 1.4f, py - ry + (k % 2) * s * 0.2f), Size(s * 2.8f, ry * 2f))
        }
        drawOval(Color(0x22203A60), Offset(px - s * 3.6f, py + s * 0.35f), Size(s * 7.2f, s * 0.7f))
    }

    drawRidge(view, horizon, parallax = 0.14f, step = 7f, base = 6.5f, amp = 9f, seed = 3, top = Color(0xFF8FA6C6), bottom = Color(0xFFB9C9DB))
    drawRidge(view, horizon, parallax = 0.26f, step = 5f, base = 3.2f, amp = 5f, seed = 57, top = Color(0xFF6F8FA8), bottom = Color(0xFF9DB6C2))
    drawRidge(view, horizon, parallax = 0.45f, step = 3.5f, base = 1.2f, amp = 2.6f, seed = 131, top = Color(0xFF55803F), bottom = Color(0xFF7FA35A))

    // A tree line on the nearest hills.
    for (i in -60..60) {
        val wx = i * 2.3f + hash(i + 900) * 1.4f
        val px = view.ax + (wx - view.ref) * view.scale * 0.6f
        if (px < -80f || px > size.width + 80f) continue
        val r = view.scale * (0.55f + hash(i + 300) * 0.5f)
        val py = horizon - r * 0.5f
        drawCircle(Color(0xFF3F6B35), r, Offset(px, py))
        drawCircle(Color(0xFF4F7F40), r * 0.7f, Offset(px - r * 0.25f, py - r * 0.3f))
    }
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x55E9E2C8)), startY = horizon - size.height * 0.2f, endY = horizon),
        topLeft = Offset(0f, horizon - size.height * 0.2f), size = Size(size.width, size.height * 0.2f),
    )
}

internal fun DrawScope.drawRidge(
    view: WorldView, horizon: Float, parallax: Float, step: Float, base: Float, amp: Float, seed: Int, top: Color, bottom: Color,
) {
    val path = Path()
    val s = view.scale * parallax
    var first = true
    var i = -70
    var peak = horizon
    while (i <= 70) {
        val px = view.ax + (i * step - view.ref) * s
        if (px > -step * s * 2f && px < size.width + step * s * 2f) {
            val hgt = base + amp * (0.35f * hash(i + seed) + 0.65f * (0.5f + 0.5f * sin(i * 0.9f + seed)))
            val py = horizon - hgt * view.scale * (0.5f + parallax)
            peak = min(peak, py)
            if (first) {
                path.moveTo(px, horizon)
                first = false
            }
            path.lineTo(px, py)
        }
        i++
    }
    if (first) return
    path.lineTo(size.width + 400f, horizon)
    path.close()
    drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = peak, endY = horizon))
}
