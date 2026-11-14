package io.github.beretta299.sharejoy

import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** Headless bot-vs-bot rounds with random aim. Prints pacing numbers used to tune [Balance]. */
class BalanceSimTest {
    @Test
    fun roundsLastAFewTurns() {
        val rng = Random(42)
        val turns = ArrayList<Int>()
        var destroyed = 0
        var shots = 0
        val wins = IntArray(3)
        repeat(30) {
            val g = Game()
            var guard = 0
            while (g.phase != Phase.ROUND_OVER && g.phase != Phase.MATCH_OVER && guard++ < 60) {
                val before = g.blockCount(0) + g.blockCount(1)
                val s = g.side(g.current)
                g.fire(s * (1.0f + rng.nextFloat() * 0.9f), -(0.8f + rng.nextFloat() * 1.0f))
                var t = 0
                while (g.phase != Phase.PLAN && g.phase != Phase.ROUND_OVER && g.phase != Phase.MATCH_OVER && t++ < 2000) g.update(1f / 60f)
                destroyed += before - (g.blockCount(0) + g.blockCount(1))
                shots++
            }
            turns += g.turn
            wins[g.roundWinner + 1]++
        }
        println("SIM turns per round: avg=%.1f min=%d max=%d | blocks destroyed per shot=%.2f | wins draw/red/blue=%s"
            .format(turns.average(), turns.min(), turns.max(), destroyed / shots.toFloat(), wins.toList()))
        assertTrue(turns.average() > 3.0, "rounds end too fast")
    }
}
