package io.github.beretta299.sharejoy.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A rigid body: either an oriented box (halfW x halfH) or a circle (radius > 0).
 *
 * All state is plain floats so the solver allocates nothing per step.
 * World units are metres, kilograms-ish and seconds; y points up.
 */
class Body(
    val id: Int,
    var halfW: Float,
    var halfH: Float,
    val radius: Float = 0f,
    density: Float,
    var friction: Float = 0.6f,
    var restitution: Float = 0.05f,
) {
    var x = 0f
    var y = 0f
    var angle = 0f
    var vx = 0f
    var vy = 0f
    var omega = 0f

    var mass = 0f
        private set
    var invMass = 0f
        private set
    var invInertia = 0f
        private set

    /** Sleeping bodies act as static until something disturbs them; this keeps stacks rock solid. */
    var asleep = false
    var sleepTime = 0f

    /** Effective inverse mass / inertia for the current step (zero while asleep). */
    internal var im = 0f
    internal var ii = 0f

    fun wake() {
        asleep = false
        sleepTime = 0f
        im = invMass
        ii = invInertia
    }

    fun sleep() {
        if (invMass == 0f) return
        asleep = true
        vx = 0f
        vy = 0f
        omega = 0f
        im = 0f
        ii = 0f
    }

    val isCircle: Boolean get() = radius > 0f
    val isStatic: Boolean get() = invMass == 0f

    /** Free slot for the game layer (block, keeper, projectile...). */
    var tag: Any? = null

    /** Largest impact (in delta-v units) received during the last step; reset every step. */
    var impact = 0f

    /** How much harder this body hits others than its momentum alone would (projectiles punch above their weight). */
    var punch = 1f

    /** True while this body touched static ground during the last step. */
    var touchingGround = false

    /** Marks static bodies that count as "the ground" for [touchingGround]. */
    var isGround = false

    init {
        setDensity(density)
    }

    fun setDensity(density: Float) {
        if (density <= 0f) {
            mass = 0f
            invMass = 0f
            invInertia = 0f
            return
        }
        if (isCircle) {
            mass = density * PI_F * radius * radius
            invMass = 1f / mass
            invInertia = 1f / (0.5f * mass * radius * radius)
        } else {
            val w = halfW * 2f
            val h = halfH * 2f
            mass = density * w * h
            invMass = 1f / mass
            invInertia = 1f / (mass * (w * w + h * h) / 12f)
        }
    }

    /** Half extents of the axis-aligned bounding box. */
    fun aabbHalfW(): Float =
        if (isCircle) radius else abs(cos(angle)) * halfW + abs(sin(angle)) * halfH

    fun aabbHalfH(): Float =
        if (isCircle) radius else abs(sin(angle)) * halfW + abs(cos(angle)) * halfH

    fun speedSq(): Float = vx * vx + vy * vy

    companion object {
        const val PI_F = 3.1415927f
    }
}
