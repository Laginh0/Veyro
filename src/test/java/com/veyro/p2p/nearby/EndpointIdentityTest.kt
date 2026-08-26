package com.veyro.p2p.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointIdentityTest {
    private val fingerprint = "0123456789abcdef"

    @Test
    fun identityRoundTripsThroughEndpointName() {
        val identity = EndpointIdentity("a1b2c3d4", 731, fingerprint, "Veyro - Telefone ç")
        assertEquals(identity, EndpointIdentity.parse(identity.toWireName()))
    }

    @Test
    fun lowerCapacityInitiatesTowardHub() {
        val lower = EndpointIdentity("aaaaaa11", 300, fingerprint, "Baixo")
        val higher = EndpointIdentity("bbbbbb22", 800, fingerprint, "Alto")
        assertTrue(EndpointIdentity.shouldInitiate(lower, higher))
        assertFalse(EndpointIdentity.shouldInitiate(higher, lower))
    }

    @Test
    fun deviceIdBreaksCapacityTieInOnlyOneDirection() {
        val first = EndpointIdentity("aaaaaa11", 500, fingerprint, "A")
        val second = EndpointIdentity("bbbbbb22", 500, fingerprint, "B")
        assertTrue(EndpointIdentity.shouldInitiate(first, second))
        assertFalse(EndpointIdentity.shouldInitiate(second, first))
    }

    @Test
    fun jitterIsStableAndWithinCollisionWindow() {
        val first = EndpointIdentity.deterministicJitterMillis("aaaaaa11", "bbbbbb22")
        val second = EndpointIdentity.deterministicJitterMillis("aaaaaa11", "bbbbbb22")
        assertEquals(first, second)
        assertTrue(first in 100L..300L)
    }

    @Test
    fun invalidWireNameIsRejected() {
        assertNull(EndpointIdentity.parse("Veyro - aparelho antigo"))
    }

    @Test
    fun wireNameStaysInsideNearbyByteLimitWithUnicodeModelNames() {
        val identity = EndpointIdentity(
            deviceId = "abcdef12",
            capacityScore = 800,
            identityKeyFingerprint = fingerprint,
            displayName = "Veyro - " + "📱 aparelho muito longo ".repeat(20)
        )

        val wireName = identity.toWireName()

        assertTrue(wireName.toByteArray(Charsets.UTF_8).size <= 131)
        assertEquals("abcdef12", EndpointIdentity.parse(wireName)?.deviceId)
    }
}
