package com.veyro.p2p.features.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.veyro.p2p.protocol.NotificationSyncAction
import com.veyro.p2p.protocol.NotificationSyncEvent

class VeyroNotificationListenerService : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isListenerConnected = false

    override fun onCreate() {
        super.onCreate()
        NotificationSyncBridge.attach(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        NotificationSyncBridge.attach(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        NotificationSyncBridge.publish(sbn.toPostedEvent())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        NotificationSyncBridge.publish(
            NotificationSyncEvent.newBuilder()
                .setSyncAction(NotificationSyncAction.REMOVE_EXISTING)
                .setNotificationKey(sbn.key)
                .setPackageName(sbn.packageName)
                .build()
        )
    }

    override fun onListenerDisconnected() {
        isListenerConnected = false
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, VeyroNotificationListenerService::class.java))
        }
    }

    override fun onDestroy() {
        isListenerConnected = false
        NotificationSyncBridge.detach(this)
        super.onDestroy()
    }

    internal fun snapshotActiveNotifications(): List<NotificationSyncEvent> {
        if (!isListenerConnected) return emptyList()
        return runCatching {
            activeNotifications
                .asSequence()
                .filterNot { it.packageName == packageName }
                .map { it.toPostedEvent() }
                .toList()
        }.getOrDefault(emptyList())
    }

    internal fun dismissNotification(notificationKey: String): Boolean {
        if (!isListenerConnected || notificationKey.isBlank()) return false
        mainHandler.post {
            if (isListenerConnected) runCatching { cancelNotification(notificationKey) }
        }
        return true
    }

    private fun StatusBarNotification.toPostedEvent(): NotificationSyncEvent {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()

        return NotificationSyncEvent.newBuilder()
            .setSyncAction(NotificationSyncAction.POST_NEW)
            .setNotificationKey(key)
            .setPackageName(packageName)
            .setAppName(resolveAppName(packageName))
            .setTitle(title.take(MAX_TEXT_LENGTH))
            .setTextBody(body.take(MAX_TEXT_LENGTH))
            .setIsClearable(isClearable)
            .build()
    }

    private fun resolveAppName(sourcePackage: String): String = runCatching {
        val info = packageManager.getApplicationInfo(sourcePackage, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrElse { error ->
        if (error is PackageManager.NameNotFoundException) sourcePackage else sourcePackage
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 4_000
    }
}
