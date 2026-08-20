package com.veyro.p2p.nearby

import android.app.Application
import android.os.BatteryManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.tasks.Task
import com.veyro.p2p.features.battery.BatteryStatusMonitor
import com.veyro.p2p.features.commands.SafeCustomCommandExecutor
import com.veyro.p2p.features.clipboard.ClipboardSyncManager
import com.veyro.p2p.features.connectivity.ConnectivityStatusMonitor
import com.veyro.p2p.features.contacts.ContactSyncManager
import com.veyro.p2p.features.finddevice.FindMyDeviceAlarmController
import com.veyro.p2p.features.media.MediaSessionCoordinator
import com.veyro.p2p.features.notifications.NotificationSyncBridge
import com.veyro.p2p.features.remoteinput.RemoteInputBridge
import com.veyro.p2p.features.remotefiles.SharedFolderManager
import com.veyro.p2p.features.shareurl.SharedUrlNotificationManager
import com.veyro.p2p.features.telephony.SmsApprovalManager
import com.veyro.p2p.features.telephony.TelephonyCallStateMonitor
import com.veyro.p2p.features.telephony.TelephonySyncBridge
import com.veyro.p2p.protocol.BatteryStatus
import com.veyro.p2p.protocol.CustomCommandEvent
import com.veyro.p2p.protocol.ConnectivityStatus
import com.veyro.p2p.protocol.ClipboardSyncEvent
import com.veyro.p2p.protocol.ContactRecord
import com.veyro.p2p.protocol.ContactSyncAction
import com.veyro.p2p.protocol.ContactSyncEvent
import com.veyro.p2p.protocol.ExecutionTypeCategory
import com.veyro.p2p.protocol.FindDeviceRequest
import com.veyro.p2p.protocol.FindDeviceTrigger
import com.veyro.p2p.protocol.MediaControlEvent
import com.veyro.p2p.protocol.MediaEventCategory
import com.veyro.p2p.protocol.NotificationSyncAction
import com.veyro.p2p.protocol.NotificationSyncEvent
import com.veyro.p2p.protocol.NetworkTransport
import com.veyro.p2p.protocol.PingAction
import com.veyro.p2p.protocol.PingEvent
import com.veyro.p2p.protocol.PowerSourceType
import com.veyro.p2p.protocol.PresentationAction
import com.veyro.p2p.protocol.PresentationEvent
import com.veyro.p2p.protocol.RemoteInputCommand
import com.veyro.p2p.protocol.RemoteInputEvent
import com.veyro.p2p.protocol.RemoteFileAction
import com.veyro.p2p.protocol.RemoteFileEntry
import com.veyro.p2p.protocol.RemoteFileEvent
import com.veyro.p2p.protocol.StylusAction
import com.veyro.p2p.protocol.TelecommunicationEvent
import com.veyro.p2p.protocol.TelecommunicationType
import com.veyro.p2p.protocol.UrlShareEvent
import com.veyro.p2p.protocol.VeyroProtocolCodec
import com.veyro.p2p.protocol.VeyroMessage
import com.veyro.p2p.service.P2PTransferService
import com.veyro.p2p.settings.EcosystemPreferences
import com.veyro.p2p.settings.EnergyMode
import com.veyro.p2p.settings.AppLanguage
import com.veyro.p2p.settings.FeatureSettings
import com.veyro.p2p.settings.TrustedDeviceRules
import com.veyro.p2p.storage.ReceivedFileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

enum class NearbyClientStatus {
    INITIALIZING,
    READY,
    ERROR
}

enum class ConnectionRole {
    NONE,
    ADVERTISER,
    DISCOVERER
}

enum class ConnectionStage {
    IDLE,
    ACTIVE,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}

data class DiscoveredEndpoint(
    val id: String,
    val name: String,
    val stableDeviceId: String = "",
    val capacityScore: Int = 0
)

data class PendingConnection(
    val endpointId: String,
    val endpointName: String,
    val authenticationDigits: String
)

data class ConnectedEndpoint(
    val id: String,
    val name: String,
    val stableDeviceId: String = "",
    val role: ConnectionRole
)

data class ReceivedCommand(
    val senderName: String,
    val text: String
)

data class RemoteBatteryStatus(
    val chargePercentage: Int,
    val isPluggedIn: Boolean,
    val powerSourceLabel: String,
    val eventTimestamp: Long
)

data class RemoteConnectivityStatus(
    val transportLabel: String,
    val hasInternet: Boolean,
    val isMetered: Boolean,
    val signalStrengthDbm: Int?,
    val eventTimestamp: Long
)

data class RemotePingStatus(
    val roundTripMillis: Long,
    val measuredAt: Long
)

data class RemoteNotification(
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val textBody: String,
    val isClearable: Boolean
)

data class RemoteMediaState(
    val playbackStatus: Int,
    val trackName: String,
    val artistName: String,
    val currentPositionMs: Long
)

data class RemoteTelecommunicationEvent(
    val type: TelecommunicationType,
    val identityLabel: String,
    val addressNumber: String,
    val textPayload: String,
    val epochTimestamp: Long
)

data class RemoteCustomCommandResult(
    val trackingId: String,
    val succeeded: Boolean,
    val message: String
)

data class RemoteSharedUrl(
    val url: String,
    val accepted: Boolean,
    val message: String
)

data class PendingContactImport(
    val requestId: String,
    val endpointId: String,
    val senderName: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val emailAddresses: List<String>
) {
    fun toProtocolRecord(): ContactRecord = ContactRecord.newBuilder()
        .setDisplayName(displayName)
        .addAllPhoneNumbers(phoneNumbers)
        .addAllEmailAddresses(emailAddresses)
        .build()
}

data class RemoteFileItem(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isDirectory: Boolean
)

data class RemotePresentationState(
    val active: Boolean = false,
    val blackedOut: Boolean = false,
    val elapsedMillis: Long = 0L
)

enum class RawFileDirection {
    SEND,
    RECEIVE
}

enum class RawFileStatus {
    IN_PROGRESS,
    COMPLETED,
    AWAITING_APPROVAL,
    SAVING,
    SAVED,
    FAILED,
    CANCELED
}

data class RawFileTransfer(
    val payloadId: Long,
    val endpointId: String = "",
    val direction: RawFileDirection,
    val temporaryUri: String? = null,
    val fileName: String? = null,
    val totalBytes: Long = 0L,
    val mimeType: String? = null,
    val bytesTransferred: Long = 0L,
    val progressPercent: Int = 0,
    val status: RawFileStatus = RawFileStatus.IN_PROGRESS,
    val savedUri: String? = null
)

data class NearbyClientUiState(
    val status: NearbyClientStatus = NearbyClientStatus.INITIALIZING,
    val serviceId: String = NearbyConnectionsClient.SERVICE_ID,
    val strategyName: String = NearbyConnectionsClient.STRATEGY_NAME,
    val role: ConnectionRole = ConnectionRole.NONE,
    val connectionStage: ConnectionStage = ConnectionStage.IDLE,
    val discoveredEndpoints: List<DiscoveredEndpoint> = emptyList(),
    val pendingConnection: PendingConnection? = null,
    val connectedEndpoints: List<ConnectedEndpoint> = emptyList(),
    val connectedEndpointId: String? = null,
    val connectedEndpointName: String? = null,
    val remoteBatteryStatus: RemoteBatteryStatus? = null,
    val remoteConnectivityStatus: RemoteConnectivityStatus? = null,
    val remotePingStatus: RemotePingStatus? = null,
    val remoteNotifications: List<RemoteNotification> = emptyList(),
    val remoteMediaState: RemoteMediaState? = null,
    val remoteTelecommunicationEvents: List<RemoteTelecommunicationEvent> = emptyList(),
    val remoteCustomCommandResults: List<RemoteCustomCommandResult> = emptyList(),
    val remoteSharedUrls: List<RemoteSharedUrl> = emptyList(),
    val pendingContactImports: List<PendingContactImport> = emptyList(),
    val lastContactResult: String? = null,
    val remoteFileItems: List<RemoteFileItem> = emptyList(),
    val remoteFileParentId: String = "",
    val remoteFileMessage: String? = null,
    val sharedFolderName: String? = null,
    val remotePresentationState: RemotePresentationState = RemotePresentationState(),
    val clipboardStatus: String? = null,
    val receivedCommands: List<ReceivedCommand> = emptyList(),
    val rawFileTransfers: List<RawFileTransfer> = emptyList(),
    val trustedDevices: List<TrustedDeviceRules> = emptyList(),
    val energyMode: EnergyMode = EnergyMode.BALANCED,
    val appLanguage: AppLanguage = AppLanguage.PORTUGUESE,
    val featureSettings: FeatureSettings = FeatureSettings(),
    val ecosystemEnabled: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

internal class NearbySessionController(
    application: Application,
    private val controllerScope: CoroutineScope
) : NearbyConnectionsListener {
    private val appContext = application.applicationContext
    private val ecosystemPreferences = EcosystemPreferences(application)
    private val localIdentity = EndpointIdentity(
        deviceId = ecosystemPreferences.localDeviceId(),
        capacityScore = calculateCapacityScore(),
        displayName = ecosystemPreferences.localDisplayName()
    )
    private val localEndpointName = localIdentity.toWireName()
    private val endpointIdentities = ConcurrentHashMap<String, EndpointIdentity>()
    private val connectionAttemptJobs = ConcurrentHashMap<String, Job>()
    private val fileMetadataByPayloadId = ConcurrentHashMap<Long, FileMetadata>()
    private val completedFilePayloadIds = ConcurrentHashMap<Long, Unit>()
    private val savingFilePayloadIds = ConcurrentHashMap<Long, Unit>()
    private val approvedFilePayloadIds = ConcurrentHashMap<Long, Unit>()
    private val receivedFileStorage = ReceivedFileStorage(application)
    private val batteryStatusMonitor = BatteryStatusMonitor(application)
    private val connectivityStatusMonitor = ConnectivityStatusMonitor(application)
    private val contactSyncManager = ContactSyncManager(application)
    private val sharedFolderManager = SharedFolderManager(application)
    private val clipboardSyncManager = ClipboardSyncManager(application) {
        syncLocalClipboard(manual = false)
    }
    private val findMyDeviceAlarm = FindMyDeviceAlarmController(application)
    private val mediaSessionCoordinator = MediaSessionCoordinator(application)
    private val telephonyCallStateMonitor = TelephonyCallStateMonitor(application)
    private val smsApprovalManager = SmsApprovalManager(application)
    private val safeCustomCommandExecutor = SafeCustomCommandExecutor(application)
    private val sharedUrlNotificationManager = SharedUrlNotificationManager(application)
    private var batterySyncJob: Job? = null
    private var connectivitySyncJob: Job? = null
    private var pingJob: Job? = null
    private data class PendingPing(val endpointId: String, val startedAt: Long)
    private val pendingPings = ConcurrentHashMap<String, PendingPing>()
    private var notificationSyncJob: Job? = null
    private var telephonySyncJob: Job? = null
    private var radioDutyCycleJob: Job? = null
    private var reconnectJob: Job? = null
    private var screenInteractive: Boolean = true
    private val seenClipboardEventIds = LinkedHashSet<String>()
    private var lastClipboardFingerprint: String? = null

    private val clientResult by lazy {
        runCatching { NearbyConnectionsClient(application, this) }
    }

    val nearbyClient: NearbyConnectionsClient
        get() = clientResult.getOrThrow()

    private val _uiState = MutableStateFlow(
        clientResult.fold(
            onSuccess = {
                NearbyClientUiState(
                    status = NearbyClientStatus.READY,
                    trustedDevices = ecosystemPreferences.trustedDevices(),
                    energyMode = ecosystemPreferences.energyMode(),
                    appLanguage = ecosystemPreferences.appLanguage(),
                    featureSettings = ecosystemPreferences.featureSettings(),
                    ecosystemEnabled = ecosystemPreferences.ecosystemEnabled(),
                    sharedFolderName = sharedFolderManager.sharedTree()?.displayName,
                    statusMessage = "Cliente Nearby inicializado."
                )
            },
            onFailure = { error ->
                NearbyClientUiState(
                    status = NearbyClientStatus.ERROR,
                    connectionStage = ConnectionStage.ERROR,
                    trustedDevices = ecosystemPreferences.trustedDevices(),
                    energyMode = ecosystemPreferences.energyMode(),
                    appLanguage = ecosystemPreferences.appLanguage(),
                    featureSettings = ecosystemPreferences.featureSettings(),
                    ecosystemEnabled = ecosystemPreferences.ecosystemEnabled(),
                    sharedFolderName = sharedFolderManager.sharedTree()?.displayName,
                    errorMessage = error.message ?: "Não foi possível inicializar o Nearby."
                )
            }
        )
    )
    val uiState: StateFlow<NearbyClientUiState> = _uiState.asStateFlow()

    fun startAdvertising() = startContinuousEcosystem()

    fun startDiscovery() = startContinuousEcosystem()

    fun startContinuousEcosystem() {
        val client = clientResult.getOrElse { error ->
            showError(error)
            return
        }
        ecosystemPreferences.setEcosystemEnabled(true)
        if (_uiState.value.connectedEndpoints.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    ecosystemEnabled = true,
                    statusMessage = "Ecossistema contínuo ativo; reconexão automática habilitada.",
                    errorMessage = null
                )
            }
            return
        }
        reconnectJob?.cancel()
        radioDutyCycleJob?.cancel()
        cancelConnectionAttempts()
        stopRadioOperations(client)
        _uiState.update {
            it.copy(
                role = ConnectionRole.NONE,
                connectionStage = ConnectionStage.ACTIVE,
                discoveredEndpoints = emptyList(),
                pendingConnection = null,
                connectedEndpoints = emptyList(),
                connectedEndpointId = null,
                connectedEndpointName = null,
                ecosystemEnabled = true,
                statusMessage = "Ativando visibilidade e detecção simultâneas...",
                errorMessage = null
            )
        }
        applyRadioPolicy()
    }

    fun restoreContinuousEcosystemIfEnabled() {
        if (ecosystemPreferences.ecosystemEnabled()) startContinuousEcosystem()
    }

    fun requestConnection(endpointId: String) {
        val endpoint = _uiState.value.discoveredEndpoints.firstOrNull { it.id == endpointId }
            ?: return
        requestConnectionInternal(endpointId, endpoint.name)
    }

    private fun requestConnectionInternal(endpointId: String, endpointName: String) {
        val state = _uiState.value
        if ((state.role == ConnectionRole.DISCOVERER && state.connectedEndpoints.isNotEmpty()) ||
            state.pendingConnection != null ||
            state.connectionStage == ConnectionStage.CONNECTING ||
            state.connectionStage == ConnectionStage.AUTHENTICATING
        ) {
            return
        }
        val client = clientResult.getOrElse { error ->
            showError(error)
            return
        }

        _uiState.update {
            it.copy(
                connectionStage = ConnectionStage.CONNECTING,
                statusMessage = "Negociando conexão com ${endpointName.removePrefix("Veyro - ")}...",
                errorMessage = null
            )
        }
        runCatching { client.requestConnection(localEndpointName, endpointId) }
            .onSuccess { task ->
                task.addOnSuccessListener {
                    _uiState.update {
                        it.copy(statusMessage = "Pedido enviado; aguardando o outro aparelho...")
                    }
                }.addOnFailureListener { error ->
                    handleConnectionAttemptFailure(error)
                }
            }
            .onFailure(::handleConnectionAttemptFailure)
    }

    fun acceptPendingConnection() {
        val pending = _uiState.value.pendingConnection ?: return
        val client = clientResult.getOrElse { error ->
            showError(error)
            return
        }

        runTask(
            taskProvider = { client.acceptConnection(pending.endpointId) },
            onSuccess = {
                _uiState.update {
                    it.copy(
                        pendingConnection = null,
                        statusMessage = "PIN confirmado. Aguardando o outro aparelho..."
                    )
                }
            }
        )
    }

    fun rejectPendingConnection() {
        val pending = _uiState.value.pendingConnection ?: return
        val client = clientResult.getOrElse { error ->
            showError(error)
            return
        }

        runTask(
            taskProvider = { client.rejectConnection(pending.endpointId) },
            onSuccess = {
                _uiState.update { state ->
                    state.copy(
                        connectionStage = if (state.ecosystemEnabled) {
                            ConnectionStage.ACTIVE
                        } else {
                            ConnectionStage.IDLE
                        },
                        pendingConnection = null,
                        statusMessage = "Conexão recusada.",
                        errorMessage = null
                    )
                }
            }
        )
        if (_uiState.value.ecosystemEnabled) scheduleRadioRestart()
    }

    fun stopSession() {
        ecosystemPreferences.setEcosystemEnabled(false)
        reconnectJob?.cancel()
        radioDutyCycleJob?.cancel()
        cancelConnectionAttempts()
        stopBatterySync()
        stopConnectivitySync()
        stopPing()
        stopNotificationSync()
        stopMediaSync()
        stopTelephonySync()
        findMyDeviceAlarm.stop()
        clientResult.getOrNull()?.let { client ->
            _uiState.value.connectedEndpoints.forEach { endpoint ->
                client.disconnectFromEndpoint(endpoint.id)
            }
            stopRadioOperations(client)
        }
        fileMetadataByPayloadId.clear()
        completedFilePayloadIds.clear()
        approvedFilePayloadIds.clear()
        endpointIdentities.clear()
        seenClipboardEventIds.clear()
        lastClipboardFingerprint = null
        _uiState.update {
            it.copy(
                role = ConnectionRole.NONE,
                connectionStage = ConnectionStage.IDLE,
                discoveredEndpoints = emptyList(),
                pendingConnection = null,
                connectedEndpoints = emptyList(),
                connectedEndpointId = null,
                connectedEndpointName = null,
                remoteBatteryStatus = null,
                remoteConnectivityStatus = null,
                remotePingStatus = null,
                remoteNotifications = emptyList(),
                remoteMediaState = null,
                remoteTelecommunicationEvents = emptyList(),
                remoteCustomCommandResults = emptyList(),
                remoteSharedUrls = emptyList(),
                pendingContactImports = emptyList(),
                lastContactResult = null,
                remoteFileItems = emptyList(),
                remoteFileParentId = "",
                remoteFileMessage = null,
                remotePresentationState = RemotePresentationState(),
                clipboardStatus = null,
                receivedCommands = emptyList(),
                rawFileTransfers = emptyList(),
                ecosystemEnabled = false,
                statusMessage = "Ecossistema contínuo desativado.",
                errorMessage = null
            )
        }
    }

    fun updateTrustedDeviceRules(rules: TrustedDeviceRules) {
        ecosystemPreferences.updateRules(rules)
        _uiState.update {
            it.copy(
                trustedDevices = ecosystemPreferences.trustedDevices(),
                statusMessage = "Regras de ${rules.deviceName.removePrefix("Veyro - ")} atualizadas.",
                errorMessage = null
            )
        }
    }

    fun removeTrustedDevice(deviceName: String) {
        ecosystemPreferences.removeDevice(deviceName)
        _uiState.update {
            it.copy(
                trustedDevices = ecosystemPreferences.trustedDevices(),
                statusMessage = "Aparelho removido do Trust Hub.",
                errorMessage = null
            )
        }
    }

    fun setEnergyMode(mode: EnergyMode) {
        ecosystemPreferences.setEnergyMode(mode)
        _uiState.update {
            it.copy(
                energyMode = mode,
                statusMessage = "Modo de energia atualizado.",
                errorMessage = null
            )
        }
        applyRadioPolicy()
        if (_uiState.value.featureSettings.ping) {
            if (_uiState.value.connectedEndpoints.isNotEmpty()) startPing()
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        ecosystemPreferences.setAppLanguage(language)
        _uiState.update {
            it.copy(
                appLanguage = language,
                statusMessage = if (language == AppLanguage.ENGLISH) {
                    "Language changed to English."
                } else {
                    "Idioma alterado para Português."
                },
                errorMessage = null
            )
        }
    }

    fun setFeatureSettings(settings: FeatureSettings) {
        ecosystemPreferences.setFeatureSettings(settings)
        _uiState.update { state ->
            state.copy(
                featureSettings = settings,
                remoteBatteryStatus = state.remoteBatteryStatus.takeIf { settings.batterySync },
                remoteConnectivityStatus = state.remoteConnectivityStatus.takeIf {
                    settings.connectivitySync
                },
                remotePingStatus = state.remotePingStatus.takeIf { settings.ping },
                remoteNotifications = state.remoteNotifications.takeIf {
                    settings.notificationSync
                }.orEmpty(),
                remoteMediaState = state.remoteMediaState.takeIf { settings.mediaControl },
                remoteTelecommunicationEvents = state.remoteTelecommunicationEvents.takeIf {
                    settings.telephonySync
                }.orEmpty(),
                remoteCustomCommandResults = state.remoteCustomCommandResults.takeIf {
                    settings.safeCommands
                }.orEmpty(),
                remoteSharedUrls = state.remoteSharedUrls.takeIf { settings.sharedLinks }.orEmpty(),
                pendingContactImports = state.pendingContactImports.takeIf {
                    settings.contactSync
                }.orEmpty(),
                lastContactResult = state.lastContactResult.takeIf { settings.contactSync },
                remoteFileItems = state.remoteFileItems.takeIf { settings.remoteFiles }.orEmpty(),
                remoteFileMessage = state.remoteFileMessage.takeIf { settings.remoteFiles },
                remotePresentationState = state.remotePresentationState.takeIf {
                    settings.presentationMode
                } ?: RemotePresentationState(),
                clipboardStatus = state.clipboardStatus.takeIf { settings.clipboardSync },
                receivedCommands = state.receivedCommands.takeIf { settings.safeCommands }.orEmpty(),
                statusMessage = "Preferências de recursos atualizadas.",
                errorMessage = null
            )
        }

        if (_uiState.value.connectedEndpoints.isNotEmpty()) {
            if (settings.batterySync) startBatterySync() else stopBatterySync()
            if (settings.connectivitySync) {
                startConnectivitySync()
            } else {
                stopConnectivitySync()
            }
            if (settings.ping) startPing() else stopPing()
            if (settings.notificationSync) startNotificationSync() else stopNotificationSync()
            if (settings.mediaControl) startMediaSync() else stopMediaSync()
            if (settings.telephonySync) startTelephonySync() else stopTelephonySync()
        }
        if (!settings.findDevice) findMyDeviceAlarm.stop()
    }

    fun syncLocalClipboard(manual: Boolean = true) {
        val state = _uiState.value
        if (!state.featureSettings.clipboardSync) return
        if (state.connectedEndpoints.isEmpty()) {
            if (manual) updateClipboardStatus(
                "Conecte um aparelho antes de sincronizar o clipboard.",
                isError = true
            )
            return
        }
        val text = clipboardSyncManager.readPlainText()
        if (text == null) {
            if (manual) updateClipboardStatus(
                "O clipboard não contém texto acessível.",
                isError = true
            )
            return
        }
        if (!ClipboardSyncManager.isSafeText(text)) {
            updateClipboardStatus("O texto excede o limite seguro de 20 KB.", isError = true)
            return
        }
        val fingerprint = ClipboardSyncManager.fingerprint(text)
        if (!manual && fingerprint == lastClipboardFingerprint) return

        val event = ClipboardSyncEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setSourceDeviceId(localIdentity.deviceId)
            .setText(text)
            .setEventTimestamp(System.currentTimeMillis())
            .build()
        rememberClipboardEvent(event.eventId)
        lastClipboardFingerprint = fingerprint
        broadcastFeaturePayload(VeyroProtocolCodec.encodeClipboardSyncEvent(event))
        updateClipboardStatus(
            if (manual) "Clipboard enviado aos aparelhos conectados."
            else "Novo texto do clipboard sincronizado.",
            isError = false
        )
    }

    fun onScreenStateChanged(interactive: Boolean) {
        screenInteractive = interactive
        applyRadioPolicy()
    }

    fun approveIncomingFile(payloadId: Long) {
        if (!_uiState.value.featureSettings.fileTransfer) return
        val awaitingApproval = _uiState.value.rawFileTransfers.any {
            it.payloadId == payloadId &&
                it.direction == RawFileDirection.RECEIVE &&
                it.status == RawFileStatus.AWAITING_APPROVAL
        }
        if (!awaitingApproval) return
        approvedFilePayloadIds[payloadId] = Unit
        trySaveIncomingFile(payloadId)
    }

    fun rejectIncomingFile(payloadId: Long) {
        approvedFilePayloadIds.remove(payloadId)
        completedFilePayloadIds.remove(payloadId)
        fileMetadataByPayloadId.remove(payloadId)
        _uiState.update { state ->
            state.copy(
                rawFileTransfers = state.rawFileTransfers.map { transfer ->
                    if (transfer.payloadId == payloadId &&
                        transfer.direction == RawFileDirection.RECEIVE
                    ) {
                        transfer.copy(status = RawFileStatus.CANCELED)
                    } else {
                        transfer
                    }
                },
                statusMessage = "Arquivo recebido recusado; nada foi salvo.",
                errorMessage = null
            )
        }
    }

    override fun onEndpointFound(endpointId: String, endpointName: String) {
        val identity = EndpointIdentity.parse(endpointName) ?: return
        if (identity.deviceId == localIdentity.deviceId) return
        endpointIdentities[endpointId] = identity
        _uiState.update { state ->
            val endpoints = state.discoveredEndpoints
                .filterNot { it.id == endpointId }
                .plus(
                    DiscoveredEndpoint(
                        id = endpointId,
                        name = identity.trustedName,
                        stableDeviceId = identity.deviceId,
                        capacityScore = identity.capacityScore
                    )
                )
                .sortedBy { it.name.lowercase() }
            state.copy(
                discoveredEndpoints = endpoints,
                statusMessage = "${endpoints.size} aparelho(s) no ecossistema próximo."
            )
        }
        scheduleDeterministicConnection(endpointId, identity)
    }

    override fun onEndpointLost(endpointId: String) {
        connectionAttemptJobs.remove(endpointId)?.cancel()
        endpointIdentities.remove(endpointId)
        _uiState.update { state ->
            val endpoints = state.discoveredEndpoints.filterNot { it.id == endpointId }
            state.copy(
                discoveredEndpoints = endpoints,
                statusMessage = if (endpoints.isEmpty()) {
                    "Ecossistema ativo; aguardando aparelhos próximos..."
                } else {
                    "${endpoints.size} aparelho(s) encontrado(s)."
                }
            )
        }
    }

    override fun onConnectionInitiated(
        endpointId: String,
        endpointName: String,
        authenticationDigits: String
    ) {
        connectionAttemptJobs.remove(endpointId)?.cancel()
        val identity = EndpointIdentity.parse(endpointName)
            ?: endpointIdentities[endpointId]
            ?: return
        endpointIdentities[endpointId] = identity
        val trustedName = identity.trustedName
        val client = clientResult.getOrNull() ?: return
        val state = _uiState.value
        val proposedRole = if (EndpointIdentity.shouldInitiate(localIdentity, identity)) {
            ConnectionRole.DISCOVERER
        } else {
            ConnectionRole.ADVERTISER
        }
        val incompatibleRole = state.connectedEndpoints.isNotEmpty() && state.role != proposedRole
        val satelliteAlreadyConnected = proposedRole == ConnectionRole.DISCOVERER &&
            state.connectedEndpoints.isNotEmpty()
        if (incompatibleRole || satelliteAlreadyConnected ||
            (state.pendingConnection != null && state.pendingConnection.endpointId != endpointId)
        ) {
            client.rejectConnection(endpointId)
            return
        }
        if (ecosystemPreferences.rulesFor(trustedName) != null) {
            _uiState.update {
                it.copy(
                    connectionStage = ConnectionStage.CONNECTING,
                    role = proposedRole,
                    pendingConnection = null,
                    statusMessage = "Reconectando automaticamente a ${identity.displayName.removePrefix("Veyro - ")}...",
                    errorMessage = null
                )
            }
            client.acceptConnection(endpointId).addOnFailureListener(::handleConnectionAttemptFailure)
            return
        }
        _uiState.update {
            it.copy(
                connectionStage = ConnectionStage.AUTHENTICATING,
                role = proposedRole,
                pendingConnection = PendingConnection(
                    endpointId = endpointId,
                    endpointName = trustedName,
                    authenticationDigits = authenticationDigits
                ),
                statusMessage = "Compare o PIN nos dois aparelhos.",
                errorMessage = null
            )
        }
    }

    override fun onConnectionResult(
        endpointId: String,
        endpointName: String?,
        isSuccess: Boolean
    ) {
        if (isSuccess) {
            reconnectJob?.cancel()
            radioDutyCycleJob?.cancel()
            radioDutyCycleJob = null
            cancelConnectionAttempts()
            val identity = endpointIdentities[endpointId]
                ?: endpointName?.let(EndpointIdentity::parse)
            val trustedDevice = ecosystemPreferences.rememberDevice(
                identity?.trustedName ?: _uiState.value.pendingConnection?.endpointName
                    ?: "Dispositivo conectado"
            )
            val endpointRole = _uiState.value.role.takeIf { it != ConnectionRole.NONE }
                ?: if (identity != null && EndpointIdentity.shouldInitiate(localIdentity, identity)) {
                    ConnectionRole.DISCOVERER
                } else {
                    ConnectionRole.ADVERTISER
                }
            clientResult.getOrNull()?.let { client ->
                if (endpointRole == ConnectionRole.ADVERTISER) {
                    client.stopDiscovery()
                } else {
                    stopRadioOperations(client)
                }
            }
            _uiState.update { state ->
                val connected = state.connectedEndpoints
                    .filterNot { it.id == endpointId }
                    .plus(
                        ConnectedEndpoint(
                            id = endpointId,
                            name = trustedDevice.deviceName,
                            stableDeviceId = identity?.deviceId.orEmpty(),
                            role = endpointRole
                        )
                    )
                val selectNew = state.connectedEndpointId == null
                state.copy(
                    role = endpointRole,
                    connectionStage = ConnectionStage.CONNECTED,
                    pendingConnection = null,
                    connectedEndpoints = connected,
                    connectedEndpointId = if (selectNew) endpointId else state.connectedEndpointId,
                    connectedEndpointName = if (selectNew) trustedDevice.deviceName else state.connectedEndpointName,
                    trustedDevices = ecosystemPreferences.trustedDevices(),
                    statusMessage = "${connected.size} aparelho(s) conectado(s).",
                    errorMessage = null
                )
            }
            val features = _uiState.value.featureSettings
            if (features.batterySync) startBatterySync()
            if (features.connectivitySync) startConnectivitySync()
            if (features.ping) startPing()
            if (features.notificationSync) startNotificationSync()
            if (features.mediaControl) startMediaSync()
            if (features.telephonySync) startTelephonySync()
        } else {
            val hasOtherConnections = _uiState.value.connectedEndpoints.isNotEmpty()
            if (!hasOtherConnections) {
                stopBatterySync()
                stopConnectivitySync()
                stopPing()
                stopNotificationSync()
                stopMediaSync()
                stopTelephonySync()
                findMyDeviceAlarm.stop()
            }
            _uiState.update { state ->
                state.copy(
                    connectionStage = if (state.connectedEndpoints.isNotEmpty()) {
                        ConnectionStage.CONNECTED
                    } else if (state.ecosystemEnabled) {
                        ConnectionStage.ACTIVE
                    } else {
                        ConnectionStage.ERROR
                    },
                    pendingConnection = null,
                    statusMessage = "Conexão não concluída; nova tentativa será feita automaticamente.",
                    errorMessage = null
                )
            }
            if (!hasOtherConnections) scheduleRadioRestart()
        }
    }

    override fun onDisconnected(endpointId: String) {
        _uiState.update { state ->
            val remaining = state.connectedEndpoints.filterNot { it.id == endpointId }
            val activeWasRemoved = state.connectedEndpointId == endpointId
            val nextActive = if (activeWasRemoved) remaining.firstOrNull() else
                remaining.firstOrNull { it.id == state.connectedEndpointId }
            state.copy(
                role = if (remaining.isEmpty()) ConnectionRole.NONE else state.role,
                connectionStage = if (remaining.isNotEmpty()) ConnectionStage.CONNECTED else
                    if (state.ecosystemEnabled) ConnectionStage.ACTIVE else ConnectionStage.IDLE,
                connectedEndpoints = remaining,
                connectedEndpointId = nextActive?.id,
                connectedEndpointName = nextActive?.name,
                remoteBatteryStatus = state.remoteBatteryStatus.takeUnless { activeWasRemoved },
                remoteConnectivityStatus = state.remoteConnectivityStatus.takeUnless { activeWasRemoved },
                remotePingStatus = state.remotePingStatus.takeUnless { activeWasRemoved },
                remoteNotifications = state.remoteNotifications.takeUnless { activeWasRemoved }.orEmpty(),
                remoteMediaState = state.remoteMediaState.takeUnless { activeWasRemoved },
                remoteTelecommunicationEvents = state.remoteTelecommunicationEvents.takeUnless {
                    activeWasRemoved
                }.orEmpty(),
                remoteCustomCommandResults = state.remoteCustomCommandResults.takeUnless {
                    activeWasRemoved
                }.orEmpty(),
                remoteSharedUrls = state.remoteSharedUrls.takeUnless { activeWasRemoved }.orEmpty(),
                remoteFileItems = state.remoteFileItems.takeUnless { activeWasRemoved }.orEmpty(),
                remoteFileParentId = state.remoteFileParentId.takeUnless { activeWasRemoved }.orEmpty(),
                remoteFileMessage = state.remoteFileMessage.takeUnless { activeWasRemoved },
                remotePresentationState = state.remotePresentationState.takeUnless {
                    activeWasRemoved
                } ?: RemotePresentationState(),
                statusMessage = if (remaining.isNotEmpty()) {
                    "${remaining.size} aparelho(s) ainda conectado(s)."
                } else if (state.ecosystemEnabled) {
                    "Aparelhos fora de alcance; procurando reconexão..."
                } else {
                    "Todos os aparelhos foram desconectados."
                }
            )
        }
        if (_uiState.value.connectedEndpoints.isEmpty()) {
            stopBatterySync()
            stopConnectivitySync()
            stopPing()
            stopNotificationSync()
            stopMediaSync()
            stopTelephonySync()
            findMyDeviceAlarm.stop()
            if (_uiState.value.ecosystemEnabled) scheduleRadioRestart()
        }
    }

    fun selectConnectedEndpoint(endpointId: String) {
        val endpoint = _uiState.value.connectedEndpoints.firstOrNull { it.id == endpointId } ?: return
        _uiState.update {
            it.copy(
                connectedEndpointId = endpoint.id,
                connectedEndpointName = endpoint.name,
                remoteBatteryStatus = null,
                remoteConnectivityStatus = null,
                remotePingStatus = null,
                remoteNotifications = emptyList(),
                remoteMediaState = null,
                remoteTelecommunicationEvents = emptyList(),
                remoteCustomCommandResults = emptyList(),
                remoteSharedUrls = emptyList(),
                remoteFileItems = emptyList(),
                remoteFileParentId = "",
                remoteFileMessage = null,
                remotePresentationState = RemotePresentationState(),
                statusMessage = "${endpoint.name.removePrefix("Veyro - ")} selecionado."
            )
        }
    }

    fun sendCommand(command: String) {
        if (!_uiState.value.featureSettings.safeCommands) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) return

        val client = clientResult.getOrElse { error ->
            showError(error)
            return
        }
        runCatching {
            client.sendBytes(endpointId, trimmedCommand.toByteArray(Charsets.UTF_8))
        }.onSuccess {
            _uiState.update {
                it.copy(statusMessage = "Comando enviado para o outro aparelho.")
            }
        }.onFailure(::showError)
    }

    fun sendFindDeviceCommand(trigger: FindDeviceTrigger, volumeScalar: Float = 1f) {
        if (!_uiState.value.featureSettings.findDevice) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val request = FindDeviceRequest.newBuilder()
            .setTriggerCommand(trigger)
            .setVolumeScalar(volumeScalar.coerceIn(0f, 1f))
            .build()

        sendFeaturePayload(
            endpointId = endpointId,
            bytes = VeyroProtocolCodec.encodeFindDeviceRequest(request),
            successMessage = when (trigger) {
                FindDeviceTrigger.START_ALARM_SEQUENCE -> "Pedido para localizar aparelho enviado."
                FindDeviceTrigger.TERMINATE_ALARM_SEQUENCE -> "Pedido para parar o alarme enviado."
                else -> "Comando de localização enviado."
            }
        )
    }

    fun sendNotificationDismiss(notificationKey: String) {
        if (!_uiState.value.featureSettings.notificationSync) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        if (notificationKey.isBlank()) return

        val event = NotificationSyncEvent.newBuilder()
            .setSyncAction(NotificationSyncAction.REMOTE_DISMISS_REQUEST)
            .setNotificationKey(notificationKey)
            .build()
        sendFeaturePayload(
            endpointId = endpointId,
            bytes = VeyroProtocolCodec.encodeNotificationSyncEvent(event),
            successMessage = "Solicitação de descarte enviada."
        )
    }

    fun sendMediaControlCommand(category: MediaEventCategory) {
        if (!_uiState.value.featureSettings.mediaControl) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        if (category == MediaEventCategory.STATE_REPORT ||
            category == MediaEventCategory.MEDIA_EVENT_CATEGORY_UNKNOWN ||
            category == MediaEventCategory.UNRECOGNIZED
        ) {
            return
        }

        val event = MediaControlEvent.newBuilder()
            .setEventCategory(category)
            .build()
        sendFeaturePayload(
            endpointId = endpointId,
            bytes = VeyroProtocolCodec.encodeMediaControlEvent(event),
            successMessage = "Comando de mídia enviado."
        )
    }

    fun sendSmsTransmitOrder(address: String, text: String) {
        if (!_uiState.value.featureSettings.telephonySync) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val cleanAddress = address.trim().take(MAX_SMS_ADDRESS_LENGTH)
        val cleanText = text.trim().take(MAX_SMS_TEXT_LENGTH)
        if (cleanAddress.isBlank() || cleanText.isBlank()) return

        val event = TelecommunicationEvent.newBuilder()
            .setTelecommunicationType(TelecommunicationType.SMS_TRANSMIT_ORDER)
            .setAddressNumber(cleanAddress)
            .setTextPayload(cleanText)
            .setEpochTimestamp(System.currentTimeMillis())
            .build()
        sendFeaturePayload(
            endpointId = endpointId,
            bytes = VeyroProtocolCodec.encodeTelecommunicationEvent(event),
            successMessage = "Pedido enviado; o outro aparelho precisará confirmar o SMS."
        )
    }

    fun refreshTelephonySync() {
        if (_uiState.value.featureSettings.telephonySync &&
            _uiState.value.connectedEndpoints.isNotEmpty()
        ) startTelephonySync()
    }

    fun sendSafeCustomCommand(action: String) {
        if (!_uiState.value.featureSettings.safeCommands) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        if (action !in ALLOWED_CUSTOM_COMMANDS) return
        val event = CustomCommandEvent.newBuilder()
            .setCommandTrackingId(UUID.randomUUID().toString())
            .setExecutionTypeCategory(ExecutionTypeCategory.NATIVE_BROADCAST_INTENT)
            .setEncodedCommandString(action)
            .setAwaitOutputConfirmation(true)
            .build()
        sendFeaturePayload(
            endpointId,
            VeyroProtocolCodec.encodeCustomCommandEvent(event),
            "Ação segura enviada; aguardando resultado remoto."
        )
    }

    fun shareUrl(url: String) {
        if (!_uiState.value.featureSettings.sharedLinks) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val cleanUrl = url.trim().take(MAX_URL_LENGTH)
        if (cleanUrl.isBlank()) return
        val event = UrlShareEvent.newBuilder()
            .setHyperlinkTarget(cleanUrl)
            .setRequiresImmediateFocus(false)
            .build()
        sendFeaturePayload(
            endpointId,
            VeyroProtocolCodec.encodeUrlShareEvent(event),
            "Link enviado; o outro aparelho deverá tocar para abri-lo."
        )
    }

    fun shareContact(uri: Uri) {
        if (!_uiState.value.featureSettings.contactSync) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val contact = runCatching { contactSyncManager.readSelectedContact(uri) }
            .getOrElse { error ->
                showFeatureError(error)
                return
            } ?: run {
                showFeatureError(IllegalArgumentException("Não foi possível ler o contato selecionado."))
                return
            }
        val event = ContactSyncEvent.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setAction(ContactSyncAction.CONTACT_OFFER)
            .setContact(contact)
            .build()
        sendFeaturePayload(
            endpointId,
            VeyroProtocolCodec.encodeContactSyncEvent(event),
            "Contato oferecido; aguardando confirmação no outro aparelho."
        )
    }

    fun approveContactImport(requestId: String) {
        val pending = _uiState.value.pendingContactImports.firstOrNull {
            it.requestId == requestId
        } ?: return
        val result = contactSyncManager.importContact(pending.toProtocolRecord())
        val accepted = result.isSuccess
        val message = if (accepted) {
            "${pending.displayName.ifBlank { "Contato" }} importado."
        } else {
            result.exceptionOrNull()?.localizedMessage ?: "Não foi possível importar o contato."
        }
        sendContactImportResult(pending, accepted, message)
        _uiState.update { state ->
            state.copy(
                pendingContactImports = state.pendingContactImports.filterNot {
                    it.requestId == requestId
                },
                lastContactResult = message,
                statusMessage = message,
                errorMessage = if (accepted) null else message
            )
        }
    }

    fun rejectContactImport(requestId: String) {
        val pending = _uiState.value.pendingContactImports.firstOrNull {
            it.requestId == requestId
        } ?: return
        val message = "Importação recusada neste aparelho."
        sendContactImportResult(pending, false, message)
        _uiState.update { state ->
            state.copy(
                pendingContactImports = state.pendingContactImports.filterNot {
                    it.requestId == requestId
                },
                lastContactResult = message,
                statusMessage = message
            )
        }
    }

    fun sendPresentationAction(action: PresentationAction, elapsedMillis: Long = 0L) {
        if (!_uiState.value.featureSettings.presentationMode) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val event = PresentationEvent.newBuilder()
            .setAction(action)
            .setElapsedMillis(elapsedMillis.coerceAtLeast(0L))
            .build()
        sendFeaturePayload(
            endpointId,
            VeyroProtocolCodec.encodePresentationEvent(event),
            "Comando de apresentação enviado."
        )
    }

    fun dismissRemoteBlackout() {
        _uiState.update {
            it.copy(
                remotePresentationState = it.remotePresentationState.copy(blackedOut = false),
                statusMessage = "Tela preta encerrada localmente."
            )
        }
    }

    fun setSharedFolder(uri: Uri) {
        if (!_uiState.value.featureSettings.remoteFiles) return
        sharedFolderManager.shareTree(uri)
            .onSuccess { info ->
                _uiState.update {
                    it.copy(
                        sharedFolderName = info.displayName,
                        statusMessage = "Pasta ${info.displayName} compartilhada explicitamente.",
                        errorMessage = null
                    )
                }
            }
            .onFailure(::showFeatureError)
    }

    fun clearSharedFolder() {
        sharedFolderManager.clearSharedTree()
        _uiState.update {
            it.copy(
                sharedFolderName = null,
                statusMessage = "Compartilhamento da pasta encerrado.",
                errorMessage = null
            )
        }
    }

    fun requestRemoteFileList(parentDocumentId: String = "") {
        if (!_uiState.value.featureSettings.remoteFiles) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val event = RemoteFileEvent.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setAction(RemoteFileAction.LIST_REQUEST)
            .setParentDocumentId(parentDocumentId.take(MAX_DOCUMENT_ID_LENGTH))
            .build()
        sendFeaturePayload(
            endpointId,
            VeyroProtocolCodec.encodeRemoteFileEvent(event),
            "Solicitação segura de pasta enviada."
        )
    }

    fun requestRemoteFileDownload(documentId: String) {
        if (!_uiState.value.featureSettings.remoteFiles || documentId.isBlank()) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val event = RemoteFileEvent.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setAction(RemoteFileAction.DOWNLOAD_REQUEST)
            .setRequestedDocumentId(documentId.take(MAX_DOCUMENT_ID_LENGTH))
            .build()
        sendFeaturePayload(
            endpointId,
            VeyroProtocolCodec.encodeRemoteFileEvent(event),
            "Solicitação de arquivo enviada."
        )
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
        if (!_uiState.value.featureSettings.drawingTablet) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val event = RemoteInputEvent.newBuilder()
            .setInputCommand(RemoteInputCommand.STYLUS_EVENT)
            .setStylusAction(action)
            .setNormalizedX(normalizedX.coerceIn(0f, 1f))
            .setNormalizedY(normalizedY.coerceIn(0f, 1f))
            .setPressure(pressure.coerceIn(0f, 1f))
            .setTiltX(tiltX.coerceIn(-1f, 1f))
            .setTiltY(tiltY.coerceIn(-1f, 1f))
            .setPrimaryButtonPressed(primaryButtonPressed)
            .setIsStylus(isStylus)
            .setMultiPointerCount(1)
            .build()
        runCatching {
            clientResult.getOrNull()?.sendBytes(
                endpointId,
                VeyroProtocolCodec.encodeRemoteInputEvent(event)
            )?.addOnFailureListener(::showFeatureError)
        }.onFailure(::showFeatureError)
    }

    fun sendRemoteInput(
        command: RemoteInputCommand,
        deltaX: Float = 0f,
        deltaY: Float = 0f,
        keyboardText: String = ""
    ) {
        if (!_uiState.value.featureSettings.remoteInput) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        if (command == RemoteInputCommand.REMOTE_INPUT_COMMAND_UNKNOWN ||
            command == RemoteInputCommand.UNRECOGNIZED
        ) return
        val event = RemoteInputEvent.newBuilder()
            .setInputCommand(command)
            .setDeltaAxisX(deltaX.coerceIn(-500f, 500f))
            .setDeltaAxisY(deltaY.coerceIn(-500f, 500f))
            .setKeyboardChar(keyboardText.take(MAX_REMOTE_KEYBOARD_CHUNK))
            .setMultiPointerCount(1)
            .build()
        val client = clientResult.getOrNull() ?: return
        runCatching {
            client.sendBytes(endpointId, VeyroProtocolCodec.encodeRemoteInputEvent(event))
                .addOnFailureListener(::showFeatureError)
        }.onFailure(::showFeatureError)
    }

    override fun onBytesPayloadReceived(endpointId: String, bytes: ByteArray) {
        val featureMessage = VeyroProtocolCodec.decodeFeatureMessage(bytes)
        if (featureMessage != null) {
            when (featureMessage.payloadCase) {
                VeyroMessage.PayloadCase.BATTERY_STATUS -> if (_uiState.value.featureSettings.batterySync)
                    updateRemoteBatteryStatus(endpointId, featureMessage.batteryStatus)

                VeyroMessage.PayloadCase.CONNECTIVITY_STATUS ->
                    if (_uiState.value.featureSettings.connectivitySync) {
                        updateRemoteConnectivityStatus(endpointId, featureMessage.connectivityStatus)
                    }

                VeyroMessage.PayloadCase.PING_EVENT -> if (_uiState.value.featureSettings.ping)
                    handlePingEvent(endpointId, featureMessage.pingEvent)

                VeyroMessage.PayloadCase.FIND_DEVICE_REQUEST -> if (_uiState.value.featureSettings.findDevice)
                    handleFindDeviceRequest(endpointId, featureMessage.findDeviceRequest)

                VeyroMessage.PayloadCase.NOTIFICATION_SYNC_EVENT -> if (_uiState.value.featureSettings.notificationSync)
                    handleNotificationSyncEvent(endpointId, featureMessage.notificationSyncEvent)

                VeyroMessage.PayloadCase.MEDIA_CONTROL_EVENT -> if (_uiState.value.featureSettings.mediaControl)
                    handleMediaControlEvent(endpointId, featureMessage.mediaControlEvent)

                VeyroMessage.PayloadCase.TELECOMMUNICATION_EVENT -> if (_uiState.value.featureSettings.telephonySync)
                    handleTelecommunicationEvent(endpointId, featureMessage.telecommunicationEvent)

                VeyroMessage.PayloadCase.CUSTOM_COMMAND_EVENT -> if (_uiState.value.featureSettings.safeCommands)
                    handleCustomCommandEvent(endpointId, featureMessage.customCommandEvent)

                VeyroMessage.PayloadCase.URL_SHARE_EVENT -> if (_uiState.value.featureSettings.sharedLinks)
                    handleUrlShareEvent(endpointId, featureMessage.urlShareEvent)

                VeyroMessage.PayloadCase.REMOTE_INPUT_EVENT -> {
                    val settings = _uiState.value.featureSettings
                    val event = featureMessage.remoteInputEvent
                    if ((event.inputCommand == RemoteInputCommand.STYLUS_EVENT && settings.drawingTablet) ||
                        (event.inputCommand != RemoteInputCommand.STYLUS_EVENT && settings.remoteInput)
                    ) handleRemoteInputEvent(event)
                }

                VeyroMessage.PayloadCase.CONTACT_SYNC_EVENT -> if (_uiState.value.featureSettings.contactSync)
                    handleContactSyncEvent(endpointId, featureMessage.contactSyncEvent)

                VeyroMessage.PayloadCase.PRESENTATION_EVENT ->
                    if (_uiState.value.featureSettings.presentationMode) {
                        handlePresentationEvent(endpointId, featureMessage.presentationEvent)
                    }

                VeyroMessage.PayloadCase.REMOTE_FILE_EVENT -> if (_uiState.value.featureSettings.remoteFiles)
                    handleRemoteFileEvent(endpointId, featureMessage.remoteFileEvent)

                VeyroMessage.PayloadCase.CLIPBOARD_SYNC_EVENT ->
                    if (_uiState.value.featureSettings.clipboardSync) {
                        handleClipboardSyncEvent(endpointId, featureMessage.clipboardSyncEvent)
                    }

                VeyroMessage.PayloadCase.PAYLOAD_NOT_SET,
                null -> Unit
            }
            return
        }

        val fileMetadata = FileMetadata.fromWireBytes(bytes)
        if (fileMetadata != null) {
            if (!_uiState.value.featureSettings.fileTransfer) return
            fileMetadataByPayloadId[fileMetadata.payloadId] = fileMetadata
            _uiState.update { state ->
                state.copy(
                    rawFileTransfers = state.rawFileTransfers.map { transfer ->
                        if (transfer.payloadId == fileMetadata.payloadId) {
                            transfer.copy(
                                fileName = fileMetadata.fileName,
                                totalBytes = fileMetadata.totalBytes,
                                mimeType = fileMetadata.mimeType
                            )
                        } else {
                            transfer
                        }
                    },
                    statusMessage = "Metadados recebidos: ${fileMetadata.fileName}."
                )
            }
            trySaveIncomingFile(fileMetadata.payloadId)
            return
        }

        if (!_uiState.value.featureSettings.safeCommands) return
        val command = bytes.toString(Charsets.UTF_8)
        val senderName = _uiState.value.connectedEndpointName
            ?.takeIf { _uiState.value.connectedEndpointId == endpointId }
            ?: "Dispositivo conectado"

        _uiState.update { state ->
            state.copy(
                receivedCommands = (state.receivedCommands + ReceivedCommand(senderName, command))
                    .takeLast(MAX_RECEIVED_COMMANDS),
                statusMessage = "Comando recebido de $senderName."
            )
        }
    }

    fun sendFile(uri: Uri) {
        if (!_uiState.value.featureSettings.fileTransfer) return
        val endpointId = _uiState.value.connectedEndpointId ?: return
        val client = clientResult.getOrElse { error ->
            showError(error)
            return
        }

        runCatching { client.sendFile(endpointId, uri) }
            .onSuccess { metadata ->
                fileMetadataByPayloadId[metadata.payloadId] = metadata
                _uiState.update { state ->
                    state.copy(
                        rawFileTransfers = state.rawFileTransfers + RawFileTransfer(
                            payloadId = metadata.payloadId,
                            endpointId = endpointId,
                            direction = RawFileDirection.SEND,
                            fileName = metadata.fileName,
                            totalBytes = metadata.totalBytes,
                            mimeType = metadata.mimeType
                        ),
                        statusMessage = "Enviando ${metadata.fileName}..."
                    )
                }
            }
            .onFailure(::showError)
    }

    override fun onFilePayloadReceived(
        endpointId: String,
        payloadId: Long,
        temporaryUri: Uri
    ) {
        if (!_uiState.value.featureSettings.fileTransfer) return
        val metadata = fileMetadataByPayloadId[payloadId]
        _uiState.update { state ->
            val existingTransfer = state.rawFileTransfers.firstOrNull {
                it.payloadId == payloadId
            }
            state.copy(
                rawFileTransfers = state.rawFileTransfers
                    .filterNot { it.payloadId == payloadId }
                    .plus(
                        existingTransfer?.copy(
                            endpointId = endpointId,
                            direction = RawFileDirection.RECEIVE,
                            temporaryUri = temporaryUri.toString(),
                            fileName = metadata?.fileName ?: existingTransfer.fileName,
                            totalBytes = metadata?.totalBytes ?: existingTransfer.totalBytes,
                            mimeType = metadata?.mimeType ?: existingTransfer.mimeType
                            ) ?: RawFileTransfer(
                                payloadId = payloadId,
                                endpointId = endpointId,
                                direction = RawFileDirection.RECEIVE,
                                temporaryUri = temporaryUri.toString(),
                                fileName = metadata?.fileName,
                                totalBytes = metadata?.totalBytes ?: 0L,
                                mimeType = metadata?.mimeType
                            )
                    ),
                statusMessage = "Arquivo bruto recebido na área temporária."
            )
        }
        trySaveIncomingFile(payloadId)
    }

    override fun onFilePayloadTransferUpdate(
        endpointId: String,
        payloadId: Long,
        bytesTransferred: Long,
        totalBytes: Long,
        status: Int
    ) {
        if (!_uiState.value.featureSettings.fileTransfer) return
        val rawFileStatus = when (status) {
            PayloadTransferUpdate.Status.SUCCESS -> RawFileStatus.COMPLETED
            PayloadTransferUpdate.Status.CANCELED -> RawFileStatus.CANCELED
            PayloadTransferUpdate.Status.IN_PROGRESS -> RawFileStatus.IN_PROGRESS
            else -> RawFileStatus.FAILED
        }
        _uiState.update { state ->
            var activeFileName = "arquivo"
            var activeProgress = 0
            state.copy(
                rawFileTransfers = state.rawFileTransfers.map { transfer ->
                    if (transfer.payloadId == payloadId) {
                        val metadata = fileMetadataByPayloadId[payloadId]
                        val effectiveTotal = (metadata?.totalBytes ?: transfer.totalBytes)
                            .takeIf { it > 0L }
                            ?: totalBytes.coerceAtLeast(0L)
                        val progress = if (effectiveTotal > 0L) {
                            ((bytesTransferred.coerceAtLeast(0L) * 100L) / effectiveTotal)
                                .coerceIn(0L, 100L)
                                .toInt()
                        } else {
                            0
                        }
                        activeFileName = metadata?.fileName ?: transfer.fileName ?: "arquivo"
                        activeProgress = progress
                        transfer.copy(
                            fileName = metadata?.fileName ?: transfer.fileName,
                            totalBytes = effectiveTotal,
                            mimeType = metadata?.mimeType ?: transfer.mimeType,
                            bytesTransferred = bytesTransferred.coerceAtLeast(0L),
                            progressPercent = if (rawFileStatus == RawFileStatus.COMPLETED) 100 else progress,
                            status = rawFileStatus
                        )
                    } else {
                        transfer
                    }
                },
                statusMessage = when (rawFileStatus) {
                    RawFileStatus.COMPLETED -> "$activeFileName concluído."
                    RawFileStatus.AWAITING_APPROVAL -> "$activeFileName aguardando aprovação."
                    RawFileStatus.CANCELED -> "$activeFileName cancelado."
                    RawFileStatus.FAILED -> "Falha ao transferir $activeFileName."
                    RawFileStatus.IN_PROGRESS -> "$activeFileName: $activeProgress%"
                    RawFileStatus.SAVING -> "Salvando $activeFileName..."
                    RawFileStatus.SAVED -> "$activeFileName salvo em Downloads/Veyro."
                }
            )
        }

        when (rawFileStatus) {
            RawFileStatus.COMPLETED -> {
                completedFilePayloadIds[payloadId] = Unit
                trySaveIncomingFile(payloadId)
            }

            RawFileStatus.CANCELED,
            RawFileStatus.FAILED -> completedFilePayloadIds.remove(payloadId)

            RawFileStatus.IN_PROGRESS,
            RawFileStatus.AWAITING_APPROVAL,
            RawFileStatus.SAVING,
            RawFileStatus.SAVED -> Unit
        }
    }

    fun close() {
        reconnectJob?.cancel()
        radioDutyCycleJob?.cancel()
        cancelConnectionAttempts()
        stopBatterySync()
        stopConnectivitySync()
        stopPing()
        stopNotificationSync()
        stopMediaSync()
        stopTelephonySync()
        findMyDeviceAlarm.stop()
        clipboardSyncManager.close()
        runCatching { clientResult.getOrNull()?.close() }
    }

    private fun scheduleDeterministicConnection(
        endpointId: String,
        remoteIdentity: EndpointIdentity
    ) {
        val state = _uiState.value
        if (!state.ecosystemEnabled || state.connectedEndpoints.isNotEmpty() ||
            state.pendingConnection != null ||
            !EndpointIdentity.shouldInitiate(localIdentity, remoteIdentity)
        ) {
            return
        }
        if (connectionAttemptJobs.containsKey(endpointId)) return
        val job = controllerScope.launch {
            delay(
                EndpointIdentity.deterministicJitterMillis(
                    localIdentity.deviceId,
                    remoteIdentity.deviceId
                )
            )
            val current = _uiState.value
            if (current.ecosystemEnabled && current.connectedEndpoints.isEmpty() &&
                current.pendingConnection == null &&
                current.discoveredEndpoints.any { it.id == endpointId }
            ) {
                requestConnectionInternal(endpointId, remoteIdentity.trustedName)
            }
            connectionAttemptJobs.remove(endpointId)
        }
        connectionAttemptJobs[endpointId] = job
    }

    private fun cancelConnectionAttempts() {
        connectionAttemptJobs.values.forEach(Job::cancel)
        connectionAttemptJobs.clear()
    }

    private fun handleConnectionAttemptFailure(error: Throwable) {
        _uiState.update { state ->
            state.copy(
                connectionStage = if (state.ecosystemEnabled) {
                    ConnectionStage.ACTIVE
                } else {
                    ConnectionStage.ERROR
                },
                pendingConnection = null,
                statusMessage = "Conexão adiada; o ecossistema continuará tentando.",
                errorMessage = if (state.ecosystemEnabled) null else
                    (error.localizedMessage ?: "Não foi possível conectar.")
            )
        }
        scheduleRadioRestart()
    }

    private fun scheduleRadioRestart() {
        if (!_uiState.value.ecosystemEnabled) return
        reconnectJob?.cancel()
        reconnectJob = controllerScope.launch {
            delay(RECONNECT_DELAY_MILLIS)
            if (_uiState.value.ecosystemEnabled &&
                _uiState.value.connectedEndpoints.isEmpty() &&
                _uiState.value.pendingConnection == null
            ) {
                applyRadioPolicy()
            }
        }
    }

    private fun applyRadioPolicy() {
        radioDutyCycleJob?.cancel()
        radioDutyCycleJob = null
        val state = _uiState.value
        val client = clientResult.getOrNull() ?: return
        if (!state.ecosystemEnabled || state.connectedEndpoints.isNotEmpty()) {
            if (!state.ecosystemEnabled) stopRadioOperations(client)
            return
        }
        if (state.energyMode == EnergyMode.BATTERY_SAVER && !screenInteractive) {
            radioDutyCycleJob = controllerScope.launch {
                while (isActive && _uiState.value.ecosystemEnabled &&
                    _uiState.value.connectedEndpoints.isEmpty()
                ) {
                    startRadioPair()
                    delay(BATTERY_SAVER_ACTIVE_WINDOW_MILLIS)
                    stopRadioOperations(client)
                    _uiState.update {
                        if (it.connectedEndpoints.isEmpty() && it.ecosystemEnabled) {
                            it.copy(
                                connectionStage = ConnectionStage.ACTIVE,
                                statusMessage = "Economia ativa; próxima varredura em breve."
                            )
                        } else {
                            it
                        }
                    }
                    delay(BATTERY_SAVER_SLEEP_WINDOW_MILLIS)
                }
            }
        } else {
            startRadioPair()
        }
    }

    private fun startRadioPair() {
        val state = _uiState.value
        if (!state.ecosystemEnabled || state.connectedEndpoints.isNotEmpty()) return
        val client = clientResult.getOrNull() ?: return
        stopRadioOperations(client)
        cancelConnectionAttempts()
        endpointIdentities.clear()
        _uiState.update {
            it.copy(
                role = ConnectionRole.NONE,
                connectionStage = ConnectionStage.ACTIVE,
                discoveredEndpoints = emptyList(),
                statusMessage = "Ecossistema ativo: visível e procurando ao mesmo tempo.",
                errorMessage = null
            )
        }
        var successfulRadios = 0
        fun radioSucceeded() {
            successfulRadios += 1
            if (successfulRadios == 2) {
                _uiState.update {
                    it.copy(
                        connectionStage = ConnectionStage.ACTIVE,
                        statusMessage = "Ecossistema contínuo ativo; aguardando aparelhos próximos.",
                        errorMessage = null
                    )
                }
            }
        }
        fun radioFailed(error: Exception) {
            _uiState.update {
                it.copy(
                    connectionStage = ConnectionStage.ACTIVE,
                    statusMessage = "Um rádio não iniciou; nova tentativa será feita.",
                    errorMessage = error.localizedMessage
                )
            }
            scheduleRadioRestart()
        }
        runCatching { client.startAdvertising(localEndpointName) }
            .onSuccess { it.addOnSuccessListener { radioSucceeded() }.addOnFailureListener(::radioFailed) }
            .onFailure { radioFailed(Exception(it)) }
        runCatching { client.startDiscovery() }
            .onSuccess { it.addOnSuccessListener { radioSucceeded() }.addOnFailureListener(::radioFailed) }
            .onFailure { radioFailed(Exception(it)) }
    }

    private fun calculateCapacityScore(): Int {
        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 50) ?: 50
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percentage = if (scale > 0) (level * 100 / scale).coerceIn(0, 100) else 50
        val plugged = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val processorScore = Runtime.getRuntime().availableProcessors().coerceIn(1, 16) * 10
        return (percentage * 4 + processorScore + if (plugged) 400 else 0).coerceIn(0, 999)
    }

    private fun startBatterySync() {
        stopBatterySync()
        if (clientResult.getOrNull() == null) return

        batterySyncJob = controllerScope.launch {
            batteryStatusMonitor.statusUpdates().collect { batteryStatus ->
                broadcastFeaturePayload(VeyroProtocolCodec.encodeBatteryStatus(batteryStatus))
            }
        }
    }

    private fun stopBatterySync() {
        batterySyncJob?.cancel()
        batterySyncJob = null
    }

    private fun startConnectivitySync() {
        stopConnectivitySync()
        if (clientResult.getOrNull() == null) return

        connectivitySyncJob = controllerScope.launch {
            connectivityStatusMonitor.statusUpdates().collect { status ->
                broadcastFeaturePayload(VeyroProtocolCodec.encodeConnectivityStatus(status))
            }
        }
    }

    private fun stopConnectivitySync() {
        connectivitySyncJob?.cancel()
        connectivitySyncJob = null
    }

    private fun startPing() {
        stopPing()
        val client = clientResult.getOrNull() ?: return

        pingJob = controllerScope.launch {
            while (isActive && _uiState.value.connectedEndpoints.isNotEmpty()) {
                val now = SystemClock.elapsedRealtime()
                pendingPings.entries.removeIf { now - it.value.startedAt > PING_TIMEOUT_MILLIS }
                _uiState.value.connectedEndpoints.forEach { endpoint ->
                    val requestId = UUID.randomUUID().toString()
                    pendingPings[requestId] = PendingPing(endpoint.id, now)
                    val event = PingEvent.newBuilder()
                        .setRequestId(requestId)
                        .setAction(PingAction.PING_REQUEST)
                        .build()
                    runCatching {
                        client.sendBytes(endpoint.id, VeyroProtocolCodec.encodePingEvent(event))
                            .addOnFailureListener { pendingPings.remove(requestId) }
                    }.onFailure { pendingPings.remove(requestId) }
                }
                delay(pingIntervalMillis())
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        pendingPings.clear()
    }

    private fun pingIntervalMillis(): Long = when (_uiState.value.energyMode) {
        EnergyMode.CONTINUOUS -> PING_CONTINUOUS_INTERVAL_MILLIS
        EnergyMode.BALANCED -> PING_BALANCED_INTERVAL_MILLIS
        EnergyMode.BATTERY_SAVER -> PING_SAVER_INTERVAL_MILLIS
    }

    private fun startNotificationSync() {
        stopNotificationSync()
        if (clientResult.getOrNull() == null) return

        notificationSyncJob = controllerScope.launch {
            NotificationSyncBridge.activeNotifications().forEach { event ->
                broadcastFeaturePayload(VeyroProtocolCodec.encodeNotificationSyncEvent(event))
            }
            NotificationSyncBridge.events.collect { event ->
                broadcastFeaturePayload(VeyroProtocolCodec.encodeNotificationSyncEvent(event))
            }
        }
    }

    private fun stopNotificationSync() {
        notificationSyncJob?.cancel()
        notificationSyncJob = null
    }

    private fun startTelephonySync() {
        stopTelephonySync()
        if (clientResult.getOrNull() == null) return
        telephonyCallStateMonitor.start()
        telephonySyncJob = controllerScope.launch {
            TelephonySyncBridge.events.collect { event ->
                broadcastFeaturePayload(VeyroProtocolCodec.encodeTelecommunicationEvent(event))
            }
        }
    }

    private fun stopTelephonySync() {
        telephonySyncJob?.cancel()
        telephonySyncJob = null
        telephonyCallStateMonitor.stop()
    }

    private fun startMediaSync() {
        stopMediaSync()
        if (clientResult.getOrNull() == null) return

        mediaSessionCoordinator.start { event ->
            broadcastFeaturePayload(VeyroProtocolCodec.encodeMediaControlEvent(event))
        }.onFailure { error ->
            if (_uiState.value.connectedEndpoints.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        errorMessage = error.localizedMessage
                            ?: "Ative o acesso às notificações para controlar mídia."
                    )
                }
            }
        }
    }

    private fun stopMediaSync() {
        mediaSessionCoordinator.stop()
    }

    private fun handleFindDeviceRequest(endpointId: String, request: FindDeviceRequest) {
        when (request.triggerCommand) {
            FindDeviceTrigger.START_ALARM_SEQUENCE -> {
                val allowed = rulesForEndpoint(endpointId)?.allowFindDevice == true
                if (!allowed) {
                    _uiState.update {
                        it.copy(
                            statusMessage = "Pedido de localização bloqueado pelo Trust Hub.",
                            errorMessage = "Autorize este aparelho nas Definições para permitir o alarme remoto."
                        )
                    }
                    return
                }
                findMyDeviceAlarm.start(request.volumeScalar.takeIf { it > 0f } ?: 1f)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                statusMessage = "Alarme de localização ativo neste aparelho.",
                                errorMessage = null
                            )
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                errorMessage = error.localizedMessage
                                    ?: "Não foi possível iniciar o alarme de localização."
                            )
                        }
                    }
            }

            FindDeviceTrigger.TERMINATE_ALARM_SEQUENCE -> {
                findMyDeviceAlarm.stop()
                _uiState.update {
                    it.copy(
                        statusMessage = "Alarme de localização encerrado.",
                        errorMessage = null
                    )
                }
            }

            FindDeviceTrigger.FIND_DEVICE_TRIGGER_UNKNOWN,
            FindDeviceTrigger.UNRECOGNIZED -> Unit
        }
    }

    private fun rulesForEndpoint(endpointId: String): TrustedDeviceRules? {
        val state = _uiState.value
        val endpoint = state.connectedEndpoints.firstOrNull { it.id == endpointId } ?: return null
        return ecosystemPreferences.rulesFor(endpoint.name)
    }

    private fun handleNotificationSyncEvent(endpointId: String, event: NotificationSyncEvent) {
        when (event.syncAction) {
            NotificationSyncAction.POST_NEW -> {
                if (_uiState.value.connectedEndpointId != endpointId) return
                if (event.notificationKey.isBlank()) return
                val remoteNotification = RemoteNotification(
                    notificationKey = event.notificationKey,
                    packageName = event.packageName,
                    appName = event.appName.ifBlank { event.packageName },
                    title = event.title,
                    textBody = event.textBody,
                    isClearable = event.isClearable
                )
                _uiState.update { state ->
                    state.copy(
                        remoteNotifications = state.remoteNotifications
                            .filterNot { it.notificationKey == remoteNotification.notificationKey }
                            .plus(remoteNotification)
                            .takeLast(MAX_REMOTE_NOTIFICATIONS),
                        statusMessage = "Notificação recebida de ${remoteNotification.appName}.",
                        errorMessage = null
                    )
                }
            }

            NotificationSyncAction.REMOVE_EXISTING -> {
                if (_uiState.value.connectedEndpointId != endpointId) return
                _uiState.update { state ->
                    state.copy(
                        remoteNotifications = state.remoteNotifications.filterNot {
                            it.notificationKey == event.notificationKey
                        },
                        statusMessage = "Notificação remota removida.",
                        errorMessage = null
                    )
                }
            }

            NotificationSyncAction.REMOTE_DISMISS_REQUEST -> {
                val accepted = NotificationSyncBridge.dismiss(event.notificationKey)
                _uiState.update {
                    it.copy(
                        statusMessage = if (accepted) {
                            "Descarte remoto solicitado ao Android."
                        } else {
                            "Ative o acesso às notificações para permitir o descarte."
                        },
                        errorMessage = if (accepted) null else
                            "O serviço de notificações não está conectado."
                    )
                }
            }

            NotificationSyncAction.NOTIFICATION_SYNC_ACTION_UNKNOWN,
            NotificationSyncAction.UNRECOGNIZED -> Unit
        }
    }

    private fun handleMediaControlEvent(endpointId: String, event: MediaControlEvent) {
        if (event.eventCategory == MediaEventCategory.STATE_REPORT) {
            if (_uiState.value.connectedEndpointId != endpointId) return
            val remoteState = RemoteMediaState(
                playbackStatus = event.playbackStatus,
                trackName = event.trackName,
                artistName = event.artistName,
                currentPositionMs = event.currentPositionMs.coerceAtLeast(0L)
            )
            _uiState.update {
                it.copy(
                    remoteMediaState = remoteState,
                    statusMessage = if (remoteState.trackName.isBlank()) {
                        "Nenhuma mídia ativa no outro aparelho."
                    } else {
                        "Mídia remota: ${remoteState.trackName}."
                    },
                    errorMessage = null
                )
            }
            return
        }

        mediaSessionCoordinator.execute(event.eventCategory)
            .onSuccess {
                _uiState.update {
                    it.copy(
                        statusMessage = "Comando aplicado à sessão de mídia local.",
                        errorMessage = null
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.localizedMessage
                            ?: "Não foi possível controlar a sessão de mídia."
                    )
                }
            }
    }

    private fun handleTelecommunicationEvent(endpointId: String, event: TelecommunicationEvent) {
        when (event.telecommunicationType) {
            TelecommunicationType.SMS_TRANSMIT_ORDER -> {
                val accepted = smsApprovalManager.requestApproval(
                    event.addressNumber,
                    event.textPayload
                )
                _uiState.update {
                    it.copy(
                        statusMessage = if (accepted) {
                            "Pedido de SMS recebido. Confirme ou recuse na notificação local."
                        } else {
                            "Pedido de SMS inválido; nada foi enviado."
                        },
                        errorMessage = if (accepted) null else "Destino ou mensagem ausente."
                    )
                }
            }

            TelecommunicationType.INBOUND_CALL,
            TelecommunicationType.MISSED_CALL,
            TelecommunicationType.SMS_RECEIVED_EVENT -> {
                if (_uiState.value.connectedEndpointId != endpointId) return
                val remoteEvent = RemoteTelecommunicationEvent(
                    type = event.telecommunicationType,
                    identityLabel = event.identityLabel.ifBlank {
                        event.addressNumber.ifBlank { "Número desconhecido" }
                    },
                    addressNumber = event.addressNumber,
                    textPayload = event.textPayload,
                    epochTimestamp = event.epochTimestamp
                )
                _uiState.update { state ->
                    state.copy(
                        remoteTelecommunicationEvents =
                            (state.remoteTelecommunicationEvents + remoteEvent)
                                .takeLast(MAX_REMOTE_TELECOMMUNICATION_EVENTS),
                        statusMessage = when (remoteEvent.type) {
                            TelecommunicationType.INBOUND_CALL -> "Chamada recebida no outro aparelho."
                            TelecommunicationType.MISSED_CALL -> "Chamada perdida no outro aparelho."
                            else -> "SMS recebido no outro aparelho."
                        },
                        errorMessage = null
                    )
                }
            }

            TelecommunicationType.TELECOMMUNICATION_TYPE_UNKNOWN,
            TelecommunicationType.UNRECOGNIZED -> Unit
        }
    }

    private fun handleCustomCommandEvent(endpointId: String, event: CustomCommandEvent) {
        if (event.executionTypeCategory == ExecutionTypeCategory.EXECUTION_RESULT) {
            if (_uiState.value.connectedEndpointId != endpointId) return
            val result = RemoteCustomCommandResult(
                trackingId = event.commandTrackingId,
                succeeded = event.executionSucceeded,
                message = event.executionOutput
            )
            _uiState.update { state ->
                state.copy(
                    remoteCustomCommandResults =
                        (state.remoteCustomCommandResults + result)
                            .takeLast(MAX_CUSTOM_COMMAND_RESULTS),
                    statusMessage = result.message,
                    errorMessage = if (result.succeeded) null else result.message
                )
            }
            return
        }

        val result = safeCustomCommandExecutor.execute(event)
        _uiState.update {
            it.copy(
                statusMessage = result.message,
                errorMessage = if (result.succeeded) null else result.message
            )
        }
        if (!event.awaitOutputConfirmation) return
        val response = CustomCommandEvent.newBuilder()
            .setCommandTrackingId(event.commandTrackingId)
            .setExecutionTypeCategory(ExecutionTypeCategory.EXECUTION_RESULT)
            .setExecutionSucceeded(result.succeeded)
            .setExecutionOutput(result.message.take(MAX_COMMAND_OUTPUT_LENGTH))
            .build()
        sendFeaturePayload(
            endpointId,
            VeyroProtocolCodec.encodeCustomCommandEvent(response),
            "Resultado da ação enviado."
        )
    }

    private fun handleUrlShareEvent(endpointId: String, event: UrlShareEvent) {
        if (event.resultMessage.isNotBlank()) {
            if (_uiState.value.connectedEndpointId != endpointId) return
            val item = RemoteSharedUrl(
                url = event.hyperlinkTarget,
                accepted = event.wasAccepted,
                message = event.resultMessage
            )
            _uiState.update { state ->
                state.copy(
                    remoteSharedUrls = (state.remoteSharedUrls + item).takeLast(MAX_SHARED_URLS),
                    statusMessage = item.message,
                    errorMessage = if (item.accepted) null else item.message
                )
            }
            return
        }

        val result = sharedUrlNotificationManager.offer(
            event.hyperlinkTarget,
            event.requiresImmediateFocus
        )
        val localItem = RemoteSharedUrl(result.normalizedUrl, result.accepted, result.message)
        _uiState.update { state ->
            state.copy(
                remoteSharedUrls = (state.remoteSharedUrls + localItem).takeLast(MAX_SHARED_URLS),
                statusMessage = result.message,
                errorMessage = if (result.accepted) null else result.message
            )
        }
        val response = UrlShareEvent.newBuilder()
            .setHyperlinkTarget(result.normalizedUrl.ifBlank { event.hyperlinkTarget.take(MAX_URL_LENGTH) })
            .setWasAccepted(result.accepted)
            .setResultMessage(result.message)
            .build()
        sendFeaturePayload(
            endpointId,
            VeyroProtocolCodec.encodeUrlShareEvent(response),
            "Confirmação do link enviada."
        )
    }

    private fun handleClipboardSyncEvent(endpointId: String, event: ClipboardSyncEvent) {
        if (event.eventId.isBlank() || event.sourceDeviceId.isBlank() ||
            event.sourceDeviceId == localIdentity.deviceId ||
            !ClipboardSyncManager.isSafeText(event.text) ||
            !rememberClipboardEvent(event.eventId)
        ) return

        lastClipboardFingerprint = ClipboardSyncManager.fingerprint(event.text)
        runCatching { clipboardSyncManager.writePlainText(event.text) }
            .onFailure {
                updateClipboardStatus(
                    "O Android não permitiu atualizar o clipboard local.",
                    isError = true
                )
                return
            }

        val sender = _uiState.value.connectedEndpoints.firstOrNull {
            it.id == endpointId
        }?.name?.removePrefix("Veyro - ") ?: "aparelho conectado"
        updateClipboardStatus("Clipboard atualizado por $sender.", isError = false)

        val payload = VeyroProtocolCodec.encodeClipboardSyncEvent(event)
        val client = clientResult.getOrNull() ?: return
        _uiState.value.connectedEndpoints
            .filterNot { it.id == endpointId }
            .forEach { endpoint ->
                runCatching {
                    client.sendBytes(endpoint.id, payload).addOnFailureListener(::showFeatureError)
                }.onFailure(::showFeatureError)
            }
    }

    private fun rememberClipboardEvent(eventId: String): Boolean {
        if (!seenClipboardEventIds.add(eventId)) return false
        while (seenClipboardEventIds.size > MAX_SEEN_CLIPBOARD_EVENTS) {
            val iterator = seenClipboardEventIds.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        return true
    }

    private fun updateClipboardStatus(message: String, isError: Boolean) {
        _uiState.update {
            it.copy(
                clipboardStatus = message,
                statusMessage = message,
                errorMessage = message.takeIf { isError }
            )
        }
    }

    private fun handleRemoteInputEvent(event: RemoteInputEvent) {
        val accepted = RemoteInputBridge.dispatch(event)
        _uiState.update {
            it.copy(
                statusMessage = if (accepted) {
                    "Entrada remota encaminhada ao serviço de acessibilidade."
                } else {
                    "Ative o serviço de acessibilidade do Veyro para receber controles."
                },
                errorMessage = if (accepted) null else
                    "Controle remoto indisponível neste aparelho."
            )
        }
    }

    private fun handleContactSyncEvent(endpointId: String, event: ContactSyncEvent) {
        when (event.action) {
            ContactSyncAction.CONTACT_OFFER -> {
                if (event.requestId.isBlank() || !event.hasContact()) return
                val contact = event.contact
                val senderName = _uiState.value.connectedEndpoints.firstOrNull {
                    it.id == endpointId
                }?.name ?: "Dispositivo conectado"
                val pending = PendingContactImport(
                    requestId = event.requestId.take(MAX_REQUEST_ID_LENGTH),
                    endpointId = endpointId,
                    senderName = senderName,
                    displayName = contact.displayName.take(MAX_CONTACT_NAME_LENGTH),
                    phoneNumbers = contact.phoneNumbersList.map { it.take(MAX_CONTACT_VALUE_LENGTH) }
                        .take(MAX_CONTACT_VALUES),
                    emailAddresses = contact.emailAddressesList.map {
                        it.take(MAX_CONTACT_VALUE_LENGTH)
                    }.take(MAX_CONTACT_VALUES)
                )
                _uiState.update { state ->
                    state.copy(
                        pendingContactImports = state.pendingContactImports
                            .filterNot { it.requestId == pending.requestId }
                            .plus(pending)
                            .takeLast(MAX_PENDING_CONTACTS),
                        statusMessage = "Contato recebido; confirme antes de importar.",
                        errorMessage = null
                    )
                }
            }

            ContactSyncAction.CONTACT_IMPORT_RESULT -> {
                if (_uiState.value.connectedEndpointId != endpointId) return
                _uiState.update {
                    it.copy(
                        lastContactResult = event.resultMessage,
                        statusMessage = event.resultMessage,
                        errorMessage = if (event.accepted) null else event.resultMessage
                    )
                }
            }

            ContactSyncAction.CONTACT_SYNC_ACTION_UNKNOWN,
            ContactSyncAction.UNRECOGNIZED -> Unit
        }
    }

    private fun sendContactImportResult(
        pending: PendingContactImport,
        accepted: Boolean,
        message: String
    ) {
        val event = ContactSyncEvent.newBuilder()
            .setRequestId(pending.requestId)
            .setAction(ContactSyncAction.CONTACT_IMPORT_RESULT)
            .setAccepted(accepted)
            .setResultMessage(message.take(MAX_RESULT_MESSAGE_LENGTH))
            .build()
        sendFeaturePayload(
            pending.endpointId,
            VeyroProtocolCodec.encodeContactSyncEvent(event),
            "Resposta da importação enviada."
        )
    }

    private fun handlePresentationEvent(endpointId: String, event: PresentationEvent) {
        if (_uiState.value.connectedEndpointId != endpointId) return
        _uiState.update { state ->
            val current = state.remotePresentationState
            val updated = when (event.action) {
                PresentationAction.PRESENTATION_START -> current.copy(
                    active = true,
                    elapsedMillis = event.elapsedMillis.coerceAtLeast(0L)
                )
                PresentationAction.PRESENTATION_STOP -> RemotePresentationState()
                PresentationAction.PRESENTATION_BLACKOUT_ON -> current.copy(blackedOut = true)
                PresentationAction.PRESENTATION_BLACKOUT_OFF -> current.copy(blackedOut = false)
                PresentationAction.PRESENTATION_TIMER_SYNC -> current.copy(
                    active = true,
                    elapsedMillis = event.elapsedMillis.coerceAtLeast(0L)
                )
                PresentationAction.PRESENTATION_ACTION_UNKNOWN,
                PresentationAction.UNRECOGNIZED -> current
            }
            state.copy(
                remotePresentationState = updated,
                statusMessage = when {
                    updated.blackedOut -> "Tela preta solicitada pela apresentação remota."
                    updated.active -> "Apresentação remota em andamento."
                    else -> "Apresentação remota encerrada."
                },
                errorMessage = null
            )
        }
    }

    private fun handleRemoteFileEvent(endpointId: String, event: RemoteFileEvent) {
        when (event.action) {
            RemoteFileAction.LIST_REQUEST -> {
                val result = sharedFolderManager.listChildren(event.parentDocumentId)
                val response = RemoteFileEvent.newBuilder()
                    .setRequestId(event.requestId.take(MAX_REQUEST_ID_LENGTH))
                    .setAction(RemoteFileAction.LIST_RESPONSE)
                    .setParentDocumentId(event.parentDocumentId.take(MAX_DOCUMENT_ID_LENGTH))
                    .apply {
                        result.onSuccess(::addAllEntries)
                        result.exceptionOrNull()?.localizedMessage?.let {
                            resultMessage = it.take(MAX_RESULT_MESSAGE_LENGTH)
                        }
                    }
                    .build()
                sendFeaturePayload(
                    endpointId,
                    VeyroProtocolCodec.encodeRemoteFileEvent(response),
                    "Conteúdo da pasta compartilhada enviado."
                )
            }

            RemoteFileAction.LIST_RESPONSE -> {
                if (_uiState.value.connectedEndpointId != endpointId) return
                val items = event.entriesList.map { it.toRemoteFileItem() }
                _uiState.update {
                    val message = event.resultMessage.ifBlank {
                        if (items.isEmpty()) "A pasta remota está vazia." else
                            "${items.size} item(ns) na pasta compartilhada."
                    }
                    it.copy(
                        remoteFileItems = items,
                        remoteFileParentId = event.parentDocumentId,
                        remoteFileMessage = message,
                        statusMessage = message,
                        errorMessage = event.resultMessage.takeIf(String::isNotBlank)
                    )
                }
            }

            RemoteFileAction.DOWNLOAD_REQUEST -> {
                val documentUri = sharedFolderManager.documentUri(event.requestedDocumentId)
                val client = clientResult.getOrNull()
                if (documentUri != null && client != null &&
                    _uiState.value.featureSettings.fileTransfer
                ) {
                    runCatching { client.sendFile(endpointId, documentUri) }
                        .onSuccess { metadata ->
                            fileMetadataByPayloadId[metadata.payloadId] = metadata
                            _uiState.update { state ->
                                state.copy(
                                    rawFileTransfers = state.rawFileTransfers + RawFileTransfer(
                                        payloadId = metadata.payloadId,
                                        endpointId = endpointId,
                                        direction = RawFileDirection.SEND,
                                        fileName = metadata.fileName,
                                        totalBytes = metadata.totalBytes,
                                        mimeType = metadata.mimeType
                                    ),
                                    statusMessage = "Enviando ${metadata.fileName} da pasta compartilhada..."
                                )
                            }
                        }
                        .onFailure(::showFeatureError)
                } else {
                    val rejection = RemoteFileEvent.newBuilder()
                        .setRequestId(event.requestId.take(MAX_REQUEST_ID_LENGTH))
                        .setAction(RemoteFileAction.DOWNLOAD_REJECTED)
                        .setResultMessage("Arquivo indisponível ou fora da pasta compartilhada.")
                        .build()
                    sendFeaturePayload(
                        endpointId,
                        VeyroProtocolCodec.encodeRemoteFileEvent(rejection),
                        "Solicitação de arquivo recusada com segurança."
                    )
                }
            }

            RemoteFileAction.DOWNLOAD_REJECTED -> {
                if (_uiState.value.connectedEndpointId != endpointId) return
                _uiState.update {
                    it.copy(
                        remoteFileMessage = event.resultMessage,
                        statusMessage = event.resultMessage,
                        errorMessage = event.resultMessage
                    )
                }
            }

            RemoteFileAction.REMOTE_FILE_ACTION_UNKNOWN,
            RemoteFileAction.UNRECOGNIZED -> Unit
        }
    }

    private fun RemoteFileEntry.toRemoteFileItem(): RemoteFileItem = RemoteFileItem(
        documentId = documentId,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes.coerceAtLeast(0L),
        isDirectory = isDirectory
    )

    private fun sendFeaturePayload(
        endpointId: String,
        bytes: ByteArray,
        successMessage: String
    ) {
        val client = clientResult.getOrElse { error ->
            showError(error)
            return
        }
        runCatching { client.sendBytes(endpointId, bytes) }
            .onSuccess { task ->
                task.addOnSuccessListener {
                    _uiState.update {
                        it.copy(statusMessage = successMessage, errorMessage = null)
                    }
                }.addOnFailureListener(::showFeatureError)
            }
            .onFailure(::showFeatureError)
    }

    private fun broadcastFeaturePayload(bytes: ByteArray) {
        val client = clientResult.getOrNull() ?: return
        _uiState.value.connectedEndpoints.forEach { endpoint ->
            runCatching {
                client.sendBytes(endpoint.id, bytes).addOnFailureListener(::showFeatureError)
            }.onFailure(::showFeatureError)
        }
    }

    private fun showFeatureError(error: Throwable) {
        _uiState.update {
            it.copy(
                errorMessage = error.localizedMessage
                    ?: "Não foi possível enviar a mensagem da funcionalidade."
            )
        }
    }

    private fun updateRemoteBatteryStatus(endpointId: String, status: BatteryStatus) {
        if (_uiState.value.connectedEndpointId != endpointId) return

        val remoteStatus = RemoteBatteryStatus(
            chargePercentage = status.chargePercentage.coerceIn(0, 100),
            isPluggedIn = status.isPluggedIn,
            powerSourceLabel = status.powerSourceType.toDisplayLabel(),
            eventTimestamp = status.eventTimestamp
        )
        _uiState.update {
            it.copy(
                remoteBatteryStatus = remoteStatus,
                statusMessage = "Bateria remota: ${remoteStatus.chargePercentage}%.",
                errorMessage = null
            )
        }
    }

    private fun updateRemoteConnectivityStatus(
        endpointId: String,
        status: ConnectivityStatus
    ) {
        if (_uiState.value.connectedEndpointId != endpointId) return
        val remoteStatus = RemoteConnectivityStatus(
            transportLabel = status.activeTransport.toDisplayLabel(),
            hasInternet = status.hasInternet,
            isMetered = status.isMetered,
            signalStrengthDbm = status.signalStrengthDbm.takeIf {
                status.hasSignalStrength
            },
            eventTimestamp = status.eventTimestamp
        )
        _uiState.update {
            it.copy(
                remoteConnectivityStatus = remoteStatus,
                statusMessage = "Conectividade remota: ${remoteStatus.transportLabel}.",
                errorMessage = null
            )
        }
    }

    private fun handlePingEvent(endpointId: String, event: PingEvent) {
        if (_uiState.value.connectedEndpoints.none { it.id == endpointId } || event.requestId.isBlank()) return
        when (event.action) {
            PingAction.PING_REQUEST -> {
                val response = PingEvent.newBuilder()
                    .setRequestId(event.requestId.take(MAX_PING_ID_LENGTH))
                    .setAction(PingAction.PING_RESPONSE)
                    .build()
                runCatching {
                    clientResult.getOrNull()?.sendBytes(
                        endpointId,
                        VeyroProtocolCodec.encodePingEvent(response)
                    )
                }
            }

            PingAction.PING_RESPONSE -> {
                val pending = pendingPings.remove(event.requestId) ?: return
                if (pending.endpointId != endpointId) return
                val roundTrip = (SystemClock.elapsedRealtime() - pending.startedAt).coerceAtLeast(0L)
                if (_uiState.value.connectedEndpointId != endpointId) return
                _uiState.update {
                    it.copy(
                        remotePingStatus = RemotePingStatus(
                            roundTripMillis = roundTrip,
                            measuredAt = System.currentTimeMillis()
                        ),
                        statusMessage = "Ping P2P: ${roundTrip} ms.",
                        errorMessage = null
                    )
                }
            }

            PingAction.PING_ACTION_UNKNOWN,
            PingAction.UNRECOGNIZED -> Unit
        }
    }

    private fun runTask(
        taskProvider: () -> Task<Void>,
        onSuccess: () -> Unit
    ) {
        runCatching(taskProvider)
            .onSuccess { task ->
                task.addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(::showError)
            }
            .onFailure(::showError)
    }

    private fun stopRadioOperations(client: NearbyConnectionsClient) {
        client.stopAdvertising()
        client.stopDiscovery()
    }

    private fun trySaveIncomingFile(payloadId: Long) {
        if (!_uiState.value.featureSettings.fileTransfer) return
        if (!completedFilePayloadIds.containsKey(payloadId)) return
        val transfer = _uiState.value.rawFileTransfers.firstOrNull {
            it.payloadId == payloadId && it.direction == RawFileDirection.RECEIVE
        } ?: return
        val temporaryUri = transfer.temporaryUri?.let(Uri::parse) ?: return
        val metadata = fileMetadataByPayloadId[payloadId] ?: return
        val autoAcceptFiles = ecosystemPreferences
            .rulesFor(_uiState.value.connectedEndpoints.firstOrNull {
                it.id == transfer.endpointId
            }?.name)
            ?.autoAcceptFiles == true
        if (!autoAcceptFiles && !approvedFilePayloadIds.containsKey(payloadId)) {
            _uiState.update { state ->
                state.copy(
                    rawFileTransfers = state.rawFileTransfers.map { item ->
                        if (item.payloadId == payloadId) {
                            item.copy(status = RawFileStatus.AWAITING_APPROVAL)
                        } else {
                            item
                        }
                    },
                    statusMessage = "${metadata.fileName} aguarda sua aprovação para ser salvo.",
                    errorMessage = null
                )
            }
            return
        }
        if (savingFilePayloadIds.putIfAbsent(payloadId, Unit) != null) return

        _uiState.update { state ->
            state.copy(
                rawFileTransfers = state.rawFileTransfers.map { item ->
                    if (item.payloadId == payloadId) {
                        item.copy(status = RawFileStatus.SAVING)
                    } else {
                        item
                    }
                },
                statusMessage = "Salvando ${metadata.fileName} em Downloads/Veyro...",
                errorMessage = null
            )
        }

        controllerScope.launch {
            runCatching {
                receivedFileStorage.saveReceivedFile(temporaryUri, metadata)
            }.onSuccess { savedFile ->
                _uiState.update { state ->
                    state.copy(
                        rawFileTransfers = state.rawFileTransfers.map { item ->
                            if (item.payloadId == payloadId) {
                                item.copy(
                                    fileName = savedFile.displayName,
                                    bytesTransferred = metadata.totalBytes,
                                    progressPercent = 100,
                                    status = RawFileStatus.SAVED,
                                    savedUri = savedFile.uri.toString()
                                )
                            } else {
                                item
                            }
                        },
                        statusMessage = "${savedFile.displayName} salvo em Downloads/Veyro.",
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        rawFileTransfers = state.rawFileTransfers.map { item ->
                            if (item.payloadId == payloadId) {
                                item.copy(status = RawFileStatus.FAILED)
                            } else {
                                item
                            }
                        },
                        statusMessage = "Falha ao salvar ${metadata.fileName}.",
                        errorMessage = error.localizedMessage
                            ?: "Não foi possível salvar o arquivo recebido."
                    )
                }
            }
            savingFilePayloadIds.remove(payloadId)
            completedFilePayloadIds.remove(payloadId)
            approvedFilePayloadIds.remove(payloadId)
            fileMetadataByPayloadId.remove(payloadId)
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update {
            it.copy(
                connectionStage = ConnectionStage.ERROR,
                errorMessage = error.localizedMessage ?: "Erro inesperado na conexão Nearby."
            )
        }
    }

    private fun stageForRole(role: ConnectionRole): ConnectionStage = when (role) {
        ConnectionRole.ADVERTISER -> ConnectionStage.ADVERTISING
        ConnectionRole.DISCOVERER -> ConnectionStage.DISCOVERING
        ConnectionRole.NONE -> ConnectionStage.IDLE
    }

    private fun PowerSourceType.toDisplayLabel(): String = when (this) {
        PowerSourceType.AC_WALL_OUTLET -> "Tomada"
        PowerSourceType.USB_COMPUTER_PORT -> "USB"
        PowerSourceType.WIRELESS_QI -> "Carregamento sem fio"
        PowerSourceType.UNKNOWN_SOURCE,
        PowerSourceType.UNRECOGNIZED -> "Fonte desconhecida"
    }

    private fun NetworkTransport.toDisplayLabel(): String = when (this) {
        NetworkTransport.NETWORK_TRANSPORT_WIFI -> "Wi-Fi"
        NetworkTransport.NETWORK_TRANSPORT_CELLULAR -> "Rede móvel"
        NetworkTransport.NETWORK_TRANSPORT_ETHERNET -> "Ethernet"
        NetworkTransport.NETWORK_TRANSPORT_BLUETOOTH -> "Bluetooth"
        NetworkTransport.NETWORK_TRANSPORT_VPN -> "VPN"
        NetworkTransport.NETWORK_TRANSPORT_NONE -> "Sem rede"
        NetworkTransport.NETWORK_TRANSPORT_OTHER -> "Outra rede"
        NetworkTransport.NETWORK_TRANSPORT_UNKNOWN,
        NetworkTransport.UNRECOGNIZED -> "Desconhecida"
    }

    private companion object {
        const val MAX_RECEIVED_COMMANDS = 50
        const val MAX_REMOTE_NOTIFICATIONS = 50
        const val MAX_REMOTE_TELECOMMUNICATION_EVENTS = 50
        const val MAX_SMS_ADDRESS_LENGTH = 64
        const val MAX_SMS_TEXT_LENGTH = 8_000
        const val MAX_CUSTOM_COMMAND_RESULTS = 20
        const val MAX_COMMAND_OUTPUT_LENGTH = 500
        const val MAX_SHARED_URLS = 20
        const val MAX_URL_LENGTH = 2_048
        const val MAX_REMOTE_KEYBOARD_CHUNK = 64
        const val MAX_PING_ID_LENGTH = 80
        const val MAX_REQUEST_ID_LENGTH = 80
        const val MAX_RESULT_MESSAGE_LENGTH = 500
        const val MAX_DOCUMENT_ID_LENGTH = 1_024
        const val MAX_CONTACT_NAME_LENGTH = 160
        const val MAX_CONTACT_VALUE_LENGTH = 320
        const val MAX_CONTACT_VALUES = 20
        const val MAX_PENDING_CONTACTS = 20
        const val MAX_SEEN_CLIPBOARD_EVENTS = 128
        const val PING_TIMEOUT_MILLIS = 2 * 60 * 1000L
        const val PING_CONTINUOUS_INTERVAL_MILLIS = 10_000L
        const val PING_BALANCED_INTERVAL_MILLIS = 20_000L
        const val PING_SAVER_INTERVAL_MILLIS = 60_000L
        const val RECONNECT_DELAY_MILLIS = 1_500L
        const val BATTERY_SAVER_ACTIVE_WINDOW_MILLIS = 15_000L
        const val BATTERY_SAVER_SLEEP_WINDOW_MILLIS = 105_000L
        val ALLOWED_CUSTOM_COMMANDS = setOf(
            SafeCustomCommandExecutor.ACTION_VOLUME_UP,
            SafeCustomCommandExecutor.ACTION_VOLUME_DOWN,
            SafeCustomCommandExecutor.ACTION_TORCH_ON,
            SafeCustomCommandExecutor.ACTION_TORCH_OFF
        )
    }
}

class NearbyViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        NearbyClientUiState(
            status = NearbyClientStatus.INITIALIZING,
            statusMessage = "Conectando ao serviço de transferência..."
        )
    )
    val uiState: StateFlow<NearbyClientUiState> = _uiState.asStateFlow()

    private var transferService: P2PTransferService? = null
    private var stateCollectionJob: Job? = null
    private var isServiceBound = false
    private var syncClipboardWhenServiceConnects = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? P2PTransferService.LocalBinder)?.service
            if (service == null) {
                showServiceError("Não foi possível acessar o serviço de transferência.")
                return
            }

            transferService = service
            if (syncClipboardWhenServiceConnects) {
                syncClipboardWhenServiceConnects = false
                service.syncClipboard(manual = false)
            }
            stateCollectionJob?.cancel()
            stateCollectionJob = viewModelScope.launch {
                service.uiState.collect { state ->
                    _uiState.value = state
                }
            }
            if (service.uiState.value.ecosystemEnabled &&
                service.uiState.value.connectionStage == ConnectionStage.IDLE
            ) {
                startForegroundAction(P2PTransferService.ACTION_START_ECOSYSTEM)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            transferService = null
            stateCollectionJob?.cancel()
            showServiceError("O serviço de transferência foi desconectado.")
        }
    }

    init {
        val serviceIntent = Intent(application, P2PTransferService::class.java)
        isServiceBound = application.bindService(
            serviceIntent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        if (!isServiceBound) {
            showServiceError("Não foi possível iniciar o serviço de transferência.")
        }
    }

    fun startAdvertising() {
        startForegroundAction(P2PTransferService.ACTION_START_ECOSYSTEM)
    }

    fun startDiscovery() {
        startForegroundAction(P2PTransferService.ACTION_START_ECOSYSTEM)
    }

    fun requestConnection(endpointId: String) {
        withService { it.requestConnection(endpointId) }
    }

    fun selectConnectedEndpoint(endpointId: String) {
        withService { it.selectConnectedEndpoint(endpointId) }
    }

    fun acceptPendingConnection() {
        withService(P2PTransferService::acceptPendingConnection)
    }

    fun rejectPendingConnection() {
        withService(P2PTransferService::rejectPendingConnection)
    }

    fun sendCommand(command: String) {
        withService { it.sendCommand(command) }
    }

    fun sendFile(uri: Uri) {
        withService { it.sendFile(uri) }
    }

    fun startRemoteFindAlarm() {
        withService { it.sendFindDeviceCommand(FindDeviceTrigger.START_ALARM_SEQUENCE) }
    }

    fun stopRemoteFindAlarm() {
        withService { it.sendFindDeviceCommand(FindDeviceTrigger.TERMINATE_ALARM_SEQUENCE) }
    }

    fun dismissRemoteNotification(notificationKey: String) {
        withService { it.sendNotificationDismiss(notificationKey) }
    }

    fun sendMediaControlCommand(category: MediaEventCategory) {
        withService { it.sendMediaControlCommand(category) }
    }

    fun sendSmsTransmitOrder(address: String, text: String) {
        withService { it.sendSmsTransmitOrder(address, text) }
    }

    fun refreshTelephonySync() {
        withService(P2PTransferService::refreshTelephonySync)
    }

    fun sendSafeCustomCommand(action: String) {
        withService { it.sendSafeCustomCommand(action) }
    }

    fun shareUrl(url: String) {
        withService { it.shareUrl(url) }
    }

    fun syncClipboard() {
        withService { it.syncClipboard(manual = true) }
    }

    fun syncClipboardFromForeground() {
        val service = transferService
        if (service == null) {
            syncClipboardWhenServiceConnects = true
        } else {
            service.syncClipboard(manual = false)
        }
    }

    fun sendRemoteInput(
        command: RemoteInputCommand,
        deltaX: Float = 0f,
        deltaY: Float = 0f,
        keyboardText: String = ""
    ) {
        withService { it.sendRemoteInput(command, deltaX, deltaY, keyboardText) }
    }

    fun shareContact(uri: Uri) {
        withService { it.shareContact(uri) }
    }

    fun approveContactImport(requestId: String) {
        withService { it.approveContactImport(requestId) }
    }

    fun rejectContactImport(requestId: String) {
        withService { it.rejectContactImport(requestId) }
    }

    fun sendPresentationAction(action: PresentationAction, elapsedMillis: Long = 0L) {
        withService { it.sendPresentationAction(action, elapsedMillis) }
    }

    fun dismissRemoteBlackout() {
        withService(P2PTransferService::dismissRemoteBlackout)
    }

    fun setSharedFolder(uri: Uri) {
        withService { it.setSharedFolder(uri) }
    }

    fun clearSharedFolder() {
        withService(P2PTransferService::clearSharedFolder)
    }

    fun requestRemoteFileList(parentDocumentId: String = "") {
        withService { it.requestRemoteFileList(parentDocumentId) }
    }

    fun requestRemoteFileDownload(documentId: String) {
        withService { it.requestRemoteFileDownload(documentId) }
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
        withService {
            it.sendStylusEvent(
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
    }

    fun updateTrustedDeviceRules(rules: TrustedDeviceRules) {
        withService { it.updateTrustedDeviceRules(rules) }
    }

    fun removeTrustedDevice(deviceName: String) {
        withService { it.removeTrustedDevice(deviceName) }
    }

    fun setEnergyMode(mode: EnergyMode) {
        withService { it.setEnergyMode(mode) }
    }

    fun setAppLanguage(language: AppLanguage) {
        withService { it.setAppLanguage(language) }
    }

    fun setFeatureSettings(settings: FeatureSettings) {
        withService { it.setFeatureSettings(settings) }
    }

    fun approveIncomingFile(payloadId: Long) {
        withService { it.approveIncomingFile(payloadId) }
    }

    fun rejectIncomingFile(payloadId: Long) {
        withService { it.rejectIncomingFile(payloadId) }
    }

    fun stopSession() {
        transferService?.stopSession() ?: showServiceError(
            "O serviço ainda não está disponível para encerrar a sessão."
        )
    }

    override fun onCleared() {
        stateCollectionJob?.cancel()
        if (isServiceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onCleared()
    }

    private fun startForegroundAction(action: String) {
        runCatching {
            val context = getApplication<Application>()
            ContextCompat.startForegroundService(
                context,
                Intent(context, P2PTransferService::class.java).setAction(action)
            )
        }.onFailure { error ->
            showServiceError(
                error.localizedMessage
                    ?: "Não foi possível iniciar a transferência em segundo plano."
            )
        }
    }

    private inline fun withService(action: (P2PTransferService) -> Unit) {
        val service = transferService
        if (service == null) {
            showServiceError("Aguarde o serviço de transferência ficar pronto.")
        } else {
            action(service)
        }
    }

    private fun showServiceError(message: String) {
        _uiState.update {
            it.copy(
                status = NearbyClientStatus.ERROR,
                connectionStage = ConnectionStage.ERROR,
                errorMessage = message
            )
        }
    }
}
