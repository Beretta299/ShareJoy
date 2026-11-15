package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.beretta299.sharejoy.game.Balance
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal fun DrawScope.drawGorge(view: WorldView) {
    val g = Balance.GORGE_HALF_WIDTH
    val left = view.x(-g)
    val right = view.x(g)
    val top = view.y(0f)
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF4A423B), Color(0xFF1A1512), Color(0xFF070606)), startY = top, endY = top + view.scale * 9f),
        topLeft = Offset(left, top), size = Size(right - left, max(0f, size.height - top) + view.scale),
    )
    // Mist rising from the river far below.
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x33AEC6D6)), startY = top + view.scale * 1.5f, endY = top + view.scale * 6f),
        topLeft = Offset(left, top + view.scale * 1.5f), size = Size(right - left, view.scale * 6f),
    )
    // A broken rope bridge: posts on both edges, ropes hanging into the gorge.
    for (s in listOf(-1f, 1f)) {
        val px = view.x(s * (g + 0.45f))
        drawLine(Color(0xFF3B2716), Offset(px, top), Offset(px, top - view.scale * 1.25f), view.scale * 0.2f, StrokeCap.Round)
        drawLine(Color(0xFF6B4A2B), Offset(px - view.scale * 0.03f, top), Offset(px - view.scale * 0.03f, top - view.scale * 1.22f), view.scale * 0.08f, StrokeCap.Round)
        val rope = Path()
        rope.moveTo(px, top - view.scale * 1.1f)
        rope.quadraticTo(px - s * view.scale * 0.9f, top + view.scale * 0.4f, px - s * view.scale * 1.15f, top + view.scale * 2.6f)
        drawPath(rope, Color(0xFF2A1C10), style = Stroke(view.scale * 0.05f, cap = StrokeCap.Round))
        for (k in 1..3) {
            val py = top + view.scale * (0.5f + k * 0.55f)
            val bx = px - s * view.scale * (0.85f + k * 0.08f)
            drawLine(Color(0xFF5A3D22), Offset(bx - view.scale * 0.3f, py), Offset(bx + view.scale * 0.3f, py + view.scale * 0.12f), view.scale * 0.09f)
        }
    }
}

internal fun DrawScope.drawGround(view: WorldView) {
    val top = view.y(0f)
    if (top > size.height + 40f) return
    val g = Balance.GORGE_HALF_WIDTH
    for (s in listOf(-1f, 1f)) {
        val x0 = if (s < 0) -200f else view.x(g)
        val x1 = if (s < 0) view.x(-g) else size.width + 200f
        if (x1 < 0f || x0 > size.width) continue
        val l = max(x0, -50f)
        val r = min(x1, size.width + 50f)
        // Soil.
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF7A5633), Color(0xFF4B331E), Color(0xFF2A1C10)), startY = top, endY = top + view.scale * 6f),
            topLeft = Offset(l, top), size = Size(r - l, size.height - top + view.scale),
        )
        // Rocky cliff face at the gorge edge.
        val edge = if (s < 0) x1 else x0
        val cliffW = view.scale * 0.9f
        val cl = if (s < 0) edge - cliffW else edge
        drawRect(
            Brush.horizontalGradient(
                if (s < 0) listOf(Color(0x004B331E), Color(0xFF6A6058), Color(0xFF3A342F)) else listOf(Color(0xFF3A342F), Color(0xFF6A6058), Color(0x004B331E)),
                startX = cl, endX = cl + cliffW,
            ),
            topLeft = Offset(cl, top), size = Size(cliffW, size.height - top + view.scale),
        )
    }

    // Strata, buried stones and grass, only where visible.
    val from = floor(view.left).toInt() - 1
    val to = view.right.toInt() + 1
    for (i in from..to) {
        val wx = i.toFloat()
        if (abs(wx + 0.5f) < g + 0.4f) continue
        for (k in 0 until 2) {
            val hx = hash(i * 13 + k * 101)
            val hy = hash(i * 29 + k * 53)
            val px = view.x(wx + hx)
            val py = top + view.scale * (0.6f + hy * 4.2f)
            val r = view.scale * (0.1f + hash(i * 7 + k) * 0.22f)
            drawOval(Color(0xFF2E2014).copy(alpha = 0.55f), Offset(px - r, py - r * 0.6f + r * 0.2f), Size(r * 2f, r * 1.3f))
            drawOval(Color(0xFF8C7F70).copy(alpha = 0.8f), Offset(px - r, py - r * 0.6f), Size(r * 2f, r * 1.2f))
        }
    }
    for (s in listOf(-1f, 1f)) {
        val x0 = if (s < 0) -200f else view.x(g)
        val x1 = if (s < 0) view.x(-g) else size.width + 200f
        val l = max(x0, -50f)
        val r = min(x1, size.width + 50f)
        if (r <= l) continue
        val th = view.scale * 0.34f
        drawRect(Color(0x66201408), topLeft = Offset(l, top + th * 0.8f), size = Size(r - l, th * 0.5f))
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF8CC152), Color(0xFF4F8A2C)), startY = top - th * 0.2f, endY = top + th),
            topLeft = Offset(l, top - th * 0.12f), size = Size(r - l, th * 1.1f),
        )
    }
    val blades = Path()
    var bx = floor(view.left * 3f).toInt() - 1
    val bEnd = (view.right * 3f).toInt() + 1
    while (bx <= bEnd) {
        val wx = bx / 3f + hash(bx) * 0.2f
        if (abs(wx) > g + 0.15f) {
            val px = view.x(wx)
            val hgt = view.scale * (0.14f + hash(bx + 500) * 0.2f)
            val lean = (hash(bx + 900) - 0.5f) * view.scale * 0.16f
            blades.moveTo(px - view.scale * 0.05f, top)
            blades.lineTo(px + lean, top - hgt)
            blades.lineTo(px + view.scale * 0.05f, top)
            blades.close()
        }
        bx++
    }
    drawPath(blades, Color(0xFF6FAE3E))
}
