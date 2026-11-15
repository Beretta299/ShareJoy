package io.github.beretta299.sharejoy.presentation.match.camera

import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import io.github.beretta299.sharejoy.model.PhysicalScreen
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A small phone next to a much bigger one: the picture must still join up, measured in real millimetres. */
class PairedScreenAlignmentTest {
    private val small = PhysicalScreen(widthMm = 62f, heightMm = 134f)
    private val large = PhysicalScreen(widthMm = 78f, heightMm = 168f)
    private val game = Game()
    private val director = CameraDirector(game, pairedScreens = listOf(small, large))
    private val screens = listOf(small, large)

    private fun step(frames: Int = 1) = repeat(frames) {
        game.update(STEP)
        game.events.clear()
        director.update(STEP, small.widthMm / small.heightMm)
    }

    private fun millimetresPerMetre(player: Int) = screens[player].widthMm / director.paired[player].viewWidth

    /** How far above the phone's bottom edge world height [worldY] is drawn, in millimetres. */
    private fun heightAboveTableMm(player: Int, worldY: Float): Float {
        val camera = director.paired[player]
        return screens[player].heightMm / 2f + (worldY - camera.centerY) * millimetresPerMetre(player)
    }

    @Test
    fun bothPhonesDrawAtTheSameRealWorldScale() {
        step()
        assertEquals(millimetresPerMetre(0), millimetresPerMetre(1), 0.0001f)
        assertEquals(Balance.SCREEN_WIDTH, director.paired[0].viewWidth, 0.001f)
        assertTrue(director.paired[1].viewWidth > Balance.SCREEN_WIDTH, "the bigger phone shows more of the world")
    }

    @Test
    fun theGroundIsTheSameHeightAboveTheTableOnBothPhones() {
        step()
        assertEquals(heightAboveTableMm(0, 0f), heightAboveTableMm(1, 0f), 0.01f)
        assertTrue(heightAboveTableMm(0, 0f) > 0f)
    }

    @Test
    fun atRestEachPhonesInnerEdgeIsItsEdgeOfTheOpenGround() {
        step()
        val left = director.paired[0]
        val right = director.paired[1]
        assertEquals(-Balance.OPEN_GROUND_WIDTH / 2f, left.centerX + left.viewWidth / 2f, 0.01f)
        assertEquals(Balance.OPEN_GROUND_WIDTH / 2f, right.centerX - right.viewWidth / 2f, 0.01f)
    }

    @Test
    fun theShotCrossesTheSeamAtTheSameHeightOnBothPhones() {
        step()
        game.fire(-1.6f, -1.1f)
        var checked = 0
        var guard = 0
        while (game.phase == Phase.RESOLVING && guard++ < 1200) {
            step()
            val shot = game.pieces.firstOrNull { it.kind == PieceKind.PROJECTILE && !it.hasHit }?.body ?: continue
            if (abs(shot.x) < 1f) {
                val left = director.paired[0]
                val right = director.paired[1]
                val gap = (right.centerX - right.viewWidth / 2f) - (left.centerX + left.viewWidth / 2f)
                assertTrue(abs(gap) < 0.2f, "screens are $gap m apart while the shot crosses")
                assertEquals(heightAboveTableMm(0, shot.y), heightAboveTableMm(1, shot.y), 0.01f)
                checked++
            }
        }
        assertTrue(checked > 0, "the shot never crossed the middle")
    }

    private companion object {
        const val STEP = 1f / 60f
    }
}
