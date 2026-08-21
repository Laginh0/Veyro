package com.veyro.p2p.features.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.veyro.p2p.protocol.TelecommunicationEvent
import com.veyro.p2p.protocol.TelecommunicationType

internal class TelephonyCallStateMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var modernCallback: ModernCallback? = null
    private var legacyListener: PhoneStateListener? = null
    private var sawRinging = false
    private var sawOffHook = false
    private var sawNonIdleState = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        stop()
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ModernCallback(::handleCallState).also { callback ->
                    modernCallback = callback
                    telephonyManager.registerTelephonyCallback(appContext.mainExecutor, callback)
                }
            } else {
                @Suppress("DEPRECATION")
                object : PhoneStateListener() {
                    @Deprecated("Deprecated by Android")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }.also { listener ->
                    legacyListener = listener
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                }
            }
        }.isSuccess
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernCallback?.let { callback ->
                runCatching { telephonyManager.unregisterTelephonyCallback(callback) }
            }
        }
        legacyListener?.let { listener ->
            @Suppress("DEPRECATION")
            runCatching { telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE) }
        }
        modernCallback = null
        legacyListener = null
        sawRinging = false
        sawOffHook = false
        sawNonIdleState = false
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (!sawRinging) publishAnonymousIncomingCallIfNeeded()
                sawNonIdleState = true
                sawRinging = true
                sawOffHook = false
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                sawNonIdleState = true
                if (sawRinging) sawOffHook = true
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (sawNonIdleState && sawRinging && !sawOffHook) publishMissedCall()
                sawRinging = false
                sawOffHook = false
                sawNonIdleState = false
            }
        }
    }

    private fun publishMissedCall() {
        val incoming = TelephonySyncBridge.latestIncomingCall()
        TelephonySyncBridge.publish(
            TelecommunicationEvent.newBuilder()
                .setTelecommunicationType(TelecommunicationType.MISSED_CALL)
                .setIdentityLabel(incoming?.identityLabel.orEmpty().ifBlank { "Número desconhecido" })
                .setAddressNumber(incoming?.addressNumber.orEmpty())
                .setEpochTimestamp(System.currentTimeMillis())
                .build()
        )
    }

    private fun publishAnonymousIncomingCallIfNeeded() {
        val veyroOwnsScreeningRole = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = appContext.getSystemService(android.app.role.RoleManager::class.java)
            roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING) &&
                roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
        } else {
            false
        }
        if (veyroOwnsScreeningRole) return

        TelephonySyncBridge.publish(
            TelecommunicationEvent.newBuilder()
                .setTelecommunicationType(TelecommunicationType.INBOUND_CALL)
                .setIdentityLabel("Chamada recebida")
                .setEpochTimestamp(System.currentTimeMillis())
                .build()
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class ModernCallback(
        private val onStateChanged: (Int) -> Unit
    ) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = onStateChanged(state)
    }
}
