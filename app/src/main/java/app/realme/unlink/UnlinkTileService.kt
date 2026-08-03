package app.realme.unlink

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings entrypoint. Tap and long-press both open the app; no control logic on the tile.
 * The tile is available in the add-tile picker after install and is not auto-pinned.
 */
class UnlinkTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Realme Unlink"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Buds controls"
            }
            updateTile()
        }
    }

    override fun onClick() {
        openApp()
    }

    // Long-press uses the default TileService behavior of opening the app's settings/panel
    // intent; with no launcher activity preferences, we also route explicitly via onClick path.
    // On modern Android, long-press opens the app info unless we handle it — lock both to open.

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startActivityAndCollapse(pendingIntent)
    }

    private companion object {
        const val REQUEST_OPEN = 1
    }
}
