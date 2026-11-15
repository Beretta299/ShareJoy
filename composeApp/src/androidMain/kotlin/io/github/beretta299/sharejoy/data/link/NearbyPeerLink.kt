package io.github.beretta299.sharejoy.data.link

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * [PeerLink] over Google Nearby Connections: Bluetooth and Wi-Fi between two Android phones, no internet needed.
 * Byte payloads arrive reliably and in order, which is what lockstep play requires.
 */
class NearbyPeerLink(context: Context) : PeerLink {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val serviceId = context.packageName
    private val received = ConcurrentLinkedQueue<ByteArray>()

    private val _status = MutableStateFlow<LinkStatus>(LinkStatus.Idle)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private var role = LinkRole.HOST
    private var localName = ""
    private var peerEndpointId: String? = null
    private var peerName = ""

    override fun host(localName: String) {
        start(LinkRole.HOST, localName)
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startAdvertising(localName, serviceId, connectionCallback, options)
            .addOnFailureListener { fail("Could not start hosting: ${it.message}") }
    }

    override fun join(localName: String) {
        start(LinkRole.GUEST, localName)
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startDiscovery(serviceId, discoveryCallback, options)
            .addOnFailureListener { fail("Could not start searching: ${it.message}") }
    }

    private fun start(role: LinkRole, localName: String) {
        this.role = role
        this.localName = localName
        _status.value = LinkStatus.Searching(role)
    }

    override fun send(message: ByteArray) {
        val endpointId = peerEndpointId ?: return
        if (_status.value is LinkStatus.Connected) client.sendPayload(endpointId, Payload.fromBytes(message))
    }

    override fun poll(): ByteArray? = received.poll()

    override fun close() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        peerEndpointId = null
        _status.value = LinkStatus.Idle
    }

    private fun fail(reason: String) {
        close()
        _status.value = LinkStatus.Disconnected(reason)
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Connect to the first host found; a second request while one is pending would fail anyway.
            if (peerEndpointId != null) return
            peerEndpointId = endpointId
            client.requestConnection(localName, endpointId, connectionCallback).addOnFailureListener {
                peerEndpointId = null
            }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            peerEndpointId = endpointId
            peerName = info.endpointName
            _status.value = LinkStatus.Connecting(role, peerName, info.authenticationDigits)
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                client.stopAdvertising()
                client.stopDiscovery()
                _status.value = LinkStatus.Connected(role, peerName)
            } else {
                // Keep advertising or discovering, so the players can simply try again.
                peerEndpointId = null
                _status.value = LinkStatus.Searching(role)
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (endpointId == peerEndpointId) fail("The other phone disconnected.")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let(received::add)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }
}
