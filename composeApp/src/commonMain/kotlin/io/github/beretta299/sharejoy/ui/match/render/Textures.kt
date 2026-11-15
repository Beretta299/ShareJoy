package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.beretta299.sharejoy.util.Rng
import io.github.beretta299.sharejoy.util.darken
import io.github.beretta299.sharejoy.util.lighten

/**
 * Material textures painted once at start-up, in code, so the prototype ships without image assets.
 * One metre of world is [TEXELS_PER_METRE] texels.
 */
class Textures {
    val stone: ImageBitmap = paint(TEXTURE_SIZE) { paintMasonry() }
    val wood: ImageBitmap = paint(TEXTURE_SIZE) { paintWood() }
    val iron: ImageBitmap = paint(TEXTURE_SIZE) { paintIron() }

    private fun paint(size: Int, block: DrawScope.() -> Unit): ImageBitmap {
        val image = ImageBitmap(size, size)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(size.toFloat(), size.toFloat()), block)
        return image
    }

    private fun DrawScope.paintMasonry() {
        val rng = Rng(7)
        drawRect(Color(0xFF4A463F)) // mortar
        val brickHeight = TEXELS_PER_METRE / 2f
        val brickWidth = TEXELS_PER_METRE.toFloat()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else -brickWidth / 2f
            while (x < size.width) {
                val shade = rng.range(-0.07f, 0.07f)
                val warm = rng.range(-0.02f, 0.03f)
                val base = Color(0.60f + shade + warm, 0.58f + shade, 0.53f + shade - warm)
                val brickTopLeft = Offset(x + 1.5f, y + 1.5f)
                val brickSize = Size(brickWidth - 3f, brickHeight - 3f)
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(base.lighten(0.10f), base, base.darken(0.12f)), startY = y, endY = y + brickHeight,
                    ),
                    topLeft = brickTopLeft, size = brickSize, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f),
                )
                // Chiselled highlight along the top edge and shadow along the bottom.
                drawLine(Color.White.copy(alpha = 0.16f), Offset(x + 3f, y + 2.5f), Offset(x + brickWidth - 3f, y + 2.5f), 1.2f)
                drawLine(Color.Black.copy(alpha = 0.22f), Offset(x + 3f, y + brickHeight - 2.5f), Offset(x + brickWidth - 3f, y + brickHeight - 2.5f), 1.4f)
                x += brickWidth
            }
            y += brickHeight
            row++
        }
        // Grain, pits and lichen.
        repeat(5200) {
            val dark = rng.next() < 0.6f
            drawCircle(
                (if (dark) Color.Black else Color.White).copy(alpha = rng.range(0.03f, 0.12f)),
                radius = rng.range(0.4f, 1.5f), center = Offset(rng.range(0f, size.width), rng.range(0f, size.height)),
            )
        }
        repeat(26) {
            val patchCenter = Offset(rng.range(0f, size.width), rng.range(0f, size.height))
            repeat(9) {
                drawCircle(
                    Color(0xFF5E6B3A).copy(alpha = rng.range(0.03f, 0.08f)),
                    radius = rng.range(3f, 9f), center = patchCenter + Offset(rng.range(-10f, 10f), rng.range(-6f, 6f)),
                )
            }
        }
        repeat(40) {
            val start = Offset(rng.range(0f, size.width), rng.range(0f, size.height))
            drawLine(Color.Black.copy(alpha = 0.16f), start, start + Offset(rng.range(-7f, 7f), rng.range(-5f, 5f)), 0.8f)
        }
    }

    private fun DrawScope.paintWood() {
        val rng = Rng(21)
        val plankHeight = TEXELS_PER_METRE / 2f
        var y = 0f
        while (y < size.height) {
            val shade = rng.range(-0.05f, 0.05f)
            val base = Color(0.52f + shade, 0.35f + shade * 0.8f, 0.19f + shade * 0.5f)
            drawRect(
                Brush.verticalGradient(listOf(base.lighten(0.08f), base, base.darken(0.14f)), startY = y, endY = y + plankHeight),
                topLeft = Offset(0f, y), size = Size(size.width, plankHeight),
            )
            // Long grain lines that wander slightly.
            repeat(13) {
                var grainX = 0f
                var grainY = y + rng.range(2f, plankHeight - 2f)
                val alpha = rng.range(0.06f, 0.2f)
                val dark = rng.next() < 0.75f
                while (grainX < size.width) {
                    val nextX = grainX + rng.range(18f, 46f)
                    val nextY = (grainY + rng.range(-1.1f, 1.1f)).coerceIn(y + 1.5f, y + plankHeight - 1.5f)
                    drawLine(
                        (if (dark) Color(0xFF2A1708) else Color(0xFFE2B47A)).copy(alpha = alpha),
                        Offset(grainX, grainY), Offset(nextX, nextY), rng.range(0.6f, 1.3f),
                    )
                    grainX = nextX
                    grainY = nextY
                }
            }
            // The gap between planks.
            drawLine(Color(0xFF1C0F05).copy(alpha = 0.75f), Offset(0f, y + plankHeight - 0.8f), Offset(size.width, y + plankHeight - 0.8f), 1.8f)
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y + 1f), Offset(size.width, y + 1f), 1f)
            y += plankHeight
        }
        repeat(1800) {
            drawCircle(
                Color(0xFF1C0F05).copy(alpha = rng.range(0.03f, 0.1f)),
                radius = rng.range(0.4f, 1.2f), center = Offset(rng.range(0f, size.width), rng.range(0f, size.height)),
            )
        }
    }

    private fun DrawScope.paintIron() {
        val rng = Rng(33)
        drawRect(Color(0xFF2B3036))
        val plateSize = TEXELS_PER_METRE.toFloat()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                val shade = rng.range(-0.03f, 0.03f)
                val base = Color(0.33f + shade, 0.36f + shade, 0.40f + shade)
                drawRect(
                    Brush.linearGradient(
                        listOf(base.lighten(0.14f), base, base.darken(0.16f)),
                        start = Offset(x, y), end = Offset(x + plateSize, y + plateSize),
                    ),
                    topLeft = Offset(x + 1.5f, y + 1.5f), size = Size(plateSize - 3f, plateSize - 3f),
                )
                for (rivetX in listOf(x + 8f, x + plateSize - 8f)) for (rivetY in listOf(y + 8f, y + plateSize - 8f)) {
                    drawCircle(Color(0xFF14171A), 3.6f, Offset(rivetX + 0.7f, rivetY + 0.9f))
                    drawCircle(Color(0xFF7C858F), 3.1f, Offset(rivetX, rivetY))
                    drawCircle(Color.White.copy(alpha = 0.5f), 1.1f, Offset(rivetX - 0.9f, rivetY - 0.9f))
                }
                x += plateSize
            }
            y += plateSize
        }
        // Brushed streaks and rust.
        repeat(700) {
            val start = Offset(rng.range(0f, size.width), rng.range(0f, size.height))
            drawLine(Color.White.copy(alpha = rng.range(0.02f, 0.08f)), start, start + Offset(rng.range(6f, 30f), rng.range(-1f, 1f)), 0.7f)
        }
        repeat(900) {
            drawCircle(
                Color(0xFF8A4B22).copy(alpha = rng.range(0.03f, 0.12f)),
                radius = rng.range(0.6f, 2.4f), center = Offset(rng.range(0f, size.width), rng.range(0f, size.height)),
            )
        }
    }

    companion object {
        const val TEXELS_PER_METRE = 64
        const val TEXTURE_SIZE = 512
    }
}
