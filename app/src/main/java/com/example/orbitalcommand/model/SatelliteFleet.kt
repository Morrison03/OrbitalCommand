
package com.example.orbitalcommand.model

/**
 * Defines temporary faults available to the simulator.
 *
 * Temporary faults can be cleared independently of
 * persistent equipment damage.
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
 * Visualization radii preserve relative altitude
 * ordering but are not physically to scale.
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
 * Defines the spacecraft monitored by Orbital Command.
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
 * Represents the operating state of one spacecraft.
 *
 * telemetry contains the last packet received by
 * mission control.
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
    val onboardTelemetry: SatelliteTelemetry = telemetry,
    val equipmentDamage: EquipmentDamage = EquipmentDamage.NONE,
    val equipmentDamageStartedSecond: Int? = null
) {

    /**
     * Indicates whether the satellite can transmit
     * telemetry to mission control.
     */
    val isConnected: Boolean
        get() = fault != SatelliteFault.COMMUNICATION_LOSS &&
                onboardTelemetry.powerMode != PowerMode.OFFLINE

    /**
     * Indicates whether either a temporary fault or
     * persistent equipment damage is present.
     */
    val hasFault: Boolean
        get() = fault != SatelliteFault.NONE ||
                equipmentDamage != EquipmentDamage.NONE

    /**
     * Indicates whether physical servicing is required.
     */
    val requiresServicing: Boolean
        get() = equipmentDamage != EquipmentDamage.NONE

    /**
     * Indicates whether the modeled power system
     * has entered its offline state.
     */
    val isPowerDepleted: Boolean
        get() = onboardTelemetry.powerMode == PowerMode.OFFLINE

    /**
     * Calculates the age of the last received telemetry.
     */
    fun packetAgeSeconds(elapsedSeconds: Int): Int {

        return (
                elapsedSeconds - telemetry.sampleSecond
                ).coerceAtLeast(0)
    }
}

/**
 * Represents the complete observable mission-control state.
 *
 * Contains satellite data, mission events, and the
 * simulated maintenance workflow.
 */
data class OrbitalUiState(
    val elapsedSeconds: Int,
    val satellites: List<SatelliteState>,
    val selectedSatelliteId: String,
    val events: List<MissionEvent> = emptyList(),
    val repairOrders: List<RepairWorkOrder> = emptyList()
) {

    /**
     * Returns the satellite currently selected
     * in the dashboard.
     */
    val selectedSatellite: SatelliteState
        get() = satellites.first {
            it.definition.id == selectedSatelliteId
        }

    /**
     * Counts satellites with temporary faults
     * or persistent equipment damage.
     */
    val faultCount: Int
        get() = satellites.count { it.hasFault }

    /**
     * Returns all unresolved mission alerts.
     */
    val activeAlerts: List<MissionEvent>
        get() = events.filter { it.isActive }

    /**
     * Counts alerts that require acknowledgement.
     */
    val unacknowledgedAlertCount: Int
        get() = activeAlerts.count {
            !it.isAcknowledged
        }

    /**
     * Returns maintenance requests that have not
     * been completed.
     */
    val activeRepairOrders: List<RepairWorkOrder>
        get() = repairOrders.filter { it.isActive }

    /**
     * Counts queued maintenance requests.
     */
    val queuedRepairCount: Int
        get() = activeRepairOrders.count {
            it.status == RepairStatus.QUEUED
        }
}
