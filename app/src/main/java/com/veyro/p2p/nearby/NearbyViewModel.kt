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
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.tasks.Task
import com.veyro.p2p.features.battery.BatteryStatusMonitor
import com.veyro.p2p.features.commands.SafeCustomCommandExecutor
import com.veyro.p2p.features.finddevice.FindMyDeviceAlarmController
import com.veyro.p2p.features.media.MediaSessionCoordinator
import com.veyro.p2p.features.notifications.NotificationSyncBridge
import com.veyro.p2p.features.remoteinput.RemoteInputBridge
import com.veyro.p2p.features.shareurl.SharedUrlNotificationManager
import com.veyro.p2p.features.telephony.SmsApprovalManager
import com.veyro.p2p.features.telephony.TelephonyCallStateMonitor
import com.veyro.p2p.features.telephony.TelephonySyncBridge
import com.veyro.p2p.protocol.BatteryStatus
import com.veyro.p2p.protocol.CustomCommandEvent
import com.veyro.p2p.protocol.ExecutionTypeCategory
import com.veyro.p2p.protocol.FindDeviceRequest
import com.veyro.p2p.protocol.FindDeviceTrigger
import com.veyro.p2p.protocol.MediaControlEvent
import com.veyro.p2p.protocol.MediaEventCategory
import com.veyro.p2p.protocol.NotificationSyncAction
import com.veyro.p2p.protocol.NotificationSyncEvent
import com.veyro.p2p.protocol.PowerSourceType
import com.veyro.p2p.protocol.RemoteInputCommand
import com.veyro.p2p.protocol.RemoteInputEvent
import com.veyro.p2p.protocol.TelecommunicationEvent
import com.veyro.p2p.protocol.TelecommunicationType
import com.veyro.p2p.protocol.UrlShareEvent
import com.veyro.p2p.protocol.VeyroProtocolCodec
import com.veyro.p2p.protocol.VeyroMessage
import com.veyro.p2p.service.P2PTransferService
import com.veyro.p2p.settings.EcosystemPreferences
import com.veyro.p2p.settings.EnergyMode
import com.veyro.p2p.settings.AppLanguage
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
    val connectedEndpointId: String? = null,
    val connectedEndpointName: String? = null,
    val remoteBatteryStatus: RemoteBatteryStatus? = null,
    val remoteNotifications: List<RemoteNotification> = emptyList(),
    val remoteMediaState: RemoteMediaState? = null,
    val remoteTelecommunicationEvents: List<RemoteTelecommunicationEvent> = emptyList(),
    val remoteCustomCommandResults: List<RemoteCustomCommandResult> = emptyList(),
    val remoteSharedUrls: List<RemoteSharedUrl> = emptyList(),
    val receivedCommands: List<ReceivedCommand> = emptyList(),
    val rawFileTransfers: List<RawFileTransfer> = emptyList(),
    val trustedDevices: List<TrustedDeviceRules> = emptyList(),
    val energyMode: EnergyMode = EnergyMode.BALANCED,
    val appLanguage: AppLanguage = AppLanguage.PORTUGUESE,
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
    private val findMyDeviceAlarm = FindMyDeviceAlarmController(application)
    private val mediaSessionCoordinator = MediaSessionCoordinator(application)
    private val telephonyCallStateMonitor = TelephonyCallStateMonitor(application)
    private val smsApprovalManager = SmsApprovalManager(application)
    private val safeCustomCommandExecutor = SafeCustomCommandExecutor(application)
    private val sharedUrlNotificationManager = SharedUrlNotificationManager(application)
    private var batterySyncJob: Job? = null
    private var notificationSyncJob: Job? = null
    private var telephonySyncJob: Job? = null
    private var radioDutyCycleJob: Job? = null
    private var reconnectJob: Job? = null
    private var screenInteractive: Boolean = true

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
                    ecosystemEnabled = ecosystemPreferences.ecosystemEnabled(),
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
                    ecosystemEnabled = ecosystemPreferences.ecosystemEnabled(),
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
        if (_uiState.value.connectionStage == ConnectionStage.CONNECTED) {
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
        if (state.connectedEndpointId != null || state.pendingConnection != null ||
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
        stopNotificationSync()
        stopMediaSync()
        stopTelephonySync()
        findMyDeviceAlarm.stop()
        clientResult.getOrNull()?.let { client ->
            _uiState.value.connectedEndpointId?.let(client::disconnectFromEndpoint)
            stopRadioOperations(client)
        }
        fileMetadataByPayloadId.clear()
        completedFilePayloadIds.clear()
        approvedFilePayloadIds.clear()
        endpointIdentities.clear()
        _uiState.update {
            it.copy(
                role = ConnectionRole.NONE,
                connectionStage = ConnectionStage.IDLE,
                discoveredEndpoints = emptyList(),
                pendingConnection = null,
                connectedEndpointId = null,
                connectedEndpointName = null,
                remoteBatteryStatus = null,
                remoteNotifications = emptyList(),
                remoteMediaState = null,
                remoteTelecommunicationEvents = emptyList(),
                remoteCustomCommandResults = emptyList(),
                remoteSharedUrls = emptyList(),
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

    fun onScreenStateChanged(interactive: Boolean) {
        screenInteractive = interactive
        applyRadioPolicy()
    }

    fun approveIncomingFile(payloadId: Long) {
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
        if (_uiState.value.connectedEndpointId != null &&
            _uiState.value.connectedEndpointId != endpointId
        ) {
            client.rejectConnection(endpointId)
            return
        }
        if (ecosystemPreferences.rulesFor(trustedName) != null) {
            _uiState.update {
                it.copy(
                    connectionStage = ConnectionStage.CONNECTING,
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
            cancelConnectionAttempts()
            reconnectJob?.cancel()
            val identity = endpointIdentities[endpointId]
                ?: endpointName?.let(EndpointIdentity::parse)
            val trustedDevice = ecosystemPreferences.rememberDevice(
                identity?.trustedName ?: _uiState.value.pendingConnection?.endpointName
                    ?: "Dispositivo conectado"
            )
            clientResult.getOrNull()?.let(::stopRadioOperations)
            _uiState.update { state ->
                state.copy(
                    connectionStage = ConnectionStage.CONNECTED,
                    pendingConnection = null,
                    connectedEndpointId = endpointId,
                    connectedEndpointName = trustedDevice.deviceName,
                    trustedDevices = ecosystemPreferences.trustedDevices(),
                    statusMessage = "Conexão P2P estabelecida.",
                    errorMessage = null
                )
            }
            startBatterySync(endpointId)
            startNotificationSync(endpointId)
            startMediaSync(endpointId)
            startTelephonySync(endpointId)
        } else {
            stopBatterySync()
            stopNotificationSync()
            stopMediaSync()
            stopTelephonySync()
            findMyDeviceAlarm.stop()
            _uiState.update {
                it.copy(
                    connectionStage = if (it.ecosystemEnabled) {
                        ConnectionStage.ACTIVE
                    } else {
                        ConnectionStage.ERROR
                    },
                    pendingConnection = null,
                    statusMessage = "Conexão não concluída; nova tentativa será feita automaticamente.",
                    errorMessage = null
                )
            }
            scheduleRadioRestart()
        }
    }

    override fun onDisconnected(endpointId: String) {
        stopBatterySync()
        stopNotificationSync()
        stopMediaSync()
        stopTelephonySync()
        findMyDeviceAlarm.stop()
        _uiState.update { state ->
            if (state.connectedEndpointId == endpointId) {
                state.copy(
                    role = ConnectionRole.NONE,
                    connectionStage = if (state.ecosystemEnabled) {
                        ConnectionStage.ACTIVE
                    } else {
                        ConnectionStage.IDLE
                    },
                    connectedEndpointId = null,
                    connectedEndpointName = null,
                    remoteBatteryStatus = null,
                    remoteNotifications = emptyList(),
                    remoteMediaState = null,
                    remoteTelecommunicationEvents = emptyList(),
                    remoteCustomCommandResults = emptyList(),
                    remoteSharedUrls = emptyList(),
                    statusMessage = if (state.ecosystemEnabled) {
                        "Aparelho fora de alcance; procurando reconexão..."
                    } else {
                        "O outro aparelho foi desconectado."
                    }
                )
            } else {
                state
            }
        }
        if (_uiState.value.ecosystemEnabled) scheduleRadioRestart()
    }

    fun sendCommand(command: String) {
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
        _uiState.value.connectedEndpointId?.let(::startTelephonySync)
    }

    fun sendSafeCustomCommand(action: String) {
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

    fun sendRemoteInput(
        command: RemoteInputCommand,
        deltaX: Float = 0f,
        deltaY: Float = 0f,
        keyboardText: String = ""
    ) {
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
                VeyroMessage.PayloadCase.BATTERY_STATUS ->
                    updateRemoteBatteryStatus(endpointId, featureMessage.batteryStatus)

                VeyroMessage.PayloadCase.FIND_DEVICE_REQUEST ->
                    handleFindDeviceRequest(endpointId, featureMessage.findDeviceRequest)

                VeyroMessage.PayloadCase.NOTIFICATION_SYNC_EVENT ->
                    handleNotificationSyncEvent(featureMessage.notificationSyncEvent)

                VeyroMessage.PayloadCase.MEDIA_CONTROL_EVENT ->
                    handleMediaControlEvent(featureMessage.mediaControlEvent)

                VeyroMessage.PayloadCase.TELECOMMUNICATION_EVENT ->
                    handleTelecommunicationEvent(featureMessage.telecommunicationEvent)

                VeyroMessage.PayloadCase.CUSTOM_COMMAND_EVENT ->
                    handleCustomCommandEvent(featureMessage.customCommandEvent)

                VeyroMessage.PayloadCase.URL_SHARE_EVENT ->
                    handleUrlShareEvent(featureMessage.urlShareEvent)

                VeyroMessage.PayloadCase.REMOTE_INPUT_EVENT ->
                    handleRemoteInputEvent(featureMessage.remoteInputEvent)

                VeyroMessage.PayloadCase.PAYLOAD_NOT_SET,
                null -> Unit
            }
            return
        }

        val fileMetadata = FileMetadata.fromWireBytes(bytes)
        if (fileMetadata != null) {
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
                            direction = RawFileDirection.RECEIVE,
                            temporaryUri = temporaryUri.toString(),
                            fileName = metadata?.fileName ?: existingTransfer.fileName,
                            totalBytes = metadata?.totalBytes ?: existingTransfer.totalBytes,
                            mimeType = metadata?.mimeType ?: existingTransfer.mimeType
                        ) ?: RawFileTransfer(
                                payloadId = payloadId,
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
        stopNotificationSync()
        stopMediaSync()
        stopTelephonySync()
        findMyDeviceAlarm.stop()
        runCatching { clientResult.getOrNull()?.close() }
    }

    private fun scheduleDeterministicConnection(
        endpointId: String,
        remoteIdentity: EndpointIdentity
    ) {
        val state = _uiState.value
        if (!state.ecosystemEnabled || state.connectedEndpointId != null ||
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
            if (current.ecosystemEnabled && current.connectedEndpointId == null &&
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
                _uiState.value.connectedEndpointId == null &&
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
        if (!state.ecosystemEnabled || state.connectedEndpointId != null) {
            if (!state.ecosystemEnabled) stopRadioOperations(client)
            return
        }
        if (state.energyMode == EnergyMode.BATTERY_SAVER && !screenInteractive) {
            radioDutyCycleJob = controllerScope.launch {
                while (isActive && _uiState.value.ecosystemEnabled &&
                    _uiState.value.connectedEndpointId == null
                ) {
                    startRadioPair()
                    delay(BATTERY_SAVER_ACTIVE_WINDOW_MILLIS)
                    stopRadioOperations(client)
                    _uiState.update {
                        if (it.connectedEndpointId == null && it.ecosystemEnabled) {
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
        if (!state.ecosystemEnabled || state.connectedEndpointId != null) return
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

    private fun startBatterySync(endpointId: String) {
        stopBatterySync()
        val client = clientResult.getOrNull() ?: return

        batterySyncJob = controllerScope.launch {
            batteryStatusMonitor.statusUpdates().collect { batteryStatus ->
                if (_uiState.value.connectedEndpointId != endpointId) return@collect

                runCatching {
                    client.sendBytes(
                        endpointId,
                        VeyroProtocolCodec.encodeBatteryStatus(batteryStatus)
                    ).addOnFailureListener { error ->
                        if (_uiState.value.connectedEndpointId == endpointId) {
                            _uiState.update {
                                it.copy(
                                    errorMessage = error.localizedMessage
                                        ?: "Não foi possível sincronizar a bateria."
                                )
                            }
                        }
                    }
                }.onFailure { error ->
                    if (_uiState.value.connectedEndpointId == endpointId) {
                        _uiState.update {
                            it.copy(
                                errorMessage = error.localizedMessage
                                    ?: "Não foi possível sincronizar a bateria."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopBatterySync() {
        batterySyncJob?.cancel()
        batterySyncJob = null
    }

    private fun startNotificationSync(endpointId: String) {
        stopNotificationSync()
        val client = clientResult.getOrNull() ?: return

        notificationSyncJob = controllerScope.launch {
            NotificationSyncBridge.activeNotifications().forEach { event ->
                if (_uiState.value.connectedEndpointId == endpointId) {
                    client.sendBytes(
                        endpointId,
                        VeyroProtocolCodec.encodeNotificationSyncEvent(event)
                    )
                }
            }
            NotificationSyncBridge.events.collect { event ->
                if (_uiState.value.connectedEndpointId == endpointId) {
                    client.sendBytes(
                        endpointId,
                        VeyroProtocolCodec.encodeNotificationSyncEvent(event)
                    )
                }
            }
        }
    }

    private fun stopNotificationSync() {
        notificationSyncJob?.cancel()
        notificationSyncJob = null
    }

    private fun startTelephonySync(endpointId: String) {
        stopTelephonySync()
        val client = clientResult.getOrNull() ?: return
        telephonyCallStateMonitor.start()
        telephonySyncJob = controllerScope.launch {
            TelephonySyncBridge.events.collect { event ->
                if (_uiState.value.connectedEndpointId == endpointId) {
                    runCatching {
                        client.sendBytes(
                            endpointId,
                            VeyroProtocolCodec.encodeTelecommunicationEvent(event)
                        ).addOnFailureListener(::showFeatureError)
                    }.onFailure(::showFeatureError)
                }
            }
        }
    }

    private fun stopTelephonySync() {
        telephonySyncJob?.cancel()
        telephonySyncJob = null
        telephonyCallStateMonitor.stop()
    }

    private fun startMediaSync(endpointId: String) {
        stopMediaSync()
        val client = clientResult.getOrNull() ?: return

        mediaSessionCoordinator.start { event ->
            if (_uiState.value.connectedEndpointId != endpointId) return@start
            runCatching {
                client.sendBytes(
                    endpointId,
                    VeyroProtocolCodec.encodeMediaControlEvent(event)
                ).addOnFailureListener(::showFeatureError)
            }.onFailure(::showFeatureError)
        }.onFailure { error ->
            if (_uiState.value.connectedEndpointId == endpointId) {
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
        if (state.connectedEndpointId != endpointId) return null
        return ecosystemPreferences.rulesFor(state.connectedEndpointName)
    }

    private fun handleNotificationSyncEvent(event: NotificationSyncEvent) {
        when (event.syncAction) {
            NotificationSyncAction.POST_NEW -> {
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

    private fun handleMediaControlEvent(event: MediaControlEvent) {
        if (event.eventCategory == MediaEventCategory.STATE_REPORT) {
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

    private fun handleTelecommunicationEvent(event: TelecommunicationEvent) {
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

    private fun handleCustomCommandEvent(event: CustomCommandEvent) {
        if (event.executionTypeCategory == ExecutionTypeCategory.EXECUTION_RESULT) {
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
        val endpointId = _uiState.value.connectedEndpointId ?: return
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

    private fun handleUrlShareEvent(event: UrlShareEvent) {
        if (event.resultMessage.isNotBlank()) {
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
        val endpointId = _uiState.value.connectedEndpointId ?: return
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
        if (!completedFilePayloadIds.containsKey(payloadId)) return
        val transfer = _uiState.value.rawFileTransfers.firstOrNull {
            it.payloadId == payloadId && it.direction == RawFileDirection.RECEIVE
        } ?: return
        val temporaryUri = transfer.temporaryUri?.let(Uri::parse) ?: return
        val metadata = fileMetadataByPayloadId[payloadId] ?: return
        val autoAcceptFiles = ecosystemPreferences
            .rulesFor(_uiState.value.connectedEndpointName)
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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? P2PTransferService.LocalBinder)?.service
            if (service == null) {
                showServiceError("Não foi possível acessar o serviço de transferência.")
                return
            }

            transferService = service
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

    fun sendRemoteInput(
        command: RemoteInputCommand,
        deltaX: Float = 0f,
        deltaY: Float = 0f,
        keyboardText: String = ""
    ) {
        withService { it.sendRemoteInput(command, deltaX, deltaY, keyboardText) }
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
