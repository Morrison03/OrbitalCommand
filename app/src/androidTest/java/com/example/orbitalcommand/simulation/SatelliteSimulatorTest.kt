
package com.example.orbitalcommand.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests deterministic telemetry generation and validates
 * the expected boundaries of the satellite simulator.
 */
class SatelliteSimulatorTest {

    /**
     * Verifies the satellite's initial telemetry values.
     */
    @Test
    fun initialTelemetryHasExpectedValues() {

        val telemetry = SatelliteSimulator.snapshot(0)

        assertEquals(0, telemetry.sampleSecond)

        assertEquals(
            525.0,
            telemetry.altitudeKm,
            0.001
        )

        assertEquals(
            7.63,
            telemetry.velocityKmS,
            0.001
        )

        assertEquals(
            87.0,
            telemetry.batteryPercent,
            0.001
        )

        assertEquals(
            21.0,
            telemetry.temperatureC,
            0.001
        )

        assertEquals(
            0f,
            telemetry.orbitAngleDegrees,
            0.001f
        )
    }

    /**
     * Verifies that identical simulation times produce
     * identical telemetry measurements.
     */
    @Test
    fun sameTimeProducesSameTelemetry() {

        val first = SatelliteSimulator.snapshot(30)
        val second = SatelliteSimulator.snapshot(30)

        assertEquals(first, second)
    }

    /**
     * Ensures battery readings remain within the
     * expected percentage range.
     */
    @Test
    fun batteryRemainsWithinValidRange() {

        val telemetry = SatelliteSimulator.snapshot(100000)

        assertTrue(telemetry.batteryPercent >= 0.0)
        assertTrue(telemetry.batteryPercent <= 100.0)
    }

    /**
     * Verifies the completion of one illustrated
     * orbital revolution.
     */
    @Test
    fun orbitCompletesExpectedRevolution() {

        val initial = SatelliteSimulator.snapshot(0)
        val completed = SatelliteSimulator.snapshot(90)

        assertEquals(
            initial.orbitAngleDegrees,
            completed.orbitAngleDegrees,
            0.001f
        )
    }

    /**
     * Confirms that negative simulation times
     * are rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun negativeSimulationTimeIsRejected() {

        SatelliteSimulator.snapshot(-1)
    }
}

