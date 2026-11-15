package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Piece
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Shots faster than this (m/s) get a motion streak. */
private const val STREAK_MIN_SPEED = 6f
private const val QUARTER_TURN = (PI / 2).toFloat()
private val SHOT_SHADOW = Color(0x40000000)
private val SHOT_OUTLINE = Color(0xCC14100C)

internal fun DrawScope.drawProjectile(view: WorldViewport, shot: Piece, time: Float) {
    val body = shot.body
    drawProjectile(
        view, body.x, body.y, body.angle, body.radius, shot.ammo, shot.cosmeticSeed, time,
        velocityX = body.velocityX, velocityY = body.velocityY,
    )
}

/** Draws a shot of [ammo] at world position ([worldX], [worldY]). Also used for the shot resting in the pouch. */
internal fun DrawScope.drawProjectile(
    view: WorldViewport, worldX: Float, worldY: Float, angle: Float, radius: Float, ammo: Ammo, seed: Int, time: Float,
    velocityX: Float = 0f, velocityY: Float = 0f,
) {
    val centre = Offset(view.screenX(worldX), view.screenY(worldY))
    if (centre.x < -OFFSCREEN_MARGIN || centre.x > size.width + OFFSCREEN_MARGIN) return
    val radiusPx = radius * view.pixelsPerMetre

    drawMotionStreak(centre, radiusPx, velocityX, velocityY)
    drawCircle(SHOT_SHADOW, radiusPx, centre + Offset(radiusPx * 0.18f, radiusPx * 0.2f))
    val highlight = centre + Offset(-radiusPx * 0.35f, -radiusPx * 0.4f)
    when (ammo) {
        Ammo.STONE, Ammo.SPLIT -> drawStoneBall(centre, highlight, radiusPx, angle, seed)
        Ammo.IRON -> drawIronBall(centre, highlight, radiusPx, angle)
        Ammo.KEG -> drawPowderKeg(centre, highlight, radiusPx, angle, time)
    }
    drawCircle(SHOT_OUTLINE, radiusPx, centre, style = Stroke(max(1.2f, radiusPx * 0.09f)))
}

private fun DrawScope.drawMotionStreak(centre: Offset, radiusPx: Float, velocityX: Float, velocityY: Float) {
    val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
    if (speed <= STREAK_MIN_SPEED) return
    // Screen y points down, so the vertical direction flips.
    val tail = Offset(centre.x - velocityX / speed * radiusPx * 3.2f, centre.y + velocityY / speed * radiusPx * 3.2f)
    val fade = Brush.linearGradient(listOf(Color(0x55FFFFFF), Color.Transparent), centre, tail)
    drawLine(fade, centre, tail, radiusPx * 1.5f, StrokeCap.Round)
}

private fun DrawScope.drawStoneBall(centre: Offset, highlight: Offset, radiusPx: Float, angle: Float, seed: Int) {
    val shading = Brush.radialGradient(
        listOf(Color(0xFFC9C3B6), Color(0xFF8B867B), Color(0xFF55514A)), center = highlight, radius = radiusPx * 1.7f,
    )
    drawCircle(shading, radiusPx, centre)
    // A few pits so the stone visibly rolls.
    repeat(5) { i ->
        val pitAngle = angle + i * 1.3f + hash(seed + i) * 2f
        val pitDistance = radiusPx * (0.25f + hash(seed + i * 3) * 0.5f)
        val pitRadius = radiusPx * (0.08f + hash(seed + i * 5) * 0.1f)
        drawCircle(Color(0x44201C18), pitRadius, centre + Offset(cos(pitAngle) * pitDistance, -sin(pitAngle) * pitDistance))
    }
}

private fun DrawScope.drawIronBall(centre: Offset, highlight: Offset, radiusPx: Float, angle: Float) {
    val shading = Brush.radialGradient(
        listOf(Color(0xFF9AA3AE), Color(0xFF3B4148), Color(0xFF16191D)), center = highlight, radius = radiusPx * 1.6f,
    )
    drawCircle(shading, radiusPx, centre)
    drawCircle(Color(0x99FFFFFF), radiusPx * 0.16f, highlight)
    // A casting seam across the ball shows its spin.
    val seam = Offset(cos(angle) * radiusPx * 0.9f, -sin(angle) * radiusPx * 0.9f)
    drawLine(Color(0x55000000), centre + seam, centre - seam, radiusPx * 0.12f)
}

private fun DrawScope.drawPowderKeg(centre: Offset, highlight: Offset, radiusPx: Float, angle: Float, time: Float) {
    val shading = Brush.radialGradient(
        listOf(Color(0xFFA9703A), Color(0xFF6B4320), Color(0xFF3B230F)), center = highlight, radius = radiusPx * 1.7f,
    )
    drawCircle(shading, radiusPx, centre)
    // Two iron hoops across the barrel.
    val alongX = cos(angle)
    val alongY = -sin(angle)
    val acrossX = cos(angle + QUARTER_TURN)
    val acrossY = -sin(angle + QUARTER_TURN)
    for (hoopPosition in listOf(-0.45f, 0.45f)) {
        val hoopCentre = centre + Offset(acrossX * radiusPx * hoopPosition, acrossY * radiusPx * hoopPosition)
        val halfLength = radiusPx * sqrt(1f - hoopPosition * hoopPosition)
        val half = Offset(alongX * halfLength, alongY * halfLength)
        drawLine(Color(0xFF2B2B2E), hoopCentre - half, hoopCentre + half, radiusPx * 0.16f)
    }
    // A flickering lit fuse.
    val fuse = centre + Offset(cos(angle + 1.2f) * radiusPx, -sin(angle + 1.2f) * radiusPx)
    drawCircle(Color(0xFFFFC14D), radiusPx * (0.22f + 0.1f * sin(time * 40f)), fuse)
    drawCircle(Color(0xFFFFF4C2), radiusPx * 0.1f, fuse)
}

private const val OFFSCREEN_MARGIN = 100f
