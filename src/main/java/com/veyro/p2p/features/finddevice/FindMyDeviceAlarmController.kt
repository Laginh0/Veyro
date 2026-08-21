package com.veyro.p2p.features.finddevice

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

class FindMyDeviceAlarmController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var originalAlarmVolume: Int? = null
    private var originalInterruptionFilter: Int? = null
    private val safetyStop = Runnable { stop() }

    val isActive: Boolean
        @Synchronized get() = mediaPlayer?.isPlaying == true

    @Suppress("DEPRECATION")
    @Synchronized
    fun start(volumeScalar: Float = 1f): Result<Unit> {
        stopInternal(restoreState = true)

        return runCatching {
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                notificationManager.isNotificationPolicyAccessGranted
            ) {
                originalInterruptionFilter = notificationManager.currentInterruptionFilter
                runCatching {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
            }

            if (!audioManager.isVolumeFixed) {
                val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val requested = (maximum * volumeScalar.coerceIn(0f, 1f)).roundToInt()
                val audibleVolume = if (volumeScalar > 0f) requested.coerceAtLeast(1) else 0
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    audibleVolume.coerceAtMost(maximum),
                    0
                )
            }

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: error("O aparelho não possui um som de alarme configurado.")

            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_ALARM)
                setDataSource(appContext, alarmUri)
                isLooping = true
                prepare()
                start()
            }
            mainHandler.removeCallbacks(safetyStop)
            mainHandler.postDelayed(safetyStop, MAX_ALARM_DURATION_MILLIS)
            Unit
        }.onFailure {
            stopInternal(restoreState = true)
        }
    }

    @Synchronized
    fun stop() {
        stopInternal(restoreState = true)
    }

    @Synchronized
    private fun stopInternal(restoreState: Boolean) {
        mainHandler.removeCallbacks(safetyStop)
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null

        if (restoreState) {
            originalAlarmVolume?.let { volume ->
                if (!audioManager.isVolumeFixed) {
                    runCatching {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                notificationManager.isNotificationPolicyAccessGranted
            ) {
                originalInterruptionFilter?.let { filter ->
                    runCatching { notificationManager.setInterruptionFilter(filter) }
                }
            }
        }

        originalAlarmVolume = null
        originalInterruptionFilter = null
    }

    private companion object {
        const val MAX_ALARM_DURATION_MILLIS = 2 * 60 * 1000L
    }
}
