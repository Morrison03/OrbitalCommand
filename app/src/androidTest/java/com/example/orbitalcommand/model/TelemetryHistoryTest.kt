
package com.example.orbitalcommand.model

import com.example.orbitalcommand.simulation.SatelliteSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies telemetry history retention, chronological
 * ordering, duplicate handling, and missing-data intervals.
 */
class TelemetryHistoryTest {

    /**
     * Verifies that a received packet is added to an
     * initially empty telemetry history.
     */
    @Test
    fun firstSampleIsStored() {

        val sample = SatelliteSimulator.snapshot(0)

        val history = TelemetryHistory.append(
            history = emptyList(),
            sample = sample
        )

        assertEquals(1, history.size)
        assertEquals(sample, history.first())
    }

    /**
     * Verifies that historical telemetry is limited
     * to the configured maximum number of samples.
     */
    @Test
    fun historyDoesNotExceedMaximumSize() {

        var history = emptyList<SatelliteTelemetry>()

        for (second in 0..150) {

            history = TelemetryHistory.append(
                history = history,
                sample = SatelliteSimulator.snapshot(second)
            )
        }

        assertEquals(
            TelemetryHistory.MAX_SAMPLES,
            history.size
        )

        assertEquals(31, history.first().sampleSecond)
        assertEquals(150, history.last().sampleSecond)
    }

    /**
     * Verifies that packets with identical timestamps
     * do not create duplicate history entries.
     */
    @Test
    fun duplicateTimestampReplacesExistingSample() {

        val sample = SatelliteSimulator.snapshot(10)

        val firstHistory = TelemetryHistory.append(
            history = emptyList(),
            sample = sample
        )

        val updatedHistory = TelemetryHistory.append(
            history = firstHistory,
            sample = sample
        )

        assertEquals(1, updatedHistory.size)
        assertEquals(sample, updatedHistory.first())
    }

    /**
     * Verifies that missing telemetry intervals are
     * preserved instead of being filled with fake readings.
     */
    @Test
    fun missingTelemetrySecondsRemainMissing() {

        val first = SatelliteSimulator.snapshot(10)
        val second = SatelliteSimulator.snapshot(15)

        var history = TelemetryHistory.append(
            history = emptyList(),
            sample = first
        )

        history = TelemetryHistory.append(
            history = history,
            sample = second
        )

        assertEquals(2, history.size)

        assertEquals(
            listOf(10, 15),
            history.map { it.sampleSecond }
        )
    }

    /**
     * Verifies that packets older than the latest
     * received timestamp are rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun outOfOrderPacketIsRejected() {

        val history = listOf(
            SatelliteSimulator.snapshot(20)
        )

        TelemetryHistory.append(
            history = history,
            sample = SatelliteSimulator.snapshot(10)
        )
    }

    /**
     * Confirms that consecutive telemetry packets
     * are retained in chronological order.
     */
    @Test
    fun consecutivePacketsRemainOrdered() {

        var history = emptyList<SatelliteTelemetry>()

        for (second in 0..10) {

            history = TelemetryHistory.append(
                history = history,
                sample = SatelliteSimulator.snapshot(second)
            )
        }

        val timestamps = history.map {
            it.sampleSecond
        }

        assertEquals((0..10).toList(), timestamps)

        assertTrue(
            timestamps.zipWithNext().all { (a, b) ->
                b > a
            }
        )
    }
}

