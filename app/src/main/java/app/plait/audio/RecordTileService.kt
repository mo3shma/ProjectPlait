package app.plait.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.plait.MainActivity
import app.plait.ui.WidgetLaunch

/** Quick Settings tile: opens the app on the record screen, the same way the widget's voice button does. */
class RecordTileService : TileService() {
    override fun onStartListening() {
        // shortcut: always inactive; the recording state lives in a per-screen controller, not somewhere a tile can read.
        qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        unlockAndRun {
            val intent = Intent(this, MainActivity::class.java).setAction(WidgetLaunch.ACTION)
                .putExtra(WidgetLaunch.EXTRA_KIND, "voice").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(intent)
            }
        }
    }
}
