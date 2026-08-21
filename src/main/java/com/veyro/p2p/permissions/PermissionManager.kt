package com.veyro.p2p.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.veyro.p2p.settings.FeatureSettings

enum class OptionalFeatureAccess {
    NOTIFICATION_LISTENER,
    NOTIFICATION_POLICY,
    TELEPHONY,
    CAMERA,
    ACCESSIBILITY
}

class PermissionManager(private val context: Context) {

    fun requiredRuntimePermissions(): List<String> = requiredPermissionsForSdk(Build.VERSION.SDK_INT)

    fun missingRuntimePermissions(): List<String> = requiredRuntimePermissions().filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    fun hasRequiredPermissions(): Boolean = missingRuntimePermissions().isEmpty()

    fun optionalTelephonyPermissions(): List<String> = telephonyPermissionsForSdk(Build.VERSION.SDK_INT)

    fun hasOptionalTelephonyPermissions(): Boolean = optionalTelephonyPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        @SuppressLint("InlinedApi")
        fun requiredPermissionsForSdk(sdkInt: Int): List<String> {
            val nearbyPermissions = when {
                sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )

                sdkInt >= Build.VERSION_CODES.S -> listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )

                sdkInt >= Build.VERSION_CODES.M -> listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )

                else -> emptyList()
            }

            return nearbyPermissions
        }

        @SuppressLint("InlinedApi")
        fun telephonyPermissionsForSdk(sdkInt: Int): List<String> =
            if (sdkInt >= Build.VERSION_CODES.M) {
                buildList {
                    addAll(listOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS
                    ))
                    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            } else {
                emptyList()
            }

        fun optionalAccessForActivation(
            current: FeatureSettings,
            requested: FeatureSettings
        ): OptionalFeatureAccess? = when {
            !current.notificationSync && requested.notificationSync ->
                OptionalFeatureAccess.NOTIFICATION_LISTENER
            !current.mediaControl && requested.mediaControl ->
                OptionalFeatureAccess.NOTIFICATION_LISTENER
            !current.telephonySync && requested.telephonySync ->
                OptionalFeatureAccess.TELEPHONY
            !current.findDevice && requested.findDevice ->
                OptionalFeatureAccess.NOTIFICATION_POLICY
            !current.safeCommands && requested.safeCommands ->
                OptionalFeatureAccess.CAMERA
            !current.remoteInput && requested.remoteInput ->
                OptionalFeatureAccess.ACCESSIBILITY
            else -> null
        }

        fun disableUnavailablePrivilegedFeatures(
            settings: FeatureSettings,
            notificationListenerGranted: Boolean,
            notificationPolicyGranted: Boolean,
            telephonyPermissionsGranted: Boolean,
            cameraPermissionGranted: Boolean,
            accessibilityGranted: Boolean
        ): FeatureSettings = settings.copy(
            notificationSync = settings.notificationSync && notificationListenerGranted,
            mediaControl = settings.mediaControl && notificationListenerGranted,
            telephonySync = settings.telephonySync && telephonyPermissionsGranted,
            findDevice = settings.findDevice && notificationPolicyGranted,
            safeCommands = settings.safeCommands && cameraPermissionGranted,
            remoteInput = settings.remoteInput && accessibilityGranted
        )
    }
}
