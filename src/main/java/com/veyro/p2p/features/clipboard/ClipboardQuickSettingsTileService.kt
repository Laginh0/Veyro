package com.veyro.p2p.features.clipboard

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.veyro.p2p.R
import com.veyro.p2p.settings.EcosystemPreferences

@TargetApi(Build.VERSION_CODES.N)
class ClipboardQuickSettingsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val preferences = EcosystemPreferences(this)
        val ready = preferences.ecosystemEnabled() &&
            preferences.featureSettings().clipboardSync
        qsTile?.apply {
            label = getString(R.string.clipboard_tile_label)
            state = if (ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    if (ready) R.string.clipboard_tile_ready else R.string.clipboard_tile_inactive
                )
            }
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val preferences = EcosystemPreferences(this)
        if (!preferences.featureSettings().clipboardSync) {
            Toast.makeText(this, R.string.clipboard_tile_enable_feature, Toast.LENGTH_SHORT).show()
            return
        }
        if (!preferences.ecosystemEnabled()) {
            Toast.makeText(this, R.string.clipboard_tile_enable_ecosystem, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ClipboardSyncActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
