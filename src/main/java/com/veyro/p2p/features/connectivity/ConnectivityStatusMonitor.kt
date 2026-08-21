package com.veyro.p2p.features.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.veyro.p2p.protocol.ConnectivityStatus
import com.veyro.p2p.protocol.NetworkTransport
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

class ConnectivityStatusMonitor(context: Context) {
    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @OptIn(FlowPreview::class)
    fun statusUpdates(): Flow<ConnectivityStatus> = callbackFlow {
        fun publishSnapshot() {
            trySend(currentStatus())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publishSnapshot()

            override fun onLost(network: Network) = publishSnapshot()

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = publishSnapshot()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
        }
        publishSnapshot()
        awaitClose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }
        .distinctUntilChanged { old, new ->
            old.activeTransport == new.activeTransport &&
                old.hasInternet == new.hasInternet &&
                old.isMetered == new.isMetered &&
                old.hasSignalStrength == new.hasSignalStrength &&
                old.signalStrengthDbm == new.signalStrengthDbm
        }
        .debounce(CONNECTIVITY_DEBOUNCE_MILLIS)

    private fun currentStatus(): ConnectivityStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return legacyStatus()

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        val signalStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities?.signalStrength?.takeUnless { it == Int.MIN_VALUE }
        } else {
            null
        }

        return ConnectivityStatus.newBuilder()
            .setActiveTransport(capabilities.toTransport())
            .setHasInternet(
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            )
            .setIsMetered(network != null && connectivityManager.isActiveNetworkMetered)
            .setHasSignalStrength(signalStrength != null)
            .setSignalStrengthDbm(signalStrength ?: 0)
            .setEventTimestamp(System.currentTimeMillis())
            .build()
    }

    @Suppress("DEPRECATION")
    private fun legacyStatus(): ConnectivityStatus {
        val info = connectivityManager.activeNetworkInfo
        val transport = when (info?.type) {
            ConnectivityManager.TYPE_WIFI -> NetworkTransport.NETWORK_TRANSPORT_WIFI
            ConnectivityManager.TYPE_MOBILE -> NetworkTransport.NETWORK_TRANSPORT_CELLULAR
            ConnectivityManager.TYPE_ETHERNET -> NetworkTransport.NETWORK_TRANSPORT_ETHERNET
            ConnectivityManager.TYPE_BLUETOOTH -> NetworkTransport.NETWORK_TRANSPORT_BLUETOOTH
            ConnectivityManager.TYPE_VPN -> NetworkTransport.NETWORK_TRANSPORT_VPN
            null -> NetworkTransport.NETWORK_TRANSPORT_NONE
            else -> NetworkTransport.NETWORK_TRANSPORT_OTHER
        }
        return ConnectivityStatus.newBuilder()
            .setActiveTransport(transport)
            .setHasInternet(info?.isConnected == true)
            .setIsMetered(connectivityManager.isActiveNetworkMetered)
            .setEventTimestamp(System.currentTimeMillis())
            .build()
    }

    private fun NetworkCapabilities?.toTransport(): NetworkTransport = when {
        this == null -> NetworkTransport.NETWORK_TRANSPORT_NONE
        hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.NETWORK_TRANSPORT_VPN
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.NETWORK_TRANSPORT_WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            NetworkTransport.NETWORK_TRANSPORT_CELLULAR
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            NetworkTransport.NETWORK_TRANSPORT_ETHERNET
        hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ->
            NetworkTransport.NETWORK_TRANSPORT_BLUETOOTH
        else -> NetworkTransport.NETWORK_TRANSPORT_OTHER
    }

    private companion object {
        const val CONNECTIVITY_DEBOUNCE_MILLIS = 1_500L
    }
}
