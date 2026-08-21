package com.veyro.p2p.desktopinterop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DesktopInteropProtocolTest {
    @Test
    fun advertisementMatchesDesktopEightByteLayout() {
        val ephemeral = byteArrayOf(1, 2, 3, 4, 5, 6)
        val encoded = DesktopInteropProtocol.encodeAdvertisement(ephemeral)
        val decoded = DesktopInteropProtocol.decodeAdvertisement(encoded)!!

        assertEquals(8, encoded.size)
        assertEquals(1, decoded.protocolMajor)
        assertEquals(0xFF, decoded.capabilities)
        assertEquals("010203040506", decoded.ephemeralId)
        assertNull(DesktopInteropProtocol.decodeAdvertisement(encoded + 7))
    }

    @Test
    fun vyroFrameRoundTripsWithBigEndianHeader() {
        val payload = ByteArray(513) { (it and 0xFF).toByte() }
        val output = ByteArrayOutputStream()

        DesktopInteropProtocol.writeFrame(output, payload)
        val encoded = output.toByteArray()

        assertArrayEquals(byteArrayOf('V'.code.toByte(), 'Y'.code.toByte(), 'R'.code.toByte(), 'O'.code.toByte()), encoded.copyOfRange(0, 4))
        assertEquals(1, encoded[4].toInt())
        assertArrayEquals(payload, DesktopInteropProtocol.readFrame(ByteArrayInputStream(encoded)))
    }
}
