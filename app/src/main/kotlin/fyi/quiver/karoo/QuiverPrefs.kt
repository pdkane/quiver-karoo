package fyi.quiver.karoo

import android.content.Context

/**
 * Tiny persistence for the paired device token (and the head-unit serial we paired
 * with). The token is the only credential the extension holds — it is minted once
 * on quiver.fyi (Settings → Connect your head unit), claimed here, and then sent as
 * `Authorization: Bearer <token>` on every ride/seed POST.
 */
object QuiverPrefs {
    private const val FILE = "quiver_prefs"
    private const val KEY_TOKEN = "device_token"
    private const val KEY_SERIAL = "paired_serial"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun isPaired(context: Context): Boolean = getToken(context) != null

    fun setToken(context: Context, token: String, serial: String?) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_SERIAL, serial)
            .apply()
    }

    fun getSerial(context: Context): String? =
        prefs(context).getString(KEY_SERIAL, null)

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).remove(KEY_SERIAL).apply()
    }
}
