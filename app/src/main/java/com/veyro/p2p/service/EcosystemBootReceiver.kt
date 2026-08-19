package com.veyro.p2p.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.veyro.p2p.settings.EcosystemPreferences

class EcosystemBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        if (!EcosystemPreferences(context).ecosystemEnabled()) return

        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, P2PTransferService::class.java)
                    .setAction(P2PTransferService.ACTION_START_ECOSYSTEM)
            )
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
