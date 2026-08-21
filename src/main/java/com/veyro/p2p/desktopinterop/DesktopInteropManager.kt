package com.veyro.p2p.desktopinterop

import android.app.Application
import android.os.Build
import com.veyro.p2p.protocol.FastChannelAnswer
import com.veyro.p2p.protocol.FastChannelOffer
import com.veyro.p2p.settings.EcosystemPreferences
import kotlinx.coroutines.CoroutineScope
import java.net.InetAddress

internal interface DesktopInteropListener {
    fun onDesktopPeersChanged(peers: List<DiscoveredDesktopPeer>)
    fun onDesktopPairingPin(verification: DesktopPairingVerification)
    fun onDesktopTrusted(peer: DesktopTrustedPeer)
    fun onDesktopConnected(peer: DesktopTrustedPeer)
    fun onDesktopDisconnected(peer: DesktopTrustedPeer)
    fun onDesktopApplicationMessage(peer: DesktopTrustedPeer, bytes: ByteArray)
    fun onDesktopStatus(message: String, error: Throwable? = null)
}

internal class DesktopInteropManager(
    application: Application,
    private val scope: CoroutineScope,
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
    private var started = false

    fun start() {
        if (started) return
        started = true
        runCatching(ble::start).onFailure {
            started = false
            listener.onDesktopStatus("O transporte do Veyro Desktop não pôde ser iniciado.", it)
        }
    }

    fun connect(endpointId: String) {
        val peer = peers.firstOrNull { it.endpointId == endpointId }
            ?: error("O Veyro Desktop não está mais visível por BLE")
        ble.connect(peer)
    }

    fun confirmPin(accepted: Boolean) = ble.confirmPin(accepted)

    fun sendApplicationMessage(bytes: ByteArray) = fastChannel
        ?.sendApplicationMessage(bytes)
        ?: error("O canal seguro com o Desktop ainda não está conectado")

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
        pendingOffer = offer
        tryOpenFastChannel()
    }

    override fun onDesktopFastChannelAnswer(answer: FastChannelAnswer) {
        listener.onDesktopStatus(
            if (answer.accepted) "O Desktop aceitou o canal rápido."
            else "O Desktop recusou o canal rápido: ${answer.reason}"
        )
    }

    override fun onDesktopBleStatus(message: String, error: Throwable?) =
        listener.onDesktopStatus(message, error)

    override fun onDesktopWifiDirectReady(groupOwnerAddress: InetAddress) {
        this.groupOwnerAddress = groupOwnerAddress
        tryOpenFastChannel()
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
        fastChannel = DesktopFastChannel(identity, peer, scope, this).also {
            it.connect(address, offer)
        }
        pendingOffer = null
    }

    override fun onDesktopFastChannelConnected(peer: DesktopTrustedPeer) = listener.onDesktopConnected(peer)

    override fun onDesktopApplicationMessage(peer: DesktopTrustedPeer, bytes: ByteArray) =
        listener.onDesktopApplicationMessage(peer, bytes)

    override fun onDesktopFastChannelStatus(message: String, error: Throwable?) =
        listener.onDesktopStatus(message, error)

    override fun onDesktopFastChannelDisconnected(peer: DesktopTrustedPeer) {
        fastChannel = null
        listener.onDesktopDisconnected(peer)
    }

    override fun close() {
        started = false
        fastChannel?.close()
        fastChannel = null
        wifiDirect.close()
        ble.close()
        peers = emptyList()
        groupOwnerAddress = null
        pendingOffer = null
    }
}
