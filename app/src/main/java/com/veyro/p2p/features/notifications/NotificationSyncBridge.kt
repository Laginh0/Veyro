package com.veyro.p2p.features.notifications

import com.veyro.p2p.protocol.NotificationSyncEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NotificationSyncBridge {
    private val _events = MutableSharedFlow<NotificationSyncEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<NotificationSyncEvent> = _events.asSharedFlow()

    @Volatile
    private var listener: VeyroNotificationListenerService? = null

    internal fun attach(service: VeyroNotificationListenerService) {
        listener = service
    }

    internal fun detach(service: VeyroNotificationListenerService) {
        if (listener === service) listener = null
    }

    internal fun publish(event: NotificationSyncEvent) {
        _events.tryEmit(event)
    }

    fun activeNotifications(): List<NotificationSyncEvent> =
        listener?.snapshotActiveNotifications().orEmpty()

    fun dismiss(notificationKey: String): Boolean =
        listener?.dismissNotification(notificationKey) == true
}
