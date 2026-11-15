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

/** A soft drop shadow offset down and right, so stacked blocks read as solid shapes. */
internal fun DrawScope.drawShadow(view: WorldViewport, piece: Piece) {
    val body = piece.body
    val centreX = view.screenX(body.x + 0.16f)
    val centreY = view.screenY(body.y - 0.16f)
    if (centreX < -200f || centreX > size.width + 200f) return
    val halfWidthPx = body.halfWidth * view.pixelsPerMetre
    val halfHeightPx = body.halfHeight * view.pixelsPerMetre
    withTransform({
        translate(centreX, centreY)
        rotate(-body.angle * 180f / PI.toFloat(), Offset.Zero)
    }) {
        drawRect(Color(0x47000000), topLeft = Offset(-halfWidthPx, -halfHeightPx), size = Size(halfWidthPx * 2f, halfHeightPx * 2f))
    }
}

/** A textured block with chipSize bevel, cracks once damaged, and chipSize flag if it carries one. */
internal fun DrawScope.drawBlock(view: WorldViewport, piece: Piece, textures: Textures, time: Float) {
    val body = piece.body
    val centreX = view.screenX(body.x)
    val centreY = view.screenY(body.y)
    val visibleReach = (body.halfWidth + body.halfHeight + 2.5f) * view.pixelsPerMetre
    if (centreX < -visibleReach || centreX > size.width + visibleReach || centreY < -visibleReach || centreY > size.height + visibleReach) return

    // Wood grain runs along the long side of chipSize plank.
    val grainRunsVertically = piece.material == Material.WOOD && body.halfHeight > body.halfWidth
    val widthMetres = if (grainRunsVertically) body.halfHeight * 2f else body.halfWidth * 2f
    val heightMetres = if (grainRunsVertically) body.halfWidth * 2f else body.halfHeight * 2f
    val widthTexels = max(4, (widthMetres * TEXELS_PER_METRE).roundToInt())
    val heightTexels = max(4, (heightMetres * TEXELS_PER_METRE).roundToInt())
    val image = when (piece.material) {
        Material.WOOD -> textures.wood
        Material.STONE -> textures.stone
        Material.IRON -> textures.iron
    }
    val textureCell = if (piece.material == Material.IRON) TEXELS_PER_METRE else TEXELS_PER_METRE / 2
    val maxSourceX = max(0, Textures.TEXTURE_SIZE - widthTexels)
    val maxSourceY = max(0, Textures.TEXTURE_SIZE - heightTexels)
    val sourceX = min(((piece.cosmeticSeed % 7) * TEXELS_PER_METRE), maxSourceX) / textureCell * textureCell
    val sourceY = min((((piece.cosmeticSeed / 7) % 13) * textureCell), maxSourceY) / textureCell * textureCell
    val texelToPixel = view.pixelsPerMetre / TEXELS_PER_METRE

    withTransform({
        translate(centreX, centreY)
        rotate(-body.angle * 180f / PI.toFloat() + if (grainRunsVertically) 90f else 0f, Offset.Zero)
        scale(texelToPixel, texelToPixel, Offset.Zero)
    }) {
        val left = -widthTexels / 2f
        val top = -heightTexels / 2f
        drawImage(
            image,
            srcOffset = IntOffset(sourceX, sourceY), srcSize = IntSize(min(widthTexels, Textures.TEXTURE_SIZE), min(heightTexels, Textures.TEXTURE_SIZE)),
            dstOffset = IntOffset(-widthTexels / 2, -heightTexels / 2), dstSize = IntSize(widthTexels, heightTexels),
        )
        // Bevel: light from the upper left.
        drawLine(Color.White.copy(alpha = 0.30f), Offset(left + 1f, top + 1.6f), Offset(left + widthTexels - 1f, top + 1.6f), 2.4f)
        drawLine(Color.White.copy(alpha = 0.14f), Offset(left + 1.6f, top + 1f), Offset(left + 1.6f, top + heightTexels - 1f), 2.2f)
        drawLine(Color.Black.copy(alpha = 0.34f), Offset(left + 1f, top + heightTexels - 1.6f), Offset(left + widthTexels - 1f, top + heightTexels - 1.6f), 2.6f)
        drawLine(Color.Black.copy(alpha = 0.24f), Offset(left + widthTexels - 1.6f, top + 1f), Offset(left + widthTexels - 1.6f, top + heightTexels - 1f), 2.4f)
        drawRect(Color(0xFF16100B).copy(alpha = 0.75f), topLeft = Offset(left, top), size = Size(widthTexels.toFloat(), heightTexels.toFloat()), style = Stroke(1.8f))

        val damageStage = piece.damageStage
        if (damageStage > 0) drawCracks(piece.cosmeticSeed, widthTexels.toFloat(), heightTexels.toFloat(), damageStage)

        if (piece.hasFlag) drawFlag(piece.owner, if (grainRunsVertically) 0f else top, time)
    }
}

/** Cracks that wander in from the edges; more of them, plus chipped corners, at damage damageStage 2. Texel space. */
internal fun DrawScope.drawCracks(seed: Int, widthTexels: Float, heightTexels: Float, damageStage: Int) {
    val rng = Rng(seed + 17)
    if (damageStage >= 2) drawRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(-widthTexels / 2f, -heightTexels / 2f), size = Size(widthTexels, heightTexels))
    val count = if (damageStage >= 2) 5 else 2
    val crackScale = min(widthTexels, heightTexels)
    repeat(count) {
        val path = Path()
        // Start on an edge and wander toward the middle.
        var x: Float
        var y: Float
        when (rng.nextInt(4)) {
            0 -> { x = rng.range(-widthTexels / 2f, widthTexels / 2f); y = -heightTexels / 2f }
            1 -> { x = rng.range(-widthTexels / 2f, widthTexels / 2f); y = heightTexels / 2f }
            2 -> { x = -widthTexels / 2f; y = rng.range(-heightTexels / 2f, heightTexels / 2f) }
            else -> { x = widthTexels / 2f; y = rng.range(-heightTexels / 2f, heightTexels / 2f) }
        }
        path.moveTo(x, y)
        val targetX = rng.range(-widthTexels * 0.2f, widthTexels * 0.2f)
        val targetY = rng.range(-heightTexels * 0.2f, heightTexels * 0.2f)
        val segments = 3 + rng.nextInt(3)
        for (segment in 1..segments) {
            val progress = segment / segments.toFloat()
            val nextX = x + (targetX - x) * progress * 0.7f + rng.range(-0.16f, 0.16f) * crackScale
            val nextY = y + (targetY - y) * progress * 0.7f + rng.range(-0.16f, 0.16f) * crackScale
            x = nextX.coerceIn(-widthTexels / 2f + 2f, widthTexels / 2f - 2f)
            y = nextY.coerceIn(-heightTexels / 2f + 2f, heightTexels / 2f - 2f)
            path.lineTo(x, y)
            if (damageStage >= 2 && rng.next() < 0.4f) {
                // A short branch.
                path.lineTo(x + rng.range(-0.14f, 0.14f) * crackScale, y + rng.range(-0.14f, 0.14f) * crackScale)
                path.moveTo(x, y)
            }
        }
        drawPath(path, Color(0xFF0E0A07).copy(alpha = 0.88f), style = Stroke(if (damageStage >= 2) 3f else 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White.copy(alpha = 0.12f), style = Stroke(1f))
    }
    if (damageStage >= 2) {
        // Chipped corners.
        repeat(3) {
            val cornerX = if (rng.next() < 0.5f) -widthTexels / 2f else widthTexels / 2f
            val cornerY = if (rng.next() < 0.5f) -heightTexels / 2f else heightTexels / 2f
            val inwardX = if (cornerX < 0) 1f else -1f
            val inwardY = if (cornerY < 0) 1f else -1f
            val chipSize = rng.range(0.1f, 0.24f) * crackScale
            val chip = Path()
            chip.moveTo(cornerX, cornerY)
            chip.lineTo(cornerX + inwardX * chipSize, cornerY)
            chip.lineTo(cornerX, cornerY + inwardY * chipSize * rng.range(0.6f, 1.2f))
            chip.close()
            drawPath(chip, Color(0xFF120D09).copy(alpha = 0.85f))
        }
    }
}

/** A pennant in the owner's colour. Drawn in block-local texels, so it topples with its tower. */
internal fun DrawScope.drawFlag(owner: Int, top: Float, time: Float) {
    val poleHeight = 1.75f * TEXELS_PER_METRE
    drawLine(Color(0xFF2A1A0E), Offset(0f, top), Offset(0f, top - poleHeight), 5f, StrokeCap.Round)
    drawLine(Color(0xFF8A6A45), Offset(-1f, top), Offset(-1f, top - poleHeight), 1.6f, StrokeCap.Round)
    drawCircle(Color(0xFFE2B93B), 4.6f, Offset(0f, top - poleHeight))
    val direction = if (owner == 0) 1f else -1f
    val flagLength = 1.25f * TEXELS_PER_METRE
    val flagHeight = 0.62f * TEXELS_PER_METRE
    val flagTop = top - poleHeight + 5f
    val flag = Path()
    val waveSegments = 8
    flag.moveTo(0f, flagTop)
    for (point in 1..waveSegments) {
        val along = point / waveSegments.toFloat()
        flag.lineTo(direction * flagLength * along, flagTop + flagHeight * 0.5f * along + sin(time * 5f - along * 5f) * 5f * along)
    }
    for (point in waveSegments downTo 0) {
        val along = point / waveSegments.toFloat()
        flag.lineTo(direction * flagLength * along, flagTop + flagHeight - flagHeight * 0.5f * along + sin(time * 5f - along * 5f) * 5f * along)
    }
    flag.close()
    drawPath(flag, Brush.horizontalGradient(listOf(PLAYER_COLORS[owner], PLAYER_DARK[owner]), startX = 0f, endX = direction * flagLength))
    drawPath(flag, Color(0xAA1A0F0A), style = Stroke(1.6f, join = StrokeJoin.Round))
    drawCircle(Color(0xFFF2D675), flagHeight * 0.16f, Offset(direction * flagLength * 0.3f, flagTop + flagHeight * 0.5f))
}
