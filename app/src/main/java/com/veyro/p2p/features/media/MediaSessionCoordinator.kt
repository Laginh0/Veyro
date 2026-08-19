package com.veyro.p2p.features.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.veyro.p2p.features.notifications.VeyroNotificationListenerService
import com.veyro.p2p.protocol.MediaControlEvent
import com.veyro.p2p.protocol.MediaEventCategory

class MediaSessionCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val sessionManager =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(
        appContext,
        VeyroNotificationListenerService::class.java
    )
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentController: MediaController? = null
    private var currentCallback: MediaController.Callback? = null
    private var reportListener: ((MediaControlEvent) -> Unit)? = null
    private var isSessionsListenerRegistered = false

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
    }

    @Synchronized
    fun execute(category: MediaEventCategory): Result<Unit> = runCatching {
        val controller = currentController ?: findBestController().also {
            selectBestController(listOfNotNull(it))
        } ?: error("Nenhuma sessão de mídia ativa.")

        when (category) {
            MediaEventCategory.CMD_PLAY -> controller.transportControls.play()
            MediaEventCategory.CMD_PAUSE -> controller.transportControls.pause()
            MediaEventCategory.CMD_NEXT -> controller.transportControls.skipToNext()
            MediaEventCategory.CMD_PREV -> controller.transportControls.skipToPrevious()
            MediaEventCategory.CMD_VOL_UP ->
                controller.adjustVolume(AudioManager.ADJUST_RAISE, 0)

            MediaEventCategory.CMD_VOL_DOWN ->
                controller.adjustVolume(AudioManager.ADJUST_LOWER, 0)

            MediaEventCategory.STATE_REPORT,
            MediaEventCategory.MEDIA_EVENT_CATEGORY_UNKNOWN,
            MediaEventCategory.UNRECOGNIZED -> error("Comando de mídia inválido.")
        }
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
                MediaControlEvent.newBuilder()
                    .setEventCategory(MediaEventCategory.STATE_REPORT)
                    .setPlaybackStatus(state?.state ?: PlaybackState.STATE_NONE)
                    .setTrackName(
                        metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                            ?: ""
                    )
                    .setArtistName(
                        metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                            ?: ""
                    )
                    .setCurrentPositionMs(state.currentPositionMs())
                    .build()
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
        if (isPlaying) {
            mainHandler.postDelayed(progressReporter, PROGRESS_REPORT_INTERVAL_MILLIS)
        }
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
        const val PROGRESS_REPORT_INTERVAL_MILLIS = 1_000L
    }
}
