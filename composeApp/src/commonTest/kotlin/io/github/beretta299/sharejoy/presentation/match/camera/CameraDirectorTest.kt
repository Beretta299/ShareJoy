package io.github.beretta299.sharejoy.presentation.match.camera

import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraDirectorTest {
    private val game = Game()
    private val director = CameraDirector(game)
    private val home = (Balance.GAP + Balance.SCREEN_WIDTH) / 2f

    private fun step(frames: Int = 1) = repeat(frames) {
        game.update(STEP)
        game.events.clear()
        director.update(STEP, PORTRAIT)
    }

    private fun shotInFlight() = game.pieces.firstOrNull { it.kind == PieceKind.PROJECTILE && !it.hasHit }?.body

    @Test
    fun atRestEachPairedPhoneShowsItsOwnersHome() {
        step()
        assertEquals(-home, director.paired[0].cx, 0.01f)
        assertEquals(home, director.paired[1].cx, 0.01f)
    }

    @Test
    fun theSharedPhoneStartsOnThePlayerWhoIsUp() {
        step()
        assertEquals(-home, director.single.cx, 0.01f)
    }

    @Test
    fun pairedPhonesFormOneContinuousWindowWhileTheShotCrossesTheMiddle() {
        step()
        game.fire(-1.6f, -1.1f)
        var checked = 0
        var guard = 0
        while (game.phase == Phase.RESOLVING && guard++ < 1200) {
            step()
            val shot = shotInFlight() ?: continue
            if (abs(shot.x) < 1f) {
                // The right phone's left edge meets the left phone's right edge.
                val gap = director.paired[1].cx - director.paired[0].cx - Balance.SCREEN_WIDTH
                assertTrue(abs(gap) < 0.2f, "screens are $gap m apart while the shot crosses")
                assertEquals(director.paired[0].cy, director.paired[1].cy, 0.001f)
                checked++
            }
        }
        assertTrue(checked > 0, "the shot never crossed the middle")
    }

    @Test
    fun pairedCamerasReturnHomeAfterTheShot() {
        step()
        game.fire(-1.6f, -1.1f)
        var guard = 0
        while (game.phase != Phase.PLAN && guard++ < 3000) step()
        step(180)
        assertEquals(-home, director.paired[0].cx, 0.05f)
        assertEquals(home, director.paired[1].cx, 0.05f)
    }

    @Test
    fun theSharedPhoneFollowsTheShotTowardTheEnemy() {
        step()
        game.fire(-1.6f, -1.1f)
        step(90)
        assertTrue(director.single.cx > -home + 3f, "camera stayed at ${director.single.cx}")
    }

    @Test
    fun panningIsLimitedToTheBattlefield() {
        step()
        director.pan(-1000f)
        step(600)
        assertEquals(Balance.HALF_WORLD - Balance.SCREEN_WIDTH / 2f, director.single.cx, 0.05f)
    }

    private companion object {
        const val STEP = 1f / 60f
        const val PORTRAIT = 9f / 19.5f
    }
}
