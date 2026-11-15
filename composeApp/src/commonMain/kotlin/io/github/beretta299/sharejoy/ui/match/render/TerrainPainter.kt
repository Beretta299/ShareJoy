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

/** The gorge at the seam: a dark drop with rising mist and a broken rope bridge. */
internal fun DrawScope.drawGorge(view: WorldViewport) {
    val metre = view.pixelsPerMetre
    val gorgeHalfWidth = Balance.GORGE_HALF_WIDTH
    val left = view.screenX(-gorgeHalfWidth)
    val right = view.screenX(gorgeHalfWidth)
    val top = view.screenY(0f)
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF4A423B), Color(0xFF1A1512), Color(0xFF070606)), startY = top, endY = top + metre * 9f),
        topLeft = Offset(left, top), size = Size(right - left, max(0f, size.height - top) + metre),
    )
    // Mist rising from the river far below.
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x33AEC6D6)), startY = top + metre * 1.5f, endY = top + metre * 6f),
        topLeft = Offset(left, top + metre * 1.5f), size = Size(right - left, metre * 6f),
    )
    // A broken rope bridge: posts on both edges, ropes hanging into the gorge.
    for (side in listOf(-1f, 1f)) {
        val postX = view.screenX(side * (gorgeHalfWidth + 0.45f))
        drawLine(Color(0xFF3B2716), Offset(postX, top), Offset(postX, top - metre * 1.25f), metre * 0.2f, StrokeCap.Round)
        drawLine(Color(0xFF6B4A2B), Offset(postX - metre * 0.03f, top), Offset(postX - metre * 0.03f, top - metre * 1.22f), metre * 0.08f, StrokeCap.Round)
        val rope = Path()
        rope.moveTo(postX, top - metre * 1.1f)
        rope.quadraticTo(postX - side * metre * 0.9f, top + metre * 0.4f, postX - side * metre * 1.15f, top + metre * 2.6f)
        drawPath(rope, Color(0xFF2A1C10), style = Stroke(metre * 0.05f, cap = StrokeCap.Round))
        for (index in 1..3) {
            val plankY = top + metre * (0.5f + index * 0.55f)
            val plankX = postX - side * metre * (0.85f + index * 0.08f)
            drawLine(Color(0xFF5A3D22), Offset(plankX - metre * 0.3f, plankY), Offset(plankX + metre * 0.3f, plankY + metre * 0.12f), metre * 0.09f)
        }
    }
}

/** Soil, cliff edges, buried stones and grass on both sides of the gorge. */
internal fun DrawScope.drawGround(view: WorldViewport) {
    val metre = view.pixelsPerMetre
    val top = view.screenY(0f)
    if (top > size.height + 40f) return
    val gorgeHalfWidth = Balance.GORGE_HALF_WIDTH
    for (side in listOf(-1f, 1f)) {
        val sideStartX = if (side < 0) -200f else view.screenX(gorgeHalfWidth)
        val sideEndX = if (side < 0) view.screenX(-gorgeHalfWidth) else size.width + 200f
        if (sideEndX < 0f || sideStartX > size.width) continue
        val visibleLeft = max(sideStartX, -50f)
        val visibleRight = min(sideEndX, size.width + 50f)
        // Soil.
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF7A5633), Color(0xFF4B331E), Color(0xFF2A1C10)), startY = top, endY = top + metre * 6f),
            topLeft = Offset(visibleLeft, top), size = Size(visibleRight - visibleLeft, size.height - top + metre),
        )
        // Rocky cliff face at the gorge edge.
        val edge = if (side < 0) sideEndX else sideStartX
        val cliffWidth = metre * 0.9f
        val cliffLeft = if (side < 0) edge - cliffWidth else edge
        drawRect(
            Brush.horizontalGradient(
                if (side < 0) listOf(Color(0x004B331E), Color(0xFF6A6058), Color(0xFF3A342F)) else listOf(Color(0xFF3A342F), Color(0xFF6A6058), Color(0x004B331E)),
                startX = cliffLeft, endX = cliffLeft + cliffWidth,
            ),
            topLeft = Offset(cliffLeft, top), size = Size(cliffWidth, size.height - top + metre),
        )
    }

    // Strata, buried stones and grass, only where visible.
    val from = floor(view.leftEdgeX).toInt() - 1
    val to = view.rightEdgeX.toInt() + 1
    for (i in from..to) {
        val worldX = i.toFloat()
        if (abs(worldX + 0.5f) < gorgeHalfWidth + 0.4f) continue
        for (index in 0 until 2) {
            val offsetX = hash(i * 13 + index * 101)
            val offsetY = hash(i * 29 + index * 53)
            val stoneX = view.screenX(worldX + offsetX)
            val stoneY = top + metre * (0.6f + offsetY * 4.2f)
            val stoneRadius = metre * (0.1f + hash(i * 7 + index) * 0.22f)
            drawOval(Color(0xFF2E2014).copy(alpha = 0.55f), Offset(stoneX - stoneRadius, stoneY - stoneRadius * 0.6f + stoneRadius * 0.2f), Size(stoneRadius * 2f, stoneRadius * 1.3f))
            drawOval(Color(0xFF8C7F70).copy(alpha = 0.8f), Offset(stoneX - stoneRadius, stoneY - stoneRadius * 0.6f), Size(stoneRadius * 2f, stoneRadius * 1.2f))
        }
    }
    for (side in listOf(-1f, 1f)) {
        val sideStartX = if (side < 0) -200f else view.screenX(gorgeHalfWidth)
        val sideEndX = if (side < 0) view.screenX(-gorgeHalfWidth) else size.width + 200f
        val visibleLeft = max(sideStartX, -50f)
        val visibleRight = min(sideEndX, size.width + 50f)
        if (visibleRight <= visibleLeft) continue
        val turfThickness = metre * 0.34f
        drawRect(Color(0x66201408), topLeft = Offset(visibleLeft, top + turfThickness * 0.8f), size = Size(visibleRight - visibleLeft, turfThickness * 0.5f))
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF8CC152), Color(0xFF4F8A2C)), startY = top - turfThickness * 0.2f, endY = top + turfThickness),
            topLeft = Offset(visibleLeft, top - turfThickness * 0.12f), size = Size(visibleRight - visibleLeft, turfThickness * 1.1f),
        )
    }
    val blades = Path()
    var bladeIndex = floor(view.leftEdgeX * 3f).toInt() - 1
    val lastBladeIndex = (view.rightEdgeX * 3f).toInt() + 1
    while (bladeIndex <= lastBladeIndex) {
        val worldX = bladeIndex / 3f + hash(bladeIndex) * 0.2f
        if (abs(worldX) > gorgeHalfWidth + 0.15f) {
            val bladeX = view.screenX(worldX)
            val bladeHeight = metre * (0.14f + hash(bladeIndex + 500) * 0.2f)
            val lean = (hash(bladeIndex + 900) - 0.5f) * metre * 0.16f
            blades.moveTo(bladeX - metre * 0.05f, top)
            blades.lineTo(bladeX + lean, top - bladeHeight)
            blades.lineTo(bladeX + metre * 0.05f, top)
            blades.close()
        }
        bladeIndex++
    }
    drawPath(blades, Color(0xFF6FAE3E))
}
