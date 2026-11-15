package io.github.beretta299.sharejoy.data.link

import kotlinx.coroutines.flow.MutableStateFlow

/** Two in-memory [PeerLink]s wired to each other. Messages only arrive when [deliver] is called, like latency. */
class FakeLinkPair {
    val host = FakeLink(LinkRole.HOST)
    val guest = FakeLink(LinkRole.GUEST)

    init {
        host.peer = guest
        guest.peer = host
    }

    /** Hands every message sent so far to its receiver. */
    fun deliver() {
        host.flushOutbox()
        guest.flushOutbox()
    }

    fun disconnect() {
        host.status.value = LinkStatus.Disconnected("test")
        guest.status.value = LinkStatus.Disconnected("test")
    }
}

class FakeLink(role: LinkRole) : PeerLink {
    override val status = MutableStateFlow<LinkStatus>(LinkStatus.Connected(role, "peer"))
    lateinit var peer: FakeLink
    private val outbox = ArrayDeque<ByteArray>()
    private val inbox = ArrayDeque<ByteArray>()

    override fun host(localName: String) = Unit
    override fun join(localName: String) = Unit

    override fun send(message: ByteArray) {
        outbox.addLast(message)
    }

    override fun poll(): ByteArray? = inbox.removeFirstOrNull()

    override fun close() {
        status.value = LinkStatus.Idle
    }

    fun flushOutbox() {
        while (outbox.isNotEmpty()) peer.inbox.addLast(outbox.removeFirst())
    }
}
