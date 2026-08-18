package bid.xyenon.caffeine.coloros.hook

import android.util.Log

object SystemServerHook {
    private const val TAG = "Caffeine:SystemServerHook"

    fun init(classLoader: ClassLoader) {
        Log.i(TAG, "Initializing SystemServer Hook for OxygenOS / ColorOS...")
        // Optional system_server optimizations can be placed here
    }
}
