package io.github.beretta299.sharejoy

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.Track
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Plays a scripted match off-screen and saves screenshots (and optionally every frame, for a video),
 * so the look of the game can be reviewed without a device.
 *
 * Skipped unless run with `-Dcapture=true`:
 * `./gradlew :composeApp:desktopTest --tests '*CaptureTool*' -Dcapture=true [-Dcapture.video=true]`
 */
class CaptureTool {
    private val enabled = System.getProperty("capture") == "true"
    private val video = System.getProperty("capture.video") == "true"
    private val width = (System.getProperty("capture.width") ?: "720").toInt()
    private val height = (System.getProperty("capture.height") ?: "1560").toInt()
    private val out = File(System.getProperty("capture.dir") ?: "build/capture")
    private val session = GameSession()
    private var nanos = 0L
    private var frameNo = 0
    private var renderNo = 0

    private fun ImageComposeScene.frames(n: Int) {
        repeat(n) {
            nanos += 16_666_667L
            val image = render(nanos)
            // Every second frame as JPEG: a 30 fps video without spending most of the run on encoding.
            if (video && renderNo++ % 2 == 0) {
                File(out, "frames/f%05d.jpg".format(frameNo++))
                    .writeBytes(image.encodeToData(EncodedImageFormat.JPEG, 90)!!.bytes)
            }
            image.close()
        }
    }

    private fun ImageComposeScene.shot(name: String) {
        val image = render(nanos)
        File(out, "$name.png").writeBytes(image.encodeToData()!!.bytes)
        image.close()
    }

    private fun ImageComposeScene.aimAndFire(pullX: Float, pullY: Float, name: String? = null) {
        val ax = session.anchorX
        val ay = session.anchorY
        session.press(ax, ay)
        for (i in 1..36) {
            session.drag(ax + pullX * i / 36f, ay + pullY * i / 36f)
            frames(1)
        }
        frames(24)
        if (name != null) shot(name)
        session.release()
    }

    private fun ImageComposeScene.waitForPlan(max: Int = 900) {
        var n = 0
        while (session.game.phase != Phase.PLAN && !over() && n++ < max) frames(1)
    }

    private fun over() = session.game.phase == Phase.ROUND_OVER || session.game.phase == Phase.MATCH_OVER

    @Test
    fun capture() {
        if (!enabled) return
        out.mkdirs()
        File(out, "frames").mkdirs()
        val scene = ImageComposeScene(width, height, Density((System.getProperty("capture.density") ?: "2").toFloat())) { App(session) }
        with(scene) {
            // Give both sides a full purse so the script can show every kind of upgrade.
            session.game.players.forEach { it.points = 12 }
            frames(50)
            shot("01_red_plan")
            session.buy(Track.AMMO)
            frames(40)
            aimAndFire(-1.6f, -1.4f, "02_red_aim")
            frames(52); session.tap(); frames(8)
            shot("03_split_shot")
            frames(40); shot("04_impact")
            waitForPlan(); frames(60)

            session.game.players[1].points = 12
            shot("05_blue_plan")
            session.buy(Track.COVER); frames(6); shot("06_blue_builds")
            waitForPlan(); frames(30)
            session.buy(Track.MATERIALS)
            frames(40)
            aimAndFire(1.7f, -1.5f, "07_blue_aim")
            frames(100); shot("08_blue_impact")
            waitForPlan(); frames(60)

            val script = listOf(
                Triple(-1.6f, -1.5f, Ammo.KEG), Triple(1.5f, -1.5f, Ammo.STONE),
                Triple(-1.9f, -1.2f, Ammo.IRON), Triple(1.8f, -1.3f, Ammo.STONE),
                Triple(-1.5f, -1.7f, Ammo.KEG), Triple(1.6f, -1.6f, Ammo.STONE),
                Triple(-1.7f, -1.5f, Ammo.IRON), Triple(1.9f, -1.2f, Ammo.STONE),
            )
            var i = 0
            while (!over() && i < script.size) {
                val (px, py, ammo) = script[i]
                if (i == 0) { session.buy(Track.AMMO); session.buy(Track.AMMO); session.buy(Track.POWER) }
                if (i == 1) { session.game.players[1].points = 8; session.buy(Track.POWER); session.repair() }
                session.selectAmmo(ammo)
                waitForPlan(); frames(45)
                if (i == 0) shot("09_upgrades")
                aimAndFire(px, py)
                frames(105)
                shot("10_exchange_$i")
                waitForPlan(); frames(55)
                i++
            }
            frames(150)
            shot("11_end")
        }
        scene.close()
        println("CAPTURE done: phase=${session.game.phase} turn=${session.game.turn} blocks=${session.game.blockCount(0)}/${session.game.blockCount(1)} frames=$frameNo")
    }
}
