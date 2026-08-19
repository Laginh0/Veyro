package com.veyro.p2p.features.shareurl

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.veyro.p2p.R

internal data class SharedUrlResult(
    val accepted: Boolean,
    val normalizedUrl: String,
    val message: String
)

internal class SharedUrlNotificationManager(context: Context) {
    private val appContext = context.applicationContext

    fun offer(rawUrl: String, requiresImmediateFocus: Boolean): SharedUrlResult {
        val uri = normalizeHttpUrl(rawUrl)
            ?: return SharedUrlResult(false, "", "Somente links HTTP ou HTTPS são aceitos.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return SharedUrlResult(false, uri.toString(), "Permissão de notificações não concedida.")
        }

        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val requestCode = uri.toString().hashCode()
        val openLink = PendingIntent.getActivity(
            appContext,
            requestCode,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        createChannel()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_veyro_transfer)
            .setContentTitle("Link recebido pelo Veyro")
            .setContentText(uri.toString().take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(uri.toString()))
            .setContentIntent(openLink)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(
                if (requiresImmediateFocus) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .addAction(0, "Abrir link", openLink)
            .build()
        return runCatching {
            NotificationManagerCompat.from(appContext).notify(requestCode, notification)
            SharedUrlResult(true, uri.toString(), "Link aguardando abertura local.")
        }.getOrElse { error ->
            SharedUrlResult(false, uri.toString(), error.localizedMessage ?: "Falha ao publicar link.")
        }
    }

    private fun normalizeHttpUrl(rawUrl: String): Uri? {
        val clean = rawUrl.trim().take(MAX_URL_LENGTH)
        val withScheme = if ("://" in clean) clean else "https://$clean"
        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return uri
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Links recebidos pelo Veyro",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Links remotos que aguardam uma ação local para serem abertos."
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "veyro_shared_urls"
        const val MAX_URL_LENGTH = 2_048
    }
}
