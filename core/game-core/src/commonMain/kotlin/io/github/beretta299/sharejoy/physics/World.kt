package io.github.beretta299.sharejoy.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One contact point between two bodies. Accumulated impulses persist between steps (warm starting). */
class Contact {
    var px = 0f
    var py = 0f
    var nx = 0f
    var ny = 0f
    var separation = 0f
    var feature = 0
    var pn = 0f
    var pt = 0f
    var massNormal = 0f
    var massTangent = 0f
    var bias = 0f
}

/** The persistent contact manifold for a pair of bodies. */
class Arbiter(val a: Body, val b: Body) {
    val contacts = Array(2) { Contact() }
    var count = 0
    var friction = 0f
    var restitution = 0f
    var touched = false
}

/**
 * A small deterministic 2D rigid-body world, in the spirit of Box2D-Lite:
 * SAT + clipping for boxes, sequential impulses with warm starting.
 *
 * Pure Kotlin with no platform dependencies, so the same simulation runs on every target.
 */
class World(val gravity: Float = -9.81f, private val iterations: Int = 16) {
    val bodies = ArrayList<Body>()
    private val arbiters = HashMap<Long, Arbiter>()
    internal val arbiterList = ArrayList<Arbiter>()
    private var nextId = 1

    // Scratch manifold reused by collision routines.
    private val scratch = Array(2) { Contact() }
    private val clipA = FloatArray(4)
    private val clipB = FloatArray(4)
    private val clipC = FloatArray(4)
    private val featA = IntArray(2)
    private val featB = IntArray(2)
    private val featC = IntArray(2)

    fun newId(): Int = nextId++

    fun add(body: Body): Body {
        bodies.add(body)
        return body
    }

    fun remove(body: Body) {
        bodies.remove(body)
        val it = arbiterList.iterator()
        while (it.hasNext()) {
            val arb = it.next()
            if (arb.a === body || arb.b === body) {
                if (arb.a.asleep) arb.a.wake()
                if (arb.b.asleep) arb.b.wake()
                it.remove()
                arbiters.remove(key(arb.a, arb.b))
            }
        }
    }

    fun clear() {
        bodies.clear()
        arbiters.clear()
        arbiterList.clear()
    }

    private fun key(a: Body, b: Body): Long = (a.id.toLong() shl 32) or b.id.toLong()

    fun step(dt: Float) {
        val invDt = if (dt > 0f) 1f / dt else 0f

        for (i in bodies.indices) {
            val b = bodies[i]
            b.impact = 0f
            b.touchingGround = false
            if (b.asleep) { b.im = 0f; b.ii = 0f } else { b.im = b.invMass; b.ii = b.invInertia }
        }

        broadPhase()
        wakeDisturbed()

        // Integrate forces.
        for (i in bodies.indices) {
            val b = bodies[i]
            if (b.im == 0f) continue
            b.vy += gravity * dt
            // Mild damping keeps stacks calm and stops endless rolling.
            b.vx *= 0.999f
            b.vy *= 0.999f
            b.omega *= 0.995f
        }

        for (i in arbiterList.indices) preStep(arbiterList[i], invDt)
        for (i in arbiterList.indices) warmStart(arbiterList[i])
        for (n in 0 until iterations) {
            for (i in arbiterList.indices) applyImpulse(arbiterList[i])
        }

        // Integrate velocities.
        for (i in bodies.indices) {
            val b = bodies[i]
            if (b.im == 0f) continue
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.angle += b.omega * dt
            if (b.speedSq() < SLEEP_SPEED_SQ && abs(b.omega) < SLEEP_OMEGA) {
                b.sleepTime += dt
                if (b.sleepTime > SLEEP_DELAY) b.sleep()
            } else {
                b.sleepTime = 0f
            }
        }
    }

    /** A sleeping body wakes when something that is really moving touches it. */
    private fun wakeDisturbed() {
        for (i in arbiterList.indices) {
            val arb = arbiterList[i]
            val a = arb.a
            val b = arb.b
            if (a.asleep && b.im > 0f && isMoving(b)) a.wake()
            else if (b.asleep && a.im > 0f && isMoving(a)) b.wake()
        }
    }

    private fun isMoving(b: Body): Boolean = b.speedSq() > WAKE_SPEED_SQ || abs(b.omega) > WAKE_OMEGA

    fun sleepAll() {
        for (i in bodies.indices) bodies[i].sleep()
    }

    fun wakeAll() {
        for (i in bodies.indices) if (bodies[i].asleep) bodies[i].wake()
    }

    val allAsleep: Boolean
        get() {
            for (i in bodies.indices) {
                val b = bodies[i]
                if (b.invMass > 0f && !b.asleep) return false
            }
            return true
        }

    private fun broadPhase() {
        for (i in arbiterList.indices) arbiterList[i].touched = false

        val n = bodies.size
        for (i in 0 until n) {
            val bi = bodies[i]
            val ihw = bi.aabbHalfW()
            val ihh = bi.aabbHalfH()
            for (j in i + 1 until n) {
                val bj = bodies[j]
                if (bi.im == 0f && bj.im == 0f) {
                    // Two resting bodies: keep their manifold (and its warm-start impulses) alive.
                    if (bi.invMass != 0f || bj.invMass != 0f) {
                        val k0 = if (bi.id < bj.id) key(bi, bj) else key(bj, bi)
                        arbiters[k0]?.touched = true
                    }
                    continue
                }
                if (abs(bi.x - bj.x) > ihw + bj.aabbHalfW()) continue
                if (abs(bi.y - bj.y) > ihh + bj.aabbHalfH()) continue

                // Keep a stable order so feature ids stay comparable between steps.
                val a = if (bi.id < bj.id) bi else bj
                val b = if (bi.id < bj.id) bj else bi
                val count = collide(a, b)
                if (count == 0) continue

                val k = key(a, b)
                var arb = arbiters[k]
                if (arb == null) {
                    arb = Arbiter(a, b)
                    arbiters[k] = arb
                    arbiterList.add(arb)
                }
                arb.friction = sqrt(a.friction * b.friction)
                arb.restitution = max(a.restitution, b.restitution)
                mergeContacts(arb, count)
                arb.touched = true
            }
        }

        // Drop manifolds that no longer touch.
        var i = arbiterList.size - 1
        while (i >= 0) {
            val arb = arbiterList[i]
            if (!arb.touched) {
                arbiters.remove(key(arb.a, arb.b))
                arbiterList.removeAt(i)
            }
            i--
        }
    }

    private fun mergeContacts(arb: Arbiter, newCount: Int) {
        // Carry accumulated impulses over for contacts with the same feature id.
        var pn0 = 0f; var pt0 = 0f; var pn1 = 0f; var pt1 = 0f
        for (i in 0 until newCount) {
            val c = scratch[i]
            var pn = 0f
            var pt = 0f
            var matched = false
            for (j in 0 until arb.count) {
                val old = arb.contacts[j]
                if (old.feature == c.feature) {
                    pn = old.pn
                    pt = old.pt
                    matched = true
                    break
                }
            }
            if (!matched) {
                // Feature ids flicker when edges line up exactly; fall back to the nearest old point.
                var best = MATCH_DIST_SQ
                for (j in 0 until arb.count) {
                    val old = arb.contacts[j]
                    val dx = old.px - c.px
                    val dy = old.py - c.py
                    val d2 = dx * dx + dy * dy
                    if (d2 < best) {
                        best = d2
                        pn = old.pn
                        pt = old.pt
                    }
                }
            }
            if (i == 0) { pn0 = pn; pt0 = pt } else { pn1 = pn; pt1 = pt }
        }
        for (i in 0 until newCount) {
            val src = scratch[i]
            val dst = arb.contacts[i]
            dst.px = src.px; dst.py = src.py
            dst.nx = src.nx; dst.ny = src.ny
            dst.separation = src.separation
            dst.feature = src.feature
            dst.pn = if (i == 0) pn0 else pn1
            dst.pt = if (i == 0) pt0 else pt1
        }
        arb.count = newCount
    }

    private fun preStep(arb: Arbiter, invDt: Float) {
        val a = arb.a
        val b = arb.b
        if ((a.isGround && a.isStatic) && !b.isStatic) b.touchingGround = true
        if ((b.isGround && b.isStatic) && !a.isStatic) a.touchingGround = true
        if (a.im == 0f && b.im == 0f) return

        for (i in 0 until arb.count) {
            val c = arb.contacts[i]
            val r1x = c.px - a.x; val r1y = c.py - a.y
            val r2x = c.px - b.x; val r2y = c.py - b.y

            val rn1 = r1x * c.nx + r1y * c.ny
            val rn2 = r2x * c.nx + r2y * c.ny
            var kNormal = a.im + b.im
            kNormal += a.ii * (r1x * r1x + r1y * r1y - rn1 * rn1) +
                b.ii * (r2x * r2x + r2y * r2y - rn2 * rn2)
            c.massNormal = 1f / kNormal

            val tx = c.ny; val ty = -c.nx
            val rt1 = r1x * tx + r1y * ty
            val rt2 = r2x * tx + r2y * ty
            var kTangent = a.im + b.im
            kTangent += a.ii * (r1x * r1x + r1y * r1y - rt1 * rt1) +
                b.ii * (r2x * r2x + r2y * r2y - rt2 * rt2)
            c.massTangent = 1f / kTangent

            c.bias = -BIAS_FACTOR * invDt * min(0f, c.separation + ALLOWED_PENETRATION)

            // Approach speed before solving: drives both restitution and impact damage.
            val dvx = b.vx - b.omega * r2y - a.vx + a.omega * r1y
            val dvy = b.vy + b.omega * r2x - a.vy - a.omega * r1x
            val vn = dvx * c.nx + dvy * c.ny
            if (vn < -IMPACT_SPEED) {
                val impulse = c.massNormal * -vn
                if (a.invMass > 0f) a.impact = max(a.impact, impulse * a.invMass * b.punch)
                if (b.invMass > 0f) b.impact = max(b.impact, impulse * b.invMass * a.punch)
                if (arb.restitution > 0f && vn < -RESTITUTION_SPEED) c.bias = max(c.bias, -arb.restitution * vn)
            }

        }
    }

    /** Re-applies last step's impulses. Runs after every arbiter has measured its approach speed. */
    private fun warmStart(arb: Arbiter) {
        val a = arb.a
        val b = arb.b
        if (a.im == 0f && b.im == 0f) return
        for (i in 0 until arb.count) {
            val c = arb.contacts[i]
            val r1x = c.px - a.x; val r1y = c.py - a.y
            val r2x = c.px - b.x; val r2y = c.py - b.y
            val tx = c.ny; val ty = -c.nx
            val px = c.pn * c.nx + c.pt * tx
            val py = c.pn * c.ny + c.pt * ty
            a.vx -= a.im * px
            a.vy -= a.im * py
            a.omega -= a.ii * (r1x * py - r1y * px)
            b.vx += b.im * px
            b.vy += b.im * py
            b.omega += b.ii * (r2x * py - r2y * px)
        }
    }

    private fun applyImpulse(arb: Arbiter) {
        val a = arb.a
        val b = arb.b
        if (a.im == 0f && b.im == 0f) return
        for (i in 0 until arb.count) {
            val c = arb.contacts[i]
            val r1x = c.px - a.x; val r1y = c.py - a.y
            val r2x = c.px - b.x; val r2y = c.py - b.y

            // Normal impulse.
            var dvx = b.vx - b.omega * r2y - a.vx + a.omega * r1y
            var dvy = b.vy + b.omega * r2x - a.vy - a.omega * r1x
            val vn = dvx * c.nx + dvy * c.ny
            var dPn = c.massNormal * (-vn + c.bias)
            val pn0 = c.pn
            c.pn = max(pn0 + dPn, 0f)
            dPn = c.pn - pn0

            var px = dPn * c.nx
            var py = dPn * c.ny
            a.vx -= a.im * px
            a.vy -= a.im * py
            a.omega -= a.ii * (r1x * py - r1y * px)
            b.vx += b.im * px
            b.vy += b.im * py
            b.omega += b.ii * (r2x * py - r2y * px)

            // Friction impulse.
            dvx = b.vx - b.omega * r2y - a.vx + a.omega * r1y
            dvy = b.vy + b.omega * r2x - a.vy - a.omega * r1x
            val tx = c.ny; val ty = -c.nx
            val vt = dvx * tx + dvy * ty
            var dPt = c.massTangent * -vt
            val maxPt = arb.friction * c.pn
            val pt0 = c.pt
            c.pt = max(-maxPt, min(pt0 + dPt, maxPt))
            dPt = c.pt - pt0

            px = dPt * tx
            py = dPt * ty
            a.vx -= a.im * px
            a.vy -= a.im * py
            a.omega -= a.ii * (r1x * py - r1y * px)
            b.vx += b.im * px
            b.vy += b.im * py
            b.omega += b.ii * (r2x * py - r2y * px)
        }
    }

    // ---------------------------------------------------------------- narrow phase

    /** Fills [scratch] with contacts; the normal points from [a] to [b]. Returns the contact count. */
    private fun collide(a: Body, b: Body): Int = when {
        a.isCircle && b.isCircle -> collideCircles(a, b)
        a.isCircle -> collideBoxCircle(b, a, flip = true)
        b.isCircle -> collideBoxCircle(a, b, flip = false)
        else -> collideBoxes(a, b)
    }

    private fun collideCircles(a: Body, b: Body): Int {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val r = a.radius + b.radius
        val d2 = dx * dx + dy * dy
        if (d2 >= r * r) return 0
        val d = sqrt(d2)
        val c = scratch[0]
        if (d > 1e-5f) { c.nx = dx / d; c.ny = dy / d } else { c.nx = 0f; c.ny = 1f }
        c.separation = d - r
        c.px = a.x + c.nx * a.radius
        c.py = a.y + c.ny * a.radius
        c.feature = 1
        return 1
    }

    private fun collideBoxCircle(box: Body, circle: Body, flip: Boolean): Int {
        val cs = cos(box.angle)
        val sn = sin(box.angle)
        val dx = circle.x - box.x
        val dy = circle.y - box.y
        // Circle centre in box space.
        val lx = cs * dx + sn * dy
        val ly = -sn * dx + cs * dy
        val qx = max(-box.halfW, min(lx, box.halfW))
        val qy = max(-box.halfH, min(ly, box.halfH))
        var nxl: Float
        var nyl: Float
        val sep: Float
        val inside = lx == qx && ly == qy
        var cxl = qx
        var cyl = qy
        if (inside) {
            // Centre is inside the box: push out through the nearest face.
            val penX = box.halfW - abs(lx)
            val penY = box.halfH - abs(ly)
            if (penX < penY) {
                nxl = if (lx > 0f) 1f else -1f; nyl = 0f
                cxl = nxl * box.halfW
                sep = -penX - circle.radius
            } else {
                nxl = 0f; nyl = if (ly > 0f) 1f else -1f
                cyl = nyl * box.halfH
                sep = -penY - circle.radius
            }
        } else {
            val ex = lx - qx
            val ey = ly - qy
            val d2 = ex * ex + ey * ey
            if (d2 >= circle.radius * circle.radius) return 0
            val d = sqrt(d2)
            nxl = ex / d; nyl = ey / d
            sep = d - circle.radius
        }
        val c = scratch[0]
        // Normal box -> circle, in world space.
        var nx = cs * nxl - sn * nyl
        var ny = sn * nxl + cs * nyl
        c.px = box.x + cs * cxl - sn * cyl
        c.py = box.y + sn * cxl + cs * cyl
        if (flip) { nx = -nx; ny = -ny }
        c.nx = nx
        c.ny = ny
        c.separation = sep
        c.feature = 1
        return 1
    }

    private fun collideBoxes(a: Body, b: Body): Int {
        val hAx = a.halfW; val hAy = a.halfH
        val hBx = b.halfW; val hBy = b.halfH

        val ca = cos(a.angle); val sa = sin(a.angle)
        val cb = cos(b.angle); val sb = sin(b.angle)
        // Rotation columns: col1 = (c, s), col2 = (-s, c).
        val dpx = b.x - a.x
        val dpy = b.y - a.y
        val dAx = ca * dpx + sa * dpy
        val dAy = -sa * dpx + ca * dpy
        val dBx = cb * dpx + sb * dpy
        val dBy = -sb * dpx + cb * dpy

        // C = RotA^T * RotB
        val c11 = ca * cb + sa * sb
        val c12 = -ca * sb + sa * cb
        val c21 = -sa * cb + ca * sb
        val c22 = sa * sb + ca * cb
        val ac11 = abs(c11); val ac12 = abs(c12); val ac21 = abs(c21); val ac22 = abs(c22)

        val faceAx = abs(dAx) - hAx - (ac11 * hBx + ac12 * hBy)
        val faceAy = abs(dAy) - hAy - (ac21 * hBx + ac22 * hBy)
        if (faceAx > 0f || faceAy > 0f) return 0
        val faceBx = abs(dBx) - (ac11 * hAx + ac21 * hAy) - hBx
        val faceBy = abs(dBy) - (ac12 * hAx + ac22 * hAy) - hBy
        if (faceBx > 0f || faceBy > 0f) return 0

        // Pick the axis of least penetration, with a bias to keep the choice stable.
        var axis = FACE_A_X
        var separation = faceAx
        var nx = if (dAx > 0f) ca else -ca
        var ny = if (dAx > 0f) sa else -sa

        if (faceAy > REL_TOL * separation + ABS_TOL * hAy) {
            axis = FACE_A_Y
            separation = faceAy
            nx = if (dAy > 0f) -sa else sa
            ny = if (dAy > 0f) ca else -ca
        }
        if (faceBx > REL_TOL * separation + ABS_TOL * hBx) {
            axis = FACE_B_X
            separation = faceBx
            nx = if (dBx > 0f) cb else -cb
            ny = if (dBx > 0f) sb else -sb
        }
        if (faceBy > REL_TOL * separation + ABS_TOL * hBy) {
            axis = FACE_B_Y
            separation = faceBy
            nx = if (dBy > 0f) -sb else sb
            ny = if (dBy > 0f) cb else -cb
        }

        val fnx: Float; val fny: Float
        val front: Float
        val snx: Float; val sny: Float
        val negSide: Float; val posSide: Float
        val negEdge: Int; val posEdge: Int

        when (axis) {
            FACE_A_X -> {
                fnx = nx; fny = ny
                front = a.x * fnx + a.y * fny + hAx
                snx = -sa; sny = ca
                val side = a.x * snx + a.y * sny
                negSide = -side + hAy; posSide = side + hAy
                negEdge = EDGE3; posEdge = EDGE1
                incidentEdge(hBx, hBy, b.x, b.y, cb, sb, fnx, fny)
            }
            FACE_A_Y -> {
                fnx = nx; fny = ny
                front = a.x * fnx + a.y * fny + hAy
                snx = ca; sny = sa
                val side = a.x * snx + a.y * sny
                negSide = -side + hAx; posSide = side + hAx
                negEdge = EDGE2; posEdge = EDGE4
                incidentEdge(hBx, hBy, b.x, b.y, cb, sb, fnx, fny)
            }
            FACE_B_X -> {
                fnx = -nx; fny = -ny
                front = b.x * fnx + b.y * fny + hBx
                snx = -sb; sny = cb
                val side = b.x * snx + b.y * sny
                negSide = -side + hBy; posSide = side + hBy
                negEdge = EDGE3; posEdge = EDGE1
                incidentEdge(hAx, hAy, a.x, a.y, ca, sa, fnx, fny)
            }
            else -> {
                fnx = -nx; fny = -ny
                front = b.x * fnx + b.y * fny + hBy
                snx = cb; sny = sb
                val side = b.x * snx + b.y * sny
                negSide = -side + hBx; posSide = side + hBx
                negEdge = EDGE2; posEdge = EDGE4
                incidentEdge(hAx, hAy, a.x, a.y, ca, sa, fnx, fny)
            }
        }

        var np = clip(clipB, featB, clipA, featA, -snx, -sny, negSide, negEdge)
        if (np < 2) return 0
        np = clip(clipC, featC, clipB, featB, snx, sny, posSide, posEdge)
        if (np < 2) return 0

        var count = 0
        for (i in 0 until 2) {
            val vx = clipC[i * 2]
            val vy = clipC[i * 2 + 1]
            val sep = fnx * vx + fny * vy - front
            if (sep <= 0f) {
                val c = scratch[count]
                c.separation = sep
                c.nx = nx
                c.ny = ny
                // Slide the point back onto the reference face.
                c.px = vx - sep * fnx
                c.py = vy - sep * fny
                var f = featC[i]
                if (axis == FACE_B_X || axis == FACE_B_Y) f = flipFeature(f)
                c.feature = f
                count++
            }
        }
        return count
    }

    /** Writes the incident edge (two world-space vertices + feature ids) into [clipA]/[featA]. */
    private fun incidentEdge(
        hx: Float, hy: Float, px: Float, py: Float, c: Float, s: Float, nx: Float, ny: Float,
    ) {
        // Reference normal in the incident box's frame, flipped.
        val lnx = -(c * nx + s * ny)
        val lny = -(-s * nx + c * ny)
        val v0x: Float; val v0y: Float; val v1x: Float; val v1y: Float
        if (abs(lnx) > abs(lny)) {
            if (lnx > 0f) {
                v0x = hx; v0y = -hy; featA[0] = feature(0, 0, EDGE3, EDGE4)
                v1x = hx; v1y = hy; featA[1] = feature(0, 0, EDGE4, EDGE1)
            } else {
                v0x = -hx; v0y = hy; featA[0] = feature(0, 0, EDGE1, EDGE2)
                v1x = -hx; v1y = -hy; featA[1] = feature(0, 0, EDGE2, EDGE3)
            }
        } else {
            if (lny > 0f) {
                v0x = hx; v0y = hy; featA[0] = feature(0, 0, EDGE4, EDGE1)
                v1x = -hx; v1y = hy; featA[1] = feature(0, 0, EDGE1, EDGE2)
            } else {
                v0x = -hx; v0y = -hy; featA[0] = feature(0, 0, EDGE2, EDGE3)
                v1x = hx; v1y = -hy; featA[1] = feature(0, 0, EDGE3, EDGE4)
            }
        }
        clipA[0] = px + c * v0x - s * v0y
        clipA[1] = py + s * v0x + c * v0y
        clipA[2] = px + c * v1x - s * v1y
        clipA[3] = py + s * v1x + c * v1y
    }

    private fun clip(
        out: FloatArray, outF: IntArray, inp: FloatArray, inF: IntArray,
        nx: Float, ny: Float, offset: Float, clipEdge: Int,
    ): Int {
        var n = 0
        val d0 = nx * inp[0] + ny * inp[1] - offset
        val d1 = nx * inp[2] + ny * inp[3] - offset
        if (d0 <= 0f) { out[n * 2] = inp[0]; out[n * 2 + 1] = inp[1]; outF[n] = inF[0]; n++ }
        if (d1 <= 0f) { out[n * 2] = inp[2]; out[n * 2 + 1] = inp[3]; outF[n] = inF[1]; n++ }
        if (d0 * d1 < 0f && n < 2) {
            val t = d0 / (d0 - d1)
            out[n * 2] = inp[0] + t * (inp[2] - inp[0])
            out[n * 2 + 1] = inp[1] + t * (inp[3] - inp[1])
            outF[n] = if (d0 > 0f) {
                // inEdge1 = clipEdge, inEdge2 = none; keep the out edges of vertex 0.
                (inF[0] and 0x00FF00FF) or (clipEdge shl 24)
            } else {
                // outEdge1 = clipEdge, outEdge2 = none; keep the in edges of vertex 1.
                (inF[1] and 0xFF00FF00.toInt()) or (clipEdge shl 16)
            }
            n++
        }
        return n
    }

    companion object {
        private const val ALLOWED_PENETRATION = 0.01f
        private const val BIAS_FACTOR = 0.2f
        private const val REL_TOL = 0.95f
        private const val ABS_TOL = 0.01f

        /** Approach speeds below this are resting contact, not impacts. */
        const val IMPACT_SPEED = 1.0f

        private const val RESTITUTION_SPEED = 2f
        private const val MATCH_DIST_SQ = 0.1f * 0.1f
        private const val SLEEP_SPEED_SQ = 0.02f
        private const val SLEEP_OMEGA = 0.2f
        private const val SLEEP_DELAY = 0.5f
        private const val WAKE_SPEED_SQ = 0.03f
        private const val WAKE_OMEGA = 0.3f

        private const val FACE_A_X = 0
        private const val FACE_A_Y = 1
        private const val FACE_B_X = 2
        private const val FACE_B_Y = 3

        private const val EDGE1 = 1
        private const val EDGE2 = 2
        private const val EDGE3 = 3
        private const val EDGE4 = 4

        // Feature id layout: inEdge1 | outEdge1 | inEdge2 | outEdge2, one byte each.
        private fun feature(in1: Int, out1: Int, in2: Int, out2: Int): Int =
            (in1 shl 24) or (out1 shl 16) or (in2 shl 8) or out2

        private fun flipFeature(f: Int): Int {
            val in1 = (f ushr 24) and 0xFF
            val out1 = (f ushr 16) and 0xFF
            val in2 = (f ushr 8) and 0xFF
            val out2 = f and 0xFF
            return feature(in2, out2, in1, out1)
        }
    }
}
