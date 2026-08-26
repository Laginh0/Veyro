package com.veyro.p2p.desktopinterop

import android.app.Application
import android.os.Build
import android.util.Log
import com.veyro.p2p.protocol.FastChannelAnswer
import com.veyro.p2p.protocol.FastChannelOffer
import com.veyro.p2p.protocol.GroupTopologyEvent
import com.veyro.p2p.settings.EcosystemPreferences
import com.veyro.p2p.nearby.LogicalSecurityContext
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress

internal interface DesktopInteropListener {
    fun onDesktopPeersChanged(peers: List<DiscoveredDesktopPeer>)
    fun onDesktopPairingPin(verification: DesktopPairingVerification)
    fun onDesktopTrusted(peer: DesktopTrustedPeer)
    fun onDesktopConnected(peer: DesktopTrustedPeer)
    fun onDesktopDisconnected(peer: DesktopTrustedPeer)
    fun onDesktopApplicationMessage(peer: DesktopTrustedPeer, bytes: ByteArray)
    fun onDesktopRoutedPeersChanged(peers: List<DesktopTrustedPeer>)
    fun onDesktopConnectionAttemptEnded(message: String)
    fun onDesktopStatus(message: String, error: Throwable? = null)
}

internal class DesktopInteropManager(
    application: Application,
    private val scope: CoroutineScope,
    private val logicalSecurity: LogicalSecurityContext,
    private val routedPeerResolver: (String) -> DesktopTrustedPeer?,
    private val listener: DesktopInteropListener
) : DesktopBleListener, DesktopWifiDirectListener, DesktopFastChannelListener, AutoCloseable {
    private val preferences = EcosystemPreferences(application)
    private val identity = DesktopIdentityStore(
        application,
        preferences.localDeviceId(),
        preferences.localDisplayName()
    ).loadOrCreate()
    private val trustStore = DesktopTrustStore(application)
    private val ble = DesktopBleController(application, identity, trustStore, this)
    private val wifiDirect = DesktopWifiDirectController(application, this)
    private var fastChannel: DesktopFastChannel? = null
    private var groupOwnerAddress: InetAddress? = null
    private var pendingOffer: FastChannelOffer? = null
    private var peers: List<DiscoveredDesktopPeer> = emptyList()
    private var routedPeers: List<DesktopTrustedPeer> = emptyList()
    private var topologyEpoch: Long = 0L
    private var started = false
    private var startRetryJob: Job? = null

    fun start() {
        if (started) return
        started = true
        runCatching(ble::start).onFailure {
            Log.e("VeyroDesktopInterop", "BLE transport start failed; retrying", it)
            started = false
            listener.onDesktopStatus("O transporte do Veyro Desktop não pôde ser iniciado.", it)
            startRetryJob?.cancel()
            startRetryJob = scope.launch {
                delay(BLE_START_RETRY_MILLIS)
                if (!started) start()
            }
        }
    }

    fun connect(endpointId: String) {
        val peer = peers.firstOrNull { it.endpointId == endpointId }
            ?: error("O Veyro Desktop não está mais visível por BLE")
        ble.connect(peer)
    }

    fun confirmPin(accepted: Boolean) = ble.confirmPin(accepted)

    fun sendApplicationMessage(bytes: ByteArray, destinationDeviceId: String? = null) = fastChannel
        ?.sendApplicationMessage(bytes, destinationDeviceId ?: checkNotNull(ble.activePeer()).deviceId)
        ?: error("O canal seguro com o Desktop ainda não está conectado")

    fun updateGroupTopology(event: GroupTopologyEvent) {
        val coordinator = ble.activePeer()
            ?: error("A topologia chegou sem um Desktop autenticado")
        require(event.coordinatorDeviceId == coordinator.deviceId && event.epoch > 0L)
        if (event.epoch < topologyEpoch) return
        val memberIds = event.membersList.map { it.deviceId }
        require(memberIds.size <= MAXIMUM_GROUP_MEMBERS && memberIds.distinct().size == memberIds.size)
        val now = System.currentTimeMillis()
        val activeRoutes = event.membersList
            .asSequence()
            .filter { member ->
                member.isAvailable && !member.isCoordinator && member.deviceId != identity.deviceId &&
                    member.deviceId != coordinator.deviceId
            }
            .map { member ->
                require(member.deviceId.isNotBlank() && member.displayName.isNotBlank())
                val publicKey = member.identityPublicKeySpki.toByteArray()
                DesktopInteropProtocol.publicKey(publicKey)
                val pinned = routedPeerResolver(member.deviceId)
                    ?: throw SecurityException("untrusted_routed_peer")
                require(!pinned.isRevoked && MessageDigest.isEqual(
                    pinned.identityPublicKeySpki,
                    publicKey
                )) { "routed_peer_key_mismatch" }
                pinned.copy(displayName = member.displayName, lastSeenAtMillis = now)
            }
            .toList()
        topologyEpoch = event.epoch
        routedPeers = activeRoutes
        fastChannel?.updateRoutedPeers(activeRoutes)
        listener.onDesktopRoutedPeersChanged(activeRoutes)
    }

    fun revokeByDisplayName(displayName: String): Boolean = ble.revokeByDisplayName(displayName)

    fun isConnected(deviceId: String): Boolean = ble.activePeer()?.deviceId == deviceId && fastChannel != null

    override fun onDesktopPeersChanged(peers: List<DiscoveredDesktopPeer>) {
        this.peers = peers
        listener.onDesktopPeersChanged(peers)
    }

    override fun onDesktopPairingPin(verification: DesktopPairingVerification) =
        listener.onDesktopPairingPin(verification)

    override fun onDesktopTrusted(peer: DesktopTrustedPeer) {
        listener.onDesktopTrusted(peer)
        runCatching { wifiDirect.connectToDesktop(peer.displayName) }
            .onFailure { listener.onDesktopStatus("Não foi possível iniciar o Wi-Fi Direct.", it) }
    }

    override fun onDesktopFastChannelOffer(offer: FastChannelOffer) {
        if (offer.targetDeviceId != identity.deviceId || fastChannel != null) return
        pendingOffer = offer
        tryOpenFastChannel()
    }

    override fun onDesktopFastChannelAnswer(answer: FastChannelAnswer) {
        listener.onDesktopStatus(
            if (answer.accepted) "O Desktop aceitou o canal rápido."
            else "O Desktop recusou o canal rápido: ${answer.reason}"
        )
    }

    override fun onDesktopBleStatus(message: String, error: Throwable?) {
        listener.onDesktopStatus(message, error)
        if (message == "Canal BLE com o Desktop encerrado." && fastChannel == null && ble.activePeer() == null) {
            listener.onDesktopConnectionAttemptEnded(message)
        }
    }

    override fun onDesktopWifiDirectReady(groupOwnerAddress: InetAddress) {
        this.groupOwnerAddress = groupOwnerAddress
        tryOpenFastChannel()
    }

    override fun onDesktopWifiDirectLost() {
        groupOwnerAddress = null
        val lostChannel = fastChannel
        val lostPeer = ble.activePeer()
        lostChannel?.close()
        fastChannel = null
        routedPeers = emptyList()
        topologyEpoch = 0L
        listener.onDesktopRoutedPeersChanged(emptyList())
        if (lostChannel != null && lostPeer != null) {
            listener.onDesktopDisconnected(lostPeer)
        }
    }

    override fun onDesktopWifiDirectStatus(message: String, error: Throwable?) =
        listener.onDesktopStatus(message, error)

    private fun tryOpenFastChannel() {
        val offer = pendingOffer ?: return
        val address = groupOwnerAddress ?: return
        val peer = ble.activePeer()
        if (peer == null || peer.deviceId != offer.deviceId) {
            runCatching { ble.sendFastChannelAnswer(offer.sessionId, false, "unauthorized") }
            pendingOffer = null
            listener.onDesktopStatus("Oferta de canal rápido rejeitada: identidade não confiável.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ble.sendFastChannelAnswer(offer.sessionId, false, "android_version_unsupported")
            pendingOffer = null
            listener.onDesktopStatus("O canal rápido com Desktop requer Android 10 ou superior.")
            return
        }
        ble.sendFastChannelAnswer(offer.sessionId, true)
        fastChannel?.close()
        fastChannel = DesktopFastChannel(identity, peer, logicalSecurity, scope, this).also {
            it.updateRoutedPeers(routedPeers)
            it.connect(address, offer)
        }
        pendingOffer = null
        routedPeers = emptyList()
        topologyEpoch = 0L
        listener.onDesktopRoutedPeersChanged(emptyList())
    }

    override fun onDesktopFastChannelConnected(source: DesktopFastChannel, peer: DesktopTrustedPeer) {
        if (fastChannel !== source) {
            source.close()
            return
        }
        listener.onDesktopConnected(peer)
    }

    override fun onDesktopApplicationMessage(
        source: DesktopFastChannel,
        peer: DesktopTrustedPeer,
        bytes: ByteArray
    ) {
        if (fastChannel === source) listener.onDesktopApplicationMessage(peer, bytes)
    }

    override fun onDesktopFastChannelStatus(
        source: DesktopFastChannel,
        message: String,
        error: Throwable?
    ) {
        if (fastChannel === source) listener.onDesktopStatus(message, error)
    }

    override fun onDesktopFastChannelDisconnected(source: DesktopFastChannel, peer: DesktopTrustedPeer) {
        if (fastChannel !== source) return
        fastChannel = null
        routedPeers = emptyList()
        topologyEpoch = 0L
        listener.onDesktopRoutedPeersChanged(emptyList())
        listener.onDesktopDisconnected(peer)
    }

    override fun close() {
        started = false
        startRetryJob?.cancel()
        startRetryJob = null
        fastChannel?.close()
        fastChannel = null
        wifiDirect.close()
        ble.close()
        peers = emptyList()
        routedPeers = emptyList()
        topologyEpoch = 0L
        listener.onDesktopRoutedPeersChanged(emptyList())
        groupOwnerAddress = null
        pendingOffer = null
    }

    private companion object {
        const val BLE_START_RETRY_MILLIS = 3_000L
        const val MAXIMUM_GROUP_MEMBERS = 32
    }
}
