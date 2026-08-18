package bid.xyenon.caffeine.coloros.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import bid.xyenon.caffeine.coloros.R
import bid.xyenon.caffeine.coloros.core.CaffeineConfig
import bid.xyenon.caffeine.coloros.core.CaffeineEngine
import bid.xyenon.caffeine.coloros.core.TimeFormatter

class CaffeineTileService : TileService() {

    companion object {
        private const val TAG = "CaffeineTileService"
    }

    private lateinit var engine: CaffeineEngine

    private val stateListener = object : CaffeineEngine.StateListener {
        override fun onStateChanged(isActive: Boolean, duration: Int, secondsRemaining: Int) {
            updateTileState()
        }

        override fun onTick(secondsRemaining: Int, formattedTime: String) {
            updateTileState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = CaffeineEngine.getInstance(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        engine.addListener(stateListener)
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "onStopListening")
        engine.removeListener(stateListener)
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick in TileService")
        engine.cycleNext()
        updateTileState()

        // In fallback standalone mode, start/stop foreground service
        if (engine.isActive) {
            CaffeineForegroundService.start(this, engine.secondsRemaining)
        } else {
            CaffeineForegroundService.stop(this)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isActive = engine.isActive
        val duration = engine.currentDuration
        val remaining = engine.secondsRemaining

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        
        val baseLabel = getString(R.string.tile_caffeine)
        val subtitle = when {
            !isActive -> getString(R.string.tile_state_off)
            engine.isInfinite -> getString(R.string.tile_state_infinite)
            else -> TimeFormatter.formatDuration(remaining)
        }

        // On OxygenOS / ColorOS, 1x1 circular tiles only render 'label'.
        // We include the countdown in label when active so it is always visible.
        tile.label = if (isActive) "$baseLabel ($subtitle)" else baseLabel

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }

        val iconRes = if (isActive) R.drawable.ic_caffeine_full else R.drawable.ic_caffeine_empty
        tile.icon = Icon.createWithResource(this, iconRes)

        tile.updateTile()
    }
}
