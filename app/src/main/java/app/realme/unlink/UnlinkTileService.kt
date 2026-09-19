package app.realme.unlink

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings entrypoint. Tapping opens the app; no control logic runs on the tile.
 * The tile is available in the add-tile picker after install and is not auto-pinned.
 */
class UnlinkTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            subtitle = "Buds controls"
            activityLaunchForClick = createLaunchIntent()
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) {
            unlockAndRun {
                openApp()
            }
        } else {
            openApp()
        }
    }

    private fun createLaunchIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val options = ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
            )
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options.toBundle(),
        )
    }

    private fun openApp() {
        startActivityAndCollapse(createLaunchIntent())
    }

    private companion object {
        const val REQUEST_OPEN = 1
    }
}
