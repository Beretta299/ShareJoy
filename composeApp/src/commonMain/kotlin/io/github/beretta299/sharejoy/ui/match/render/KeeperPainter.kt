package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.beretta299.sharejoy.game.Piece
import io.github.beretta299.sharejoy.util.lighten
import kotlin.math.PI

internal fun DrawScope.drawKeeper(view: WorldView, p: Piece, down: Boolean) {
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
