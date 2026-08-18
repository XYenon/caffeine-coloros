package bid.xyenon.caffeine.coloros.core

import java.util.Locale

object TimeFormatter {
    fun formatDuration(seconds: Int, offString: String = "Off", infinityString: String = "∞"): String {
        return when {
            seconds == CaffeineConfig.OFF_DURATION -> offString
            seconds == CaffeineConfig.INFINITY_DURATION -> infinityString
            seconds < 60 -> String.format(Locale.getDefault(), "0:%02d", seconds)
            seconds < 3600 -> String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
            else -> String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        }
    }

    fun formatDurationMinutes(seconds: Int): String {
        return when {
            seconds == CaffeineConfig.OFF_DURATION -> "Off"
            seconds == CaffeineConfig.INFINITY_DURATION -> "∞"
            seconds >= 60 -> "${seconds / 60}m"
            else -> "${seconds}s"
        }
    }
}
