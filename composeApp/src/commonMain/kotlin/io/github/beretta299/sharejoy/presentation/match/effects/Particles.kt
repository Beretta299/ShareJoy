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

class Particle {
    var kind = ParticleKind.DUST
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var rot = 0f
    var spin = 0f
    var size = 0.1f
    var aspect = 1f
    var life = 0f
    var maxLife = 1f
    var color = Color.White
}

/** Purely cosmetic effects: rubble, dust, sparks. They never affect the simulation. */
class Particles {
    val items = ArrayList<Particle>()
    private val rng = Rng(99)

    fun clear() = items.clear()

    fun update(dt: Float) {
        var i = items.size - 1
        while (i >= 0) {
            val p = items[i]
            p.life += dt
            if (p.life >= p.maxLife) {
                items.removeAt(i)
            } else {
                when (p.kind) {
                    ParticleKind.DEBRIS, ParticleKind.SPARK -> {
                        p.vy -= 9.81f * dt
                        if (p.y < 0.05f && p.vy < 0f && abs(p.x) > Balance.GORGE_HALF_WIDTH) {
                            // Rubble bounces once or twice on the ground, then lies still.
                            p.y = 0.05f
                            p.vy = -p.vy * 0.3f
                            p.vx *= 0.6f
                            p.spin *= 0.5f
                        }
                    }
                    ParticleKind.DUST -> {
                        p.vx *= 1f - 1.8f * dt
                        p.vy = p.vy * (1f - 1.8f * dt) + 0.5f * dt
                        p.size += 0.9f * dt
                    }
                    ParticleKind.FLASH -> p.size += 14f * dt
                    ParticleKind.GLINT -> p.vy += 0.6f * dt
                }
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rot += p.spin * dt
            }
            i--
        }
    }

    private fun spawn(kind: ParticleKind): Particle? {
        if (items.size > MAX) return null
        val p = Particle()
        p.kind = kind
        items.add(p)
        return p
    }

    private fun materialColor(m: Material): Color = when (m) {
        Material.WOOD -> Color(0.50f, 0.33f, 0.18f).lighten(rng.range(-0.08f, 0.08f))
        Material.STONE -> Color(0.58f, 0.56f, 0.51f).lighten(rng.range(-0.1f, 0.08f))
        Material.IRON -> Color(0.33f, 0.36f, 0.40f).lighten(rng.range(-0.06f, 0.1f))
    }

    private fun dustColor(m: Material): Color = when (m) {
        Material.WOOD -> Color(0xFFBFA98A)
        Material.STONE -> Color(0xFFCFCAC0)
        Material.IRON -> Color(0xFF9AA0A8)
    }

    fun shatter(e: GameEvent.BlockDestroyed) {
        val c = cos(e.angle)
        val s = sin(e.angle)
        val area = e.halfW * e.halfH * 4f
        val chunks = min(26, (10 + area * 7).toInt())
        repeat(chunks) {
            val p = spawn(ParticleKind.DEBRIS) ?: return
            val lx = rng.range(-e.halfW, e.halfW)
            val ly = rng.range(-e.halfH, e.halfH)
            p.x = e.x + c * lx - s * ly
            p.y = e.y + s * lx + c * ly
            p.vx = e.vx * 0.5f + rng.range(-3.5f, 3.5f)
            p.vy = e.vy * 0.5f + rng.range(0.5f, 5.5f)
            p.rot = rng.range(0f, 6.28f)
            p.spin = rng.range(-9f, 9f)
            p.size = rng.range(0.1f, 0.3f)
            p.aspect = if (e.material == Material.WOOD) rng.range(2f, 4.5f) else rng.range(0.7f, 1.5f)
            p.maxLife = rng.range(1.6f, 3.2f)
            p.color = materialColor(e.material)
        }
        repeat(7) {
            val p = spawn(ParticleKind.DUST) ?: return
            p.x = e.x + rng.range(-e.halfW, e.halfW)
            p.y = e.y + rng.range(-e.halfH, e.halfH)
            p.vx = rng.range(-1.6f, 1.6f)
            p.vy = rng.range(-0.2f, 1.6f)
            p.size = rng.range(0.35f, 0.8f)
            p.maxLife = rng.range(0.8f, 1.7f)
            p.color = dustColor(e.material)
        }
    }

    fun impact(x: Float, y: Float, strength: Float, material: Material) {
        val n = min(6, 1 + (strength / 4f).toInt())
        repeat(n) {
            val p = spawn(ParticleKind.DUST) ?: return
            p.x = x + rng.range(-0.4f, 0.4f)
            p.y = y + rng.range(-0.4f, 0.4f)
            p.vx = rng.range(-1.2f, 1.2f)
            p.vy = rng.range(0f, 1.2f)
            p.size = rng.range(0.2f, 0.5f)
            p.maxLife = rng.range(0.5f, 1.1f)
            p.color = dustColor(material)
        }
        if (material != Material.WOOD && strength > 5f) {
            repeat(min(8, (strength / 2f).toInt())) {
                val p = spawn(ParticleKind.SPARK) ?: return
                p.x = x
                p.y = y
                p.vx = rng.range(-6f, 6f)
                p.vy = rng.range(1f, 7f)
                p.size = rng.range(0.04f, 0.08f)
                p.maxLife = rng.range(0.25f, 0.6f)
                p.color = Color(0xFFFFD27A)
            }
        }
        if (strength > 6f) {
            repeat(3) {
                val p = spawn(ParticleKind.DEBRIS) ?: return
                p.x = x
                p.y = y
                p.vx = rng.range(-3f, 3f)
                p.vy = rng.range(1f, 4f)
                p.spin = rng.range(-8f, 8f)
                p.size = rng.range(0.06f, 0.14f)
                p.aspect = if (material == Material.WOOD) 3f else 1f
                p.maxLife = rng.range(0.8f, 1.6f)
                p.color = materialColor(material)
            }
        }
    }

    fun explosion(x: Float, y: Float, radius: Float) {
        spawn(ParticleKind.FLASH)?.apply {
            this.x = x; this.y = y; size = radius * 0.3f; maxLife = 0.28f; color = Color(0xFFFFE9B0)
        }
        repeat(34) {
            val p = spawn(ParticleKind.SPARK) ?: return
            val a = rng.range(0f, 6.28f)
            val v = rng.range(4f, 15f)
            p.x = x; p.y = y
            p.vx = cos(a) * v
            p.vy = sin(a) * v + 2f
            p.size = rng.range(0.05f, 0.12f)
            p.maxLife = rng.range(0.3f, 0.9f)
            p.color = if (rng.next() < 0.5f) Color(0xFFFFB347) else Color(0xFFFFE08A)
        }
        repeat(16) {
            val p = spawn(ParticleKind.DUST) ?: return
            p.x = x + rng.range(-1f, 1f)
            p.y = y + rng.range(-1f, 1f)
            p.vx = rng.range(-3.5f, 3.5f)
            p.vy = rng.range(-0.5f, 3.5f)
            p.size = rng.range(0.6f, 1.3f)
            p.maxLife = rng.range(1f, 2.2f)
            p.color = if (rng.next() < 0.5f) Color(0xFF4A4541) else Color(0xFF8C8379)
        }
    }

    fun sparkle(x: Float, y: Float, built: Boolean) {
        repeat(6) {
            val p = spawn(ParticleKind.GLINT) ?: return
            p.x = x + rng.range(-0.6f, 0.6f)
            p.y = y + rng.range(-0.6f, 0.6f)
            p.vx = rng.range(-0.4f, 0.4f)
            p.vy = rng.range(0.3f, 1.2f)
            p.size = rng.range(0.08f, 0.16f)
            p.maxLife = rng.range(0.5f, 1f)
            p.color = if (built) Color(0xFFFFE08A) else Color(0xFF9BE28F)
        }
    }

    companion object {
        private const val MAX = 700
    }
}
