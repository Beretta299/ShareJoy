package io.github.beretta299.sharejoy.presentation.match.sync

import io.github.beretta299.sharejoy.data.link.LinkStatus
import io.github.beretta299.sharejoy.data.link.PeerLink
import io.github.beretta299.sharejoy.data.link.WireCodec
import io.github.beretta299.sharejoy.data.link.WireMessage
import io.github.beretta299.sharejoy.game.GameCommand

/**
 * Keeps two phones' simulations in step ("deterministic lockstep").
 *
 * Both phones run the same [io.github.beretta299.sharejoy.game.Game], numbered in fixed simulation steps
 * (ticks). A command is never run at once: it is scheduled [inputDelay] ticks ahead and sent to the other phone,
 * and both run it just before that tick. A phone may only simulate a tick once the other phone has confirmed it
 * sent every command up to that tick, so neither can run ahead and miss one. Commands for one tick run in a
 * fixed order (player 0 first, then by issue order), which keeps both games bit-identical.
 */
class LockstepSync(
    private val link: PeerLink,
    /** The player this phone controls. */
    val localPlayer: Int,
    private val inputDelay: Int = INPUT_DELAY_TICKS,
) {
    private class ScheduledCommand(val tick: Int, val player: Int, val sequence: Int, val command: GameCommand)

    /** The next tick to simulate. */
    var tick = 0
        private set

    /** The other phone has sent every command up to and including this tick. */
    private var peerConfirmedTick = inputDelay - 1
    private var lastConfirmSent = -1
    private var nextSequence = 0
    private val pending = ArrayList<ScheduledCommand>()
    private val localChecksums = HashMap<Int, Int>()
    private val peerChecksums = HashMap<Int, Int>()

    /** The other player's slingshot pull, while they are aiming. */
    var remoteAim: WireMessage.Aim? = null
        private set

    /** True once the two games are known to differ. */
    var isOutOfSync = false
        private set

    val isPeerConnected: Boolean get() = link.status.value is LinkStatus.Connected

    private val peerPlayer get() = 1 - localPlayer

    /** Reads everything the other phone sent since the last call. */
    fun receiveAll() {
        while (true) {
            val bytes = link.poll() ?: return
            WireCodec.decode(bytes)?.let(::handle)
        }
    }

    private fun handle(message: WireMessage) {
        when (message) {
            is WireMessage.Command -> {
                if (message.player != peerPlayer || message.tick < tick) {
                    // A command for a tick already simulated can never be run identically on both phones.
                    isOutOfSync = true
                    return
                }
                schedule(message.tick, message.player, message.command)
            }
            is WireMessage.Confirm -> peerConfirmedTick = maxOf(peerConfirmedTick, message.tick)
            is WireMessage.Aim -> remoteAim = message.takeIf { it.isAiming }
            is WireMessage.Checksum -> {
                peerChecksums[message.tick] = message.value
                compareChecksums(message.tick)
            }
            is WireMessage.Hello -> Unit
        }
    }

    /** Schedules a command from this phone and sends it to the other one. */
    fun submit(command: GameCommand) {
        val runAt = tick + inputDelay
        schedule(runAt, localPlayer, command)
        send(WireMessage.Command(runAt, localPlayer, command))
    }

    private fun schedule(runAt: Int, player: Int, command: GameCommand) {
        pending.add(ScheduledCommand(runAt, player, nextSequence++, command))
    }

    /** True when the other phone has confirmed its commands for [tick], so simulating it is safe. */
    fun canStep(): Boolean = tick <= peerConfirmedTick

    /** Removes and returns the commands to run before [tick], in the order both phones agree on. */
    fun takeCommandsForTick(): List<Pair<GameCommand, Int>> {
        val due = pending.filter { it.tick == tick }
        if (due.isEmpty()) return emptyList()
        pending.removeAll(due.toSet())
        return due.sortedWith(compareBy({ it.player }, { it.sequence })).map { it.command to it.player }
    }

    /** Call after simulating [tick]. */
    fun finishTick() {
        tick++
    }

    /** Tells the other phone how far ahead this phone's commands are settled. */
    fun sendConfirm() {
        // Anything submitted from now on runs at tick + inputDelay or later.
        val confirmed = tick + inputDelay - 1
        if (confirmed <= lastConfirmSent) return
        lastConfirmSent = confirmed
        send(WireMessage.Confirm(confirmed))
    }

    fun sendAim(isAiming: Boolean, pullX: Float, pullY: Float) {
        send(WireMessage.Aim(isAiming, pullX, pullY))
    }

    /** Records this phone's game checksum before [tick] and shares it, to catch drift early. */
    fun shareChecksum(value: Int) {
        localChecksums[tick] = value
        send(WireMessage.Checksum(tick, value))
        compareChecksums(tick)
    }

    private fun compareChecksums(atTick: Int) {
        val local = localChecksums[atTick] ?: return
        val peer = peerChecksums[atTick] ?: return
        if (local != peer) isOutOfSync = true
        localChecksums.remove(atTick)
        peerChecksums.remove(atTick)
    }

    private fun send(message: WireMessage) = link.send(WireCodec.encode(message))

    companion object {
        /** About 130 ms: enough for a Bluetooth round trip, short enough that a shot still feels instant. */
        const val INPUT_DELAY_TICKS = 8
    }
}
