package io.github.beretta299.sharejoy.data.link

import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.GameCommand
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.model.PhysicalScreen

/** Bump this whenever the messages or the simulation change, so mismatched builds refuse to play together. */
const val PROTOCOL_VERSION = 2

/** Every message two paired phones exchange. */
sealed interface WireMessage {
    /** First message after connecting. [screen] is the sender's real screen size, if it knows it. */
    data class Hello(val protocolVersion: Int, val playerName: String, val screen: PhysicalScreen? = null) : WireMessage

    /** [player] issued [command], to be run just before simulation step [tick]. */
    data class Command(val tick: Int, val player: Int, val command: GameCommand) : WireMessage

    /** The sender has sent every command it will ever run up to and including [tick]. */
    data class Confirm(val tick: Int) : WireMessage

    /** Live slingshot pull of the player whose turn it is, so the other phone can show it. Cosmetic only. */
    data class Aim(val isAiming: Boolean, val pullX: Float, val pullY: Float) : WireMessage

    /** The sender's [io.github.beretta299.sharejoy.game.checksum] before simulation step [tick]. */
    data class Checksum(val tick: Int, val value: Int) : WireMessage
}

/** Turns [WireMessage]s into compact bytes and back. */
object WireCodec {
    private const val HELLO: Byte = 0
    private const val COMMAND: Byte = 1
    private const val CONFIRM: Byte = 2
    private const val AIM: Byte = 3
    private const val CHECKSUM: Byte = 4

    private const val BUY_UPGRADE: Byte = 0
    private const val REPAIR: Byte = 1
    private const val SELECT_AMMO: Byte = 2
    private const val FIRE: Byte = 3
    private const val SPLIT: Byte = 4
    private const val NEXT_ROUND: Byte = 5
    private const val RESTART: Byte = 6

    fun encode(message: WireMessage): ByteArray {
        val out = ByteWriter()
        when (message) {
            is WireMessage.Hello -> {
                out.byte(HELLO); out.int(message.protocolVersion); out.string(message.playerName)
                out.float(message.screen?.widthMm ?: 0f); out.float(message.screen?.heightMm ?: 0f)
            }
            is WireMessage.Command -> {
                out.byte(COMMAND); out.int(message.tick); out.byte(message.player.toByte())
                writeCommand(out, message.command)
            }
            is WireMessage.Confirm -> {
                out.byte(CONFIRM); out.int(message.tick)
            }
            is WireMessage.Aim -> {
                out.byte(AIM); out.byte(if (message.isAiming) 1 else 0); out.float(message.pullX); out.float(message.pullY)
            }
            is WireMessage.Checksum -> {
                out.byte(CHECKSUM); out.int(message.tick); out.int(message.value)
            }
        }
        return out.toByteArray()
    }

    /** Returns null for bytes that are not a message this build understands. */
    fun decode(bytes: ByteArray): WireMessage? = runCatching {
        val input = ByteReader(bytes)
        when (input.byte()) {
            HELLO -> {
                val version = input.int()
                val name = input.string()
                // Older builds send no screen size; the version check turns them away afterwards.
                val screen = if (version >= 2) PhysicalScreen(input.float(), input.float()).takeIf { it.isValid } else null
                WireMessage.Hello(version, name, screen)
            }
            COMMAND -> WireMessage.Command(input.int(), input.byte().toInt(), readCommand(input) ?: return null)
            CONFIRM -> WireMessage.Confirm(input.int())
            AIM -> WireMessage.Aim(input.byte() == 1.toByte(), input.float(), input.float())
            CHECKSUM -> WireMessage.Checksum(input.int(), input.int())
            else -> null
        }
    }.getOrNull()

    private fun writeCommand(out: ByteWriter, command: GameCommand) {
        when (command) {
            is GameCommand.BuyUpgrade -> { out.byte(BUY_UPGRADE); out.byte(command.track.ordinal.toByte()) }
            GameCommand.Repair -> out.byte(REPAIR)
            is GameCommand.SelectAmmo -> { out.byte(SELECT_AMMO); out.byte(command.ammo.ordinal.toByte()) }
            is GameCommand.Fire -> { out.byte(FIRE); out.float(command.pullX); out.float(command.pullY) }
            GameCommand.Split -> out.byte(SPLIT)
            GameCommand.NextRound -> out.byte(NEXT_ROUND)
            GameCommand.Restart -> out.byte(RESTART)
        }
    }

    private fun readCommand(input: ByteReader): GameCommand? = when (input.byte()) {
        BUY_UPGRADE -> Track.entries.getOrNull(input.byte().toInt())?.let { GameCommand.BuyUpgrade(it) }
        REPAIR -> GameCommand.Repair
        SELECT_AMMO -> Ammo.entries.getOrNull(input.byte().toInt())?.let { GameCommand.SelectAmmo(it) }
        FIRE -> GameCommand.Fire(input.float(), input.float())
        SPLIT -> GameCommand.Split
        NEXT_ROUND -> GameCommand.NextRound
        RESTART -> GameCommand.Restart
        else -> null
    }
}

/** Big-endian writer for the few primitive types the protocol uses. */
private class ByteWriter {
    private val bytes = ArrayList<Byte>(16)

    fun byte(value: Byte) {
        bytes.add(value)
    }

    fun int(value: Int) {
        for (shift in 24 downTo 0 step 8) bytes.add((value ushr shift).toByte())
    }

    fun float(value: Float) = int(value.toRawBits())

    fun string(value: String) {
        val utf8 = value.encodeToByteArray()
        int(utf8.size)
        utf8.forEach(bytes::add)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}

private class ByteReader(private val bytes: ByteArray) {
    private var position = 0

    fun byte(): Byte = bytes[position++]

    fun int(): Int {
        var value = 0
        repeat(4) { value = (value shl 8) or (byte().toInt() and 0xFF) }
        return value
    }

    fun float(): Float = Float.fromBits(int())

    fun string(): String {
        val length = int()
        require(length in 0..(bytes.size - position))
        return bytes.decodeToString(position, position + length).also { position += length }
    }
}
