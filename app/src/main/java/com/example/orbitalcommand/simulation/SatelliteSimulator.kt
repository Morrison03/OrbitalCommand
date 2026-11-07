
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.PowerMode
import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.SatelliteDefinition
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteTelemetry
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates deterministic telemetry for simulated satellites.
 *
 * Orbital movement and temperatures are simplified.
 * The electrical power model is delegated to
 * SatellitePowerSystem.
 *
 * The simulator does not model physical orbital mechanics.
 */
object SatelliteSimulator {

    /**
     * Creates nominal telemetry for the primary satellite.
     *
     * This overload maintains compatibility with
     * existing simulator unit tests.
     */
    fun snapshot(second: Int): SatelliteTelemetry {

        return snapshot(
            second = second,
            satellite = SatelliteCatalog.satellites.first()
        )
    }

    /**
     * Calculates the simulated telemetry for a given time.
     *
     * This function starts from the satellite's initial
     * state and advances through the requested interval.
     *
     * The live ViewModel uses advance() instead to avoid
     * repeatedly recalculating earlier simulation steps.
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

        var telemetry = initialTelemetry(satellite)

        for (tick in 1..second) {

            val activeFault = if (
                fault == SatelliteFault.NONE ||
                faultStartedSecond == null ||
                tick >= faultStartedSecond
            ) {
                fault
            } else {
                SatelliteFault.NONE
            }

            telemetry = advance(
                previous = telemetry,
                second = tick,
                satellite = satellite,
                fault = activeFault,
                faultStartedSecond = faultStartedSecond
            )
        }

        return telemetry
    }

    /**
     * Generates the initial packet for a satellite.
     */
    fun initialTelemetry(
        satellite: SatelliteDefinition
    ): SatelliteTelemetry {

        val power = SatellitePowerSystem.current(
            batteryPercent = satellite.initialBatteryPercent,
            previousMode = PowerMode.NORMAL,
            second = 0,
            fault = SatelliteFault.NONE
        )

        return createTelemetry(
            second = 0,
            satellite = satellite,
            power = power,
            fault = SatelliteFault.NONE,
            faultStartedSecond = null
        )
    }

    /**
     * Advances a satellite by exactly one simulation second.
     *
     * Battery charge is calculated from the previous
     * packet rather than from a fixed time-based formula.
     */
    fun advance(
        previous: SatelliteTelemetry,
        second: Int,
        satellite: SatelliteDefinition,
        fault: SatelliteFault = SatelliteFault.NONE,
        faultStartedSecond: Int? = null
    ): SatelliteTelemetry {

        require(second == previous.sampleSecond + 1) {
            "Simulation updates must advance one second."
        }

        val power = SatellitePowerSystem.advance(
            previousBatteryPercent = previous.batteryPercent,
            previousMode = previous.powerMode,
            second = second,
            fault = fault
        )

        return createTelemetry(
            second = second,
            satellite = satellite,
            power = power,
            fault = fault,
            faultStartedSecond = faultStartedSecond
        )
    }

    /**
     * Recalculates instantaneous telemetry after a fault
     * changes without advancing simulation time.
     *
     * Existing battery charge is preserved.
     */
    fun refresh(
        previous: SatelliteTelemetry,
        satellite: SatelliteDefinition,
        fault: SatelliteFault,
        faultStartedSecond: Int?
    ): SatelliteTelemetry {

        val power = SatellitePowerSystem.current(
            batteryPercent = previous.batteryPercent,
            previousMode = previous.powerMode,
            second = previous.sampleSecond,
            fault = fault
        )

        return createTelemetry(
            second = previous.sampleSecond,
            satellite = satellite,
            power = power,
            fault = fault,
            faultStartedSecond = faultStartedSecond
        )
    }

    /**
     * Combines orbital, thermal, and electrical
     * measurements into one telemetry packet.
     */
    private fun createTelemetry(
        second: Int,
        satellite: SatelliteDefinition,
        power: PowerSnapshot,
        fault: SatelliteFault,
        faultStartedSecond: Int?
    ): SatelliteTelemetry {

        val altitude =
            satellite.baseAltitudeKm +
                    1.8 * sin(second / 15.0)

        val velocity =
            satellite.baseVelocityKmS +
                    0.02 * cos(second / 15.0)

        var temperature =
            21.0 + 3.5 * sin(second / 9.0)

        if (fault == SatelliteFault.THERMAL_OVERLOAD) {

            val faultDuration = (
                    second - (faultStartedSecond ?: second)
                    ).coerceAtLeast(0)

            temperature += faultDuration * 0.75
        }

        temperature = temperature.coerceAtMost(95.0)

        val angle = (
                (second.toLong() * 4L) % 360L
                ).toFloat()

        val orbitalAngle = (
                angle + satellite.orbitPhaseDegrees
                ) % 360f

        return SatelliteTelemetry(
            sampleSecond = second,
            altitudeKm = altitude,
            velocityKmS = velocity,
            batteryPercent = power.batteryPercent,
            temperatureC = temperature,
            orbitAngleDegrees = orbitalAngle,
            solarExposure = power.exposure,
            solarGenerationWatts = power.generationWatts,
            powerConsumptionWatts = power.consumptionWatts,
            powerMode = power.mode
        )
    }
}
