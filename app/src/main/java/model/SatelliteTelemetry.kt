
package com.example.orbitalcommand.model

/**
 * Contains measurements received from a simulated satellite.
 *
 * Each snapshot records the simulation second when the
 * measurements were generated.
 */
data class SatelliteTelemetry(
    val sampleSecond: Int,
    val altitudeKm: Double,
    val velocityKmS: Double,
    val batteryPercent: Double,
    val temperatureC: Double,
    val orbitAngleDegrees: Float
)

/**
 * Represents the observable state of the mission dashboard.
 *
 * The interface uses this state to render telemetry,
 * connection status, and simulation time.
 */
data class OrbitalUiState(
    val elapsedSeconds: Int,
    val telemetry: SatelliteTelemetry,
    val linkLost: Boolean = false
) {

    /**
     * Returns the age of the most recently received
     * telemetry packet in simulated seconds.
     */
    val packetAgeSeconds: Int
        get() = (elapsedSeconds - telemetry.sampleSecond)
            .coerceAtLeast(0)
}
