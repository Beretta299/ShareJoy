package io.github.beretta299.sharejoy

import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.GameCommand
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.game.checksum
import io.github.beretta299.sharejoy.game.execute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GameCommandTest {
    private fun Game.settle() {
        var guard = 0
        while (phase != Phase.PLAN && guard++ < 6000) update(1f / 60f)
    }

    @Test
    fun onlyThePlayerWhoseTurnItIsCanAct() {
        val game = Game()
        game.players[0].points = 12
        assertFalse(game.execute(GameCommand.Fire(1.5f, -1f), player = 1))
        assertFalse(game.execute(GameCommand.BuyUpgrade(Track.POWER), player = 1))
        assertEquals(Phase.PLAN, game.phase)
        assertTrue(game.execute(GameCommand.BuyUpgrade(Track.POWER), player = 0))
        assertTrue(game.execute(GameCommand.Fire(-1.5f, -1f), player = 0))
    }

    @Test
    fun nextRoundDoesNothingMidRound() {
        val game = Game()
        val before = game.checksum()
        assertFalse(game.execute(GameCommand.NextRound, player = 1))
        assertEquals(before, game.checksum())
    }

    @Test
    fun theSameCommandsGiveTheSameChecksum() {
        val games = List(2) { Game() }
        for (game in games) {
            game.execute(GameCommand.Fire(-1.6f, -1.1f), player = 0)
            game.settle()
            game.execute(GameCommand.Fire(1.7f, -0.9f), player = 1)
            game.settle()
        }
        assertEquals(games[0].checksum(), games[1].checksum())
    }

    @Test
    fun aSlightlyDifferentShotGivesADifferentChecksum() {
        val games = List(2) { Game() }
        games[0].execute(GameCommand.Fire(-1.6f, -1.1f), player = 0)
        games[1].execute(GameCommand.Fire(-1.6f, -1.1001f), player = 0)
        games.forEach { it.settle() }
        assertNotEquals(games[0].checksum(), games[1].checksum())
    }
}
