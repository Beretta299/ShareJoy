package io.github.beretta299.sharejoy.presentation.match

import androidx.lifecycle.ViewModel
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.game.GameCommand
import io.github.beretta299.sharejoy.game.GameEvent
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.game.checksum
import io.github.beretta299.sharejoy.game.execute
import io.github.beretta299.sharejoy.presentation.match.aim.SlingshotAim
import io.github.beretta299.sharejoy.presentation.match.camera.CameraDirector
import io.github.beretta299.sharejoy.presentation.match.effects.MatchEffects
import io.github.beretta299.sharejoy.presentation.match.sync.LockstepSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/**
 * Runs one match for the screen.
 *
 * The rules live in the domain ([Game]); this class only drives them. It steps the simulation at a fixed rate,
 * turns player input into [GameCommand]s, and publishes two things: [uiState] for the HUD and [scene] for the
 * canvas.
 *
 * With no [sync] both players share this device and commands run at once. With a [sync] this phone is one of
 * two paired phones: it controls only [LockstepSync.localPlayer], and commands run in lockstep with the other
 * phone so both simulations stay identical.
 */
class MatchViewModel(
    private val game: Game = Game(),
    private val sync: LockstepSync? = null,
    private val onLeave: () -> Unit = {},
) : ViewModel() {
    private val cameras = CameraDirector(game)
    private val aim = SlingshotAim(game)
    private val effects = MatchEffects()
    private val localPlayer: Int? = sync?.localPlayer

    private var time = 0f
    private var accumulator = 0f
    private var secondsWaitingForPeer = 0f
    private var aimChanged = false

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    val scene: MatchScene = object : MatchScene {
        override val game get() = this@MatchViewModel.game
        override val camera get() = cameras.single
        override val pairCameras get() = cameras.paired
        override val particles get() = effects.particles
        override val time get() = this@MatchViewModel.time
        override val shake get() = effects.shake
        override val aiming get() = aim.active || remoteAim() != null
        override val pullX get() = if (aim.active) aim.pullX else remoteAim()?.pullX ?: 0f
        override val pullY get() = if (aim.active) aim.pullY else remoteAim()?.pullY ?: 0f
        override val anchorX get() = aim.anchorX
        override val anchorY get() = aim.anchorY
        override fun launchVelocityX() = -pullX * game.launchSpeed() / Balance.MAX_SLINGSHOT_PULL
        override fun launchVelocityY() = -pullY * game.launchSpeed() / Balance.MAX_SLINGSHOT_PULL
    }

    /** The other phone's player pulling their slingshot, shown on this phone too. */
    private fun remoteAim() = sync?.remoteAim?.takeIf { game.phase == Phase.PLAN && game.currentPlayer != localPlayer }

    // ------------------------------------------------------------------ frame loop

    /** Called once per display frame. [screenAspect] is the width of one phone screen divided by its height. */
    fun onFrame(realDt: Float, screenAspect: Float) {
        val dt = min(realDt, MAX_FRAME_SECONDS)
        time += dt
        sync?.receiveAll()
        stepSimulation(dt * effects.timeScale, dt)
        shareAim()
        sync?.sendConfirm()

        effects.update(dt, dt * effects.timeScale)
        cameras.update(dt, screenAspect)
        publish()
    }

    private fun stepSimulation(simDt: Float, dt: Float) {
        accumulator += simDt
        var steps = 0
        while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME && canStep()) {
            stepOnce()
            accumulator -= STEP
            steps++
        }
        val waitingForPeer = sync != null && !sync.canStep() && accumulator >= STEP
        if (waitingForPeer) {
            // Hold back until the other phone catches up, without building a burst of steps to run later.
            accumulator = min(accumulator, STEP)
            secondsWaitingForPeer += dt
        } else {
            secondsWaitingForPeer = 0f
            // Too far behind to catch up: drop the backlog rather than spiral.
            if (steps == MAX_STEPS_PER_FRAME) accumulator = 0f
        }
    }

    private fun canStep(): Boolean = sync?.canStep() ?: true

    private fun stepOnce() {
        sync?.takeCommandsForTick()?.forEach { (command, player) -> run(command, player) }
        game.update(STEP)
        dispatchGameEvents()
        sync?.finishTick()
        // Both phones are at the same tick, so their games must match exactly. Compare twice a second.
        if (sync != null && sync.tick % CHECKSUM_INTERVAL_TICKS == 0) sync.shareChecksum(game.checksum())
    }

    private fun dispatchGameEvents() {
        for (event in game.events) {
            if (event is GameEvent.Fired) cameras.onShotFired(event.x)
            effects.onEvent(event)
        }
        game.events.clear()
    }

    // ------------------------------------------------------------------ commands

    /** Runs [command] now on a shared device, or schedules it in lockstep on a paired phone. */
    private fun submit(command: GameCommand) {
        if (sync == null) {
            run(command, game.currentPlayer)
            publish()
        } else {
            sync.submit(command)
        }
    }

    private fun run(command: GameCommand, player: Int) {
        val changed = game.execute(command, player)
        val startsOver = command == GameCommand.NextRound || command == GameCommand.Restart
        if (changed && startsOver) {
            effects.clear()
            aim.cancel()
            cameras.resetPan()
        }
        dispatchGameEvents()
    }

    // ------------------------------------------------------------------ HUD actions

    fun onAction(action: MatchAction) {
        when (action) {
            is MatchAction.BuyUpgrade -> submit(GameCommand.BuyUpgrade(action.track))
            MatchAction.Repair -> submit(GameCommand.Repair)
            is MatchAction.SelectAmmo -> submit(GameCommand.SelectAmmo(action.ammo))
            MatchAction.NextRound -> submit(GameCommand.NextRound)
            MatchAction.Restart -> submit(GameCommand.Restart)
            MatchAction.Leave -> onLeave()
        }
    }

    // ------------------------------------------------------------------ pointer input, in world metres

    private val isMyTurn: Boolean get() = localPlayer == null || localPlayer == game.currentPlayer

    /** Returns true if the press grabbed the slingshot; otherwise a drag pans the camera. */
    fun onPointerDown(worldX: Float, worldY: Float): Boolean {
        val grabbed = isMyTurn && aim.grab(worldX, worldY)
        aimChanged = grabbed
        publish()
        return grabbed
    }

    fun onAimDrag(worldX: Float, worldY: Float) {
        aim.dragTo(worldX, worldY)
        aimChanged = true
    }

    fun onAimRelease() {
        aim.release()?.let(::submit)
        aimChanged = true
        publish()
    }

    fun onPan(worldDx: Float) {
        if (game.phase == Phase.PLAN && !aim.active) cameras.pan(worldDx)
    }

    /** A tap anywhere splits a split shot in flight. */
    fun onTap() {
        if (isMyTurn) submit(GameCommand.Split)
    }

    /** Sends the slingshot pull to the other phone, at most once a frame. */
    private fun shareAim() {
        if (!aimChanged) return
        aimChanged = false
        sync?.sendAim(aim.active, aim.pullX, aim.pullY)
    }

    // ------------------------------------------------------------------ UI state

    private fun publish() {
        _uiState.value = buildUiState()
    }

    private fun buildUiState(): MatchUiState {
        val player = game.activePlayer
        return MatchUiState(
            phase = game.phase,
            currentPlayer = game.currentPlayer,
            localPlayer = localPlayer,
            turn = game.turn,
            round = game.round,
            points = game.players.map { it.points },
            roundsWon = game.players.map { it.roundsWon },
            levels = Track.entries.map { player.level(it) },
            costs = Track.entries.map { game.upgradeCost(it) },
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
            link = linkState(),
        )
    }

    private fun linkState(): MatchLinkState = when {
        sync == null -> MatchLinkState.SHARED_DEVICE
        !sync.isPeerConnected -> MatchLinkState.DISCONNECTED
        sync.isOutOfSync -> MatchLinkState.OUT_OF_SYNC
        secondsWaitingForPeer > WAITING_NOTICE_SECONDS -> MatchLinkState.WAITING
        else -> MatchLinkState.CONNECTED
    }

    companion object {
        const val STEP = 1f / 60f
        private const val MAX_FRAME_SECONDS = 0.05f
        private const val MAX_STEPS_PER_FRAME = 4
        private const val CHECKSUM_INTERVAL_TICKS = 30

        /** A pause shorter than this is normal network jitter and not worth mentioning. */
        private const val WAITING_NOTICE_SECONDS = 0.6f
    }
}
