package com.veyro.p2p.features.clipboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.veyro.p2p.R
import com.veyro.p2p.service.P2PTransferService

class ClipboardSyncActivity : Activity() {
    private var dispatched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(1, 1)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || dispatched) return
        dispatched = true
        startService(
            Intent(this, P2PTransferService::class.java)
                .setAction(P2PTransferService.ACTION_SYNC_CLIPBOARD)
        )
        Toast.makeText(this, R.string.clipboard_tile_sync_requested, Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed({ finishAndRemoveTask() }, FINISH_DELAY_MILLIS)
    }

    private companion object {
        const val FINISH_DELAY_MILLIS = 700L
    }
}
