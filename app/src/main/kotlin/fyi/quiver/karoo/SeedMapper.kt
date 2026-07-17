package fyi.quiver.karoo

import io.hammerhead.karooext.models.Bikes
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.UserProfile

/**
 * Pure translation from the head unit's at-rest state (Bikes / SavedDevices /
 * UserProfile) into the thin [SeedPayload] the backend expects. Intentionally does
 * NO enrichment — Quiver's seed route owns the token→enum normalization; we just
 * forward raw Karoo values (and canonical ones pass through unchanged).
 */
object SeedMapper {

    fun build(
        bikes: List<Bikes.Bike>,
        devices: List<SavedDevices.SavedDevice>,
        profile: UserProfile?,
    ): SeedPayload = SeedPayload(
        bikes = bikes.map { bike ->
            SeedBike(
                externalUid = bike.id,
                name = bike.name,
                isDefault = bike.name.equals("Default", ignoreCase = true),
            )
        }.ifEmpty { null },
        devices = devices.map { it.toSeedDevice() }.ifEmpty { null },
        profile = profile?.toSeedProfile(),
    )

    private fun SavedDevices.SavedDevice.toSeedDevice(): SeedDevice = SeedDevice(
        serial = details.serialNumber,
        name = name,
        manufacturer = details.manufacturer,
        kind = kindToken(supportedDataTypes),
        batteryBand = details.lastBattery?.name,
        components = components?.map { (role, detail) ->
            SeedComponent(
                role = role,
                serial = detail.serialNumber,
                batteryBand = detail.lastBattery?.name,
            )
        }?.ifEmpty { null },
    )

    /**
     * The ride fingerprint: every saved sensor that carries a serial, as
     * [RideDevice]s. The backend matches these serials to a bike, so attaching them
     * to a finished ride is what lets null-bikeId mileage reach the right bike.
     */
    fun rideDevices(devices: List<SavedDevices.SavedDevice>): List<RideDevice>? =
        devices.mapNotNull { d ->
            val serial = d.details.serialNumber?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RideDevice(serial = serial, name = d.name, kind = canonicalKind(d.supportedDataTypes))
        }.ifEmpty { null }

    /** Best-effort canonical Quiver BleKind for a sensor (cosmetic; serial drives
     *  attribution). */
    private fun canonicalKind(supportedDataTypes: List<String>): String {
        val ids = supportedDataTypes.map { it.uppercase() }
        fun any(vararg needles: String) = ids.any { id -> needles.any { id.contains(it) } }
        return when {
            any("RADAR") -> "radar"
            any("SHIFTING") -> "shifting"
            any("POWER") -> "power-meter"
            any("HEART_RATE", "_HR_", "HR_ID") -> "heart-rate"
            any("CAD") -> "cadence"
            any("SPEED") -> "speed"
            else -> "other"
        }
    }

    private fun UserProfile.toSeedProfile(): SeedProfile? {
        val kg = weight.takeIf { it > 0f }?.toDouble()
        val watts = ftp.takeIf { it > 0 }
        return if (kg == null && watts == null) null else SeedProfile(weightKg = kg, ftp = watts)
    }

    /** Best-effort primary signal token from a sensor's supported data types. The
     *  backend maps unknown tokens to 'other', so a miss is harmless. */
    private fun kindToken(supportedDataTypes: List<String>): String? {
        val ids = supportedDataTypes.map { it.uppercase() }
        fun any(vararg needles: String) = ids.any { id -> needles.any { id.contains(it) } }
        return when {
            any("RADAR") -> "RADAR"
            any("SHIFTING") -> "SHIFTING"
            any("POWER") -> "POWER"
            any("HEART_RATE", "_HR_", "HR_ID") -> "HEART_RATE"
            any("CAD") -> "CADENCE"
            any("SPEED") -> "SPEED"
            else -> null
        }
    }
}
