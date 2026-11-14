package io.github.beretta299.sharejoy

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ShareJoy: Castle Duel (prototype)",
        state = rememberWindowState(width = 420.dp, height = 900.dp),
    ) {
        App()
    }
}
