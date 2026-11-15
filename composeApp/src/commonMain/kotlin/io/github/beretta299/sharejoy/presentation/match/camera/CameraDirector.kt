package io.github.beretta299.sharejoy.presentation.match.camera

import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import kotlin.math.exp
import kotlin.math.max

/**
 * Decides where every screen looks.
 *
 * [single] is the camera of one shared phone: it shows one half and slides to whoever is up.
 * [paired] are the cameras of two phones standing side by side, left and right. At rest each shows its owner's
 * home. While a shot is in the air the two lock together into one continuous window that travels with the shot
 * across the open ground, so it leaves one screen and enters the other at the same height and the same instant.
 * After the hit each camera glides back home.
 */
class CameraDirector(private val game: Game) {
    val single = Camera()
    val paired = listOf(Camera(), Camera())

    private var aspect = 0.46f
    private var sized = false
    private var panOffset = 0f
    private var lastCurrent = game.currentPlayer
    private var lingerX = 0f

    /** Slides the shared-phone camera sideways, to peek at the enemy tower while planning. */
    fun pan(worldDx: Float) {
        panOffset = (panOffset - worldDx).coerceIn(-2f * Balance.HALF_FIELD_WIDTH, 2f * Balance.HALF_FIELD_WIDTH)
    }

    fun resetPan() {
        panOffset = 0f
        lastCurrent = game.currentPlayer
    }

    fun onShotFired(x: Float) {
        lingerX = x
    }

    /** [screenAspect] is the width of one phone screen divided by its height. */
    fun update(dt: Float, screenAspect: Float) {
        aspect = screenAspect
        if (game.currentPlayer != lastCurrent) resetPan()
        val snap = !sized
        sized = true
        updateSingle(dt, snap)
        updatePaired(dt, snap)
    }

    private fun viewHeight() = Balance.SCREEN_WIDTH / aspect

    /** Height of the camera centre that puts the ground [GROUND_LINE] of a screen below the middle. */
    private fun restY() = viewHeight() * GROUND_LINE

    private fun homeX(player: Int) = game.sideSign(player) * (Balance.OPEN_GROUND_WIDTH + Balance.SCREEN_WIDTH) / 2f

    private fun flyingShot() = if (game.phase == Phase.RESOLVING) {
        game.pieces.firstOrNull { it.kind == PieceKind.PROJECTILE && !it.hasHit }?.body
    } else {
        null
    }

    /** Keeps a high lob on screen. */
    private fun followY(shotY: Float) = max(restY(), shotY - viewHeight() * LOB_MARGIN)

    private fun updateSingle(dt: Float, snap: Boolean) {
        var targetY = restY()
        val targetX = when (game.phase) {
            Phase.PLAN, Phase.BUILDING, Phase.HANDOVER -> homeX(game.currentPlayer) + panOffset
            Phase.RESOLVING -> {
                val flying = flyingShot()
                if (flying != null) {
                    targetY = followY(flying.y)
                    lingerX = flying.x
                    flying.x + flying.velocityX * SINGLE_LEAD
                } else {
                    // Stay on the tower that was hit while it settles.
                    val shot = game.pieces.firstOrNull { it.kind == PieceKind.PROJECTILE }
                    val hitX = shot?.body?.x ?: lingerX
                    val enemy = 1 - game.currentPlayer
                    if (hitX * game.sideSign(enemy) >= -Balance.GORGE_HALF_WIDTH) homeX(enemy) else homeX(game.currentPlayer)
                }
            }
            Phase.ROUND_OVER, Phase.MATCH_OVER -> homeX(if (game.keeperDown[0]) 0 else 1)
        }
        val limit = Balance.HALF_FIELD_WIDTH - Balance.SCREEN_WIDTH / 2f
        glide(single, targetX.coerceIn(-limit, limit), targetY, dt, SINGLE_SPEED, snap)
    }

    private fun updatePaired(dt: Float, snap: Boolean) {
        val flying = flyingShot()
        // Centre of the joined window: it starts on the shooter's home and ends on the target's.
        val joined = flying?.let { (it.x + it.velocityX * PAIRED_LEAD).coerceIn(-Balance.OPEN_GROUND_WIDTH / 2f, Balance.OPEN_GROUND_WIDTH / 2f) }
        val targetY = if (flying != null) followY(flying.y) else restY()
        // The idle phone has to close the whole gap before the shot reaches the seam, so it moves briskly.
        val speed = if (flying != null) PAIRED_FLIGHT_SPEED else PAIRED_HOME_SPEED
        for (player in 0..1) {
            val targetX = if (joined != null) joined + game.sideSign(player) * Balance.SCREEN_WIDTH / 2f else homeX(player)
            glide(paired[player], targetX, targetY, dt, speed, snap)
        }
    }

    private fun glide(camera: Camera, targetX: Float, targetY: Float, dt: Float, speed: Float, snap: Boolean) {
        camera.viewWidth = Balance.SCREEN_WIDTH
        val blend = if (snap) 1f else 1f - exp(-dt * speed)
        camera.centerX += (targetX - camera.centerX) * blend
        camera.centerY += (targetY - camera.centerY) * blend
    }

    companion object {
        /** The ground sits this far below the middle of the screen, as a fraction of the view height. */
        const val GROUND_LINE = 0.25f
        private const val LOB_MARGIN = 0.22f
        private const val SINGLE_LEAD = 0.25f
        private const val PAIRED_LEAD = 0.3f
        private const val SINGLE_SPEED = 3.2f
        private const val PAIRED_FLIGHT_SPEED = 10f
        private const val PAIRED_HOME_SPEED = 4f
    }
}
