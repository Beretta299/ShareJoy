package io.github.beretta299.sharejoy.presentation.match.camera

import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import io.github.beretta299.sharejoy.model.PhysicalScreen
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
class CameraDirector(
    private val game: Game,
    /**
     * Real screen sizes of the two paired phones, left and right, or null when unknown. With them both phones
     * draw at the same real-world scale and put the ground the same distance above their bottom edges, so phones
     * of any size standing on one table line up along the ground line.
     */
    private val pairedScreens: List<PhysicalScreen>? = null,
) {
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
    private fun restY(viewHeight: Float = viewHeight()) = viewHeight * GROUND_LINE

    private fun homeX(player: Int) = game.sideSign(player) * (Balance.OPEN_GROUND_WIDTH + Balance.SCREEN_WIDTH) / 2f

    private fun flyingShot() = if (game.phase == Phase.RESOLVING) {
        game.pieces.firstOrNull { it.kind == PieceKind.PROJECTILE && !it.hasHit }?.body
    } else {
        null
    }

    /** Keeps a high lob on screen. */
    private fun followY(shotY: Float, viewHeight: Float = viewHeight()) =
        max(restY(viewHeight), shotY - viewHeight * LOB_MARGIN)

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
        // The seam between the phones: it starts at the shooter's edge of the open ground and ends at the target's.
        val seam = flying?.let { (it.x + it.velocityX * PAIRED_LEAD).coerceIn(-Balance.OPEN_GROUND_WIDTH / 2f, Balance.OPEN_GROUND_WIDTH / 2f) }
        // Both phones rise by the same amount to keep a high lob in view, so the ground stays level across them.
        val lift = if (flying != null) followY(flying.y, pairedReferenceHeight()) - restY(pairedReferenceHeight()) else 0f
        // The idle phone has to close the whole gap before the shot reaches the seam, so it moves briskly.
        val speed = if (flying != null) PAIRED_FLIGHT_SPEED else PAIRED_HOME_SPEED
        for (player in 0..1) {
            val innerEdge = seam ?: (game.sideSign(player) * Balance.OPEN_GROUND_WIDTH / 2f)
            val targetX = innerEdge + game.sideSign(player) * pairedViewWidth(player) / 2f
            val targetY = pairedViewHeight(player) / 2f - pairedGroundHeight() + lift
            glide(paired[player], targetX, targetY, dt, speed, snap, pairedViewWidth(player))
        }
    }

    // ------------------------------------------------------------------ paired screen sizes, in world metres

    /** World metres per millimetre of glass: the narrower phone shows exactly [Balance.SCREEN_WIDTH]. */
    private fun metresPerMillimetre(screens: List<PhysicalScreen>) = Balance.SCREEN_WIDTH / screens.minOf { it.widthMm }

    private fun pairedViewWidth(player: Int): Float {
        val screens = pairedScreens ?: return Balance.SCREEN_WIDTH
        return screens[player].widthMm * metresPerMillimetre(screens)
    }

    private fun pairedViewHeight(player: Int): Float {
        val screens = pairedScreens ?: return viewHeight()
        return screens[player].heightMm * metresPerMillimetre(screens)
    }

    /** The shorter phone decides how much sky there is to work with. */
    private fun pairedReferenceHeight() = minOf(pairedViewHeight(0), pairedViewHeight(1))

    /** How far the ground sits above the bottom edge of both phones, in world metres. */
    private fun pairedGroundHeight() = pairedReferenceHeight() * (0.5f - GROUND_LINE)

    private fun glide(
        camera: Camera, targetX: Float, targetY: Float, dt: Float, speed: Float, snap: Boolean,
        viewWidth: Float = Balance.SCREEN_WIDTH,
    ) {
        camera.viewWidth = viewWidth
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
