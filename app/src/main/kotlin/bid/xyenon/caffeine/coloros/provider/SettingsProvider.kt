package bid.xyenon.caffeine.coloros.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import bid.xyenon.caffeine.coloros.core.CaffeineConfig

class SettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "bid.xyenon.caffeine.coloros.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")

        const val METHOD_GET_PREFS = "getPrefs"
        const val METHOD_SET_PREF = "setPref"
        const val METHOD_CHECK_ACTIVE = "checkActive"
    }

    private fun getPrefs(): SharedPreferences? {
        return context?.getSharedPreferences(CaffeineConfig.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val result = Bundle()
        val prefs = getPrefs()

        when (method) {
            METHOD_GET_PREFS -> {
                if (prefs != null) {
                    result.putBoolean(CaffeineConfig.KEY_SCREEN_OFF_RESET, prefs.getBoolean(CaffeineConfig.KEY_SCREEN_OFF_RESET, true))
                    result.putBoolean(CaffeineConfig.KEY_HAPTIC_FEEDBACK, prefs.getBoolean(CaffeineConfig.KEY_HAPTIC_FEEDBACK, true))
                    result.putString(CaffeineConfig.KEY_DURATIONS, prefs.getString(CaffeineConfig.KEY_DURATIONS, null))
                }
            }
            METHOD_SET_PREF -> {
                if (prefs != null && extras != null && arg != null) {
                    val editor = prefs.edit()
                    when (arg) {
                        CaffeineConfig.KEY_SCREEN_OFF_RESET -> editor.putBoolean(arg, extras.getBoolean("value", true))
                        CaffeineConfig.KEY_HAPTIC_FEEDBACK -> editor.putBoolean(arg, extras.getBoolean("value", true))
                        CaffeineConfig.KEY_DURATIONS -> editor.putString(arg, extras.getString("value"))
                    }
                    editor.apply()
                    result.putBoolean("success", true)
                }
            }
            METHOD_CHECK_ACTIVE -> {
                result.putBoolean("active", true)
            }
        }
        return result
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
