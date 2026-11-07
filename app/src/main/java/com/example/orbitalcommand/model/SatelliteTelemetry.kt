package com.example.orbitalcommand.model

/**
 * Represents a telemetry packet received from a satellite.
 *
 * Each packet contains measurements associated with a
 * particular second of the simulation.
 */
data class SatelliteTelemetry(
    val sampleSecond: Int,
    val altitudeKm: Double,
    val velocityKmS: Double,
    val batteryPercent: Double,
    val temperatureC: Double,
    val orbitAngleDegrees: Float
)
