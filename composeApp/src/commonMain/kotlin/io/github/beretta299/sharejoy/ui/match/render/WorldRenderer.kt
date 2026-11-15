package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.PieceKind
import io.github.beretta299.sharejoy.presentation.match.MatchScene
import io.github.beretta299.sharejoy.presentation.match.camera.Camera
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Screen shake at full strength moves the picture this far, in world metres. */
private const val MAX_SHAKE_METRES = 0.22f

/** How fast the shake wobbles on each axis. Different values keep it from looking like a straight line. */
private const val SHAKE_WOBBLE_X = 71f
private const val SHAKE_WOBBLE_Y = 57f

private val SUN_GLOW = Color(0x18FFD9A0)
private val VIGNETTE = Color(0x59000000)
private val SUDDEN_DEATH_TINT = Color(0x14FF3B1F)

/**
 * Draws one frame of the match: scenery, castles, shots and effects. [seamless] is true when this screen is one
 * of two paired phones sharing a picture.
 */
fun DrawScope.drawWorld(scene: MatchScene, textures: Textures, camera: Camera = scene.camera, seamless: Boolean = false) {
    val shakePixels = scene.shake * MAX_SHAKE_METRES * size.width / camera.viewWidth
    val view = WorldViewport(
        camera, size.width, size.height,
        shakeX = sin(scene.time * SHAKE_WOBBLE_X) * shakePixels,
        shakeY = cos(scene.time * SHAKE_WOBBLE_Y) * shakePixels,
        seamless = seamless,
    )
    val game = scene.game

    drawSky(view, scene.time)
    drawGorge(view)
    drawGround(view)
    for (player in 0..1) drawSlingshot(view, scene, player, front = false)

    // Soft drop shadows first, so stacked blocks read as solid shapes.
    for (piece in game.pieces) if (piece.kind != PieceKind.PROJECTILE) drawShadow(view, piece)
    for (piece in game.pieces) {
        when (piece.kind) {
            PieceKind.BLOCK -> drawBlock(view, piece, textures, scene.time)
            PieceKind.KEEPER -> drawKeeper(view, piece, game.keeperDown[piece.owner])
            PieceKind.PROJECTILE -> drawProjectile(view, piece, scene.time)
        }
    }

    for (player in 0..1) drawSlingshot(view, scene, player, front = true)
    drawParticles(view, scene.particles)
    if (scene.aiming) drawTrajectory(view, scene)

    // A vignette per screen would darken the seam between two paired phones, so only a single screen gets one.
    if (!seamless) drawLighting()
    val roundInPlay = game.phase != Phase.ROUND_OVER && game.phase != Phase.MATCH_OVER
    if (game.suddenDeath && roundInPlay) drawRect(SUDDEN_DEATH_TINT)
}

/** Warm light from the sun side and a gentle vignette pull the scene together. */
private fun DrawScope.drawLighting() {
    drawRect(Brush.linearGradient(listOf(SUN_GLOW, Color.Transparent), Offset(size.width, 0f), Offset(size.width * 0.3f, size.height)))
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent, 1f to VIGNETTE,
            center = Offset(size.width / 2f, size.height / 2f), radius = max(size.width, size.height) * 0.72f,
        ),
    )
}
