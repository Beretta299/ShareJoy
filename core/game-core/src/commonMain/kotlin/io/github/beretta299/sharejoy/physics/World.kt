package io.github.beretta299.sharejoy.physics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A small deterministic 2D rigid-body world, in the spirit of Box2D-Lite.
 *
 * Each [step] finds touching pairs ([findContacts]), resolves them with the [ContactSolver], then moves the
 * bodies and puts still ones to sleep. Pure Kotlin with no platform dependencies, so the same simulation runs
 * on every target.
 */
class World(val gravity: Float = -9.81f, private val solverIterations: Int = 16) {
    val bodies = ArrayList<Body>()
    private val manifoldsByPair = HashMap<Long, ContactManifold>()
    internal val manifolds = ArrayList<ContactManifold>()
    private val collisionDetector = CollisionDetector()
    private var nextId = 1

    val allAsleep: Boolean get() = bodies.all { it.isStatic || it.isAsleep }

    fun newId(): Int = nextId++

    fun add(body: Body): Body {
        bodies.add(body)
        return body
    }

    /** Removes [body] and wakes everything it was touching, so nothing is left floating. */
    fun remove(body: Body) {
        bodies.remove(body)
        val iterator = manifolds.iterator()
        while (iterator.hasNext()) {
            val manifold = iterator.next()
            if (manifold.bodyA !== body && manifold.bodyB !== body) continue
            if (manifold.bodyA.isAsleep) manifold.bodyA.wake()
            if (manifold.bodyB.isAsleep) manifold.bodyB.wake()
            iterator.remove()
            manifoldsByPair.remove(pairKey(manifold.bodyA, manifold.bodyB))
        }
    }

    fun clear() {
        bodies.clear()
        manifoldsByPair.clear()
        manifolds.clear()
    }

    fun sleepAll() = bodies.forEach { it.sleep() }

    fun wakeAll() = bodies.forEach { if (it.isAsleep) it.wake() }

    fun step(dt: Float) {
        val inverseDt = if (dt > 0f) 1f / dt else 0f
        resetPerStepState()
        findContacts()
        wakeDisturbedBodies()
        applyGravityAndDamping(dt)

        manifolds.forEach { ContactSolver.prepare(it, inverseDt) }
        manifolds.forEach { ContactSolver.warmStart(it) }
        repeat(solverIterations) { manifolds.forEach { ContactSolver.solveVelocities(it) } }

        moveBodies(dt)
    }

    private fun resetPerStepState() {
        for (body in bodies) {
            body.strongestImpact = 0f
            body.isTouchingGround = false
            body.solverInverseMass = if (body.isAsleep) 0f else body.inverseMass
            body.solverInverseInertia = if (body.isAsleep) 0f else body.inverseInertia
        }
    }

    private fun applyGravityAndDamping(dt: Float) {
        for (body in bodies) {
            if (body.solverInverseMass == 0f) continue
            body.velocityY += gravity * dt
            // Mild damping keeps stacks calm and stops endless rolling.
            body.velocityX *= LINEAR_DAMPING
            body.velocityY *= LINEAR_DAMPING
            body.angularVelocity *= ANGULAR_DAMPING
        }
    }

    private fun moveBodies(dt: Float) {
        for (body in bodies) {
            if (body.solverInverseMass == 0f) continue
            body.x += body.velocityX * dt
            body.y += body.velocityY * dt
            body.angle += body.angularVelocity * dt
            updateSleep(body, dt)
        }
    }

    /** A body that stays nearly still for long enough falls asleep. */
    private fun updateSleep(body: Body, dt: Float) {
        val nearlyStill = body.speedSquared() < SLEEP_SPEED_SQUARED && abs(body.angularVelocity) < SLEEP_ANGULAR_SPEED
        if (!nearlyStill) {
            body.restingSeconds = 0f
            return
        }
        body.restingSeconds += dt
        if (body.restingSeconds > SLEEP_DELAY_SECONDS) body.sleep()
    }

    /** A sleeping body wakes when something that is really moving touches it. */
    private fun wakeDisturbedBodies() {
        for (manifold in manifolds) {
            val a = manifold.bodyA
            val b = manifold.bodyB
            if (a.isAsleep && b.solverInverseMass > 0f && isMovingNoticeably(b)) {
                a.wake()
            } else if (b.isAsleep && a.solverInverseMass > 0f && isMovingNoticeably(a)) {
                b.wake()
            }
        }
    }

    private fun isMovingNoticeably(body: Body): Boolean =
        body.speedSquared() > WAKE_SPEED_SQUARED || abs(body.angularVelocity) > WAKE_ANGULAR_SPEED

    // ------------------------------------------------------------------ broad phase

    /** Updates the contact manifold of every touching pair and drops pairs that no longer touch. */
    private fun findContacts() {
        manifolds.forEach { it.isTouching = false }
        for (i in bodies.indices) {
            for (j in i + 1 until bodies.size) {
                // Keep a stable order so feature ids stay comparable between steps.
                val first = if (bodies[i].id < bodies[j].id) bodies[i] else bodies[j]
                val second = if (first === bodies[i]) bodies[j] else bodies[i]
                updatePair(first, second)
            }
        }
        dropSeparatedPairs()
    }

    private fun updatePair(a: Body, b: Body) {
        val key = pairKey(a, b)
        if (a.solverInverseMass == 0f && b.solverInverseMass == 0f) {
            // Neither can move: keep a resting pair's manifold, and its warm-start impulses, for when one wakes.
            if (!a.isStatic || !b.isStatic) manifoldsByPair[key]?.isTouching = true
            return
        }
        if (!boundingBoxesOverlap(a, b)) return
        val count = collisionDetector.collide(a, b)
        if (count == 0) return

        val manifold = manifoldsByPair.getOrPut(key) { ContactManifold(a, b).also { manifolds.add(it) } }
        manifold.friction = sqrt(a.friction * b.friction)
        manifold.restitution = max(a.restitution, b.restitution)
        mergeContacts(manifold, count)
        manifold.isTouching = true
    }

    private fun boundingBoxesOverlap(a: Body, b: Body): Boolean =
        abs(a.x - b.x) <= a.boundingHalfWidth() + b.boundingHalfWidth() &&
            abs(a.y - b.y) <= a.boundingHalfHeight() + b.boundingHalfHeight()

    private fun dropSeparatedPairs() {
        for (i in manifolds.indices.reversed()) {
            val manifold = manifolds[i]
            if (manifold.isTouching) continue
            manifoldsByPair.remove(pairKey(manifold.bodyA, manifold.bodyB))
            manifolds.removeAt(i)
        }
    }

    /**
     * Replaces the manifold's contacts with the [newCount] just found, carrying over the accumulated impulses of
     * contacts that are the same point as last step. That carry-over (warm starting) is what keeps stacks stable.
     */
    private fun mergeContacts(manifold: ContactManifold, newCount: Int) {
        val found = collisionDetector.contacts
        for (i in 0 until newCount) {
            val previous = findPreviousContact(manifold, found[i])
            carriedNormalImpulse[i] = previous?.normalImpulse ?: 0f
            carriedTangentImpulse[i] = previous?.tangentImpulse ?: 0f
        }
        for (i in 0 until newCount) {
            val contact = manifold.contacts[i]
            contact.copyGeometryFrom(found[i])
            contact.normalImpulse = carriedNormalImpulse[i]
            contact.tangentImpulse = carriedTangentImpulse[i]
        }
        manifold.contactCount = newCount
    }

    private val carriedNormalImpulse = FloatArray(ContactManifold.MAX_CONTACTS)
    private val carriedTangentImpulse = FloatArray(ContactManifold.MAX_CONTACTS)

    /** The contact from last step that is the same point as [contact]: same feature id, or failing that, nearest. */
    private fun findPreviousContact(manifold: ContactManifold, contact: Contact): Contact? {
        for (i in 0 until manifold.contactCount) {
            val old = manifold.contacts[i]
            if (old.featureId == contact.featureId) return old
        }
        // Feature ids flicker when edges line up exactly; fall back to the nearest old point.
        var nearest: Contact? = null
        var nearestDistanceSquared = MATCH_DISTANCE * MATCH_DISTANCE
        for (i in 0 until manifold.contactCount) {
            val old = manifold.contacts[i]
            val dx = old.pointX - contact.pointX
            val dy = old.pointY - contact.pointY
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared
                nearest = old
            }
        }
        return nearest
    }

    private fun pairKey(a: Body, b: Body): Long = (a.id.toLong() shl 32) or b.id.toLong()

    private companion object {
        const val LINEAR_DAMPING = 0.999f
        const val ANGULAR_DAMPING = 0.995f
        const val MATCH_DISTANCE = 0.1f
        const val SLEEP_SPEED_SQUARED = 0.02f
        const val SLEEP_ANGULAR_SPEED = 0.2f
        const val SLEEP_DELAY_SECONDS = 0.5f
        const val WAKE_SPEED_SQUARED = 0.03f
        const val WAKE_ANGULAR_SPEED = 0.3f
    }
}
