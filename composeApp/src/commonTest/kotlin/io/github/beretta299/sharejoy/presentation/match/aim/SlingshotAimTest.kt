package io.github.beretta299.sharejoy.presentation.match.aim

import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.execute
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlingshotAimTest {
    private val game = Game()
    private val aim = SlingshotAim(game)

    @Test
    fun thePullIsCappedAtTheMaximumLength() {
        aim.grab(aim.anchorX, aim.anchorY)
        aim.dragTo(aim.anchorX - 30f, aim.anchorY)
        val length = sqrt(aim.pullX * aim.pullX + aim.pullY * aim.pullY)
        assertEquals(Balance.MAX_SLINGSHOT_PULL, length, 0.001f)
    }

    @Test
    fun thePouchCannotGoUnderground() {
        aim.grab(aim.anchorX, aim.anchorY)
        aim.dragTo(aim.anchorX, aim.anchorY - 30f)
        assertTrue(aim.anchorY + aim.pullY > 0f)
    }

    @Test
    fun aShotLaunchesOppositeToThePull() {
        aim.grab(aim.anchorX, aim.anchorY)
        aim.dragTo(aim.anchorX - 1.5f, aim.anchorY - 1f)
        assertTrue(aim.launchVelocityX() > 0f)
        assertTrue(aim.launchVelocityY() > 0f)
    }

    @Test
    fun releasingReturnsTheShotAndResetsThePull() {
        aim.grab(aim.anchorX, aim.anchorY)
        aim.dragTo(aim.anchorX - 1.5f, aim.anchorY - 1f)
        val shot = assertNotNull(aim.release())
        assertEquals(-1.5f, shot.pullX, 0.001f)
        assertFalse(aim.active)
        assertEquals(0f, aim.pullX)
        assertTrue(game.execute(shot, game.currentPlayer))
        assertEquals(Phase.RESOLVING, game.phase)
    }

    @Test
    fun releasingWithoutAGrabDoesNothing() {
        assertNull(aim.release())
        assertEquals(Phase.PLAN, game.phase)
    }

    @Test
    fun theSlingshotCannotBeGrabbedWhileAShotIsResolving() {
        aim.grab(aim.anchorX, aim.anchorY)
        aim.dragTo(aim.anchorX - 1.5f, aim.anchorY - 1f)
        game.execute(aim.release()!!, game.currentPlayer)
        assertFalse(aim.grab(aim.anchorX, aim.anchorY))
    }
}
