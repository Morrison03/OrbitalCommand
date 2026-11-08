
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.EquipmentDamage
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
 * Combines simplified orbital measurements, thermal behavior,
 * and the electrical power model into telemetry packets.
 *
 * Persistent equipment damage remains active until the
 * owning satellite's state is explicitly repaired.
 */
object SatelliteSimulator {

    /**
     * Generates nominal telemetry for the primary satellite.
     *
     * Preserves compatibility with existing unit tests.
     */
    fun snapshot(second: Int): SatelliteTelemetry {

        return snapshot(
            second = second,
            satellite = SatelliteCatalog.satellites.first()
        )
    }

    /**
     * Calculates satellite telemetry at a requested
     * simulation second.
     *
     * This method replays earlier simulation steps.
     * The live ViewModel uses advance() for efficiency.
     */
    fun snapshot(
        second: Int,
        satellite: SatelliteDefinition,
        fault: SatelliteFault = SatelliteFault.NONE,
        faultStartedSecond: Int? = null,
        equipmentDamage: EquipmentDamage =
            EquipmentDamage.NONE,
        equipmentDamageStartedSecond: Int? = null
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

            val activeDamage = if (
                equipmentDamage == EquipmentDamage.NONE ||
                equipmentDamageStartedSecond == null ||
                tick >= equipmentDamageStartedSecond
            ) {
                equipmentDamage
            } else {
                EquipmentDamage.NONE
            }

            telemetry = advance(
                previous = telemetry,
                second = tick,
                satellite = satellite,
                fault = activeFault,
                faultStartedSecond = faultStartedSecond,
                equipmentDamage = activeDamage,
                equipmentDamageStartedSecond =
                    equipmentDamageStartedSecond
            )
        }

        return telemetry
    }

    /**
     * Creates the satellite's initial telemetry packet.
     */
    fun initialTelemetry(
        satellite: SatelliteDefinition
    ): SatelliteTelemetry {

        val power = SatellitePowerSystem.current(
            batteryPercent =
                satellite.initialBatteryPercent,
            previousMode = PowerMode.NORMAL,
            second = 0,
            fault = SatelliteFault.NONE
        )

        return createTelemetry(
            second = 0,
            satellite = satellite,
            power = power,
            fault = SatelliteFault.NONE,
            faultStartedSecond = null,
            equipmentDamage = EquipmentDamage.NONE,
            equipmentDamageStartedSecond = null
        )
    }

    /**
     * Advances satellite telemetry by exactly
     * one simulation second.
     *
     * Persistent equipment damage is included in
     * each power and thermal calculation.
     */
    fun advance(
        previous: SatelliteTelemetry,
        second: Int,
        satellite: SatelliteDefinition,
        fault: SatelliteFault = SatelliteFault.NONE,
        faultStartedSecond: Int? = null,
        equipmentDamage: EquipmentDamage =
            EquipmentDamage.NONE,
        equipmentDamageStartedSecond: Int? = null
    ): SatelliteTelemetry {

        require(second == previous.sampleSecond + 1) {
            "Simulation updates must advance one second."
        }

        val power = SatellitePowerSystem.advance(
            previousBatteryPercent =
                previous.batteryPercent,
            previousMode = previous.powerMode,
            second = second,
            fault = fault,
            equipmentDamage = equipmentDamage
        )

        return createTelemetry(
            second = second,
            satellite = satellite,
            power = power,
            fault = fault,
            faultStartedSecond = faultStartedSecond,
            equipmentDamage = equipmentDamage,
            equipmentDamageStartedSecond =
                equipmentDamageStartedSecond
        )
    }

    /**
     * Recalculates instantaneous telemetry when
     * fault or equipment status changes.
     *
     * Battery charge and simulation time remain
     * unchanged during this operation.
     */
    fun refresh(
        previous: SatelliteTelemetry,
        satellite: SatelliteDefinition,
        fault: SatelliteFault,
        faultStartedSecond: Int?,
        equipmentDamage: EquipmentDamage =
            EquipmentDamage.NONE,
        equipmentDamageStartedSecond: Int? = null
    ): SatelliteTelemetry {

        val power = SatellitePowerSystem.current(
            batteryPercent = previous.batteryPercent,
            previousMode = previous.powerMode,
            second = previous.sampleSecond,
            fault = fault,
            equipmentDamage = equipmentDamage
        )

        return createTelemetry(
            second = previous.sampleSecond,
            satellite = satellite,
            power = power,
            fault = fault,
            faultStartedSecond = faultStartedSecond,
            equipmentDamage = equipmentDamage,
            equipmentDamageStartedSecond =
                equipmentDamageStartedSecond
        )
    }

    /**
     * Combines satellite motion, thermal measurements,
     * and power data into one telemetry packet.
     */
    private fun createTelemetry(
        second: Int,
        satellite: SatelliteDefinition,
        power: PowerSnapshot,
        fault: SatelliteFault,
        faultStartedSecond: Int?,
        equipmentDamage: EquipmentDamage,
        equipmentDamageStartedSecond: Int?
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

            val duration = (
                    second - (faultStartedSecond ?: second)
                    ).coerceAtLeast(0)

            temperature += duration * 0.75
        }

        if (
            equipmentDamage ==
            EquipmentDamage.THERMAL_RADIATOR_DAMAGE
        ) {

            val damageDuration = (
                    second -
                            (equipmentDamageStartedSecond ?: second)
                    ).coerceAtLeast(0)

            temperature += (
                    damageDuration * 0.10
                    ).coerceAtMost(65.0)
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
            solarGenerationWatts =
                power.generationWatts,
            powerConsumptionWatts =
                power.consumptionWatts,
            powerMode = power.mode
        )
    }
}
