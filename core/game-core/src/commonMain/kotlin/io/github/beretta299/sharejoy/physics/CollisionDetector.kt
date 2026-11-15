package io.github.beretta299.sharejoy.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Narrow phase: finds where two bodies touch.
 *
 * Boxes use the separating axis test followed by clipping the incident edge against the reference face,
 * as in Box2D-Lite. Results go into the reused [contacts] array, so nothing is allocated per step.
 */
internal class CollisionDetector {
    /** Contacts found by the last [collide] call. Only the first N (its return value) are valid. */
    val contacts = Array(ContactManifold.MAX_CONTACTS) { Contact() }

    private val reference = ReferenceFace()
    private val incidentEdge = EdgeSegment()
    private val clippedOnce = EdgeSegment()
    private val clippedTwice = EdgeSegment()

    /** Fills [contacts] with the points where [a] and [b] touch; the normal points from [a] to [b]. Returns how many. */
    fun collide(a: Body, b: Body): Int = when {
        a.isCircle && b.isCircle -> collideCircles(a, b)
        a.isCircle -> collideBoxWithCircle(box = b, circle = a, normalFromCircle = true)
        b.isCircle -> collideBoxWithCircle(box = a, circle = b, normalFromCircle = false)
        else -> collideBoxes(a, b)
    }

    // ------------------------------------------------------------------ circles

    private fun collideCircles(a: Body, b: Body): Int {
        val offsetX = b.x - a.x
        val offsetY = b.y - a.y
        val touchDistance = a.radius + b.radius
        val distanceSquared = offsetX * offsetX + offsetY * offsetY
        if (distanceSquared >= touchDistance * touchDistance) return 0

        val distance = sqrt(distanceSquared)
        val contact = contacts[0]
        if (distance > MIN_DISTANCE) {
            contact.normalX = offsetX / distance
            contact.normalY = offsetY / distance
        } else {
            // Centres coincide: any direction works, up is as good as any.
            contact.normalX = 0f
            contact.normalY = 1f
        }
        contact.separation = distance - touchDistance
        contact.pointX = a.x + contact.normalX * a.radius
        contact.pointY = a.y + contact.normalY * a.radius
        contact.featureId = SINGLE_POINT_FEATURE
        return 1
    }

    private fun collideBoxWithCircle(box: Body, circle: Body, normalFromCircle: Boolean): Int {
        val cosAngle = cos(box.angle)
        val sinAngle = sin(box.angle)
        // Circle centre in the box's local frame.
        val offsetX = circle.x - box.x
        val offsetY = circle.y - box.y
        val localX = cosAngle * offsetX + sinAngle * offsetY
        val localY = -sinAngle * offsetX + cosAngle * offsetY
        // Closest point of the box to the circle centre.
        val closestX = localX.coerceIn(-box.halfWidth, box.halfWidth)
        val closestY = localY.coerceIn(-box.halfHeight, box.halfHeight)

        val localNormalX: Float
        val localNormalY: Float
        val separation: Float
        var contactLocalX = closestX
        var contactLocalY = closestY
        val centreInsideBox = localX == closestX && localY == closestY
        if (centreInsideBox) {
            // Push out through the nearest face.
            val depthX = box.halfWidth - abs(localX)
            val depthY = box.halfHeight - abs(localY)
            if (depthX < depthY) {
                localNormalX = if (localX > 0f) 1f else -1f
                localNormalY = 0f
                contactLocalX = localNormalX * box.halfWidth
                separation = -depthX - circle.radius
            } else {
                localNormalX = 0f
                localNormalY = if (localY > 0f) 1f else -1f
                contactLocalY = localNormalY * box.halfHeight
                separation = -depthY - circle.radius
            }
        } else {
            val gapX = localX - closestX
            val gapY = localY - closestY
            val gapSquared = gapX * gapX + gapY * gapY
            if (gapSquared >= circle.radius * circle.radius) return 0
            val gap = sqrt(gapSquared)
            localNormalX = gapX / gap
            localNormalY = gapY / gap
            separation = gap - circle.radius
        }

        val contact = contacts[0]
        val direction = if (normalFromCircle) -1f else 1f
        contact.normalX = direction * (cosAngle * localNormalX - sinAngle * localNormalY)
        contact.normalY = direction * (sinAngle * localNormalX + cosAngle * localNormalY)
        contact.pointX = box.x + cosAngle * contactLocalX - sinAngle * contactLocalY
        contact.pointY = box.y + sinAngle * contactLocalX + cosAngle * contactLocalY
        contact.separation = separation
        contact.featureId = SINGLE_POINT_FEATURE
        return 1
    }

    // ------------------------------------------------------------------ boxes

    private fun collideBoxes(a: Body, b: Body): Int {
        val cosA = cos(a.angle)
        val sinA = sin(a.angle)
        val cosB = cos(b.angle)
        val sinB = sin(b.angle)

        // Offset from A to B, seen from each box's own frame.
        val offsetX = b.x - a.x
        val offsetY = b.y - a.y
        val offsetInAX = cosA * offsetX + sinA * offsetY
        val offsetInAY = -sinA * offsetX + cosA * offsetY
        val offsetInBX = cosB * offsetX + sinB * offsetY
        val offsetInBY = -sinB * offsetX + cosB * offsetY

        // B's axes expressed in A's frame (the relative rotation), and its absolute values.
        val relativeXX = abs(cosA * cosB + sinA * sinB)
        val relativeXY = abs(-cosA * sinB + sinA * cosB)
        val relativeYX = abs(-sinA * cosB + cosA * sinB)
        val relativeYY = abs(sinA * sinB + cosA * cosB)

        // Separating axis test: gap along each of the four face normals. Positive means apart.
        val gapAlongAX = abs(offsetInAX) - a.halfWidth - (relativeXX * b.halfWidth + relativeXY * b.halfHeight)
        val gapAlongAY = abs(offsetInAY) - a.halfHeight - (relativeYX * b.halfWidth + relativeYY * b.halfHeight)
        if (gapAlongAX > 0f || gapAlongAY > 0f) return 0
        val gapAlongBX = abs(offsetInBX) - (relativeXX * a.halfWidth + relativeYX * a.halfHeight) - b.halfWidth
        val gapAlongBY = abs(offsetInBY) - (relativeXY * a.halfWidth + relativeYY * a.halfHeight) - b.halfHeight
        if (gapAlongBX > 0f || gapAlongBY > 0f) return 0

        // Pick the axis of least penetration. Later axes must win clearly, so the choice does not flicker.
        var axis = Axis.A_X
        var separation = gapAlongAX
        var normalX = if (offsetInAX > 0f) cosA else -cosA
        var normalY = if (offsetInAX > 0f) sinA else -sinA
        if (clearlyBetter(gapAlongAY, separation, a.halfHeight)) {
            axis = Axis.A_Y
            separation = gapAlongAY
            normalX = if (offsetInAY > 0f) -sinA else sinA
            normalY = if (offsetInAY > 0f) cosA else -cosA
        }
        if (clearlyBetter(gapAlongBX, separation, b.halfWidth)) {
            axis = Axis.B_X
            separation = gapAlongBX
            normalX = if (offsetInBX > 0f) cosB else -cosB
            normalY = if (offsetInBX > 0f) sinB else -sinB
        }
        if (clearlyBetter(gapAlongBY, separation, b.halfHeight)) {
            axis = Axis.B_Y
            normalX = if (offsetInBY > 0f) -sinB else sinB
            normalY = if (offsetInBY > 0f) cosB else -cosB
        }

        // The box that owns the chosen axis gives the reference face; the other box gives the incident edge.
        when (axis) {
            Axis.A_X -> setReferenceFace(a, cosA, sinA, normalX, normalY, faceOnXAxis = true)
            Axis.A_Y -> setReferenceFace(a, cosA, sinA, normalX, normalY, faceOnXAxis = false)
            Axis.B_X -> setReferenceFace(b, cosB, sinB, -normalX, -normalY, faceOnXAxis = true)
            Axis.B_Y -> setReferenceFace(b, cosB, sinB, -normalX, -normalY, faceOnXAxis = false)
        }
        val referenceIsA = axis == Axis.A_X || axis == Axis.A_Y
        if (referenceIsA) findIncidentEdge(b, cosB, sinB) else findIncidentEdge(a, cosA, sinA)

        // Trim the incident edge to the width of the reference face.
        val r = reference
        if (clipSegment(incidentEdge, clippedOnce, -r.sideNormalX, -r.sideNormalY, r.negativeSideOffset, r.negativeSideEdge) < 2) return 0
        if (clipSegment(clippedOnce, clippedTwice, r.sideNormalX, r.sideNormalY, r.positiveSideOffset, r.positiveSideEdge) < 2) return 0

        // Keep the clipped points that are behind the reference face.
        var count = 0
        for (i in 0 until 2) {
            val pointX = clippedTwice.x(i)
            val pointY = clippedTwice.y(i)
            val pointSeparation = r.normalX * pointX + r.normalY * pointY - r.offset
            if (pointSeparation > 0f) continue
            val contact = contacts[count]
            contact.separation = pointSeparation
            contact.normalX = normalX
            contact.normalY = normalY
            // Slide the point back onto the reference face.
            contact.pointX = pointX - pointSeparation * r.normalX
            contact.pointY = pointY - pointSeparation * r.normalY
            val featureId = clippedTwice.featureIds[i]
            contact.featureId = if (referenceIsA) featureId else FeatureId.swapped(featureId)
            count++
        }
        return count
    }

    private fun clearlyBetter(candidate: Float, current: Float, halfExtent: Float): Boolean =
        candidate > AXIS_PREFERENCE_RELATIVE * current + AXIS_PREFERENCE_ABSOLUTE * halfExtent

    /**
     * Describes the face of [box] whose outward normal is ([normalX], [normalY]). [faceOnXAxis] is true when that
     * face is perpendicular to the box's own x axis (a left or right face).
     */
    private fun setReferenceFace(
        box: Body, cosAngle: Float, sinAngle: Float, normalX: Float, normalY: Float, faceOnXAxis: Boolean,
    ) {
        val r = reference
        r.normalX = normalX
        r.normalY = normalY
        val halfDepth = if (faceOnXAxis) box.halfWidth else box.halfHeight
        val halfSpan = if (faceOnXAxis) box.halfHeight else box.halfWidth
        r.offset = box.x * normalX + box.y * normalY + halfDepth
        if (faceOnXAxis) {
            r.sideNormalX = -sinAngle
            r.sideNormalY = cosAngle
            r.negativeSideEdge = EDGE_3
            r.positiveSideEdge = EDGE_1
        } else {
            r.sideNormalX = cosAngle
            r.sideNormalY = sinAngle
            r.negativeSideEdge = EDGE_2
            r.positiveSideEdge = EDGE_4
        }
        val sideOffset = box.x * r.sideNormalX + box.y * r.sideNormalY
        r.negativeSideOffset = -sideOffset + halfSpan
        r.positiveSideOffset = sideOffset + halfSpan
    }

    /** Finds the edge of [box] that faces the reference face most directly and writes it to [incidentEdge]. */
    private fun findIncidentEdge(box: Body, cosAngle: Float, sinAngle: Float) {
        // Reference normal in the incident box's frame, reversed.
        val localNormalX = -(cosAngle * reference.normalX + sinAngle * reference.normalY)
        val localNormalY = -(-sinAngle * reference.normalX + cosAngle * reference.normalY)
        val halfWidth = box.halfWidth
        val halfHeight = box.halfHeight
        val ids = incidentEdge.featureIds
        // The edge's two corners in the box's local frame.
        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float
        if (abs(localNormalX) > abs(localNormalY)) {
            if (localNormalX > 0f) {
                startX = halfWidth; startY = -halfHeight; ids[0] = FeatureId.of(inEdge2 = EDGE_3, outEdge2 = EDGE_4)
                endX = halfWidth; endY = halfHeight; ids[1] = FeatureId.of(inEdge2 = EDGE_4, outEdge2 = EDGE_1)
            } else {
                startX = -halfWidth; startY = halfHeight; ids[0] = FeatureId.of(inEdge2 = EDGE_1, outEdge2 = EDGE_2)
                endX = -halfWidth; endY = -halfHeight; ids[1] = FeatureId.of(inEdge2 = EDGE_2, outEdge2 = EDGE_3)
            }
        } else {
            if (localNormalY > 0f) {
                startX = halfWidth; startY = halfHeight; ids[0] = FeatureId.of(inEdge2 = EDGE_4, outEdge2 = EDGE_1)
                endX = -halfWidth; endY = halfHeight; ids[1] = FeatureId.of(inEdge2 = EDGE_1, outEdge2 = EDGE_2)
            } else {
                startX = -halfWidth; startY = -halfHeight; ids[0] = FeatureId.of(inEdge2 = EDGE_2, outEdge2 = EDGE_3)
                endX = halfWidth; endY = -halfHeight; ids[1] = FeatureId.of(inEdge2 = EDGE_3, outEdge2 = EDGE_4)
            }
        }
        incidentEdge.set(0, box.x + cosAngle * startX - sinAngle * startY, box.y + sinAngle * startX + cosAngle * startY)
        incidentEdge.set(1, box.x + cosAngle * endX - sinAngle * endY, box.y + sinAngle * endX + cosAngle * endY)
    }

    /**
     * Keeps the part of [input] on the inner side of the plane (normal · p <= offset) and writes it to [output].
     * A point created by the cut is tagged with [clipEdge] so it can be recognised next step. Returns the point count.
     */
    private fun clipSegment(
        input: EdgeSegment, output: EdgeSegment, planeNormalX: Float, planeNormalY: Float, planeOffset: Float, clipEdge: Int,
    ): Int {
        var count = 0
        val distanceStart = planeNormalX * input.x(0) + planeNormalY * input.y(0) - planeOffset
        val distanceEnd = planeNormalX * input.x(1) + planeNormalY * input.y(1) - planeOffset
        if (distanceStart <= 0f) {
            output.copyPoint(count++, input, 0)
        }
        if (distanceEnd <= 0f) {
            output.copyPoint(count++, input, 1)
        }
        val crossesPlane = distanceStart * distanceEnd < 0f
        if (crossesPlane && count < 2) {
            val t = distanceStart / (distanceStart - distanceEnd)
            output.set(
                count,
                input.x(0) + t * (input.x(1) - input.x(0)),
                input.y(0) + t * (input.y(1) - input.y(0)),
            )
            output.featureIds[count] = if (distanceStart > 0f) {
                FeatureId.withInEdge1(input.featureIds[0], clipEdge)
            } else {
                FeatureId.withOutEdge1(input.featureIds[1], clipEdge)
            }
            count++
        }
        return count
    }

    private enum class Axis { A_X, A_Y, B_X, B_Y }

    /** The face that incident points are clipped against, plus its two side planes. */
    private class ReferenceFace {
        var normalX = 0f
        var normalY = 0f
        var offset = 0f
        var sideNormalX = 0f
        var sideNormalY = 0f
        var negativeSideOffset = 0f
        var positiveSideOffset = 0f
        var negativeSideEdge = 0
        var positiveSideEdge = 0
    }

    /** Two points and their feature ids, in world space. */
    private class EdgeSegment {
        private val coordinates = FloatArray(4)
        val featureIds = IntArray(2)

        fun x(index: Int) = coordinates[index * 2]
        fun y(index: Int) = coordinates[index * 2 + 1]

        fun set(index: Int, x: Float, y: Float) {
            coordinates[index * 2] = x
            coordinates[index * 2 + 1] = y
        }

        fun copyPoint(index: Int, from: EdgeSegment, fromIndex: Int) {
            set(index, from.x(fromIndex), from.y(fromIndex))
            featureIds[index] = from.featureIds[fromIndex]
        }
    }

    /**
     * A contact point's identity: which edges enter and leave it on each box, one byte each
     * (inEdge1 | outEdge1 | inEdge2 | outEdge2). The same id next step means the same point.
     */
    private object FeatureId {
        fun of(inEdge1: Int = 0, outEdge1: Int = 0, inEdge2: Int = 0, outEdge2: Int = 0): Int =
            (inEdge1 shl 24) or (outEdge1 shl 16) or (inEdge2 shl 8) or outEdge2

        /** The same id seen from the other box. */
        fun swapped(id: Int): Int = of(
            inEdge1 = (id ushr 8) and 0xFF,
            outEdge1 = id and 0xFF,
            inEdge2 = (id ushr 24) and 0xFF,
            outEdge2 = (id ushr 16) and 0xFF,
        )

        /** Replaces inEdge1 with [edge] and clears inEdge2, keeping the out edges. */
        fun withInEdge1(id: Int, edge: Int): Int = (id and 0x00FF00FF) or (edge shl 24)

        /** Replaces outEdge1 with [edge] and clears outEdge2, keeping the in edges. */
        fun withOutEdge1(id: Int, edge: Int): Int = (id and 0xFF00FF00.toInt()) or (edge shl 16)
    }

    private companion object {
        const val MIN_DISTANCE = 1e-5f
        const val SINGLE_POINT_FEATURE = 1
        const val AXIS_PREFERENCE_RELATIVE = 0.95f
        const val AXIS_PREFERENCE_ABSOLUTE = 0.01f

        // Box edges, numbered counter-clockwise starting from the right face's top.
        const val EDGE_1 = 1
        const val EDGE_2 = 2
        const val EDGE_3 = 3
        const val EDGE_4 = 4
    }
}
