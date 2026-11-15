package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.presentation.match.MatchScene
import io.github.beretta299.sharejoy.util.darken
import kotlin.math.abs
import kotlin.math.sin

internal fun DrawScope.drawSlingshot(view: WorldView, scene: MatchScene, owner: Int, front: Boolean) {
    val game = scene.game
    val x = game.slingshotX(owner)
    val s = view.scale
    val baseP = Offset(view.x(x), view.y(0f))
    val forkP = Offset(view.x(x), view.y(1.35f))
    // The two prongs sit one behind the other, seen slightly from the side.
    val backTip = Offset(view.x(x + 0.34f), view.y(Balance.SLINGSHOT_Y + 0.12f))
    val frontTip = Offset(view.x(x - 0.34f), view.y(Balance.SLINGSHOT_Y + 0.02f))

    val active = owner == game.current && game.phase == Phase.PLAN
    val pouch = if (active && scene.aiming) {
        Offset(view.x(x + scene.pullX), view.y(Balance.SLINGSHOT_Y + scene.pullY))
    } else {
        Offset(view.x(x), view.y(Balance.SLINGSHOT_Y - 0.18f))
    }
    val dark = Color(0xFF3A2413)
    val mid = Color(0xFF7B5330)
    val light = Color(0xFFA9794A)
    val band = Color(0xFF2A1A12)

    if (!front) {
        drawLine(band, backTip, pouch, s * 0.08f, StrokeCap.Round)
        drawLine(dark, forkP, backTip, s * 0.3f, StrokeCap.Round)
        drawLine(mid.darken(0.08f), forkP, backTip, s * 0.19f, StrokeCap.Round)
        return
    }

    if (active) {
        // The loaded shot rests in the pouch.
        val ammo = game.activePlayer.selectedAmmo
        val wx = (pouch.x - view.w / 2f - view.ox) / s + view.cx
        val wy = view.cy - (pouch.y - view.h / 2f - view.oy) / s
        drawProjectile(view, wx, wy, 0f, ammo.radius, ammo, 7, scene.time, 0f, 0f)
    }
    drawLine(dark, baseP, forkP, s * 0.36f, StrokeCap.Round)
    drawLine(mid, baseP, forkP, s * 0.24f, StrokeCap.Round)
    drawLine(light.copy(alpha = 0.6f), baseP + Offset(-s * 0.05f, 0f), forkP + Offset(-s * 0.05f, 0f), s * 0.06f, StrokeCap.Round)
    drawLine(dark, forkP, frontTip, s * 0.3f, StrokeCap.Round)
    drawLine(mid, forkP, frontTip, s * 0.19f, StrokeCap.Round)
    // Rope binding at the fork and the leather band to the pouch.
    for (k in 0..2) {
        val y = forkP.y + s * (0.05f + k * 0.09f)
        drawLine(Color(0xFFD9C29A), Offset(forkP.x - s * 0.17f, y), Offset(forkP.x + s * 0.17f, y - s * 0.04f), s * 0.05f, StrokeCap.Round)
    }
    drawLine(band, frontTip, pouch, s * 0.09f, StrokeCap.Round)
    drawCircle(Color(0xFF4A2F1C), s * 0.13f, pouch)

    // A ring on the ground marks whose turn it is.
    if (active && !scene.aiming) {
        val pulse = 0.5f + 0.5f * sin(scene.time * 4f)
        drawCircle(
            Color.White.copy(alpha = 0.25f + 0.3f * pulse), s * (0.75f + 0.12f * pulse),
            Offset(view.x(x), view.y(Balance.SLINGSHOT_Y - 0.18f)), style = Stroke(s * 0.06f),
        )
    }
}

internal fun DrawScope.drawTrajectory(view: WorldView, scene: MatchScene) {
    val game = scene.game
    val x0 = scene.anchorX + scene.pullX
    val y0 = scene.anchorY + scene.pullY
    val vx = scene.launchVx()
    val vy = scene.launchVy()
    if (vx * vx + vy * vy < 4f) return
    val total = game.aimSeconds()
    var t = 0.07f
    var i = 0
    while (t < total) {
        val wx = x0 + vx * t
        val wy = y0 + vy * t + 0.5f * game.world.gravity * t * t
        if (wy < 0f && abs(wx) > Balance.GORGE_HALF_WIDTH) break
        val fade = 1f - t / total
        val c = Offset(view.x(wx), view.y(wy))
        val r = view.scale * (0.07f + 0.07f * fade)
        drawCircle(Color.Black.copy(alpha = 0.25f * fade + 0.1f), r * 1.35f, c)
        drawCircle(Color.White.copy(alpha = 0.55f * fade + 0.4f), r, c)
        t += 0.065f
        i++
    }
}
