package io.github.beretta299.sharejoy.presentation.match

import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchViewModelTest {
    private val game = Game().apply { players[0].points = 12 }
    private val viewModel = MatchViewModel(game)
    private val state get() = viewModel.uiState.value

    private fun frames(count: Int) = repeat(count) { viewModel.onFrame(MatchViewModel.STEP, PORTRAIT) }

    private fun pullAndRelease() {
        val scene = viewModel.scene
        viewModel.onPointerDown(scene.anchorX, scene.anchorY)
        viewModel.onAimDrag(scene.anchorX - 1.6f, scene.anchorY - 1.1f)
        viewModel.onAimRelease()
    }

    @Test
    fun startsWithRedPlanningTheFirstTurn() {
        assertEquals(Phase.PLAN, state.phase)
        assertEquals(0, state.currentPlayer)
        assertEquals(1, state.turn)
        assertEquals(Track.entries.size, state.levels.size)
        assertEquals(listOf(Ammo.STONE), state.ammo)
        assertFalse(state.aiming)
    }

    @Test
    fun buyingAnUpgradeSpendsPointsAndRaisesItsLevel() {
        val cost = state.costs[Track.POWER.ordinal]!!
        viewModel.onAction(MatchAction.BuyUpgrade(Track.POWER))
        assertEquals(12 - cost, state.points[0])
        assertEquals(1, state.levels[Track.POWER.ordinal])
    }

    @Test
    fun unlockedAmmoCanBeSelected() {
        viewModel.onAction(MatchAction.BuyUpgrade(Track.AMMO))
        frames(120) // let the build animation finish
        viewModel.onAction(MatchAction.SelectAmmo(Ammo.SPLIT))
        assertTrue(Ammo.SPLIT in state.ammo)
        assertEquals(Ammo.SPLIT, state.selectedAmmo)
    }

    @Test
    fun anUpgradeThePlayerCannotAffordChangesNothing() {
        game.players[0].points = 0
        viewModel.onAction(MatchAction.BuyUpgrade(Track.POWER))
        assertEquals(0, state.points[0])
        assertEquals(0, state.levels[Track.POWER.ordinal])
    }

    @Test
    fun pressingTheSlingshotStartsAimingAndReleasingFires() {
        val scene = viewModel.scene
        assertTrue(viewModel.onPointerDown(scene.anchorX, scene.anchorY))
        assertTrue(state.aiming)
        viewModel.onAimDrag(scene.anchorX - 1.6f, scene.anchorY - 1.1f)
        viewModel.onAimRelease()
        assertFalse(state.aiming)
        assertEquals(Phase.RESOLVING, state.phase)
    }

    @Test
    fun pressingAwayFromTheSlingshotDoesNotAim() {
        val scene = viewModel.scene
        assertFalse(viewModel.onPointerDown(scene.anchorX + 6f, scene.anchorY + 6f))
        assertFalse(state.aiming)
    }

    @Test
    fun theTurnPassesToBlueOnceTheShotSettles() {
        pullAndRelease()
        var guard = 0
        while (state.phase != Phase.PLAN && guard++ < 3000) frames(1)
        assertEquals(Phase.PLAN, state.phase)
        assertEquals(1, state.currentPlayer)
        assertEquals(2, state.turn)
    }

    @Test
    fun restartBeginsAFreshMatch() {
        pullAndRelease()
        frames(30)
        viewModel.onAction(MatchAction.Restart)
        assertEquals(Phase.PLAN, state.phase)
        assertEquals(0, state.currentPlayer)
        assertEquals(1, state.turn)
        assertEquals(1, state.round)
    }

    private companion object {
        const val PORTRAIT = 9f / 19.5f
    }
}
