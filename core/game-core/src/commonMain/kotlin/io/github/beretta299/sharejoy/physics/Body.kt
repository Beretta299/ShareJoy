package io.github.beretta299.sharejoy.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A rigid body: either an oriented box ([halfWidth] x [halfHeight]) or a circle ([radius] > 0).
 *
 * All state is plain floats so the solver allocates nothing per step.
 * World units are metres, kilograms and seconds; y points up.
 */
class Body(
    val id: Int,
    var halfWidth: Float,
    var halfHeight: Float,
    val radius: Float = 0f,
    density: Float,
    var friction: Float = 0.6f,
    var restitution: Float = 0.05f,
) {
    var x = 0f
    var y = 0f

    /** Rotation in radians, counter-clockwise. */
    var angle = 0f
    var velocityX = 0f
    var velocityY = 0f

    /** Radians per second, counter-clockwise. */
    var angularVelocity = 0f

    var mass = 0f
        private set
    var inverseMass = 0f
        private set
    var inverseInertia = 0f
        private set

    /** Sleeping bodies act as static until something disturbs them; this keeps stacks rock solid. */
    var isAsleep = false
        private set

    /** How long the body has been nearly still. It falls asleep once this passes a threshold. */
    internal var restingSeconds = 0f

    /**
     * Inverse mass and inertia the solver uses this step. Equal to [inverseMass] and [inverseInertia]
     * while awake, zero while asleep, so a sleeping body behaves as immovable.
     */
    internal var solverInverseMass = 0f
    internal var solverInverseInertia = 0f

    /** Free slot for the game layer to link the body back to its owner (block, Keeper, projectile). */
    var userData: Any? = null

    /** Largest impact received during the last step, as a change in velocity (m/s). Reset every step. */
    var strongestImpact = 0f

    /** How much harder this body hits others than its momentum alone would. Projectiles hit above their weight. */
    var impactMultiplier = 1f

    /** True while this body touched static ground during the last step. */
    var isTouchingGround = false

    /** Marks static bodies that count as "the ground" for [isTouchingGround]. */
    var isGround = false

    val isCircle: Boolean get() = radius > 0f
    val isStatic: Boolean get() = inverseMass == 0f

    init {
        setDensity(density)
    }

    fun wake() {
        isAsleep = false
        restingSeconds = 0f
        solverInverseMass = inverseMass
        solverInverseInertia = inverseInertia
    }

    fun sleep() {
        if (isStatic) return
        isAsleep = true
        velocityX = 0f
        velocityY = 0f
        angularVelocity = 0f
        solverInverseMass = 0f
        solverInverseInertia = 0f
    }

    /** Recomputes mass and inertia. A density of zero or less makes the body static. */
    fun setDensity(density: Float) {
        if (density <= 0f) {
            mass = 0f
            inverseMass = 0f
            inverseInertia = 0f
            return
        }
        if (isCircle) {
            mass = density * PI_FLOAT * radius * radius
            inverseMass = 1f / mass
            inverseInertia = 1f / (0.5f * mass * radius * radius)
        } else {
            val width = halfWidth * 2f
            val height = halfHeight * 2f
            mass = density * width * height
            inverseMass = 1f / mass
            inverseInertia = 1f / (mass * (width * width + height * height) / 12f)
        }
    }

    /** Half the width of the axis-aligned box that encloses this body at its current angle. */
    fun boundingHalfWidth(): Float =
        if (isCircle) radius else abs(cos(angle)) * halfWidth + abs(sin(angle)) * halfHeight

    /** Half the height of the axis-aligned box that encloses this body at its current angle. */
    fun boundingHalfHeight(): Float =
        if (isCircle) radius else abs(sin(angle)) * halfWidth + abs(cos(angle)) * halfHeight

    fun speedSquared(): Float = velocityX * velocityX + velocityY * velocityY

    private companion object {
        const val PI_FLOAT = PI.toFloat()
    }
}
