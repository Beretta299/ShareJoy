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
    private var lastCurrent = game.current
    private var lingerX = 0f

    /** Slides the shared-phone camera sideways, to peek at the enemy tower while planning. */
    fun pan(worldDx: Float) {
        panOffset = (panOffset - worldDx).coerceIn(-2f * Balance.HALF_WORLD, 2f * Balance.HALF_WORLD)
    }

    fun resetPan() {
        panOffset = 0f
        lastCurrent = game.current
    }

    fun onShotFired(x: Float) {
        lingerX = x
    }

    /** [screenAspect] is the width of one phone screen divided by its height. */
    fun update(dt: Float, screenAspect: Float) {
        aspect = screenAspect
        if (game.current != lastCurrent) resetPan()
        val snap = !sized
        sized = true
        updateSingle(dt, snap)
        updatePaired(dt, snap)
    }

    private fun viewHeight() = Balance.SCREEN_WIDTH / aspect

    /** Height of the camera centre that puts the ground [GROUND_LINE] of a screen below the middle. */
    private fun restY() = viewHeight() * GROUND_LINE

    private fun homeX(player: Int) = game.side(player) * (Balance.GAP + Balance.SCREEN_WIDTH) / 2f

    private fun flyingShot() = if (game.phase == Phase.RESOLVING) {
        game.pieces.firstOrNull { it.kind == PieceKind.PROJECTILE && !it.hasHit }?.body
    } else {
        null
    }

    /** Keeps a high lob on screen. */
    private fun followY(shotY: Float) = max(restY(), shotY - viewHeight() * LOB_MARGIN)

    private fun updateSingle(dt: Float, snap: Boolean) {
        var ty = restY()
        val tx = when (game.phase) {
            Phase.PLAN, Phase.BUILDING, Phase.HANDOVER -> homeX(game.current) + panOffset
            Phase.RESOLVING -> {
                val flying = flyingShot()
                if (flying != null) {
                    ty = followY(flying.y)
                    lingerX = flying.x
                    flying.x + flying.vx * SINGLE_LEAD
                } else {
                    // Stay on the tower that was hit while it settles.
                    val shot = game.pieces.firstOrNull { it.kind == PieceKind.PROJECTILE }
                    val hitX = shot?.body?.x ?: lingerX
                    val enemy = 1 - game.current
                    if (hitX * game.side(enemy) >= -Balance.GORGE_HALF_WIDTH) homeX(enemy) else homeX(game.current)
                }
            }
            Phase.ROUND_OVER, Phase.MATCH_OVER -> homeX(if (game.keeperDown[0]) 0 else 1)
        }
        val limit = Balance.HALF_WORLD - Balance.SCREEN_WIDTH / 2f
        glide(single, tx.coerceIn(-limit, limit), ty, dt, SINGLE_SPEED, snap)
    }

    private fun updatePaired(dt: Float, snap: Boolean) {
        val flying = flyingShot()
        // Centre of the joined window: it starts on the shooter's home and ends on the target's.
        val joined = flying?.let { (it.x + it.vx * PAIRED_LEAD).coerceIn(-Balance.GAP / 2f, Balance.GAP / 2f) }
        val ty = if (flying != null) followY(flying.y) else restY()
        // The idle phone has to close the whole gap before the shot reaches the seam, so it moves briskly.
        val speed = if (flying != null) PAIRED_FLIGHT_SPEED else PAIRED_HOME_SPEED
        for (player in 0..1) {
            val tx = if (joined != null) joined + game.side(player) * Balance.SCREEN_WIDTH / 2f else homeX(player)
            glide(paired[player], tx, ty, dt, speed, snap)
        }
    }

    private fun glide(camera: Camera, tx: Float, ty: Float, dt: Float, speed: Float, snap: Boolean) {
        camera.viewW = Balance.SCREEN_WIDTH
        val k = if (snap) 1f else 1f - exp(-dt * speed)
        camera.cx += (tx - camera.cx) * k
        camera.cy += (ty - camera.cy) * k
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
