
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.SatelliteDefinition
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteTelemetry
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates deterministic telemetry for simulated satellites.
 *
 * Each spacecraft uses its own baseline configuration.
 * Optional faults modify selected measurements to create
 * predictable diagnostic scenarios.
 *
 * The orbital motion is accelerated and illustrative.
 * It does not model orbital mechanics or spacecraft dynamics.
 */
object SatelliteSimulator {

    /**
     * Generates nominal telemetry for the primary satellite.
     *
     * This overload preserves compatibility with existing
     * simulator tests and single-satellite calculations.
     */
    fun snapshot(second: Int): SatelliteTelemetry {

        return snapshot(
            second = second,
            satellite = SatelliteCatalog.satellites.first()
        )
    }

    /**
     * Generates a telemetry packet for a particular satellite.
     *
     * @param second Current simulation second.
     * @param satellite Spacecraft configuration.
     * @param fault Fault currently affecting the spacecraft.
     * @param faultStartedSecond Time the fault was introduced.
     * @return Simulated telemetry measurements.
     */
    fun snapshot(
        second: Int,
        satellite: SatelliteDefinition,
        fault: SatelliteFault = SatelliteFault.NONE,
        faultStartedSecond: Int? = null
    ): SatelliteTelemetry {

        require(second >= 0) {
            "Simulation time cannot be negative."
        }

        val faultDuration = if (
            fault != SatelliteFault.NONE &&
            faultStartedSecond != null
        ) {

            (second - faultStartedSecond).coerceAtLeast(0)

        } else {
            0
        }

        // Generate the satellite's nominal measurements.
        val altitude =
            satellite.baseAltitudeKm +
                    1.8 * sin(second / 15.0)

        val velocity =
            satellite.baseVelocityKmS +
                    0.02 * cos(second / 15.0)

        var battery =
            satellite.initialBatteryPercent -
                    second * 0.015

        var temperature =
            21.0 + 3.5 * sin(second / 9.0)

        // Apply additional battery consumption during a fault.
        if (fault == SatelliteFault.POWER_DRAIN) {

            battery -= faultDuration * 0.5
        }

        // Simulate increasing temperature during a fault.
        if (fault == SatelliteFault.THERMAL_OVERLOAD) {

            temperature += faultDuration * 0.75
        }

        battery = battery.coerceIn(0.0, 100.0)
        temperature = temperature.coerceAtMost(95.0)

        // Offset each satellite's position along its orbit.
        val angle = (
                (second.toLong() * 4L) % 360L
                ).toFloat()

        val orbitAngle = (
                angle + satellite.orbitPhaseDegrees
                ) % 360f

        return SatelliteTelemetry(
            sampleSecond = second,
            altitudeKm = altitude,
            velocityKmS = velocity,
            batteryPercent = battery,
            temperatureC = temperature,
            orbitAngleDegrees = orbitAngle
        )
    }
}
