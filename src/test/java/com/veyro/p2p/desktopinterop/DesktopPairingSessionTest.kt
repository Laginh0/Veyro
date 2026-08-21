package com.veyro.p2p.desktopinterop

import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class DesktopPairingSessionTest {
    @Test
    fun bothPlatformsDeriveTheSamePinAndRequireBilateralConfirmation() {
        val first = identity("0123456789abcdef", "Android")
        val second = identity("fedcba9876543210", "Windows")
        DesktopPairingSession(first, 0xFF, PAIRING_ID, FIXED_TIME).use { android ->
            DesktopPairingSession(second, 0xFF, PAIRING_ID, FIXED_TIME).use { windows ->
                val androidVerification = android.acceptRemoteHello(windows.localHello, FIXED_TIME)
                val windowsVerification = windows.acceptRemoteHello(android.localHello, FIXED_TIME)

                assertEquals(androidVerification.pin, windowsVerification.pin)
                android.acceptRemoteConfirmation(windows.createConfirmation(true))
                windows.acceptRemoteConfirmation(android.createConfirmation(true))

                assertTrue(android.isMutuallyConfirmed)
                assertTrue(windows.isMutuallyConfirmed)
                assertEquals(second.keyPair.public.encoded.toList(), android.trustedPeer().identityPublicKeySpki.toList())
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun tamperedHelloSignatureIsRejected() {
        val first = identity("0123456789abcdef", "Android")
        val second = identity("fedcba9876543210", "Windows")
        DesktopPairingSession(first, 0xFF, PAIRING_ID, FIXED_TIME).use { android ->
            DesktopPairingSession(second, 0xFF, PAIRING_ID, FIXED_TIME).use { windows ->
                val signature = windows.localHello.signature.toByteArray().also {
                    it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
                }
                android.acceptRemoteHello(
                    windows.localHello.toBuilder().setSignature(ByteString.copyFrom(signature)).build(),
                    FIXED_TIME
                )
            }
        }
    }

    @Test
    fun p1363SignatureIsAlwaysFixedWidth() {
        val identity = identity("0123456789abcdef", "Android")
        val payload = "cross-platform".toByteArray()
        val signature = DesktopInteropProtocol.signP1363(identity.keyPair.private, payload)

        assertEquals(64, signature.size)
        assertTrue(DesktopInteropProtocol.verifyP1363(identity.keyPair.public, payload, signature))
        assertFalse(DesktopInteropProtocol.verifyP1363(identity.keyPair.public, payload + 0.toByte(), signature))
    }

    private fun identity(deviceId: String, name: String): DesktopIdentity {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return DesktopIdentity(deviceId, name, generator.generateKeyPair())
    }

    private companion object {
        const val PAIRING_ID = "00112233445566778899aabbccddeeff"
        const val FIXED_TIME = 1_800_000_000_000L
    }
}
