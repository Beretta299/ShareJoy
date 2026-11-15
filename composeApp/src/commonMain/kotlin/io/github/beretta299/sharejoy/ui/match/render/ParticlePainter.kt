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
import kotlin.math.max

internal fun DrawScope.drawParticles(view: WorldView, particles: Particles) {
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
