package io.github.beretta299.sharejoy.presentation.match.sync

import io.github.beretta299.sharejoy.data.link.FakeLinkPair
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.game.checksum
import io.github.beretta299.sharejoy.presentation.match.MatchAction
import io.github.beretta299.sharejoy.presentation.match.MatchLinkState
import io.github.beretta299.sharejoy.presentation.match.MatchViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Two phones, each with its own game, joined by a fake link. They must stay identical whatever the timing. */
class PairedMatchTest {
    private val links = FakeLinkPair()
    private val games = listOf(Game(), Game())
    private val phones = listOf(
        MatchViewModel(games[0], LockstepSync(links.host, localPlayer = 0)),
        MatchViewModel(games[1], LockstepSync(links.guest, localPlayer = 1)),
    )
    private var frame = 0

    /** Phones run at different frame rates and messages arrive only every few frames, like a real link. */
    private fun frames(count: Int, deliverEvery: Int = 3) = repeat(count) {
        phones[0].onFrame(1f / 60f, PORTRAIT)
        phones[1].onFrame(if (frame % 2 == 0) 1f / 45f else 1f / 90f, PORTRAIT)
        if (frame % deliverEvery == 0) links.deliver()
        frame++
    }

    private fun shoot(player: Int, pullX: Float, pullY: Float) {
        val phone = phones[player]
        val scene = phone.scene
        assertTrue(phone.onPointerDown(scene.anchorX, scene.anchorY), "player $player grabs the slingshot")
        phone.onAimDrag(scene.anchorX + pullX, scene.anchorY + pullY)
        phone.onAimRelease()
    }

    private fun playUntilPlan() {
        frames(40)
        var guard = 0
        while ((games[0].phase != Phase.PLAN || games[1].phase != Phase.PLAN) && guard++ < 6000) frames(1)
        frames(40)
    }

    private fun assertInSync() {
        assertEquals(games[0].checksum(), games[1].checksum())
        phones.forEach { assertEquals(MatchLinkState.CONNECTED, it.uiState.value.link) }
    }

    @Test
    fun bothPhonesSeeTheSameGameAfterSeveralTurns() {
        games.forEach { game -> game.players.forEach { it.points = 12 } }
        phones[0].onAction(MatchAction.BuyUpgrade(Track.POWER))
        playUntilPlan()
        shoot(0, -1.6f, -1.1f)
        playUntilPlan()
        assertEquals(1, games[0].currentPlayer)
        assertInSync()

        phones[1].onAction(MatchAction.BuyUpgrade(Track.WALLS))
        playUntilPlan()
        shoot(1, 1.7f, -0.9f)
        playUntilPlan()
        shoot(0, -1.9f, -0.7f)
        playUntilPlan()
        shoot(1, 1.4f, -1.3f)
        playUntilPlan()

        assertEquals(games[0].turn, games[1].turn)
        assertTrue(games[0].turn >= 5)
        assertInSync()
    }

    @Test
    fun aPlayerCannotActOnTheOtherPlayersTurn() {
        val scene = phones[1].scene
        assertFalse(phones[1].onPointerDown(scene.anchorX, scene.anchorY))
        games.forEach { it.players[0].points = 12 }
        phones[1].onAction(MatchAction.BuyUpgrade(Track.POWER))
        frames(60)
        assertEquals(12, games[0].players[0].points)
        assertEquals(12, games[1].players[0].points)
        assertInSync()
    }

    @Test
    fun theOtherPhoneShowsThePullWhileAiming() {
        val scene = phones[0].scene
        phones[0].onPointerDown(scene.anchorX, scene.anchorY)
        phones[0].onAimDrag(scene.anchorX - 1.2f, scene.anchorY - 0.8f)
        frames(10)
        assertTrue(phones[1].scene.aiming)
        assertEquals(-1.2f, phones[1].scene.pullX, 0.001f)
        phones[0].onAimRelease()
        frames(30)
        assertFalse(phones[1].scene.aiming)
    }

    @Test
    fun theGameWaitsWhenTheOtherPhoneGoesQuiet() {
        frames(30)
        val reached = games[0].let { phones[0].uiState.value.turn }
        // No deliveries: neither phone may run ahead of what the other has confirmed.
        shoot(0, -1.6f, -1.1f)
        frames(120, deliverEvery = Int.MAX_VALUE)
        assertEquals(Phase.PLAN, games[1].phase)
        assertEquals(MatchLinkState.WAITING, phones[0].uiState.value.link)
        // Once messages flow again the match carries on, still in step.
        playUntilPlan()
        assertEquals(reached + 1, games[0].turn)
        assertInSync()
    }

    @Test
    fun losingTheConnectionIsReported() {
        frames(10)
        links.disconnect()
        frames(2)
        phones.forEach { assertEquals(MatchLinkState.DISCONNECTED, it.uiState.value.link) }
    }

    @Test
    fun aDifferentGameOnTheOtherPhoneIsNoticed() {
        games[1].players[0].levels[Track.POWER.ordinal] = 1 // the phones no longer agree
        shoot(0, -1.6f, -1.1f)
        playUntilPlan()
        phones.forEach { assertEquals(MatchLinkState.OUT_OF_SYNC, it.uiState.value.link) }
    }

    private companion object {
        const val PORTRAIT = 9f / 19.5f
    }
}
