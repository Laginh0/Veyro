package com.veyro.p2p.nearby

import com.google.protobuf.ByteString
import com.veyro.p2p.desktopinterop.DesktopIdentity
import com.veyro.p2p.desktopinterop.DesktopInteropProtocol
import com.veyro.p2p.desktopinterop.DesktopTrustedPeer
import com.veyro.p2p.protocol.NearbyIdentityClaim
import com.veyro.p2p.protocol.NearbySecurityPacket
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

internal object NearbyIdentitySecurity {
    private val claimDomain = "Veyro.NearbyIdentityClaim.v1".toByteArray(StandardCharsets.UTF_8)
    private val packetMagic = "VYRN1".toByteArray(StandardCharsets.US_ASCII)

    fun fingerprint(publicKeySpki: ByteArray): String = DesktopInteropProtocol.sha256(publicKeySpki)
        .take(8)
        .joinToString("") { "%02x".format(it) }

    fun createClaim(identity: DesktopIdentity, authenticationDigits: String): NearbyIdentityClaim {
        require(authenticationDigits.matches(Regex("[0-9]{4,6}")))
        val builder = NearbyIdentityClaim.newBuilder()
            .setProtocolMajor(DesktopInteropProtocol.protocolMajor)
            .setDeviceId(identity.deviceId)
            .setDisplayName(identity.displayName)
            .setIdentityPublicKeySpki(ByteString.copyFrom(identity.keyPair.public.encoded))
            .setNonce(ByteString.copyFrom(ByteArray(32).also(SecureRandom()::nextBytes)))
        val unsigned = builder.build()
        return builder.setSignature(
            ByteString.copyFrom(
                DesktopInteropProtocol.signP1363(
                    identity.keyPair.private,
                    canonicalClaim(unsigned, authenticationDigits)
                )
            )
        ).build()
    }

    fun verifyClaim(
        claim: NearbyIdentityClaim,
        advertisedIdentity: EndpointIdentity,
        authenticationDigits: String
    ): DesktopTrustedPeer? = runCatching {
        require(claim.protocolMajor == DesktopInteropProtocol.protocolMajor)
        require(claim.deviceId == advertisedIdentity.deviceId)
        require(claim.displayName.isNotBlank() && claim.displayName.length <= 80)
        require(claim.identityPublicKeySpki.size() in 65..256)
        require(claim.nonce.size() == 32 && claim.signature.size() == 64)
        val publicKey = claim.identityPublicKeySpki.toByteArray()
        require(fingerprint(publicKey) == advertisedIdentity.identityKeyFingerprint)
        require(
            DesktopInteropProtocol.verifyP1363(
                DesktopInteropProtocol.publicKey(publicKey),
                canonicalClaim(claim.toBuilder().clearSignature().build(), authenticationDigits),
                claim.signature.toByteArray()
            )
        )
        val now = System.currentTimeMillis()
        DesktopTrustedPeer(claim.deviceId, claim.displayName, publicKey, 0, now, now, null)
    }.getOrNull()

    fun wrapClaim(claim: NearbyIdentityClaim): ByteArray = NearbySecurityPacket.newBuilder()
        .setMagic(ByteString.copyFrom(packetMagic))
        .setIdentityClaim(claim)
        .build()
        .toByteArray()

    fun wrapEnvelope(envelope: com.veyro.p2p.protocol.TransportEnvelope): ByteArray =
        NearbySecurityPacket.newBuilder()
            .setMagic(ByteString.copyFrom(packetMagic))
            .setTransportEnvelope(envelope)
            .build()
            .toByteArray()

    fun parsePacket(bytes: ByteArray): NearbySecurityPacket? = runCatching {
        NearbySecurityPacket.parseFrom(bytes)
    }.getOrNull()?.takeIf { MessageDigest.isEqual(it.magic.toByteArray(), packetMagic) }

    private fun canonicalClaim(claim: NearbyIdentityClaim, authenticationDigits: String): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(claimDomain)
                output.writeInt(claim.protocolMajor)
                output.writeLengthPrefixed(claim.deviceId.toByteArray(StandardCharsets.UTF_8))
                output.writeLengthPrefixed(claim.displayName.toByteArray(StandardCharsets.UTF_8))
                output.writeLengthPrefixed(claim.identityPublicKeySpki.toByteArray())
                output.writeLengthPrefixed(claim.nonce.toByteArray())
                output.writeLengthPrefixed(authenticationDigits.toByteArray(StandardCharsets.US_ASCII))
            }
            buffer.toByteArray()
        }

    private fun DataOutputStream.writeLengthPrefixed(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }
}
