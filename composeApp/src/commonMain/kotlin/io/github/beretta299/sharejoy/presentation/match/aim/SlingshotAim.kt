package io.github.beretta299.sharejoy.presentation.match.aim

import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import kotlin.math.sqrt

/** Turns a finger dragging the slingshot pouch into a pull vector, in world metres, and fires on release. */
class SlingshotAim(private val game: Game) {
    var active = false
        private set
    var pullX = 0f
        private set
    var pullY = 0f
        private set

    val anchorX: Float get() = game.slingshotX(game.currentPlayer)
    val anchorY: Float get() = Balance.SLINGSHOT_Y

    /** Returns true if the press landed on the active player's slingshot and grabbed it. */
    fun grab(worldX: Float, worldY: Float): Boolean {
        if (game.phase != Phase.PLAN) return false
        val offsetX = worldX - anchorX
        val offsetY = worldY - anchorY
        if (offsetX * offsetX + offsetY * offsetY > GRAB_RADIUS * GRAB_RADIUS) return false
        active = true
        dragTo(worldX, worldY)
        return true
    }

    fun dragTo(worldX: Float, worldY: Float) {
        if (!active) return
        var offsetX = worldX - anchorX
        var offsetY = worldY - anchorY
        val length = sqrt(offsetX * offsetX + offsetY * offsetY)
        if (length > Balance.MAX_SLINGSHOT_PULL) {
            offsetX *= Balance.MAX_SLINGSHOT_PULL / length
            offsetY *= Balance.MAX_SLINGSHOT_PULL / length
        }
        // The pouch cannot go underground.
        if (anchorY + offsetY < MIN_POUCH_HEIGHT) offsetY = MIN_POUCH_HEIGHT - anchorY
        pullX = offsetX
        pullY = offsetY
    }

    /** Lets go of the pouch and fires. Returns false if nothing was being aimed. */
    fun release(): Boolean {
        if (!active) return false
        game.fire(pullX, pullY)
        cancel()
        return true
    }

    fun cancel() {
        active = false
        pullX = 0f
        pullY = 0f
    }

    /** Launch velocity for the current pull, used to draw the trajectory guide. */
    fun launchVelocityX(): Float = -pullX * game.launchSpeed() / Balance.MAX_SLINGSHOT_PULL
    fun launchVelocityY(): Float = -pullY * game.launchSpeed() / Balance.MAX_SLINGSHOT_PULL

    companion object {
        const val GRAB_RADIUS = 2.2f
        private const val MIN_POUCH_HEIGHT = 0.45f
    }
}
