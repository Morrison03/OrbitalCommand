
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.EquipmentDamage
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
 * Simulates solar generation, electrical consumption,
 * battery depletion, and automatic power recovery.
 *
 * Persistent solar array damage reduces generation
 * until an explicit servicing operation is completed.
 *
 * All power behavior is accelerated for UI testing.
 */
object SatellitePowerSystem {

    const val CYCLE_SECONDS = 60
    const val SUNLIGHT_SECONDS = 35

    private const val SOLAR_GENERATION_WATTS = 150.0
    private const val DAMAGED_ARRAY_EFFICIENCY = 0.10
    private const val CHARGE_FACTOR = 0.0018

    /**
     * Returns sunlight or eclipse for the current
     * simulation second.
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
     * Determines the satellite's power operating mode.
     *
     * Once depleted, the satellite remains offline
     * until battery charge reaches 10 percent.
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
     * Evaluates power conditions without advancing
     * the simulation clock or battery charge.
     *
     * Used when equipment or fault states change.
     */
    fun current(
        batteryPercent: Double,
        previousMode: PowerMode,
        second: Int,
        fault: SatelliteFault,
        equipmentDamage: EquipmentDamage =
            EquipmentDamage.NONE
    ): PowerSnapshot {

        val mode = modeFor(
            batteryPercent,
            previousMode
        )

        val exposure = exposureAt(second)

        return PowerSnapshot(
            batteryPercent =
                batteryPercent.coerceIn(0.0, 100.0),
            exposure = exposure,
            generationWatts = generationFor(
                exposure = exposure,
                equipmentDamage = equipmentDamage
            ),
            consumptionWatts = consumptionFor(
                mode = mode,
                fault = fault
            ),
            mode = mode
        )
    }

    /**
     * Advances the electrical power simulation
     * by one second.
     *
     * Battery charge changes according to generated
     * power minus electrical consumption.
     */
    fun advance(
        previousBatteryPercent: Double,
        previousMode: PowerMode,
        second: Int,
        fault: SatelliteFault,
        equipmentDamage: EquipmentDamage =
            EquipmentDamage.NONE
    ): PowerSnapshot {

        val exposure = exposureAt(second)

        val generation = generationFor(
            exposure = exposure,
            equipmentDamage = equipmentDamage
        )

        val consumption = consumptionFor(
            mode = previousMode,
            fault = fault
        )

        val battery = (
                previousBatteryPercent +
                        (generation - consumption) * CHARGE_FACTOR
                ).coerceIn(0.0, 100.0)

        val nextMode = modeFor(
            batteryPercent = battery,
            previousMode = previousMode
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
     * Determines available solar power.
     *
     * Damaged solar arrays produce ten percent
     * of nominal solar generation.
     */
    private fun generationFor(
        exposure: SolarExposure,
        equipmentDamage: EquipmentDamage
    ): Double {

        if (exposure == SolarExposure.ECLIPSE) {
            return 0.0
        }

        return if (
            equipmentDamage ==
            EquipmentDamage.SOLAR_ARRAY_DAMAGE
        ) {

            SOLAR_GENERATION_WATTS *
                    DAMAGED_ARRAY_EFFICIENCY

        } else {

            SOLAR_GENERATION_WATTS
        }
    }

    /**
     * Calculates the load associated with the
     * current power mode and temporary faults.
     *
     * Nonessential systems consume less power
     * in safe mode and offline conditions.
     */
    private fun consumptionFor(
        mode: PowerMode,
        fault: SatelliteFault
    ): Double {

        val baseConsumption = when (mode) {

            PowerMode.NORMAL -> 70.0

            PowerMode.LOW_POWER -> 50.0

            PowerMode.SAFE_MODE -> 25.0

            PowerMode.OFFLINE -> 2.0
        }

        val faultConsumption =
            if (fault == SatelliteFault.POWER_DRAIN) {
                650.0
            } else {
                0.0
            }

        return baseConsumption + faultConsumption
    }
}
