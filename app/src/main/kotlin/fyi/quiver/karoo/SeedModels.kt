package fyi.quiver.karoo

import kotlinx.serialization.Serializable

/**
 * JSON payload contracts mirrored from the Quiver backend:
 *   - SeedPayload  ->  POST /api/karoo/seed   (src/lib/karoo/seed-types.ts)
 *   - RidePayload  ->  POST /api/ride/ingest  (src/app/api/ride/ingest/route.ts)
 *
 * Serialized with `explicitNulls = false` (see QuiverApi.json) so absent/null
 * fields are omitted — the backend treats the seed as fill-empty + idempotent.
 */

@Serializable
data class SeedComponent(
    val role: String? = null,
    val serial: String? = null,
    val batteryBand: String? = null,
)

@Serializable
data class SeedDevice(
    val serial: String? = null,
    val name: String? = null,
    val manufacturer: String? = null,
    /** Raw Karoo signal token (POWER, HEART_RATE, RADAR, …) or a canonical BleKind;
     *  the backend normalizes and falls back to 'other'. */
    val kind: String? = null,
    val batteryBand: String? = null,
    val components: List<SeedComponent>? = null,
)

@Serializable
data class SeedBike(
    val externalUid: String,
    val name: String,
    val isDefault: Boolean? = null,
)

@Serializable
data class SeedProfile(
    val weightKg: Double? = null,
    val ftp: Int? = null,
)

@Serializable
data class SeedPayload(
    val bikes: List<SeedBike>? = null,
    val devices: List<SeedDevice>? = null,
    val profile: SeedProfile? = null,
)

@Serializable
data class RidePayload(
    val source: String = "hammerhead",
    val externalId: String,
    val distanceMeters: Double,
    val movingTimeS: Long? = null,
    val rideEndedAt: String? = null,
    val bikeId: String? = null,
)
