package io.github.beretta299.sharejoy.data.link

import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.GameCommand
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.model.PhysicalScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WireCodecTest {
    private val everyCommand = listOf(
        GameCommand.BuyUpgrade(Track.AMMO),
        GameCommand.Repair,
        GameCommand.SelectAmmo(Ammo.KEG),
        GameCommand.Fire(-1.6180339f, -0.333333f),
        GameCommand.Split,
        GameCommand.NextRound,
        GameCommand.Restart,
    )

    @Test
    fun everyMessageSurvivesARoundTrip() {
        val messages = everyCommand.mapIndexed { index, command -> WireMessage.Command(1000 + index, index % 2, command) } +
            listOf(
                WireMessage.Hello(PROTOCOL_VERSION, "Pixel 9 · Żółw", PhysicalScreen(68.5f, 152.25f)),
                WireMessage.Hello(PROTOCOL_VERSION, "No screen size"),
                WireMessage.Confirm(123456),
                WireMessage.Aim(true, -0.5f, 0.25f),
                WireMessage.Checksum(77, -19283746),
            )
        for (message in messages) assertEquals(message, WireCodec.decode(WireCodec.encode(message)))
    }

    @Test
    fun aShotKeepsEveryBitOfThePull() {
        val shot = GameCommand.Fire(Float.fromBits(0x3FCF1BBD), Float.fromBits(-0x40555556))
        val decoded = WireCodec.decode(WireCodec.encode(WireMessage.Command(1, 0, shot))) as WireMessage.Command
        val fire = decoded.command as GameCommand.Fire
        assertEquals(shot.pullX.toRawBits(), fire.pullX.toRawBits())
        assertEquals(shot.pullY.toRawBits(), fire.pullY.toRawBits())
    }

    @Test
    fun garbageIsRejectedInsteadOfCrashing() {
        assertNull(WireCodec.decode(byteArrayOf()))
        assertNull(WireCodec.decode(byteArrayOf(99, 1, 2)))
        assertNull(WireCodec.decode(byteArrayOf(1, 0, 0)))
        assertNull(WireCodec.decode(byteArrayOf(0, 0, 0, 0, 1, 0x7F, 0, 0, 0)))
    }
}
