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

/** The Keeper: a small king in the owner's colours, facing the enemy. Crossed-out eyes once fallen. */
internal fun DrawScope.drawKeeper(view: WorldViewport, keeper: Piece, down: Boolean) {
    val body = keeper.body
    val centreX = view.screenX(body.x)
    val centreY = view.screenY(body.y)
    if (centreX < -200f || centreX > size.width + 200f) return
    val texelToPixel = view.pixelsPerMetre / TEXELS_PER_METRE
    val widthTexels = body.halfWidth * 2f * TEXELS_PER_METRE
    val heightTexels = body.halfHeight * 2f * TEXELS_PER_METRE
    val facing = if (keeper.owner == 0) 1f else -1f
    val robe = PLAYER_COLORS[keeper.owner]
    val robeDark = PLAYER_DARK[keeper.owner]
    val outline = Color(0xFF1B120C)

    withTransform({
        translate(centreX, centreY)
        rotate(-body.angle * 180f / PI.toFloat(), Offset.Zero)
        scale(texelToPixel * facing, texelToPixel, Offset.Zero)
    }) {
        val bottom = heightTexels / 2f
        val shoulder = -heightTexels * 0.14f
        // Robe.
        val robeShape = Path()
        robeShape.moveTo(-widthTexels / 2f + 2f, bottom)
        robeShape.lineTo(widthTexels / 2f - 2f, bottom)
        robeShape.lineTo(widthTexels / 2f - 9f, shoulder)
        robeShape.quadraticTo(0f, shoulder - 9f, -widthTexels / 2f + 9f, shoulder)
        robeShape.close()
        drawPath(robeShape, Brush.horizontalGradient(listOf(robeDark, robe, robe.lighten(0.12f), robe), startX = -widthTexels / 2f, endX = widthTexels / 2f))
        drawLine(robeDark, Offset(-4f, shoulder + 6f), Offset(-7f, bottom - 9f), 2f)
        drawLine(robeDark, Offset(8f, shoulder + 8f), Offset(11f, bottom - 9f), 2f)
        // Ermine hem and gold belt.
        drawRect(Color(0xFFF4EFE4), topLeft = Offset(-widthTexels / 2f + 2f, bottom - 9f), size = Size(widthTexels - 4f, 9f))
        for (i in 0..4) drawCircle(outline, 1.5f, Offset(-widthTexels / 2f + 8f + i * (widthTexels - 16f) / 4f, bottom - 4.5f))
        drawRect(Color(0xFFE2B93B), topLeft = Offset(-widthTexels / 2f + 7f, heightTexels * 0.08f), size = Size(widthTexels - 14f, 5f))
        drawPath(robeShape, outline, style = Stroke(2.2f, join = StrokeJoin.Round))
        // Head, beard, crown.
        val headCentre = Offset(1f, shoulder - 9f)
        val headRadius = 13.5f
        drawCircle(Color(0xFFF0C9A2), headRadius, headCentre)
        val beard = Path()
        beard.moveTo(headCentre.x - headRadius + 1f, headCentre.y + 1f)
        beard.quadraticTo(headCentre.x, headCentre.y + headRadius * 2.1f, headCentre.x + headRadius - 1f, headCentre.y + 1f)
        beard.quadraticTo(headCentre.x, headCentre.y + headRadius * 0.9f, headCentre.x - headRadius + 1f, headCentre.y + 1f)
        drawPath(beard, Color(0xFFE9E4DA))
        drawCircle(outline, headRadius, headCentre, style = Stroke(2f))
        if (down) {
            for (eyeX in listOf(headCentre.x + 1f, headCentre.x + 8f)) {
                drawLine(outline, Offset(eyeX - 2.2f, headCentre.y - 4.5f), Offset(eyeX + 2.2f, headCentre.y - 0.5f), 1.6f)
                drawLine(outline, Offset(eyeX - 2.2f, headCentre.y - 0.5f), Offset(eyeX + 2.2f, headCentre.y - 4.5f), 1.6f)
            }
        } else {
            drawCircle(outline, 1.8f, Offset(headCentre.x + 1.5f, headCentre.y - 2.5f))
            drawCircle(outline, 1.8f, Offset(headCentre.x + 8.5f, headCentre.y - 2.5f))
        }
        val crown = Path()
        val crownBase = headCentre.y - headRadius + 3f
        crown.moveTo(headCentre.x - 12f, crownBase)
        crown.lineTo(headCentre.x - 13f, crownBase - 13f)
        crown.lineTo(headCentre.x - 6f, crownBase - 6f)
        crown.lineTo(headCentre.x, crownBase - 15f)
        crown.lineTo(headCentre.x + 6f, crownBase - 6f)
        crown.lineTo(headCentre.x + 13f, crownBase - 13f)
        crown.lineTo(headCentre.x + 12f, crownBase)
        crown.close()
        drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFC9972B)), startY = crownBase - 15f, endY = crownBase))
        drawPath(crown, outline, style = Stroke(1.8f, join = StrokeJoin.Round))
        drawCircle(Color(0xFFD23B3B), 2.2f, Offset(headCentre.x, crownBase - 4.5f))
    }
}
