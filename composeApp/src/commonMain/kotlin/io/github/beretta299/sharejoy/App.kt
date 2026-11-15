package io.github.beretta299.sharejoy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.beretta299.sharejoy.data.link.PairingPlatform
import io.github.beretta299.sharejoy.presentation.lobby.LobbyViewModel
import io.github.beretta299.sharejoy.presentation.lobby.PairedMatch
import io.github.beretta299.sharejoy.presentation.match.MatchViewModel
import io.github.beretta299.sharejoy.presentation.match.sync.LockstepSync
import io.github.beretta299.sharejoy.ui.lobby.LobbyScreen
import io.github.beretta299.sharejoy.ui.match.MatchRoute
import io.github.beretta299.sharejoy.ui.theme.ShareJoyTheme

/** Where the app is: in the lobby, or in a match on one shared device or with a paired phone. */
private sealed interface Destination {
    data object Lobby : Destination
    data object SharedDeviceMatch : Destination
    class PairedDeviceMatch(val match: PairedMatch) : Destination
}

/**
 * Entry point shared by Android, desktop and iOS. [pairingPlatform] is how this platform connects two phones,
 * or null where pairing is not available yet.
 */
@Composable
fun App(pairingPlatform: PairingPlatform? = null) {
    ShareJoyTheme {
        var destination by remember { mutableStateOf<Destination>(Destination.Lobby) }
        val lobby = viewModel { LobbyViewModel(pairingPlatform) }
        val pairedMatch by lobby.pairedMatch.collectAsState()

        LaunchedEffect(pairedMatch) {
            pairedMatch?.let {
                destination = Destination.PairedDeviceMatch(it)
                lobby.onMatchStarted()
            }
        }

        when (val current = destination) {
            Destination.Lobby -> LobbyScreen(
                state = lobby.uiState.collectAsState().value,
                onPlayOnThisDevice = { destination = Destination.SharedDeviceMatch },
                onHost = lobby::host,
                onJoin = lobby::join,
                onCancel = lobby::cancel,
            )
            Destination.SharedDeviceMatch -> {
                val match = remember(current) { MatchViewModel(onLeave = { destination = Destination.Lobby }) }
                MatchRoute(match)
            }
            is Destination.PairedDeviceMatch -> {
                val match = remember(current) {
                    MatchViewModel(
                        sync = LockstepSync(current.match.link, localPlayer = current.match.role.player),
                        pairedScreens = current.match.screens,
                        onLeave = { destination = Destination.Lobby },
                    )
                }
                DisposableEffect(current) { onDispose { current.match.link.close() } }
                MatchRoute(match)
            }
        }
    }
}
