package com.veyro.p2p.features.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.veyro.p2p.protocol.TelecommunicationEvent
import com.veyro.p2p.protocol.TelecommunicationType

class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val number = messages.firstNotNullOfOrNull { it.originatingAddress }.orEmpty()
        val text = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
            .take(MAX_SHARED_SMS_LENGTH)
        val timestamp = messages.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        TelephonySyncBridge.publish(
            TelecommunicationEvent.newBuilder()
                .setTelecommunicationType(TelecommunicationType.SMS_RECEIVED_EVENT)
                .setIdentityLabel(ContactNameResolver(context).resolve(number))
                .setAddressNumber(number)
                .setTextPayload(text)
                .setEpochTimestamp(timestamp)
                .build()
        )
    }

    private companion object {
        const val MAX_SHARED_SMS_LENGTH = 8_000
    }
}
