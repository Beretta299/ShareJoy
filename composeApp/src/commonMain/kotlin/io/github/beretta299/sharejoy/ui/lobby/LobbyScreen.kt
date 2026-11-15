package io.github.beretta299.sharejoy.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.beretta299.sharejoy.data.link.LinkRole
import io.github.beretta299.sharejoy.presentation.lobby.LobbyUiState

private val Background = Brush.verticalGradient(listOf(Color(0xFF2B3A55), Color(0xFF1A1410)))
private val Panel = Color(0xE6221810)
private val Gold = Color(0xFFD8B25A)
private val Parchment = Color(0xFFF3E6C8)
private val Muted = Color(0xFFB9A888)
private val Warning = Color(0xFFFF9A7A)
private val ButtonShape = RoundedCornerShape(10.dp)

/** The first screen: play on this device, or pair with another phone as host or guest. */
@Composable
fun LobbyScreen(
    state: LobbyUiState,
    onPlayOnThisDevice: () -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 340.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LobbyText("ShareJoy", 40, Parchment, FontWeight.Bold)
            LobbyText("Castle Duel", 18, Gold)
            Spacer(Modifier.height(18.dp))
            when (state) {
                is LobbyUiState.Menu -> Menu(state, onPlayOnThisDevice, onHost, onJoin)
                is LobbyUiState.Searching -> Searching(state.role, onCancel)
                is LobbyUiState.Connecting -> Connecting(state, onCancel)
            }
        }
    }
}

@Composable
private fun Menu(state: LobbyUiState.Menu, onPlayOnThisDevice: () -> Unit, onHost: () -> Unit, onJoin: () -> Unit) {
    state.error?.let { LobbyText(it, 13, Warning) }
    if (state.pairingName != null) {
        LobbyText("Two phones, side by side", 13, Muted)
        LobbyButton("Host a game", onHost, primary = true)
        LobbyButton("Join a game", onJoin, primary = true)
        Spacer(Modifier.height(10.dp))
    } else {
        LobbyText("Pairing two phones is not available on this device yet.", 13, Muted)
    }
    LobbyText("One device, passed between players", 13, Muted)
    LobbyButton("Play on this device", onPlayOnThisDevice, primary = false)
}

@Composable
private fun Searching(role: LinkRole, onCancel: () -> Unit) {
    val hosting = role == LinkRole.HOST
    LobbyText(if (hosting) "Waiting for the other phone…" else "Looking for a host…", 20, Parchment, FontWeight.Bold)
    LobbyText(
        if (hosting) "On the other phone, tap Join a game. You play the left castle." else "On the other phone, tap Host a game. You play the right castle.",
        13, Muted,
    )
    LobbyButton("Cancel", onCancel, primary = false)
}

@Composable
private fun Connecting(state: LobbyUiState.Connecting, onCancel: () -> Unit) {
    LobbyText("Connecting to ${state.peerName}…", 20, Parchment, FontWeight.Bold)
    if (state.authCode.isNotEmpty()) {
        LobbyText("Both phones should show this code", 13, Muted)
        LobbyText(state.authCode, 30, Gold, FontWeight.Bold)
    }
    LobbyButton("Cancel", onCancel, primary = false)
}

@Composable
private fun LobbyButton(text: String, onClick: () -> Unit, primary: Boolean) {
    Text(
        text,
        modifier = Modifier.clip(ButtonShape)
            .background(if (primary) Gold else Panel)
            .border(1.dp, Gold.copy(alpha = 0.6f), ButtonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
        color = if (primary) Color(0xFF221810) else Parchment,
        fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
    )
}

@Composable
private fun LobbyText(text: String, size: Int, color: Color, weight: FontWeight = FontWeight.Normal) {
    Text(text, color = color, fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
}
