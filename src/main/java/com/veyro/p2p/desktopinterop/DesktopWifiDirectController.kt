package com.veyro.p2p.desktopinterop

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.MacAddress
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.net.InetAddress

internal interface DesktopWifiDirectListener {
    fun onDesktopWifiDirectReady(groupOwnerAddress: InetAddress)
    fun onDesktopWifiDirectLost()
    fun onDesktopWifiDirectStatus(message: String, error: Throwable? = null)
}

@SuppressLint("MissingPermission")
internal class DesktopWifiDirectController(
    context: Context,
    private val listener: DesktopWifiDirectListener
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val handler = Handler(Looper.getMainLooper())
    private val channel = manager.initialize(appContext, appContext.mainLooper) {
        listener.onDesktopWifiDirectStatus("O canal Wi-Fi Direct foi perdido.")
    }
    private var registered = false
    private var expectedDisplayName: String? = null
    private var connectingAddress: String? = null
    private var directLinkReady = false
    private var closed = false
    private var recoveryAttempts = 0
    private val connectionTimeout = Runnable {
        if (!directLinkReady && expectedDisplayName != null) {
            listener.onDesktopWifiDirectStatus(
                "O grupo Wi-Fi Direct não respondeu; tentando novamente sem desligar o Wi-Fi comum…"
            )
            recoverDirectLink()
        }
    }
    private val discoveryRetry = Runnable {
        if (!closed && expectedDisplayName != null) discoverDesktopGroup()
    }

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
                    if (networkInfo?.isConnected == true) {
                        manager.requestConnectionInfo(channel, ::handleConnectionInfo)
                    } else if (connectingAddress != null || directLinkReady) {
                        connectingAddress = null
                        directLinkReady = false
                        handler.removeCallbacks(connectionTimeout)
                        listener.onDesktopWifiDirectLost()
                        listener.onDesktopWifiDirectStatus(
                            "Enlace Wi-Fi Direct encerrado; tentando recriá-lo sem alterar o Wi-Fi comum."
                        )
                        recoveryAttempts++
                        if (recoveryAttempts < MAXIMUM_RECOVERY_ATTEMPTS) {
                            scheduleDiscoveryRetry()
                        } else {
                            expectedDisplayName = null
                            listener.onDesktopWifiDirectStatus(
                                "O enlace direto não foi formado após $MAXIMUM_RECOVERY_ATTEMPTS tentativas. " +
                                    "O Wi-Fi comum foi mantido."
                            )
                        }
                    }
                }
            }
        }
    }

    fun connectToDesktop(displayName: String) {
        requirePermissions()
        closed = false
        expectedDisplayName = displayName
        recoveryAttempts = 0
        ensureReceiver()
        discoverDesktopGroup()
    }

    private fun discoverDesktopGroup() {
        handler.removeCallbacks(discoveryRetry)
        manager.discoverPeers(channel, actionListener(
            success = { listener.onDesktopWifiDirectStatus("Procurando o grupo Wi-Fi Direct do Desktop…") },
            failurePrefix = "A descoberta Wi-Fi Direct falhou",
            retryOnFailure = true
        ))
    }

    private fun recoverDirectLink() {
        handler.removeCallbacks(connectionTimeout)
        handler.removeCallbacks(discoveryRetry)
        connectingAddress = null
        directLinkReady = false
        listener.onDesktopWifiDirectLost()
        recoveryAttempts++
        runCatching { manager.cancelConnect(channel, null) }
        if (recoveryAttempts >= MAXIMUM_RECOVERY_ATTEMPTS) {
            listener.onDesktopWifiDirectStatus(
                "A conexão direta não foi formada. O Wi-Fi comum foi preservado; tente conectar novamente."
            )
            expectedDisplayName = null
            return
        }
        scheduleDiscoveryRetry(RECOVERY_SETTLE_MILLIS)
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
        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(selected.deviceAddress))
                .enablePersistentMode(false)
                .build()
                .apply {
                    wps.setup = WpsInfo.PBC
                    groupOwnerIntent = 0
                }
        } else {
            WifiP2pConfig().apply {
                deviceAddress = selected.deviceAddress
                wps.setup = WpsInfo.PBC
                groupOwnerIntent = 0
            }
        }
        manager.connect(channel, config, actionListener(
            success = {
                listener.onDesktopWifiDirectStatus("Negociando enlace direto com ${selected.deviceName}…")
                handler.removeCallbacks(connectionTimeout)
                handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MILLIS)
            },
            failurePrefix = "A conexão Wi-Fi Direct falhou",
            retryOnFailure = true
        ))
    }

    private fun handleConnectionInfo(info: WifiP2pInfo) {
        if (!info.groupFormed) return
        if (info.isGroupOwner) {
            listener.onDesktopWifiDirectStatus("O Android tornou-se coordenador; aguardando renegociação com o Desktop.")
            return
        }
        val owner = info.groupOwnerAddress ?: return
        directLinkReady = true
        recoveryAttempts = 0
        handler.removeCallbacks(connectionTimeout)
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
            ContextCompat.RECEIVER_EXPORTED
        )
        registered = true
    }

    private fun actionListener(
        success: () -> Unit,
        failurePrefix: String,
        retryOnFailure: Boolean = false
    ) =
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() = success()
            override fun onFailure(reason: Int) {
                connectingAddress = null
                listener.onDesktopWifiDirectStatus("$failurePrefix ($reason).")
                if (retryOnFailure) {
                    recoveryAttempts++
                    if (recoveryAttempts < MAXIMUM_RECOVERY_ATTEMPTS) scheduleDiscoveryRetry()
                    else {
                        listener.onDesktopWifiDirectStatus(
                            "Não foi possível formar o enlace direto. O Wi-Fi comum continua disponível."
                        )
                        expectedDisplayName = null
                    }
                }
            }
        }

    private fun scheduleDiscoveryRetry(delayMillis: Long = DISCOVERY_RETRY_MILLIS) {
        if (closed || expectedDisplayName == null) return
        handler.removeCallbacks(discoveryRetry)
        handler.postDelayed(discoveryRetry, delayMillis)
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
        closed = true
        handler.removeCallbacks(connectionTimeout)
        handler.removeCallbacks(discoveryRetry)
        runCatching { manager.stopPeerDiscovery(channel, null) }
        runCatching { manager.removeGroup(channel, null) }
        if (registered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            registered = false
        }
        connectingAddress = null
        directLinkReady = false
        expectedDisplayName = null
        recoveryAttempts = 0
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 30_000L
        const val DISCOVERY_RETRY_MILLIS = 3_000L
        const val RECOVERY_SETTLE_MILLIS = 1_000L
        const val MAXIMUM_RECOVERY_ATTEMPTS = 3
    }
}
