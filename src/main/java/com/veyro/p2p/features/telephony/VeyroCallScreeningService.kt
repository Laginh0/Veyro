package com.veyro.p2p.features.telephony

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.veyro.p2p.protocol.TelecommunicationEvent
import com.veyro.p2p.protocol.TelecommunicationType

@RequiresApi(Build.VERSION_CODES.N)
class VeyroCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        // The call is always allowed and the mandatory response happens before any lookup.
        respondToCall(callDetails, CallResponse.Builder().build())

        val isIncoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        if (!isIncoming) return

        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        TelephonySyncBridge.publish(
            TelecommunicationEvent.newBuilder()
                .setTelecommunicationType(TelecommunicationType.INBOUND_CALL)
                .setIdentityLabel(ContactNameResolver(this).resolve(number))
                .setAddressNumber(number)
                .setEpochTimestamp(System.currentTimeMillis())
                .build()
        )
    }
}
