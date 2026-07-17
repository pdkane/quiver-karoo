package fyi.quiver.karoo

import android.content.Context

/**
 * A tiny durable queue of finished-ride JSON payloads that haven't been accepted by
 * the backend yet. A ride can end while the Karoo is offline; rather than drop the
 * mileage, we persist it and flush on the next successful connection. The backend
 * dedups on externalId, so re-sending is always safe.
 */
object RideOutbox {
    private const val FILE = "quiver_outbox"
    private const val KEY = "pending_rides"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun add(context: Context, rideJson: String) {
        val next = HashSet(all(context))
        next.add(rideJson)
        prefs(context).edit().putStringSet(KEY, next).apply()
    }

    fun all(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun remove(context: Context, rideJson: String) {
        val next = HashSet(all(context))
        if (next.remove(rideJson)) {
            prefs(context).edit().putStringSet(KEY, next).apply()
        }
    }
}
