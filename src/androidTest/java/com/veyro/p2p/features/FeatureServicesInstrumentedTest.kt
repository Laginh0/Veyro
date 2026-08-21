package com.veyro.p2p.features

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veyro.p2p.features.finddevice.FindMyDeviceAlarmController
import com.veyro.p2p.features.commands.SafeCustomCommandExecutor
import com.veyro.p2p.features.notifications.VeyroNotificationListenerService
import com.veyro.p2p.features.remoteinput.VeyroAccessibilityService
import com.veyro.p2p.features.remoteinput.RemoteInputBridge
import com.veyro.p2p.features.shareurl.SharedUrlNotificationManager
import com.veyro.p2p.features.telephony.SmsApprovalReceiver
import com.veyro.p2p.features.telephony.SmsReceivedReceiver
import com.veyro.p2p.features.telephony.VeyroCallScreeningService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.veyro.p2p.protocol.CustomCommandEvent
import com.veyro.p2p.protocol.ExecutionTypeCategory
import com.veyro.p2p.protocol.RemoteInputCommand
import com.veyro.p2p.protocol.RemoteInputEvent

@RunWith(AndroidJUnit4::class)
class FeatureServicesInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun findDeviceAlarm_startsSilentlyAndRestoresAlarmVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val controller = FindMyDeviceAlarmController(context)

        try {
            assertTrue(controller.start(volumeScalar = 0f).isSuccess)
            assertTrue(controller.isActive)
        } finally {
            controller.stop()
        }

        assertFalse(controller.isActive)
        assertEquals(
            originalVolume,
            audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        )
    }

    @Test
    fun notificationListener_isPrivateAndProtectedBySystemBindingPermission() {
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, VeyroNotificationListenerService::class.java),
            0
        )

        assertFalse(serviceInfo.exported)
        assertEquals(
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            serviceInfo.permission
        )
    }

    @Test
    fun callScreeningService_isExportedOnlyThroughSystemBindingPermission() {
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, VeyroCallScreeningService::class.java),
            0
        )

        assertTrue(serviceInfo.exported)
        assertEquals(Manifest.permission.BIND_SCREENING_SERVICE, serviceInfo.permission)
    }

    @Test
    fun smsReceivers_haveNarrowExposure() {
        val smsReceiver = context.packageManager.getReceiverInfo(
            ComponentName(context, SmsReceivedReceiver::class.java),
            0
        )
        val approvalReceiver = context.packageManager.getReceiverInfo(
            ComponentName(context, SmsApprovalReceiver::class.java),
            0
        )

        assertTrue(smsReceiver.exported)
        assertEquals(Manifest.permission.BROADCAST_SMS, smsReceiver.permission)
        assertFalse(approvalReceiver.exported)
    }

    @Test
    fun remoteInputAccessibilityService_isPrivateAndSystemProtected() {
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, VeyroAccessibilityService::class.java),
            0
        )

        assertFalse(serviceInfo.exported)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, serviceInfo.permission)
    }

    @Test
    fun arbitraryShellCommand_isRejectedWithoutExecution() {
        val result = SafeCustomCommandExecutor(context).execute(
            CustomCommandEvent.newBuilder()
                .setExecutionTypeCategory(ExecutionTypeCategory.RAW_SHELL_COMMAND)
                .setEncodedCommandString("touch /sdcard/should-never-exist")
                .build()
        )

        assertFalse(result.succeeded)
    }

    @Test
    fun nonHttpUrl_isRejectedBeforeNotification() {
        val result = SharedUrlNotificationManager(context).offer(
            "intent://unsafe#Intent;scheme=veyro;end",
            requiresImmediateFocus = true
        )

        assertFalse(result.accepted)
    }

    @Test
    fun remoteInput_withoutEnabledAccessibilityService_isRejected() {
        val accepted = RemoteInputBridge.dispatch(
            RemoteInputEvent.newBuilder()
                .setInputCommand(RemoteInputCommand.SINGLE_TAP)
                .build()
        )

        assertFalse(accepted)
    }
}
