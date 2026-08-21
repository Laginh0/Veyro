package com.veyro.p2p.permissions

import android.Manifest
import android.os.Build
import com.veyro.p2p.settings.FeatureSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PermissionManagerTest {
    @Test
    fun android13StartupRequestsOnlyNearbyConnectionPermissions() {
        val permissions = PermissionManager.requiredPermissionsForSdk(Build.VERSION_CODES.TIRAMISU)

        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ),
            permissions
        )
        assertFalse(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun android12IncludesLocationRequiredByWifiDirectPeerDiscovery() {
        val permissions = PermissionManager.requiredPermissionsForSdk(Build.VERSION_CODES.S)

        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            permissions
        )
    }

    @Test
    fun legacyStartupDoesNotRequestStoragePermission() {
        val permissions = PermissionManager.requiredPermissionsForSdk(Build.VERSION_CODES.P)

        assertEquals(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            permissions
        )
        assertFalse(permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    @Test
    fun android13TelephonyConsentIncludesBackgroundApprovalNotifications() {
        val permissions = PermissionManager.telephonyPermissionsForSdk(Build.VERSION_CODES.TIRAMISU)

        assertEquals(
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.POST_NOTIFICATIONS
            ),
            permissions
        )
    }

    @Test
    fun activationMapsOnlyPrivilegedFeaturesToContextualAccess() {
        val current = FeatureSettings()

        assertEquals(
            OptionalFeatureAccess.NOTIFICATION_LISTENER,
            PermissionManager.optionalAccessForActivation(
                current,
                current.copy(notificationSync = true)
            )
        )
        assertEquals(
            OptionalFeatureAccess.TELEPHONY,
            PermissionManager.optionalAccessForActivation(
                current,
                current.copy(telephonySync = true)
            )
        )
        assertEquals(
            OptionalFeatureAccess.CAMERA,
            PermissionManager.optionalAccessForActivation(
                current,
                current.copy(safeCommands = true)
            )
        )
        assertEquals(
            null,
            PermissionManager.optionalAccessForActivation(
                current,
                current.copy(clipboardSync = true)
            )
        )
    }

    @Test
    fun disablingAFeatureNeverRequestsAccess() {
        val current = FeatureSettings(remoteInput = true)

        assertEquals(
            null,
            PermissionManager.optionalAccessForActivation(
                current,
                current.copy(remoteInput = false)
            )
        )
    }

    @Test
    fun unavailableAccessDisablesOnlyTheAffectedPrivilegedFeatures() {
        val allEnabled = FeatureSettings(
            notificationSync = true,
            mediaControl = true,
            telephonySync = true,
            findDevice = true,
            safeCommands = true,
            remoteInput = true
        )

        val reconciled = PermissionManager.disableUnavailablePrivilegedFeatures(
            settings = allEnabled,
            notificationListenerGranted = true,
            notificationPolicyGranted = true,
            telephonyPermissionsGranted = true,
            cameraPermissionGranted = true,
            accessibilityGranted = false
        )

        assertFalse(reconciled.remoteInput)
        assertEquals(true, reconciled.notificationSync)
        assertEquals(true, reconciled.mediaControl)
        assertEquals(true, reconciled.telephonySync)
        assertEquals(true, reconciled.findDevice)
        assertEquals(true, reconciled.safeCommands)
        assertEquals(allEnabled.clipboardSync, reconciled.clipboardSync)
    }
}
