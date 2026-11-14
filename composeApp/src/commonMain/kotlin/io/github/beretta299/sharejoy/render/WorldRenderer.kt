package io.github.beretta299.sharejoy.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.beretta299.sharejoy.GameSession
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Material
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.Piece
import io.github.beretta299.sharejoy.game.PieceKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

val PLAYER_COLORS = listOf(Color(0xFFB8322A), Color(0xFF2B5FB3))
val PLAYER_DARK = listOf(Color(0xFF7A1D18), Color(0xFF1B3F7A))

private const val TPM = Textures.TPM

/** Maps world metres (y up) to screen pixels (y down). */
private class View(val scale: Float, val cx: Float, val cy: Float, val w: Float, val h: Float, val ox: Float, val oy: Float) {
    fun x(wx: Float) = (wx - cx) * scale + w / 2f + ox
    fun y(wy: Float) = h / 2f - (wy - cy) * scale + oy
    val left get() = cx - w / 2f / scale
    val right get() = cx + w / 2f / scale
}

private fun hash(i: Int): Float {
    val v = sin(i * 127.1f + 311.7f) * 43758.547f
    return v - floor(v)
}

/** Draws one frame of the match: scenery, castles, shots and effects. */
fun DrawScope.drawWorld(session: GameSession, tex: Textures) {
    val cam = session.camera
    val scale = size.width / cam.viewW
    val shakePx = session.shake * 0.22f * scale
    val view = View(
        scale, cam.cx, cam.cy, size.width, size.height,
        sin(session.time * 71f) * shakePx, cos(session.time * 57f) * shakePx,
    )
    val game = session.game

    drawSky(view, session.time)
    drawGorge(view)
    drawGround(view)

    for (owner in 0..1) drawSlingshot(view, session, owner, front = false)

    // Soft drop shadows first, so stacked blocks read as solid shapes.
    for (p in game.pieces) if (p.kind != PieceKind.PROJECTILE) drawShadow(view, p)
    for (p in game.pieces) {
        when (p.kind) {
            PieceKind.BLOCK -> drawBlock(view, p, tex, session.time)
            PieceKind.KEEPER -> drawKeeper(view, p, game.keeperDown[p.owner])
            PieceKind.PROJECTILE -> drawProjectile(view, p.body.x, p.body.y, p.body.angle, p.body.radius, p.ammo, p.seed, session.time, p.body.vx, p.body.vy)
        }
    }

    for (owner in 0..1) drawSlingshot(view, session, owner, front = true)
    drawParticles(view, session.particles)
    if (session.aiming) drawTrajectory(view, session)

    // Warm light from the sun side and a gentle vignette pull the scene together.
    drawRect(Brush.linearGradient(listOf(Color(0x18FFD9A0), Color.Transparent), Offset(size.width, 0f), Offset(size.width * 0.3f, size.height)))
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent, 1f to Color(0x59000000),
            center = Offset(size.width / 2f, size.height / 2f), radius = max(size.width, size.height) * 0.72f,
        ),
    )
    if (game.suddenDeath && game.phase != Phase.ROUND_OVER && game.phase != Phase.MATCH_OVER) {
        drawRect(Color(0x14FF3B1F))
    }
}

// ---------------------------------------------------------------------- scenery

private fun DrawScope.drawSky(view: View, time: Float) {
    val horizon = view.y(0f)
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF2F5F9F), 0.5f to Color(0xFF86B9E2), 0.8f to Color(0xFFE9DDC0), 1f to Color(0xFFF4E3BC),
            startY = min(0f, horizon - size.height * 1.2f), endY = horizon,
        ),
    )
    // Sun with a wide glow.
    val sun = Offset(size.width * 0.74f - view.cx * view.scale * 0.03f, horizon - size.height * 0.46f)
    val unit = min(size.width, size.height)
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFF3C4), Color.Transparent), center = sun, radius = unit * 0.6f), unit * 0.6f, sun)
    drawCircle(Color(0xFFFFF6D8), unit * 0.06f, sun)

    // Clouds drift slowly; far layers move less than the camera.
    for (i in 0 until 9) {
        val wx = -130f + i * 31f + hash(i) * 14f + time * 0.35f
        val px = size.width / 2f + (wx - view.cx) * view.scale * 0.16f
        val py = horizon - size.height * (0.3f + hash(i + 40) * 0.36f)
        val s = view.scale * (0.7f + hash(i + 80) * 0.7f)
        for (k in 0 until 5) {
            val ox = (k - 2) * 1.5f * s
            val ry = s * (0.75f + hash(i * 7 + k) * 0.5f)
            drawOval(Color.White.copy(alpha = 0.5f), Offset(px + ox - s * 1.4f, py - ry + (k % 2) * s * 0.2f), Size(s * 2.8f, ry * 2f))
        }
        drawOval(Color(0x22203A60), Offset(px - s * 3.6f, py + s * 0.35f), Size(s * 7.2f, s * 0.7f))
    }

    drawRidge(view, horizon, parallax = 0.14f, step = 7f, base = 6.5f, amp = 9f, seed = 3, top = Color(0xFF8FA6C6), bottom = Color(0xFFB9C9DB))
    drawRidge(view, horizon, parallax = 0.26f, step = 5f, base = 3.2f, amp = 5f, seed = 57, top = Color(0xFF6F8FA8), bottom = Color(0xFF9DB6C2))
    drawRidge(view, horizon, parallax = 0.45f, step = 3.5f, base = 1.2f, amp = 2.6f, seed = 131, top = Color(0xFF55803F), bottom = Color(0xFF7FA35A))

    // A tree line on the nearest hills.
    for (i in -60..60) {
        val wx = i * 2.3f + hash(i + 900) * 1.4f
        val px = size.width / 2f + (wx - view.cx) * view.scale * 0.6f
        if (px < -80f || px > size.width + 80f) continue
        val r = view.scale * (0.55f + hash(i + 300) * 0.5f)
        val py = horizon - r * 0.5f
        drawCircle(Color(0xFF3F6B35), r, Offset(px, py))
        drawCircle(Color(0xFF4F7F40), r * 0.7f, Offset(px - r * 0.25f, py - r * 0.3f))
    }
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x55E9E2C8)), startY = horizon - size.height * 0.2f, endY = horizon),
        topLeft = Offset(0f, horizon - size.height * 0.2f), size = Size(size.width, size.height * 0.2f),
    )
}

private fun DrawScope.drawRidge(
    view: View, horizon: Float, parallax: Float, step: Float, base: Float, amp: Float, seed: Int, top: Color, bottom: Color,
) {
    val path = Path()
    val s = view.scale * parallax
    var first = true
    var i = -70
    var peak = horizon
    while (i <= 70) {
        val px = size.width / 2f + (i * step - view.cx) * s
        if (px > -step * s * 2f && px < size.width + step * s * 2f) {
            val hgt = base + amp * (0.35f * hash(i + seed) + 0.65f * (0.5f + 0.5f * sin(i * 0.9f + seed)))
            val py = horizon - hgt * view.scale * (0.5f + parallax)
            peak = min(peak, py)
            if (first) {
                path.moveTo(px, horizon)
                first = false
            }
            path.lineTo(px, py)
        }
        i++
    }
    if (first) return
    path.lineTo(size.width + 400f, horizon)
    path.close()
    drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = peak, endY = horizon))
}

private fun DrawScope.drawGorge(view: View) {
    val g = Balance.GORGE_HALF_WIDTH
    val left = view.x(-g)
    val right = view.x(g)
    val top = view.y(0f)
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF4A423B), Color(0xFF1A1512), Color(0xFF070606)), startY = top, endY = top + view.scale * 9f),
        topLeft = Offset(left, top), size = Size(right - left, max(0f, size.height - top) + view.scale),
    )
    // Mist rising from the river far below.
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x33AEC6D6)), startY = top + view.scale * 1.5f, endY = top + view.scale * 6f),
        topLeft = Offset(left, top + view.scale * 1.5f), size = Size(right - left, view.scale * 6f),
    )
    // A broken rope bridge: posts on both edges, ropes hanging into the gorge.
    for (s in listOf(-1f, 1f)) {
        val px = view.x(s * (g + 0.45f))
        drawLine(Color(0xFF3B2716), Offset(px, top), Offset(px, top - view.scale * 1.25f), view.scale * 0.2f, StrokeCap.Round)
        drawLine(Color(0xFF6B4A2B), Offset(px - view.scale * 0.03f, top), Offset(px - view.scale * 0.03f, top - view.scale * 1.22f), view.scale * 0.08f, StrokeCap.Round)
        val rope = Path()
        rope.moveTo(px, top - view.scale * 1.1f)
        rope.quadraticTo(px - s * view.scale * 0.9f, top + view.scale * 0.4f, px - s * view.scale * 1.15f, top + view.scale * 2.6f)
        drawPath(rope, Color(0xFF2A1C10), style = Stroke(view.scale * 0.05f, cap = StrokeCap.Round))
        for (k in 1..3) {
            val py = top + view.scale * (0.5f + k * 0.55f)
            val bx = px - s * view.scale * (0.85f + k * 0.08f)
            drawLine(Color(0xFF5A3D22), Offset(bx - view.scale * 0.3f, py), Offset(bx + view.scale * 0.3f, py + view.scale * 0.12f), view.scale * 0.09f)
        }
    }
}

private fun DrawScope.drawGround(view: View) {
    val top = view.y(0f)
    if (top > size.height + 40f) return
    val g = Balance.GORGE_HALF_WIDTH
    for (s in listOf(-1f, 1f)) {
        val x0 = if (s < 0) -200f else view.x(g)
        val x1 = if (s < 0) view.x(-g) else size.width + 200f
        if (x1 < 0f || x0 > size.width) continue
        val l = max(x0, -50f)
        val r = min(x1, size.width + 50f)
        // Soil.
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF7A5633), Color(0xFF4B331E), Color(0xFF2A1C10)), startY = top, endY = top + view.scale * 6f),
            topLeft = Offset(l, top), size = Size(r - l, size.height - top + view.scale),
        )
        // Rocky cliff face at the gorge edge.
        val edge = if (s < 0) x1 else x0
        val cliffW = view.scale * 0.9f
        val cl = if (s < 0) edge - cliffW else edge
        drawRect(
            Brush.horizontalGradient(
                if (s < 0) listOf(Color(0x004B331E), Color(0xFF6A6058), Color(0xFF3A342F)) else listOf(Color(0xFF3A342F), Color(0xFF6A6058), Color(0x004B331E)),
                startX = cl, endX = cl + cliffW,
            ),
            topLeft = Offset(cl, top), size = Size(cliffW, size.height - top + view.scale),
        )
    }

    // Strata, buried stones and grass, only where visible.
    val from = floor(view.left).toInt() - 1
    val to = view.right.toInt() + 1
    for (i in from..to) {
        val wx = i.toFloat()
        if (abs(wx + 0.5f) < g + 0.4f) continue
        for (k in 0 until 2) {
            val hx = hash(i * 13 + k * 101)
            val hy = hash(i * 29 + k * 53)
            val px = view.x(wx + hx)
            val py = top + view.scale * (0.6f + hy * 4.2f)
            val r = view.scale * (0.1f + hash(i * 7 + k) * 0.22f)
            drawOval(Color(0xFF2E2014).copy(alpha = 0.55f), Offset(px - r, py - r * 0.6f + r * 0.2f), Size(r * 2f, r * 1.3f))
            drawOval(Color(0xFF8C7F70).copy(alpha = 0.8f), Offset(px - r, py - r * 0.6f), Size(r * 2f, r * 1.2f))
        }
    }
    for (s in listOf(-1f, 1f)) {
        val x0 = if (s < 0) -200f else view.x(g)
        val x1 = if (s < 0) view.x(-g) else size.width + 200f
        val l = max(x0, -50f)
        val r = min(x1, size.width + 50f)
        if (r <= l) continue
        val th = view.scale * 0.34f
        drawRect(Color(0x66201408), topLeft = Offset(l, top + th * 0.8f), size = Size(r - l, th * 0.5f))
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF8CC152), Color(0xFF4F8A2C)), startY = top - th * 0.2f, endY = top + th),
            topLeft = Offset(l, top - th * 0.12f), size = Size(r - l, th * 1.1f),
        )
    }
    val blades = Path()
    var bx = floor(view.left * 3f).toInt() - 1
    val bEnd = (view.right * 3f).toInt() + 1
    while (bx <= bEnd) {
        val wx = bx / 3f + hash(bx) * 0.2f
        if (abs(wx) > g + 0.15f) {
            val px = view.x(wx)
            val hgt = view.scale * (0.14f + hash(bx + 500) * 0.2f)
            val lean = (hash(bx + 900) - 0.5f) * view.scale * 0.16f
            blades.moveTo(px - view.scale * 0.05f, top)
            blades.lineTo(px + lean, top - hgt)
            blades.lineTo(px + view.scale * 0.05f, top)
            blades.close()
        }
        bx++
    }
    drawPath(blades, Color(0xFF6FAE3E))
}

// ---------------------------------------------------------------------- slingshot

private fun DrawScope.drawSlingshot(view: View, session: GameSession, owner: Int, front: Boolean) {
    val game = session.game
    val x = game.slingshotX(owner)
    val s = view.scale
    val baseP = Offset(view.x(x), view.y(0f))
    val forkP = Offset(view.x(x), view.y(1.35f))
    // The two prongs sit one behind the other, seen slightly from the side.
    val backTip = Offset(view.x(x + 0.34f), view.y(Balance.SLINGSHOT_Y + 0.12f))
    val frontTip = Offset(view.x(x - 0.34f), view.y(Balance.SLINGSHOT_Y + 0.02f))

    val active = owner == game.current && game.phase == Phase.PLAN
    val pouch = if (active && session.aiming) {
        Offset(view.x(x + session.pullX), view.y(Balance.SLINGSHOT_Y + session.pullY))
    } else {
        Offset(view.x(x), view.y(Balance.SLINGSHOT_Y - 0.18f))
    }
    val dark = Color(0xFF3A2413)
    val mid = Color(0xFF7B5330)
    val light = Color(0xFFA9794A)
    val band = Color(0xFF2A1A12)

    if (!front) {
        drawLine(band, backTip, pouch, s * 0.08f, StrokeCap.Round)
        drawLine(dark, forkP, backTip, s * 0.3f, StrokeCap.Round)
        drawLine(mid.darken(0.08f), forkP, backTip, s * 0.19f, StrokeCap.Round)
        return
    }

    if (active) {
        // The loaded shot rests in the pouch.
        val ammo = game.activePlayer.selectedAmmo
        val wx = (pouch.x - view.w / 2f - view.ox) / s + view.cx
        val wy = view.cy - (pouch.y - view.h / 2f - view.oy) / s
        drawProjectile(view, wx, wy, 0f, ammo.radius, ammo, 7, session.time, 0f, 0f)
    }
    drawLine(dark, baseP, forkP, s * 0.36f, StrokeCap.Round)
    drawLine(mid, baseP, forkP, s * 0.24f, StrokeCap.Round)
    drawLine(light.copy(alpha = 0.6f), baseP + Offset(-s * 0.05f, 0f), forkP + Offset(-s * 0.05f, 0f), s * 0.06f, StrokeCap.Round)
    drawLine(dark, forkP, frontTip, s * 0.3f, StrokeCap.Round)
    drawLine(mid, forkP, frontTip, s * 0.19f, StrokeCap.Round)
    // Rope binding at the fork and the leather band to the pouch.
    for (k in 0..2) {
        val y = forkP.y + s * (0.05f + k * 0.09f)
        drawLine(Color(0xFFD9C29A), Offset(forkP.x - s * 0.17f, y), Offset(forkP.x + s * 0.17f, y - s * 0.04f), s * 0.05f, StrokeCap.Round)
    }
    drawLine(band, frontTip, pouch, s * 0.09f, StrokeCap.Round)
    drawCircle(Color(0xFF4A2F1C), s * 0.13f, pouch)

    // A ring on the ground marks whose turn it is.
    if (active && !session.aiming) {
        val pulse = 0.5f + 0.5f * sin(session.time * 4f)
        drawCircle(
            Color.White.copy(alpha = 0.25f + 0.3f * pulse), s * (0.75f + 0.12f * pulse),
            Offset(view.x(x), view.y(Balance.SLINGSHOT_Y - 0.18f)), style = Stroke(s * 0.06f),
        )
    }
}

// ---------------------------------------------------------------------- pieces

private fun DrawScope.drawShadow(view: View, p: Piece) {
    val b = p.body
    val px = view.x(b.x + 0.16f)
    val py = view.y(b.y - 0.16f)
    if (px < -200f || px > size.width + 200f) return
    val hw = b.halfW * view.scale
    val hh = b.halfH * view.scale
    withTransform({
        translate(px, py)
        rotate(-b.angle * 180f / PI.toFloat(), Offset.Zero)
    }) {
        drawRect(Color(0x47000000), topLeft = Offset(-hw, -hh), size = Size(hw * 2f, hh * 2f))
    }
}

private fun DrawScope.drawBlock(view: View, p: Piece, tex: Textures, time: Float) {
    val b = p.body
    val px = view.x(b.x)
    val py = view.y(b.y)
    val reach = (b.halfW + b.halfH + 2.5f) * view.scale
    if (px < -reach || px > size.width + reach || py < -reach || py > size.height + reach) return

    // Wood grain runs along the long side of a plank.
    val turned = p.material == Material.WOOD && b.halfH > b.halfW
    val wM = if (turned) b.halfH * 2f else b.halfW * 2f
    val hM = if (turned) b.halfW * 2f else b.halfH * 2f
    val w = max(4, (wM * TPM).roundToInt())
    val h = max(4, (hM * TPM).roundToInt())
    val image = when (p.material) {
        Material.WOOD -> tex.wood
        Material.STONE -> tex.stone
        Material.IRON -> tex.iron
    }
    val cell = if (p.material == Material.IRON) TPM else TPM / 2
    val maxX = max(0, Textures.SIZE - w)
    val maxY = max(0, Textures.SIZE - h)
    val srcX = min(((p.seed % 7) * TPM), maxX) / cell * cell
    val srcY = min((((p.seed / 7) % 13) * cell), maxY) / cell * cell
    val k = view.scale / TPM

    withTransform({
        translate(px, py)
        rotate(-b.angle * 180f / PI.toFloat() + if (turned) 90f else 0f, Offset.Zero)
        scale(k, k, Offset.Zero)
    }) {
        val left = -w / 2f
        val top = -h / 2f
        drawImage(
            image,
            srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(min(w, Textures.SIZE), min(h, Textures.SIZE)),
            dstOffset = IntOffset(-w / 2, -h / 2), dstSize = IntSize(w, h),
        )
        // Bevel: light from the upper left.
        drawLine(Color.White.copy(alpha = 0.30f), Offset(left + 1f, top + 1.6f), Offset(left + w - 1f, top + 1.6f), 2.4f)
        drawLine(Color.White.copy(alpha = 0.14f), Offset(left + 1.6f, top + 1f), Offset(left + 1.6f, top + h - 1f), 2.2f)
        drawLine(Color.Black.copy(alpha = 0.34f), Offset(left + 1f, top + h - 1.6f), Offset(left + w - 1f, top + h - 1.6f), 2.6f)
        drawLine(Color.Black.copy(alpha = 0.24f), Offset(left + w - 1.6f, top + 1f), Offset(left + w - 1.6f, top + h - 1f), 2.4f)
        drawRect(Color(0xFF16100B).copy(alpha = 0.75f), topLeft = Offset(left, top), size = Size(w.toFloat(), h.toFloat()), style = Stroke(1.8f))

        val stage = p.damageStage
        if (stage > 0) drawCracks(p.seed, w.toFloat(), h.toFloat(), stage)

        if (p.hasFlag) drawFlag(p.owner, if (turned) 0f else top, time)
    }
}

private fun DrawScope.drawCracks(seed: Int, w: Float, h: Float, stage: Int) {
    val rng = Rng(seed + 17)
    if (stage >= 2) drawRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(-w / 2f, -h / 2f), size = Size(w, h))
    val count = if (stage >= 2) 5 else 2
    val unit = min(w, h)
    repeat(count) {
        val path = Path()
        // Start on an edge and wander toward the middle.
        var x: Float
        var y: Float
        when (rng.int(4)) {
            0 -> { x = rng.range(-w / 2f, w / 2f); y = -h / 2f }
            1 -> { x = rng.range(-w / 2f, w / 2f); y = h / 2f }
            2 -> { x = -w / 2f; y = rng.range(-h / 2f, h / 2f) }
            else -> { x = w / 2f; y = rng.range(-h / 2f, h / 2f) }
        }
        path.moveTo(x, y)
        val tx = rng.range(-w * 0.2f, w * 0.2f)
        val ty = rng.range(-h * 0.2f, h * 0.2f)
        val segments = 3 + rng.int(3)
        for (sgm in 1..segments) {
            val t = sgm / segments.toFloat()
            val nx = x + (tx - x) * t * 0.7f + rng.range(-0.16f, 0.16f) * unit
            val ny = y + (ty - y) * t * 0.7f + rng.range(-0.16f, 0.16f) * unit
            x = nx.coerceIn(-w / 2f + 2f, w / 2f - 2f)
            y = ny.coerceIn(-h / 2f + 2f, h / 2f - 2f)
            path.lineTo(x, y)
            if (stage >= 2 && rng.next() < 0.4f) {
                // A short branch.
                path.lineTo(x + rng.range(-0.14f, 0.14f) * unit, y + rng.range(-0.14f, 0.14f) * unit)
                path.moveTo(x, y)
            }
        }
        drawPath(path, Color(0xFF0E0A07).copy(alpha = 0.88f), style = Stroke(if (stage >= 2) 3f else 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White.copy(alpha = 0.12f), style = Stroke(1f))
    }
    if (stage >= 2) {
        // Chipped corners.
        repeat(3) {
            val cx = if (rng.next() < 0.5f) -w / 2f else w / 2f
            val cy = if (rng.next() < 0.5f) -h / 2f else h / 2f
            val sx = if (cx < 0) 1f else -1f
            val sy = if (cy < 0) 1f else -1f
            val a = rng.range(0.1f, 0.24f) * unit
            val chip = Path()
            chip.moveTo(cx, cy)
            chip.lineTo(cx + sx * a, cy)
            chip.lineTo(cx, cy + sy * a * rng.range(0.6f, 1.2f))
            chip.close()
            drawPath(chip, Color(0xFF120D09).copy(alpha = 0.85f))
        }
    }
}

/** A pennant in the owner's colour. Drawn in block-local texels, so it topples with its tower. */
private fun DrawScope.drawFlag(owner: Int, top: Float, time: Float) {
    val poleH = 1.75f * TPM
    drawLine(Color(0xFF2A1A0E), Offset(0f, top), Offset(0f, top - poleH), 5f, StrokeCap.Round)
    drawLine(Color(0xFF8A6A45), Offset(-1f, top), Offset(-1f, top - poleH), 1.6f, StrokeCap.Round)
    drawCircle(Color(0xFFE2B93B), 4.6f, Offset(0f, top - poleH))
    val dir = if (owner == 0) 1f else -1f
    val len = 1.25f * TPM
    val hgt = 0.62f * TPM
    val y0 = top - poleH + 5f
    val flag = Path()
    val n = 8
    flag.moveTo(0f, y0)
    for (i in 1..n) {
        val t = i / n.toFloat()
        flag.lineTo(dir * len * t, y0 + hgt * 0.5f * t + sin(time * 5f - t * 5f) * 5f * t)
    }
    for (i in n downTo 0) {
        val t = i / n.toFloat()
        flag.lineTo(dir * len * t, y0 + hgt - hgt * 0.5f * t + sin(time * 5f - t * 5f) * 5f * t)
    }
    flag.close()
    drawPath(flag, Brush.horizontalGradient(listOf(PLAYER_COLORS[owner], PLAYER_DARK[owner]), startX = 0f, endX = dir * len))
    drawPath(flag, Color(0xAA1A0F0A), style = Stroke(1.6f, join = StrokeJoin.Round))
    drawCircle(Color(0xFFF2D675), hgt * 0.16f, Offset(dir * len * 0.3f, y0 + hgt * 0.5f))
}

private fun DrawScope.drawKeeper(view: View, p: Piece, down: Boolean) {
    val b = p.body
    val px = view.x(b.x)
    val py = view.y(b.y)
    if (px < -200f || px > size.width + 200f) return
    val k = view.scale / TPM
    val w = b.halfW * 2f * TPM
    val h = b.halfH * 2f * TPM
    val face = if (p.owner == 0) 1f else -1f
    val robe = PLAYER_COLORS[p.owner]
    val robeDark = PLAYER_DARK[p.owner]
    val outline = Color(0xFF1B120C)

    withTransform({
        translate(px, py)
        rotate(-b.angle * 180f / PI.toFloat(), Offset.Zero)
        scale(k * face, k, Offset.Zero)
    }) {
        val bottom = h / 2f
        val shoulder = -h * 0.14f
        // Robe.
        val body = Path()
        body.moveTo(-w / 2f + 2f, bottom)
        body.lineTo(w / 2f - 2f, bottom)
        body.lineTo(w / 2f - 9f, shoulder)
        body.quadraticTo(0f, shoulder - 9f, -w / 2f + 9f, shoulder)
        body.close()
        drawPath(body, Brush.horizontalGradient(listOf(robeDark, robe, robe.lighten(0.12f), robe), startX = -w / 2f, endX = w / 2f))
        drawLine(robeDark, Offset(-4f, shoulder + 6f), Offset(-7f, bottom - 9f), 2f)
        drawLine(robeDark, Offset(8f, shoulder + 8f), Offset(11f, bottom - 9f), 2f)
        // Ermine hem and gold belt.
        drawRect(Color(0xFFF4EFE4), topLeft = Offset(-w / 2f + 2f, bottom - 9f), size = Size(w - 4f, 9f))
        for (i in 0..4) drawCircle(outline, 1.5f, Offset(-w / 2f + 8f + i * (w - 16f) / 4f, bottom - 4.5f))
        drawRect(Color(0xFFE2B93B), topLeft = Offset(-w / 2f + 7f, h * 0.08f), size = Size(w - 14f, 5f))
        drawPath(body, outline, style = Stroke(2.2f, join = StrokeJoin.Round))
        // Head, beard, crown.
        val headC = Offset(1f, shoulder - 9f)
        val headR = 13.5f
        drawCircle(Color(0xFFF0C9A2), headR, headC)
        val beard = Path()
        beard.moveTo(headC.x - headR + 1f, headC.y + 1f)
        beard.quadraticTo(headC.x, headC.y + headR * 2.1f, headC.x + headR - 1f, headC.y + 1f)
        beard.quadraticTo(headC.x, headC.y + headR * 0.9f, headC.x - headR + 1f, headC.y + 1f)
        drawPath(beard, Color(0xFFE9E4DA))
        drawCircle(outline, headR, headC, style = Stroke(2f))
        if (down) {
            for (ex in listOf(headC.x + 1f, headC.x + 8f)) {
                drawLine(outline, Offset(ex - 2.2f, headC.y - 4.5f), Offset(ex + 2.2f, headC.y - 0.5f), 1.6f)
                drawLine(outline, Offset(ex - 2.2f, headC.y - 0.5f), Offset(ex + 2.2f, headC.y - 4.5f), 1.6f)
            }
        } else {
            drawCircle(outline, 1.8f, Offset(headC.x + 1.5f, headC.y - 2.5f))
            drawCircle(outline, 1.8f, Offset(headC.x + 8.5f, headC.y - 2.5f))
        }
        val crown = Path()
        val cy = headC.y - headR + 3f
        crown.moveTo(headC.x - 12f, cy)
        crown.lineTo(headC.x - 13f, cy - 13f)
        crown.lineTo(headC.x - 6f, cy - 6f)
        crown.lineTo(headC.x, cy - 15f)
        crown.lineTo(headC.x + 6f, cy - 6f)
        crown.lineTo(headC.x + 13f, cy - 13f)
        crown.lineTo(headC.x + 12f, cy)
        crown.close()
        drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFC9972B)), startY = cy - 15f, endY = cy))
        drawPath(crown, outline, style = Stroke(1.8f, join = StrokeJoin.Round))
        drawCircle(Color(0xFFD23B3B), 2.2f, Offset(headC.x, cy - 4.5f))
    }
}

private fun DrawScope.drawProjectile(
    view: View, wx: Float, wy: Float, angle: Float, radius: Float, ammo: Ammo, seed: Int, time: Float, vx: Float, vy: Float,
) {
    val c = Offset(view.x(wx), view.y(wy))
    if (c.x < -100f || c.x > size.width + 100f) return
    val r = radius * view.scale
    val speed = sqrt(vx * vx + vy * vy)
    if (speed > 6f) {
        // Motion streak.
        val tail = Offset(c.x - vx / speed * r * 3.2f, c.y + vy / speed * r * 3.2f)
        drawLine(Brush.linearGradient(listOf(Color(0x55FFFFFF), Color.Transparent), c, tail), c, tail, r * 1.5f, StrokeCap.Round)
    }
    drawCircle(Color(0x40000000), r, c + Offset(r * 0.18f, r * 0.2f))
    val lightC = c + Offset(-r * 0.35f, -r * 0.4f)
    when (ammo) {
        Ammo.STONE, Ammo.SPLIT -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFC9C3B6), Color(0xFF8B867B), Color(0xFF55514A)), center = lightC, radius = r * 1.7f), r, c)
            for (i in 0 until 5) {
                val a = angle + i * 1.3f + hash(seed + i) * 2f
                val d = r * (0.25f + hash(seed + i * 3) * 0.5f)
                drawCircle(Color(0x44201C18), r * (0.08f + hash(seed + i * 5) * 0.1f), c + Offset(cos(a) * d, -sin(a) * d))
            }
        }
        Ammo.IRON -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF9AA3AE), Color(0xFF3B4148), Color(0xFF16191D)), center = lightC, radius = r * 1.6f), r, c)
            drawCircle(Color(0x99FFFFFF), r * 0.16f, lightC)
            val a = angle
            drawLine(Color(0x55000000), c + Offset(cos(a) * r * 0.9f, -sin(a) * r * 0.9f), c - Offset(cos(a) * r * 0.9f, -sin(a) * r * 0.9f), r * 0.12f)
        }
        Ammo.KEG -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFA9703A), Color(0xFF6B4320), Color(0xFF3B230F)), center = lightC, radius = r * 1.7f), r, c)
            for (o in listOf(-0.45f, 0.45f)) {
                val nx = cos(angle + 1.5708f)
                val ny = -sin(angle + 1.5708f)
                val tx = cos(angle)
                val ty = -sin(angle)
                val m = c + Offset(nx * r * o, ny * r * o)
                val half = r * sqrt(1f - o * o)
                drawLine(Color(0xFF2B2B2E), m - Offset(tx * half, ty * half), m + Offset(tx * half, ty * half), r * 0.16f)
            }
            val spark = c + Offset(cos(angle + 1.2f) * r, -sin(angle + 1.2f) * r)
            drawCircle(Color(0xFFFFC14D), r * (0.22f + 0.1f * sin(time * 40f)), spark)
            drawCircle(Color(0xFFFFF4C2), r * 0.1f, spark)
        }
    }
    drawCircle(Color(0xCC14100C), r, c, style = Stroke(max(1.2f, r * 0.09f)))
}

// ---------------------------------------------------------------------- effects

private fun DrawScope.drawParticles(view: View, particles: Particles) {
    for (p in particles.items) {
        val c = Offset(view.x(p.x), view.y(p.y))
        if (c.x < -60f || c.x > size.width + 60f) continue
        val t = p.life / p.maxLife
        val s = p.size * view.scale
        when (p.kind) {
            ParticleKind.DEBRIS -> {
                val alpha = if (t > 0.7f) (1f - t) / 0.3f else 1f
                withTransform({
                    translate(c.x, c.y)
                    rotate(-p.rot * 57.3f, Offset.Zero)
                }) {
                    drawRect(p.color.copy(alpha = alpha), topLeft = Offset(-s * p.aspect / 2f, -s / 2f), size = Size(s * p.aspect, s))
                    drawRect(Color.Black.copy(alpha = 0.35f * alpha), topLeft = Offset(-s * p.aspect / 2f, -s / 2f), size = Size(s * p.aspect, s), style = Stroke(1f))
                }
            }
            ParticleKind.DUST -> drawCircle(
                Brush.radialGradient(listOf(p.color.copy(alpha = 0.42f * (1f - t)), p.color.copy(alpha = 0f)), center = c, radius = s),
                s, c,
            )
            ParticleKind.SPARK -> {
                val tail = Offset(c.x - p.vx * 0.03f * view.scale, c.y + p.vy * 0.03f * view.scale)
                drawLine(p.color.copy(alpha = 1f - t), c, tail, max(1.5f, s), StrokeCap.Round)
            }
            ParticleKind.FLASH -> drawCircle(
                Brush.radialGradient(listOf(p.color.copy(alpha = 0.95f * (1f - t)), Color(0xFFFF8A2B).copy(alpha = 0.6f * (1f - t)), Color.Transparent), center = c, radius = s),
                s, c,
            )
            ParticleKind.GLINT -> {
                val a = 1f - t
                drawLine(p.color.copy(alpha = a), c - Offset(s, 0f), c + Offset(s, 0f), max(1.2f, s * 0.3f), StrokeCap.Round)
                drawLine(p.color.copy(alpha = a), c - Offset(0f, s), c + Offset(0f, s), max(1.2f, s * 0.3f), StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawTrajectory(view: View, session: GameSession) {
    val game = session.game
    val x0 = session.anchorX + session.pullX
    val y0 = session.anchorY + session.pullY
    val vx = session.launchVx()
    val vy = session.launchVy()
    if (vx * vx + vy * vy < 4f) return
    val total = game.aimSeconds()
    var t = 0.07f
    var i = 0
    while (t < total) {
        val wx = x0 + vx * t
        val wy = y0 + vy * t + 0.5f * game.world.gravity * t * t
        if (wy < 0f && abs(wx) > Balance.GORGE_HALF_WIDTH) break
        val fade = 1f - t / total
        val c = Offset(view.x(wx), view.y(wy))
        val r = view.scale * (0.07f + 0.07f * fade)
        drawCircle(Color.Black.copy(alpha = 0.25f * fade + 0.1f), r * 1.35f, c)
        drawCircle(Color.White.copy(alpha = 0.55f * fade + 0.4f), r, c)
        t += 0.065f
        i++
    }
}

/** Converts a screen position back to world metres; used for touch input. */
fun screenToWorld(session: GameSession, width: Float, height: Float, sx: Float, sy: Float): Offset {
    val cam = session.camera
    val scale = width / cam.viewW
    return Offset((sx - width / 2f) / scale + cam.cx, cam.cy - (sy - height / 2f) / scale)
}

