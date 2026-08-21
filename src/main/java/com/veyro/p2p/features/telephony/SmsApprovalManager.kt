package com.veyro.p2p.features.telephony

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.veyro.p2p.MainActivity
import com.veyro.p2p.R
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class PendingSmsRequest(
    val address: String,
    val text: String
)

internal object PendingSmsApprovalStore {
    private val requests = ConcurrentHashMap<String, PendingSmsRequest>()

    fun put(request: PendingSmsRequest): String = UUID.randomUUID().toString().also { token ->
        requests[token] = request
    }

    fun consume(token: String): PendingSmsRequest? = requests.remove(token)
}

internal class SmsApprovalManager(context: Context) {
    private val appContext = context.applicationContext

    fun requestApproval(address: String, text: String): Boolean {
        val cleanAddress = address.trim().take(MAX_ADDRESS_LENGTH)
        val cleanText = text.trim().take(MAX_SMS_LENGTH)
        if (cleanAddress.isBlank() || cleanText.isBlank()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        createChannel()
        val token = PendingSmsApprovalStore.put(PendingSmsRequest(cleanAddress, cleanText))
        val notificationId = token.hashCode()
        val openApp = PendingIntent.getActivity(
            appContext,
            notificationId,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val approveIntent = actionIntent(token, notificationId, SmsApprovalReceiver.ACTION_APPROVE, 1)
        val rejectIntent = actionIntent(token, notificationId, SmsApprovalReceiver.ACTION_REJECT, 2)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_veyro_transfer)
            .setContentTitle("Confirmar envio de SMS")
            .setContentText("Para $cleanAddress: ${cleanText.take(90)}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "O outro aparelho solicitou este envio.\n\nDestino: $cleanAddress\n\n$cleanText"
                )
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setTimeoutAfter(APPROVAL_TIMEOUT_MILLIS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(0, "Recusar", rejectIntent)
            .addAction(0, "Enviar SMS", approveIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        }.isSuccess
    }

    private fun actionIntent(
        token: String,
        notificationId: Int,
        action: String,
        offset: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        notificationId + offset,
        Intent(appContext, SmsApprovalReceiver::class.java)
            .setAction(action)
            .putExtra(SmsApprovalReceiver.EXTRA_TOKEN, token)
            .putExtra(SmsApprovalReceiver.EXTRA_NOTIFICATION_ID, notificationId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Aprovação de SMS Veyro",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pede confirmação antes de enviar um SMS solicitado remotamente."
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "veyro_sms_approval"
        const val MAX_ADDRESS_LENGTH = 64
        const val MAX_SMS_LENGTH = 8_000
        const val APPROVAL_TIMEOUT_MILLIS = 10 * 60 * 1000L
    }
}
