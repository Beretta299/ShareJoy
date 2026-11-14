package io.github.beretta299.sharejoy

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ShareJoy: Castle Duel (prototype)",
        state = rememberWindowState(width = 1280.dp, height = 620.dp),
    ) {
        App()
    }
}
