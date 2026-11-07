
package com.example.orbitalcommand.model

/**
 * Defines the faults supported by the satellite simulator.
 */
enum class SatelliteFault(
    val displayName: String,
    val description: String
) {

    NONE(
        "No Fault",
        "All simulated systems are operating normally."
    ),

    POWER_DRAIN(
        "Power Drain",
        "Battery consumption is significantly increased."
    ),

    THERMAL_OVERLOAD(
        "Thermal Overload",
        "The satellite experiences abnormal heating."
    ),

    PAYLOAD_FAILURE(
        "Payload Failure",
        "The satellite's primary payload is unavailable."
    ),

    COMMUNICATION_LOSS(
        "Communication Loss",
        "The ground station stops receiving telemetry."
    )
}

/**
 * Defines the configuration of a simulated satellite.
 *
 * Orbital visualization radii preserve the relative
 * ordering of satellite altitudes but are not to scale.
 */
data class SatelliteDefinition(
    val id: String,
    val name: String,
    val baseAltitudeKm: Double,
    val baseVelocityKmS: Double,
    val initialBatteryPercent: Double,
    val orbitPhaseDegrees: Float,
    val orbitRadiusFraction: Float
)

/**
 * Contains the satellites monitored by Orbital Command.
 */
object SatelliteCatalog {

    val satellites = listOf(

        SatelliteDefinition(
            id = "SAT-001",
            name = "Earth Observation",
            baseAltitudeKm = 525.0,
            baseVelocityKmS = 7.61,
            initialBatteryPercent = 87.0,
            orbitPhaseDegrees = 0f,
            orbitRadiusFraction = 0.37f
        ),

        SatelliteDefinition(
            id = "SAT-002",
            name = "Communications",
            baseAltitudeKm = 610.0,
            baseVelocityKmS = 7.54,
            initialBatteryPercent = 94.0,
            orbitPhaseDegrees = 125f,
            orbitRadiusFraction = 0.44f
        ),

        SatelliteDefinition(
            id = "SAT-003",
            name = "Scientific Research",
            baseAltitudeKm = 455.0,
            baseVelocityKmS = 7.65,
            initialBatteryPercent = 78.0,
            orbitPhaseDegrees = 250f,
            orbitRadiusFraction = 0.30f
        )
    )
}

/**
 * Represents the operating state of an individual satellite.
 *
 * telemetry contains the last packet received by the
 * ground interface.
 *
 * onboardTelemetry contains the internally calculated
 * simulation state, including during communication loss.
 */
data class SatelliteState(
    val definition: SatelliteDefinition,
    val telemetry: SatelliteTelemetry,
    val fault: SatelliteFault = SatelliteFault.NONE,
    val faultStartedSecond: Int? = null,
    val history: List<SatelliteTelemetry> = listOf(telemetry),
    val onboardTelemetry: SatelliteTelemetry = telemetry
) {

    /**
     * Indicates whether the satellite is capable of
     * delivering a new telemetry packet.
     *
     * Both communications failures and complete power
     * depletion prevent telemetry reception.
     */
    val isConnected: Boolean
        get() = fault != SatelliteFault.COMMUNICATION_LOSS &&
                onboardTelemetry.powerMode != PowerMode.OFFLINE

    /**
     * Indicates whether a manually simulated fault is active.
     */
    val hasFault: Boolean
        get() = fault != SatelliteFault.NONE

    /**
     * Indicates whether the internal power model
     * is currently in its depleted/offline state.
     */
    val isPowerDepleted: Boolean
        get() = onboardTelemetry.powerMode == PowerMode.OFFLINE

    /**
     * Returns the age of the last received telemetry packet.
     */
    fun packetAgeSeconds(elapsedSeconds: Int): Int {

        return (
                elapsedSeconds - telemetry.sampleSecond
                ).coerceAtLeast(0)
    }
}

/**
 * Represents the complete observable state of
 * the satellite mission-control interface.
 */
data class OrbitalUiState(
    val elapsedSeconds: Int,
    val satellites: List<SatelliteState>,
    val selectedSatelliteId: String,
    val events: List<MissionEvent> = emptyList()
) {

    /**
     * Returns the selected spacecraft.
     */
    val selectedSatellite: SatelliteState
        get() = satellites.first {
            it.definition.id == selectedSatelliteId
        }

    /**
     * Counts spacecraft with manually injected faults.
     */
    val faultCount: Int
        get() = satellites.count { it.hasFault }

    /**
     * Returns all unresolved operational alerts.
     */
    val activeAlerts: List<MissionEvent>
        get() = events.filter { it.isActive }

    /**
     * Counts unresolved alerts that have not
     * been acknowledged by the operator.
     */
    val unacknowledgedAlertCount: Int
        get() = activeAlerts.count {
            !it.isAcknowledged
        }
}
