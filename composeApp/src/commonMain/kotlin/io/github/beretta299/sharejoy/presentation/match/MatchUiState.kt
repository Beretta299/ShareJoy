package io.github.beretta299.sharejoy.presentation.match

import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Phase

/** Everything the HUD shows. Immutable, so the HUD only recomposes when something actually changed. */
data class MatchUiState(
    val phase: Phase = Phase.PLAN,
    /** Index of the player whose turn it is. */
    val currentPlayer: Int = 0,
    /** The player this phone controls when paired with another phone, or null when both share this device. */
    val localPlayer: Int? = null,
    val turn: Int = 1,
    val round: Int = 1,
    val points: List<Int> = listOf(0, 0),
    val roundsWon: List<Int> = listOf(0, 0),
    /** One entry per upgrade track, in the order of the Track enum, for the player whose turn it is. */
    val levels: List<Int> = emptyList(),
    val costs: List<Int?> = emptyList(),
    val canBuy: List<Boolean> = emptyList(),
    val canRepair: Boolean = false,
    val ammo: List<Ammo> = listOf(Ammo.STONE),
    val selectedAmmo: Ammo = Ammo.STONE,
    val suddenDeath: Boolean = false,
    val roundWinner: Int = -1,
    val canSplit: Boolean = false,
    val aiming: Boolean = false,
    val link: MatchLinkState = MatchLinkState.SHARED_DEVICE,
)

/** How the connection to the other phone is doing. */
enum class MatchLinkState {
    /** Both players share this device; there is no other phone. */
    SHARED_DEVICE,
    CONNECTED,

    /** The other phone is late; the game pauses until it catches up. */
    WAITING,
    DISCONNECTED,

    /** The two phones' games have drifted apart and can no longer be trusted. */
    OUT_OF_SYNC,
}
