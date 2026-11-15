package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min
import kotlin.math.sin

/** Sky gradient, sun, drifting clouds, three ranges of hills and a tree line, each layer with its own parallax. */
internal fun DrawScope.drawSky(view: WorldViewport, time: Float) {
    val horizon = view.screenY(0f)
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF2F5F9F), 0.5f to Color(0xFF86B9E2), 0.8f to Color(0xFFE9DDC0), 1f to Color(0xFFF4E3BC),
            startY = min(0f, horizon - size.height * 1.2f), endY = horizon,
        ),
    )
    // Sun with a wide glow.
    val sun = Offset(view.parallaxScreenX(0f, depthFactor = 0.03f) + size.width * 0.24f, horizon - size.height * 0.46f)
    val shortSide = min(size.width, size.height)
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFF3C4), Color.Transparent), center = sun, radius = shortSide * 0.6f), shortSide * 0.6f, sun)
    drawCircle(Color(0xFFFFF6D8), shortSide * 0.06f, sun)

    // Clouds drift slowly; far layers move less than the camera.
    for (cloud in 0 until CLOUD_COUNT) {
        val worldX = -130f + cloud * 31f + hash(cloud) * 14f + time * 0.35f
        val screenX = view.parallaxScreenX(worldX, depthFactor = 0.16f)
        val screenY = horizon - size.height * (0.3f + hash(cloud + 40) * 0.36f)
        val puffSize = view.pixelsPerMetre * (0.7f + hash(cloud + 80) * 0.7f)
        for (puff in 0 until 5) {
            val puffOffsetX = (puff - 2) * 1.5f * puffSize
            val puffRadiusY = puffSize * (0.75f + hash(cloud * 7 + puff) * 0.5f)
            drawOval(Color.White.copy(alpha = 0.5f), Offset(screenX + puffOffsetX - puffSize * 1.4f, screenY - puffRadiusY + (puff % 2) * puffSize * 0.2f), Size(puffSize * 2.8f, puffRadiusY * 2f))
        }
        drawOval(Color(0x22203A60), Offset(screenX - puffSize * 3.6f, screenY + puffSize * 0.35f), Size(puffSize * 7.2f, puffSize * 0.7f))
    }

    drawRidge(view, horizon, parallax = 0.14f, step = 7f, base = 6.5f, amplitude = 9f, seed = 3, top = Color(0xFF8FA6C6), bottom = Color(0xFFB9C9DB))
    drawRidge(view, horizon, parallax = 0.26f, step = 5f, base = 3.2f, amplitude = 5f, seed = 57, top = Color(0xFF6F8FA8), bottom = Color(0xFF9DB6C2))
    drawRidge(view, horizon, parallax = 0.45f, step = 3.5f, base = 1.2f, amplitude = 2.6f, seed = 131, top = Color(0xFF55803F), bottom = Color(0xFF7FA35A))

    // A tree line on the nearest hills.
    for (tree in -TREES_EACH_SIDE..TREES_EACH_SIDE) {
        val worldX = tree * 2.3f + hash(tree + 900) * 1.4f
        val screenX = view.parallaxScreenX(worldX, depthFactor = 0.6f)
        if (screenX < -80f || screenX > size.width + 80f) continue
        val treeRadius = view.pixelsPerMetre * (0.55f + hash(tree + 300) * 0.5f)
        val screenY = horizon - treeRadius * 0.5f
        drawCircle(Color(0xFF3F6B35), treeRadius, Offset(screenX, screenY))
        drawCircle(Color(0xFF4F7F40), treeRadius * 0.7f, Offset(screenX - treeRadius * 0.25f, screenY - treeRadius * 0.3f))
    }
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x55E9E2C8)), startY = horizon - size.height * 0.2f, endY = horizon),
        topLeft = Offset(0f, horizon - size.height * 0.2f), size = Size(size.width, size.height * 0.2f),
    )
}

/** One range of hills. [parallax] is how fast it slides relative to the foreground (0 = fixed, 1 = foreground). */
internal fun DrawScope.drawRidge(
    view: WorldViewport, horizon: Float, parallax: Float, step: Float, base: Float, amplitude: Float, seed: Int, top: Color, bottom: Color,
) {
    val path = Path()
    val layerScale = view.pixelsPerMetre * parallax
    var first = true
    var ridgePoint = -RIDGE_POINTS
    var peak = horizon
    while (ridgePoint <= RIDGE_POINTS) {
        val screenX = view.parallaxScreenX(ridgePoint * step, depthFactor = parallax)
        if (screenX > -step * layerScale * 2f && screenX < size.width + step * layerScale * 2f) {
            val ridgeHeight = base + amplitude * (0.35f * hash(ridgePoint + seed) + 0.65f * (0.5f + 0.5f * sin(ridgePoint * 0.9f + seed)))
            val screenY = horizon - ridgeHeight * view.pixelsPerMetre * (0.5f + parallax)
            peak = min(peak, screenY)
            if (first) {
                path.moveTo(screenX, horizon)
                first = false
            }
            path.lineTo(screenX, screenY)
        }
        ridgePoint++
    }
    if (first) return
    path.lineTo(size.width + 400f, horizon)
    path.close()
    drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = peak, endY = horizon))
}

private const val CLOUD_COUNT = 9
private const val TREES_EACH_SIDE = 60
private const val RIDGE_POINTS = 70
