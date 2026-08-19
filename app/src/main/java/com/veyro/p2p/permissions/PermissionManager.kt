package com.veyro.p2p.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

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
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.POST_NOTIFICATIONS
                )

                sdkInt >= Build.VERSION_CODES.S -> listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )

                sdkInt >= Build.VERSION_CODES.M -> listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )

                else -> emptyList()
            }

            return if (sdkInt in Build.VERSION_CODES.M..Build.VERSION_CODES.P) {
                nearbyPermissions + Manifest.permission.WRITE_EXTERNAL_STORAGE
            } else {
                nearbyPermissions
            }
        }

        fun telephonyPermissionsForSdk(sdkInt: Int): List<String> =
            if (sdkInt >= Build.VERSION_CODES.M) {
                listOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS
                )
            } else {
                emptyList()
            }
    }
}
