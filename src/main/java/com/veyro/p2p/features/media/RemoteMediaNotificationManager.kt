package com.veyro.p2p.features.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.session.PlaybackState
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.veyro.p2p.MainActivity
import com.veyro.p2p.R
import com.veyro.p2p.nearby.RemoteMediaState
import com.veyro.p2p.service.P2PTransferService

class RemoteMediaNotificationManager(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        createChannel()
    }

    fun update(
        state: RemoteMediaState?,
        connectedDeviceName: String?,
        shouldShow: Boolean
    ) {
        if (!shouldShow || state == null || state.trackName.isBlank()) {
            cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val isPlaying = state.playbackStatus == PlaybackState.STATE_PLAYING
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_veyro_clipboard_tile)
            .setContentTitle(state.trackName.ifBlank { "Mídia remota" })
            .setContentText(state.artistName.ifBlank { "Reprodução em outro aparelho" })
            .setSubText(
                connectedDeviceName
                    ?.removePrefix("Veyro - ")
                    ?.let { "Veyro • $it" }
                    ?: "Veyro"
            )
            .setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .addAction(
                R.drawable.ic_media_previous,
                "Anterior",
                commandIntent(ACTION_REMOTE_MEDIA_PREVIOUS, 1)
            )
            .addAction(
                if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                if (isPlaying) "Pausar" else "Reproduzir",
                commandIntent(
                    if (isPlaying) ACTION_REMOTE_MEDIA_PAUSE else ACTION_REMOTE_MEDIA_PLAY,
                    2
                )
            )
            .addAction(
                R.drawable.ic_media_next,
                "Próxima",
                commandIntent(ACTION_REMOTE_MEDIA_NEXT, 3)
            )

        if (state.durationMs > 0L) {
            val maximum = state.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val progress = state.currentPositionMs.coerceIn(0L, state.durationMs)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            builder.setProgress(maximum, progress, false)
        }

        state.artworkThumbnail.takeIf(ByteArray::isNotEmpty)?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                .getOrNull()
                ?.let { artwork ->
                    builder
                        .setLargeIcon(artwork)
                        .setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(artwork)
                                .bigLargeIcon(null as android.graphics.Bitmap?)
                                .setSummaryText(state.artistName)
                        )
                }
        }

        runCatching { notificationManager.notify(NOTIFICATION_ID, builder.build()) }
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun commandIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            appContext,
            requestCode,
            Intent(appContext, P2PTransferService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Mídia de aparelhos conectados",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra e controla a reprodução sincronizada pelo Veyro."
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        const val ACTION_REMOTE_MEDIA_PLAY = "com.veyro.p2p.action.REMOTE_MEDIA_PLAY"
        const val ACTION_REMOTE_MEDIA_PAUSE = "com.veyro.p2p.action.REMOTE_MEDIA_PAUSE"
        const val ACTION_REMOTE_MEDIA_PREVIOUS = "com.veyro.p2p.action.REMOTE_MEDIA_PREVIOUS"
        const val ACTION_REMOTE_MEDIA_NEXT = "com.veyro.p2p.action.REMOTE_MEDIA_NEXT"

        private const val CHANNEL_ID = "veyro_remote_media"
        private const val NOTIFICATION_ID = 2_104
    }
}
