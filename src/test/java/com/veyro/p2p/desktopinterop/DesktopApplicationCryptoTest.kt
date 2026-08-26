package com.veyro.p2p.desktopinterop

import com.google.protobuf.ByteString
import com.veyro.p2p.protocol.TransportEnvelope
import com.veyro.p2p.protocol.TransportPayloadType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class DesktopApplicationCryptoTest {
    @Test
    fun payloadIsEncryptedForRecipientAndRoundTrips() {
        val origin = identity("android", "Android")
        val destination = identity("desktop", "Desktop")
        val trustedDestination = trusted(destination)
        val plaintext = "conteúdo protegido".toByteArray()

        val encrypted = DesktopApplicationCrypto.encrypt(plaintext, origin, trustedDestination)
        val decrypted = DesktopApplicationCrypto.decrypt(encrypted, origin.deviceId, destination)

        assertFalse(encrypted.contentEquals(plaintext))
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `Android routed payload remains opaque to the Desktop mediator`() {
        val origin = identity("android-a", "Android A")
        val destination = identity("android-b", "Android B")
        val desktop = identity("desktop", "Desktop")
        val plaintext = "somente os Androids podem ler".toByteArray()

        val encrypted = DesktopApplicationCrypto.encrypt(plaintext, origin, trusted(destination))

        assertArrayEquals(
            plaintext,
            DesktopApplicationCrypto.decrypt(encrypted, origin.deviceId, destination)
        )
        assertThrows(IllegalStateException::class.java) {
            DesktopApplicationCrypto.decrypt(encrypted, origin.deviceId, desktop)
        }
    }

    @Test
    fun signatureCoversEveryImmutableEnvelopeField() {
        val origin = identity("android", "Android")
        val encrypted = ByteString.copyFrom(byteArrayOf(1, 2, 3, 4))
        val builder = TransportEnvelope.newBuilder()
            .setProtocolMajor(1)
            .setProtocolMinor(0)
            .setMessageId("message-1")
            .setOriginDeviceId(origin.deviceId)
            .addDestinationDeviceIds("desktop")
            .setPayloadType(TransportPayloadType.APPLICATION_MESSAGE)
            .setCreatedAtUnixMs(10)
            .setExpiresAtUnixMs(20)
            .setRemainingHops(8)
            .setSequenceNumber(1)
            .setSenderEpoch(1)
            .setEncryptedPayload(encrypted)
        val unsigned = builder.build()
        val signed = builder
            .setOriginAuthentication(ByteString.copyFrom(DesktopApplicationCrypto.sign(unsigned, origin)))
            .build()

        assertTrue(DesktopApplicationCrypto.verify(signed, trusted(origin)))
        assertFalse(DesktopApplicationCrypto.verify(signed.toBuilder().setSequenceNumber(2).build(), trusted(origin)))
        assertFalse(DesktopApplicationCrypto.verify(signed.toBuilder().setSenderEpoch(2).build(), trusted(origin)))
        assertFalse(DesktopApplicationCrypto.verify(signed.toBuilder().setEncryptedPayload(ByteString.copyFromUtf8("changed")).build(), trusted(origin)))
    }

    private fun identity(deviceId: String, name: String): DesktopIdentity {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return DesktopIdentity(deviceId, name, generator.generateKeyPair())
    }

    private fun trusted(identity: DesktopIdentity) = DesktopTrustedPeer(
        deviceId = identity.deviceId,
        displayName = identity.displayName,
        identityPublicKeySpki = identity.keyPair.public.encoded,
        capabilities = 0xFF,
        trustedAtMillis = 1,
        lastSeenAtMillis = 1,
        revokedAtMillis = null
    )
}
