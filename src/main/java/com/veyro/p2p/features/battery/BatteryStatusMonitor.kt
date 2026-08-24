package com.veyro.p2p.features.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.veyro.p2p.protocol.BatteryStatus
import com.veyro.p2p.protocol.PowerSourceType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

class BatteryStatusMonitor(context: Context) {
    private val appContext = context.applicationContext

    @OptIn(FlowPreview::class)
    fun statusUpdates(): Flow<BatteryStatus> = callbackFlow {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.toBatteryStatus()?.let { trySend(it) }
            }
        }

        appContext.registerReceiver(null, filter)
            ?.toBatteryStatus()
            ?.let { trySend(it) }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }
        .distinctUntilChanged { old, new ->
            old.chargePercentage == new.chargePercentage &&
                old.isPluggedIn == new.isPluggedIn &&
                old.powerSourceType == new.powerSourceType
        }
        .debounce(BATTERY_DEBOUNCE_MILLIS)

    private fun Intent.toBatteryStatus(): BatteryStatus? {
        if (action != Intent.ACTION_BATTERY_CHANGED) return null

        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null

        val percentage = ((level.toDouble() / scale.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
        val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

        return BatteryStatus.newBuilder()
            .setChargePercentage(percentage)
            .setIsPluggedIn(plugged != 0)
            .setPowerSourceType(plugged.toPowerSourceType())
            .setEventTimestamp(System.currentTimeMillis())
            .build()
    }

    private fun Int.toPowerSourceType(): PowerSourceType = when (this) {
        BatteryManager.BATTERY_PLUGGED_AC -> PowerSourceType.AC_WALL_OUTLET
        BatteryManager.BATTERY_PLUGGED_USB -> PowerSourceType.USB_COMPUTER_PORT
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSourceType.WIRELESS_QI
        else -> PowerSourceType.UNKNOWN_SOURCE
    }

    private companion object {
        const val BATTERY_DEBOUNCE_MILLIS = 10_000L
    }
}
