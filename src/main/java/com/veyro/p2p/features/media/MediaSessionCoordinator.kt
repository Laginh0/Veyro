package com.veyro.p2p.features.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.protobuf.ByteString
import com.veyro.p2p.features.notifications.VeyroNotificationListenerService
import com.veyro.p2p.protocol.AudioOutputRoute
import com.veyro.p2p.protocol.AudioStreamKind
import com.veyro.p2p.protocol.AudioStreamVolume
import com.veyro.p2p.protocol.MediaControlEvent
import com.veyro.p2p.protocol.MediaEventCategory
import java.io.ByteArrayOutputStream

class MediaSessionCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val sessionManager =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val listenerComponent = ComponentName(
        appContext,
        VeyroNotificationListenerService::class.java
    )
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentController: MediaController? = null
    private var currentCallback: MediaController.Callback? = null
    private var reportListener: ((MediaControlEvent) -> Unit)? = null
    private var isSessionsListenerRegistered = false
    private var lastArtworkSignature: String? = null

    private val progressReporter = object : Runnable {
        override fun run() {
            publishState(currentController)
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            selectBestController(controllers.orEmpty())
        }

    @Synchronized
    fun start(onStateReport: (MediaControlEvent) -> Unit): Result<Unit> {
        stop()
        reportListener = onStateReport

        return runCatching {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                listenerComponent,
                mainHandler
            )
            isSessionsListenerRegistered = true
            refreshControllers()
        }.onFailure {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        mainHandler.removeCallbacks(progressReporter)
        currentController?.let { controller ->
            currentCallback?.let { callback ->
                runCatching { controller.unregisterCallback(callback) }
            }
        }
        currentController = null
        currentCallback = null

        if (isSessionsListenerRegistered) {
            runCatching {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            }
        }
        isSessionsListenerRegistered = false
        reportListener = null
        lastArtworkSignature = null
    }

    @Synchronized
    fun execute(category: MediaEventCategory): Result<Unit> = execute(
        MediaControlEvent.newBuilder().setEventCategory(category).build()
    )

    @Synchronized
    fun execute(event: MediaControlEvent): Result<Unit> = runCatching {
        val controller = currentController ?: findBestController().also {
            selectBestController(listOfNotNull(it))
        } ?: error("Nenhuma sessão de mídia ativa.")

        when (event.eventCategory) {
            MediaEventCategory.CMD_PLAY -> controller.transportControls.play()
            MediaEventCategory.CMD_PAUSE -> controller.transportControls.pause()
            MediaEventCategory.CMD_NEXT -> controller.transportControls.skipToNext()
            MediaEventCategory.CMD_PREV -> controller.transportControls.skipToPrevious()
            MediaEventCategory.CMD_VOL_UP ->
                controller.adjustVolume(AudioManager.ADJUST_RAISE, 0)

            MediaEventCategory.CMD_VOL_DOWN ->
                controller.adjustVolume(AudioManager.ADJUST_LOWER, 0)

            MediaEventCategory.CMD_SET_VOLUME -> setMediaVolume(
                controller,
                event.requestedVolume
            )

            MediaEventCategory.CMD_SET_STREAM_VOLUME -> setStreamVolume(
                event.targetStream,
                event.requestedVolume
            )

            MediaEventCategory.CMD_SEEK_TO -> controller.transportControls.seekTo(
                event.requestedPositionMs.coerceAtLeast(0L)
            )

            MediaEventCategory.STATE_REPORT,
            MediaEventCategory.MEDIA_EVENT_CATEGORY_UNKNOWN,
            MediaEventCategory.UNRECOGNIZED -> error("Comando de mídia inválido.")
        }
        mainHandler.postDelayed({ publishState(currentController) }, STATE_REFRESH_DELAY_MILLIS)
        Unit
    }.onFailure {
        clearDestroyedController()
        runCatching { refreshControllers() }
    }

    @Synchronized
    private fun refreshControllers() {
        selectBestController(sessionManager.getActiveSessions(listenerComponent).orEmpty())
    }

    @Synchronized
    private fun findBestController(): MediaController? =
        chooseBest(sessionManager.getActiveSessions(listenerComponent).orEmpty())

    @Synchronized
    private fun selectBestController(controllers: List<MediaController>) {
        val selected = chooseBest(controllers)
        if (selected?.sessionToken == currentController?.sessionToken) {
            publishState(selected)
            return
        }

        currentController?.let { oldController ->
            currentCallback?.let { oldCallback ->
                runCatching { oldController.unregisterCallback(oldCallback) }
            }
        }
        currentController = selected
        currentCallback = selected?.let(::createControllerCallback)
        if (selected != null && currentCallback != null) {
            selected.registerCallback(currentCallback!!, mainHandler)
        }
        publishState(selected)
    }

    private fun chooseBest(controllers: List<MediaController>): MediaController? =
        controllers.firstOrNull {
            runCatching { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .getOrDefault(false)
        } ?: controllers.firstOrNull()

    private fun createControllerCallback(controller: MediaController) =
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                mainHandler.post {
                    if (currentController?.sessionToken == controller.sessionToken) {
                        runCatching { refreshControllers() }
                    }
                }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                if (currentController?.sessionToken == controller.sessionToken) {
                    publishState(controller)
                }
            }

            override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) {
                if (currentController?.sessionToken == controller.sessionToken) {
                    publishState(controller)
                }
            }

            override fun onSessionDestroyed() {
                if (currentController?.sessionToken != controller.sessionToken) return
                clearDestroyedController()
                reportListener?.invoke(emptyStateReport())
                mainHandler.post { runCatching { refreshControllers() } }
            }
        }

    @Synchronized
    private fun clearDestroyedController() {
        mainHandler.removeCallbacks(progressReporter)
        currentController?.let { controller ->
            currentCallback?.let { callback ->
                runCatching { controller.unregisterCallback(callback) }
            }
        }
        currentController = null
        currentCallback = null
    }

    private fun publishState(controller: MediaController?) {
        val event = if (controller == null) {
            emptyStateReport()
        } else {
            runCatching {
                val state = controller.playbackState
                val metadata = controller.metadata
                val trackName = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: ""
                val artistName = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: ""
                val durationMs = metadata
                    ?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    ?.coerceAtLeast(0L)
                    ?: 0L
                val artwork = metadata?.preferredArtwork()
                val artworkSignature = listOf(
                    trackName,
                    artistName,
                    durationMs.toString(),
                    artwork?.generationId?.toString().orEmpty()
                ).joinToString("|")
                val includeArtwork = artwork != null && artworkSignature != lastArtworkSignature
                if (includeArtwork) lastArtworkSignature = artworkSignature

                val playbackInfo = controller.playbackInfo
                val mediaVolume = playbackInfo?.currentVolume
                    ?: audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val mediaVolumeMax = playbackInfo?.maxVolume
                    ?: audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val builder = MediaControlEvent.newBuilder()
                    .setEventCategory(MediaEventCategory.STATE_REPORT)
                    .setPlaybackStatus(state?.state ?: PlaybackState.STATE_NONE)
                    .setTrackName(trackName)
                    .setArtistName(artistName)
                    .setCurrentPositionMs(state.currentPositionMs())
                    .setDurationMs(durationMs)
                    .setVolumeLevel(mediaVolume.coerceAtLeast(0))
                    .setVolumeMax(mediaVolumeMax.coerceAtLeast(1))
                    .addAllAudioStreamVolumes(currentAudioStreams())
                    .addAllAudioOutputRoutes(currentAudioRoutes(playbackInfo))
                if (includeArtwork) {
                    artwork?.compressedThumbnail()?.let { bytes ->
                        builder
                            .setArtworkThumbnail(ByteString.copyFrom(bytes))
                            .setArtworkMimeType("image/jpeg")
                    }
                }
                builder.build()
            }.getOrElse {
                clearDestroyedController()
                emptyStateReport()
            }
        }
        reportListener?.invoke(event)
        scheduleProgressReport(controller)
    }

    private fun scheduleProgressReport(controller: MediaController?) {
        mainHandler.removeCallbacks(progressReporter)
        val isPlaying = runCatching {
            controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        }.getOrDefault(false)
        if (controller != null) {
            mainHandler.postDelayed(
                progressReporter,
                if (isPlaying) PROGRESS_REPORT_INTERVAL_MILLIS
                else IDLE_REPORT_INTERVAL_MILLIS
            )
        }
    }

    private fun setMediaVolume(controller: MediaController, requestedVolume: Int) {
        val info = controller.playbackInfo
        val max = info?.maxVolume?.takeIf { it > 0 }
            ?: audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = requestedVolume.coerceIn(0, max.coerceAtLeast(1))
        if (info?.volumeControl == VolumeProvider.VOLUME_CONTROL_ABSOLUTE) {
            controller.setVolumeTo(target, 0)
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }

    private fun setStreamVolume(kind: AudioStreamKind, requestedVolume: Int) {
        val stream = kind.androidStream() ?: error("Fluxo de áudio inválido.")
        val max = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        audioManager.setStreamVolume(stream, requestedVolume.coerceIn(0, max), 0)
    }

    private fun currentAudioStreams(): List<AudioStreamVolume> = STREAMS.map { descriptor ->
        val current = audioManager.getStreamVolume(descriptor.androidStream)
        AudioStreamVolume.newBuilder()
            .setStreamKind(descriptor.kind)
            .setDisplayName(descriptor.label)
            .setCurrentVolume(current.coerceAtLeast(0))
            .setMaxVolume(audioManager.getStreamMaxVolume(descriptor.androidStream).coerceAtLeast(1))
            .setIsMuted(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.isStreamMute(descriptor.androidStream)
                } else {
                    current == 0
                }
            )
            .build()
    }

    private fun currentAudioRoutes(
        playbackInfo: MediaController.PlaybackInfo?
    ): List<AudioOutputRoute> {
        if (playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            return listOf(
                AudioOutputRoute.newBuilder()
                    .setRouteId(-1)
                    .setDisplayName("Saída de reprodução remota")
                    .setRouteType("REMOTE")
                    .setIsActive(true)
                    .build()
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return listOf(
                AudioOutputRoute.newBuilder()
                    .setRouteId(0)
                    .setDisplayName("Alto-falante")
                    .setRouteType("SPEAKER")
                    .setIsActive(true)
                    .build()
            )
        }
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val activeId = devices.maxByOrNull { routePriority(it.type) }?.id
        return devices.map { device ->
            AudioOutputRoute.newBuilder()
                .setRouteId(device.id)
                .setDisplayName(device.routeDisplayName())
                .setRouteType(device.routeTypeLabel())
                .setIsActive(device.id == activeId)
                .build()
        }
    }

    private fun MediaMetadata.preferredArtwork(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private fun Bitmap.compressedThumbnail(): ByteArray? = runCatching {
        val source = if (width > ARTWORK_MAX_EDGE || height > ARTWORK_MAX_EDGE) {
            val ratio = minOf(ARTWORK_MAX_EDGE.toFloat() / width, ARTWORK_MAX_EDGE.toFloat() / height)
            Bitmap.createScaledBitmap(
                this,
                (width * ratio).toInt().coerceAtLeast(1),
                (height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            this
        }
        ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.JPEG, ARTWORK_JPEG_QUALITY, output)
            output.toByteArray().takeIf { it.size <= ARTWORK_MAX_BYTES }
        }.also {
            if (source !== this && !source.isRecycled) source.recycle()
        }
    }.getOrNull()

    private fun AudioStreamKind.androidStream(): Int? = STREAMS
        .firstOrNull { it.kind == this }
        ?.androidStream

    private fun AudioDeviceInfo.routeDisplayName(): String = productName
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fones com fio"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Áudio Bluetooth"
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> "Áudio USB"
            else -> "Saída de áudio"
        }

    private fun AudioDeviceInfo.routeTypeLabel(): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "HEADPHONES"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH"
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        else -> "OTHER"
    }

    private fun routePriority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 50
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> 40
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> 30
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 10
        else -> 0
    }

    private fun PlaybackState?.currentPositionMs(): Long {
        val state = this ?: return 0L
        val basePosition = state.position.coerceAtLeast(0L)
        if (state.state != PlaybackState.STATE_PLAYING || state.lastPositionUpdateTime <= 0L) {
            return basePosition
        }
        val elapsed = (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime)
            .coerceAtLeast(0L)
        return (basePosition + (elapsed * state.playbackSpeed).toLong()).coerceAtLeast(0L)
    }

    private fun emptyStateReport(): MediaControlEvent = MediaControlEvent.newBuilder()
        .setEventCategory(MediaEventCategory.STATE_REPORT)
        .setPlaybackStatus(PlaybackState.STATE_NONE)
        .build()

    private companion object {
        data class StreamDescriptor(
            val kind: AudioStreamKind,
            val androidStream: Int,
            val label: String
        )

        val STREAMS = listOf(
            StreamDescriptor(AudioStreamKind.MEDIA, AudioManager.STREAM_MUSIC, "Mídia"),
            StreamDescriptor(AudioStreamKind.RING, AudioManager.STREAM_RING, "Toque"),
            StreamDescriptor(AudioStreamKind.ALARM, AudioManager.STREAM_ALARM, "Alarme"),
            StreamDescriptor(AudioStreamKind.NOTIFICATION, AudioManager.STREAM_NOTIFICATION, "Notificações"),
            StreamDescriptor(AudioStreamKind.VOICE_CALL, AudioManager.STREAM_VOICE_CALL, "Chamadas"),
            StreamDescriptor(AudioStreamKind.SYSTEM, AudioManager.STREAM_SYSTEM, "Sistema")
        )
        const val PROGRESS_REPORT_INTERVAL_MILLIS = 1_000L
        const val IDLE_REPORT_INTERVAL_MILLIS = 5_000L
        const val STATE_REFRESH_DELAY_MILLIS = 180L
        const val ARTWORK_MAX_EDGE = 384
        const val ARTWORK_JPEG_QUALITY = 82
        const val ARTWORK_MAX_BYTES = 196_608
    }
}
