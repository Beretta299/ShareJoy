package io.github.beretta299.sharejoy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.GameEvent
import io.github.beretta299.sharejoy.game.Material
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.render.Particles
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Where the camera looks: the centre of the view in world metres and how many metres fit across the screen. */
class Camera {
    var cx = -6.5f
    var cy = 6f
    var viewW = 13f
}

/** Everything the HUD shows. A data class, so Compose only recomposes when something actually changed. */
data class HudState(
    val phase: Phase = Phase.PLAN,
    val current: Int = 0,
    val turn: Int = 1,
    val round: Int = 1,
    val points: List<Int> = listOf(0, 0),
    val roundsWon: List<Int> = listOf(0, 0),
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

/**
 * Glue between the pure game and the screen: fixed-step loop, camera, aiming input and cosmetic effects.
 * A portrait screen shows one player's half of the battlefield, exactly what that player's phone will show
 * when two phones stand side by side, and the camera slides to whoever is up. A wide screen (a tablet, or a
 * desktop window) shows both halves joined into one picture.
 */
class GameSession {
    val game = Game()
    val camera = Camera()
    val particles = Particles()

    var hud by mutableStateOf(HudState())
        private set

    /** Seconds since start, for flags, water and other idle animation. */
    var time = 0f
        private set
    var shake = 0f
        private set

    // Aiming.
    var aiming = false
        private set
    var pullX = 0f
        private set
    var pullY = 0f
        private set

    private var accumulator = 0f
    private var slowMo = 0f
    private var panOffset = 0f
    private var lastCurrent = 0
    private var lingerX = 0f
    private var sized = false
    private var aspect = 0.46f

    init {
        snapCamera()
        refreshHud()
    }

    // ------------------------------------------------------------------ loop

    fun advance(realDt: Float, screenAspect: Float) {
        aspect = screenAspect
        if (!sized) {
            sized = true
            snapCamera()
        }
        val dt = min(realDt, 0.05f)
        time += dt
        val scale = if (slowMo > 0f) 0.28f else 1f
        slowMo = max(0f, slowMo - dt)

        accumulator += dt * scale
        var steps = 0
        while (accumulator >= STEP && steps < 4) {
            game.update(STEP)
            accumulator -= STEP
            steps++
            consumeEvents()
        }
        if (steps == 4) accumulator = 0f

        if (game.current != lastCurrent) {
            lastCurrent = game.current
            panOffset = 0f
        }
        particles.update(dt * scale)
        shake = max(0f, shake - dt * 2.2f)
        updateCamera(dt)
        updatePairCameras(dt)
        refreshHud()
    }

    private fun consumeEvents() {
        if (game.events.isEmpty()) return
        for (e in game.events) {
            when (e) {
                is GameEvent.BlockDestroyed -> {
                    particles.shatter(e)
                    shake = max(shake, 0.35f)
                }
                is GameEvent.Impact -> {
                    particles.impact(e.x, e.y, e.strength, e.material)
                    if (e.strength > 7f) shake = max(shake, min(0.5f, e.strength * 0.03f))
                }
                is GameEvent.Explosion -> {
                    particles.explosion(e.x, e.y, e.radius)
                    shake = 1f
                }
                is GameEvent.KeeperDown -> {
                    slowMo = 1.6f
                    shake = max(shake, 0.6f)
                    particles.impact(e.x, e.y, 14f, Material.WOOD)
                }
                is GameEvent.Built -> particles.sparkle(e.x, e.y, built = true)
                is GameEvent.Repaired -> particles.sparkle(e.x, e.y, built = false)
                is GameEvent.Fired -> lingerX = e.x
                GameEvent.Split -> Unit
            }
        }
        game.events.clear()
    }

    // ------------------------------------------------------------------ camera

    private fun viewH(viewW: Float) = viewW / aspect

    private fun homeX(player: Int) = game.side(player) * (Balance.GAP + Balance.SCREEN_WIDTH) / 2f

    private fun homeView() = Balance.SCREEN_WIDTH

    /**
     * Cameras of two paired phones, left and right. At rest each shows its owner's home. While a shot is in the
     * air the two screens lock together into one continuous window that travels with the shot across the open
     * ground, so it leaves one screen and enters the other at the same height and the same instant. After the
     * hit each camera glides back home.
     */
    val pairCameras = listOf(Camera(), Camera())

    private fun updatePairCameras(dt: Float) {
        val half = Balance.SCREEN_WIDTH / 2f
        val rest = viewH(Balance.SCREEN_WIDTH) * GROUND_LINE
        var ty = rest
        val flying = if (game.phase == Phase.RESOLVING) {
            game.pieces.firstOrNull { it.kind == PieceKind.PROJECTILE && !it.hasHit }?.body
        } else null
        for (i in 0..1) {
            val cam = pairCameras[i]
            var tx = homeX(i)
            if (flying != null) {
                // Centre of the joined window: it starts on the shooter's home and ends on the target's.
                val c = (flying.x + flying.vx * 0.3f).coerceIn(-Balance.GAP / 2f, Balance.GAP / 2f)
                tx = c + game.side(i) * half
                ty = max(rest, flying.y - viewH(Balance.SCREEN_WIDTH) * 0.22f)
            }
            if (!pairSized) {
                cam.cx = tx
                cam.cy = ty
            }
            cam.viewW = Balance.SCREEN_WIDTH
            val k = 1f - exp(-dt * 5f)
            cam.cx += (tx - cam.cx) * k
            cam.cy += (ty - cam.cy) * k
        }
        pairSized = true
    }
    private var pairSized = false

    private fun snapCamera() {
        camera.cx = homeX(game.current)
        camera.viewW = homeView()
        camera.cy = viewH(camera.viewW) * GROUND_LINE
    }

    private fun updateCamera(dt: Float) {
        var tx: Float
        val tw = homeView()
        var ty = -1f
        when (game.phase) {
            Phase.PLAN, Phase.BUILDING, Phase.HANDOVER -> tx = homeX(game.current) + panOffset
            Phase.RESOLVING -> {
                val shots = game.pieces.filter { it.kind == PieceKind.PROJECTILE }
                val flying = shots.filter { !it.hasHit }
                if (flying.isNotEmpty()) {
                    val b = flying[0].body
                    tx = b.x + b.vx * 0.25f
                    // Keep a high lob on screen.
                    ty = max(viewH(tw) * GROUND_LINE, b.y - viewH(tw) * 0.22f)
                    lingerX = b.x
                } else {
                    // Stay on the castle that was hit while it settles.
                    val hitX = if (shots.isNotEmpty()) shots[0].body.x else lingerX
                    val enemy = 1 - game.current
                    tx = if (hitX * game.side(enemy) >= -Balance.GORGE_HALF_WIDTH) homeX(enemy) else homeX(game.current)
                }
            }
            Phase.ROUND_OVER, Phase.MATCH_OVER -> {
                val loser = if (game.keeperDown[0]) 0 else 1
                tx = homeX(loser)
            }
        }
        if (ty < 0f) ty = viewH(tw) * GROUND_LINE

        val limit = max(0f, Balance.HALF_WORLD - tw / 2f)
        tx = tx.coerceIn(-limit, limit)
        val k = 1f - exp(-dt * 3.2f)
        camera.cx += (tx - camera.cx) * k
        camera.cy += (ty - camera.cy) * k
        camera.viewW += (tw - camera.viewW) * k
    }

    // ------------------------------------------------------------------ input (world coordinates)

    val anchorX: Float get() = game.slingshotX(game.current)
    val anchorY: Float get() = Balance.SLINGSHOT_Y

    /** Returns true if the press grabbed the slingshot; otherwise the drag pans the camera. */
    fun press(wx: Float, wy: Float): Boolean {
        if (game.phase != Phase.PLAN) return false
        val dx = wx - anchorX
        val dy = wy - anchorY
        if (dx * dx + dy * dy > GRAB_RADIUS * GRAB_RADIUS) return false
        aiming = true
        drag(wx, wy)
        return true
    }

    fun drag(wx: Float, wy: Float) {
        if (!aiming) return
        var dx = wx - anchorX
        var dy = wy - anchorY
        val len = sqrt(dx * dx + dy * dy)
        if (len > Balance.MAX_PULL) {
            dx *= Balance.MAX_PULL / len
            dy *= Balance.MAX_PULL / len
        }
        // The pouch cannot go underground.
        if (anchorY + dy < 0.45f) dy = 0.45f - anchorY
        pullX = dx
        pullY = dy
    }

    fun release() {
        if (!aiming) return
        aiming = false
        game.fire(pullX, pullY)
        pullX = 0f
        pullY = 0f
    }

    fun cancelAim() {
        aiming = false
        pullX = 0f
        pullY = 0f
    }

    fun pan(worldDx: Float) {
        if (game.phase == Phase.PLAN && !aiming) panOffset = (panOffset - worldDx).coerceIn(-2f * Balance.HALF_WORLD, 2f * Balance.HALF_WORLD)
    }

    fun tap() {
        game.tapSplit()
    }

    /** Launch velocity for the current pull, used to draw the trajectory guide. */
    fun launchVx(): Float = -pullX * game.launchSpeed() / Balance.MAX_PULL
    fun launchVy(): Float = -pullY * game.launchSpeed() / Balance.MAX_PULL

    // ------------------------------------------------------------------ HUD actions

    fun buy(track: Track) {
        game.buy(track)
        refreshHud()
    }

    fun repair() {
        game.repair()
        consumeEvents()
        refreshHud()
    }

    fun selectAmmo(ammo: Ammo) {
        game.selectAmmo(ammo)
        refreshHud()
    }

    fun nextRound() {
        if (game.phase == Phase.MATCH_OVER) game.newMatch() else game.nextRound()
        particles.clear()
        lastCurrent = game.current
        panOffset = 0f
        refreshHud()
    }

    fun restart() {
        game.newMatch()
        particles.clear()
        cancelAim()
        lastCurrent = game.current
        panOffset = 0f
        refreshHud()
    }

    private fun refreshHud() {
        val p = game.activePlayer
        val next = HudState(
            phase = game.phase,
            current = game.current,
            turn = game.turn,
            round = game.round,
            points = game.players.map { it.points },
            roundsWon = game.players.map { it.roundsWon },
            levels = Track.entries.map { p.level(it) },
            costs = Track.entries.map { game.cost(it) },
            canBuy = Track.entries.map { game.canBuy(it) },
            canRepair = game.canRepair(),
            ammo = p.unlockedAmmo(),
            selectedAmmo = p.selectedAmmo,
            suddenDeath = game.suddenDeath,
            roundWinner = game.roundWinner,
            canSplit = game.phase == Phase.RESOLVING && game.pieces.any {
                it.kind == PieceKind.PROJECTILE && it.ammo == Ammo.SPLIT && !it.isFragment && !it.hasHit
            },
            aiming = aiming,
        )
        if (next != hud) hud = next
    }

    companion object {
        const val STEP = 1f / 60f
        const val WIDE_ASPECT = 0.8f

        /** The ground sits this far up the screen, as a fraction of the view height below centre. */
        const val GROUND_LINE = 0.25f
        const val GRAB_RADIUS = 2.2f
    }
}
