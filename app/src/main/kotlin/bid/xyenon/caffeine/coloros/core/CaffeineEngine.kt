package bid.xyenon.caffeine.coloros.core

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log

class CaffeineEngine(context: Context) {

    companion object {
        private const val TAG = "CaffeineEngine"
        private const val WAKE_LOCK_TAG = "Caffeine:ScreenWakeLock"

        const val EXTRA_SENDER_PID = "sender_pid"

        @SuppressLint("StaticFieldLeak") // The engine stores applicationContext only.
        @Volatile
        private var instance: CaffeineEngine? = null

        fun getInstance(context: Context): CaffeineEngine {
            return instance ?: synchronized(this) {
                instance ?: CaffeineEngine(context).also { instance = it }
            }
        }
    }

    interface StateListener {
        fun onStateChanged(isActive: Boolean, duration: Int, secondsRemaining: Int)
        fun onTick(secondsRemaining: Int, formattedTime: String)
    }

    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val listeners = mutableListOf<StateListener>()

    var durations: IntArray = CaffeineConfig.DEFAULT_DURATIONS
    var resetOnScreenOff: Boolean = true

    var currentDuration: Int = CaffeineConfig.OFF_DURATION
        private set

    var secondsRemaining: Int = 0
        private set

    val isActive: Boolean
        get() = currentDuration != CaffeineConfig.OFF_DURATION

    val isInfinite: Boolean
        get() = currentDuration == CaffeineConfig.INFINITY_DURATION

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!isActive || isInfinite) return

            if (secondsRemaining > 0) {
                secondsRemaining--
                val formatted = TimeFormatter.formatDuration(secondsRemaining)
                notifyTick(secondsRemaining, formatted)
                if (secondsRemaining > 0) {
                    handler.postDelayed(this, 1000L)
                } else {
                    Log.d(TAG, "Countdown reached 0, deactivating Caffeine")
                    deactivate()
                }
            } else {
                deactivate()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            try {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    Log.d(TAG, "Received ACTION_SCREEN_OFF")
                    if (resetOnScreenOff && isActive) {
                        Log.d(TAG, "Auto-deactivating Caffeine on screen off")
                        deactivate()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in screenReceiver", t)
            }
        }
    }

    private val ipcReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            try {
                if (intent?.action == CaffeineConfig.ACTION_STATE_CHANGED) {
                    val senderPid = intent.getIntExtra(EXTRA_SENDER_PID, 0)
                    if (senderPid != Process.myPid()) {
                        val duration = intent.getIntExtra(CaffeineConfig.EXTRA_DURATION, CaffeineConfig.OFF_DURATION)
                        val remaining = intent.getIntExtra(CaffeineConfig.EXTRA_SECONDS_REMAINING, 0)
                        Log.d(TAG, "Received IPC sync state: duration=$duration, remaining=$remaining from PID=$senderPid")
                        syncFromRemote(duration, remaining)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in ipcReceiver", t)
            }
        }
    }

    private var isReceiverRegistered = false

    init {
        registerReceivers()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // The flags overload is unavailable before API 33.
    private fun registerReceivers() {
        if (!isReceiverRegistered) {
            try {
                // 1. System broadcast receiver for screen off
                val screenFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(screenReceiver, screenFilter)
                }

                // 2. Custom IPC broadcast receiver for state sync
                val ipcFilter = IntentFilter(CaffeineConfig.ACTION_STATE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(ipcReceiver, ipcFilter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(ipcReceiver, ipcFilter)
                }
                isReceiverRegistered = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to register receivers", t)
            }
        }
    }

    fun addListener(listener: StateListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
                listener.onStateChanged(isActive, currentDuration, secondsRemaining)
            }
        }
    }

    fun removeListener(listener: StateListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyStateChanged() {
        synchronized(listeners) {
            val list = ArrayList(listeners)
            for (l in list) {
                try {
                    l.onStateChanged(isActive, currentDuration, secondsRemaining)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in onStateChanged listener", t)
                }
            }
        }
        broadcastState()
    }

    private fun notifyTick(sec: Int, formatted: String) {
        synchronized(listeners) {
            val list = ArrayList(listeners)
            for (l in list) {
                try {
                    l.onTick(sec, formatted)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in onTick listener", t)
                }
            }
        }
    }

    private fun broadcastState() {
        try {
            val intent = Intent(CaffeineConfig.ACTION_STATE_CHANGED).apply {
                putExtra(CaffeineConfig.EXTRA_IS_ACTIVE, isActive)
                putExtra(CaffeineConfig.EXTRA_DURATION, currentDuration)
                putExtra(CaffeineConfig.EXTRA_SECONDS_REMAINING, secondsRemaining)
                putExtra(EXTRA_SENDER_PID, Process.myPid())
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            // Ignore broadcast failure
        }
    }

    private fun syncFromRemote(duration: Int, remaining: Int) {
        handler.removeCallbacks(countdownRunnable)
        currentDuration = duration
        secondsRemaining = remaining

        if (duration == CaffeineConfig.OFF_DURATION) {
            releaseWakeLock()
        } else {
            acquireWakeLock()
            if (!isInfinite && secondsRemaining > 0) {
                handler.postDelayed(countdownRunnable, 1000L)
            }
        }

        synchronized(listeners) {
            val list = ArrayList(listeners)
            for (l in list) {
                try {
                    l.onStateChanged(isActive, currentDuration, secondsRemaining)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in sync listener", t)
                }
            }
        }
    }

    /**
     * Cycles to the next duration in the sequence:
     * OFF -> 5m -> 10m -> 30m -> ∞ -> OFF
     */
    @Synchronized
    fun cycleNext(): Int {
        val nextDuration = if (!isActive) {
            durations.firstOrNull() ?: CaffeineConfig.DEFAULT_DURATIONS[0]
        } else {
            val currentIndex = durations.indexOf(currentDuration)
            if (currentIndex >= 0 && currentIndex < durations.size - 1) {
                durations[currentIndex + 1]
            } else {
                CaffeineConfig.OFF_DURATION
            }
        }

        setDuration(nextDuration)
        return nextDuration
    }

    @Synchronized
    fun setDuration(durationSeconds: Int) {
        handler.removeCallbacks(countdownRunnable)

        if (durationSeconds == CaffeineConfig.OFF_DURATION) {
            currentDuration = CaffeineConfig.OFF_DURATION
            secondsRemaining = 0
            releaseWakeLock()
            Log.d(TAG, "Caffeine deactivated")
        } else {
            currentDuration = durationSeconds
            secondsRemaining = if (durationSeconds == CaffeineConfig.INFINITY_DURATION) -1 else durationSeconds
            acquireWakeLock()
            if (!isInfinite) {
                handler.postDelayed(countdownRunnable, 1000L)
            }
            Log.d(TAG, "Caffeine activated with duration: $durationSeconds seconds")
        }

        notifyStateChanged()
    }

    fun deactivate() {
        setDuration(CaffeineConfig.OFF_DURATION)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                val flags = PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE
                wakeLock = pm?.newWakeLock(flags, WAKE_LOCK_TAG)
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
                Log.d(TAG, "WakeLock acquired successfully in process ${Process.myPid()}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire WakeLock", t)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released in process ${Process.myPid()}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to release WakeLock", t)
        }
    }

    fun cleanup() {
        deactivate()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(ipcReceiver)
            } catch (t: Throwable) {
                // Ignore
            }
            isReceiverRegistered = false
        }
    }
}
