package com.veyro.p2p.nearby

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task
import com.veyro.p2p.BuildConfig
import java.security.MessageDigest

interface NearbyConnectionsListener {
    fun onEndpointFound(endpointId: String, endpointName: String)
    fun onEndpointLost(endpointId: String)
    fun onConnectionInitiated(
        endpointId: String,
        endpointName: String,
        authenticationDigits: String
    )
    fun onConnectionResult(endpointId: String, endpointName: String?, isSuccess: Boolean)
    fun onDisconnected(endpointId: String)
    fun onBytesPayloadReceived(endpointId: String, bytes: ByteArray)
    fun onFilePayloadReceived(endpointId: String, payloadId: Long, temporaryUri: Uri)
    fun onFilePayloadTransferUpdate(
        endpointId: String,
        payloadId: Long,
        bytesTransferred: Long,
        totalBytes: Long,
        status: Int
    )
}

class NearbyConnectionsClient(
    context: Context,
    private val listener: NearbyConnectionsListener
) {
    private val contentResolver = context.applicationContext.contentResolver
    private val endpointNames = mutableMapOf<String, String>()
    private val incomingFilePayloadIds = mutableSetOf<Long>()
    private val outgoingFileDescriptors = mutableMapOf<Long, ParcelFileDescriptor>()

    val connectionsClient: ConnectionsClient =
        Nearby.getConnectionsClient(context.applicationContext)

    val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder()
        .setStrategy(STRATEGY)
        .build()

    val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder()
        .setStrategy(STRATEGY)
        .build()

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            endpointNames[endpointId] = connectionInfo.endpointName
            listener.onConnectionInitiated(
                endpointId = endpointId,
                endpointName = connectionInfo.endpointName,
                authenticationDigits = connectionInfo.authenticationDigits
            )
        }

        override fun onConnectionResult(
            endpointId: String,
            connectionResolution: ConnectionResolution
        ) {
            listener.onConnectionResult(
                endpointId = endpointId,
                endpointName = endpointNames[endpointId],
                isSuccess = connectionResolution.status.isSuccess
            )
        }

        override fun onDisconnected(endpointId: String) {
            endpointNames.remove(endpointId)
            listener.onDisconnected(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            endpointNames[endpointId] = info.endpointName
            listener.onEndpointFound(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            endpointNames.remove(endpointId)
            listener.onEndpointLost(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { bytes ->
                    listener.onBytesPayloadReceived(endpointId, bytes)
                }

                Payload.Type.FILE -> payload.asFile()?.asUri()?.let { uri ->
                    incomingFilePayloadIds += payload.id
                    listener.onFilePayloadReceived(endpointId, payload.id, uri)
                }
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            val isIncomingFile = update.payloadId in incomingFilePayloadIds
            val isOutgoingFile = update.payloadId in outgoingFileDescriptors
            if (!isIncomingFile && !isOutgoingFile) return

            listener.onFilePayloadTransferUpdate(
                endpointId = endpointId,
                payloadId = update.payloadId,
                bytesTransferred = update.bytesTransferred,
                totalBytes = update.totalBytes,
                status = update.status
            )

            if (update.status != PayloadTransferUpdate.Status.IN_PROGRESS) {
                outgoingFileDescriptors.remove(update.payloadId)?.close()
                incomingFilePayloadIds.remove(update.payloadId)
            }
        }
    }

    fun startAdvertising(endpointName: String): Task<Void> =
        connectionsClient.startAdvertising(
            endpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        )

    fun startDiscovery(): Task<Void> =
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        )

    fun requestConnection(localEndpointName: String, endpointId: String): Task<Void> =
        connectionsClient.requestConnection(
            localEndpointName,
            endpointId,
            connectionLifecycleCallback
        )

    fun acceptConnection(endpointId: String): Task<Void> =
        connectionsClient.acceptConnection(endpointId, payloadCallback)

    fun rejectConnection(endpointId: String): Task<Void> =
        connectionsClient.rejectConnection(endpointId)

    fun sendBytes(endpointId: String, bytes: ByteArray): Task<Void> =
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))

    fun sendFile(
        endpointId: String,
        uri: Uri,
        protectMetadata: (FileMetadata) -> ByteArray
    ): FileMetadata {
        val descriptor = contentResolver.openFileDescriptor(uri, "r")
            ?: error("Não foi possível abrir o arquivo selecionado.")
        val payload = Payload.fromFile(descriptor)
        val metadata = queryFileMetadata(uri, payload.id, descriptor)
        outgoingFileDescriptors[payload.id] = descriptor

        return runCatching {
            connectionsClient.sendPayload(
                endpointId,
                Payload.fromBytes(protectMetadata(metadata))
            )
            connectionsClient.sendPayload(endpointId, payload)
            metadata
        }.getOrElse { error ->
            outgoingFileDescriptors.remove(payload.id)?.close()
            throw error
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
    }

    fun disconnectFromEndpoint(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        endpointNames.remove(endpointId)
    }

    fun close() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        outgoingFileDescriptors.values.forEach { descriptor ->
            runCatching { descriptor.close() }
        }
        outgoingFileDescriptors.clear()
        incomingFilePayloadIds.clear()
        endpointNames.clear()
    }

    private fun queryFileMetadata(
        uri: Uri,
        payloadId: Long,
        descriptor: ParcelFileDescriptor
    ): FileMetadata {
        var fileName = uri.lastPathSegment ?: "arquivo"
        var totalBytes = descriptor.statSize.takeIf { it >= 0L } ?: 0L

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                    fileName = cursor.getString(nameColumn)
                }
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    totalBytes = cursor.getLong(sizeColumn)
                }
            }
        }

        return FileMetadata(
            payloadId = payloadId,
            fileName = fileName,
            totalBytes = totalBytes.coerceAtLeast(0L),
            mimeType = contentResolver.getType(uri) ?: "application/octet-stream",
            sha256 = contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
                digest.digest()
            } ?: error("Não foi possível calcular o hash do arquivo selecionado.")
        )
    }

    companion object {
        val SERVICE_ID: String = BuildConfig.APPLICATION_ID
        val STRATEGY: Strategy = Strategy.P2P_STAR
        const val STRATEGY_NAME = "P2P_STAR"
    }
}
