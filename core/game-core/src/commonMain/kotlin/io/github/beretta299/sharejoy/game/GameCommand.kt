package io.github.beretta299.sharejoy.game

/**
 * Everything a player can do to a [Game], as plain data.
 *
 * Paired phones never send each other positions. Each phone runs the same simulation, and only these commands
 * travel between them, so both games stay identical.
 */
sealed interface GameCommand {
    data class BuyUpgrade(val track: Track) : GameCommand
    data object Repair : GameCommand
    data class SelectAmmo(val ammo: Ammo) : GameCommand
    data class Fire(val pullX: Float, val pullY: Float) : GameCommand
    data object Split : GameCommand
    data object NextRound : GameCommand
    data object Restart : GameCommand
}

/**
 * Runs [command] on behalf of [player]. Turn actions are ignored unless it is [player]'s turn; either player may
 * start the next round or a new match. Returns true if the game changed.
 */
fun Game.execute(command: GameCommand, player: Int): Boolean {
    val isTheirTurn = player == currentPlayer
    return when (command) {
        is GameCommand.BuyUpgrade -> isTheirTurn && buyUpgrade(command.track)
        GameCommand.Repair -> isTheirTurn && repair()
        is GameCommand.SelectAmmo -> isTheirTurn && selectAmmoIfAllowed(command.ammo)
        is GameCommand.Fire -> isTheirTurn && fire(command.pullX, command.pullY)
        GameCommand.Split -> isTheirTurn && splitShot()
        GameCommand.NextRound -> startNextRound()
        GameCommand.Restart -> true.also { newMatch() }
    }
}

private fun Game.selectAmmoIfAllowed(ammo: Ammo): Boolean {
    val before = activePlayer.selectedAmmo
    selectAmmo(ammo)
    return activePlayer.selectedAmmo != before
}

private fun Game.startNextRound(): Boolean = when (phase) {
    Phase.MATCH_OVER -> true.also { newMatch() }
    Phase.ROUND_OVER -> true.also { nextRound() }
    else -> false
}

/**
 * A fingerprint of everything that decides how the game plays out. Two paired phones compare it at the start of
 * every turn; different values mean their simulations have drifted apart.
 */
fun Game.checksum(): Int {
    var hash = HASH_SEED
    fun mix(value: Int) {
        hash = (hash xor value) * HASH_PRIME
    }
    mix(phase.ordinal)
    mix(currentPlayer)
    mix(turn)
    mix(round)
    for (player in players) {
        mix(player.points)
        mix(player.roundsWon)
        player.levels.forEach(::mix)
        mix(player.selectedAmmo.ordinal)
    }
    for (piece in pieces) {
        mix(piece.body.id)
        mix(piece.body.x.toRawBits())
        mix(piece.body.y.toRawBits())
        mix(piece.body.angle.toRawBits())
        mix(piece.health.toRawBits())
    }
    return hash
}

// FNV-1a constants.
private const val HASH_SEED = -2128831035
private const val HASH_PRIME = 16777619
