package com.veyro.p2p.desktopinterop

import com.google.protobuf.ByteString
import com.veyro.p2p.protocol.EncryptedApplicationPayload
import com.veyro.p2p.protocol.RecipientCiphertext
import com.veyro.p2p.protocol.TransportEnvelope
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object DesktopApplicationCrypto {
    private val applicationDomain = "Veyro.ApplicationPayload.v1".toByteArray(StandardCharsets.UTF_8)
    private val envelopeDomain = "Veyro.TransportEnvelope.v1".toByteArray(StandardCharsets.UTF_8)
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH = 16
    private const val KEY_LENGTH = 32

    fun encrypt(
        plaintext: ByteArray,
        identity: DesktopIdentity,
        recipient: DesktopTrustedPeer
    ): ByteArray {
        require(plaintext.isNotEmpty()) { "The application payload cannot be empty" }
        check(!recipient.isRevoked) { "A revoked device cannot receive an application payload" }

        val ephemeral = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val sharedSecret = deriveSecret(ephemeral.private, DesktopInteropProtocol.publicKey(recipient.identityPublicKeySpki))
        val key = deriveEncryptionKey(sharedSecret, identity.deviceId, recipient.deviceId)
        return try {
            val nonce = ByteArray(NONCE_LENGTH).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH * 8, nonce))
            cipher.updateAAD(associatedData(identity.deviceId, recipient.deviceId))
            val sealed = cipher.doFinal(plaintext)
            val ciphertext = sealed.copyOfRange(0, sealed.size - TAG_LENGTH)
            val tag = sealed.copyOfRange(sealed.size - TAG_LENGTH, sealed.size)
            EncryptedApplicationPayload.newBuilder()
                .setEphemeralPublicKeySpki(ByteString.copyFrom(ephemeral.public.encoded))
                .addRecipients(
                    RecipientCiphertext.newBuilder()
                        .setDestinationDeviceId(recipient.deviceId)
                        .setNonce(ByteString.copyFrom(nonce))
                        .setCiphertext(ByteString.copyFrom(ciphertext))
                        .setAuthenticationTag(ByteString.copyFrom(tag))
                )
                .build()
                .toByteArray()
        } finally {
            sharedSecret.fill(0)
            key.fill(0)
        }
    }

    fun decrypt(
        encryptedBytes: ByteArray,
        originDeviceId: String,
        identity: DesktopIdentity
    ): ByteArray {
        require(encryptedBytes.isNotEmpty()) { "The encrypted application payload cannot be empty" }
        val encrypted = EncryptedApplicationPayload.parseFrom(encryptedBytes)
        val recipient = encrypted.recipientsList.singleOrNull { it.destinationDeviceId == identity.deviceId }
            ?: error("The application payload is not addressed to this device")
        require(!encrypted.ephemeralPublicKeySpki.isEmpty &&
            recipient.nonce.size() == NONCE_LENGTH &&
            recipient.authenticationTag.size() == TAG_LENGTH &&
            !recipient.ciphertext.isEmpty
        ) { "The encrypted application payload has invalid cryptographic fields" }

        val ephemeralPublicKey = DesktopInteropProtocol.publicKey(encrypted.ephemeralPublicKeySpki.toByteArray())
        val sharedSecret = deriveSecret(identity.keyPair.private, ephemeralPublicKey)
        val key = deriveEncryptionKey(sharedSecret, originDeviceId, identity.deviceId)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_LENGTH * 8, recipient.nonce.toByteArray())
            )
            cipher.updateAAD(associatedData(originDeviceId, identity.deviceId))
            cipher.doFinal(recipient.ciphertext.toByteArray() + recipient.authenticationTag.toByteArray())
        } finally {
            sharedSecret.fill(0)
            key.fill(0)
        }
    }

    fun sign(envelope: TransportEnvelope, identity: DesktopIdentity): ByteArray =
        DesktopInteropProtocol.signP1363(identity.keyPair.private, encodeImmutableFields(envelope))

    fun verify(envelope: TransportEnvelope, peer: DesktopTrustedPeer): Boolean =
        !peer.isRevoked && envelope.originDeviceId == peer.deviceId &&
            DesktopInteropProtocol.verifyP1363(
                DesktopInteropProtocol.publicKey(peer.identityPublicKeySpki),
                encodeImmutableFields(envelope),
                envelope.originAuthentication.toByteArray()
            )

    internal fun encodeImmutableFields(envelope: TransportEnvelope): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(envelopeDomain)
                output.writeInt(envelope.protocolMajor)
                output.writeInt(envelope.protocolMinor)
                output.writeLengthPrefixed(envelope.messageId.toByteArray(StandardCharsets.UTF_8))
                output.writeLengthPrefixed(envelope.originDeviceId.toByteArray(StandardCharsets.UTF_8))
                output.writeInt(envelope.destinationDeviceIdsCount)
                envelope.destinationDeviceIdsList.forEach {
                    output.writeLengthPrefixed(it.toByteArray(StandardCharsets.UTF_8))
                }
                output.writeByte(if (envelope.authorizedBroadcast) 1 else 0)
                output.writeInt(envelope.payloadTypeValue)
                output.writeLong(envelope.createdAtUnixMs)
                output.writeLong(envelope.expiresAtUnixMs)
                output.writeLong(envelope.sequenceNumber)
                output.writeLong(envelope.senderEpoch)
                output.writeLengthPrefixed(envelope.acknowledgesMessageId.toByteArray(StandardCharsets.UTF_8))
                output.writeLengthPrefixed(envelope.encryptedPayload.toByteArray())
            }
            buffer.toByteArray()
        }

    private fun deriveSecret(privateKey: java.security.PrivateKey, publicKey: java.security.PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }

    private fun deriveEncryptionKey(secret: ByteArray, origin: String, destination: String): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(applicationDomain, "HmacSHA256"))
        val pseudorandomKey = extract.doFinal(secret)
        return try {
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(pseudorandomKey, "HmacSHA256"))
            expand.update(associatedData(origin, destination))
            expand.update(1)
            expand.doFinal().copyOf(KEY_LENGTH)
        } finally {
            pseudorandomKey.fill(0)
        }
    }

    private fun associatedData(origin: String, destination: String): ByteArray =
        "Veyro.ApplicationPayload.v1\u0000$origin\u0000$destination".toByteArray(StandardCharsets.UTF_8)

    private fun DataOutputStream.writeLengthPrefixed(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }
}
