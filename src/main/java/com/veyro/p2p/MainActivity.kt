package com.veyro.p2p

import android.Manifest
import android.app.role.RoleManager
import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.session.PlaybackState
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CellWifi
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.veyro.p2p.nearby.ConnectionStage
import com.veyro.p2p.nearby.DiscoveredEndpoint
import com.veyro.p2p.nearby.NearbyClientStatus
import com.veyro.p2p.nearby.NearbyClientUiState
import com.veyro.p2p.nearby.NearbyViewModel
import com.veyro.p2p.nearby.PendingConnection
import com.veyro.p2p.nearby.ReceivedCommand
import com.veyro.p2p.nearby.RawFileDirection
import com.veyro.p2p.nearby.RawFileStatus
import com.veyro.p2p.nearby.RawFileTransfer
import com.veyro.p2p.nearby.RemoteBatteryStatus
import com.veyro.p2p.nearby.RemoteConnectivityStatus
import com.veyro.p2p.nearby.RemotePingStatus
import com.veyro.p2p.nearby.RemoteNotification
import com.veyro.p2p.nearby.RemoteMediaState
import com.veyro.p2p.nearby.RemoteAudioStreamVolume
import com.veyro.p2p.nearby.RemoteAudioOutputRoute
import com.veyro.p2p.nearby.RemoteTelecommunicationEvent
import com.veyro.p2p.nearby.RemoteCustomCommandResult
import com.veyro.p2p.nearby.RemoteSharedUrl
import com.veyro.p2p.nearby.PendingContactImport
import com.veyro.p2p.nearby.RemoteFileItem
import com.veyro.p2p.nearby.RemotePresentationState
import com.veyro.p2p.features.commands.SafeCustomCommandExecutor
import com.veyro.p2p.features.clipboard.ClipboardQuickSettingsTileService
import com.veyro.p2p.features.remoteinput.VeyroAccessibilityService
import com.veyro.p2p.permissions.OptionalFeatureAccess
import com.veyro.p2p.permissions.PermissionManager
import com.veyro.p2p.protocol.MediaEventCategory
import com.veyro.p2p.protocol.AudioStreamKind
import com.veyro.p2p.protocol.TelecommunicationType
import com.veyro.p2p.protocol.RemoteInputCommand
import com.veyro.p2p.protocol.PresentationAction
import com.veyro.p2p.protocol.StylusAction
import com.veyro.p2p.settings.EnergyMode
import com.veyro.p2p.settings.AppLanguage
import com.veyro.p2p.settings.FeatureSettings
import com.veyro.p2p.settings.TrustedDeviceRules
import com.veyro.p2p.ui.i18n.VeyroI18n
import com.veyro.p2p.ui.theme.VeyroTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

private data class ExtendedFeatureActions(
    val onSelectConnectedEndpoint: (String) -> Unit,
    val onPickContact: () -> Unit,
    val onApproveContact: (String) -> Unit,
    val onRejectContact: (String) -> Unit,
    val onPresentationAction: (PresentationAction, Long) -> Unit,
    val onDismissRemoteBlackout: () -> Unit,
    val onStylusEvent: (StylusAction, Float, Float, Float, Float, Float, Boolean, Boolean) -> Unit,
    val onChooseSharedFolder: () -> Unit,
    val onClearSharedFolder: () -> Unit,
    val onRequestRemoteFileList: (String) -> Unit,
    val onRequestRemoteFileDownload: (String) -> Unit,
    val onSyncClipboard: () -> Unit,
    val onRequestClipboardTile: () -> Unit
)

private data class PendingFeatureActivation(
    val settings: FeatureSettings,
    val access: OptionalFeatureAccess
)

private data class FeatureHubItem(
    val key: String,
    val title: String,
    val icon: ImageVector
)

private data class MediaControlRequest(
    val category: MediaEventCategory,
    val requestedVolume: Int = -1,
    val targetStream: AudioStreamKind = AudioStreamKind.AUDIO_STREAM_KIND_UNKNOWN,
    val requestedPositionMs: Long = -1L
)

private fun featureHubTitle(key: String?): String = when (key) {
    "files" -> "Enviar arquivos"
    "clipboard" -> "Área de transferência"
    "presentation" -> "Controle de apresentação"
    "media" -> "Controle multimídia"
    "remote_input" -> "Mouse e teclado"
    "commands" -> "Executar comando"
    "battery" -> "Estado da bateria"
    "connectivity" -> "Conectividade e ping"
    "find" -> "Encontrar aparelho"
    "notifications" -> "Notificações"
    "telephony" -> "Chamadas e SMS"
    "links" -> "Compartilhar link"
    "contacts" -> "Sincronizar contatos"
    "tablet" -> "Mesa digitalizadora"
    "remote_files" -> "Arquivos remotos"
    else -> "Recurso"
}

class MainActivity : ComponentActivity() {
    private val nearbyViewModel: NearbyViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager
    private var permissionsGranted by mutableStateOf(false)
    private var notificationListenerGranted by mutableStateOf(false)
    private var notificationPolicyGranted by mutableStateOf(false)
    private var telephonyPermissionsGranted by mutableStateOf(false)
    private var callScreeningRoleGranted by mutableStateOf(false)
    private var cameraPermissionGranted by mutableStateOf(false)
    private var remoteInputAccessibilityGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionManager = PermissionManager(this)
        refreshPermissionState()
        refreshFeatureAccessState()

        setContent {
            val nearbyUiState by nearbyViewModel.uiState.collectAsState()
            var pendingFeatureActivation by remember {
                mutableStateOf<PendingFeatureActivation?>(null)
            }
            var showFeatureAccessDialog by remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                refreshPermissionState()
            }
            val telephonyPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                refreshFeatureAccessState()
                nearbyViewModel.refreshTelephonySync()
            }
            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                refreshFeatureAccessState()
            }
            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let(nearbyViewModel::sendFile)
            }
            var pendingContactRequestId by rememberSaveable { mutableStateOf<String?>(null) }
            val writeContactsPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                pendingContactRequestId?.let { requestId ->
                    if (granted) nearbyViewModel.approveContactImport(requestId)
                }
                pendingContactRequestId = null
            }
            val contactPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickContact()
            ) { uri -> uri?.let(nearbyViewModel::shareContact) }
            val sharedFolderLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri -> uri?.let(nearbyViewModel::setSharedFolder) }

            LaunchedEffect(
                pendingFeatureActivation,
                notificationListenerGranted,
                notificationPolicyGranted,
                telephonyPermissionsGranted,
                cameraPermissionGranted,
                remoteInputAccessibilityGranted
            ) {
                val pending = pendingFeatureActivation ?: return@LaunchedEffect
                if (isOptionalFeatureAccessGranted(pending.access)) {
                    nearbyViewModel.setFeatureSettings(pending.settings)
                    pendingFeatureActivation = null
                    showFeatureAccessDialog = false
                }
            }

            LaunchedEffect(
                nearbyUiState.featureSettings,
                notificationListenerGranted,
                notificationPolicyGranted,
                telephonyPermissionsGranted,
                cameraPermissionGranted,
                remoteInputAccessibilityGranted
            ) {
                val availableSettings = PermissionManager.disableUnavailablePrivilegedFeatures(
                    settings = nearbyUiState.featureSettings,
                    notificationListenerGranted = notificationListenerGranted,
                    notificationPolicyGranted = notificationPolicyGranted,
                    telephonyPermissionsGranted = telephonyPermissionsGranted,
                    cameraPermissionGranted = cameraPermissionGranted,
                    accessibilityGranted = remoteInputAccessibilityGranted
                )
                if (availableSettings != nearbyUiState.featureSettings) {
                    nearbyViewModel.setFeatureSettings(availableSettings)
                }
            }
            val extendedFeatureActions = ExtendedFeatureActions(
                onSelectConnectedEndpoint = nearbyViewModel::selectConnectedEndpoint,
                onPickContact = { contactPickerLauncher.launch(null) },
                onApproveContact = { requestId ->
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        nearbyViewModel.approveContactImport(requestId)
                    } else {
                        pendingContactRequestId = requestId
                        writeContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                    }
                },
                onRejectContact = nearbyViewModel::rejectContactImport,
                onPresentationAction = nearbyViewModel::sendPresentationAction,
                onDismissRemoteBlackout = nearbyViewModel::dismissRemoteBlackout,
                onStylusEvent = nearbyViewModel::sendStylusEvent,
                onChooseSharedFolder = { sharedFolderLauncher.launch(null) },
                onClearSharedFolder = nearbyViewModel::clearSharedFolder,
                onRequestRemoteFileList = nearbyViewModel::requestRemoteFileList,
                onRequestRemoteFileDownload = nearbyViewModel::requestRemoteFileDownload,
                onSyncClipboard = nearbyViewModel::syncClipboard,
                onRequestClipboardTile = ::requestClipboardTile
            )

            VeyroApp(
                permissionsGranted = permissionsGranted,
                nearbyUiState = nearbyUiState,
                onRequestPermissions = {
                    permissionLauncher.launch(
                        permissionManager.requiredRuntimePermissions().toTypedArray()
                    )
                },
                onStartAdvertising = nearbyViewModel::startAdvertising,
                onStartDiscovery = nearbyViewModel::startDiscovery,
                onRequestConnection = nearbyViewModel::requestConnection,
                onAcceptConnection = nearbyViewModel::acceptPendingConnection,
                onRejectConnection = nearbyViewModel::rejectPendingConnection,
                onSendCommand = nearbyViewModel::sendCommand,
                onStartRemoteFindAlarm = nearbyViewModel::startRemoteFindAlarm,
                onStopRemoteFindAlarm = nearbyViewModel::stopRemoteFindAlarm,
                notificationListenerGranted = notificationListenerGranted,
                notificationPolicyGranted = notificationPolicyGranted,
                telephonyPermissionsGranted = telephonyPermissionsGranted,
                callScreeningRoleGranted = callScreeningRoleGranted,
                onRequestTelephonyPermissions = {
                    telephonyPermissionLauncher.launch(
                        permissionManager.optionalTelephonyPermissions().toTypedArray()
                    )
                },
                onRequestCallScreeningRole = ::requestCallScreeningRole,
                cameraPermissionGranted = cameraPermissionGranted,
                remoteInputAccessibilityGranted = remoteInputAccessibilityGranted,
                onRequestCameraPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenAccessibilitySettings = {
                    openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                },
                onOpenNotificationListenerSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    }
                },
                onOpenNotificationPolicySettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        openSystemSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    }
                },
                onDismissRemoteNotification = nearbyViewModel::dismissRemoteNotification,
                onMediaControlCommand = { request ->
                    nearbyViewModel.sendMediaControlCommand(
                        category = request.category,
                        requestedVolume = request.requestedVolume,
                        targetStream = request.targetStream,
                        requestedPositionMs = request.requestedPositionMs
                    )
                },
                onSendSmsTransmitOrder = nearbyViewModel::sendSmsTransmitOrder,
                onSendSafeCustomCommand = nearbyViewModel::sendSafeCustomCommand,
                onShareUrl = nearbyViewModel::shareUrl,
                onRemoteInput = nearbyViewModel::sendRemoteInput,
                onPickFile = { filePickerLauncher.launch("*/*") },
                onApproveIncomingFile = nearbyViewModel::approveIncomingFile,
                onRejectIncomingFile = nearbyViewModel::rejectIncomingFile,
                onUpdateTrustedDeviceRules = nearbyViewModel::updateTrustedDeviceRules,
                onRemoveTrustedDevice = nearbyViewModel::removeTrustedDevice,
                onSetEnergyMode = nearbyViewModel::setEnergyMode,
                onSetAppLanguage = nearbyViewModel::setAppLanguage,
                onSetFeatureSettings = { requestedSettings ->
                    val requiredAccess = PermissionManager.optionalAccessForActivation(
                        current = nearbyUiState.featureSettings,
                        requested = requestedSettings
                    )
                    if (requiredAccess == null || isOptionalFeatureAccessGranted(requiredAccess)) {
                        nearbyViewModel.setFeatureSettings(requestedSettings)
                    } else {
                        pendingFeatureActivation = PendingFeatureActivation(
                            settings = requestedSettings,
                            access = requiredAccess
                        )
                        showFeatureAccessDialog = true
                    }
                },
                extendedFeatureActions = extendedFeatureActions,
                onStopSession = nearbyViewModel::stopSession
            )

            pendingFeatureActivation
                ?.takeIf { showFeatureAccessDialog }
                ?.let { pending ->
                    OptionalFeatureAccessDialog(
                        access = pending.access,
                        onContinue = {
                            showFeatureAccessDialog = false
                            when (pending.access) {
                                OptionalFeatureAccess.NOTIFICATION_LISTENER -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                        openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    }
                                }
                                OptionalFeatureAccess.NOTIFICATION_POLICY -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        openSystemSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                    }
                                }
                                OptionalFeatureAccess.TELEPHONY -> {
                                    telephonyPermissionLauncher.launch(
                                        permissionManager.optionalTelephonyPermissions().toTypedArray()
                                    )
                                }
                                OptionalFeatureAccess.CAMERA -> {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                                OptionalFeatureAccess.ACCESSIBILITY -> {
                                    openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                }
                            }
                        },
                        onDismiss = {
                            pendingFeatureActivation = null
                            showFeatureAccessDialog = false
                        }
                    )
                }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::permissionManager.isInitialized) {
            refreshPermissionState()
            refreshFeatureAccessState()
        }
        nearbyViewModel.syncClipboardFromForeground()
    }

    private fun requestClipboardTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                this,
                "Edite os Controles rápidos e adicione Sincronizar clipboard.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val statusBarManager = getSystemService(StatusBarManager::class.java)
        statusBarManager.requestAddTileService(
            ComponentName(this, ClipboardQuickSettingsTileService::class.java),
            getString(R.string.clipboard_tile_label),
            Icon.createWithResource(this, R.drawable.ic_veyro_clipboard_tile),
            mainExecutor
        ) { result ->
            val message = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                "Controle rápido do clipboard adicionado."
            } else {
                "O controle rápido não foi adicionado ou já existe."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshPermissionState() {
        permissionsGranted = permissionManager.hasRequiredPermissions()
    }

    private fun refreshFeatureAccessState() {
        notificationListenerGranted = NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)
        notificationPolicyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.isNotificationPolicyAccessGranted
        } else {
            true
        }
        telephonyPermissionsGranted = permissionManager.hasOptionalTelephonyPermissions()
        callScreeningRoleGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            false
        }
        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val accessibilityComponent = ComponentName(this, VeyroAccessibilityService::class.java)
        remoteInputAccessibilityGranted = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString)
            .any { it == accessibilityComponent }
    }

    private fun isOptionalFeatureAccessGranted(access: OptionalFeatureAccess): Boolean = when (access) {
        OptionalFeatureAccess.NOTIFICATION_LISTENER -> notificationListenerGranted
        OptionalFeatureAccess.NOTIFICATION_POLICY -> notificationPolicyGranted
        OptionalFeatureAccess.TELEPHONY -> telephonyPermissionsGranted
        OptionalFeatureAccess.CAMERA -> cameraPermissionGranted
        OptionalFeatureAccess.ACCESSIBILITY -> remoteInputAccessibilityGranted
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun openSystemSettings(action: String) {
        runCatching { startActivity(Intent(action)) }
    }
}

@Composable
private fun VeyroApp(
    permissionsGranted: Boolean,
    nearbyUiState: NearbyClientUiState,
    onRequestPermissions: () -> Unit,
    onStartAdvertising: () -> Unit,
    onStartDiscovery: () -> Unit,
    onRequestConnection: (String) -> Unit,
    onAcceptConnection: () -> Unit,
    onRejectConnection: () -> Unit,
    onSendCommand: (String) -> Unit,
    onStartRemoteFindAlarm: () -> Unit,
    onStopRemoteFindAlarm: () -> Unit,
    notificationListenerGranted: Boolean,
    notificationPolicyGranted: Boolean,
    telephonyPermissionsGranted: Boolean,
    callScreeningRoleGranted: Boolean,
    onRequestTelephonyPermissions: () -> Unit,
    onRequestCallScreeningRole: () -> Unit,
    cameraPermissionGranted: Boolean,
    remoteInputAccessibilityGranted: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onDismissRemoteNotification: (String) -> Unit,
    onMediaControlCommand: (MediaControlRequest) -> Unit,
    onSendSmsTransmitOrder: (String, String) -> Unit,
    onSendSafeCustomCommand: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onRemoteInput: (RemoteInputCommand, Float, Float, String) -> Unit,
    onPickFile: () -> Unit,
    onApproveIncomingFile: (Long) -> Unit,
    onRejectIncomingFile: (Long) -> Unit,
    onUpdateTrustedDeviceRules: (TrustedDeviceRules) -> Unit,
    onRemoveTrustedDevice: (String) -> Unit,
    onSetEnergyMode: (EnergyMode) -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
    onSetFeatureSettings: (FeatureSettings) -> Unit,
    extendedFeatureActions: ExtendedFeatureActions,
    onStopSession: () -> Unit
) {
    var showPermissionExplanation by remember { mutableStateOf(!permissionsGranted) }

    CompositionLocalProvider(LocalVeyroLanguage provides nearbyUiState.appLanguage) {
        VeyroTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
            VeyroScreen(
                permissionsGranted = permissionsGranted,
                nearbyUiState = nearbyUiState,
                onRequestPermissions = onRequestPermissions,
                onStartAdvertising = onStartAdvertising,
                onStartDiscovery = onStartDiscovery,
                onRequestConnection = onRequestConnection,
                onSendCommand = onSendCommand,
                onStartRemoteFindAlarm = onStartRemoteFindAlarm,
                onStopRemoteFindAlarm = onStopRemoteFindAlarm,
                notificationListenerGranted = notificationListenerGranted,
                notificationPolicyGranted = notificationPolicyGranted,
                telephonyPermissionsGranted = telephonyPermissionsGranted,
                callScreeningRoleGranted = callScreeningRoleGranted,
                onRequestTelephonyPermissions = onRequestTelephonyPermissions,
                onRequestCallScreeningRole = onRequestCallScreeningRole,
                cameraPermissionGranted = cameraPermissionGranted,
                remoteInputAccessibilityGranted = remoteInputAccessibilityGranted,
                onRequestCameraPermission = onRequestCameraPermission,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                onDismissRemoteNotification = onDismissRemoteNotification,
                onMediaControlCommand = onMediaControlCommand,
                onSendSmsTransmitOrder = onSendSmsTransmitOrder,
                onSendSafeCustomCommand = onSendSafeCustomCommand,
                onShareUrl = onShareUrl,
                onRemoteInput = onRemoteInput,
                onPickFile = onPickFile,
                onApproveIncomingFile = onApproveIncomingFile,
                onRejectIncomingFile = onRejectIncomingFile,
                onUpdateTrustedDeviceRules = onUpdateTrustedDeviceRules,
                onRemoveTrustedDevice = onRemoveTrustedDevice,
                onSetEnergyMode = onSetEnergyMode,
                onSetAppLanguage = onSetAppLanguage,
                onSetFeatureSettings = onSetFeatureSettings,
                extendedFeatureActions = extendedFeatureActions,
                onStopSession = onStopSession
            )

            if (showPermissionExplanation && !permissionsGranted) {
                PermissionExplanationDialog(
                    onContinue = {
                        showPermissionExplanation = false
                        onRequestPermissions()
                    },
                    onDismiss = { showPermissionExplanation = false }
                )
            }

            nearbyUiState.pendingConnection
                ?.takeIf { nearbyUiState.connectionStage == ConnectionStage.AUTHENTICATING }
                ?.let { pending ->
                    AuthenticationDialog(
                        pendingConnection = pending,
                        onAccept = onAcceptConnection,
                        onReject = onRejectConnection
                    )
                }
            if (nearbyUiState.remotePresentationState.blackedOut) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(onClick = extendedFeatureActions.onDismissRemoteBlackout),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        "Tela preta ativa • toque para sair",
                        modifier = Modifier.padding(bottom = 40.dp),
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun VeyroScreen(
    permissionsGranted: Boolean,
    nearbyUiState: NearbyClientUiState,
    onRequestPermissions: () -> Unit,
    onStartAdvertising: () -> Unit,
    onStartDiscovery: () -> Unit,
    onRequestConnection: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onStartRemoteFindAlarm: () -> Unit,
    onStopRemoteFindAlarm: () -> Unit,
    notificationListenerGranted: Boolean,
    notificationPolicyGranted: Boolean,
    telephonyPermissionsGranted: Boolean,
    callScreeningRoleGranted: Boolean,
    onRequestTelephonyPermissions: () -> Unit,
    onRequestCallScreeningRole: () -> Unit,
    cameraPermissionGranted: Boolean,
    remoteInputAccessibilityGranted: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onDismissRemoteNotification: (String) -> Unit,
    onMediaControlCommand: (MediaControlRequest) -> Unit,
    onSendSmsTransmitOrder: (String, String) -> Unit,
    onSendSafeCustomCommand: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onRemoteInput: (RemoteInputCommand, Float, Float, String) -> Unit,
    onPickFile: () -> Unit,
    onApproveIncomingFile: (Long) -> Unit,
    onRejectIncomingFile: (Long) -> Unit,
    onUpdateTrustedDeviceRules: (TrustedDeviceRules) -> Unit,
    onRemoveTrustedDevice: (String) -> Unit,
    onSetEnergyMode: (EnergyMode) -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
    onSetFeatureSettings: (FeatureSettings) -> Unit,
    extendedFeatureActions: ExtendedFeatureActions,
    onStopSession: () -> Unit
) {
    if (!permissionsGranted) {
        PermissionWelcome(
            onRequestPermissions = onRequestPermissions
        )
        return
    }

    var selectedDestinationOrdinal by rememberSaveable {
        mutableStateOf(VeyroDestination.ECOSYSTEM.ordinal)
    }
    var selectedResourceKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDestination = VeyroDestination.entries[selectedDestinationOrdinal]

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp
        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                VeyroNavigationRail(
                    selected = selectedDestination,
                    onSelected = { selectedDestinationOrdinal = it.ordinal }
                )
                VeyroDestinationContent(
                    modifier = Modifier.weight(1f),
                    destination = selectedDestination,
                    nearbyUiState = nearbyUiState,
                    onStartAdvertising = onStartAdvertising,
                    onStartDiscovery = onStartDiscovery,
                    onRequestConnection = onRequestConnection,
                    onSendCommand = onSendCommand,
                    onStartRemoteFindAlarm = onStartRemoteFindAlarm,
                    onStopRemoteFindAlarm = onStopRemoteFindAlarm,
                    notificationListenerGranted = notificationListenerGranted,
                    notificationPolicyGranted = notificationPolicyGranted,
                    telephonyPermissionsGranted = telephonyPermissionsGranted,
                    callScreeningRoleGranted = callScreeningRoleGranted,
                    cameraPermissionGranted = cameraPermissionGranted,
                    remoteInputAccessibilityGranted = remoteInputAccessibilityGranted,
                    onRequestTelephonyPermissions = onRequestTelephonyPermissions,
                    onRequestCallScreeningRole = onRequestCallScreeningRole,
                    onRequestCameraPermission = onRequestCameraPermission,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                    onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                    onDismissRemoteNotification = onDismissRemoteNotification,
                    onMediaControlCommand = onMediaControlCommand,
                    onSendSmsTransmitOrder = onSendSmsTransmitOrder,
                    onSendSafeCustomCommand = onSendSafeCustomCommand,
                    onShareUrl = onShareUrl,
                    onRemoteInput = onRemoteInput,
                    onPickFile = onPickFile,
                    onApproveIncomingFile = onApproveIncomingFile,
                    onRejectIncomingFile = onRejectIncomingFile,
                    onUpdateTrustedDeviceRules = onUpdateTrustedDeviceRules,
                    onRemoveTrustedDevice = onRemoveTrustedDevice,
                    onSetEnergyMode = onSetEnergyMode,
                    onSetAppLanguage = onSetAppLanguage,
                    onSetFeatureSettings = onSetFeatureSettings,
                    extendedFeatureActions = extendedFeatureActions,
                    onStopSession = onStopSession,
                    selectedFeatureKey = selectedResourceKey,
                    onSelectedFeatureChange = { selectedResourceKey = it }
                )
            }
        } else {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val drawerScope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    VeyroDrawer(
                        selected = selectedDestination,
                        uiState = nearbyUiState,
                        onSelected = { destination ->
                            selectedDestinationOrdinal = destination.ordinal
                            drawerScope.launch { drawerState.close() }
                        },
                        onStartPairing = {
                            selectedDestinationOrdinal = VeyroDestination.ECOSYSTEM.ordinal
                            onStartDiscovery()
                            drawerScope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    bottomBar = {
                        if (selectedResourceKey == null) {
                            VeyroNavigationBar(
                                selected = selectedDestination,
                                onSelected = { selectedDestinationOrdinal = it.ordinal }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        VeyroDestinationContent(
                            modifier = Modifier
                            .fillMaxSize()
                            .padding(top = if (selectedResourceKey == null) 56.dp else 0.dp),
                            destination = selectedDestination,
                            nearbyUiState = nearbyUiState,
                            onStartAdvertising = onStartAdvertising,
                            onStartDiscovery = onStartDiscovery,
                            onRequestConnection = onRequestConnection,
                            onSendCommand = onSendCommand,
                            onStartRemoteFindAlarm = onStartRemoteFindAlarm,
                            onStopRemoteFindAlarm = onStopRemoteFindAlarm,
                            notificationListenerGranted = notificationListenerGranted,
                            notificationPolicyGranted = notificationPolicyGranted,
                            telephonyPermissionsGranted = telephonyPermissionsGranted,
                            callScreeningRoleGranted = callScreeningRoleGranted,
                            cameraPermissionGranted = cameraPermissionGranted,
                            remoteInputAccessibilityGranted = remoteInputAccessibilityGranted,
                            onRequestTelephonyPermissions = onRequestTelephonyPermissions,
                            onRequestCallScreeningRole = onRequestCallScreeningRole,
                            onRequestCameraPermission = onRequestCameraPermission,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                            onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                            onDismissRemoteNotification = onDismissRemoteNotification,
                            onMediaControlCommand = onMediaControlCommand,
                            onSendSmsTransmitOrder = onSendSmsTransmitOrder,
                            onSendSafeCustomCommand = onSendSafeCustomCommand,
                            onShareUrl = onShareUrl,
                            onRemoteInput = onRemoteInput,
                            onPickFile = onPickFile,
                            onApproveIncomingFile = onApproveIncomingFile,
                            onRejectIncomingFile = onRejectIncomingFile,
                            onUpdateTrustedDeviceRules = onUpdateTrustedDeviceRules,
                            onRemoveTrustedDevice = onRemoveTrustedDevice,
                            onSetEnergyMode = onSetEnergyMode,
                            onSetAppLanguage = onSetAppLanguage,
                            onSetFeatureSettings = onSetFeatureSettings,
                            extendedFeatureActions = extendedFeatureActions,
                            onStopSession = onStopSession,
                            selectedFeatureKey = selectedResourceKey,
                            onSelectedFeatureChange = { selectedResourceKey = it }
                        )
                        if (selectedResourceKey == null) {
                            VeyroCompactTopBar(
                                modifier = Modifier.align(Alignment.TopStart),
                                onOpenDrawer = { drawerScope.launch { drawerState.open() } }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class VeyroDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    ECOSYSTEM("Ecossistema", Icons.Default.Hub),
    RESOURCES("Recursos", Icons.Default.Devices),
    SETTINGS("Configurações", Icons.Default.Settings),
    ABOUT("Sobre", Icons.Default.Info)
}

private data class EcosystemActivity(
    val title: String,
    val detail: String
)

@Composable
private fun PermissionWelcome(onRequestPermissions: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.veyro_logo_color),
                contentDescription = "Veyro",
                modifier = Modifier.size(112.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Seu ecossistema Veyro",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Conecte seus aparelhos diretamente, sem internet, com você no controle de cada acesso.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                onClick = onRequestPermissions
            ) {
                Text("Configurar meu ecossistema")
            }
        }
    }
}

@Composable
private fun VeyroCompactTopBar(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit
) {
    Box(
        modifier = modifier
            .size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Default.Menu, contentDescription = "Abrir menu")
        }
    }
}

@Composable
private fun VeyroDrawer(
    selected: VeyroDestination,
    uiState: NearbyClientUiState,
    onSelected: (VeyroDestination) -> Unit,
    onStartPairing: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.widthIn(max = 340.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(top = 28.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Image(
                        painter = painterResource(R.drawable.veyro_logo_color),
                        contentDescription = "Veyro",
                        modifier = Modifier.padding(9.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Veyro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        Build.MODEL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when (uiState.connectionStage) {
                            ConnectionStage.CONNECTED -> "Conectado agora"
                            ConnectionStage.ACTIVE,
                            ConnectionStage.ADVERTISING,
                            ConnectionStage.DISCOVERING -> "Ecossistema ativo"
                            else -> "Pronto para conectar"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                "NAVEGAÇÃO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            VeyroDestination.entries
                .filterNot { it == VeyroDestination.RESOURCES }
                .forEach { destination ->
                NavigationDrawerItem(
                    selected = destination == selected,
                    onClick = { onSelected(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            FilledTonalButton(
                onClick = onStartPairing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Hub, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text("Conectar novo aparelho")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Veyro ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp, 20.dp)
            )
        }
    }
}

@Composable
private fun VeyroNavigationBar(
    selected: VeyroDestination,
    onSelected: (VeyroDestination) -> Unit
) {
    NavigationBar {
        listOf(VeyroDestination.ECOSYSTEM, VeyroDestination.RESOURCES).forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(destination.icon, contentDescription = destination.label)
                },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable
private fun VeyroNavigationRail(
    selected: VeyroDestination,
    onSelected: (VeyroDestination) -> Unit
) {
    NavigationRail(
        header = {
            Surface(
                modifier = Modifier.padding(vertical = 18.dp).size(52.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(
                            if (isSystemInDarkTheme()) {
                                R.drawable.veyro_logo_white
                            } else {
                                R.drawable.veyro_logo_negative
                            }
                        ),
                        contentDescription = "Veyro",
                        modifier = Modifier.padding(9.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    ) {
        VeyroDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(destination.icon, contentDescription = destination.label)
                },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable
private fun VeyroDestinationContent(
    modifier: Modifier,
    destination: VeyroDestination,
    nearbyUiState: NearbyClientUiState,
    onStartAdvertising: () -> Unit,
    onStartDiscovery: () -> Unit,
    onRequestConnection: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onStartRemoteFindAlarm: () -> Unit,
    onStopRemoteFindAlarm: () -> Unit,
    notificationListenerGranted: Boolean,
    notificationPolicyGranted: Boolean,
    telephonyPermissionsGranted: Boolean,
    callScreeningRoleGranted: Boolean,
    cameraPermissionGranted: Boolean,
    remoteInputAccessibilityGranted: Boolean,
    onRequestTelephonyPermissions: () -> Unit,
    onRequestCallScreeningRole: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onDismissRemoteNotification: (String) -> Unit,
    onMediaControlCommand: (MediaControlRequest) -> Unit,
    onSendSmsTransmitOrder: (String, String) -> Unit,
    onSendSafeCustomCommand: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onRemoteInput: (RemoteInputCommand, Float, Float, String) -> Unit,
    onPickFile: () -> Unit,
    onApproveIncomingFile: (Long) -> Unit,
    onRejectIncomingFile: (Long) -> Unit,
    onUpdateTrustedDeviceRules: (TrustedDeviceRules) -> Unit,
    onRemoveTrustedDevice: (String) -> Unit,
    onSetEnergyMode: (EnergyMode) -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
    onSetFeatureSettings: (FeatureSettings) -> Unit,
    extendedFeatureActions: ExtendedFeatureActions,
    onStopSession: () -> Unit,
    selectedFeatureKey: String?,
    onSelectedFeatureChange: (String?) -> Unit
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (destination) {
            VeyroDestination.ECOSYSTEM -> EcosystemPage(
                uiState = nearbyUiState,
                onStartAdvertising = onStartAdvertising,
                onStartDiscovery = onStartDiscovery,
                onRequestConnection = onRequestConnection,
                onStopSession = onStopSession
            )

            VeyroDestination.RESOURCES -> ResourcesPage(
                uiState = nearbyUiState,
                onStartAdvertising = onStartAdvertising,
                onStartDiscovery = onStartDiscovery,
                onRequestConnection = onRequestConnection,
                onSendCommand = onSendCommand,
                onStartRemoteFindAlarm = onStartRemoteFindAlarm,
                onStopRemoteFindAlarm = onStopRemoteFindAlarm,
                notificationListenerGranted = notificationListenerGranted,
                notificationPolicyGranted = notificationPolicyGranted,
                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                onDismissRemoteNotification = onDismissRemoteNotification,
                onMediaControlCommand = onMediaControlCommand,
                onSendSmsTransmitOrder = onSendSmsTransmitOrder,
                onSendSafeCustomCommand = onSendSafeCustomCommand,
                onShareUrl = onShareUrl,
                onRemoteInput = onRemoteInput,
                onPickFile = onPickFile,
                onApproveIncomingFile = onApproveIncomingFile,
                onRejectIncomingFile = onRejectIncomingFile,
                extendedFeatureActions = extendedFeatureActions,
                onStopSession = onStopSession,
                selectedFeatureKey = selectedFeatureKey,
                onSelectedFeatureChange = onSelectedFeatureChange
            )

            VeyroDestination.SETTINGS -> SettingsPage(
                uiState = nearbyUiState,
                notificationListenerGranted = notificationListenerGranted,
                notificationPolicyGranted = notificationPolicyGranted,
                telephonyPermissionsGranted = telephonyPermissionsGranted,
                callScreeningRoleGranted = callScreeningRoleGranted,
                cameraPermissionGranted = cameraPermissionGranted,
                remoteInputAccessibilityGranted = remoteInputAccessibilityGranted,
                onRequestTelephonyPermissions = onRequestTelephonyPermissions,
                onRequestCallScreeningRole = onRequestCallScreeningRole,
                onRequestCameraPermission = onRequestCameraPermission,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                onUpdateTrustedDeviceRules = onUpdateTrustedDeviceRules,
                onRemoveTrustedDevice = onRemoveTrustedDevice,
                onSetEnergyMode = onSetEnergyMode,
                onSetAppLanguage = onSetAppLanguage,
                onSetFeatureSettings = onSetFeatureSettings
            )

            VeyroDestination.ABOUT -> AboutPage()
        }
    }
}

@Composable
private fun EcosystemPage(
    uiState: NearbyClientUiState,
    onStartAdvertising: () -> Unit,
    onStartDiscovery: () -> Unit,
    onRequestConnection: (String) -> Unit,
    onStopSession: () -> Unit
) {
    VeyroPage {
        PageHeader(
            eyebrow = "VEYRO",
            title = "Seu ecossistema",
            subtitle = "Aparelhos próximos e atividades em uma única visão."
        )
        EcosystemRadar(
            uiState = uiState,
            onRequestConnection = onRequestConnection
        )
        Spacer(modifier = Modifier.height(16.dp))
        EcosystemConnectionPanel(
            uiState = uiState,
            onStartAdvertising = onStartAdvertising,
            onStartDiscovery = onStartDiscovery,
            onRequestConnection = onRequestConnection,
            onStopSession = onStopSession
        )
        Spacer(modifier = Modifier.height(24.dp))
        EcosystemActivityFeed(uiState)
    }
}

@Composable
private fun AboutPage() {
    VeyroPage {
        PageHeader(
            eyebrow = "VEYRO",
            title = "Sobre o Veyro",
            subtitle = "Conexões diretas, privadas e sob o seu controle."
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.veyro_logo_color),
                    contentDescription = "Veyro",
                    modifier = Modifier.size(104.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Veyro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    BuildConfig.VERSION_NAME,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Feito para o seu ecossistema", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "O Veyro conecta seus aparelhos localmente para compartilhar arquivos, estados e controles sem depender de uma nuvem.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Privacidade por padrão", fontWeight = FontWeight.SemiBold)
                Text(
                    "Cada conexão é confirmada por PIN e cada recurso pode ser desligado individualmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResourcesPage(
    uiState: NearbyClientUiState,
    onStartAdvertising: () -> Unit,
    onStartDiscovery: () -> Unit,
    onRequestConnection: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onStartRemoteFindAlarm: () -> Unit,
    onStopRemoteFindAlarm: () -> Unit,
    notificationListenerGranted: Boolean,
    notificationPolicyGranted: Boolean,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onDismissRemoteNotification: (String) -> Unit,
    onMediaControlCommand: (MediaControlRequest) -> Unit,
    onSendSmsTransmitOrder: (String, String) -> Unit,
    onSendSafeCustomCommand: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onRemoteInput: (RemoteInputCommand, Float, Float, String) -> Unit,
    onPickFile: () -> Unit,
    onApproveIncomingFile: (Long) -> Unit,
    onRejectIncomingFile: (Long) -> Unit,
    extendedFeatureActions: ExtendedFeatureActions,
    onStopSession: () -> Unit,
    selectedFeatureKey: String?,
    onSelectedFeatureChange: (String?) -> Unit
) {
    val debugPreview = BuildConfig.DEBUG &&
        uiState.connectionStage != ConnectionStage.CONNECTED

    if (selectedFeatureKey != null) {
        FeaturePageScaffold(
            title = featureHubTitle(selectedFeatureKey),
            scrollable = selectedFeatureKey != "media",
            onBack = { onSelectedFeatureChange(null) }
        ) {
            if (uiState.connectionStage == ConnectionStage.CONNECTED) {
                SessionControls(
                    uiState = uiState,
                    onStartAdvertising = onStartAdvertising,
                    onStartDiscovery = onStartDiscovery,
                    onRequestConnection = onRequestConnection,
                    onSendCommand = onSendCommand,
                    onStartRemoteFindAlarm = onStartRemoteFindAlarm,
                    onStopRemoteFindAlarm = onStopRemoteFindAlarm,
                    notificationListenerGranted = notificationListenerGranted,
                    notificationPolicyGranted = notificationPolicyGranted,
                    onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                    onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                    onDismissRemoteNotification = onDismissRemoteNotification,
                    onMediaControlCommand = onMediaControlCommand,
                    onSendSmsTransmitOrder = onSendSmsTransmitOrder,
                    onSendSafeCustomCommand = onSendSafeCustomCommand,
                    onShareUrl = onShareUrl,
                    onRemoteInput = onRemoteInput,
                    onPickFile = onPickFile,
                    onApproveIncomingFile = onApproveIncomingFile,
                    onRejectIncomingFile = onRejectIncomingFile,
                    extendedFeatureActions = extendedFeatureActions,
                    onStopSession = onStopSession,
                    selectedFeatureKey = selectedFeatureKey,
                    onSelectedFeatureChange = onSelectedFeatureChange
                )
            } else if (debugPreview) {
                FeatureHub(
                    uiState = uiState,
                    onSendCommand = {},
                    onStartRemoteFindAlarm = {},
                    onStopRemoteFindAlarm = {},
                    notificationListenerGranted = notificationListenerGranted,
                    notificationPolicyGranted = notificationPolicyGranted,
                    onOpenNotificationListenerSettings = {},
                    onOpenNotificationPolicySettings = {},
                    onDismissRemoteNotification = {},
                    onMediaControlCommand = {},
                    onSendSmsTransmitOrder = { _, _ -> },
                    onSendSafeCustomCommand = {},
                    onShareUrl = {},
                    onRemoteInput = { _, _, _, _ -> },
                    onPickFile = {},
                    onApproveIncomingFile = {},
                    onRejectIncomingFile = {},
                    extendedFeatureActions = extendedFeatureActions.copy(
                        onSelectConnectedEndpoint = {},
                        onPickContact = {},
                        onApproveContact = {},
                        onRejectContact = {},
                        onPresentationAction = { _, _ -> },
                        onDismissRemoteBlackout = {},
                        onStylusEvent = { _, _, _, _, _, _, _, _ -> },
                        onChooseSharedFolder = {},
                        onClearSharedFolder = {},
                        onRequestRemoteFileList = {},
                        onRequestRemoteFileDownload = {},
                        onSyncClipboard = {},
                        onRequestClipboardTile = {}
                    ),
                    onStopSession = {},
                    previewMode = true,
                    selectedKey = selectedFeatureKey,
                    onSelectedKeyChange = onSelectedFeatureChange
                )
            }
        }
        return
    }

    VeyroPage {
        PageHeader(
            eyebrow = "RECURSOS",
            title = uiState.connectedEndpointName
                ?.removePrefix("Veyro - ")
                ?: "Ações conectadas",
            subtitle = if (uiState.connectionStage == ConnectionStage.CONNECTED) {
                "Escolha uma função para começar."
            } else {
                "Arquivos, mídia, comandos e continuidade entre aparelhos."
            }
        )
        if (uiState.connectionStage == ConnectionStage.CONNECTED) {
            SessionControls(
                uiState = uiState,
                onStartAdvertising = onStartAdvertising,
                onStartDiscovery = onStartDiscovery,
                onRequestConnection = onRequestConnection,
                onSendCommand = onSendCommand,
                onStartRemoteFindAlarm = onStartRemoteFindAlarm,
                onStopRemoteFindAlarm = onStopRemoteFindAlarm,
                notificationListenerGranted = notificationListenerGranted,
                notificationPolicyGranted = notificationPolicyGranted,
                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                onDismissRemoteNotification = onDismissRemoteNotification,
                onMediaControlCommand = onMediaControlCommand,
                onSendSmsTransmitOrder = onSendSmsTransmitOrder,
                onSendSafeCustomCommand = onSendSafeCustomCommand,
                onShareUrl = onShareUrl,
                onRemoteInput = onRemoteInput,
                onPickFile = onPickFile,
                onApproveIncomingFile = onApproveIncomingFile,
                onRejectIncomingFile = onRejectIncomingFile,
                extendedFeatureActions = extendedFeatureActions,
                onStopSession = onStopSession,
                selectedFeatureKey = selectedFeatureKey,
                onSelectedFeatureChange = onSelectedFeatureChange
            )
        } else if (debugPreview) {
            if (selectedFeatureKey == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "PRÉ-VISUALIZAÇÃO DEBUG",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Todas as telas estão disponíveis para revisão. Os comandos ficam desativados até existir uma conexão.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            FeatureHub(
                uiState = uiState,
                onSendCommand = {},
                onStartRemoteFindAlarm = {},
                onStopRemoteFindAlarm = {},
                notificationListenerGranted = notificationListenerGranted,
                notificationPolicyGranted = notificationPolicyGranted,
                onOpenNotificationListenerSettings = {},
                onOpenNotificationPolicySettings = {},
                onDismissRemoteNotification = {},
                onMediaControlCommand = {},
                onSendSmsTransmitOrder = { _, _ -> },
                onSendSafeCustomCommand = {},
                onShareUrl = {},
                onRemoteInput = { _, _, _, _ -> },
                onPickFile = {},
                onApproveIncomingFile = {},
                onRejectIncomingFile = {},
                extendedFeatureActions = extendedFeatureActions.copy(
                    onSelectConnectedEndpoint = {},
                    onPickContact = {},
                    onApproveContact = {},
                    onRejectContact = {},
                    onPresentationAction = { _, _ -> },
                    onDismissRemoteBlackout = {},
                    onStylusEvent = { _, _, _, _, _, _, _, _ -> },
                    onChooseSharedFolder = {},
                    onClearSharedFolder = {},
                    onRequestRemoteFileList = {},
                    onRequestRemoteFileDownload = {},
                    onSyncClipboard = {},
                    onRequestClipboardTile = {}
                ),
                onStopSession = {},
                previewMode = true,
                selectedKey = selectedFeatureKey,
                onSelectedKeyChange = onSelectedFeatureChange
            )
        } else if (selectedFeatureKey == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Conecte um aparelho", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Os recursos aparecem aqui assim que uma conexão segura for confirmada no Ecossistema.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePageScaffold(
    title: String,
    scrollable: Boolean = true,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val pageContentModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 16.dp)
                .let { modifier ->
                    if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier
                }
            Column(
                modifier = pageContentModifier,
                content = content
            )
        }
    }
}

@Composable
private fun SettingsPage(
    uiState: NearbyClientUiState,
    notificationListenerGranted: Boolean,
    notificationPolicyGranted: Boolean,
    telephonyPermissionsGranted: Boolean,
    callScreeningRoleGranted: Boolean,
    cameraPermissionGranted: Boolean,
    remoteInputAccessibilityGranted: Boolean,
    onRequestTelephonyPermissions: () -> Unit,
    onRequestCallScreeningRole: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onUpdateTrustedDeviceRules: (TrustedDeviceRules) -> Unit,
    onRemoveTrustedDevice: (String) -> Unit,
    onSetEnergyMode: (EnergyMode) -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
    onSetFeatureSettings: (FeatureSettings) -> Unit
) {
    VeyroPage {
        PageHeader(
            eyebrow = "CONFIGURAÇÕES",
            title = "Central de controle",
            subtitle = "Escolha como cada parte do seu ecossistema deve funcionar."
        )
        FeatureSettingsPanel(
            settings = uiState.featureSettings,
            onSettingsChange = onSetFeatureSettings
        )
        Spacer(modifier = Modifier.height(16.dp))
        EnergyModePanel(
            selectedMode = uiState.energyMode,
            onSelectMode = onSetEnergyMode
        )
        Spacer(modifier = Modifier.height(16.dp))
        LanguagePanel(
            selectedLanguage = uiState.appLanguage,
            onSelectLanguage = onSetAppLanguage
        )
        Spacer(modifier = Modifier.height(16.dp))
        TrustHubPanel(
            devices = uiState.trustedDevices,
            connectedDeviceName = uiState.connectedEndpointName,
            onUpdateRules = onUpdateTrustedDeviceRules,
            onRemoveDevice = onRemoveTrustedDevice
        )
        if (uiState.featureSettings.requiresSpecialAccess) {
            Spacer(modifier = Modifier.height(16.dp))
            FeatureAccessCard(
                featureSettings = uiState.featureSettings,
                notificationListenerGranted = notificationListenerGranted,
                notificationPolicyGranted = notificationPolicyGranted,
                telephonyPermissionsGranted = telephonyPermissionsGranted,
                callScreeningRoleGranted = callScreeningRoleGranted,
                cameraPermissionGranted = cameraPermissionGranted,
                remoteInputAccessibilityGranted = remoteInputAccessibilityGranted,
                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                onRequestTelephonyPermissions = onRequestTelephonyPermissions,
                onRequestCallScreeningRole = onRequestCallScreeningRole,
                onRequestCameraPermission = onRequestCameraPermission,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Princípios da conexão", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsPrinciple("Direta", "A comunicação ocorre entre os aparelhos, sem nuvem.")
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                SettingsPrinciple("Confirmada", "Um PIN igual nos dois aparelhos valida cada conexão.")
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                SettingsPrinciple("Temporária", "Encerrar a sessão interrompe o canal e as sincronizações.")
            }
        }
    }
}

@Composable
private fun FeatureSettingsPanel(
    settings: FeatureSettings,
    onSettingsChange: (FeatureSettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recursos do ecossistema", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${settings.enabledCount} de ${FeatureSettings.AVAILABLE_COUNT} ativos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            FeatureCategoryLabel("CONTINUIDADE")
            FeatureToggleRow(
                title = "Transferência de arquivos",
                detail = "Envie, receba e aprove arquivos entre aparelhos.",
                checked = settings.fileTransfer,
                icon = Icons.Default.Devices,
                onCheckedChange = { onSettingsChange(settings.copy(fileTransfer = it)) }
            )
            FeatureToggleRow(
                title = "Estado da bateria",
                detail = "Compartilhe carga e fonte de energia durante a conexão.",
                checked = settings.batterySync,
                icon = Icons.Default.Hub,
                onCheckedChange = { onSettingsChange(settings.copy(batterySync = it)) }
            )
            FeatureToggleRow(
                title = "Relatório de conectividade",
                detail = "Compartilhe transporte, internet, rede limitada e sinal disponível.",
                checked = settings.connectivitySync,
                icon = Icons.Default.Hub,
                onCheckedChange = { onSettingsChange(settings.copy(connectivitySync = it)) }
            )
            FeatureToggleRow(
                title = "Ping P2P",
                detail = "Meça periodicamente a latência direta entre os aparelhos.",
                checked = settings.ping,
                icon = Icons.Default.Hub,
                onCheckedChange = { onSettingsChange(settings.copy(ping = it)) }
            )
            FeatureToggleRow(
                title = "Links compartilhados",
                detail = "Envie links que só abrem após um toque no destino.",
                checked = settings.sharedLinks,
                icon = Icons.Default.Hub,
                onCheckedChange = { onSettingsChange(settings.copy(sharedLinks = it)) }
            )
            FeatureToggleRow(
                title = "Sincronizar clipboard",
                detail = "Compartilhe somente texto ao voltar ao Veyro ou ao tocar em sincronizar.",
                checked = settings.clipboardSync,
                icon = Icons.Default.Devices,
                onCheckedChange = { onSettingsChange(settings.copy(clipboardSync = it)) }
            )
            FeatureToggleRow(
                title = "Pasta remota compartilhada",
                detail = "Exponha somente uma pasta escolhida pelo seletor seguro do Android.",
                checked = settings.remoteFiles,
                icon = Icons.Default.Devices,
                onCheckedChange = { onSettingsChange(settings.copy(remoteFiles = it)) }
            )

            FeatureCategoryLabel("MÍDIA E COMUNICAÇÃO")
            FeatureToggleRow(
                title = "Sincronizar notificações",
                detail = "Mostre e descarte notificações do aparelho conectado.",
                checked = settings.notificationSync,
                icon = Icons.Default.AutoAwesome,
                onCheckedChange = { onSettingsChange(settings.copy(notificationSync = it)) }
            )
            FeatureToggleRow(
                title = "Controle de mídia",
                detail = "Acompanhe e controle a reprodução remotamente.",
                checked = settings.mediaControl,
                icon = Icons.Default.AutoAwesome,
                onCheckedChange = { onSettingsChange(settings.copy(mediaControl = it)) }
            )
            FeatureToggleRow(
                title = "Chamadas e SMS",
                detail = "Sincronize eventos e confirme localmente cada SMS.",
                checked = settings.telephonySync,
                icon = Icons.Default.Devices,
                onCheckedChange = { onSettingsChange(settings.copy(telephonySync = it)) }
            )
            FeatureToggleRow(
                title = "Sincronização de contatos",
                detail = "Ofereça contatos selecionados e confirme cada importação.",
                checked = settings.contactSync,
                icon = Icons.Default.Devices,
                onCheckedChange = { onSettingsChange(settings.copy(contactSync = it)) }
            )
            FeatureToggleRow(
                title = "Modo de apresentação",
                detail = "Controle slides, tela preta e cronômetro.",
                checked = settings.presentationMode,
                icon = Icons.Default.AutoAwesome,
                onCheckedChange = { onSettingsChange(settings.copy(presentationMode = it)) }
            )

            FeatureCategoryLabel("ACESSO REMOTO")
            FeatureToggleRow(
                title = "Encontrar aparelho",
                detail = "Permita solicitar um alarme no aparelho conectado.",
                checked = settings.findDevice,
                icon = Icons.Default.Hub,
                onCheckedChange = { onSettingsChange(settings.copy(findDevice = it)) }
            )
            FeatureToggleRow(
                title = "Ações remotas seguras",
                detail = "Controle volume e lanterna com comandos nativos.",
                checked = settings.safeCommands,
                icon = Icons.Default.Settings,
                onCheckedChange = { onSettingsChange(settings.copy(safeCommands = it)) }
            )
            FeatureToggleRow(
                title = "Mouse e teclado remotos",
                detail = "Use este aparelho como touchpad e teclado.",
                checked = settings.remoteInput,
                icon = Icons.Default.Devices,
                onCheckedChange = { onSettingsChange(settings.copy(remoteInput = it)) }
            )
            FeatureToggleRow(
                title = "Mesa digitalizadora",
                detail = "Transmita stylus, pressão, inclinação e botão principal.",
                checked = settings.drawingTablet,
                icon = Icons.Default.Devices,
                onCheckedChange = { onSettingsChange(settings.copy(drawingTablet = it)) }
            )
        }
    }
}

@Composable
private fun FeatureCategoryLabel(label: String) {
    Text(
        label,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FeatureToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (checked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LanguagePanel(
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Idioma", style = MaterialTheme.typography.titleLarge)
            Text(
                "Escolha o idioma da interface do Veyro.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppLanguage.entries.forEach { language ->
                val title = when (language) {
                    AppLanguage.PORTUGUESE -> "Português"
                    AppLanguage.ENGLISH -> "Inglês"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectLanguage(language) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedLanguage == language,
                        onClick = { onSelectLanguage(language) }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(title, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EnergyModePanel(
    selectedMode: EnergyMode,
    onSelectMode: (EnergyMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Energia e rádio", style = MaterialTheme.typography.titleLarge)
            Text(
                "Escolha quanto tempo o Veyro pode manter o ecossistema ativo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            EnergyMode.entries.forEach { mode ->
                val title = when (mode) {
                    EnergyMode.CONTINUOUS -> "Contínuo"
                    EnergyMode.BALANCED -> "Equilibrado"
                    EnergyMode.BATTERY_SAVER -> "Economia"
                }
                val description = when (mode) {
                    EnergyMode.CONTINUOUS -> "Mantém o processador disponível durante toda a sessão."
                    EnergyMode.BALANCED -> "Mantém o wakelock apenas durante transferências."
                    EnergyMode.BATTERY_SAVER -> "Além disso, encerra buscas ociosas quando a tela apaga."
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectMode(mode) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == mode,
                        onClick = { onSelectMode(mode) }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

private val LocalVeyroLanguage = staticCompositionLocalOf { AppLanguage.PORTUGUESE }

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = VeyroI18n.translate(text, LocalVeyroLanguage.current),
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = style
    )
}

@Composable
private fun TrustHubPanel(
    devices: List<TrustedDeviceRules>,
    connectedDeviceName: String?,
    onUpdateRules: (TrustedDeviceRules) -> Unit,
    onRemoveDevice: (String) -> Unit
) {
    Column {
        Text("Trust Hub", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Cada aparelho só recebe os privilégios que você ativar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (devices.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Nenhum aparelho confirmado", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Depois de confirmar o PIN de uma conexão, o aparelho aparecerá aqui com todos os privilégios desativados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            devices.forEachIndexed { index, device ->
                TrustedDeviceCard(
                    rules = device,
                    connected = device.deviceName == connectedDeviceName,
                    onUpdateRules = onUpdateRules,
                    onRemoveDevice = onRemoveDevice
                )
                if (index != devices.lastIndex) Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun TrustedDeviceCard(
    rules: TrustedDeviceRules,
    connected: Boolean,
    onUpdateRules: (TrustedDeviceRules) -> Unit,
    onRemoveDevice: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rules.deviceName.removePrefix("Veyro - "),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (connected) "Conectado agora" else "Aparelho conhecido",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                TextButton(onClick = { onRemoveDevice(rules.deviceName) }) {
                    Text("Remover")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            TrustRuleRow(
                title = "Salvar arquivos automaticamente",
                detail = "Sem pedir confirmação local a cada recebimento.",
                checked = rules.autoAcceptFiles,
                onCheckedChange = {
                    onUpdateRules(rules.copy(autoAcceptFiles = it))
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            TrustRuleRow(
                title = "Permitir localizar este aparelho",
                detail = "Autoriza o outro aparelho a tocar o alarme remoto.",
                checked = rules.allowFindDevice,
                onCheckedChange = {
                    onUpdateRules(rules.copy(allowFindDevice = it))
                }
            )
        }
    }
}

@Composable
private fun TrustRuleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsPrinciple(title: String, detail: String) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VeyroPage(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 900.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            content = content
        )
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String) {
    Text(
        text = eyebrow,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = title, style = MaterialTheme.typography.headlineLarge)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun EcosystemRadar(
    uiState: NearbyClientUiState,
    onRequestConnection: (String) -> Unit
) {
    val nodes = buildList {
        uiState.connectedEndpoints.forEach { connected ->
            add(
                DiscoveredEndpoint(
                    id = connected.id,
                    name = connected.name
                )
            )
        }
        addAll(
            uiState.discoveredEndpoints.filter { endpoint ->
                uiState.connectedEndpoints.none { it.id == endpoint.id }
            }
        )
    }.take(5)
    val hasActiveTransfer = uiState.rawFileTransfers.any {
        it.status == RawFileStatus.IN_PROGRESS || it.status == RawFileStatus.SAVING
    }
    val transition = rememberInfiniteTransition(label = "transferPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nodePulse"
    )
    val ringColor = MaterialTheme.colorScheme.outlineVariant
    val linkColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Radar", style = MaterialTheme.typography.titleMedium)
                EcosystemStatusChip(uiState.connectionStage)
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val outerRadius = size.minDimension * 0.40f
                    drawCircle(ringColor, outerRadius, style = Stroke(width = 2f))
                    drawCircle(ringColor.copy(alpha = 0.55f), outerRadius * 0.58f, style = Stroke(width = 2f))
                    nodes.forEachIndexed { index, _ ->
                        val angle = (index * 2.0 * Math.PI / nodes.size.coerceAtLeast(1)) - Math.PI / 2
                        drawLine(
                            color = linkColor,
                            start = center,
                            end = Offset(
                                x = center.x + cos(angle).toFloat() * outerRadius,
                                y = center.y + sin(angle).toFloat() * outerRadius
                            ),
                            strokeWidth = 3f
                        )
                    }
                }
                Surface(
                    modifier = Modifier.size(104.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.veyro_logo_white),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = "Este aparelho",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                nodes.forEachIndexed { index, endpoint ->
                    val angle = (index * 2.0 * Math.PI / nodes.size.coerceAtLeast(1)) - Math.PI / 2
                    val isConnected = uiState.connectedEndpoints.any { it.id == endpoint.id }
                    RadarNode(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = (cos(angle) * 110).dp,
                                y = (sin(angle) * 110).dp
                            )
                            .graphicsLayer {
                                scaleX = if (isConnected && hasActiveTransfer) pulse else 1f
                                scaleY = if (isConnected && hasActiveTransfer) pulse else 1f
                            },
                        name = endpoint.name,
                        connected = isConnected,
                        enabled = uiState.connectionStage in setOf(
                            ConnectionStage.ACTIVE,
                            ConnectionStage.DISCOVERING
                        ),
                        onClick = { onRequestConnection(endpoint.id) }
                    )
                }
                if (nodes.isEmpty()) {
                    Text(
                        text = "Inicie uma busca para revelar aparelhos próximos",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarNode(
    modifier: Modifier,
    name: String,
    connected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .size(82.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (connected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = name.removePrefix("Veyro - "),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EcosystemStatusChip(stage: ConnectionStage) {
    val (label, color) = when (stage) {
        ConnectionStage.CONNECTED -> "Conectado" to MaterialTheme.colorScheme.primaryContainer
        ConnectionStage.ACTIVE -> "Sempre ativo" to MaterialTheme.colorScheme.primaryContainer
        ConnectionStage.DISCOVERING -> "Buscando" to MaterialTheme.colorScheme.tertiaryContainer
        ConnectionStage.ADVERTISING -> "Visível" to MaterialTheme.colorScheme.secondaryContainer
        ConnectionStage.CONNECTING,
        ConnectionStage.AUTHENTICATING -> "Conectando" to MaterialTheme.colorScheme.tertiaryContainer
        ConnectionStage.ERROR -> "Atenção" to MaterialTheme.colorScheme.errorContainer
        ConnectionStage.IDLE -> "Em espera" to MaterialTheme.colorScheme.surface
    }
    Surface(shape = CircleShape, color = color) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun EcosystemConnectionPanel(
    uiState: NearbyClientUiState,
    onStartAdvertising: () -> Unit,
    onStartDiscovery: () -> Unit,
    onRequestConnection: (String) -> Unit,
    onStopSession: () -> Unit
) {
    when (uiState.connectionStage) {
        ConnectionStage.IDLE,
        ConnectionStage.ERROR -> {
            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onStartDiscovery) {
                Text("Ativar ecossistema contínuo")
            }
        }

        ConnectionStage.ACTIVE -> {
            OperationCard(
                title = "Ecossistema contínuo ativo",
                message = uiState.statusMessage
                    ?: "Este aparelho está visível e procurando ao mesmo tempo.",
                stateName = uiState.connectionStage.name,
                stopButtonLabel = "Desativar",
                onStopSession = onStopSession
            )
            if (uiState.discoveredEndpoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                EndpointList(uiState.discoveredEndpoints, onRequestConnection)
            }
        }

        ConnectionStage.DISCOVERING -> {
            OperationCard(
                title = "Procurando aparelhos",
                message = uiState.statusMessage ?: "A busca está ativa.",
                stateName = uiState.connectionStage.name,
                onStopSession = onStopSession
            )
            if (uiState.discoveredEndpoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                EndpointList(uiState.discoveredEndpoints, onRequestConnection)
            }
        }

        ConnectionStage.ADVERTISING -> OperationCard(
            title = "Este aparelho está visível",
            message = uiState.statusMessage ?: "Outro aparelho Veyro pode encontrá-lo.",
            stateName = uiState.connectionStage.name,
            onStopSession = onStopSession
        )

        ConnectionStage.CONNECTING -> OperationCard(
            title = "Criando conexão segura",
            message = uiState.statusMessage ?: "Aguardando o outro aparelho.",
            stateName = uiState.connectionStage.name,
            onStopSession = onStopSession
        )

        ConnectionStage.AUTHENTICATING -> OperationCard(
            title = "Confirme a identidade",
            message = "Compare o PIN exibido nos dois aparelhos.",
            stateName = uiState.connectionStage.name,
            onStopSession = onStopSession
        )

        ConnectionStage.CONNECTED -> OperationCard(
            title = uiState.connectedEndpointName?.removePrefix("Veyro - ") ?: "Aparelho conectado",
            message = uiState.statusMessage ?: "Sincronização direta ativa.",
            stateName = uiState.connectionStage.name,
            stopButtonLabel = "Desconectar",
            onStopSession = onStopSession
        )
    }
}

@Composable
private fun EcosystemActivityFeed(uiState: NearbyClientUiState) {
    val activities = buildList {
        uiState.errorMessage?.let { add(EcosystemActivity("Atenção necessária", it)) }
        uiState.rawFileTransfers.takeLast(2).reversed().forEach { transfer ->
            add(
                EcosystemActivity(
                    title = transfer.fileName ?: "Transferência de arquivo",
                    detail = when (transfer.status) {
                        RawFileStatus.IN_PROGRESS -> "Em andamento: ${transfer.progressPercent}%"
                        RawFileStatus.AWAITING_APPROVAL -> "Aguardando sua aprovação"
                        RawFileStatus.SAVING -> "Salvando no aparelho"
                        RawFileStatus.SAVED -> "Recebido e salvo"
                        RawFileStatus.COMPLETED -> "Transferência concluída"
                        RawFileStatus.FAILED -> "Falha na transferência"
                        RawFileStatus.CANCELED -> "Transferência cancelada"
                    }
                )
            )
        }
        uiState.remoteNotifications.take(2).forEach { notification ->
            add(EcosystemActivity(notification.appName, notification.title.ifBlank { "Nova notificação" }))
        }
        uiState.remoteBatteryStatus?.let { battery ->
            add(EcosystemActivity("Bateria remota", "${battery.chargePercentage}% • ${battery.powerSourceLabel}"))
        }
        uiState.remoteConnectivityStatus?.let { connectivity ->
            val internet = if (connectivity.hasInternet) "com internet" else "sem internet"
            add(EcosystemActivity("Conectividade remota", "${connectivity.transportLabel} • $internet"))
        }
        uiState.remotePingStatus?.let { ping ->
            add(EcosystemActivity("Ping P2P", "${ping.roundTripMillis} ms"))
        }
        if (isEmpty()) {
            add(
                EcosystemActivity(
                    "Tudo tranquilo",
                    uiState.statusMessage ?: "As atividades do ecossistema aparecerão aqui."
                )
            )
        }
    }.take(5)

    Text("Atividade recente", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            activities.forEachIndexed { index, activity ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {}
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activity.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            activity.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (index != activities.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FeatureAccessCard(
    featureSettings: FeatureSettings,
    notificationListenerGranted: Boolean,
    notificationPolicyGranted: Boolean,
    telephonyPermissionsGranted: Boolean,
    callScreeningRoleGranted: Boolean,
    cameraPermissionGranted: Boolean,
    remoteInputAccessibilityGranted: Boolean,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onRequestTelephonyPermissions: () -> Unit,
    onRequestCallScreeningRole: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val needsNotificationAccess = featureSettings.notificationSync || featureSettings.mediaControl
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Acessos das novas funções", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            if (needsNotificationAccess) Text(
                text = "Notificações: ${if (notificationListenerGranted) "ativo" else "pendente"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (featureSettings.findDevice) Text(
                text = "Modos/Não Perturbe: ${if (notificationPolicyGranted) "ativo" else "pendente"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (featureSettings.telephonySync) Text(
                text = "Telefonia e SMS: ${if (telephonyPermissionsGranted) "ativo" else "pendente"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (featureSettings.telephonySync) Text(
                text = "Veyro como identificador: ${if (callScreeningRoleGranted) "ativo" else "inativo (opcional)"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (featureSettings.safeCommands) Text(
                text = "Lanterna remota: ${if (cameraPermissionGranted) "ativo" else "pendente"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (featureSettings.remoteInput) Text(
                text = "Controle remoto: ${if (remoteInputAccessibilityGranted) "ativo" else "pendente"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (needsNotificationAccess && !notificationListenerGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenNotificationListenerSettings
                ) {
                    Text("Ativar acesso às notificações")
                }
            }
            if (featureSettings.findDevice && !notificationPolicyGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenNotificationPolicySettings
                ) {
                    Text("Ativar acesso a modos")
                }
            }
            if (featureSettings.telephonySync && !telephonyPermissionsGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestTelephonyPermissions
                ) {
                    Text("Permitir telefonia e SMS")
                }
            }
            if (featureSettings.telephonySync && !callScreeningRoleGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestCallScreeningRole
                ) {
                    Text("Usar Veyro no lugar do identificador atual")
                }
            }
            if (featureSettings.safeCommands && !cameraPermissionGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestCameraPermission
                ) {
                    Text("Permitir lanterna remota")
                }
            }
            if (featureSettings.remoteInput && !remoteInputAccessibilityGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenAccessibilitySettings
                ) {
                    Text("Ativar controle remoto")
                }
            }
            if (featureSettings.telephonySync) Spacer(modifier = Modifier.height(8.dp))
            if (featureSettings.telephonySync) Text(
                text = if (callScreeningRoleGranted) {
                    "O Veyro pode sincronizar nome e número. Eventos só são compartilhados durante uma conexão; todo SMS remoto exige confirmação local."
                } else {
                    "Sem o acesso de identificação de chamadas, o Veyro sincroniza apenas o estado da chamada, sem nome ou número. Todo SMS remoto exige confirmação local."
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SessionControls(
    uiState: NearbyClientUiState,
    onStartAdvertising: () -> Unit,
    onStartDiscovery: () -> Unit,
    onRequestConnection: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onStartRemoteFindAlarm: () -> Unit,
    onStopRemoteFindAlarm: () -> Unit,
    notificationListenerGranted: Boolean,
    notificationPolicyGranted: Boolean,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onDismissRemoteNotification: (String) -> Unit,
    onMediaControlCommand: (MediaControlRequest) -> Unit,
    onSendSmsTransmitOrder: (String, String) -> Unit,
    onSendSafeCustomCommand: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onRemoteInput: (RemoteInputCommand, Float, Float, String) -> Unit,
    onPickFile: () -> Unit,
    onApproveIncomingFile: (Long) -> Unit,
    onRejectIncomingFile: (Long) -> Unit,
    extendedFeatureActions: ExtendedFeatureActions,
    onStopSession: () -> Unit,
    selectedFeatureKey: String?,
    onSelectedFeatureChange: (String?) -> Unit
) {
    when (uiState.connectionStage) {
        ConnectionStage.IDLE,
        ConnectionStage.ERROR -> {
            Text(
                text = "O que deseja fazer?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStartAdvertising
                ) {
                    Text("Enviar arquivos")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onStartDiscovery
                ) {
                    Text("Receber arquivos")
                }
            }
        }

        ConnectionStage.ACTIVE -> OperationCard(
            title = "Ecossistema contínuo ativo",
            message = uiState.statusMessage ?: "Aguardando conexão automática...",
            stateName = uiState.connectionStage.name,
            stopButtonLabel = "Desativar",
            onStopSession = onStopSession
        )

        ConnectionStage.ADVERTISING -> OperationCard(
            title = "Aguardando destinatário",
            message = uiState.statusMessage ?: "Este aparelho está visível.",
            stateName = uiState.connectionStage.name,
            onStopSession = onStopSession
        )

        ConnectionStage.DISCOVERING -> {
            OperationCard(
                title = "Procurando aparelhos",
                message = uiState.statusMessage ?: "Aguarde...",
                stateName = uiState.connectionStage.name,
                onStopSession = onStopSession
            )
            Spacer(modifier = Modifier.height(12.dp))
            EndpointList(
                endpoints = uiState.discoveredEndpoints,
                onRequestConnection = onRequestConnection
            )
        }

        ConnectionStage.CONNECTING -> OperationCard(
            title = "Solicitando conexão",
            message = uiState.statusMessage ?: "Aguardando o outro aparelho...",
            stateName = uiState.connectionStage.name,
            onStopSession = onStopSession
        )

        ConnectionStage.AUTHENTICATING -> OperationCard(
            title = "Validando segurança",
            message = "Compare o PIN exibido nos dois aparelhos.",
            stateName = uiState.connectionStage.name,
            onStopSession = onStopSession
        )

        ConnectionStage.CONNECTED -> {
            if (uiState.connectedEndpoints.size > 1) {
                ConnectedEndpointSelector(
                    uiState = uiState,
                    onSelect = extendedFeatureActions.onSelectConnectedEndpoint
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            FeatureHub(
                uiState = uiState,
                onSendCommand = onSendCommand,
                onStartRemoteFindAlarm = onStartRemoteFindAlarm,
                onStopRemoteFindAlarm = onStopRemoteFindAlarm,
                notificationListenerGranted = notificationListenerGranted,
                notificationPolicyGranted = notificationPolicyGranted,
                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                onDismissRemoteNotification = onDismissRemoteNotification,
                onMediaControlCommand = onMediaControlCommand,
                onSendSmsTransmitOrder = onSendSmsTransmitOrder,
                onSendSafeCustomCommand = onSendSafeCustomCommand,
                onShareUrl = onShareUrl,
                onRemoteInput = onRemoteInput,
                onPickFile = onPickFile,
                onApproveIncomingFile = onApproveIncomingFile,
                onRejectIncomingFile = onRejectIncomingFile,
                extendedFeatureActions = extendedFeatureActions,
                onStopSession = onStopSession,
                selectedKey = selectedFeatureKey,
                onSelectedKeyChange = onSelectedFeatureChange
            )
        }
    }
}

@Composable
private fun FeatureHub(
    uiState: NearbyClientUiState,
    onSendCommand: (String) -> Unit,
    onStartRemoteFindAlarm: () -> Unit,
    onStopRemoteFindAlarm: () -> Unit,
    notificationListenerGranted: Boolean,
    notificationPolicyGranted: Boolean,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onDismissRemoteNotification: (String) -> Unit,
    onMediaControlCommand: (MediaControlRequest) -> Unit,
    onSendSmsTransmitOrder: (String, String) -> Unit,
    onSendSafeCustomCommand: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onRemoteInput: (RemoteInputCommand, Float, Float, String) -> Unit,
    onPickFile: () -> Unit,
    onApproveIncomingFile: (Long) -> Unit,
    onRejectIncomingFile: (Long) -> Unit,
    extendedFeatureActions: ExtendedFeatureActions,
    onStopSession: () -> Unit,
    previewMode: Boolean = false,
    selectedKey: String?,
    onSelectedKeyChange: (String?) -> Unit
) {
    val features = uiState.featureSettings
    val items = buildList {
        if (features.fileTransfer || previewMode) add(FeatureHubItem("files", "Enviar arquivos", Icons.Default.Description))
        if (features.clipboardSync || previewMode) add(FeatureHubItem("clipboard", "Área de transferência", Icons.Default.ContentPaste))
        if (features.presentationMode || previewMode) add(FeatureHubItem("presentation", "Controle de apresentação", Icons.Default.Slideshow))
        if (features.mediaControl || previewMode) add(FeatureHubItem("media", "Controle multimídia", Icons.Default.PlayCircle))
        if (features.remoteInput || previewMode) add(FeatureHubItem("remote_input", "Mouse e teclado", Icons.Default.Keyboard))
        if (features.safeCommands || previewMode) add(FeatureHubItem("commands", "Executar comando", Icons.Default.Terminal))
        if (features.batterySync || previewMode) add(FeatureHubItem("battery", "Estado da bateria", Icons.Default.BatteryChargingFull))
        if (features.connectivitySync || features.ping || previewMode) add(FeatureHubItem("connectivity", "Conectividade e ping", Icons.Default.CellWifi))
        if (features.findDevice || previewMode) add(FeatureHubItem("find", "Encontrar aparelho", Icons.Default.MyLocation))
        if (features.notificationSync || previewMode) add(FeatureHubItem("notifications", "Notificações", Icons.Default.Notifications))
        if (features.telephonySync || previewMode) add(FeatureHubItem("telephony", "Chamadas e SMS", Icons.Default.Sms))
        if (features.sharedLinks || previewMode) add(FeatureHubItem("links", "Compartilhar link", Icons.Default.InsertLink))
        if (features.contactSync || previewMode) add(FeatureHubItem("contacts", "Sincronizar contatos", Icons.Default.Contacts))
        if (features.drawingTablet || previewMode) add(FeatureHubItem("tablet", "Mesa digitalizadora", Icons.Default.Draw))
        if (features.remoteFiles || previewMode) add(FeatureHubItem("remote_files", "Arquivos remotos", Icons.Default.Folder))
    }
    LaunchedEffect(items.map(FeatureHubItem::key), selectedKey) {
        if (selectedKey != null && selectedKey !in items.map(FeatureHubItem::key)) {
            onSelectedKeyChange(null)
        }
    }

    if (selectedKey == null) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    FeatureHubButton(
                        item = item,
                        selected = false,
                        onClick = { onSelectedKeyChange(item.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (!previewMode) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStopSession
            ) { Text("Desconectar") }
        }
        return
    }

    val key = selectedKey ?: return
    when (key) {
                    "files" -> RawFilePanel(
                        transfers = uiState.rawFileTransfers,
                        onPickFile = onPickFile,
                        onApproveIncomingFile = onApproveIncomingFile,
                        onRejectIncomingFile = onRejectIncomingFile
                    )
                    "clipboard" -> ClipboardSyncPanel(
                        status = uiState.clipboardStatus,
                        onSync = extendedFeatureActions.onSyncClipboard,
                        onAddQuickSettingsTile = extendedFeatureActions.onRequestClipboardTile
                    )
                    "presentation" -> PresentationPanel(
                        remoteState = uiState.remotePresentationState,
                        onMediaCommand = onMediaControlCommand,
                        onPresentationAction = extendedFeatureActions.onPresentationAction
                    )
                    "media" -> MediaControlPanel(uiState.remoteMediaState, onMediaControlCommand)
                    "remote_input" -> RemoteInputPanel(onRemoteInput)
                    "commands" -> {
                        SafeCustomCommandsPanel(uiState.remoteCustomCommandResults, onSendSafeCustomCommand)
                        Spacer(modifier = Modifier.height(12.dp))
                        CommandPanel(uiState.receivedCommands, onSendCommand)
                    }
                    "battery" -> BatteryStatusCard(uiState.remoteBatteryStatus)
                    "connectivity" -> ConnectivityStatusCard(
                        status = uiState.remoteConnectivityStatus,
                        ping = uiState.remotePingStatus,
                        showConnectivity = features.connectivitySync || previewMode,
                        showPing = features.ping || previewMode
                    )
                    "find" -> FindMyDevicePanel(
                        notificationPolicyGranted = notificationPolicyGranted,
                        onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                        onStartRemoteAlarm = onStartRemoteFindAlarm,
                        onStopRemoteAlarm = onStopRemoteFindAlarm
                    )
                    "notifications" -> NotificationSyncPanel(
                        notificationListenerGranted = notificationListenerGranted,
                        notifications = uiState.remoteNotifications,
                        onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                        onDismissNotification = onDismissRemoteNotification
                    )
                    "telephony" -> TelephonyPanel(uiState.remoteTelecommunicationEvents, onSendSmsTransmitOrder)
                    "links" -> ShareUrlPanel(uiState.remoteSharedUrls, onShareUrl)
                    "contacts" -> ContactSyncPanel(
                        pendingImports = uiState.pendingContactImports,
                        lastResult = uiState.lastContactResult,
                        onPickContact = extendedFeatureActions.onPickContact,
                        onApprove = extendedFeatureActions.onApproveContact,
                        onReject = extendedFeatureActions.onRejectContact
                    )
                    "tablet" -> DrawingTabletPanel(extendedFeatureActions.onStylusEvent)
                    "remote_files" -> RemoteFilesPanel(
                        sharedFolderName = uiState.sharedFolderName,
                        remoteItems = uiState.remoteFileItems,
                        remoteParentId = uiState.remoteFileParentId,
                        remoteMessage = uiState.remoteFileMessage,
                        onChooseSharedFolder = extendedFeatureActions.onChooseSharedFolder,
                        onClearSharedFolder = extendedFeatureActions.onClearSharedFolder,
                        onRequestList = extendedFeatureActions.onRequestRemoteFileList,
                        onRequestDownload = extendedFeatureActions.onRequestRemoteFileDownload
                    )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun FeatureHubButton(
    item: FeatureHubItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(min = 132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConnectedEndpointSelector(
    uiState: NearbyClientUiState,
    onSelect: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Aparelhos conectados (${uiState.connectedEndpoints.size})",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Escolha o destino dos controles e dados exibidos abaixo.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            uiState.connectedEndpoints.forEach { endpoint ->
                val selected = endpoint.id == uiState.connectedEndpointId
                if (selected) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(endpoint.id) }
                    ) { Text(endpoint.name.removePrefix("Veyro - ")) }
                } else {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(endpoint.id) }
                    ) { Text(endpoint.name.removePrefix("Veyro - ")) }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ClipboardSyncPanel(
    status: String?,
    onSync: () -> Unit,
    onAddQuickSettingsTile: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sincronização de clipboard", fontWeight = FontWeight.SemiBold)
            Text(
                "Sincroniza apenas texto, sem imagens, arquivos ou conteúdo formatado. " +
                    "No Android recente, volte ao Veyro após copiar para permitir a leitura. " +
                    "O sistema avisa quando um texto recebido é colocado no clipboard.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = onSync) {
                Text("Sincronizar texto agora")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddQuickSettingsTile
            ) {
                Text("Adicionar aos Controles rápidos")
            }
            status?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ContactSyncPanel(
    pendingImports: List<PendingContactImport>,
    lastResult: String?,
    onPickContact: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sincronização de contatos", fontWeight = FontWeight.SemiBold)
            Text(
                "Selecione um contato no Android. Fotos não são enviadas e toda importação exige confirmação local.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = onPickContact) {
                Text("Selecionar e oferecer contato")
            }
            pendingImports.forEach { pending ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(pending.displayName.ifBlank { "Contato sem nome" }, fontWeight = FontWeight.Bold)
                Text("Enviado por ${pending.senderName.removePrefix("Veyro - ")}", style = MaterialTheme.typography.labelSmall)
                pending.phoneNumbers.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                pending.emailAddresses.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onApprove(pending.requestId) }
                    ) { Text("Importar") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onReject(pending.requestId) }
                    ) { Text("Recusar") }
                }
            }
            lastResult?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PresentationPanel(
    remoteState: RemotePresentationState,
    onMediaCommand: (MediaControlRequest) -> Unit,
    onPresentationAction: (PresentationAction, Long) -> Unit
) {
    var running by rememberSaveable { mutableStateOf(false) }
    var blackedOut by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableStateOf(0L) }
    var elapsedMillis by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(running, startedAt) {
        while (running) {
            elapsedMillis = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            onPresentationAction(PresentationAction.PRESENTATION_TIMER_SYNC, elapsedMillis)
            delay(1_000)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Modo de apresentação", fontWeight = FontWeight.SemiBold)
            Text(
                if (running) "Cronômetro: ${formatMediaPosition(elapsedMillis)}" else "Cronômetro parado",
                style = MaterialTheme.typography.titleMedium
            )
            if (remoteState.active) {
                Text(
                    "Remoto: ${formatMediaPosition(remoteState.elapsedMillis)}" +
                        if (remoteState.blackedOut) " • tela preta" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onMediaCommand(MediaControlRequest(MediaEventCategory.CMD_PREV)) }
                ) { Text("Anterior") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onMediaCommand(MediaControlRequest(MediaEventCategory.CMD_NEXT)) }
                ) { Text("Próximo") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (running) {
                            running = false
                            onPresentationAction(PresentationAction.PRESENTATION_STOP, elapsedMillis)
                        } else {
                            elapsedMillis = 0L
                            startedAt = SystemClock.elapsedRealtime()
                            running = true
                            onPresentationAction(PresentationAction.PRESENTATION_START, 0L)
                        }
                    }
                ) { Text(if (running) "Parar" else "Iniciar") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        blackedOut = !blackedOut
                        onPresentationAction(
                            if (blackedOut) PresentationAction.PRESENTATION_BLACKOUT_ON
                            else PresentationAction.PRESENTATION_BLACKOUT_OFF,
                            elapsedMillis
                        )
                    }
                ) { Text(if (blackedOut) "Restaurar tela" else "Tela preta") }
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    elapsedMillis = 0L
                    if (running) startedAt = SystemClock.elapsedRealtime()
                    onPresentationAction(PresentationAction.PRESENTATION_TIMER_SYNC, 0L)
                }
            ) { Text("Zerar cronômetro") }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun DrawingTabletPanel(
    onStylusEvent: (StylusAction, Float, Float, Float, Float, Float, Boolean, Boolean) -> Unit
) {
    var widthPixels by remember { mutableStateOf(1) }
    var heightPixels by remember { mutableStateOf(1) }
    var lastPressure by remember { mutableStateOf(0f) }
    var stylusDetected by remember { mutableStateOf(false) }
    var toolLabel by remember { mutableStateOf("Toque ou use uma caneta") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mesa digitalizadora", fontWeight = FontWeight.SemiBold)
            Text(
                "A área transmite posição, pressão, inclinação e o botão principal do stylus.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .onSizeChanged {
                        widthPixels = it.width.coerceAtLeast(1)
                        heightPixels = it.height.coerceAtLeast(1)
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                    .pointerInteropFilter { event ->
                        val toolType = event.getToolType(0)
                        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                            toolType == MotionEvent.TOOL_TYPE_ERASER
                        stylusDetected = stylusDetected || isStylus
                        toolLabel = when (toolType) {
                            MotionEvent.TOOL_TYPE_ERASER -> "Borracha"
                            MotionEvent.TOOL_TYPE_STYLUS -> "Stylus"
                            MotionEvent.TOOL_TYPE_FINGER -> "Toque"
                            else -> "Ponteiro"
                        }
                        lastPressure = event.pressure.coerceIn(0f, 1f)
                        val tilt = event.getAxisValue(MotionEvent.AXIS_TILT)
                        val orientation = event.orientation
                        val action = when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> StylusAction.STYLUS_DOWN
                            MotionEvent.ACTION_MOVE -> StylusAction.STYLUS_MOVE
                            MotionEvent.ACTION_UP -> StylusAction.STYLUS_UP
                            else -> StylusAction.STYLUS_CANCEL
                        }
                        onStylusEvent(
                            action,
                            (event.x / widthPixels).coerceIn(0f, 1f),
                            (event.y / heightPixels).coerceIn(0f, 1f),
                            lastPressure,
                            (sin(orientation) * tilt).coerceIn(-1f, 1f),
                            (cos(orientation) * tilt).coerceIn(-1f, 1f),
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0,
                            isStylus
                        )
                        true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (stylusDetected || lastPressure > 0f) {
                        "$toolLabel • pressão ${(lastPressure * 100).toInt()}%"
                    } else {
                        toolLabel
                    }
                )
            }
        }
    }
}

@Composable
private fun RemoteFilesPanel(
    sharedFolderName: String?,
    remoteItems: List<RemoteFileItem>,
    remoteParentId: String,
    remoteMessage: String?,
    onChooseSharedFolder: () -> Unit,
    onClearSharedFolder: () -> Unit,
    onRequestList: (String) -> Unit,
    onRequestDownload: (String) -> Unit
) {
    var history by remember { mutableStateOf(emptyList<String>()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Acesso remoto a arquivos", fontWeight = FontWeight.SemiBold)
            Text(
                sharedFolderName?.let { "Pasta local compartilhada: $it" }
                    ?: "Nenhuma pasta local exposta. O restante do armazenamento permanece inacessível.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onChooseSharedFolder
                ) { Text("Escolher pasta") }
                if (sharedFolderName != null) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onClearSharedFolder
                    ) { Text("Parar acesso") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        history = emptyList()
                        onRequestList("")
                    }
                ) { Text("Abrir pasta remota") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = history.isNotEmpty(),
                    onClick = {
                        val parent = history.lastOrNull().orEmpty()
                        history = history.dropLast(1)
                        onRequestList(parent)
                    }
                ) { Text("Voltar") }
            }
            if (remoteItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                remoteItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.isDirectory) {
                                    history = history + remoteParentId
                                    onRequestList(item.documentId)
                                } else {
                                    onRequestDownload(item.documentId)
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (item.isDirectory) "Pasta" else "Arquivo", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!item.isDirectory) Text(formatFileSize(item.sizeBytes), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }
            } else if (remoteMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(remoteMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

@Composable
private fun MediaControlPanel(
    state: RemoteMediaState?,
    onCommand: (MediaControlRequest) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MediaPanelTab.PLAYBACK) }
    val visibleState = state ?: if (BuildConfig.DEBUG) debugMediaPreviewState() else null

    Column(modifier = Modifier.fillMaxSize()) {
        MediaPanelSwitcher(
            selected = selectedTab,
            onSelected = { selectedTab = it }
        )

        if (visibleState == null) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Aguardando o estado de mídia do outro aparelho.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        when (selectedTab) {
            MediaPanelTab.PLAYBACK -> MediaPlaybackTab(visibleState, onCommand)
            MediaPanelTab.DEVICES -> MediaDevicesTab(
                state = visibleState,
                onCommand = onCommand,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp, bottom = 8.dp)
            )
        }
    }
}

private enum class MediaPanelTab { PLAYBACK, DEVICES }

@Composable
private fun MediaPanelSwitcher(
    selected: MediaPanelTab,
    onSelected: (MediaPanelTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MediaPanelSwitcherButton(
                selected = selected == MediaPanelTab.PLAYBACK,
                label = "Reproduzir",
                modifier = Modifier.weight(1f),
                onClick = { onSelected(MediaPanelTab.PLAYBACK) }
            )
            MediaPanelSwitcherButton(
                selected = selected == MediaPanelTab.DEVICES,
                label = "Dispositivos",
                modifier = Modifier.weight(1f),
                onClick = { onSelected(MediaPanelTab.DEVICES) }
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun MediaPanelSwitcherButton(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .heightIn(min = 62.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Box(
            modifier = Modifier
                .height(4.dp)
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
    }
}

@Composable
private fun MediaPlaybackTab(
    state: RemoteMediaState,
    onCommand: (MediaControlRequest) -> Unit
) {
    val artworkBitmap = remember(state.artworkThumbnail.contentHashCode()) {
        state.artworkThumbnail.takeIf(ByteArray::isNotEmpty)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
    val isPlaying = state.playbackStatus == PlaybackState.STATE_PLAYING

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkHeight = if (maxHeight < 620.dp) {
            (maxHeight * 0.38f).coerceIn(170.dp, 230.dp)
        } else {
            (maxHeight * 0.52f).coerceIn(230.dp, 380.dp)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artworkHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (artworkBitmap != null) {
                    Image(
                        bitmap = artworkBitmap.asImageBitmap(),
                        contentDescription = "Capa da mídia",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Mídia Veyro",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = state.trackName.ifBlank { "Nenhuma mídia ativa" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state.artistName.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.artistName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            MediaProgressControl(
                positionMs = state.currentPositionMs,
                durationMs = state.durationMs,
                onSeek = { position ->
                    onCommand(
                        MediaControlRequest(
                            category = MediaEventCategory.CMD_SEEK_TO,
                            requestedPositionMs = position
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaTransportButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "Faixa anterior",
                    onClick = { onCommand(MediaControlRequest(MediaEventCategory.CMD_PREV)) }
                )
                MediaTransportButton(
                    icon = Icons.Default.Replay10,
                    contentDescription = "Voltar 10 segundos",
                    onClick = {
                        onCommand(
                            MediaControlRequest(
                                MediaEventCategory.CMD_SEEK_TO,
                                requestedPositionMs = (state.currentPositionMs - 10_000L).coerceAtLeast(0L)
                            )
                        )
                    }
                )
                FilledIconButton(
                    modifier = Modifier.size(72.dp),
                    onClick = {
                        onCommand(
                            MediaControlRequest(
                                if (isPlaying) MediaEventCategory.CMD_PAUSE
                                else MediaEventCategory.CMD_PLAY
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        modifier = Modifier.size(38.dp)
                    )
                }
                MediaTransportButton(
                    icon = Icons.Default.Forward10,
                    contentDescription = "Avançar 10 segundos",
                    onClick = {
                        onCommand(
                            MediaControlRequest(
                                MediaEventCategory.CMD_SEEK_TO,
                                requestedPositionMs = if (state.durationMs > 0L) {
                                    (state.currentPositionMs + 10_000L).coerceAtMost(state.durationMs)
                                } else {
                                    state.currentPositionMs + 10_000L
                                }
                            )
                        )
                    }
                )
                MediaTransportButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Próxima faixa",
                    onClick = { onCommand(MediaControlRequest(MediaEventCategory.CMD_NEXT)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                MediaVolumeControl(
                    current = state.volumeLevel,
                    maximum = state.volumeMax,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    onCommit = { volume ->
                        onCommand(
                            MediaControlRequest(
                                category = MediaEventCategory.CMD_SET_VOLUME,
                                requestedVolume = volume
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun MediaTransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(27.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MediaProgressControl(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    var draftPosition by remember { mutableStateOf(positionMs.coerceAtLeast(0L).toFloat()) }
    LaunchedEffect(positionMs, safeDuration) {
        draftPosition = positionMs
            .coerceIn(0L, safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
            .toFloat()
    }

    if (safeDuration > 0L) {
        VeyroSlider(
            progress = (draftPosition / safeDuration.toFloat()).coerceIn(0f, 1f),
            onProgressChange = { draftPosition = it * safeDuration },
            onProgressChangeFinished = { onSeek((it * safeDuration).toLong()) }
        )
    } else {
        LinearProgressIndicator(
            progress = { 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatMediaPosition(draftPosition.toLong()), style = MaterialTheme.typography.labelSmall)
        Text(
            if (safeDuration > 0L) formatMediaPosition(safeDuration) else "--:--",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun MediaVolumeControl(
    current: Int,
    maximum: Int,
    modifier: Modifier = Modifier,
    onCommit: (Int) -> Unit
) {
    val safeMaximum = maximum.coerceAtLeast(1)
    var draft by remember(current, safeMaximum) {
        mutableStateOf(current.coerceIn(0, safeMaximum).toFloat())
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.VolumeDown,
            contentDescription = "Diminuir volume",
            modifier = Modifier.size(22.dp)
        )
        VeyroSlider(
            progress = (draft / safeMaximum.toFloat()).coerceIn(0f, 1f),
            onProgressChange = { draft = it * safeMaximum },
            onProgressChangeFinished = { onCommit((it * safeMaximum).roundToInt()) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )
        Icon(
            Icons.Default.VolumeUp,
            contentDescription = "Aumentar volume",
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VeyroSlider(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(1f) }
    val safeProgress = progress.coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val thumbColor = MaterialTheme.colorScheme.primary
    val thumbCenterColor = MaterialTheme.colorScheme.onPrimary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1).toFloat() }
            .pointerInteropFilter { event ->
                val updatedProgress = (event.x / widthPx).coerceIn(0f, 1f)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        onProgressChange(updatedProgress)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        onProgressChange(updatedProgress)
                        onProgressChangeFinished(updatedProgress)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }
    ) {
        val horizontalInset = 9.dp.toPx()
        val trackStart = horizontalInset
        val trackEnd = (size.width - horizontalInset).coerceAtLeast(trackStart)
        val centerY = size.height / 2f
        val thumbX = trackStart + ((trackEnd - trackStart) * safeProgress)
        val trackWidth = 6.dp.toPx()

        drawLine(
            color = inactiveColor,
            start = Offset(trackStart, centerY),
            end = Offset(trackEnd, centerY),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(trackStart, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = thumbColor,
            radius = 9.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = thumbCenterColor,
            radius = 3.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
    }
}

@Composable
private fun MediaDevicesTab(
    state: RemoteMediaState,
    onCommand: (MediaControlRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    val routes = state.audioRoutes.ifEmpty {
        listOf(RemoteAudioOutputRoute(0, "Alto-falante", "SPEAKER", true))
    }
    val streams = state.audioStreams.ifEmpty {
        listOf(
            RemoteAudioStreamVolume(
                streamKind = AudioStreamKind.MEDIA,
                displayName = "Mídia",
                currentVolume = state.volumeLevel,
                maxVolume = state.volumeMax,
                isMuted = state.volumeLevel == 0
            )
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Dispositivos de som", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        routes.forEach { route -> RemoteAudioRouteCard(route) }
        Text(
            "A saída ativa é informada pelo outro aparelho. Os volumes abaixo podem ser alterados individualmente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("Volumes individuais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        streams.forEach { stream ->
            RemoteStreamVolumeCard(
                stream = stream,
                onCommit = { volume ->
                    onCommand(
                        MediaControlRequest(
                            category = MediaEventCategory.CMD_SET_STREAM_VOLUME,
                            requestedVolume = volume,
                            targetStream = stream.streamKind
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun RemoteAudioRouteCard(route: RemoteAudioOutputRoute) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = route.isActive, onClick = null)
            Spacer(modifier = Modifier.size(10.dp))
            Icon(Icons.Default.Headphones, contentDescription = null)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.displayName.ifBlank { "Saída de áudio" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (route.isActive) "Em uso" else route.routeType.lowercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (route.isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RemoteStreamVolumeCard(
    stream: RemoteAudioStreamVolume,
    onCommit: (Int) -> Unit
) {
    val maximum = stream.maxVolume.coerceAtLeast(1)
    var draft by remember(stream.streamKind, stream.currentVolume, maximum) {
        mutableStateOf(stream.currentVolume.coerceIn(0, maximum).toFloat())
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    stream.displayName.ifBlank { stream.streamKind.name.lowercase() },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text("${draft.roundToInt()}/$maximum", style = MaterialTheme.typography.labelMedium)
            }
            VeyroSlider(
                progress = (draft / maximum.toFloat()).coerceIn(0f, 1f),
                onProgressChange = { draft = it * maximum },
                onProgressChangeFinished = { onCommit((it * maximum).roundToInt()) }
            )
        }
    }
}

private fun debugMediaPreviewState(): RemoteMediaState = RemoteMediaState(
    playbackStatus = PlaybackState.STATE_PAUSED,
    trackName = "Mídia do aparelho conectado",
    artistName = "Pré-visualização Veyro",
    currentPositionMs = 94_000L,
    durationMs = 225_000L,
    artworkThumbnail = ByteArray(0),
    artworkMimeType = "",
    volumeLevel = 9,
    volumeMax = 15,
    audioStreams = listOf(
        RemoteAudioStreamVolume(AudioStreamKind.MEDIA, "Mídia", 9, 15, false),
        RemoteAudioStreamVolume(AudioStreamKind.RING, "Toque", 6, 15, false),
        RemoteAudioStreamVolume(AudioStreamKind.ALARM, "Alarme", 8, 15, false),
        RemoteAudioStreamVolume(AudioStreamKind.NOTIFICATION, "Notificações", 5, 15, false),
        RemoteAudioStreamVolume(AudioStreamKind.VOICE_CALL, "Chamadas", 4, 7, false),
        RemoteAudioStreamVolume(AudioStreamKind.SYSTEM, "Sistema", 7, 15, false)
    ),
    audioRoutes = listOf(
        RemoteAudioOutputRoute(1, "Alto-falante", "SPEAKER", true),
        RemoteAudioOutputRoute(2, "Áudio Bluetooth", "BLUETOOTH", false)
    )
)

private fun playbackStatusLabel(status: Int): String = when (status) {
    PlaybackState.STATE_PLAYING -> "Reproduzindo"
    PlaybackState.STATE_PAUSED -> "Pausado"
    PlaybackState.STATE_BUFFERING -> "Carregando"
    PlaybackState.STATE_STOPPED -> "Parado"
    PlaybackState.STATE_CONNECTING -> "Conectando"
    PlaybackState.STATE_ERROR -> "Erro de reprodução"
    else -> "Estado $status"
}

private fun formatMediaPosition(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun FindMyDevicePanel(
    notificationPolicyGranted: Boolean,
    onOpenNotificationPolicySettings: () -> Unit,
    onStartRemoteAlarm: () -> Unit,
    onStopRemoteAlarm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Encontrar meu dispositivo", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Faz o outro aparelho tocar no volume de alarme.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (notificationPolicyGranted) {
                    "Acesso a modos concedido neste aparelho."
                } else {
                    "Conceda acesso a modos para este aparelho tocar mesmo em Não Perturbe."
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (!notificationPolicyGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenNotificationPolicySettings
                ) {
                    Text("Configurar acesso a modos")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStartRemoteAlarm
                ) {
                    Text("Fazer tocar")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onStopRemoteAlarm
                ) {
                    Text("Parar")
                }
            }
        }
    }
}

@Composable
private fun NotificationSyncPanel(
    notificationListenerGranted: Boolean,
    notifications: List<RemoteNotification>,
    onOpenNotificationListenerSettings: () -> Unit,
    onDismissNotification: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notificações sincronizadas", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (notificationListenerGranted) {
                    "Acesso local ativo. Conteúdo protegido pelo Android permanece oculto."
                } else {
                    "Ative o acesso local para compartilhar e descartar notificações."
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (!notificationListenerGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenNotificationListenerSettings
                ) {
                    Text("Configurar acesso às notificações")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (notifications.isEmpty()) {
                Text(
                    text = "Nenhuma notificação recebida do outro aparelho.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications.reversed(), key = { it.notificationKey }) { item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(item.appName, fontWeight = FontWeight.SemiBold)
                            if (item.title.isNotBlank()) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (item.textBody.isNotBlank()) {
                                Text(item.textBody, style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.isClearable) {
                                TextButton(
                                    onClick = {
                                        onDismissNotification(item.notificationKey)
                                    }
                                ) {
                                    Text("Descartar no outro aparelho")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryStatusCard(status: RemoteBatteryStatus?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bateria do outro aparelho", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            if (status == null) {
                Text(
                    text = "Aguardando a primeira atualização segura...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = "${status.chargePercentage}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (status.isPluggedIn) {
                        "Conectado à energia • ${status.powerSourceLabel}"
                    } else {
                        "Usando a bateria"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ConnectivityStatusCard(
    status: RemoteConnectivityStatus?,
    ping: RemotePingStatus?,
    showConnectivity: Boolean,
    showPing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Conectividade do outro aparelho", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            if (showConnectivity) {
                if (status == null) {
                    Text(
                        "Aguardando o primeiro relatório de rede...",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        status.transportLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val internetLabel = if (status.hasInternet) {
                        "Internet disponível"
                    } else {
                        "Sem acesso à internet"
                    }
                    val meteredLabel = if (status.isMetered) "Rede limitada" else "Rede não limitada"
                    Text("$internetLabel • $meteredLabel")
                    status.signalStrengthDbm?.let { strength ->
                        Text("Sinal informado: $strength dBm", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (showConnectivity && showPing) Spacer(modifier = Modifier.height(12.dp))
            if (showPing) {
                Text("Latência do canal Nearby", style = MaterialTheme.typography.labelLarge)
                Text(
                    ping?.let { "${it.roundTripMillis} ms" } ?: "Aguardando resposta do ping...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Medição de ida e volta pelo canal P2P.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun OperationCard(
    title: String,
    message: String,
    stateName: String,
    onStopSession: () -> Unit,
    stopButtonLabel: String = "Parar"
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Estado: $stateName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStopSession
            ) {
                Text(stopButtonLabel)
            }
        }
    }
}

@Composable
private fun SafeCustomCommandsPanel(
    results: List<RemoteCustomCommandResult>,
    onCommand: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ações remotas seguras", fontWeight = FontWeight.SemiBold)
            Text(
                "Somente ações nativas desta lista são aceitas; comandos shell são sempre bloqueados.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onCommand(SafeCustomCommandExecutor.ACTION_VOLUME_UP) }
                ) { Text("Volume +") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onCommand(SafeCustomCommandExecutor.ACTION_VOLUME_DOWN) }
                ) { Text("Volume −") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onCommand(SafeCustomCommandExecutor.ACTION_TORCH_ON) }
                ) { Text("Ligar lanterna") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onCommand(SafeCustomCommandExecutor.ACTION_TORCH_OFF) }
                ) { Text("Desligar") }
            }
            results.lastOrNull()?.let { result ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Último resultado: ${result.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.succeeded) Color(0xFF145C2E) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ShareUrlPanel(
    sharedUrls: List<RemoteSharedUrl>,
    onShareUrl: (String) -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Compartilhar link", fontWeight = FontWeight.SemiBold)
            Text(
                "Somente HTTP/HTTPS. O link abre apenas após um toque no aparelho de destino.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = url,
                onValueChange = { url = it.take(2_048) },
                label = { Text("https://exemplo.com") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = url.isNotBlank(),
                onClick = {
                    onShareUrl(url)
                    url = ""
                }
            ) { Text("Enviar link") }
            sharedUrls.lastOrNull()?.let { item ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.message, style = MaterialTheme.typography.bodySmall)
                if (item.url.isNotBlank()) {
                    Text(item.url, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RemoteInputPanel(
    onRemoteInput: (RemoteInputCommand, Float, Float, String) -> Unit
) {
    var keyboardText by rememberSaveable { mutableStateOf("") }
    val deltaChannel = remember { Channel<Offset>(Channel.UNLIMITED) }
    LaunchedEffect(deltaChannel) {
        while (isActive) {
            var total = deltaChannel.receive()
            delay(16)
            while (true) {
                val next = deltaChannel.tryReceive().getOrNull() ?: break
                total += next
            }
            onRemoteInput(RemoteInputCommand.MOUSE_DELTA, total.x, total.y, "")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Controle remoto", fontWeight = FontWeight.SemiBold)
            Text(
                "Arraste para mover o cursor virtual; toque ou toque duas vezes para clicar.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        MaterialTheme.shapes.medium
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                onRemoteInput(RemoteInputCommand.SINGLE_TAP, 0f, 0f, "")
                            },
                            onDoubleTap = {
                                onRemoteInput(RemoteInputCommand.DOUBLE_TAP, 0f, 0f, "")
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            deltaChannel.trySend(dragAmount)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Touchpad Veyro", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRemoteInput(RemoteInputCommand.SCROLL_GESTURE, 0f, 420f, "")
                    }
                ) { Text("Rolar ↓") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRemoteInput(RemoteInputCommand.SCROLL_GESTURE, 0f, -420f, "")
                    }
                ) { Text("Rolar ↑") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = keyboardText,
                onValueChange = { keyboardText = it.take(64) },
                label = { Text("Texto para o campo focado") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = keyboardText.isNotBlank(),
                onClick = {
                    onRemoteInput(RemoteInputCommand.KEYBOARD_INPUT, 0f, 0f, keyboardText)
                    keyboardText = ""
                }
            ) { Text("Digitar no outro aparelho") }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Voltar" to "{BACK}", "Início" to "{HOME}", "Recentes" to "{RECENTS}")
                    .forEach { (label, token) ->
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onRemoteInput(RemoteInputCommand.KEYBOARD_INPUT, 0f, 0f, token)
                            }
                        ) { Text(label) }
                    }
            }
        }
    }
}

@Composable
private fun CommandPanel(
    receivedCommands: List<ReceivedCommand>,
    onSendCommand: (String) -> Unit
) {
    var command by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Enviar comando", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = command,
                onValueChange = { command = it },
                label = { Text("Mensagem") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = command.isNotBlank(),
                onClick = {
                    onSendCommand(command)
                    command = ""
                }
            ) {
                Text("Enviar comando")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Comandos recebidos", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            if (receivedCommands.isEmpty()) {
                Text(
                    text = "Nenhum comando recebido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(receivedCommands) { commandItem ->
                        Text(
                            text = "${commandItem.senderName}: ${commandItem.text}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelephonyPanel(
    events: List<RemoteTelecommunicationEvent>,
    onSendSmsTransmitOrder: (String, String) -> Unit
) {
    var destination by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Telefonia e SMS", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "O envio remoto nunca é automático: o outro aparelho recebe uma notificação para confirmar ou recusar.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = destination,
                onValueChange = { destination = it.take(64) },
                label = { Text("Número de destino") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = message,
                onValueChange = { message = it.take(8_000) },
                label = { Text("Mensagem SMS") },
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = destination.isNotBlank() && message.isNotBlank(),
                onClick = {
                    onSendSmsTransmitOrder(destination, message)
                    destination = ""
                    message = ""
                }
            ) {
                Text("Solicitar envio no outro aparelho")
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Eventos do outro aparelho", fontWeight = FontWeight.SemiBold)
            if (events.isEmpty()) {
                Text(
                    text = "Nenhuma chamada ou SMS sincronizado nesta conexão.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(events.reversed()) { event ->
                        val title = when (event.type) {
                            TelecommunicationType.INBOUND_CALL -> "Chamada recebida"
                            TelecommunicationType.MISSED_CALL -> "Chamada perdida"
                            TelecommunicationType.SMS_RECEIVED_EVENT -> "SMS recebido"
                            else -> "Evento de telefonia"
                        }
                        Column {
                            Text(title, fontWeight = FontWeight.Medium)
                            Text(
                                event.identityLabel.ifBlank { "Número desconhecido" },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (event.textPayload.isNotBlank()) {
                                Text(event.textPayload, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RawFilePanel(
    transfers: List<RawFileTransfer>,
    onPickFile: () -> Unit,
    onApproveIncomingFile: (Long) -> Unit,
    onRejectIncomingFile: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Transferência de arquivo", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nome, tamanho e tipo são enviados antes do Payload.FILE.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickFile
            ) {
                Text("Selecionar arquivo")
            }

            if (transfers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                transfers.takeLast(3).forEach { transfer ->
                    val direction = when (transfer.direction) {
                        RawFileDirection.SEND -> "Enviado"
                        RawFileDirection.RECEIVE -> "Recebido"
                    }
                    val status = when (transfer.status) {
                        RawFileStatus.IN_PROGRESS -> "em andamento"
                        RawFileStatus.COMPLETED -> "concluído"
                        RawFileStatus.AWAITING_APPROVAL -> "aguardando sua aprovação"
                        RawFileStatus.SAVING -> "salvando"
                        RawFileStatus.SAVED -> "salvo em Downloads/Veyro"
                        RawFileStatus.FAILED -> "falhou"
                        RawFileStatus.CANCELED -> "cancelado"
                    }
                    Text(
                        text = transfer.fileName ?: "Aguardando metadados...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$direction • ${formatBytes(transfer.totalBytes)} • $status",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { transfer.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${transfer.progressPercent}% • ${formatBytes(transfer.bytesTransferred)} transferidos",
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (transfer.savedUri != null) {
                        Text(
                            text = "Disponível em Downloads/Veyro.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF145C2E),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (transfer.temporaryUri != null) {
                        Text(
                            text = "Arquivo temporário; aguardando salvamento definitivo.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (transfer.status == RawFileStatus.AWAITING_APPROVAL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { onApproveIncomingFile(transfer.payloadId) }
                            ) {
                                Text("Salvar")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onRejectIncomingFile(transfer.payloadId) }
                            ) {
                                Text("Recusar")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "%.1f KB".format(bytes / 1_024.0)
    bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.1f GB".format(bytes / 1_073_741_824.0)
}

@Composable
private fun EndpointList(
    endpoints: List<DiscoveredEndpoint>,
    onRequestConnection: (String) -> Unit
) {
    if (endpoints.isEmpty()) {
        Text(
            text = "Nenhum aparelho encontrado ainda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(endpoints, key = { it.id }) { endpoint ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onRequestConnection(endpoint.id) }
            ) {
                Text(endpoint.name)
            }
        }
    }
}

@Composable
private fun AuthenticationDialog(
    pendingConnection: PendingConnection,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Confirmar PIN de segurança") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Compare este código com ${pendingConnection.endpointName}:",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = pendingConnection.authenticationDigits,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Aceite somente se os dois aparelhos mostrarem exatamente o mesmo PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("PIN confere")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Recusar")
            }
        }
    )
}

@Composable
private fun NearbyStatusCard(uiState: NearbyClientUiState) {
    val isReady = uiState.status == NearbyClientStatus.READY
    val containerColor = if (isReady) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isReady) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (isReady) "Cliente Nearby pronto" else "Falha ao iniciar Nearby",
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${uiState.serviceId} • ${uiState.strategyName}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PermissionStatusCard(permissionsGranted: Boolean) {
    val containerColor = if (permissionsGranted) Color(0xFFE2F5E8)
    else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (permissionsGranted) Color(0xFF145C2E)
    else MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (permissionsGranted) "✓" else "!",
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = if (permissionsGranted) "Dispositivo pronto" else "Permissões necessárias",
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (permissionsGranted) {
                    "Rádios locais autorizados."
                } else {
                    "Autorize dispositivos próximos para continuar."
                    },
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PermissionExplanationDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encontrar dispositivos próximos") },
        text = {
            Text(
                "A Veyro usa Bluetooth e Wi-Fi local para localizar outros aparelhos " +
                    "e transferir dados diretamente, sem enviar sua localização. " +
                    "Os demais acessos são opcionais e serão explicados somente ao ativar a função correspondente."
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("Continuar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Agora não") }
        }
    )
}

@Composable
private fun OptionalFeatureAccessDialog(
    access: OptionalFeatureAccess,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, detail) = when (access) {
        OptionalFeatureAccess.NOTIFICATION_LISTENER ->
            "Acesso às notificações necessário" to
                "Este recurso precisa do acesso especial às notificações para ler estados locais e permitir a sincronização. O Android mostrará a tela onde você pode autorizar o Veyro."
        OptionalFeatureAccess.NOTIFICATION_POLICY ->
            "Acesso a Modos necessário" to
                "Encontrar aparelho precisa controlar temporariamente o áudio mesmo quando Não Perturbe estiver ativo. O Android mostrará a tela de acesso a Modos."
        OptionalFeatureAccess.TELEPHONY ->
            "Permissões de telefone e SMS necessárias" to
                "Chamadas e SMS precisa acessar o estado do telefone, contatos, mensagens e notificações. Os dados só circulam durante uma conexão e todo SMS remoto ainda exige confirmação local."
        OptionalFeatureAccess.CAMERA ->
            "Permissão de câmera necessária" to
                "Ações remotas seguras usa a permissão de câmera somente para ligar ou desligar a lanterna. O Veyro não captura imagens."
        OptionalFeatureAccess.ACCESSIBILITY ->
            "Acesso de controle remoto necessário" to
                "Mouse e teclado remotos precisa do serviço de Acessibilidade para executar gestos e inserir texto. O Veyro não transmite o conteúdo da tela."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(detail) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("Conceder acesso") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Manter desativado") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun VeyroScreenPreview() {
    MaterialTheme {
        VeyroScreen(
            permissionsGranted = true,
            nearbyUiState = NearbyClientUiState(status = NearbyClientStatus.READY),
            onRequestPermissions = {},
            onStartAdvertising = {},
            onStartDiscovery = {},
            onRequestConnection = {},
            onSendCommand = {},
            onStartRemoteFindAlarm = {},
            onStopRemoteFindAlarm = {},
            notificationListenerGranted = false,
            notificationPolicyGranted = false,
            telephonyPermissionsGranted = false,
            callScreeningRoleGranted = false,
            onRequestTelephonyPermissions = {},
            onRequestCallScreeningRole = {},
            cameraPermissionGranted = false,
            remoteInputAccessibilityGranted = false,
            onRequestCameraPermission = {},
            onOpenAccessibilitySettings = {},
            onOpenNotificationListenerSettings = {},
            onOpenNotificationPolicySettings = {},
            onDismissRemoteNotification = {},
            onMediaControlCommand = {},
            onSendSmsTransmitOrder = { _, _ -> },
            onSendSafeCustomCommand = {},
            onShareUrl = {},
            onRemoteInput = { _, _, _, _ -> },
            onPickFile = {},
            onApproveIncomingFile = {},
            onRejectIncomingFile = {},
            onUpdateTrustedDeviceRules = {},
            onRemoveTrustedDevice = {},
            onSetEnergyMode = {},
            onSetAppLanguage = {},
            onSetFeatureSettings = {},
            extendedFeatureActions = ExtendedFeatureActions(
                onSelectConnectedEndpoint = {},
                onPickContact = {},
                onApproveContact = {},
                onRejectContact = {},
                onPresentationAction = { _, _ -> },
                onDismissRemoteBlackout = {},
                onStylusEvent = { _, _, _, _, _, _, _, _ -> },
                onChooseSharedFolder = {},
                onClearSharedFolder = {},
                onRequestRemoteFileList = {},
                onRequestRemoteFileDownload = {},
                onSyncClipboard = {},
                onRequestClipboardTile = {}
            ),
            onStopSession = {}
        )
    }
}
