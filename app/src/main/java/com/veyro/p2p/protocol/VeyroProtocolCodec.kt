package com.veyro.p2p.protocol

object VeyroProtocolCodec {
    const val PROTOCOL_VERSION = "veyro.features.v1"

    fun encodeBatteryStatus(status: BatteryStatus): ByteArray = VeyroMessage.newBuilder()
        .setProtocolVersion(PROTOCOL_VERSION)
        .setBatteryStatus(status)
        .build()
        .toByteArray()

    fun encodeFindDeviceRequest(request: FindDeviceRequest): ByteArray =
        VeyroMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setFindDeviceRequest(request)
            .build()
            .toByteArray()

    fun encodeNotificationSyncEvent(event: NotificationSyncEvent): ByteArray =
        VeyroMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setNotificationSyncEvent(event)
            .build()
            .toByteArray()

    fun encodeMediaControlEvent(event: MediaControlEvent): ByteArray =
        VeyroMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setMediaControlEvent(event)
            .build()
            .toByteArray()

    fun encodeTelecommunicationEvent(event: TelecommunicationEvent): ByteArray =
        VeyroMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setTelecommunicationEvent(event)
            .build()
            .toByteArray()

    fun encodeCustomCommandEvent(event: CustomCommandEvent): ByteArray =
        VeyroMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setCustomCommandEvent(event)
            .build()
            .toByteArray()

    fun encodeUrlShareEvent(event: UrlShareEvent): ByteArray =
        VeyroMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setUrlShareEvent(event)
            .build()
            .toByteArray()

    fun encodeRemoteInputEvent(event: RemoteInputEvent): ByteArray =
        VeyroMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setRemoteInputEvent(event)
            .build()
            .toByteArray()

    fun encodeConnectivityStatus(status: ConnectivityStatus): ByteArray =
        VeyroMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setConnectivityStatus(status)
            .build()
            .toByteArray()

    fun encodePingEvent(event: PingEvent): ByteArray = VeyroMessage.newBuilder()
        .setProtocolVersion(PROTOCOL_VERSION)
        .setPingEvent(event)
        .build()
        .toByteArray()

    fun encodeContactSyncEvent(event: ContactSyncEvent): ByteArray = VeyroMessage.newBuilder()
        .setProtocolVersion(PROTOCOL_VERSION)
        .setContactSyncEvent(event)
        .build()
        .toByteArray()

    fun encodePresentationEvent(event: PresentationEvent): ByteArray = VeyroMessage.newBuilder()
        .setProtocolVersion(PROTOCOL_VERSION)
        .setPresentationEvent(event)
        .build()
        .toByteArray()

    fun encodeRemoteFileEvent(event: RemoteFileEvent): ByteArray = VeyroMessage.newBuilder()
        .setProtocolVersion(PROTOCOL_VERSION)
        .setRemoteFileEvent(event)
        .build()
        .toByteArray()

    fun encodeClipboardSyncEvent(event: ClipboardSyncEvent): ByteArray = VeyroMessage.newBuilder()
        .setProtocolVersion(PROTOCOL_VERSION)
        .setClipboardSyncEvent(event)
        .build()
        .toByteArray()

    fun decodeFeatureMessage(bytes: ByteArray): VeyroMessage? = runCatching {
        VeyroMessage.parseFrom(bytes)
    }.getOrNull()
        ?.takeIf { message ->
            message.protocolVersion == PROTOCOL_VERSION &&
                message.payloadCase != VeyroMessage.PayloadCase.PAYLOAD_NOT_SET
        }

    fun decodeBatteryStatus(bytes: ByteArray): BatteryStatus? = decodeFeatureMessage(bytes)
        ?.takeIf(VeyroMessage::hasBatteryStatus)
        ?.batteryStatus
}
