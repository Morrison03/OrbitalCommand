package com.example.orbitalcommand.model

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Geographical coordinates of a map marker, in degrees. */
data class OrbitGeoCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Invalid latitude: $latitude" }
        require(latitude.isFinite() && longitude.isFinite())
    }
}

/**
 * Resolved marker positions for one target satellite and SERVICER-01.
 * A predicted coordinate is not a received telemetry measurement.
 */
data class DockedServicerMapMarkers(
    val satelliteMarker: OrbitGeoCoordinate,
    val servicerMarker: OrbitGeoCoordinate?,
    val satelliteTelemetryFrozen: Boolean,
    val servicerPositionPredicted: Boolean,
    val physicallyAttached: Boolean
)

/**
 * Maintains the last two RECEIVED positions to estimate a short outage's
 * motion along the previously observed great-circle track.
 *
 * Prefer the simulation's current physical/orbital position when available;
 * this geometric estimate is a fallback for a telemetry-frozen model.
 */
class DockedServicerMapTracking {
    private data class Observation(
        val seconds: Double,
        val coordinate: OrbitGeoCoordinate
    )

    private data class History(
        val previous: Observation? = null,
        val latest: Observation? = null
    )

    private val history = mutableMapOf<String, History>()

    /**
     * Call this once per updated map frame for the target satellite.
     *
     * [reportedSatellite] is the last observed marker position, or the
     * current measurement while linked. [physicalSatellite] should come
     * from the running orbital propagator even during communications loss;
     * pass null if the available source stops updating during an outage.
     *
     * For undocked missions the existing map positioning is unchanged.
     */
    @Synchronized
    fun resolve(
        satelliteId: String,
        elapsedSeconds: Double,
        telemetryAvailable: Boolean,
        reportedSatellite: OrbitGeoCoordinate,
        normalServicer: OrbitGeoCoordinate?,
        physicalSatellite: OrbitGeoCoordinate? = null,
        attachedToThisSatellite: Boolean =
            ServicerAttachmentRegistry.isAttachedTo(satelliteId)
    ): DockedServicerMapMarkers {
        if (telemetryAvailable) {
            record(satelliteId, elapsedSeconds, reportedSatellite)
        }

        // An undocked servicer uses the existing navigation/map behavior.
        if (!attachedToThisSatellite) {
            return DockedServicerMapMarkers(
                satelliteMarker = reportedSatellite,
                servicerMarker = normalServicer,
                satelliteTelemetryFrozen = false,
                servicerPositionPredicted = false,
                physicallyAttached = false
            )
        }

        if (telemetryAvailable) {
            // Both bodies must occupy the same map location after recovery.
            return DockedServicerMapMarkers(
                satelliteMarker = reportedSatellite,
                servicerMarker = reportedSatellite,
                satelliteTelemetryFrozen = false,
                servicerPositionPredicted = false,
                physicallyAttached = true
            )
        }

        val lastReport = history[satelliteId]?.latest?.coordinate
            ?: reportedSatellite

        // Do NOT use a stale reported position as the moving servicer's
        // trajectory. The physical orbit or an explicit estimate is required.
        val predicted = physicalSatellite
            ?: estimate(satelliteId, elapsedSeconds)
            ?: lastReport

        return DockedServicerMapMarkers(
            satelliteMarker = lastReport,
            servicerMarker = predicted,
            satelliteTelemetryFrozen = true,
            servicerPositionPredicted = true,
            physicallyAttached = true
        )
    }

    @Synchronized
    fun clear() = history.clear()

    private fun record(
        satelliteId: String,
        seconds: Double,
        position: OrbitGeoCoordinate
    ) {
        require(seconds.isFinite())
        val prior = history[satelliteId] ?: History()
        val latest = prior.latest
        if (latest != null && seconds <= latest.seconds) return
        history[satelliteId] = History(previous = latest, latest = Observation(seconds, position))
    }

    private fun estimate(
        satelliteId: String,
        seconds: Double
    ): OrbitGeoCoordinate? {
        val readings = history[satelliteId] ?: return null
        val a = readings.previous ?: return null
        val b = readings.latest ?: return null
        val dt = b.seconds - a.seconds
        if (dt <= 0.0 || seconds <= b.seconds) return b.coordinate

        val first = sphericalVector(a.coordinate)
        val last = sphericalVector(b.coordinate)
        val rawAxis = cross(first, last)
        val length = norm(rawAxis)
        if (length < 1e-10) return b.coordinate

        val axis = rawAxis.map { it / length }
        val dot = first.zip(last).sumOf { (u, v) -> u * v }
            .coerceIn(-1.0, 1.0)
        val angle = acos(dot)
        // Suppress pathological estimates from noisy/teleporting positions.
        val rate = (angle / dt).coerceIn(0.0, 0.005)
        val extrapolation = rate * (seconds - b.seconds)
        val c = cos(extrapolation)
        val s = sin(extrapolation)
        val axisDot = axis.zip(last).sumOf { (u, v) -> u * v }
        val crossPart = cross(axis, last)
        val rotated = List(3) { i ->
            last[i] * c + crossPart[i] * s +
                axis[i] * axisDot * (1.0 - c)
        }
        return OrbitGeoCoordinate(
            latitude = Math.toDegrees(asin(rotated[2].coerceIn(-1.0, 1.0))),
            longitude = Math.toDegrees(atan2(rotated[1], rotated[0]))
        )
    }

    private fun sphericalVector(p: OrbitGeoCoordinate): List<Double> {
        val lat = Math.toRadians(p.latitude)
        val lon = Math.toRadians(p.longitude)
        return listOf(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
    }

    private fun cross(a: List<Double>, b: List<Double>): List<Double> = listOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )

    private fun norm(a: List<Double>): Double = sqrt(a.sumOf { it * it })
}
