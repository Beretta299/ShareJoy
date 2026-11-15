package io.github.beretta299.sharejoy.presentation.match

import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Track

/** Everything a player can ask for through the HUD. */
sealed interface MatchAction {
    data class BuyUpgrade(val track: Track) : MatchAction
    data object Repair : MatchAction
    data class SelectAmmo(val ammo: Ammo) : MatchAction
    data object NextRound : MatchAction
    data object Restart : MatchAction

    /** Ends a match with another phone and returns to the lobby. */
    data object Leave : MatchAction
}
