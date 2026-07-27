package com.kangle.kardleaf.widget

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kangle.kardleaf.data.utils.KardLeafLog

private const val QUICK_ADD_TILE_LOG_TAG = "KardLeafQuickTile"

class QuickAddTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        KardLeafLog.i(
            QUICK_ADD_TILE_LOG_TAG,
            "tile clicked locked=$isLocked secure=$isSecure",
        )
        if (isLocked && isSecure) {
            unlockAndRun { launchQuickAdd() }
        } else {
            launchQuickAdd()
        }
    }

    private fun updateTileState() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun launchQuickAdd() {
        val intent = Intent(this, NoteWidgetQuickAddActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("kardleaf://quick-settings/quick-add")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    REQUEST_QUICK_ADD,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onSuccess {
            KardLeafLog.i(QUICK_ADD_TILE_LOG_TAG, "quick add activity requested")
        }.onFailure { error ->
            KardLeafLog.e(QUICK_ADD_TILE_LOG_TAG, "quick add activity request failed", error)
            runCatching { startActivity(intent) }
                .onFailure { fallbackError ->
                    KardLeafLog.e(
                        QUICK_ADD_TILE_LOG_TAG,
                        "quick add fallback failed",
                        fallbackError,
                    )
                }
        }
    }

    private companion object {
        const val REQUEST_QUICK_ADD = 35_000
    }
}
