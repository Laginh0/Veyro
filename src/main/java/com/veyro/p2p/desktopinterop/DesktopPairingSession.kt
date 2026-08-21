package com.veyro.p2p.desktopinterop

import com.google.protobuf.ByteString
import com.veyro.p2p.protocol.PairingConfirmation
import com.veyro.p2p.protocol.PairingHello
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class DesktopPairingVerification(
    val pin: String,
    val remoteDeviceId: String,
    val remoteDisplayName: String
)

internal class DesktopPairingSession(
    private val localIdentity: DesktopIdentity,
    private val capabilities: Int,
    pairingId: String,
    createdAtMillis: Long = System.currentTimeMillis(),
    private val secureRandom: SecureRandom = SecureRandom()
) : AutoCloseable {
    private val ephemeralKeyPair: KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        generateKeyPair()
    }
    private var remoteHello: PairingHello? = null
    private var verificationDigest: ByteArray? = null
    private var localAccepted = false
    private var remoteAccepted = false

    val localHello: PairingHello = PairingHello.newBuilder()
        .setPairingId(pairingId)
        .setDeviceId(localIdentity.deviceId)
        .setDisplayName(localIdentity.displayName)
        .setCapabilities(capabilities)
        .setCreatedAtUnixMs(createdAtMillis)
        .setNonce(ByteString.copyFrom(ByteArray(32).also(secureRandom::nextBytes)))
        .setIdentityPublicKeySpki(ByteString.copyFrom(localIdentity.keyPair.public.encoded))
        .setEphemeralPublicKeySpki(ByteString.copyFrom(ephemeralKeyPair.public.encoded))
        .build()
        .let { unsigned ->
            unsigned.toBuilder()
                .setSignature(ByteString.copyFrom(signHello(unsigned)))
                .build()
        }

    val isMutuallyConfirmed: Boolean
        get() = localAccepted && remoteAccepted

    fun acceptRemoteHello(remote: PairingHello, nowMillis: Long = System.currentTimeMillis()): DesktopPairingVerification {
        require(localHello.pairingId == remote.pairingId) { "Pairing session IDs do not match" }
        require(localHello.deviceId != remote.deviceId) { "A device cannot pair with itself" }
        require(kotlin.math.abs(nowMillis - remote.createdAtUnixMs) <= MAXIMUM_CLOCK_SKEW_MILLIS) {
            "Pairing hello is outside the accepted time window"
        }
        require(remote.nonce.size() == 32 && remote.identityPublicKeySpki.size() > 0 && remote.ephemeralPublicKeySpki.size() > 0) {
            "Pairing hello contains invalid key material"
        }
        val identityPublicKey = DesktopInteropProtocol.publicKey(remote.identityPublicKeySpki.toByteArray())
        require(
            DesktopInteropProtocol.verifyP1363(
                identityPublicKey,
                encodeHello(remote),
                remote.signature.toByteArray()
            )
        ) { "Pairing hello signature is invalid" }

        val remoteEphemeral = DesktopInteropProtocol.publicKey(remote.ephemeralPublicKeySpki.toByteArray())
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(ephemeralKeyPair.private)
        agreement.doPhase(remoteEphemeral, true)
        val rawSecret = agreement.generateSecret()
        val derivedSecret = DesktopInteropProtocol.sha256(rawSecret)
        rawSecret.fill(0)
        val transcript = listOf(localHello, remote)
            .sortedBy(PairingHello::getDeviceId)
            .map(::encodeHello)
            .reduce(ByteArray::plus)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(derivedSecret, "HmacSHA256"))
        val digest = mac.doFinal(VERIFICATION_LABEL + transcript)
        derivedSecret.fill(0)
        verificationDigest?.fill(0)
        verificationDigest = digest
        remoteHello = remote
        val pinValue = java.nio.ByteBuffer.wrap(digest, 0, 4).int.toUInt().toLong() % 1_000_000L
        return DesktopPairingVerification(
            pin = pinValue.toString().padStart(6, '0'),
            remoteDeviceId = remote.deviceId,
            remoteDisplayName = remote.displayName
        )
    }

    fun createConfirmation(accepted: Boolean): PairingConfirmation {
        val digest = checkNotNull(verificationDigest) { "A valid remote hello is required first" }
        localAccepted = accepted
        val unsigned = PairingConfirmation.newBuilder()
            .setPairingId(localHello.pairingId)
            .setAccepted(accepted)
            .setVerificationDigest(ByteString.copyFrom(digest))
            .build()
        return unsigned.toBuilder()
            .setSignature(ByteString.copyFrom(signConfirmation(unsigned)))
            .build()
    }

    fun acceptRemoteConfirmation(confirmation: PairingConfirmation) {
        val digest = checkNotNull(verificationDigest) { "A valid remote hello is required first" }
        val remote = checkNotNull(remoteHello)
        require(confirmation.pairingId == localHello.pairingId)
        require(MessageDigest.isEqual(digest, confirmation.verificationDigest.toByteArray()))
        require(
            DesktopInteropProtocol.verifyP1363(
                DesktopInteropProtocol.publicKey(remote.identityPublicKeySpki.toByteArray()),
                encodeConfirmation(confirmation),
                confirmation.signature.toByteArray()
            )
        ) { "Remote pairing confirmation is invalid" }
        remoteAccepted = confirmation.accepted
    }

    fun trustedPeer(): DesktopTrustedPeer {
        check(isMutuallyConfirmed) { "Both devices must confirm the verification PIN" }
        val remote = checkNotNull(remoteHello)
        val now = System.currentTimeMillis()
        return DesktopTrustedPeer(
            deviceId = remote.deviceId,
            displayName = remote.displayName,
            identityPublicKeySpki = remote.identityPublicKeySpki.toByteArray(),
            capabilities = remote.capabilities,
            trustedAtMillis = now,
            lastSeenAtMillis = now,
            revokedAtMillis = null
        )
    }

    private fun signHello(hello: PairingHello): ByteArray = DesktopInteropProtocol.signP1363(
        localIdentity.keyPair.private,
        encodeHello(hello)
    )

    private fun signConfirmation(confirmation: PairingConfirmation): ByteArray =
        DesktopInteropProtocol.signP1363(localIdentity.keyPair.private, encodeConfirmation(confirmation))

    override fun close() {
        verificationDigest?.fill(0)
        verificationDigest = null
    }

    companion object {
        private const val MAXIMUM_CLOCK_SKEW_MILLIS = 120_000L
        private val HELLO_LABEL = "Veyro.PairingHello.v1".toByteArray()
        private val CONFIRMATION_LABEL = "Veyro.PairingConfirmation.v1".toByteArray()
        private val VERIFICATION_LABEL = "Veyro.PairingVerification.v1".toByteArray()

        internal fun encodeHello(hello: PairingHello): ByteArray = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(HELLO_LABEL)
                output.writeLengthPrefixed(hello.pairingId.toByteArray())
                output.writeLengthPrefixed(hello.deviceId.toByteArray())
                output.writeLengthPrefixed(hello.displayName.toByteArray())
                output.writeByte(hello.capabilities and 0xFF)
                output.writeLong(hello.createdAtUnixMs)
                output.writeLengthPrefixed(hello.nonce.toByteArray())
                output.writeLengthPrefixed(hello.identityPublicKeySpki.toByteArray())
                output.writeLengthPrefixed(hello.ephemeralPublicKeySpki.toByteArray())
            }
            buffer.toByteArray()
        }

        internal fun encodeConfirmation(confirmation: PairingConfirmation): ByteArray =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.write(CONFIRMATION_LABEL)
                    output.writeLengthPrefixed(confirmation.pairingId.toByteArray())
                    output.writeByte(if (confirmation.accepted) 1 else 0)
                    output.writeLengthPrefixed(confirmation.verificationDigest.toByteArray())
                }
                buffer.toByteArray()
            }

        private fun DataOutputStream.writeLengthPrefixed(bytes: ByteArray) {
            writeInt(bytes.size)
            write(bytes)
        }
    }
}
