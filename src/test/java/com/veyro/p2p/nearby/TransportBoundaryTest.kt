package com.veyro.p2p.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportBoundaryTest {
    private val android = ConnectedEndpoint(
        id = "nearby-endpoint",
        name = "Veyro - Android",
        role = ConnectionRole.DISCOVERER,
        transport = EndpointTransport.NEARBY
    )
    private val desktop = ConnectedEndpoint(
        id = "desktop:trusted-device",
        name = "Veyro Desktop",
        role = ConnectionRole.DISCOVERER,
        transport = EndpointTransport.DESKTOP
    )

    @Test
    fun `android endpoints are restricted to Google Nearby`() {
        assertEquals(ConnectionDataPlane.GOOGLE_NEARBY, TransportBoundary.dataPlaneFor(android))
        assertTrue(TransportBoundary.hasNearbySession(listOf(android)))
        assertEquals(listOf(android), TransportBoundary.nearbySessions(listOf(android, desktop)))
    }

    @Test
    fun `desktop endpoints are restricted to Wi-Fi fast channel`() {
        assertEquals(ConnectionDataPlane.DESKTOP_WIFI, TransportBoundary.dataPlaneFor(desktop))
        assertFalse(TransportBoundary.hasNearbySession(listOf(desktop)))
        assertEquals(listOf(desktop), TransportBoundary.desktopWifiSessions(listOf(android, desktop)))
    }

    @Test
    fun `Desktop star suspends Nearby and its loss enables the Android fallback`() {
        assertFalse(TransportBoundary.shouldRunNearbyFallback(listOf(desktop)))
        assertFalse(TransportBoundary.shouldRunNearbyFallback(listOf(android, desktop)))
        assertTrue(TransportBoundary.shouldRunNearbyFallback(listOf(android)))
        assertTrue(TransportBoundary.shouldRunNearbyFallback(emptyList()))
    }

    @Test
    fun `unknown endpoint is never silently routed to either transport`() {
        assertNull(TransportBoundary.connectedEndpoint("missing", listOf(android, desktop)))
    }
}
