package com.veyro.p2p.features.telephony

import com.veyro.p2p.protocol.TelecommunicationEvent
import com.veyro.p2p.protocol.TelecommunicationType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TelephonySyncBridge {
    private val _events = MutableSharedFlow<TelecommunicationEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<TelecommunicationEvent> = _events.asSharedFlow()

    @Volatile
    private var latestIncomingCall: TelecommunicationEvent? = null

    internal fun publish(event: TelecommunicationEvent) {
        if (event.telecommunicationType == TelecommunicationType.INBOUND_CALL) {
            latestIncomingCall = event
        }
        _events.tryEmit(event)
    }

    internal fun latestIncomingCall(): TelecommunicationEvent? = latestIncomingCall
}
