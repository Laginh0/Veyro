package com.veyro.p2p.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.veyro.p2p.MainActivity
import com.veyro.p2p.R
import com.veyro.p2p.nearby.ConnectionStage
import com.veyro.p2p.nearby.NearbyClientUiState
import com.veyro.p2p.nearby.NearbySessionController
import com.veyro.p2p.nearby.RawFileStatus
import com.veyro.p2p.protocol.FindDeviceTrigger
import com.veyro.p2p.protocol.MediaEventCategory
import com.veyro.p2p.protocol.RemoteInputCommand
import com.veyro.p2p.protocol.PresentationAction
import com.veyro.p2p.protocol.StylusAction
import com.veyro.p2p.settings.EnergyMode
import com.veyro.p2p.settings.AppLanguage
import com.veyro.p2p.settings.FeatureSettings
import com.veyro.p2p.settings.TrustedDeviceRules
import com.veyro.p2p.settings.EcosystemPreferences
import com.veyro.p2p.ui.i18n.VeyroI18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class P2PTransferService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: NearbySessionController
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundStarted = false
    private var screenReceiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val interactive = intent?.action == Intent.ACTION_SCREEN_ON
            controller.onScreenStateChanged(interactive)
            applyWakeLockPolicy(controller.uiState.value)
        }
    }

    val uiState: StateFlow<NearbyClientUiState>
        get() = controller.uiState

    inner class LocalBinder : Binder() {
        val service: P2PTransferService
            get() = this@P2PTransferService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        controller = NearbySessionController(application, serviceScope)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        controller.onScreenStateChanged(powerManager.isInteractive)
        registerScreenReceiver()

        serviceScope.launch {
            controller.uiState.collect { state ->
                applyWakeLockPolicy(state)
                if (isForegroundStarted) {
                    updateForegroundNotification(state)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ECOSYSTEM,
            ACTION_START_ADVERTISING -> {
                startForegroundSession()
                controller.startContinuousEcosystem()
            }

            ACTION_START_DISCOVERY -> {
                startForegroundSession()
                controller.startContinuousEcosystem()
            }

            ACTION_STOP_SESSION -> stopSession()
            null -> if (controller.uiState.value.ecosystemEnabled) {
                startForegroundSession()
                controller.restoreContinuousEcosystemIfEnabled()
            }
        }
        return if (controller.uiState.value.ecosystemEnabled) START_STICKY else START_NOT_STICKY
    }

    fun requestConnection(endpointId: String) {
        controller.requestConnection(endpointId)
    }

    fun acceptPendingConnection() {
        controller.acceptPendingConnection()
    }

    fun rejectPendingConnection() {
        controller.rejectPendingConnection()
    }

    fun sendCommand(command: String) {
        controller.sendCommand(command)
    }

    fun sendFile(uri: Uri) {
        controller.sendFile(uri)
    }

    fun sendFindDeviceCommand(trigger: FindDeviceTrigger) {
        controller.sendFindDeviceCommand(trigger)
    }

    fun sendNotificationDismiss(notificationKey: String) {
        controller.sendNotificationDismiss(notificationKey)
    }

    fun sendMediaControlCommand(category: MediaEventCategory) {
        controller.sendMediaControlCommand(category)
    }

    fun sendSmsTransmitOrder(address: String, text: String) {
        controller.sendSmsTransmitOrder(address, text)
    }

    fun refreshTelephonySync() {
        controller.refreshTelephonySync()
    }

    fun sendSafeCustomCommand(action: String) {
        controller.sendSafeCustomCommand(action)
    }

    fun shareUrl(url: String) {
        controller.shareUrl(url)
    }

    fun sendRemoteInput(
        command: RemoteInputCommand,
        deltaX: Float,
        deltaY: Float,
        keyboardText: String
    ) {
        controller.sendRemoteInput(command, deltaX, deltaY, keyboardText)
    }

    fun selectConnectedEndpoint(endpointId: String) {
        controller.selectConnectedEndpoint(endpointId)
    }

    fun shareContact(uri: Uri) {
        controller.shareContact(uri)
    }

    fun approveContactImport(requestId: String) {
        controller.approveContactImport(requestId)
    }

    fun rejectContactImport(requestId: String) {
        controller.rejectContactImport(requestId)
    }

    fun sendPresentationAction(action: PresentationAction, elapsedMillis: Long) {
        controller.sendPresentationAction(action, elapsedMillis)
    }

    fun dismissRemoteBlackout() {
        controller.dismissRemoteBlackout()
    }

    fun setSharedFolder(uri: Uri) {
        controller.setSharedFolder(uri)
    }

    fun clearSharedFolder() {
        controller.clearSharedFolder()
    }

    fun requestRemoteFileList(parentDocumentId: String) {
        controller.requestRemoteFileList(parentDocumentId)
    }

    fun requestRemoteFileDownload(documentId: String) {
        controller.requestRemoteFileDownload(documentId)
    }

    fun sendStylusEvent(
        action: StylusAction,
        normalizedX: Float,
        normalizedY: Float,
        pressure: Float,
        tiltX: Float,
        tiltY: Float,
        primaryButtonPressed: Boolean,
        isStylus: Boolean
    ) {
        controller.sendStylusEvent(
            action,
            normalizedX,
            normalizedY,
            pressure,
            tiltX,
            tiltY,
            primaryButtonPressed,
            isStylus
        )
    }

    fun stopSession() {
        controller.stopSession()
        if (isForegroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        releaseWakeLock()
        stopSelf()
    }

    fun updateTrustedDeviceRules(rules: TrustedDeviceRules) {
        controller.updateTrustedDeviceRules(rules)
    }

    fun removeTrustedDevice(deviceName: String) {
        controller.removeTrustedDevice(deviceName)
    }

    fun setEnergyMode(mode: EnergyMode) {
        controller.setEnergyMode(mode)
        applyWakeLockPolicy(controller.uiState.value)
    }

    fun setAppLanguage(language: AppLanguage) {
        controller.setAppLanguage(language)
        createNotificationChannel()
    }

    fun setFeatureSettings(settings: FeatureSettings) {
        controller.setFeatureSettings(settings)
    }

    fun approveIncomingFile(payloadId: Long) {
        controller.approveIncomingFile(payloadId)
    }

    fun rejectIncomingFile(payloadId: Long) {
        controller.rejectIncomingFile(payloadId)
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenReceiverRegistered = false
        }
        releaseWakeLock()
        controller.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSession() {
        if (!isForegroundStarted) {
            isForegroundStarted = true
        }
        applyWakeLockPolicy(controller.uiState.value)
        updateForegroundNotification(controller.uiState.value)
    }

    private fun updateForegroundNotification(state: NearbyClientUiState) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(state),
            foregroundServiceTypes(state)
        )
    }

    private fun foregroundServiceTypes(state: NearbyClientUiState): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val transferActive = state.rawFileTransfers.any {
            it.status == RawFileStatus.IN_PROGRESS || it.status == RawFileStatus.SAVING
        }
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            if (transferActive) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
    }

    private fun buildNotification(state: NearbyClientUiState): Notification {
        val latestTransfer = state.rawFileTransfers.lastOrNull()
        val fileName = latestTransfer?.fileName ?: "arquivo"
        val title = when (latestTransfer?.status) {
            RawFileStatus.IN_PROGRESS -> "Transferindo $fileName"
            RawFileStatus.COMPLETED -> "Transferência concluída"
            RawFileStatus.AWAITING_APPROVAL -> "Aprovação necessária para $fileName"
            RawFileStatus.SAVING -> "Salvando $fileName"
            RawFileStatus.SAVED -> "$fileName salvo"
            RawFileStatus.FAILED -> "Falha na transferência"
            RawFileStatus.CANCELED -> "Transferência cancelada"
            null -> when (state.connectionStage) {
                ConnectionStage.ADVERTISING -> "Veyro aguardando destinatário"
                ConnectionStage.DISCOVERING -> "Veyro procurando aparelhos"
                ConnectionStage.ACTIVE -> "Ecossistema Veyro ativo"
                ConnectionStage.CONNECTING,
                ConnectionStage.AUTHENTICATING -> "Veyro conectando"
                ConnectionStage.CONNECTED -> "Veyro conectado"
                ConnectionStage.IDLE,
                ConnectionStage.ERROR -> "Veyro em segundo plano"
            }
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, P2PTransferService::class.java).setAction(ACTION_STOP_SESSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_veyro_transfer)
            .setContentTitle(VeyroI18n.translate(title, state.appLanguage))
            .setContentText(
                VeyroI18n.translate(
                    state.statusMessage ?: "Transferência P2P ativa.",
                    state.appLanguage
                )
            )
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                VeyroI18n.translate("Encerrar", state.appLanguage),
                stopIntent
            )
            .apply {
                if (latestTransfer?.status == RawFileStatus.IN_PROGRESS) {
                    val hasKnownProgress = latestTransfer.totalBytes > 0L
                    setProgress(
                        100,
                        latestTransfer.progressPercent.coerceIn(0, 100),
                        !hasKnownProgress
                    )
                } else {
                    setProgress(0, 0, false)
                }
            }
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val language = EcosystemPreferences(this).appLanguage()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            VeyroI18n.translate("Transferências Veyro", language),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = VeyroI18n.translate(
                "Mostra conexões e transferências P2P em andamento.",
                language
            )
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:P2PTransfer"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }
    }

    private fun applyWakeLockPolicy(state: NearbyClientUiState) {
        if (!isForegroundStarted) {
            releaseWakeLock()
            return
        }
        val transferActive = state.rawFileTransfers.any {
            it.status == RawFileStatus.IN_PROGRESS || it.status == RawFileStatus.SAVING
        }
        val shouldHoldWakeLock = when (state.energyMode) {
            EnergyMode.CONTINUOUS -> true
            EnergyMode.BALANCED,
            EnergyMode.BATTERY_SAVER -> transferActive
        }
        if (shouldHoldWakeLock) acquireWakeLock() else releaseWakeLock()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_START_ADVERTISING = "com.veyro.p2p.action.START_ADVERTISING"
        const val ACTION_START_DISCOVERY = "com.veyro.p2p.action.START_DISCOVERY"
        const val ACTION_START_ECOSYSTEM = "com.veyro.p2p.action.START_ECOSYSTEM"
        const val ACTION_STOP_SESSION = "com.veyro.p2p.action.STOP_SESSION"

        private const val NOTIFICATION_CHANNEL_ID = "veyro_p2p_transfers"
        private const val NOTIFICATION_ID = 9001
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6 * 60 * 60 * 1000L
    }
}
