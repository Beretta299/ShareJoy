package io.github.beretta299.sharejoy.util

import androidx.compose.ui.graphics.Color

fun Color.lighten(amount: Float): Color = Color(
    (red + amount).coerceIn(0f, 1f), (green + amount).coerceIn(0f, 1f), (blue + amount).coerceIn(0f, 1f), alpha,
)

fun Color.darken(amount: Float): Color = lighten(-amount)
