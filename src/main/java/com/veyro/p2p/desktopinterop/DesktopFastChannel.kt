package com.veyro.p2p.desktopinterop

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.protobuf.ByteString
import com.veyro.p2p.protocol.FastChannelHello
import com.veyro.p2p.protocol.FastChannelOffer
import com.veyro.p2p.protocol.FastChannelPacket
import com.veyro.p2p.protocol.KeepAlive
import com.veyro.p2p.protocol.KeepAliveAcknowledgement
import com.veyro.p2p.protocol.ResumeRequest
import com.veyro.p2p.protocol.TransportEnvelope
import com.veyro.p2p.protocol.TransportPayloadType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

internal interface DesktopFastChannelListener {
    fun onDesktopFastChannelConnected(peer: DesktopTrustedPeer)
    fun onDesktopApplicationMessage(peer: DesktopTrustedPeer, bytes: ByteArray)
    fun onDesktopFastChannelStatus(message: String, error: Throwable? = null)
    fun onDesktopFastChannelDisconnected(peer: DesktopTrustedPeer)
}

internal class DesktopFastChannel(
    private val identity: DesktopIdentity,
    private val trustedPeer: DesktopTrustedPeer,
    private val scope: CoroutineScope,
    private val listener: DesktopFastChannelListener
) : AutoCloseable {
    private val messageSequence = AtomicLong()
    private val keepAliveSequence = AtomicLong()
    private val receivedMessageIds = LinkedHashSet<String>()
    private var lastReceivedMessageSequence = 0L
    private var socket: SSLSocket? = null
    private var receiveJob: Job? = null
    private var keepAliveJob: Job? = null
    @Volatile private var connected = false
    @Volatile private var lastReceivedAt = 0L

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connect(groupOwnerAddress: InetAddress, offer: FastChannelOffer) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "O canal TLS com ALPN do Veyro Desktop requer Android 10 ou superior"
        }
        check(validateOffer(offer)) { "Oferta de canal rápido inválida ou não confiável" }
        close()
        receiveJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val localAddress = findP2pAddress(groupOwnerAddress)
                    ?: error("O Android não expôs um endereço exclusivo do enlace Wi-Fi Direct")
                val sslContext = createSslContext()
                val activeSocket = sslContext.socketFactory.createSocket() as SSLSocket
                activeSocket.bind(InetSocketAddress(localAddress, 0))
                activeSocket.enabledProtocols = activeSocket.supportedProtocols
                    .filter { it == "TLSv1.2" || it == "TLSv1.3" }
                    .toTypedArray()
                activeSocket.sslParameters = activeSocket.sslParameters.apply {
                    applicationProtocols = arrayOf(DesktopInteropProtocol.alpn)
                }
                activeSocket.soTimeout = CONNECTION_TIMEOUT_MILLIS.toInt()
                activeSocket.connect(
                    InetSocketAddress(groupOwnerAddress, offer.tcpPort),
                    CONNECTION_TIMEOUT_MILLIS.toInt()
                )
                activeSocket.startHandshake()
                check(activeSocket.applicationProtocol == DesktopInteropProtocol.alpn) {
                    "O Desktop não negociou o ALPN Veyro"
                }
                socket = activeSocket
                performHandshake(activeSocket, offer)
                connected = true
                lastReceivedAt = System.currentTimeMillis()
                listener.onDesktopFastChannelConnected(trustedPeer)
                listener.onDesktopFastChannelStatus("Canal seguro ativo com ${trustedPeer.displayName}.")
                startKeepAlive()
                receiveLoop(activeSocket)
            }.onFailure { error ->
                if (connected || socket != null) {
                    listener.onDesktopFastChannelStatus("O canal seguro com o Desktop foi interrompido.", error)
                } else {
                    listener.onDesktopFastChannelStatus("Não foi possível abrir o canal seguro com o Desktop.", error)
                }
            }
            val wasConnected = connected
            connected = false
            socket?.runCatching { close() }
            socket = null
            keepAliveJob?.cancel()
            if (wasConnected) listener.onDesktopFastChannelDisconnected(trustedPeer)
        }
    }

    fun sendApplicationMessage(bytes: ByteArray) {
        require(bytes.isNotEmpty() && bytes.size <= MAXIMUM_APPLICATION_PAYLOAD_SIZE)
        val activeSocket = checkNotNull(socket) { "O canal com o Desktop não está conectado" }
        check(connected)
        val now = System.currentTimeMillis()
        val encrypted = DesktopApplicationCrypto.encrypt(bytes, identity, trustedPeer)
        val builder = TransportEnvelope.newBuilder()
            .setProtocolMajor(DesktopInteropProtocol.protocolMajor)
            .setProtocolMinor(DesktopInteropProtocol.protocolMinor)
            .setMessageId(UUID.randomUUID().toString())
            .setOriginDeviceId(identity.deviceId)
            .addDestinationDeviceIds(trustedPeer.deviceId)
            .setPayloadType(TransportPayloadType.APPLICATION_MESSAGE)
            .setCreatedAtUnixMs(now)
            .setExpiresAtUnixMs(now + MESSAGE_VALIDITY_MILLIS)
            .setRemainingHops(8)
            .setSequenceNumber(messageSequence.incrementAndGet())
            .setEncryptedPayload(ByteString.copyFrom(encrypted))
        val unsignedEnvelope = builder.build()
        val envelope = builder
            .setOriginAuthentication(ByteString.copyFrom(DesktopApplicationCrypto.sign(unsignedEnvelope, identity)))
            .build()
        sendPacket(activeSocket, FastChannelPacket.newBuilder().setTransportEnvelope(envelope).build())
    }

    private fun performHandshake(activeSocket: SSLSocket, offer: FastChannelOffer) {
        sendPacket(
            activeSocket,
            FastChannelPacket.newBuilder()
                .setHello(
                    FastChannelHello.newBuilder()
                        .setSessionId(offer.sessionId)
                        .setDeviceId(identity.deviceId)
                        .setProtocolMajor(DesktopInteropProtocol.protocolMajor)
                        .setProtocolMinor(DesktopInteropProtocol.protocolMinor)
                )
                .build()
        )
        val remoteHello = readPacket(activeSocket)
        check(remoteHello.bodyCase == FastChannelPacket.BodyCase.HELLO)
        check(remoteHello.hello.sessionId == offer.sessionId)
        check(remoteHello.hello.deviceId == trustedPeer.deviceId)
        check(remoteHello.hello.protocolMajor == DesktopInteropProtocol.protocolMajor)

        sendPacket(
            activeSocket,
            FastChannelPacket.newBuilder()
                .setResumeRequest(
                    ResumeRequest.newBuilder()
                        .setPreviousSessionId(offer.sessionId)
                        .setLastReceivedSequence(0)
                        .setResumeToken(offer.resumeToken)
                )
                .build()
        )
        val resume = readPacket(activeSocket)
        check(resume.bodyCase == FastChannelPacket.BodyCase.RESUME_RESPONSE)
        check(resume.resumeResponse.accepted && resume.resumeResponse.previousSessionId == offer.sessionId)
    }

    private fun receiveLoop(activeSocket: SSLSocket) {
        while (scope.isActive && connected) {
            try {
                val packet = readPacket(activeSocket)
                lastReceivedAt = System.currentTimeMillis()
                when (packet.bodyCase) {
                    FastChannelPacket.BodyCase.KEEP_ALIVE -> sendPacket(
                        activeSocket,
                        FastChannelPacket.newBuilder()
                            .setKeepAliveAcknowledgement(
                                KeepAliveAcknowledgement.newBuilder().setSequence(packet.keepAlive.sequence)
                            )
                            .build()
                    )
                    FastChannelPacket.BodyCase.KEEP_ALIVE_ACKNOWLEDGEMENT -> Unit
                    FastChannelPacket.BodyCase.TRANSPORT_ENVELOPE -> handleEnvelope(packet.transportEnvelope)
                    FastChannelPacket.BodyCase.BODY_NOT_SET -> error("Pacote de canal rápido sem conteúdo")
                    else -> Unit
                }
            } catch (_: SocketTimeoutException) {
                if (System.currentTimeMillis() - lastReceivedAt > CONNECTION_TIMEOUT_MILLIS) {
                    error("Timeout do keepalive do Veyro Desktop")
                }
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive && connected) {
                delay(KEEP_ALIVE_INTERVAL_MILLIS)
                val activeSocket = socket ?: break
                sendPacket(
                    activeSocket,
                    FastChannelPacket.newBuilder()
                        .setKeepAlive(
                            KeepAlive.newBuilder()
                                .setSequence(keepAliveSequence.incrementAndGet())
                                .setSentAtUnixMs(System.currentTimeMillis())
                        )
                        .build()
                )
            }
        }
    }

    private fun handleEnvelope(envelope: TransportEnvelope) {
        val now = System.currentTimeMillis()
        if (envelope.protocolMajor != DesktopInteropProtocol.protocolMajor ||
            envelope.originDeviceId != trustedPeer.deviceId ||
            envelope.messageId.isBlank() ||
            envelope.createdAtUnixMs > now + MAXIMUM_CLOCK_SKEW_MILLIS ||
            envelope.expiresAtUnixMs < now ||
            envelope.expiresAtUnixMs <= envelope.createdAtUnixMs ||
            envelope.remainingHops !in 1..8 ||
            envelope.sequenceNumber <= 0 ||
            envelope.payloadType != TransportPayloadType.APPLICATION_MESSAGE ||
            envelope.originAuthentication.size() != 64 ||
            envelope.encryptedPayload.isEmpty
        ) return
        val addressedToUs = identity.deviceId in envelope.destinationDeviceIdsList || envelope.authorizedBroadcast
        if (!addressedToUs || !DesktopApplicationCrypto.verify(envelope, trustedPeer)) return
        synchronized(receivedMessageIds) {
            if (envelope.messageId in receivedMessageIds || envelope.sequenceNumber <= lastReceivedMessageSequence) return
            receivedMessageIds += envelope.messageId
            while (receivedMessageIds.size > MAXIMUM_TRACKED_MESSAGE_IDS) {
                receivedMessageIds.remove(receivedMessageIds.first())
            }
            lastReceivedMessageSequence = envelope.sequenceNumber
        }
        runCatching {
            DesktopApplicationCrypto.decrypt(envelope.encryptedPayload.toByteArray(), envelope.originDeviceId, identity)
        }.onSuccess { plaintext ->
            listener.onDesktopApplicationMessage(trustedPeer, plaintext)
        }.onFailure { error ->
            listener.onDesktopFastChannelStatus("Uma mensagem do Desktop falhou na verificação criptográfica.", error)
        }
    }

    private fun validateOffer(offer: FastChannelOffer): Boolean {
        if (offer.deviceId != trustedPeer.deviceId ||
            offer.sessionId.isBlank() ||
            offer.roleValue != 1 ||
            offer.tcpPort !in 1..65535 ||
            offer.tlsAlpn != DesktopInteropProtocol.alpn ||
            offer.resumeToken.size() != 32 ||
            offer.signature.size() != 64
        ) return false
        return DesktopInteropProtocol.verifyP1363(
            DesktopInteropProtocol.publicKey(trustedPeer.identityPublicKeySpki),
            DesktopInteropProtocol.fastChannelOfferPayload(offer),
            offer.signature.toByteArray()
        )
    }

    private fun createSslContext(): SSLContext {
        val certificate = createSelfSignedCertificate()
        val keyManager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(KEY_ALIAS)
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: java.net.Socket?): String = KEY_ALIAS
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = emptyArray()
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: java.net.Socket?): String? = null
            override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(certificate)
            override fun getPrivateKey(alias: String?) = identity.keyPair.private
            override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String = KEY_ALIAS
        }
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val peerCertificate = chain?.firstOrNull() ?: error("O Desktop não apresentou certificado")
                peerCertificate.checkValidity()
                val commonName = peerCertificate.subjectX500Principal.name
                    .split(',')
                    .firstOrNull { it.trim().startsWith("CN=") }
                    ?.substringAfter("CN=")
                check(commonName == trustedPeer.deviceId) { "A identidade TLS do Desktop diverge do Trust Hub" }
                check(MessageDigest.isEqual(peerCertificate.publicKey.encoded, trustedPeer.identityPublicKeySpki)) {
                    "A chave TLS do Desktop diverge do pareamento BLE"
                }
            }
        }
        return SSLContext.getInstance("TLS").apply {
            init(arrayOf<KeyManager>(keyManager), arrayOf<TrustManager>(trustManager), SecureRandom())
        }
    }

    private fun createSelfSignedCertificate(): X509Certificate {
        val now = System.currentTimeMillis()
        val name = X500Name("CN=${identity.deviceId}")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(160, SecureRandom()).abs(),
            Date(now - 5 * 60_000L),
            Date(now + 2L * 365 * 24 * 60 * 60_000L),
            name,
            identity.keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(identity.keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer)).also {
            it.verify(identity.keyPair.public)
        }
    }

    private fun sendPacket(activeSocket: SSLSocket, packet: FastChannelPacket) {
        DesktopInteropProtocol.writeFrame(activeSocket.outputStream, packet.toByteArray())
    }

    private fun readPacket(activeSocket: SSLSocket): FastChannelPacket {
        val payload = DesktopInteropProtocol.readFrame(activeSocket.inputStream)
            ?: error("O Desktop encerrou o canal seguro")
        return FastChannelPacket.parseFrom(payload)
    }

    private fun findP2pAddress(groupOwnerAddress: InetAddress): InetAddress? {
        val ownerBytes = groupOwnerAddress.address
        return NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.name.contains("p2p", ignoreCase = true) }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { candidate ->
                val bytes = candidate.address
                bytes.size == ownerBytes.size && bytes.size == 4 &&
                    bytes[0] == ownerBytes[0] && bytes[1] == ownerBytes[1] && bytes[2] == ownerBytes[2]
            }
    }

    override fun close() {
        connected = false
        keepAliveJob?.cancel()
        receiveJob?.cancel()
        socket?.runCatching { close() }
        socket = null
    }

    private companion object {
        const val KEY_ALIAS = "veyro-mobile-identity"
        const val KEEP_ALIVE_INTERVAL_MILLIS = 5_000L
        const val CONNECTION_TIMEOUT_MILLIS = 15_000L
        const val MESSAGE_VALIDITY_MILLIS = 120_000L
        const val MAXIMUM_CLOCK_SKEW_MILLIS = 60_000L
        const val MAXIMUM_TRACKED_MESSAGE_IDS = 2_048
        const val MAXIMUM_APPLICATION_PAYLOAD_SIZE = 900 * 1024
    }
}
