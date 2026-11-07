
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.TelemetryHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies satellite fleet configuration, independent
 * telemetry generation, orbital visualization consistency,
 * and simulated fault behavior.
 */
class SatelliteFleetTest {

    /**
     * Verifies that the catalog contains three spacecraft
     * with unique identifiers.
     */
    @Test
    fun satelliteCatalogContainsUniqueSpacecraft() {

        val satellites = SatelliteCatalog.satellites

        assertEquals(3, satellites.size)

        assertEquals(
            3,
            satellites.map { it.id }.distinct().size
        )
    }

    /**
     * Verifies that spacecraft configurations produce
     * different starting telemetry measurements.
     */
    @Test
    fun satellitesHaveIndependentMeasurements() {

        val snapshots = SatelliteCatalog.satellites.map {
            SatelliteSimulator.snapshot(0, it)
        }

        val altitudes = snapshots.map {
            it.altitudeKm
        }

        assertEquals(
            3,
            altitudes.distinct().size
        )
    }

    /**
     * Verifies that satellite visualization radii increase
     * in the same order as their configured orbital altitudes.
     *
     * This prevents the orbital display from contradicting
     * the satellite telemetry.
     */
    @Test
    fun orbitalRadiusMatchesAltitudeOrdering() {

        val satellites = SatelliteCatalog.satellites

        val sortedByAltitude = satellites.sortedBy {
            it.baseAltitudeKm
        }

        val sortedByRadius = satellites.sortedBy {
            it.orbitRadiusFraction
        }

        assertEquals(
            sortedByAltitude.map { it.id },
            sortedByRadius.map { it.id }
        )
    }

    /**
     * Confirms that a power drain fault increases
     * battery consumption relative to nominal operation.
     */
    @Test
    fun powerDrainReducesBatteryLevel() {

        val satellite = SatelliteCatalog.satellites.first()

        val nominal = SatelliteSimulator.snapshot(
            second = 40,
            satellite = satellite
        )

        val faulted = SatelliteSimulator.snapshot(
            second = 40,
            satellite = satellite,
            fault = SatelliteFault.POWER_DRAIN,
            faultStartedSecond = 10
        )

        assertTrue(
            faulted.batteryPercent < nominal.batteryPercent
        )
    }

    /**
     * Confirms that a thermal overload raises temperature
     * above the corresponding nominal measurement.
     */
    @Test
    fun thermalOverloadIncreasesTemperature() {

        val satellite = SatelliteCatalog.satellites.first()

        val nominal = SatelliteSimulator.snapshot(
            second = 40,
            satellite = satellite
        )

        val faulted = SatelliteSimulator.snapshot(
            second = 40,
            satellite = satellite,
            fault = SatelliteFault.THERMAL_OVERLOAD,
            faultStartedSecond = 10
        )

        assertTrue(
            faulted.temperatureC > nominal.temperatureC
        )
    }

    /**
     * Verifies that only a communication loss fault
     * disables the simulated telemetry connection.
     */
    @Test
    fun communicationLossDisablesConnection() {

        val definition = SatelliteCatalog.satellites.first()

        val telemetry = SatelliteSimulator.snapshot(
            second = 0,
            satellite = definition
        )

        val nominalState = SatelliteState(
            definition = definition,
            telemetry = telemetry
        )

        val disconnectedState = nominalState.copy(
            fault = SatelliteFault.COMMUNICATION_LOSS
        )

        assertTrue(nominalState.isConnected)
        assertFalse(disconnectedState.isConnected)
    }

    /**
     * Confirms that each satellite can maintain its own
     * independent collection of received telemetry.
     */
    @Test
    fun satelliteHistoriesRemainIndependent() {

        val first = SatelliteCatalog.satellites[0]
        val second = SatelliteCatalog.satellites[1]

        val firstHistory = TelemetryHistory.append(
            history = listOf(
                SatelliteSimulator.snapshot(0, first)
            ),
            sample = SatelliteSimulator.snapshot(1, first)
        )

        val secondHistory = listOf(
            SatelliteSimulator.snapshot(0, second)
        )

        assertEquals(2, firstHistory.size)
        assertEquals(1, secondHistory.size)
    }
}
