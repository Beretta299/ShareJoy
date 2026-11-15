package io.github.beretta299.sharejoy.physics

/** One contact point between two bodies. Accumulated impulses persist between steps (warm starting). */
class Contact {
    var pointX = 0f
    var pointY = 0f

    /** Unit normal pointing from the manifold's first body to its second. */
    var normalX = 0f
    var normalY = 0f

    /** Negative when the bodies overlap. */
    var separation = 0f

    /** Identifies which edges or corners produced this point, so it can be matched across steps. */
    var featureId = 0

    var normalImpulse = 0f
    var tangentImpulse = 0f
    internal var normalMass = 0f
    internal var tangentMass = 0f

    /** Extra separating speed the solver aims for: pushes out of overlap and adds bounce. */
    internal var velocityBias = 0f

    /** The tangent is the normal turned a quarter clockwise. */
    val tangentX: Float get() = normalY
    val tangentY: Float get() = -normalX

    fun copyGeometryFrom(other: Contact) {
        pointX = other.pointX
        pointY = other.pointY
        normalX = other.normalX
        normalY = other.normalY
        separation = other.separation
        featureId = other.featureId
    }
}

/** The persistent set of contact points (at most two) between one pair of bodies. */
class ContactManifold(val bodyA: Body, val bodyB: Body) {
    val contacts = Array(MAX_CONTACTS) { Contact() }
    var contactCount = 0
    var friction = 0f
    var restitution = 0f

    /** Set when the pair was found touching this step; manifolds left unmarked are dropped. */
    internal var isTouching = false

    /** True when neither body can move this step, so there is nothing to solve. */
    internal val isFrozen: Boolean get() = bodyA.solverInverseMass == 0f && bodyB.solverInverseMass == 0f

    companion object {
        const val MAX_CONTACTS = 2
    }
}
