package com.veyro.p2p.desktopinterop

import com.veyro.p2p.protocol.FastChannelOffer
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

internal object DesktopInteropProtocol {
    val serviceUuid: UUID = UUID.fromString("68d0925e-266d-4ca5-9588-9c804c6cd8ff")
    val controlCharacteristicUuid: UUID = UUID.fromString("886c164a-9f9f-465f-9428-8fb7ee8cd15a")
    val clientConfigurationUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val protocolMajor: Int = 1
    const val protocolMinor: Int = 0
    const val alpn: String = "veyro/1"
    const val maximumBlePacketSize: Int = 512
    const val maximumFramePayloadSize: Int = 1024 * 1024
    const val capabilityMask: Int = 0xFF
    const val desktopEndpointPrefix: String = "desktop-ble:"

    private val frameMagic = byteArrayOf('V'.code.toByte(), 'Y'.code.toByte(), 'R'.code.toByte(), 'O'.code.toByte())

    fun encodeAdvertisement(ephemeralId: ByteArray): ByteArray {
        require(ephemeralId.size == 6)
        return byteArrayOf(protocolMajor.toByte(), capabilityMask.toByte()) + ephemeralId
    }

    fun decodeAdvertisement(bytes: ByteArray?): DesktopBleAdvertisement? {
        if (bytes == null || bytes.size != 8 || bytes[0].toInt() == 0) return null
        return DesktopBleAdvertisement(
            protocolMajor = bytes[0].toInt() and 0xFF,
            capabilities = bytes[1].toInt() and 0xFF,
            ephemeralId = bytes.copyOfRange(2, 8).toHex()
        )
    }

    fun reconnectPayload(deviceId: String, challenge: ByteArray): ByteArray =
        "Veyro.ReconnectChallenge.v1".toByteArray() + deviceId.toByteArray() + challenge

    fun fastChannelOfferPayload(offer: FastChannelOffer): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.write("Veyro.FastChannelOffer.v1".toByteArray())
            output.writeLengthPrefixed(offer.sessionId.toByteArray())
            output.writeLengthPrefixed(offer.deviceId.toByteArray())
            output.writeInt(offer.roleValue)
            output.writeInt(offer.tcpPort)
            output.writeLengthPrefixed(offer.tlsAlpn.toByteArray())
            output.writeLengthPrefixed(offer.resumeToken.toByteArray())
        }
        buffer.toByteArray()
    }

    fun publicKey(spki: ByteArray): PublicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(spki))

    fun signP1363(privateKey: java.security.PrivateKey, payload: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(payload)
        return derToP1363(signer.sign())
    }

    fun verifyP1363(publicKey: PublicKey, payload: ByteArray, signature: ByteArray): Boolean =
        runCatching {
            if (signature.size != 64) return false
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(payload)
            verifier.verify(p1363ToDer(signature))
        }.getOrDefault(false)

    fun writeFrame(output: OutputStream, payload: ByteArray, flags: Int = 0) {
        require(payload.size <= maximumFramePayloadSize)
        synchronized(output) {
            val data = DataOutputStream(output)
            data.write(frameMagic)
            data.writeByte(1)
            data.writeByte(flags and 0xFF)
            data.writeShort(0)
            data.writeInt(payload.size)
            data.write(payload)
            data.flush()
        }
    }

    fun readFrame(input: InputStream): ByteArray? {
        val data = DataInputStream(input)
        val first = data.read()
        if (first < 0) return null
        val header = ByteArray(12)
        header[0] = first.toByte()
        try {
            data.readFully(header, 1, header.size - 1)
        } catch (_: EOFException) {
            throw EOFException("Truncated VYRO frame header")
        }
        require(header.copyOfRange(0, 4).contentEquals(frameMagic)) { "Invalid VYRO frame magic" }
        require(header[4].toInt() == 1) { "Unsupported VYRO frame version" }
        require(header[6].toInt() == 0 && header[7].toInt() == 0) { "Reserved frame bytes are not zero" }
        val length = ByteBuffer.wrap(header, 8, 4).order(ByteOrder.BIG_ENDIAN).int
        require(length in 0..maximumFramePayloadSize) { "Invalid VYRO frame payload length" }
        return ByteArray(length).also(data::readFully)
    }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun DataOutputStream.writeLengthPrefixed(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun derToP1363(der: ByteArray): ByteArray {
        var offset = 0
        require(der[offset++].toInt() == 0x30)
        val sequenceLength = readDerLength(der, offset)
        offset = sequenceLength.second
        require(sequenceLength.first == der.size - offset)
        require(der[offset++].toInt() == 0x02)
        val rLength = readDerLength(der, offset)
        offset = rLength.second
        val r = der.copyOfRange(offset, offset + rLength.first)
        offset += rLength.first
        require(der[offset++].toInt() == 0x02)
        val sLength = readDerLength(der, offset)
        offset = sLength.second
        val s = der.copyOfRange(offset, offset + sLength.first)
        return normalizeInteger(r) + normalizeInteger(s)
    }

    private fun p1363ToDer(signature: ByteArray): ByteArray {
        require(signature.size == 64)
        val r = encodePositiveInteger(signature.copyOfRange(0, 32))
        val s = encodePositiveInteger(signature.copyOfRange(32, 64))
        val body = byteArrayOf(0x02) + encodeDerLength(r.size) + r +
            byteArrayOf(0x02) + encodeDerLength(s.size) + s
        return byteArrayOf(0x30) + encodeDerLength(body.size) + body
    }

    private fun normalizeInteger(value: ByteArray): ByteArray {
        val unsigned = value.dropWhile { it == 0.toByte() }.toByteArray()
        require(unsigned.size <= 32)
        return ByteArray(32 - unsigned.size) + unsigned
    }

    private fun encodePositiveInteger(value: ByteArray): ByteArray {
        val stripped = value.dropWhile { it == 0.toByte() }.toByteArray()
        val trimmed = if (stripped.isEmpty()) byteArrayOf(0) else stripped
        return if ((trimmed[0].toInt() and 0x80) != 0) byteArrayOf(0) + trimmed else trimmed
    }

    private fun readDerLength(bytes: ByteArray, start: Int): Pair<Int, Int> {
        val first = bytes[start].toInt() and 0xFF
        if (first < 0x80) return first to start + 1
        val count = first and 0x7F
        require(count in 1..2)
        var value = 0
        repeat(count) { index -> value = (value shl 8) or (bytes[start + 1 + index].toInt() and 0xFF) }
        return value to start + 1 + count
    }

    private fun encodeDerLength(length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(length.toByte())
        length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
        else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), length.toByte())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

internal data class DesktopBleAdvertisement(
    val protocolMajor: Int,
    val capabilities: Int,
    val ephemeralId: String
)

internal data class DiscoveredDesktopPeer(
    val endpointId: String,
    val address: String,
    val ephemeralId: String,
    val capabilities: Int,
    val rssi: Int,
    val observedAtMillis: Long
)
