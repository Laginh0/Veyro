package com.veyro.p2p.nearby

/**
 * Defines the non-negotiable data-plane boundary used by Veyro.
 *
 * Without a Desktop hub, Android peers exchange application data through Google Nearby.
 * While an authenticated Desktop hub is active, Android peers are represented as routed
 * DESKTOP endpoints and exchange end-to-end encrypted data through its Wi-Fi fast channels.
 * BLE may announce a Desktop peer and bootstrap trust, but it is never an application-data plane.
 */
internal enum class ConnectionDataPlane {
    GOOGLE_NEARBY,
    DESKTOP_WIFI
}

internal object TransportBoundary {
    fun dataPlaneFor(endpoint: ConnectedEndpoint): ConnectionDataPlane = when (endpoint.transport) {
        EndpointTransport.NEARBY -> ConnectionDataPlane.GOOGLE_NEARBY
        EndpointTransport.DESKTOP -> ConnectionDataPlane.DESKTOP_WIFI
    }

    fun connectedEndpoint(
        endpointId: String,
        endpoints: List<ConnectedEndpoint>
    ): ConnectedEndpoint? = endpoints.firstOrNull { it.id == endpointId }

    fun hasNearbySession(endpoints: List<ConnectedEndpoint>): Boolean =
        endpoints.any { dataPlaneFor(it) == ConnectionDataPlane.GOOGLE_NEARBY }

    fun hasDesktopWifiSession(endpoints: List<ConnectedEndpoint>): Boolean =
        endpoints.any { dataPlaneFor(it) == ConnectionDataPlane.DESKTOP_WIFI }

    fun shouldRunNearbyFallback(endpoints: List<ConnectedEndpoint>): Boolean =
        !hasDesktopWifiSession(endpoints)

    fun nearbySessions(endpoints: List<ConnectedEndpoint>): List<ConnectedEndpoint> =
        endpoints.filter { dataPlaneFor(it) == ConnectionDataPlane.GOOGLE_NEARBY }

    fun desktopWifiSessions(endpoints: List<ConnectedEndpoint>): List<ConnectedEndpoint> =
        endpoints.filter { dataPlaneFor(it) == ConnectionDataPlane.DESKTOP_WIFI }
}
