package com.veyro.p2p.nearby

import com.veyro.p2p.desktopinterop.DesktopIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class NearbyIdentitySecurityTest {
    @Test
    fun `claim proves the advertised persistent key on this Nearby connection`() {
        val identity = identity("android-a", "Android A")
        val advertised = endpoint(identity)
        val claim = NearbyIdentitySecurity.createClaim(identity, "1234")

        val verified = NearbyIdentitySecurity.verifyClaim(claim, advertised, "1234")

        assertNotNull(verified)
        assertEquals(identity.deviceId, verified?.deviceId)
    }

    @Test
    fun `captured claim cannot be replayed on a connection with different auth digits`() {
        val identity = identity("android-a", "Android A")
        val claim = NearbyIdentitySecurity.createClaim(identity, "1234")

        assertNull(NearbyIdentitySecurity.verifyClaim(claim, endpoint(identity), "5678"))
    }

    @Test
    fun `same device id with a different advertised key is rejected`() {
        val legitimate = identity("android-b", "Android B")
        val attacker = identity("android-b", "Attacker")
        val attackerClaim = NearbyIdentitySecurity.createClaim(attacker, "1234")

        assertNull(NearbyIdentitySecurity.verifyClaim(attackerClaim, endpoint(legitimate), "1234"))
    }

    @Test
    fun `raw feature protobuf is not a Nearby security packet`() {
        assertNull(NearbyIdentitySecurity.parsePacket("veyro.features.v1".toByteArray()))
    }

    private fun identity(deviceId: String, name: String): DesktopIdentity {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return DesktopIdentity(deviceId, name, generator.generateKeyPair())
    }

    private fun endpoint(identity: DesktopIdentity) = EndpointIdentity(
        identity.deviceId,
        500,
        NearbyIdentitySecurity.fingerprint(identity.keyPair.public.encoded),
        identity.displayName
    )
}
