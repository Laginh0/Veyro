package com.veyro.p2p.features.remoteinput

import com.veyro.p2p.protocol.RemoteInputEvent
import java.lang.ref.WeakReference

object RemoteInputBridge {
    @Volatile
    private var serviceReference = WeakReference<VeyroAccessibilityService>(null)

    internal fun attach(service: VeyroAccessibilityService) {
        serviceReference = WeakReference(service)
    }

    internal fun detach(service: VeyroAccessibilityService) {
        if (serviceReference.get() === service) serviceReference.clear()
    }

    fun dispatch(event: RemoteInputEvent): Boolean =
        serviceReference.get()?.handleRemoteInput(event) == true

    fun resetEphemeralState() {
        serviceReference.get()?.resetRemoteInputState()
    }

    fun isConnected(): Boolean = serviceReference.get() != null
}
