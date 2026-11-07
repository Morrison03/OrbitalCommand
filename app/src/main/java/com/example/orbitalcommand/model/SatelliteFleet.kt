
package com.example.orbitalcommand.model

/**
 * Defines the faults supported by the satellite simulator.
 *
 * Faults affect simulated spacecraft behavior and provide
 * operators with diagnostic scenarios to investigate.
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
 * Defines the permanent configuration of a satellite.
 *
 * Visualization radii preserve the relative altitude
 * ordering of spacecraft but are not physically to scale.
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
 * Defines the spacecraft included in the simulation.
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
 * Represents the current state of an individual satellite.
 *
 * Each spacecraft maintains its own telemetry, active
 * fault, and historical measurements.
 */
data class SatelliteState(
    val definition: SatelliteDefinition,
    val telemetry: SatelliteTelemetry,
    val fault: SatelliteFault = SatelliteFault.NONE,
    val faultStartedSecond: Int? = null,
    val history: List<SatelliteTelemetry> = listOf(telemetry)
) {

    /**
     * Indicates whether telemetry communication is available.
     */
    val isConnected: Boolean
        get() = fault != SatelliteFault.COMMUNICATION_LOSS

    /**
     * Indicates whether a simulated fault is active.
     */
    val hasFault: Boolean
        get() = fault != SatelliteFault.NONE

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
 * Represents the observable state of the mission dashboard.
 *
 * Contains the complete satellite fleet, current selection,
 * simulation clock, and recorded mission events.
 */
data class OrbitalUiState(
    val elapsedSeconds: Int,
    val satellites: List<SatelliteState>,
    val selectedSatelliteId: String,
    val events: List<MissionEvent> = emptyList()
) {

    /**
     * Returns the currently selected spacecraft.
     */
    val selectedSatellite: SatelliteState
        get() = satellites.first {
            it.definition.id == selectedSatelliteId
        }

    /**
     * Counts spacecraft with an active simulated fault.
     */
    val faultCount: Int
        get() = satellites.count { it.hasFault }

    /**
     * Returns all unresolved mission alerts.
     */
    val activeAlerts: List<MissionEvent>
        get() = events.filter { it.isActive }

    /**
     * Counts unresolved alerts that an operator
     * has not yet acknowledged.
     */
    val unacknowledgedAlertCount: Int
        get() = activeAlerts.count {
            !it.isAcknowledged
        }
}
