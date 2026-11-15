package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.beretta299.sharejoy.game.Material
import io.github.beretta299.sharejoy.game.Piece
import io.github.beretta299.sharejoy.util.Rng
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun DrawScope.drawShadow(view: WorldView, p: Piece) {
    val b = p.body
    val px = view.x(b.x + 0.16f)
    val py = view.y(b.y - 0.16f)
    if (px < -200f || px > size.width + 200f) return
    val hw = b.halfW * view.scale
    val hh = b.halfH * view.scale
    withTransform({
        translate(px, py)
        rotate(-b.angle * 180f / PI.toFloat(), Offset.Zero)
    }) {
        drawRect(Color(0x47000000), topLeft = Offset(-hw, -hh), size = Size(hw * 2f, hh * 2f))
    }
}

internal fun DrawScope.drawBlock(view: WorldView, p: Piece, tex: Textures, time: Float) {
    val b = p.body
    val px = view.x(b.x)
    val py = view.y(b.y)
    val reach = (b.halfW + b.halfH + 2.5f) * view.scale
    if (px < -reach || px > size.width + reach || py < -reach || py > size.height + reach) return

    // Wood grain runs along the long side of a plank.
    val turned = p.material == Material.WOOD && b.halfH > b.halfW
    val wM = if (turned) b.halfH * 2f else b.halfW * 2f
    val hM = if (turned) b.halfW * 2f else b.halfH * 2f
    val w = max(4, (wM * TPM).roundToInt())
    val h = max(4, (hM * TPM).roundToInt())
    val image = when (p.material) {
        Material.WOOD -> tex.wood
        Material.STONE -> tex.stone
        Material.IRON -> tex.iron
    }
    val cell = if (p.material == Material.IRON) TPM else TPM / 2
    val maxX = max(0, Textures.SIZE - w)
    val maxY = max(0, Textures.SIZE - h)
    val srcX = min(((p.seed % 7) * TPM), maxX) / cell * cell
    val srcY = min((((p.seed / 7) % 13) * cell), maxY) / cell * cell
    val k = view.scale / TPM

    withTransform({
        translate(px, py)
        rotate(-b.angle * 180f / PI.toFloat() + if (turned) 90f else 0f, Offset.Zero)
        scale(k, k, Offset.Zero)
    }) {
        val left = -w / 2f
        val top = -h / 2f
        drawImage(
            image,
            srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(min(w, Textures.SIZE), min(h, Textures.SIZE)),
            dstOffset = IntOffset(-w / 2, -h / 2), dstSize = IntSize(w, h),
        )
        // Bevel: light from the upper left.
        drawLine(Color.White.copy(alpha = 0.30f), Offset(left + 1f, top + 1.6f), Offset(left + w - 1f, top + 1.6f), 2.4f)
        drawLine(Color.White.copy(alpha = 0.14f), Offset(left + 1.6f, top + 1f), Offset(left + 1.6f, top + h - 1f), 2.2f)
        drawLine(Color.Black.copy(alpha = 0.34f), Offset(left + 1f, top + h - 1.6f), Offset(left + w - 1f, top + h - 1.6f), 2.6f)
        drawLine(Color.Black.copy(alpha = 0.24f), Offset(left + w - 1.6f, top + 1f), Offset(left + w - 1.6f, top + h - 1f), 2.4f)
        drawRect(Color(0xFF16100B).copy(alpha = 0.75f), topLeft = Offset(left, top), size = Size(w.toFloat(), h.toFloat()), style = Stroke(1.8f))

        val stage = p.damageStage
        if (stage > 0) drawCracks(p.seed, w.toFloat(), h.toFloat(), stage)

        if (p.hasFlag) drawFlag(p.owner, if (turned) 0f else top, time)
    }
}

internal fun DrawScope.drawCracks(seed: Int, w: Float, h: Float, stage: Int) {
    val rng = Rng(seed + 17)
    if (stage >= 2) drawRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(-w / 2f, -h / 2f), size = Size(w, h))
    val count = if (stage >= 2) 5 else 2
    val unit = min(w, h)
    repeat(count) {
        val path = Path()
        // Start on an edge and wander toward the middle.
        var x: Float
        var y: Float
        when (rng.int(4)) {
            0 -> { x = rng.range(-w / 2f, w / 2f); y = -h / 2f }
            1 -> { x = rng.range(-w / 2f, w / 2f); y = h / 2f }
            2 -> { x = -w / 2f; y = rng.range(-h / 2f, h / 2f) }
            else -> { x = w / 2f; y = rng.range(-h / 2f, h / 2f) }
        }
        path.moveTo(x, y)
        val tx = rng.range(-w * 0.2f, w * 0.2f)
        val ty = rng.range(-h * 0.2f, h * 0.2f)
        val segments = 3 + rng.int(3)
        for (sgm in 1..segments) {
            val t = sgm / segments.toFloat()
            val nx = x + (tx - x) * t * 0.7f + rng.range(-0.16f, 0.16f) * unit
            val ny = y + (ty - y) * t * 0.7f + rng.range(-0.16f, 0.16f) * unit
            x = nx.coerceIn(-w / 2f + 2f, w / 2f - 2f)
            y = ny.coerceIn(-h / 2f + 2f, h / 2f - 2f)
            path.lineTo(x, y)
            if (stage >= 2 && rng.next() < 0.4f) {
                // A short branch.
                path.lineTo(x + rng.range(-0.14f, 0.14f) * unit, y + rng.range(-0.14f, 0.14f) * unit)
                path.moveTo(x, y)
            }
        }
        drawPath(path, Color(0xFF0E0A07).copy(alpha = 0.88f), style = Stroke(if (stage >= 2) 3f else 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White.copy(alpha = 0.12f), style = Stroke(1f))
    }
    if (stage >= 2) {
        // Chipped corners.
        repeat(3) {
            val cx = if (rng.next() < 0.5f) -w / 2f else w / 2f
            val cy = if (rng.next() < 0.5f) -h / 2f else h / 2f
            val sx = if (cx < 0) 1f else -1f
            val sy = if (cy < 0) 1f else -1f
            val a = rng.range(0.1f, 0.24f) * unit
            val chip = Path()
            chip.moveTo(cx, cy)
            chip.lineTo(cx + sx * a, cy)
            chip.lineTo(cx, cy + sy * a * rng.range(0.6f, 1.2f))
            chip.close()
            drawPath(chip, Color(0xFF120D09).copy(alpha = 0.85f))
        }
    }
}

/** A pennant in the owner's colour. Drawn in block-local texels, so it topples with its tower. */
internal fun DrawScope.drawFlag(owner: Int, top: Float, time: Float) {
    val poleH = 1.75f * TPM
    drawLine(Color(0xFF2A1A0E), Offset(0f, top), Offset(0f, top - poleH), 5f, StrokeCap.Round)
    drawLine(Color(0xFF8A6A45), Offset(-1f, top), Offset(-1f, top - poleH), 1.6f, StrokeCap.Round)
    drawCircle(Color(0xFFE2B93B), 4.6f, Offset(0f, top - poleH))
    val dir = if (owner == 0) 1f else -1f
    val len = 1.25f * TPM
    val hgt = 0.62f * TPM
    val y0 = top - poleH + 5f
    val flag = Path()
    val n = 8
    flag.moveTo(0f, y0)
    for (i in 1..n) {
        val t = i / n.toFloat()
        flag.lineTo(dir * len * t, y0 + hgt * 0.5f * t + sin(time * 5f - t * 5f) * 5f * t)
    }
    for (i in n downTo 0) {
        val t = i / n.toFloat()
        flag.lineTo(dir * len * t, y0 + hgt - hgt * 0.5f * t + sin(time * 5f - t * 5f) * 5f * t)
    }
    flag.close()
    drawPath(flag, Brush.horizontalGradient(listOf(PLAYER_COLORS[owner], PLAYER_DARK[owner]), startX = 0f, endX = dir * len))
    drawPath(flag, Color(0xAA1A0F0A), style = Stroke(1.6f, join = StrokeJoin.Round))
    drawCircle(Color(0xFFF2D675), hgt * 0.16f, Offset(dir * len * 0.3f, y0 + hgt * 0.5f))
}
