package io.github.beretta299.sharejoy.data.link

import kotlinx.coroutines.flow.StateFlow

/** Which side of the pairing this phone took. The host plays the left castle, the guest the right. */
enum class LinkRole(val player: Int) { HOST(0), GUEST(1) }

sealed interface LinkStatus {
    data object Idle : LinkStatus

    /** Advertising (host) or looking for a host (guest). */
    data class Searching(val role: LinkRole) : LinkStatus

    /** A phone was found. [authCode] is shown on both screens so players can check they found each other. */
    data class Connecting(val role: LinkRole, val peerName: String, val authCode: String) : LinkStatus
    data class Connected(val role: LinkRole, val peerName: String) : LinkStatus
    data class Disconnected(val reason: String) : LinkStatus
}

/**
 * A reliable, ordered message channel to one other nearby phone.
 *
 * Implementations are platform specific (Nearby Connections on Android, a local socket on desktop). Messages
 * are opaque bytes; [io.github.beretta299.sharejoy.data.link.WireCodec] gives them meaning.
 */
interface PeerLink {
    val status: StateFlow<LinkStatus>

    /** Starts waiting for a guest to join. */
    fun host(localName: String)

    /** Starts looking for a host and connects to the first one found. */
    fun join(localName: String)

    /** Queues [message] for the connected peer. Ignored when not connected. */
    fun send(message: ByteArray)

    /** The oldest message received and not yet read, or null if there is none. Never blocks. */
    fun poll(): ByteArray?

    /** Stops searching and drops the connection. */
    fun close()
}

/** What the running platform offers for pairing two phones. */
interface PairingPlatform {
    /** Shown in the lobby, e.g. "Nearby Connections". */
    val technologyName: String

    /** This phone's name as the other phone will see it. */
    val deviceName: String

    /** Asks the user for whatever permissions pairing needs. Returns true if pairing may go ahead. */
    suspend fun ensurePermissions(): Boolean

    fun createLink(): PeerLink
}
