package bid.xyenon.caffeine.coloros.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import bid.xyenon.caffeine.coloros.R
import bid.xyenon.caffeine.coloros.core.CaffeineEngine
import bid.xyenon.caffeine.coloros.core.TimeFormatter
import bid.xyenon.caffeine.coloros.ui.MainActivity

class CaffeineForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "caffeine_notification_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, remainingSeconds: Int) {
            val intent = Intent(context, CaffeineForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CaffeineForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var engine: CaffeineEngine

    private val stateListener = object : CaffeineEngine.StateListener {
        override fun onStateChanged(isActive: Boolean, duration: Int, secondsRemaining: Int) {
            if (!isActive) {
                stopSelf()
            } else {
                updateNotification(secondsRemaining)
            }
        }

        override fun onTick(secondsRemaining: Int, formattedTime: String) {
            updateNotification(secondsRemaining)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        engine = CaffeineEngine.getInstance(this)
        engine.addListener(stateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!engine.isActive) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification(engine.secondsRemaining)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.removeListener(stateListener)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows remaining caffeine wake time"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(remainingSeconds: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timeStr = if (engine.isInfinite) {
            getString(R.string.tile_state_infinite)
        } else {
            TimeFormatter.formatDuration(remainingSeconds)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, timeStr))
            .setSmallIcon(R.drawable.ic_caffeine_tile)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(remainingSeconds: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(remainingSeconds))
    }
}
