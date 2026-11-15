package io.github.beretta299.sharejoy.physics

import kotlin.math.max
import kotlin.math.min

/**
 * Resolves contacts with sequential impulses, as in Box2D-Lite.
 *
 * Each step runs in three passes over every manifold: [prepare] measures how hard bodies are approaching
 * (for bounce and damage) before anything changes, [warmStart] re-applies last step's impulses, and
 * [solveVelocities] runs several times to push the bodies apart and apply friction.
 */
internal object ContactSolver {
    /** Overlap tolerated without correction, so resting contacts do not jitter. */
    private const val ALLOWED_PENETRATION = 0.01f

    /** Fraction of the remaining overlap corrected per step. */
    private const val POSITION_CORRECTION = 0.2f

    /** Approach speeds below this are resting contact, not impacts. */
    private const val IMPACT_SPEED = 1.0f

    /** Bodies only bounce above this approach speed; slower contacts just settle. */
    private const val BOUNCE_SPEED = 2f

    fun prepare(manifold: ContactManifold, inverseDt: Float) {
        val a = manifold.bodyA
        val b = manifold.bodyB
        if (a.isGround && a.isStatic && !b.isStatic) b.isTouchingGround = true
        if (b.isGround && b.isStatic && !a.isStatic) a.isTouchingGround = true
        if (manifold.isFrozen) return

        for (i in 0 until manifold.contactCount) {
            val contact = manifold.contacts[i]
            // Lever arms from each body's centre to the contact point.
            val armAX = contact.pointX - a.x
            val armAY = contact.pointY - a.y
            val armBX = contact.pointX - b.x
            val armBY = contact.pointY - b.y

            contact.normalMass = 1f / effectiveMass(a, b, armAX, armAY, armBX, armBY, contact.normalX, contact.normalY)
            contact.tangentMass = 1f / effectiveMass(a, b, armAX, armAY, armBX, armBY, contact.tangentX, contact.tangentY)
            contact.velocityBias = -POSITION_CORRECTION * inverseDt * min(0f, contact.separation + ALLOWED_PENETRATION)

            // Approach speed before solving drives both bounce and impact damage.
            val normalSpeed = relativeVelocityX(a, b, armAY, armBY) * contact.normalX +
                relativeVelocityY(a, b, armAX, armBX) * contact.normalY
            if (normalSpeed < -IMPACT_SPEED) recordImpact(manifold, contact, normalSpeed)
        }
    }

    private fun recordImpact(manifold: ContactManifold, contact: Contact, normalSpeed: Float) {
        val a = manifold.bodyA
        val b = manifold.bodyB
        val impulse = contact.normalMass * -normalSpeed
        if (!a.isStatic) a.strongestImpact = max(a.strongestImpact, impulse * a.inverseMass * b.impactMultiplier)
        if (!b.isStatic) b.strongestImpact = max(b.strongestImpact, impulse * b.inverseMass * a.impactMultiplier)
        if (manifold.restitution > 0f && normalSpeed < -BOUNCE_SPEED) {
            contact.velocityBias = max(contact.velocityBias, -manifold.restitution * normalSpeed)
        }
    }

    /** Re-applies last step's impulses. Runs after every manifold has measured its approach speed. */
    fun warmStart(manifold: ContactManifold) {
        if (manifold.isFrozen) return
        val a = manifold.bodyA
        val b = manifold.bodyB
        for (i in 0 until manifold.contactCount) {
            val contact = manifold.contacts[i]
            val impulseX = contact.normalImpulse * contact.normalX + contact.tangentImpulse * contact.tangentX
            val impulseY = contact.normalImpulse * contact.normalY + contact.tangentImpulse * contact.tangentY
            applyImpulse(a, b, contact, impulseX, impulseY)
        }
    }

    fun solveVelocities(manifold: ContactManifold) {
        if (manifold.isFrozen) return
        for (i in 0 until manifold.contactCount) {
            val contact = manifold.contacts[i]
            solveNormal(manifold, contact)
            solveFriction(manifold, contact)
        }
    }

    /** Pushes the bodies apart along the normal. The total impulse may never pull them together. */
    private fun solveNormal(manifold: ContactManifold, contact: Contact) {
        val a = manifold.bodyA
        val b = manifold.bodyB
        val normalSpeed = relativeVelocityX(a, b, contact.pointY - a.y, contact.pointY - b.y) * contact.normalX +
            relativeVelocityY(a, b, contact.pointX - a.x, contact.pointX - b.x) * contact.normalY
        val previous = contact.normalImpulse
        contact.normalImpulse = max(previous + contact.normalMass * (-normalSpeed + contact.velocityBias), 0f)
        val delta = contact.normalImpulse - previous
        applyImpulse(a, b, contact, delta * contact.normalX, delta * contact.normalY)
    }

    /** Resists sliding, up to the friction limit set by how hard the bodies press together. */
    private fun solveFriction(manifold: ContactManifold, contact: Contact) {
        val a = manifold.bodyA
        val b = manifold.bodyB
        val tangentSpeed = relativeVelocityX(a, b, contact.pointY - a.y, contact.pointY - b.y) * contact.tangentX +
            relativeVelocityY(a, b, contact.pointX - a.x, contact.pointX - b.x) * contact.tangentY
        val limit = manifold.friction * contact.normalImpulse
        val previous = contact.tangentImpulse
        contact.tangentImpulse = (previous - contact.tangentMass * tangentSpeed).coerceIn(-limit, limit)
        val delta = contact.tangentImpulse - previous
        applyImpulse(a, b, contact, delta * contact.tangentX, delta * contact.tangentY)
    }

    /** Applies an impulse at the contact point: pushes [b] along it and [a] the opposite way. */
    private fun applyImpulse(a: Body, b: Body, contact: Contact, impulseX: Float, impulseY: Float) {
        val armAX = contact.pointX - a.x
        val armAY = contact.pointY - a.y
        val armBX = contact.pointX - b.x
        val armBY = contact.pointY - b.y
        a.velocityX -= a.solverInverseMass * impulseX
        a.velocityY -= a.solverInverseMass * impulseY
        a.angularVelocity -= a.solverInverseInertia * cross(armAX, armAY, impulseX, impulseY)
        b.velocityX += b.solverInverseMass * impulseX
        b.velocityY += b.solverInverseMass * impulseY
        b.angularVelocity += b.solverInverseInertia * cross(armBX, armBY, impulseX, impulseY)
    }

    /** How hard it is to change the bodies' relative speed along a direction at this point (inverse of mass). */
    private fun effectiveMass(
        a: Body, b: Body, armAX: Float, armAY: Float, armBX: Float, armBY: Float, directionX: Float, directionY: Float,
    ): Float {
        val armAAlong = armAX * directionX + armAY * directionY
        val armBAlong = armBX * directionX + armBY * directionY
        val linear = a.solverInverseMass + b.solverInverseMass
        val angular = a.solverInverseInertia * (armAX * armAX + armAY * armAY - armAAlong * armAAlong) +
            b.solverInverseInertia * (armBX * armBX + armBY * armBY - armBAlong * armBAlong)
        return linear + angular
    }

    /** X of the velocity of B's contact point relative to A's, including spin. */
    private fun relativeVelocityX(a: Body, b: Body, armAY: Float, armBY: Float): Float =
        b.velocityX - b.angularVelocity * armBY - a.velocityX + a.angularVelocity * armAY

    /** Y of the velocity of B's contact point relative to A's, including spin. */
    private fun relativeVelocityY(a: Body, b: Body, armAX: Float, armBX: Float): Float =
        b.velocityY + b.angularVelocity * armBX - a.velocityY - a.angularVelocity * armAX

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float): Float = ax * by - ay * bx
}
