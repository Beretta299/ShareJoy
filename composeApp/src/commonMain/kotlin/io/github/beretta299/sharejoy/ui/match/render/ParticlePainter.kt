package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.beretta299.sharejoy.presentation.match.effects.ParticleKind
import io.github.beretta299.sharejoy.presentation.match.effects.Particles
import kotlin.math.PI
import kotlin.math.max

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()

/** Rubble, dust, sparks, blast flashes and repair glints. */
internal fun DrawScope.drawParticles(view: WorldViewport, particles: Particles) {
    for (particle in particles.items) {
        val centre = Offset(view.screenX(particle.x), view.screenY(particle.y))
        if (centre.x < -60f || centre.x > size.width + 60f) continue
        val lifeFraction = particle.ageSeconds / particle.lifetimeSeconds
        val sizePx = particle.size * view.pixelsPerMetre
        when (particle.kind) {
            ParticleKind.DEBRIS -> {
                val alpha = if (lifeFraction > 0.7f) (1f - lifeFraction) / 0.3f else 1f
                withTransform({
                    translate(centre.x, centre.y)
                    rotate(-particle.rotation * DEGREES_PER_RADIAN, Offset.Zero)
                }) {
                    drawRect(particle.color.copy(alpha = alpha), topLeft = Offset(-sizePx * particle.aspectRatio / 2f, -sizePx / 2f), size = Size(sizePx * particle.aspectRatio, sizePx))
                    drawRect(Color.Black.copy(alpha = 0.35f * alpha), topLeft = Offset(-sizePx * particle.aspectRatio / 2f, -sizePx / 2f), size = Size(sizePx * particle.aspectRatio, sizePx), style = Stroke(1f))
                }
            }
            ParticleKind.DUST -> drawCircle(
                Brush.radialGradient(listOf(particle.color.copy(alpha = 0.42f * (1f - lifeFraction)), particle.color.copy(alpha = 0f)), center = centre, radius = sizePx),
                sizePx, centre,
            )
            ParticleKind.SPARK -> {
                val tail = Offset(centre.x - particle.velocityX * 0.03f * view.pixelsPerMetre, centre.y + particle.velocityY * 0.03f * view.pixelsPerMetre)
                drawLine(particle.color.copy(alpha = 1f - lifeFraction), centre, tail, max(1.5f, sizePx), StrokeCap.Round)
            }
            ParticleKind.FLASH -> drawCircle(
                Brush.radialGradient(listOf(particle.color.copy(alpha = 0.95f * (1f - lifeFraction)), Color(0xFFFF8A2B).copy(alpha = 0.6f * (1f - lifeFraction)), Color.Transparent), center = centre, radius = sizePx),
                sizePx, centre,
            )
            ParticleKind.GLINT -> {
                val glintAlpha = 1f - lifeFraction
                drawLine(particle.color.copy(alpha = glintAlpha), centre - Offset(sizePx, 0f), centre + Offset(sizePx, 0f), max(1.2f, sizePx * 0.3f), StrokeCap.Round)
                drawLine(particle.color.copy(alpha = glintAlpha), centre - Offset(0f, sizePx), centre + Offset(0f, sizePx), max(1.2f, sizePx * 0.3f), StrokeCap.Round)
            }
        }
    }
}
