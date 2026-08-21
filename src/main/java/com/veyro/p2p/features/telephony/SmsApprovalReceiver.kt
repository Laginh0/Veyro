package com.veyro.p2p.features.telephony

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat

class SmsApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val request = PendingSmsApprovalStore.consume(token) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(notificationId)

        if (intent.action != ACTION_APPROVE) {
            Toast.makeText(context, "Envio de SMS recusado.", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Permissão para enviar SMS não concedida.", Toast.LENGTH_LONG).show()
            return
        }

        runCatching { sendSms(request) }
            .onSuccess {
                Toast.makeText(context, "SMS autorizado e enviado.", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(context, "Não foi possível enviar o SMS.", Toast.LENGTH_LONG).show()
            }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun sendSms(request: PendingSmsRequest) {
        val manager = SmsManager.getDefault()
        val parts = manager.divideMessage(request.text)
        if (parts.size <= 1) {
            manager.sendTextMessage(request.address, null, request.text, null, null)
        } else {
            manager.sendMultipartTextMessage(
                request.address,
                null,
                ArrayList(parts),
                null as ArrayList<PendingIntent>?,
                null as ArrayList<PendingIntent>?
            )
        }
    }

    companion object {
        const val ACTION_APPROVE = "com.veyro.p2p.action.APPROVE_SMS"
        const val ACTION_REJECT = "com.veyro.p2p.action.REJECT_SMS"
        const val EXTRA_TOKEN = "approval_token"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
