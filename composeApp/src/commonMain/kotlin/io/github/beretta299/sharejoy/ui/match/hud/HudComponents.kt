package io.github.beretta299.sharejoy.ui.match.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Panel = Color(0xE6221810)
internal val PanelLight = Color(0xE63A2A1B)
internal val Gold = Color(0xFFD8B25A)
internal val Parchment = Color(0xFFF3E6C8)
internal val Muted = Color(0xFFB9A888)
internal val PanelShape = RoundedCornerShape(10.dp)
internal val NAMES = listOf("Red", "Blue")

@Composable
internal fun Label(
    text: String, size: Int, color: Color = Parchment, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier,
) {
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight,
        fontFamily = FontFamily.Serif, textAlign = TextAlign.Start, maxLines = 1,
    )
}

@Composable
internal fun Coin(amount: Int, dim: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (dim) 0.45f else 1f)) {
        Box(
            Modifier.size(13.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFE9A6), Color(0xFFC9972B))))
                .border(1.dp, Color(0xFF7A5A14), CircleShape),
        )
        Spacer(Modifier.width(4.dp))
        Label("$amount", 14, Parchment, FontWeight.Bold)
    }
}

@Composable
internal fun Banner(text: String, accent: Color, small: Boolean = false) {
    Label(
        text, if (small) 16 else 26, Parchment, FontWeight.Bold,
        modifier = Modifier.clip(PanelShape).background(Panel).border(2.dp, accent, PanelShape)
            .padding(horizontal = if (small) 16.dp else 28.dp, vertical = if (small) 6.dp else 12.dp),
    )
}
