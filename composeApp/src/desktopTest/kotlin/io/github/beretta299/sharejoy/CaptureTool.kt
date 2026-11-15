package io.github.beretta299.sharejoy

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.presentation.match.MatchAction
import io.github.beretta299.sharejoy.presentation.match.MatchViewModel
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
    private val game = Game()
    private val viewModel = MatchViewModel(game)
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
        val ax = viewModel.scene.anchorX
        val ay = viewModel.scene.anchorY
        viewModel.onPointerDown(ax, ay)
        for (i in 1..36) {
            viewModel.onAimDrag(ax + pullX * i / 36f, ay + pullY * i / 36f)
            frames(1)
        }
        frames(24)
        if (name != null) shot(name)
        viewModel.onAimRelease()
    }

    private fun ImageComposeScene.waitForPlan(max: Int = 900) {
        var n = 0
        while (game.phase != Phase.PLAN && !over() && n++ < max) frames(1)
    }

    private fun over() = game.phase == Phase.ROUND_OVER || game.phase == Phase.MATCH_OVER

    @Test
    fun capture() {
        if (!enabled) return
        out.mkdirs()
        File(out, "frames").mkdirs()
        val scene = ImageComposeScene(width, height, Density((System.getProperty("capture.density") ?: "2").toFloat())) { App(viewModel) }
        with(scene) {
            // Give both sides a full purse so the script can show every kind of upgrade.
            game.players.forEach { it.points = 12 }
            frames(50)
            shot("01_red_plan")
            viewModel.onAction(MatchAction.BuyUpgrade(Track.AMMO))
            frames(40)
            aimAndFire(-1.6f, -1.1f, "02_red_aim")
            frames(52); viewModel.onTap(); frames(8)
            shot("03_split_shot")
            frames(40); shot("04_impact")
            waitForPlan(); frames(60)

            game.players[1].points = 12
            shot("05_blue_plan")
            viewModel.onAction(MatchAction.BuyUpgrade(Track.COVER)); frames(6); shot("06_blue_builds")
            waitForPlan(); frames(30)
            viewModel.onAction(MatchAction.BuyUpgrade(Track.MATERIALS))
            frames(40)
            aimAndFire(1.8f, -1.7f, "07_blue_aim")
            frames(100); shot("08_blue_impact")
            waitForPlan(); frames(60)

            val script = listOf(
                Triple(-1.7f, -1.3f, Ammo.KEG), Triple(2.0f, -1.4f, Ammo.STONE),
                Triple(-1.5f, -1.0f, Ammo.IRON), Triple(1.6f, -1.1f, Ammo.STONE),
                Triple(-1.5f, -1.4f, Ammo.KEG), Triple(1.8f, -1.1f, Ammo.STONE),
                Triple(-1.6f, -1.2f, Ammo.IRON), Triple(2.0f, -1.7f, Ammo.STONE),
            )
            var i = 0
            while (!over() && i < script.size) {
                val (px, py, ammo) = script[i]
                if (i == 0) { viewModel.onAction(MatchAction.BuyUpgrade(Track.AMMO)); viewModel.onAction(MatchAction.BuyUpgrade(Track.AMMO)); viewModel.onAction(MatchAction.BuyUpgrade(Track.POWER)) }
                if (i == 1) { game.players[1].points = 8; viewModel.onAction(MatchAction.BuyUpgrade(Track.POWER)); viewModel.onAction(MatchAction.Repair) }
                viewModel.onAction(MatchAction.SelectAmmo(ammo))
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
        println("CAPTURE done: phase=${game.phase} turn=${game.turn} blocks=${game.blockCount(0)}/${game.blockCount(1)} frames=$frameNo")
    }
}
