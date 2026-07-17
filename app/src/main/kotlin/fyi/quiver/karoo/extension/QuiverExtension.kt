package fyi.quiver.karoo.extension

import fyi.quiver.karoo.BuildConfig
import fyi.quiver.karoo.QuiverApi
import fyi.quiver.karoo.QuiverPrefs
import fyi.quiver.karoo.RideOutbox
import fyi.quiver.karoo.RidePayload
import fyi.quiver.karoo.SeedMapper
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.Bikes
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Quiver background service. Bound by the Karoo System for the life of the app,
 * it connects a [KarooSystemService] and:
 *   1. Watches [RideState]; when a ride finishes it POSTs the distance to Quiver's
 *      ride-ingest endpoint (via a durable [RideOutbox]) so wear/maintenance clocks
 *      advance automatically — replacing the manual FIT backfill.
 *   2. On each connection, flushes any queued rides and refreshes the garage seed.
 *
 * v1 declares no data types and does not scan — it is purely an observer + sync.
 */
class QuiverExtension : KarooExtension("quiver", BuildConfig.VERSION_NAME) {

    private lateinit var karooSystem: KarooSystemService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val consumerIds = mutableListOf<String>()

    // Ride tracking (single ride at a time).
    @Volatile private var rideActive = false
    @Volatile private var rideDistanceMeters = 0.0
    @Volatile private var rideStartMillis = 0L
    @Volatile private var rideExternalId: String? = null

    // Latest at-rest garage snapshot, for the seed.
    @Volatile private var latestBikes: List<Bikes.Bike> = emptyList()
    @Volatile private var latestDevices: List<SavedDevices.SavedDevice> = emptyList()
    @Volatile private var latestProfile: UserProfile? = null
    @Volatile private var seededThisConnection = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "QuiverExtension onCreate")
        karooSystem = KarooSystemService(applicationContext)

        karooSystem.connect { connected ->
            Log.d(TAG, "Karoo connected=$connected")
            if (connected) {
                seededThisConnection = false
                scope.launch {
                    flushPendingRides()
                    maybeSeed()
                }
            }
        }

        consumerIds += karooSystem.addConsumer { state: RideState -> onRideState(state) }
        consumerIds += karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.DISTANCE),
        ) { event: OnStreamState -> onDistance(event.state) }
        consumerIds += karooSystem.addConsumer { event: Bikes -> latestBikes = event.bikes }
        consumerIds += karooSystem.addConsumer { event: SavedDevices -> latestDevices = event.devices }
        consumerIds += karooSystem.addConsumer { event: UserProfile -> latestProfile = event }
    }

    override fun onDestroy() {
        Log.d(TAG, "QuiverExtension onDestroy")
        consumerIds.forEach { karooSystem.removeConsumer(it) }
        consumerIds.clear()
        runCatching { karooSystem.disconnect() }
        scope.cancel()
        super.onDestroy()
    }

    private fun onDistance(state: StreamState) {
        val meters = (state as? StreamState.Streaming)?.dataPoint?.singleValue ?: return
        // Distance is monotonic within a ride; guard against a spurious 0 at ride end
        // clobbering the accumulated total (RideState.Idle can race the stream reset).
        if (meters > rideDistanceMeters) rideDistanceMeters = meters
    }

    private fun onRideState(state: RideState) {
        when (state) {
            is RideState.Recording -> {
                if (!rideActive) {
                    rideActive = true
                    rideDistanceMeters = 0.0
                    rideStartMillis = System.currentTimeMillis()
                    rideExternalId = "karoo-${karooSystem.serial ?: "unknown"}-$rideStartMillis"
                    Log.d(TAG, "Ride started ($rideExternalId)")
                }
            }
            is RideState.Paused -> Unit // stay active
            is RideState.Idle -> {
                if (rideActive) {
                    rideActive = false
                    finishRide(rideDistanceMeters, rideExternalId)
                    rideDistanceMeters = 0.0
                    rideExternalId = null
                }
            }
        }
    }

    private fun finishRide(distanceMeters: Double, externalId: String?) {
        if (externalId == null) return
        if (distanceMeters < MIN_RIDE_METERS) {
            Log.d(TAG, "Ride $externalId below threshold (${distanceMeters}m); skipping")
            return
        }
        // Only queue when paired — an unpaired head unit has no account to sync to.
        if (!QuiverPrefs.isPaired(applicationContext)) {
            Log.d(TAG, "Ride $externalId finished but not paired; skipping")
            return
        }
        val payload = RidePayload(
            source = "hammerhead",
            externalId = externalId,
            distanceMeters = distanceMeters,
            rideEndedAt = iso8601(System.currentTimeMillis()),
        )
        val ride = QuiverApi.json.encodeToString(payload)
        RideOutbox.add(applicationContext, ride)
        Log.d(TAG, "Ride $externalId queued (${distanceMeters}m)")
        scope.launch { flushPendingRides() }
    }

    private suspend fun flushPendingRides() {
        val token = QuiverPrefs.getToken(applicationContext) ?: return
        val pending = RideOutbox.all(applicationContext)
        if (pending.isEmpty()) return
        for (rideJson in pending) {
            val payload = runCatching {
                QuiverApi.json.decodeFromString(RidePayload.serializer(), rideJson)
            }.getOrNull() ?: run {
                RideOutbox.remove(applicationContext, rideJson) // unparseable, drop
                continue
            }
            val accepted = QuiverApi.ingestRide(karooSystem, token, payload)
            if (accepted) {
                RideOutbox.remove(applicationContext, rideJson)
                Log.d(TAG, "Ride ${payload.externalId} synced")
            } else {
                Log.d(TAG, "Ride ${payload.externalId} not accepted; will retry")
            }
        }
    }

    private suspend fun maybeSeed() {
        if (seededThisConnection) return
        val token = QuiverPrefs.getToken(applicationContext) ?: return
        val payload = SeedMapper.build(latestBikes, latestDevices, latestProfile)
        if (payload.bikes == null && payload.devices == null && payload.profile == null) return
        val ok = QuiverApi.seed(karooSystem, token, payload)
        if (ok) {
            seededThisConnection = true
            Log.d(TAG, "Garage seed synced")
        }
    }

    companion object {
        private const val TAG = "QuiverExtension"

        /** Ignore sub-100 m "rides" (a bump into the record button, a paused test). */
        private const val MIN_RIDE_METERS = 100.0

        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private fun iso8601(millis: Long): String = ISO.format(Date(millis))
    }
}
