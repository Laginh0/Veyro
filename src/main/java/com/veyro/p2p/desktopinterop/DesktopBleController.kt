package com.veyro.p2p.desktopinterop

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.google.protobuf.ByteString
import com.veyro.p2p.protocol.BleControlPacket
import com.veyro.p2p.protocol.FastChannelAnswer
import com.veyro.p2p.protocol.FastChannelOffer
import com.veyro.p2p.protocol.PairingConfirmation
import com.veyro.p2p.protocol.ReconnectProof
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.UUID

internal interface DesktopBleListener {
    fun onDesktopPeersChanged(peers: List<DiscoveredDesktopPeer>)
    fun onDesktopPairingPin(verification: DesktopPairingVerification)
    fun onDesktopTrusted(peer: DesktopTrustedPeer)
    fun onDesktopFastChannelOffer(offer: FastChannelOffer)
    fun onDesktopFastChannelAnswer(answer: FastChannelAnswer)
    fun onDesktopBleStatus(message: String, error: Throwable? = null)
}

@SuppressLint("MissingPermission")
internal class DesktopBleController(
    context: Context,
    private val identity: DesktopIdentity,
    private val trustStore: DesktopTrustStore,
    private val listener: DesktopBleListener
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager.adapter
    private val serviceUuid = ParcelUuid(DesktopInteropProtocol.serviceUuid)
    private val secureRandom = SecureRandom()
    private val ephemeralId = ByteArray(6).also(secureRandom::nextBytes)
    private val discovered = LinkedHashMap<String, DiscoveredDesktopPeer>()
    private val clientWriteQueue = ArrayDeque<ByteArray>()
    private val serverNotifyQueue = ArrayDeque<ByteArray>()
    private var gattServer: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null
    private var serverPeer: BluetoothDevice? = null
    private var clientGatt: BluetoothGatt? = null
    private var clientCharacteristic: BluetoothGattCharacteristic? = null
    private var clientWriteInProgress = false
    private var serverNotifyInProgress = false
    private var started = false
    private var pairingSession: DesktopPairingSession? = null
    private var activeSender: ((ByteArray) -> Unit)? = null
    private var reconnectChallenge: ByteArray? = null
    private var activeTrustedPeer: DesktopTrustedPeer? = null
    private var reconnectOnly = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val advertisement = DesktopInteropProtocol.decodeAdvertisement(
                result.scanRecord?.getServiceData(serviceUuid)
            ) ?: return
            if (advertisement.protocolMajor != DesktopInteropProtocol.protocolMajor ||
                advertisement.ephemeralId.equals(ephemeralId.toHex(), ignoreCase = true)
            ) return
            val address = result.device.address ?: return
            val peer = DiscoveredDesktopPeer(
                endpointId = DesktopInteropProtocol.desktopEndpointPrefix + address,
                address = address,
                ephemeralId = advertisement.ephemeralId,
                capabilities = advertisement.capabilities,
                rssi = result.rssi,
                observedAtMillis = System.currentTimeMillis()
            )
            synchronized(discovered) {
                discovered[address] = peer
                discovered.entries.removeAll { System.currentTimeMillis() - it.value.observedAtMillis > PEER_EXPIRY_MILLIS }
                listener.onDesktopPeersChanged(discovered.values.sortedByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onDesktopBleStatus("A varredura BLE para Desktop falhou ($errorCode).")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            listener.onDesktopBleStatus("O anúncio BLE compatível com Desktop falhou ($errorCode).")
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            listener.onDesktopBleStatus("Veyro visível para computadores próximos.")
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) startAdvertising() else {
                listener.onDesktopBleStatus("Não foi possível publicar o serviço GATT ($status).")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val previousPeer = serverPeer
                if (previousPeer != null && previousPeer.address != device.address) {
                    runCatching { gattServer?.cancelConnection(previousPeer) }
                }
                serverPeer = device
                listener.onDesktopBleStatus("Canal BLE recebido de um computador.")
            } else if (serverPeer?.address == device.address) {
                serverPeer = null
                synchronized(serverNotifyQueue) {
                    serverNotifyQueue.clear()
                    serverNotifyInProgress = false
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == DesktopInteropProtocol.clientConfigurationUuid) serverPeer = device
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        @Deprecated("Deprecated in Android")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid != DesktopInteropProtocol.controlCharacteristicUuid ||
                value.isEmpty() || value.size > DesktopInteropProtocol.maximumBlePacketSize
            ) {
                if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                return
            }
            serverPeer = device
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            processPacket(value) { sendServerPacket(it) }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            synchronized(serverNotifyQueue) {
                serverNotifyInProgress = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.onDesktopBleStatus("A resposta GATT não foi entregue ($status).")
                }
                pumpServerNotifications()
            }
        }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onDesktopBleStatus("BLE conectado; localizando o canal Veyro…")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener.onDesktopBleStatus("Canal BLE com o Desktop encerrado.")
                clientCharacteristic = null
                clientGatt = null
                reconnectOnly = false
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onDesktopBleStatus("O serviço GATT do Desktop não respondeu ($status).")
                return
            }
            val characteristic = gatt
                .getService(DesktopInteropProtocol.serviceUuid)
                ?.getCharacteristic(DesktopInteropProtocol.controlCharacteristicUuid)
            if (characteristic == null) {
                listener.onDesktopBleStatus("O computador não expôs o canal GATT Veyro.")
                return
            }
            clientCharacteristic = characteristic
            gatt.requestMtu(517)
            enableClientNotifications(gatt, characteristic)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == DesktopInteropProtocol.clientConfigurationUuid && status == BluetoothGatt.GATT_SUCCESS) {
                beginOutgoingConnection { sendClientPacket(it) }
            }
        }

        @Deprecated("Deprecated in Android")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            processPacket(characteristic.value ?: return) { sendClientPacket(it) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            processPacket(value) { sendClientPacket(it) }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            synchronized(clientWriteQueue) {
                clientWriteInProgress = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.onDesktopBleStatus("O pacote BLE não foi entregue ($status).")
                }
                pumpClientWrites()
            }
        }
    }

    fun start() {
        if (started) return
        requireRuntimeSupport()
        val bluetoothAdapter = requireNotNull(adapter) { "Bluetooth indisponível neste aparelho" }
        check(bluetoothAdapter.isEnabled) { "Ative o Bluetooth para encontrar o Veyro Desktop" }
        started = true
        try {
            openGattServer()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bluetoothAdapter.bluetoothLeScanner?.startScan(emptyList(), settings, scanCallback)
                ?: error("Este aparelho não oferece varredura Bluetooth LE")
            listener.onDesktopBleStatus("Procurando computadores Veyro por BLE.")
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun connect(peer: DiscoveredDesktopPeer, reconnectOnly: Boolean = false) {
        requireRuntimeSupport()
        val device = requireNotNull(adapter).getRemoteDevice(peer.address)
        this.reconnectOnly = reconnectOnly
        activeTrustedPeer = null
        resetPairing(clearSender = true)
        clientGatt?.close()
        clientGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, clientCallback)
        }
        listener.onDesktopBleStatus(
            if (reconnectOnly) "Restaurando a confiança com o Veyro Desktop…"
            else "Conectando ao Veyro Desktop por BLE…"
        )
    }

    fun confirmPin(accepted: Boolean) {
        val session = checkNotNull(pairingSession) { "Não há pareamento Desktop aguardando confirmação" }
        val sender = checkNotNull(activeSender)
        sender(encodeConfirmation(session.createConfirmation(accepted)))
        if (!accepted) {
            resetPairing()
            listener.onDesktopBleStatus("Pareamento com o Desktop recusado.")
        } else {
            completePairingIfReady()
        }
    }

    fun sendFastChannelAnswer(sessionId: String, accepted: Boolean, reason: String = "") {
        val packet = BleControlPacket.newBuilder()
            .setFastChannelAnswer(
                FastChannelAnswer.newBuilder()
                    .setSessionId(sessionId)
                    .setAccepted(accepted)
                    .setReason(reason)
            )
            .build()
        checkNotNull(activeSender) { "O canal BLE não está ativo" }(packet.toByteArray())
    }

    fun activePeer(): DesktopTrustedPeer? = activeTrustedPeer

    fun revokeByDisplayName(displayName: String): Boolean {
        val revoked = trustStore.revokeByDisplayName(displayName)
        if (activeTrustedPeer?.displayName == displayName) activeTrustedPeer = null
        return revoked
    }

    private fun beginOutgoingConnection(sender: (ByteArray) -> Unit) {
        resetPairing(clearSender = false)
        activeSender = sender
        if (reconnectOnly) {
            sendReconnectChallenge(sender)
            listener.onDesktopBleStatus("Prova de confiança solicitada ao Desktop.")
        } else {
            pairingSession = DesktopPairingSession(
                identity,
                DesktopInteropProtocol.capabilityMask,
                UUID.randomUUID().toString().replace("-", "")
            )
            sender(encodeHello(pairingSession!!.localHello))
            listener.onDesktopBleStatus("Solicitação de pareamento enviada ao Desktop; confirme o PIN.")
        }
    }

    private fun processPacket(bytes: ByteArray, sender: (ByteArray) -> Unit) {
        runCatching {
            require(bytes.isNotEmpty() && bytes.size <= DesktopInteropProtocol.maximumBlePacketSize)
            activeSender = sender
            val packet = BleControlPacket.parseFrom(bytes)
            when (packet.bodyCase) {
                BleControlPacket.BodyCase.PAIRING_HELLO -> processHello(packet.pairingHello, sender)
                BleControlPacket.BodyCase.PAIRING_CONFIRMATION -> processConfirmation(packet.pairingConfirmation)
                BleControlPacket.BodyCase.RECONNECT_CHALLENGE -> {
                    val challenge = packet.reconnectChallenge.challenge.toByteArray()
                    require(challenge.size == 32)
                    val proof = ReconnectProof.newBuilder()
                        .setDeviceId(identity.deviceId)
                        .setChallenge(ByteString.copyFrom(challenge))
                        .setSignature(
                            ByteString.copyFrom(
                                DesktopInteropProtocol.signP1363(
                                    identity.keyPair.private,
                                    DesktopInteropProtocol.reconnectPayload(identity.deviceId, challenge)
                                )
                            )
                        )
                        .build()
                    sender(BleControlPacket.newBuilder().setReconnectProof(proof).build().toByteArray())
                    if (reconnectChallenge == null && trustStore.allActive().isNotEmpty()) {
                        sendReconnectChallenge(sender)
                    }
                }
                BleControlPacket.BodyCase.RECONNECT_PROOF -> processReconnectProof(packet.reconnectProof)
                BleControlPacket.BodyCase.FAST_CHANNEL_OFFER -> listener.onDesktopFastChannelOffer(packet.fastChannelOffer)
                BleControlPacket.BodyCase.FAST_CHANNEL_ANSWER -> listener.onDesktopFastChannelAnswer(packet.fastChannelAnswer)
                BleControlPacket.BodyCase.BODY_NOT_SET, null -> error("Pacote BLE sem conteúdo")
            }
        }.onFailure { listener.onDesktopBleStatus("Mensagem BLE do Desktop rejeitada.", it) }
    }

    private fun processHello(remote: com.veyro.p2p.protocol.PairingHello, sender: (ByteArray) -> Unit) {
        val shouldSendHello = pairingSession?.localHello?.pairingId != remote.pairingId
        if (shouldSendHello) {
            resetPairing(clearSender = false)
            activeSender = sender
            pairingSession = DesktopPairingSession(
                identity,
                DesktopInteropProtocol.capabilityMask,
                remote.pairingId
            )
        }
        val verification = checkNotNull(pairingSession).acceptRemoteHello(remote)
        if (shouldSendHello) sender(encodeHello(checkNotNull(pairingSession).localHello))
        listener.onDesktopPairingPin(verification)
        listener.onDesktopBleStatus("Confirme o mesmo PIN no Android e no Desktop.")
    }

    private fun processConfirmation(confirmation: PairingConfirmation) {
        val session = checkNotNull(pairingSession) { "Confirmação sem sessão ativa" }
        session.acceptRemoteConfirmation(confirmation)
        if (!confirmation.accepted) {
            listener.onDesktopBleStatus("O Desktop recusou o pareamento.")
            resetPairing()
        } else {
            completePairingIfReady()
        }
    }

    private fun processReconnectProof(proof: ReconnectProof) {
        val challenge = reconnectChallenge ?: error("Prova de reconexão sem desafio ativo")
        require(MessageDigest.isEqual(challenge, proof.challenge.toByteArray()))
        val trusted = trustStore.active(proof.deviceId) ?: run {
            listener.onDesktopBleStatus("A reconexão foi recusada: faça um novo pareamento com PIN.")
            if (reconnectOnly) clientGatt?.disconnect()
            return
        }
        require(
            DesktopInteropProtocol.verifyP1363(
                DesktopInteropProtocol.publicKey(trusted.identityPublicKeySpki),
                DesktopInteropProtocol.reconnectPayload(trusted.deviceId, challenge),
                proof.signature.toByteArray()
            )
        ) { "Prova de reconexão inválida" }
        trustStore.markSeen(trusted.deviceId)
        activeTrustedPeer = trusted.copy(lastSeenAtMillis = System.currentTimeMillis())
        reconnectOnly = false
        reconnectChallenge?.fill(0)
        reconnectChallenge = null
        listener.onDesktopTrusted(activeTrustedPeer!!)
        listener.onDesktopBleStatus("${trusted.displayName} autenticado novamente.")
    }

    private fun completePairingIfReady() {
        val session = pairingSession ?: return
        if (!session.isMutuallyConfirmed) return
        val trusted = session.trustedPeer()
        trustStore.trust(trusted)
        activeTrustedPeer = trusted
        listener.onDesktopTrusted(trusted)
        listener.onDesktopBleStatus("${trusted.displayName} adicionado ao Trust Hub.")
        resetPairing(clearSender = false)
    }

    private fun encodeHello(hello: com.veyro.p2p.protocol.PairingHello): ByteArray =
        BleControlPacket.newBuilder().setPairingHello(hello).build().toByteArray()

    private fun encodeConfirmation(confirmation: PairingConfirmation): ByteArray =
        BleControlPacket.newBuilder().setPairingConfirmation(confirmation).build().toByteArray()

    private fun encodeReconnectChallenge(challenge: ByteArray): ByteArray =
        BleControlPacket.newBuilder()
            .setReconnectChallenge(
                com.veyro.p2p.protocol.ReconnectChallenge.newBuilder()
                    .setRequestingDeviceId(identity.deviceId)
                    .setChallenge(ByteString.copyFrom(challenge))
            )
            .build()
            .toByteArray()

    private fun sendReconnectChallenge(sender: (ByteArray) -> Unit) {
        reconnectChallenge?.fill(0)
        reconnectChallenge = ByteArray(32).also(secureRandom::nextBytes)
        sender(encodeReconnectChallenge(checkNotNull(reconnectChallenge)))
    }

    private fun openGattServer() {
        val server = bluetoothManager.openGattServer(appContext, serverCallback)
            ?: error("Não foi possível abrir o servidor GATT")
        val service = BluetoothGattService(
            DesktopInteropProtocol.serviceUuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val characteristic = BluetoothGattCharacteristic(
            DesktopInteropProtocol.controlCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        characteristic.addDescriptor(
            BluetoothGattDescriptor(
                DesktopInteropProtocol.clientConfigurationUuid,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        service.addCharacteristic(characteristic)
        gattServer = server
        serverCharacteristic = characteristic
        check(server.addService(service)) { "Não foi possível adicionar o serviço GATT Veyro" }
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(serviceUuid, DesktopInteropProtocol.encodeAdvertisement(ephemeralId))
            .build()
        adapter?.bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            ?: error("Este aparelho não permite anúncios Bluetooth LE")
    }

    private fun enableClientNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        check(gatt.setCharacteristicNotification(characteristic, true))
        val descriptor = characteristic.getDescriptor(DesktopInteropProtocol.clientConfigurationUuid)
            ?: error("O canal GATT não oferece notificações")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            check(gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            check(gatt.writeDescriptor(descriptor))
        }
    }

    private fun sendClientPacket(packet: ByteArray) {
        require(packet.size in 1..DesktopInteropProtocol.maximumBlePacketSize)
        synchronized(clientWriteQueue) {
            clientWriteQueue.add(packet)
            pumpClientWrites()
        }
    }

    private fun pumpClientWrites() {
        if (clientWriteInProgress || clientWriteQueue.isEmpty()) return
        val gatt = clientGatt ?: return
        val characteristic = clientCharacteristic ?: return
        val packet = clientWriteQueue.removeFirst()
        clientWriteInProgress = true
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = packet
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            clientWriteInProgress = false
            listener.onDesktopBleStatus("O Android não iniciou a escrita GATT.")
        }
    }

    private fun sendServerPacket(packet: ByteArray) {
        require(packet.size in 1..DesktopInteropProtocol.maximumBlePacketSize)
        synchronized(serverNotifyQueue) {
            serverNotifyQueue.add(packet)
            pumpServerNotifications()
        }
    }

    private fun pumpServerNotifications() {
        if (serverNotifyInProgress || serverNotifyQueue.isEmpty()) return
        val server = gattServer ?: return
        val device = serverPeer ?: return
        val characteristic = serverCharacteristic ?: return
        val packet = serverNotifyQueue.removeFirst()
        serverNotifyInProgress = true
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, false, packet) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = packet
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
        if (!started) {
            serverNotifyInProgress = false
            listener.onDesktopBleStatus("O Android não iniciou a notificação GATT.")
        }
    }

    private fun requireRuntimeSupport() {
        check(DesktopIdentityStore.isSupported()) { "Veyro Desktop requer Android 6 ou superior" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
            check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun resetPairing(clearSender: Boolean = true) {
        pairingSession?.close()
        pairingSession = null
        reconnectChallenge?.fill(0)
        reconnectChallenge = null
        if (clearSender) activeSender = null
    }

    override fun close() {
        if (!started) return
        started = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        clientGatt?.disconnect()
        clientGatt?.close()
        clientGatt = null
        gattServer?.close()
        gattServer = null
        discovered.clear()
        resetPairing()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val PEER_EXPIRY_MILLIS = 20_000L
    }
}
