package io.github.beretta299.sharejoy.presentation.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.beretta299.sharejoy.data.link.LinkRole
import io.github.beretta299.sharejoy.data.link.LinkStatus
import io.github.beretta299.sharejoy.data.link.PROTOCOL_VERSION
import io.github.beretta299.sharejoy.data.link.PairingPlatform
import io.github.beretta299.sharejoy.data.link.PeerLink
import io.github.beretta299.sharejoy.data.link.WireCodec
import io.github.beretta299.sharejoy.data.link.WireMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A connection to another phone that is ready to play on. */
class PairedMatch(val link: PeerLink, val role: LinkRole, val peerName: String)

/** What the lobby shows. */
sealed interface LobbyUiState {
    /** [pairingName] names the pairing technology, or is null when this platform cannot pair yet. */
    data class Menu(val pairingName: String?, val error: String? = null) : LobbyUiState
    data class Searching(val role: LinkRole) : LobbyUiState
    data class Connecting(val peerName: String, val authCode: String) : LobbyUiState
}

/**
 * Finds another phone and agrees to play. One phone hosts and takes the left castle; the other joins and takes
 * the right one. After connecting, both send a [WireMessage.Hello] so builds that cannot play together find out
 * before the match starts.
 */
class LobbyViewModel(private val platform: PairingPlatform?) : ViewModel() {
    private val menu = LobbyUiState.Menu(platform?.technologyName)
    private val _uiState = MutableStateFlow<LobbyUiState>(menu)
    val uiState: StateFlow<LobbyUiState> = _uiState.asStateFlow()

    private val _pairedMatch = MutableStateFlow<PairedMatch?>(null)

    /** Becomes non-null once a match with another phone can start. */
    val pairedMatch: StateFlow<PairedMatch?> = _pairedMatch.asStateFlow()

    private var link: PeerLink? = null
    private var pairingJob: Job? = null

    fun host() = startPairing(LinkRole.HOST)

    fun join() = startPairing(LinkRole.GUEST)

    private fun startPairing(role: LinkRole) {
        val platform = platform ?: return
        cancel()
        pairingJob = viewModelScope.launch {
            if (!platform.ensurePermissions()) {
                _uiState.value = menu.copy(error = "Pairing needs the Nearby devices permission.")
                return@launch
            }
            val newLink = platform.createLink()
            link = newLink
            _uiState.value = LobbyUiState.Searching(role)
            if (role == LinkRole.HOST) newLink.host(platform.deviceName) else newLink.join(platform.deviceName)
            newLink.status.collect { status -> onStatus(newLink, status) }
        }
    }

    private suspend fun onStatus(link: PeerLink, status: LinkStatus) {
        when (status) {
            LinkStatus.Idle, is LinkStatus.Searching -> Unit
            is LinkStatus.Connecting -> _uiState.value = LobbyUiState.Connecting(status.peerName, status.authCode)
            is LinkStatus.Connected -> greet(link, status)
            is LinkStatus.Disconnected -> fail(status.reason)
        }
    }

    /** Swaps [WireMessage.Hello]s and starts the match if both phones speak the same protocol. */
    private suspend fun greet(link: PeerLink, status: LinkStatus.Connected) {
        link.send(WireCodec.encode(WireMessage.Hello(PROTOCOL_VERSION, platform?.deviceName.orEmpty())))
        while (link.status.value is LinkStatus.Connected) {
            val hello = link.poll()?.let(WireCodec::decode) as? WireMessage.Hello
            if (hello != null) {
                if (hello.protocolVersion != PROTOCOL_VERSION) {
                    fail("The other phone has a different version of ShareJoy. Update both phones.")
                } else {
                    this.link = null // handed over to the match
                    _uiState.value = menu
                    _pairedMatch.value = PairedMatch(link, status.role, status.peerName)
                    pairingJob?.cancel()
                }
                return
            }
            delay(HELLO_POLL_MILLIS)
        }
    }

    private fun fail(reason: String) {
        link?.close()
        link = null
        _uiState.value = menu.copy(error = reason)
        pairingJob?.cancel()
    }

    /** Stops searching and returns to the menu. */
    fun cancel() {
        pairingJob?.cancel()
        pairingJob = null
        link?.close()
        link = null
        _uiState.value = menu
    }

    /** Call once the match has taken the [pairedMatch]. */
    fun onMatchStarted() {
        _pairedMatch.value = null
    }

    override fun onCleared() {
        cancel()
    }

    private companion object {
        const val HELLO_POLL_MILLIS = 16L
    }
}
