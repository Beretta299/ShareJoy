package io.github.beretta299.sharejoy.presentation.match

import androidx.lifecycle.ViewModel
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.GameEvent
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.presentation.match.aim.SlingshotAim
import io.github.beretta299.sharejoy.presentation.match.camera.CameraDirector
import io.github.beretta299.sharejoy.presentation.match.effects.MatchEffects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/**
 * Runs one match for the screen.
 *
 * The rules live in the domain ([Game]); this class only drives them. It steps the simulation at a fixed rate,
 * forwards player input, and publishes two things: [uiState] for the HUD and [scene] for the canvas.
 */
class MatchViewModel(private val game: Game = Game()) : ViewModel() {
    private val cameras = CameraDirector(game)
    private val aim = SlingshotAim(game)
    private val effects = MatchEffects()

    private var time = 0f
    private var accumulator = 0f

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    val scene: MatchScene = object : MatchScene {
        override val game get() = this@MatchViewModel.game
        override val camera get() = cameras.single
        override val pairCameras get() = cameras.paired
        override val particles get() = effects.particles
        override val time get() = this@MatchViewModel.time
        override val shake get() = effects.shake
        override val aiming get() = aim.active
        override val pullX get() = aim.pullX
        override val pullY get() = aim.pullY
        override val anchorX get() = aim.anchorX
        override val anchorY get() = aim.anchorY
        override fun launchVx() = aim.launchVx()
        override fun launchVy() = aim.launchVy()
    }

    // ------------------------------------------------------------------ frame loop

    /** Called once per display frame. [screenAspect] is the width of one phone screen divided by its height. */
    fun onFrame(realDt: Float, screenAspect: Float) {
        val dt = min(realDt, MAX_FRAME_SECONDS)
        time += dt
        val simDt = dt * effects.timeScale

        accumulator += simDt
        var steps = 0
        while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME) {
            game.update(STEP)
            accumulator -= STEP
            steps++
            dispatchGameEvents()
        }
        // Too far behind to catch up: drop the backlog rather than spiral.
        if (steps == MAX_STEPS_PER_FRAME) accumulator = 0f

        effects.update(dt, simDt)
        cameras.update(dt, screenAspect)
        publish()
    }

    private fun dispatchGameEvents() {
        for (event in game.events) {
            if (event is GameEvent.Fired) cameras.onShotFired(event.x)
            effects.onEvent(event)
        }
        game.events.clear()
    }

    // ------------------------------------------------------------------ HUD actions

    fun onAction(action: MatchAction) {
        when (action) {
            is MatchAction.BuyUpgrade -> game.buy(action.track)
            MatchAction.Repair -> game.repair()
            is MatchAction.SelectAmmo -> game.selectAmmo(action.ammo)
            MatchAction.NextRound -> startNext(newMatch = game.phase == Phase.MATCH_OVER)
            MatchAction.Restart -> startNext(newMatch = true)
        }
        dispatchGameEvents()
        publish()
    }

    private fun startNext(newMatch: Boolean) {
        if (newMatch) game.newMatch() else game.nextRound()
        effects.clear()
        aim.cancel()
        cameras.resetPan()
    }

    // ------------------------------------------------------------------ pointer input, in world metres

    /** Returns true if the press grabbed the slingshot; otherwise a drag pans the camera. */
    fun onPointerDown(worldX: Float, worldY: Float): Boolean = aim.grab(worldX, worldY).also { publish() }

    fun onAimDrag(worldX: Float, worldY: Float) = aim.dragTo(worldX, worldY)

    fun onAimRelease() {
        aim.release()
        publish()
    }

    fun onPan(worldDx: Float) {
        if (game.phase == Phase.PLAN && !aim.active) cameras.pan(worldDx)
    }

    /** A tap anywhere splits a split shot in flight. */
    fun onTap() {
        game.tapSplit()
    }

    // ------------------------------------------------------------------ UI state

    private fun publish() {
        _uiState.value = buildUiState()
    }

    private fun buildUiState(): MatchUiState {
        val player = game.activePlayer
        return MatchUiState(
            phase = game.phase,
            current = game.current,
            turn = game.turn,
            round = game.round,
            points = game.players.map { it.points },
            roundsWon = game.players.map { it.roundsWon },
            levels = Track.entries.map { player.level(it) },
            costs = Track.entries.map { game.cost(it) },
            canBuy = Track.entries.map { game.canBuy(it) },
            canRepair = game.canRepair(),
            ammo = player.unlockedAmmo(),
            selectedAmmo = player.selectedAmmo,
            suddenDeath = game.suddenDeath,
            roundWinner = game.roundWinner,
            canSplit = game.phase == Phase.RESOLVING && game.pieces.any {
                it.kind == PieceKind.PROJECTILE && it.ammo == Ammo.SPLIT && !it.isFragment && !it.hasHit
            },
            aiming = aim.active,
        )
    }

    companion object {
        const val STEP = 1f / 60f
        private const val MAX_FRAME_SECONDS = 0.05f
        private const val MAX_STEPS_PER_FRAME = 4
    }
}
