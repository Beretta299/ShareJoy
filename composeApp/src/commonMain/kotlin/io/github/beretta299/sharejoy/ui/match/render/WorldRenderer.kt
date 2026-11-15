package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import io.github.beretta299.sharejoy.presentation.match.MatchScene
import io.github.beretta299.sharejoy.presentation.match.camera.Camera
import io.github.beretta299.sharejoy.util.darken
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Draws one frame of the match: scenery, castles, shots and effects. */
fun DrawScope.drawWorld(scene: MatchScene, tex: Textures, cam: Camera = scene.camera, seamless: Boolean = false) {
    val scale = size.width / cam.viewW
    val shakePx = scene.shake * 0.22f * scale
    val view = WorldView(
        scale, cam.cx, cam.cy, size.width, size.height,
        sin(scene.time * 71f) * shakePx, cos(scene.time * 57f) * shakePx, seamless,
    )
    val game = scene.game

    drawSky(view, scene.time)
    drawGorge(view)
    drawGround(view)

    for (owner in 0..1) drawSlingshot(view, scene, owner, front = false)

    // Soft drop shadows first, so stacked blocks read as solid shapes.
    for (p in game.pieces) if (p.kind != PieceKind.PROJECTILE) drawShadow(view, p)
    for (p in game.pieces) {
        when (p.kind) {
            PieceKind.BLOCK -> drawBlock(view, p, tex, scene.time)
            PieceKind.KEEPER -> drawKeeper(view, p, game.keeperDown[p.owner])
            PieceKind.PROJECTILE -> drawProjectile(view, p.body.x, p.body.y, p.body.angle, p.body.radius, p.ammo, p.seed, scene.time, p.body.vx, p.body.vy)
        }
    }

    for (owner in 0..1) drawSlingshot(view, scene, owner, front = true)
    drawParticles(view, scene.particles)
    if (scene.aiming) drawTrajectory(view, scene)

    // Warm light from the sun side and a gentle vignette pull the scene together. Not when two screens share
    // one picture: a vignette per screen would darken the seam.
    if (!seamless) drawRect(Brush.linearGradient(listOf(Color(0x18FFD9A0), Color.Transparent), Offset(size.width, 0f), Offset(size.width * 0.3f, size.height)))
    if (!seamless) drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent, 1f to Color(0x59000000),
            center = Offset(size.width / 2f, size.height / 2f), radius = max(size.width, size.height) * 0.72f,
        ),
    )
    if (game.suddenDeath && game.phase != Phase.ROUND_OVER && game.phase != Phase.MATCH_OVER) {
        drawRect(Color(0x14FF3B1F))
    }
}
