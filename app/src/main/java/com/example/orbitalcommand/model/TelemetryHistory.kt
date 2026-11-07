
package com.example.orbitalcommand.model

/**
 * Maintains a bounded collection of received telemetry.
 *
 * Only the most recent measurements are retained to
 * prevent historical data from growing indefinitely
 * during long-running simulations.
 */
object TelemetryHistory {

    const val MAX_SAMPLES = 120

    /**
     * Adds a received telemetry packet to the history.
     *
     * Packets must be chronological. A packet received for
     * an existing simulation second replaces the previous
     * packet rather than creating a duplicate.
     *
     * Missing simulation seconds are preserved as gaps
     * between received packets.
     *
     * @param history Existing telemetry measurements.
     * @param sample Newly received telemetry packet.
     * @return Updated history containing at most MAX_SAMPLES.
     */
    fun append(
        history: List<SatelliteTelemetry>,
        sample: SatelliteTelemetry
    ): List<SatelliteTelemetry> {

        val lastSample = history.lastOrNull()

        require(
            lastSample == null ||
                    sample.sampleSecond >= lastSample.sampleSecond
        ) {
            "Telemetry packets must be chronological."
        }

        if (lastSample?.sampleSecond == sample.sampleSecond) {

            return history.dropLast(1) + sample
        }

        return (history + sample).takeLast(MAX_SAMPLES)
    }
}
