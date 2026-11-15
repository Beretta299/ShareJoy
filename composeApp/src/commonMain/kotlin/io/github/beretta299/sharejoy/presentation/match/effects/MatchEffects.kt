package io.github.beretta299.sharejoy.presentation.match.effects

import io.github.beretta299.sharejoy.game.GameEvent
import io.github.beretta299.sharejoy.game.Material
import kotlin.math.max
import kotlin.math.min

/** Turns game events into feedback that never affects the simulation: rubble, screen shake, slow motion. */
class MatchEffects {
    val particles = Particles()

    var shake = 0f
        private set
    private var slowMo = 0f

    /** How fast the simulation runs right now: slower for a moment when a Keeper falls. */
    val timeScale: Float get() = if (slowMo > 0f) SLOW_MOTION_SCALE else 1f

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.BlockDestroyed -> {
                particles.shatter(event)
                shake = max(shake, 0.35f)
            }
            is GameEvent.Impact -> {
                particles.impact(event.x, event.y, event.strength, event.material)
                if (event.strength > 7f) shake = max(shake, min(0.5f, event.strength * 0.03f))
            }
            is GameEvent.Explosion -> {
                particles.explosion(event.x, event.y, event.radius)
                shake = 1f
            }
            is GameEvent.KeeperDown -> {
                slowMo = SLOW_MOTION_SECONDS
                shake = max(shake, 0.6f)
                particles.impact(event.x, event.y, 14f, Material.WOOD)
            }
            is GameEvent.Built -> particles.sparkle(event.x, event.y, built = true)
            is GameEvent.Repaired -> particles.sparkle(event.x, event.y, built = false)
            is GameEvent.Fired, GameEvent.Split -> Unit
        }
    }

    /** [dt] is real time; [simDt] is the time the simulation advanced, which is less during slow motion. */
    fun update(dt: Float, simDt: Float) {
        slowMo = max(0f, slowMo - dt)
        particles.update(simDt)
        shake = max(0f, shake - dt * SHAKE_DECAY)
    }

    fun clear() {
        particles.clear()
        shake = 0f
        slowMo = 0f
    }

    private companion object {
        const val SLOW_MOTION_SCALE = 0.28f
        const val SLOW_MOTION_SECONDS = 1.6f
        const val SHAKE_DECAY = 2.2f
    }
}
