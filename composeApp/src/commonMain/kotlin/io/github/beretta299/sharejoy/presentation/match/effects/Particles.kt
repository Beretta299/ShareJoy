package io.github.beretta299.sharejoy.presentation.match.effects

import androidx.compose.ui.graphics.Color
import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.GameEvent
import io.github.beretta299.sharejoy.game.Material
import io.github.beretta299.sharejoy.util.Rng
import io.github.beretta299.sharejoy.util.lighten
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ParticleKind { DEBRIS, DUST, SPARK, FLASH, GLINT }

/** One speck of cosmetic debris. Positions and sizes are world metres, angles radians. */
class Particle {
    var kind = ParticleKind.DUST
    var x = 0f
    var y = 0f
    var velocityX = 0f
    var velocityY = 0f
    var rotation = 0f

    /** Radians per second. */
    var spin = 0f
    var size = 0.1f

    /** Width divided by height: long splinters for wood, chunky lumps for stone. */
    var aspectRatio = 1f
    var ageSeconds = 0f
    var lifetimeSeconds = 1f
    var color = Color.White
}

/** Purely cosmetic effects: rubble, dust, sparks. They never affect the simulation. */
/** All live particles. Spawns them from game events and moves them; never affects the simulation. */
class Particles {
    val items = ArrayList<Particle>()
    private val rng = Rng(99)

    fun clear() = items.clear()

    fun update(dt: Float) {
        var index = items.size - 1
        while (index >= 0) {
            val particle = items[index]
            particle.ageSeconds += dt
            if (particle.ageSeconds >= particle.lifetimeSeconds) {
                items.removeAt(index)
            } else {
                when (particle.kind) {
                    ParticleKind.DEBRIS, ParticleKind.SPARK -> {
                        particle.velocityY -= GRAVITY * dt
                        if (particle.y < 0.05f && particle.velocityY < 0f && abs(particle.x) > Balance.GORGE_HALF_WIDTH) {
                            // Rubble bounces once or twice on the ground, then lies still.
                            particle.y = 0.05f
                            particle.velocityY = -particle.velocityY * 0.3f
                            particle.velocityX *= 0.6f
                            particle.spin *= 0.5f
                        }
                    }
                    ParticleKind.DUST -> {
                        particle.velocityX *= 1f - 1.8f * dt
                        particle.velocityY = particle.velocityY * (1f - 1.8f * dt) + 0.5f * dt
                        particle.size += 0.9f * dt
                    }
                    ParticleKind.FLASH -> particle.size += 14f * dt
                    ParticleKind.GLINT -> particle.velocityY += 0.6f * dt
                }
                particle.x += particle.velocityX * dt
                particle.y += particle.velocityY * dt
                particle.rotation += particle.spin * dt
            }
            index--
        }
    }

    private fun spawn(kind: ParticleKind): Particle? {
        if (items.size > MAX_PARTICLES) return null
        val particle = Particle()
        particle.kind = kind
        items.add(particle)
        return particle
    }

    private fun materialColor(material: Material): Color = when (material) {
        Material.WOOD -> Color(0.50f, 0.33f, 0.18f).lighten(rng.range(-0.08f, 0.08f))
        Material.STONE -> Color(0.58f, 0.56f, 0.51f).lighten(rng.range(-0.1f, 0.08f))
        Material.IRON -> Color(0.33f, 0.36f, 0.40f).lighten(rng.range(-0.06f, 0.1f))
    }

    private fun dustColor(m: Material): Color = when (m) {
        Material.WOOD -> Color(0xFFBFA98A)
        Material.STONE -> Color(0xFFCFCAC0)
        Material.IRON -> Color(0xFF9AA0A8)
    }

    fun shatter(event: GameEvent.BlockDestroyed) {
        val cosAngle = cos(event.angle)
        val sinAngle = sin(event.angle)
        val area = event.halfWidth * event.halfHeight * 4f
        val chunks = min(26, (10 + area * 7).toInt())
        repeat(chunks) {
            val particle = spawn(ParticleKind.DEBRIS) ?: return
            val localX = rng.range(-event.halfWidth, event.halfWidth)
            val localY = rng.range(-event.halfHeight, event.halfHeight)
            particle.x = event.x + cosAngle * localX - sinAngle * localY
            particle.y = event.y + sinAngle * localX + cosAngle * localY
            particle.velocityX = event.velocityX * 0.5f + rng.range(-3.5f, 3.5f)
            particle.velocityY = event.velocityY * 0.5f + rng.range(0.5f, 5.5f)
            particle.rotation = rng.range(0f, FULL_TURN)
            particle.spin = rng.range(-9f, 9f)
            particle.size = rng.range(0.1f, 0.3f)
            particle.aspectRatio = if (event.material == Material.WOOD) rng.range(2f, 4.5f) else rng.range(0.7f, 1.5f)
            particle.lifetimeSeconds = rng.range(1.6f, 3.2f)
            particle.color = materialColor(event.material)
        }
        repeat(7) {
            val particle = spawn(ParticleKind.DUST) ?: return
            particle.x = event.x + rng.range(-event.halfWidth, event.halfWidth)
            particle.y = event.y + rng.range(-event.halfHeight, event.halfHeight)
            particle.velocityX = rng.range(-1.6f, 1.6f)
            particle.velocityY = rng.range(-0.2f, 1.6f)
            particle.size = rng.range(0.35f, 0.8f)
            particle.lifetimeSeconds = rng.range(0.8f, 1.7f)
            particle.color = dustColor(event.material)
        }
    }

    fun impact(x: Float, y: Float, strength: Float, material: Material) {
        val puffs = min(6, 1 + (strength / 4f).toInt())
        repeat(puffs) {
            val particle = spawn(ParticleKind.DUST) ?: return
            particle.x = x + rng.range(-0.4f, 0.4f)
            particle.y = y + rng.range(-0.4f, 0.4f)
            particle.velocityX = rng.range(-1.2f, 1.2f)
            particle.velocityY = rng.range(0f, 1.2f)
            particle.size = rng.range(0.2f, 0.5f)
            particle.lifetimeSeconds = rng.range(0.5f, 1.1f)
            particle.color = dustColor(material)
        }
        if (material != Material.WOOD && strength > 5f) {
            repeat(min(8, (strength / 2f).toInt())) {
                val particle = spawn(ParticleKind.SPARK) ?: return
                particle.x = x
                particle.y = y
                particle.velocityX = rng.range(-6f, 6f)
                particle.velocityY = rng.range(1f, 7f)
                particle.size = rng.range(0.04f, 0.08f)
                particle.lifetimeSeconds = rng.range(0.25f, 0.6f)
                particle.color = Color(0xFFFFD27A)
            }
        }
        if (strength > 6f) {
            repeat(3) {
                val particle = spawn(ParticleKind.DEBRIS) ?: return
                particle.x = x
                particle.y = y
                particle.velocityX = rng.range(-3f, 3f)
                particle.velocityY = rng.range(1f, 4f)
                particle.spin = rng.range(-8f, 8f)
                particle.size = rng.range(0.06f, 0.14f)
                particle.aspectRatio = if (material == Material.WOOD) 3f else 1f
                particle.lifetimeSeconds = rng.range(0.8f, 1.6f)
                particle.color = materialColor(material)
            }
        }
    }

    fun explosion(x: Float, y: Float, radius: Float) {
        spawn(ParticleKind.FLASH)?.apply {
            this.x = x; this.y = y; size = radius * 0.3f; lifetimeSeconds = 0.28f; color = Color(0xFFFFE9B0)
        }
        repeat(34) {
            val particle = spawn(ParticleKind.SPARK) ?: return
            val direction = rng.range(0f, FULL_TURN)
            val speed = rng.range(4f, 15f)
            particle.x = x; particle.y = y
            particle.velocityX = cos(direction) * speed
            particle.velocityY = sin(direction) * speed + 2f
            particle.size = rng.range(0.05f, 0.12f)
            particle.lifetimeSeconds = rng.range(0.3f, 0.9f)
            particle.color = if (rng.next() < 0.5f) Color(0xFFFFB347) else Color(0xFFFFE08A)
        }
        repeat(16) {
            val particle = spawn(ParticleKind.DUST) ?: return
            particle.x = x + rng.range(-1f, 1f)
            particle.y = y + rng.range(-1f, 1f)
            particle.velocityX = rng.range(-3.5f, 3.5f)
            particle.velocityY = rng.range(-0.5f, 3.5f)
            particle.size = rng.range(0.6f, 1.3f)
            particle.lifetimeSeconds = rng.range(1f, 2.2f)
            particle.color = if (rng.next() < 0.5f) Color(0xFF4A4541) else Color(0xFF8C8379)
        }
    }

    fun sparkle(x: Float, y: Float, built: Boolean) {
        repeat(6) {
            val particle = spawn(ParticleKind.GLINT) ?: return
            particle.x = x + rng.range(-0.6f, 0.6f)
            particle.y = y + rng.range(-0.6f, 0.6f)
            particle.velocityX = rng.range(-0.4f, 0.4f)
            particle.velocityY = rng.range(0.3f, 1.2f)
            particle.size = rng.range(0.08f, 0.16f)
            particle.lifetimeSeconds = rng.range(0.5f, 1f)
            particle.color = if (built) Color(0xFFFFE08A) else Color(0xFF9BE28F)
        }
    }

    companion object {
        private const val MAX_PARTICLES = 700
        private const val GRAVITY = 9.81f
        private const val FULL_TURN = 6.28f
    }
}
