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

private val WOOD_DARK = Color(0xFF3A2413)
private val WOOD_MID = Color(0xFF7B5330)
private val WOOD_LIGHT = Color(0xFFA9794A)
private val BAND = Color(0xFF2A1A12)
private val ROPE = Color(0xFFD9C29A)
private val POUCH = Color(0xFF4A2F1C)

/** Height of the fork where the two prongs split, in metres above the ground. */
private const val FORK_HEIGHT = 1.35f
private const val PRONG_SPREAD = 0.34f

/** How far the empty pouch hangs below the prong tips. */
private const val POUCH_REST_DROP = 0.18f
private const val SEED_OF_LOADED_SHOT = 7

/**
 * Draws a player's slingshot in two passes: the back prong and band behind the castles and shots ([front] false),
 * then the trunk, front prong, pouch and loaded shot on top ([front] true).
 */
internal fun DrawScope.drawSlingshot(view: WorldViewport, scene: MatchScene, player: Int, front: Boolean) {
    val game = scene.game
    val x = game.slingshotX(player)
    val metre = view.pixelsPerMetre
    val base = Offset(view.screenX(x), view.screenY(0f))
    val fork = Offset(view.screenX(x), view.screenY(FORK_HEIGHT))
    // The two prongs sit one behind the other, seen slightly from the side.
    val backTip = Offset(view.screenX(x + PRONG_SPREAD), view.screenY(Balance.SLINGSHOT_Y + 0.12f))
    val frontTip = Offset(view.screenX(x - PRONG_SPREAD), view.screenY(Balance.SLINGSHOT_Y + 0.02f))
    val restingPouch = Offset(view.screenX(x), view.screenY(Balance.SLINGSHOT_Y - POUCH_REST_DROP))

    val isActive = player == game.currentPlayer && game.phase == Phase.PLAN
    val pouch = if (isActive && scene.aiming) {
        Offset(view.screenX(x + scene.pullX), view.screenY(Balance.SLINGSHOT_Y + scene.pullY))
    } else {
        restingPouch
    }

    if (!front) {
        drawLine(BAND, backTip, pouch, metre * 0.08f, StrokeCap.Round)
        drawLine(WOOD_DARK, fork, backTip, metre * 0.3f, StrokeCap.Round)
        drawLine(WOOD_MID.darken(0.08f), fork, backTip, metre * 0.19f, StrokeCap.Round)
        return
    }

    if (isActive) {
        val ammo = game.activePlayer.selectedAmmo
        drawProjectile(view, view.worldX(pouch.x), view.worldY(pouch.y), 0f, ammo.radius, ammo, SEED_OF_LOADED_SHOT, scene.time)
    }
    drawTrunkAndFrontProng(base, fork, frontTip, metre)
    drawLine(BAND, frontTip, pouch, metre * 0.09f, StrokeCap.Round)
    drawCircle(POUCH, metre * 0.13f, pouch)
    if (isActive && !scene.aiming) drawTurnMarker(restingPouch, metre, scene.time)
}

private fun DrawScope.drawTrunkAndFrontProng(base: Offset, fork: Offset, frontTip: Offset, metre: Float) {
    drawLine(WOOD_DARK, base, fork, metre * 0.36f, StrokeCap.Round)
    drawLine(WOOD_MID, base, fork, metre * 0.24f, StrokeCap.Round)
    val highlightShift = Offset(-metre * 0.05f, 0f)
    drawLine(WOOD_LIGHT.copy(alpha = 0.6f), base + highlightShift, fork + highlightShift, metre * 0.06f, StrokeCap.Round)
    drawLine(WOOD_DARK, fork, frontTip, metre * 0.3f, StrokeCap.Round)
    drawLine(WOOD_MID, fork, frontTip, metre * 0.19f, StrokeCap.Round)
    // Rope binding at the fork.
    for (turn in 0..2) {
        val y = fork.y + metre * (0.05f + turn * 0.09f)
        drawLine(ROPE, Offset(fork.x - metre * 0.17f, y), Offset(fork.x + metre * 0.17f, y - metre * 0.04f), metre * 0.05f, StrokeCap.Round)
    }
}

/** A pulsing ring around the pouch marks whose turn it is. */
private fun DrawScope.drawTurnMarker(centre: Offset, metre: Float, time: Float) {
    val pulse = 0.5f + 0.5f * sin(time * 4f)
    drawCircle(
        Color.White.copy(alpha = 0.25f + 0.3f * pulse), metre * (0.75f + 0.12f * pulse), centre,
        style = Stroke(metre * 0.06f),
    )
}

/** Dotted arc showing where the shot will fly. Longer with each Aim upgrade. */
internal fun DrawScope.drawTrajectory(view: WorldViewport, scene: MatchScene) {
    val game = scene.game
    val startX = scene.anchorX + scene.pullX
    val startY = scene.anchorY + scene.pullY
    val velocityX = scene.launchVelocityX()
    val velocityY = scene.launchVelocityY()
    if (velocityX * velocityX + velocityY * velocityY < MIN_GUIDE_SPEED_SQUARED) return
    val guideSeconds = game.aimGuideSeconds()
    var seconds = GUIDE_FIRST_DOT_SECONDS
    while (seconds < guideSeconds) {
        val x = startX + velocityX * seconds
        val y = startY + velocityY * seconds + 0.5f * game.world.gravity * seconds * seconds
        val belowGround = y < 0f && abs(x) > Balance.GORGE_HALF_WIDTH
        if (belowGround) break
        val fade = 1f - seconds / guideSeconds
        val dot = Offset(view.screenX(x), view.screenY(y))
        val dotRadius = view.pixelsPerMetre * (0.07f + 0.07f * fade)
        drawCircle(Color.Black.copy(alpha = 0.25f * fade + 0.1f), dotRadius * 1.35f, dot)
        drawCircle(Color.White.copy(alpha = 0.55f * fade + 0.4f), dotRadius, dot)
        seconds += GUIDE_DOT_INTERVAL_SECONDS
    }
}

private const val MIN_GUIDE_SPEED_SQUARED = 4f
private const val GUIDE_FIRST_DOT_SECONDS = 0.07f
private const val GUIDE_DOT_INTERVAL_SECONDS = 0.065f
