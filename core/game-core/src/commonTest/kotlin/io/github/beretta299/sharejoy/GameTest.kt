package io.github.beretta299.sharejoy

import io.github.beretta299.sharejoy.game.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameTest {
    private val dt = 1f / 60f

    private fun Game.runUntilPlan(maxSeconds: Float = 20f): Float {
        var t = 0f
        while (phase != Phase.PLAN && phase != Phase.ROUND_OVER && phase != Phase.MATCH_OVER && t < maxSeconds) {
            update(dt)
            t += dt
        }
        return t
    }

    @Test
    fun castleStandsStillUnderGravity() {
        val game = Game()
        val start = game.pieces.associateWith { it.body.x to it.body.y }
        game.world.wakeAll()
        repeat(60 * 15) { game.world.step(dt) }
        var worst = 0f
        for ((p, pos) in start) {
            worst = maxOf(worst, abs(p.body.x - pos.first), abs(p.body.y - pos.second), abs(p.body.angle))
        }
        println("worst drift after 15 s: $worst")
        assertTrue(worst < 0.08f, "castle drifted $worst")
    }

    @Test
    fun aMissedShotPassesTheTurnAndPaysIncome() {
        val game = Game()
        assertEquals(0, game.currentPlayer)
        assertTrue(game.fire(-0.3f, -2.1f)) // nearly straight up
        val t = game.runUntilPlan()
        println("lob resolved in $t s, turn=${game.turn}")
        assertEquals(1, game.currentPlayer)
        assertEquals(2, game.turn)
        assertEquals(1 + Balance.POINTS_PER_TURN, game.players[1].points)
    }

    @Test
    fun shotsDamageTheEnemyCastle() {
        val game = Game()
        val before = game.blockCount(1)
        var best = 0
        // Sweep a few pulls and make sure at least one wrecks something.
        for (pullX in listOf(-1.0f, -1.2f, -1.4f, -1.6f, -1.8f, -2.0f)) for (pullY in listOf(-0.8f, -1.1f, -1.4f, -1.7f)) {
            val shot = Game()
            shot.fire(pullX, pullY)
            val seconds = shot.runUntilPlan()
            val lost = before - shot.blockCount(1)
            println("pull $pullX,$pullY: lost $lost blocks, keeperDown=${shot.keeperDown[1]}, phase=${shot.phase}, seconds=$seconds")
            best = maxOf(best, lost)
        }
        assertTrue(best > 0)
    }

    @Test
    fun upgradesCostPointsAndAddBlocks() {
        val game = Game()
        game.players[0].points = 12
        val blocks = game.blockCount(0)
        assertTrue(game.buyUpgrade(Track.COVER))
        assertEquals(10, game.players[0].points)
        assertEquals(blocks + 2, game.blockCount(0))
        game.runUntilPlan()
        assertEquals(Phase.PLAN, game.phase)
        assertTrue(game.buyUpgrade(Track.AMMO))
        assertEquals(Ammo.SPLIT, game.players[0].selectedAmmo)
    }

    @Test
    fun simulationIsDeterministic() {
        fun run(): List<Float> {
            val g = Game()
            g.fire(-1.6f, -1.4f)
            g.runUntilPlan()
            return g.pieces.flatMap { listOf(it.body.x, it.body.y, it.body.angle) }
        }
        assertEquals(run(), run())
    }
}
