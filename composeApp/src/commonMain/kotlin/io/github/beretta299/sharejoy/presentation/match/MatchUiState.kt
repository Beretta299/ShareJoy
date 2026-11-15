package io.github.beretta299.sharejoy.presentation.match

import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Phase

/** Everything the HUD shows. Immutable, so the HUD only recomposes when something actually changed. */
data class MatchUiState(
    val phase: Phase = Phase.PLAN,
    val current: Int = 0,
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
)
