
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.EquipmentDamage
import com.example.orbitalcommand.model.MissionEvent
import com.example.orbitalcommand.model.MissionEventKind
import com.example.orbitalcommand.model.MissionEventLog
import com.example.orbitalcommand.model.MissionEventSeverity
import com.example.orbitalcommand.model.PowerMode
import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that persistent equipment damage changes
 * simulated spacecraft measurements and remains
 * independent of temporary diagnostic faults.
 */
class EquipmentDamageTest {

    private val satellite =
        SatelliteCatalog.satellites.first()

    /**
     * Confirms that damaged solar arrays generate
     * significantly less electricity during sunlight.
     */
    @Test
    fun damagedSolarArrayReducesGeneration() {

        val nominal = SatellitePowerSystem.current(
            batteryPercent = 70.0,
            previousMode = PowerMode.NORMAL,
            second = 1,
            fault = SatelliteFault.NONE
        )

        val damaged = SatellitePowerSystem.current(
            batteryPercent = 70.0,
            previousMode = PowerMode.NORMAL,
            second = 1,
            fault = SatelliteFault.NONE,
            equipmentDamage =
                EquipmentDamage.SOLAR_ARRAY_DAMAGE
        )

        assertEquals(
            150.0,
            nominal.generationWatts,
            0.001
        )

        assertEquals(
            15.0,
            damaged.generationWatts,
            0.001
        )
    }

    /**
     * Verifies that solar damage causes the battery
     * to lose charge even while in sunlight.
     */
    @Test
    fun damagedArrayDischargesInSunlight() {

        val damaged = SatellitePowerSystem.advance(
            previousBatteryPercent = 70.0,
            previousMode = PowerMode.NORMAL,
            second = 1,
            fault = SatelliteFault.NONE,
            equipmentDamage =
                EquipmentDamage.SOLAR_ARRAY_DAMAGE
        )

        assertTrue(damaged.batteryPercent < 70.0)
    }

    /**
     * Verifies that solar generation returns to normal
     * after equipment damage is removed.
     */
    @Test
    fun repairingArrayRestoresGeneration() {

        val damaged = SatellitePowerSystem.current(
            batteryPercent = 40.0,
            previousMode = PowerMode.NORMAL,
            second = 1,
            fault = SatelliteFault.NONE,
            equipmentDamage =
                EquipmentDamage.SOLAR_ARRAY_DAMAGE
        )

        val repaired = SatellitePowerSystem.current(
            batteryPercent = damaged.batteryPercent,
            previousMode = damaged.mode,
            second = 1,
            fault = SatelliteFault.NONE,
            equipmentDamage = EquipmentDamage.NONE
        )

        assertEquals(
            150.0,
            repaired.generationWatts,
            0.001
        )

        assertEquals(
            damaged.batteryPercent,
            repaired.batteryPercent,
            0.001
        )
    }

    /**
     * Confirms that thermal radiator damage causes
     * higher temperatures than nominal operation.
     */
    @Test
    fun radiatorDamageIncreasesTemperature() {

        val nominal = SatelliteSimulator.snapshot(
            second = 100,
            satellite = satellite
        )

        val damaged = SatelliteSimulator.snapshot(
            second = 100,
            satellite = satellite,
            equipmentDamage =
                EquipmentDamage.THERMAL_RADIATOR_DAMAGE,
            equipmentDamageStartedSecond = 10
        )

        assertTrue(
            damaged.temperatureC > nominal.temperatureC
        )
    }

    /**
     * Verifies that clearing a temporary fault
     * does not remove persistent equipment damage.
     */
    @Test
    fun equipmentDamageRemainsAfterFaultClearing() {

        val telemetry =
            SatelliteSimulator.initialTelemetry(satellite)

        val damagedState = SatelliteState(
            definition = satellite,
            telemetry = telemetry,
            fault = SatelliteFault.POWER_DRAIN,
            equipmentDamage =
                EquipmentDamage.SOLAR_ARRAY_DAMAGE
        )

        val clearedState = damagedState.copy(
            fault = SatelliteFault.NONE
        )

        assertEquals(
            EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            clearedState.equipmentDamage
        )

        assertTrue(clearedState.requiresServicing)
        assertTrue(clearedState.hasFault)
    }

    /**
     * Verifies that payload sensor damage is reported
     * as a persistent equipment maintenance requirement.
     */
    @Test
    fun sensorDamageRequiresServicing() {

        val telemetry =
            SatelliteSimulator.initialTelemetry(satellite)

        val state = SatelliteState(
            definition = satellite,
            telemetry = telemetry,
            equipmentDamage =
                EquipmentDamage.PAYLOAD_SENSOR_DAMAGE
        )

        assertTrue(state.requiresServicing)
        assertTrue(state.hasFault)
    }

    /**
     * Confirms that resolving a hardware damage alert
     * does not resolve an unrelated power alert.
     */
    @Test
    fun repairAlertResolutionPreservesPowerAlert() {

        val damageAlert = MissionEvent(
            id = 1L,
            satelliteId = "SAT-001",
            occurredAtSecond = 10,
            title = "Solar Array Damage",
            description = "Equipment repair required.",
            severity = MissionEventSeverity.WARNING,
            kind = MissionEventKind.ALERT,
            sourceKey = "EQUIPMENT_DAMAGE"
        )

        val powerAlert = MissionEvent(
            id = 2L,
            satelliteId = "SAT-001",
            occurredAtSecond = 20,
            title = "Low battery",
            description = "Battery charge is low.",
            severity = MissionEventSeverity.WARNING,
            kind = MissionEventKind.ALERT,
            sourceKey = "POWER_STATUS"
        )

        val updated =
            MissionEventLog.resolveActiveForSource(
                history = listOf(powerAlert, damageAlert),
                satelliteId = "SAT-001",
                sourceKey = "EQUIPMENT_DAMAGE",
                atSecond = 30
            )

        assertFalse(
            updated.first { it.id == 1L }.isActive
        )

        assertTrue(
            updated.first { it.id == 2L }.isActive
        )
    }
}

