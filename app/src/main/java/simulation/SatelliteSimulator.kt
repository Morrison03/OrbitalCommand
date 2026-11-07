
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.SatelliteTelemetry
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates deterministic satellite telemetry for the
 * local mission-control simulation.
 *
 * Measurements are simplified for interface development.
 * The visual orbit completes one revolution every
 * 90 simulation seconds and is not physically accurate.
 */
object SatelliteSimulator {

    /**
     * Generates the measurements associated with a
     * particular simulation second.
     *
     * @param second Nonnegative elapsed simulation time.
     * @return Simulated satellite telemetry.
     */
    fun snapshot(second: Int): SatelliteTelemetry {

        require(second >= 0) {
            "Simulation time cannot be negative."
        }

        val altitude = 525.0 +
                1.8 * sin(second / 15.0)

        val velocity = 7.61 +
                0.02 * cos(second / 15.0)

        val battery = (
                87.0 - second * 0.015
                ).coerceIn(0.0, 100.0)

        val temperature = 21.0 +
                3.5 * sin(second / 9.0)

        val angle = (
                (second.toLong() * 4L) % 360L
                ).toFloat()

        return SatelliteTelemetry(
            sampleSecond = second,
            altitudeKm = altitude,
            velocityKmS = velocity,
            batteryPercent = battery,
            temperatureC = temperature,
            orbitAngleDegrees = angle
        )
    }
}

