package com.veyro.p2p.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VeyroProtocolCodecTest {

    @Test
    fun customCommand_roundTripsThroughEnvelope() {
        val event = CustomCommandEvent.newBuilder()
            .setCommandTrackingId("tracking")
            .setExecutionTypeCategory(ExecutionTypeCategory.NATIVE_BROADCAST_INTENT)
            .setEncodedCommandString("VEYRO_VOLUME_UP")
            .setAwaitOutputConfirmation(true)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodeCustomCommandEvent(event)
        )

        assertEquals(event, decoded?.customCommandEvent)
    }

    @Test
    fun urlShare_roundTripsThroughEnvelope() {
        val event = UrlShareEvent.newBuilder()
            .setHyperlinkTarget("https://example.com")
            .setRequiresImmediateFocus(false)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodeUrlShareEvent(event)
        )

        assertEquals(event, decoded?.urlShareEvent)
    }

    @Test
    fun remoteInput_roundTripsThroughEnvelope() {
        val event = RemoteInputEvent.newBuilder()
            .setInputCommand(RemoteInputCommand.MOUSE_DELTA)
            .setDeltaAxisX(12.5f)
            .setDeltaAxisY(-4f)
            .setMultiPointerCount(1)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodeRemoteInputEvent(event)
        )

        assertEquals(event, decoded?.remoteInputEvent)
    }

    @Test
    fun telecommunicationEvent_roundTripsThroughEnvelope() {
        val event = TelecommunicationEvent.newBuilder()
            .setTelecommunicationType(TelecommunicationType.SMS_RECEIVED_EVENT)
            .setIdentityLabel("Contato")
            .setAddressNumber("+5511999999999")
            .setTextPayload("Mensagem")
            .setEpochTimestamp(1_700_000_000_000L)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodeTelecommunicationEvent(event)
        )

        assertEquals(event, decoded?.telecommunicationEvent)
    }
    @Test
    fun batteryStatus_roundTripsThroughEnvelope() {
        val original = BatteryStatus.newBuilder()
            .setChargePercentage(73)
            .setIsPluggedIn(true)
            .setPowerSourceType(PowerSourceType.USB_COMPUTER_PORT)
            .setEventTimestamp(1_725_000_000_000L)
            .build()

        val decoded = VeyroProtocolCodec.decodeBatteryStatus(
            VeyroProtocolCodec.encodeBatteryStatus(original)
        )

        assertEquals(original, decoded)
    }

    @Test
    fun legacyText_isNotMistakenForFeatureMessage() {
        assertNull(
            VeyroProtocolCodec.decodeBatteryStatus("mensagem antiga".toByteArray())
        )
    }

    @Test
    fun wrongProtocolVersion_isRejected() {
        val message = VeyroMessage.newBuilder()
            .setProtocolVersion("outro-protocolo")
            .setBatteryStatus(BatteryStatus.getDefaultInstance())
            .build()

        assertNull(VeyroProtocolCodec.decodeBatteryStatus(message.toByteArray()))
    }

    @Test
    fun findDeviceRequest_roundTripsThroughEnvelope() {
        val request = FindDeviceRequest.newBuilder()
            .setTriggerCommand(FindDeviceTrigger.START_ALARM_SEQUENCE)
            .setVolumeScalar(0.8f)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodeFindDeviceRequest(request)
        )

        assertEquals(VeyroMessage.PayloadCase.FIND_DEVICE_REQUEST, decoded?.payloadCase)
        assertEquals(request, decoded?.findDeviceRequest)
    }

    @Test
    fun notificationEvent_roundTripsThroughEnvelope() {
        val event = NotificationSyncEvent.newBuilder()
            .setSyncAction(NotificationSyncAction.POST_NEW)
            .setNotificationKey("notification-key")
            .setPackageName("com.example.app")
            .setAppName("Aplicativo")
            .setTitle("Título")
            .setTextBody("Conteúdo")
            .setIsClearable(true)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodeNotificationSyncEvent(event)
        )

        assertEquals(VeyroMessage.PayloadCase.NOTIFICATION_SYNC_EVENT, decoded?.payloadCase)
        assertEquals(event, decoded?.notificationSyncEvent)
    }

    @Test
    fun mediaControlEvent_roundTripsThroughEnvelope() {
        val event = MediaControlEvent.newBuilder()
            .setEventCategory(MediaEventCategory.STATE_REPORT)
            .setPlaybackStatus(3)
            .setTrackName("Faixa de teste")
            .setArtistName("Artista de teste")
            .setCurrentPositionMs(42_000L)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodeMediaControlEvent(event)
        )

        assertEquals(VeyroMessage.PayloadCase.MEDIA_CONTROL_EVENT, decoded?.payloadCase)
        assertEquals(event, decoded?.mediaControlEvent)
    }

    @Test
    fun connectivityStatus_roundTripsThroughEnvelope() {
        val status = ConnectivityStatus.newBuilder()
            .setActiveTransport(NetworkTransport.NETWORK_TRANSPORT_WIFI)
            .setHasInternet(true)
            .setIsMetered(false)
            .setHasSignalStrength(true)
            .setSignalStrengthDbm(-54)
            .setEventTimestamp(1_700_000_000_000L)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodeConnectivityStatus(status)
        )

        assertEquals(VeyroMessage.PayloadCase.CONNECTIVITY_STATUS, decoded?.payloadCase)
        assertEquals(status, decoded?.connectivityStatus)
    }

    @Test
    fun pingEvent_roundTripsThroughEnvelope() {
        val event = PingEvent.newBuilder()
            .setRequestId("ping-request")
            .setAction(PingAction.PING_REQUEST)
            .build()

        val decoded = VeyroProtocolCodec.decodeFeatureMessage(
            VeyroProtocolCodec.encodePingEvent(event)
        )

        assertEquals(VeyroMessage.PayloadCase.PING_EVENT, decoded?.payloadCase)
        assertEquals(event, decoded?.pingEvent)
    }
}
