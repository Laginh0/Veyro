package com.veyro.p2p.desktopinterop

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.InetAddress

internal interface DesktopWifiDirectListener {
    fun onDesktopWifiDirectReady(groupOwnerAddress: InetAddress)
    fun onDesktopWifiDirectStatus(message: String, error: Throwable? = null)
}

@SuppressLint("MissingPermission")
internal class DesktopWifiDirectController(
    context: Context,
    private val listener: DesktopWifiDirectListener
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(appContext, appContext.mainLooper) {
        listener.onDesktopWifiDirectStatus("O canal Wi-Fi Direct foi perdido.")
    }
    private var registered = false
    private var expectedDisplayName: String? = null
    private var connectingAddress: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        listener.onDesktopWifiDirectStatus("Ative o Wi-Fi para conectar ao Veyro Desktop.")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> manager.requestPeers(channel, ::choosePeer)
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }
                    if (networkInfo?.isConnected == true) manager.requestConnectionInfo(channel, ::handleConnectionInfo)
                }
            }
        }
    }

    fun connectToDesktop(displayName: String) {
        requirePermissions()
        expectedDisplayName = displayName
        ensureReceiver()
        manager.discoverPeers(channel, actionListener(
            success = { listener.onDesktopWifiDirectStatus("Procurando o grupo Wi-Fi Direct do Desktop…") },
            failurePrefix = "A descoberta Wi-Fi Direct falhou"
        ))
    }

    private fun choosePeer(devices: WifiP2pDeviceList) {
        if (connectingAddress != null) return
        val peers = devices.deviceList.filter { it.status != WifiP2pDevice.FAILED }
        val expected = normalize(expectedDisplayName.orEmpty())
        val selected = peers.firstOrNull { candidate ->
            val candidateName = normalize(candidate.deviceName.orEmpty())
            expected.isNotBlank() && (candidateName.contains(expected) || expected.contains(candidateName))
        } ?: peers.singleOrNull()
        if (selected == null) {
            if (peers.isNotEmpty()) {
                listener.onDesktopWifiDirectStatus(
                    "Há vários pares Wi-Fi Direct próximos; mantenha somente o Desktop desejado disponível."
                )
            }
            return
        }
        connectingAddress = selected.deviceAddress
        val config = WifiP2pConfig().apply {
            deviceAddress = selected.deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 0
        }
        manager.connect(channel, config, actionListener(
            success = { listener.onDesktopWifiDirectStatus("Negociando enlace direto com ${selected.deviceName}…") },
            failurePrefix = "A conexão Wi-Fi Direct falhou"
        ))
    }

    private fun handleConnectionInfo(info: WifiP2pInfo) {
        if (!info.groupFormed) return
        if (info.isGroupOwner) {
            listener.onDesktopWifiDirectStatus("O Android tornou-se coordenador; aguardando renegociação com o Desktop.")
            return
        }
        val owner = info.groupOwnerAddress ?: return
        listener.onDesktopWifiDirectStatus("Enlace Wi-Fi Direct formado sem roteador.")
        listener.onDesktopWifiDirectReady(owner)
    }

    private fun ensureReceiver() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    private fun actionListener(success: () -> Unit, failurePrefix: String) =
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() = success()
            override fun onFailure(reason: Int) {
                connectingAddress = null
                listener.onDesktopWifiDirectStatus("$failurePrefix ($reason).")
            }
        }

    private fun requirePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            check(
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                    PackageManager.PERMISSION_GRANTED
            ) { "Permita dispositivos Wi-Fi próximos" }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            check(
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            ) { "A descoberta Wi-Fi Direct requer a permissão de localização nesta versão do Android" }
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .filter(Char::isLetterOrDigit)
        .removePrefix("veyro")

    override fun close() {
        runCatching { manager.stopPeerDiscovery(channel, null) }
        runCatching { manager.removeGroup(channel, null) }
        if (registered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            registered = false
        }
        connectingAddress = null
        expectedDisplayName = null
    }
}
