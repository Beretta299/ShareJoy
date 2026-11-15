package io.github.beretta299.sharejoy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import io.github.beretta299.sharejoy.data.link.AndroidPairingPlatform
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var pairingPlatform: AndroidPairingPlatform

    override fun onCreate(savedInstanceState: Bundle?) {
        pairingPlatform = AndroidPairingPlatform(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Full screen: hide the system bars, swipe from an edge to bring them back.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { App(pairingPlatform) }
    }
}
