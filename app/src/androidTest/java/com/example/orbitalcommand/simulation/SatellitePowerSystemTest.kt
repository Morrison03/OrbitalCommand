
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.PowerMode
import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.SolarExposure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies simulated solar exposure, battery charging,
 * power depletion, and automatic recovery behavior.
 */
class SatellitePowerSystemTest {

    /**
     * Verifies that the configured solar cycle
     * alternates between sunlight and eclipse.
     */
    @Test
    fun solarExposureChangesAtExpectedTimes() {

        assertEquals(
            SolarExposure.SUNLIGHT,
            SatellitePowerSystem.exposureAt(0)
        )

        assertEquals(
            SolarExposure.SUNLIGHT,
            SatellitePowerSystem.exposureAt(34)
        )

        assertEquals(
            SolarExposure.ECLIPSE,
            SatellitePowerSystem.exposureAt(35)
        )

        assertEquals(
            SolarExposure.ECLIPSE,
            SatellitePowerSystem.exposureAt(59)
        )

        assertEquals(
            SolarExposure.SUNLIGHT,
            SatellitePowerSystem.exposureAt(60)
        )
    }

    /**
     * Confirms that solar generation increases
     * battery charge when generation exceeds load.
     */
    @Test
    fun sunlightChargesBattery() {

        val result = SatellitePowerSystem.advance(
            previousBatteryPercent = 50.0,
            previousMode = PowerMode.NORMAL,
            second = 1,
            fault = SatelliteFault.NONE
        )

        assertTrue(result.batteryPercent > 50.0)
        assertTrue(result.generationWatts > 0.0)
    }

    /**
     * Confirms that battery charge decreases
     * while the satellite is in eclipse.
     */
    @Test
    fun eclipseDischargesBattery() {

        val result = SatellitePowerSystem.advance(
            previousBatteryPercent = 50.0,
            previousMode = PowerMode.NORMAL,
            second = 35,
            fault = SatelliteFault.NONE
        )

        assertTrue(result.batteryPercent < 50.0)

        assertEquals(
            0.0,
            result.generationWatts,
            0.001
        )
    }

    /**
     * Verifies that the simulated power drain fault
     * causes more battery consumption than normal.
     */
    @Test
    fun powerDrainIncreasesConsumption() {

        val nominal = SatellitePowerSystem.advance(
            previousBatteryPercent = 70.0,
            previousMode = PowerMode.NORMAL,
            second = 1,
            fault = SatelliteFault.NONE
        )

        val faulted = SatellitePowerSystem.advance(
            previousBatteryPercent = 70.0,
            previousMode = PowerMode.NORMAL,
            second = 1,
            fault = SatelliteFault.POWER_DRAIN
        )

        assertTrue(
            faulted.consumptionWatts >
                    nominal.consumptionWatts
        )

        assertTrue(
            faulted.batteryPercent <
                    nominal.batteryPercent
        )
    }

    /**
     * Confirms that a depleted battery enters
     * the offline operating state.
     */
    @Test
    fun zeroBatteryEntersOfflineMode() {

        val mode = SatellitePowerSystem.modeFor(
            batteryPercent = 0.0,
            previousMode = PowerMode.SAFE_MODE
        )

        assertEquals(
            PowerMode.OFFLINE,
            mode
        )
    }

    /**
     * Verifies that an offline satellite remains
     * disconnected until battery charge reaches
     * the configured recovery threshold.
     */
    @Test
    fun offlineSatelliteRequiresRecoveryCharge() {

        val insufficient = SatellitePowerSystem.modeFor(
            batteryPercent = 5.0,
            previousMode = PowerMode.OFFLINE
        )

        val recovered = SatellitePowerSystem.modeFor(
            batteryPercent = 10.0,
            previousMode = PowerMode.OFFLINE
        )

        assertEquals(
            PowerMode.OFFLINE,
            insufficient
        )

        assertEquals(
            PowerMode.LOW_POWER,
            recovered
        )
    }

    /**
     * Confirms that complete power depletion
     * disables the simulated telemetry connection.
     */
    @Test
    fun depletedSatelliteCannotTransmit() {

        val satellite = SatelliteCatalog.satellites.first()

        val initial =
            SatelliteSimulator.initialTelemetry(satellite)

        val depleted = initial.copy(
            batteryPercent = 0.0,
            powerMode = PowerMode.OFFLINE
        )

        val state = SatelliteState(
            definition = satellite,
            telemetry = initial,
            onboardTelemetry = depleted
        )

        assertFalse(state.isConnected)

        assertEquals(
            initial,
            state.telemetry
        )

        assertTrue(state.isPowerDepleted)
    }

    /**
     * Verifies that the power model never
     * produces a negative battery percentage.
     */
    @Test
    fun batteryChargeNeverBecomesNegative() {

        var battery = 2.0
        var mode = PowerMode.SAFE_MODE

        for (second in 1..100) {

            val result = SatellitePowerSystem.advance(
                previousBatteryPercent = battery,
                previousMode = mode,
                second = second,
                fault = SatelliteFault.POWER_DRAIN
            )

            battery = result.batteryPercent
            mode = result.mode
        }

        assertTrue(battery >= 0.0)
        assertTrue(battery <= 100.0)
    }

    /**
     * Verifies that a satellite depleted by a
     * power fault can eventually recover after
     * the fault is removed.
     */
    @Test
    fun satelliteRecoversAfterPowerDrainIsCleared() {

        val definition = SatelliteCatalog.satellites.first()

        var telemetry = SatelliteSimulator.snapshot(
            second = 300,
            satellite = definition,
            fault = SatelliteFault.POWER_DRAIN,
            faultStartedSecond = 0
        )

        assertEquals(
            PowerMode.OFFLINE,
            telemetry.powerMode
        )

        for (second in 301..900) {

            telemetry = SatelliteSimulator.advance(
                previous = telemetry,
                second = second,
                satellite = definition,
                fault = SatelliteFault.NONE
            )
        }

        assertTrue(
            telemetry.batteryPercent >= 10.0
        )

        assertTrue(
            telemetry.powerMode != PowerMode.OFFLINE
        )
    }
}

