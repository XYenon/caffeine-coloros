package bid.xyenon.caffeine.coloros.core

object CaffeineConfig {
    const val APPLICATION_PACKAGE = "bid.xyenon.caffeine.coloros"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    const val PREFS_NAME = "caffeine_prefs"
    const val KEY_SCREEN_OFF_RESET = "screen_off_reset"
    const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
    const val KEY_DURATIONS = "durations"
    const val KEY_MODULE_ACTIVE = "module_active"

    const val INFINITY_DURATION = -1
    const val OFF_DURATION = 0

    // Standard sequence: 5 min, 10 min, 30 min, 1 hour, Infinity
    val DEFAULT_DURATIONS = intArrayOf(
        5 * 60,      // 5 min
        10 * 60,     // 10 min
        30 * 60,     // 30 min
        60 * 60,     // 1 hour
        INFINITY_DURATION // ∞ (Infinity / No Timeout)
    )

    const val ACTION_STATE_CHANGED = "bid.xyenon.caffeine.coloros.ACTION_STATE_CHANGED"
    const val ACTION_STATE_REQUEST = "bid.xyenon.caffeine.coloros.ACTION_STATE_REQUEST"
    const val ACTION_HOOK_PING = "bid.xyenon.caffeine.coloros.ACTION_HOOK_PING"
    const val ACTION_HOOK_PONG = "bid.xyenon.caffeine.coloros.ACTION_HOOK_PONG"
    const val EXTRA_IS_ACTIVE = "is_active"
    const val EXTRA_SECONDS_REMAINING = "seconds_remaining"
    const val EXTRA_DURATION = "duration"
    const val EXTRA_REQUEST_TOKEN = "request_token"
}
