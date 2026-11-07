
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.PowerMode
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SolarExposure

/**
 * Contains the results of one simulated electrical
 * power system calculation.
 */
data class PowerSnapshot(
    val batteryPercent: Double,
    val exposure: SolarExposure,
    val generationWatts: Double,
    val consumptionWatts: Double,
    val mode: PowerMode
)

/**
 * Simulates electrical generation, consumption,
 * battery depletion, and automatic power recovery.
 *
 * The model uses an accelerated sunlight/eclipsing cycle
 * and simplified charge/discharge rates.
 *
 * It is designed to support user-interface testing
 * rather than physical spacecraft power analysis.
 */
object SatellitePowerSystem {

    const val CYCLE_SECONDS = 60
    const val SUNLIGHT_SECONDS = 35

    private const val SOLAR_GENERATION_WATTS = 150.0

    /**
     * Converts the simulated power balance into a
     * battery percentage change per simulation second.
     */
    private const val CHARGE_FACTOR = 0.0018

    /**
     * Returns the satellite's current solar exposure.
     *
     * Each 60-second cycle consists of 35 seconds
     * in sunlight followed by 25 seconds in eclipse.
     */
    fun exposureAt(second: Int): SolarExposure {

        require(second >= 0) {
            "Simulation time cannot be negative."
        }

        return if (
            second % CYCLE_SECONDS < SUNLIGHT_SECONDS
        ) {
            SolarExposure.SUNLIGHT
        } else {
            SolarExposure.ECLIPSE
        }
    }

    /**
     * Calculates the power operating mode.
     *
     * A satellite recovering from an OFFLINE state
     * remains offline until battery charge reaches 10%.
     * This avoids immediately restoring communications
     * after a negligible amount of charging.
     */
    fun modeFor(
        batteryPercent: Double,
        previousMode: PowerMode
    ): PowerMode {

        return when {

            batteryPercent <= 0.0 ->
                PowerMode.OFFLINE

            previousMode == PowerMode.OFFLINE &&
                    batteryPercent < 10.0 ->
                PowerMode.OFFLINE

            batteryPercent < 10.0 ->
                PowerMode.SAFE_MODE

            batteryPercent < 25.0 ->
                PowerMode.LOW_POWER

            else ->
                PowerMode.NORMAL
        }
    }

    /**
     * Generates a power snapshot without advancing
     * the battery charge.
     *
     * This is used at initialization and when the
     * selected simulated fault changes.
     */
    fun current(
        batteryPercent: Double,
        previousMode: PowerMode,
        second: Int,
        fault: SatelliteFault
    ): PowerSnapshot {

        val mode = modeFor(
            batteryPercent,
            previousMode
        )

        val exposure = exposureAt(second)

        return PowerSnapshot(
            batteryPercent = batteryPercent.coerceIn(0.0, 100.0),
            exposure = exposure,
            generationWatts = generationFor(exposure),
            consumptionWatts = consumptionFor(mode, fault),
            mode = mode
        )
    }

    /**
     * Advances the battery model by one simulation second.
     *
     * Positive net power charges the battery.
     * Negative net power discharges the battery.
     *
     * A power drain fault introduces a large additional
     * electrical load for testing failure scenarios.
     */
    fun advance(
        previousBatteryPercent: Double,
        previousMode: PowerMode,
        second: Int,
        fault: SatelliteFault
    ): PowerSnapshot {

        val exposure = exposureAt(second)

        val generation = generationFor(exposure)

        val consumption = consumptionFor(
            previousMode,
            fault
        )

        val powerBalance = generation - consumption

        val battery = (
                previousBatteryPercent +
                        powerBalance * CHARGE_FACTOR
                ).coerceIn(0.0, 100.0)

        val nextMode = modeFor(
            battery,
            previousMode
        )

        return PowerSnapshot(
            batteryPercent = battery,
            exposure = exposure,
            generationWatts = generation,
            consumptionWatts = consumption,
            mode = nextMode
        )
    }

    /**
     * Returns solar electrical generation for
     * the current illumination condition.
     */
    private fun generationFor(
        exposure: SolarExposure
    ): Double {

        return if (exposure == SolarExposure.SUNLIGHT) {
            SOLAR_GENERATION_WATTS
        } else {
            0.0
        }
    }

    /**
     * Calculates the electrical load associated
     * with a spacecraft's operating mode.
     *
     * Safe and offline modes reduce normal system
     * consumption, while a power drain fault adds
     * a simulated abnormal load.
     */
    private fun consumptionFor(
        mode: PowerMode,
        fault: SatelliteFault
    ): Double {

        val normalConsumption = when (mode) {

            PowerMode.NORMAL -> 70.0

            PowerMode.LOW_POWER -> 50.0

            PowerMode.SAFE_MODE -> 25.0

            PowerMode.OFFLINE -> 2.0
        }

        val additionalConsumption =
            if (fault == SatelliteFault.POWER_DRAIN) {
                650.0
            } else {
                0.0
            }

        return normalConsumption + additionalConsumption
    }
}

