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

    val anchorX: Float get() = game.slingshotX(game.current)
    val anchorY: Float get() = Balance.SLINGSHOT_Y

    /** Returns true if the press landed on the active player's slingshot and grabbed it. */
    fun grab(wx: Float, wy: Float): Boolean {
        if (game.phase != Phase.PLAN) return false
        val dx = wx - anchorX
        val dy = wy - anchorY
        if (dx * dx + dy * dy > GRAB_RADIUS * GRAB_RADIUS) return false
        active = true
        dragTo(wx, wy)
        return true
    }

    fun dragTo(wx: Float, wy: Float) {
        if (!active) return
        var dx = wx - anchorX
        var dy = wy - anchorY
        val length = sqrt(dx * dx + dy * dy)
        if (length > Balance.MAX_PULL) {
            dx *= Balance.MAX_PULL / length
            dy *= Balance.MAX_PULL / length
        }
        // The pouch cannot go underground.
        if (anchorY + dy < MIN_POUCH_HEIGHT) dy = MIN_POUCH_HEIGHT - anchorY
        pullX = dx
        pullY = dy
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
    fun launchVx(): Float = -pullX * game.launchSpeed() / Balance.MAX_PULL
    fun launchVy(): Float = -pullY * game.launchSpeed() / Balance.MAX_PULL

    companion object {
        const val GRAB_RADIUS = 2.2f
        private const val MIN_POUCH_HEIGHT = 0.45f
    }
}
