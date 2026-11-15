package io.github.beretta299.sharejoy.presentation.match

import io.github.beretta299.sharejoy.game.Game
import io.github.beretta299.sharejoy.presentation.match.camera.Camera
import io.github.beretta299.sharejoy.presentation.match.effects.Particles

/**
 * What the renderer needs to draw one frame. It changes sixty times a second, so the canvas reads it directly
 * on every frame instead of receiving it as immutable UI state. The renderer only ever reads from it.
 */
interface MatchScene {
    val game: Game

    /** Camera of one shared phone. */
    val camera: Camera

    /** Cameras of two paired phones, left and right. */
    val pairCameras: List<Camera>
    val particles: Particles

    /** Seconds since start, for flags, clouds and other idle animation. */
    val time: Float
    val shake: Float

    val aiming: Boolean
    val pullX: Float
    val pullY: Float
    val anchorX: Float
    val anchorY: Float
    fun launchVx(): Float
    fun launchVy(): Float
}
