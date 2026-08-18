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

class CaffeineEngine(context: Context, initiallyOwnsState: Boolean) {

    companion object {
        private const val TAG = "CaffeineEngine"
        private const val WAKE_LOCK_TAG = "Caffeine:ScreenWakeLock"

        const val EXTRA_SENDER_PID = "sender_pid"

        @SuppressLint("StaticFieldLeak") // The engine stores applicationContext only.
        @Volatile
        private var instance: CaffeineEngine? = null

        fun getInstance(context: Context, ownsState: Boolean = true): CaffeineEngine {
            return instance ?: synchronized(this) {
                instance ?: CaffeineEngine(context, ownsState).also { instance = it }
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
    @Volatile
    private var ownsState = initiallyOwnsState

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
                    finishCountdown()
                }
            } else {
                finishCountdown()
            }
        }
    }

    private fun finishCountdown() {
        if (ownsState) {
            Log.d(TAG, "Countdown reached 0, deactivating Caffeine")
            deactivate()
        } else {
            currentDuration = CaffeineConfig.OFF_DURATION
            secondsRemaining = 0
            notifyStateListeners()
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
                val senderPid = intent?.getIntExtra(EXTRA_SENDER_PID, 0) ?: return
                if (senderPid == Process.myPid()) return

                when (intent.action) {
                    CaffeineConfig.ACTION_STATE_CHANGED -> {
                        val duration = intent.getIntExtra(CaffeineConfig.EXTRA_DURATION, CaffeineConfig.OFF_DURATION)
                        val remaining = intent.getIntExtra(CaffeineConfig.EXTRA_SECONDS_REMAINING, 0)
                        Log.d(TAG, "Received IPC sync state: duration=$duration, remaining=$remaining from PID=$senderPid")
                        syncFromRemote(duration, remaining)
                    }
                    CaffeineConfig.ACTION_STATE_REQUEST -> {
                        Log.d(TAG, "Received IPC state request from PID=$senderPid")
                        if (ownsState) broadcastState()
                    }
                    CaffeineConfig.ACTION_HOOK_PING -> {
                        val token = intent.getStringExtra(CaffeineConfig.EXTRA_REQUEST_TOKEN)
                        if (ownsState && context.packageName == CaffeineConfig.SYSTEM_UI_PACKAGE && token != null) {
                            val response = Intent(CaffeineConfig.ACTION_HOOK_PONG).apply {
                                setPackage(CaffeineConfig.APPLICATION_PACKAGE)
                                putExtra(CaffeineConfig.EXTRA_REQUEST_TOKEN, token)
                                putExtra(EXTRA_SENDER_PID, Process.myPid())
                            }
                            context.sendBroadcast(response)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in ipcReceiver", t)
            }
        }
    }

    private var ipcReceiverRegistered = false
    private var screenReceiverRegistered = false

    init {
        registerReceivers()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // The flags overload is unavailable before API 33.
    private fun registerReceivers() {
        registerScreenReceiverIfNeeded()
        if (!ipcReceiverRegistered) {
            try {
                val ipcFilter = IntentFilter().apply {
                    addAction(CaffeineConfig.ACTION_STATE_CHANGED)
                    addAction(CaffeineConfig.ACTION_STATE_REQUEST)
                    addAction(CaffeineConfig.ACTION_HOOK_PING)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(ipcReceiver, ipcFilter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(ipcReceiver, ipcFilter)
                }
                ipcReceiverRegistered = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to register IPC receiver", t)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // The flags overload is unavailable before API 33.
    private fun registerScreenReceiverIfNeeded() {
        if (!ownsState || screenReceiverRegistered) return
        try {
            val screenFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(screenReceiver, screenFilter)
            }
            screenReceiverRegistered = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register screen receiver", t)
        }
    }

    @Synchronized
    fun setOwnsState(value: Boolean) {
        if (ownsState == value) return

        ownsState = value
        handler.removeCallbacks(countdownRunnable)
        if (value) {
            registerScreenReceiverIfNeeded()
            if (isActive) acquireWakeLock()
        } else {
            releaseWakeLock()
            if (screenReceiverRegistered) {
                runCatching { context.unregisterReceiver(screenReceiver) }
                screenReceiverRegistered = false
            }
        }
        scheduleCountdownIfNeeded()
    }

    fun addListener(listener: StateListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
                listener.onStateChanged(isActive, currentDuration, secondsRemaining)
            }
        }
        if (!ownsState) scheduleCountdownIfNeeded()
    }

    fun removeListener(listener: StateListener) {
        synchronized(listeners) {
            listeners.remove(listener)
            if (!ownsState && listeners.isEmpty()) {
                handler.removeCallbacks(countdownRunnable)
            }
        }
    }

    fun requestStateSync() {
        try {
            val intent = Intent(CaffeineConfig.ACTION_STATE_REQUEST).apply {
                putExtra(EXTRA_SENDER_PID, Process.myPid())
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to request IPC state sync", t)
        }
    }

    private fun notifyStateChanged() {
        notifyStateListeners()
        broadcastState()
    }

    private fun notifyStateListeners() {
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
            if (ownsState) releaseWakeLock()
        } else if (ownsState) {
            acquireWakeLock()
        }

        scheduleCountdownIfNeeded()
        notifyStateListeners()
    }

    private fun scheduleCountdownIfNeeded() {
        if (!isActive || isInfinite || secondsRemaining <= 0) return
        val shouldRun = ownsState || synchronized(listeners) { listeners.isNotEmpty() }
        if (shouldRun) {
            handler.removeCallbacks(countdownRunnable)
            handler.postDelayed(countdownRunnable, 1000L)
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
            if (ownsState) releaseWakeLock()
            Log.d(TAG, "Caffeine deactivated")
        } else {
            currentDuration = durationSeconds
            secondsRemaining = if (durationSeconds == CaffeineConfig.INFINITY_DURATION) -1 else durationSeconds
            if (ownsState) acquireWakeLock()
            scheduleCountdownIfNeeded()
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
                wakeLock = pm?.newWakeLock(PowerManager.FULL_WAKE_LOCK, WAKE_LOCK_TAG)
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
        if (ipcReceiverRegistered) {
            try {
                context.unregisterReceiver(ipcReceiver)
            } catch (t: Throwable) {
                // Ignore
            }
            ipcReceiverRegistered = false
        }
        if (screenReceiverRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (t: Throwable) {
                // Ignore
            }
            screenReceiverRegistered = false
        }
    }
}
