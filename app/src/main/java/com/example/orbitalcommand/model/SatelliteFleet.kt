
package com.example.orbitalcommand.model

/**
 * Defines temporary diagnostic faults supported
 * by the satellite simulation.
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
 * Defines one satellite's permanent simulation configuration.
 *
 * Visualization radii preserve the relative altitude
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
 * Provides the satellite fleet monitored by Orbital Command.
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
 * Represents the operational state of one spacecraft.
 *
 * telemetry stores the most recently received packet.
 * onboardTelemetry stores the internal simulation state,
 * including when communications are unavailable.
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
     * Indicates whether the spacecraft can transmit telemetry.
     */
    val isConnected: Boolean
        get() = fault != SatelliteFault.COMMUNICATION_LOSS &&
                onboardTelemetry.powerMode != PowerMode.OFFLINE

    /**
     * Indicates whether any temporary or persistent fault exists.
     */
    val hasFault: Boolean
        get() = fault != SatelliteFault.NONE ||
                equipmentDamage != EquipmentDamage.NONE

    /**
     * Indicates whether equipment requires servicing.
     */
    val requiresServicing: Boolean
        get() = equipmentDamage != EquipmentDamage.NONE

    /**
     * Indicates whether modeled spacecraft power is depleted.
     */
    val isPowerDepleted: Boolean
        get() = onboardTelemetry.powerMode == PowerMode.OFFLINE

    /**
     * Returns the age of the last received telemetry packet.
     */
    fun packetAgeSeconds(elapsedSeconds: Int): Int =
        (elapsedSeconds - telemetry.sampleSecond).coerceAtLeast(0)
}

/**
 * Represents all observable mission-control state.
 *
 * Includes satellite monitoring, alerts, repair orders,
 * servicing station, robotic vehicle, and the current
 * interactive maintenance procedure.
 */
data class OrbitalUiState(
    val elapsedSeconds: Int,
    val satellites: List<SatelliteState>,
    val selectedSatelliteId: String,
    val events: List<MissionEvent> = emptyList(),
    val repairOrders: List<RepairWorkOrder> = emptyList(),
    val servicingStation: ServicingStation =
        OrbitalServicing.stationAt(0),
    val preparedMission: ServicingMissionPlan? = null,
    val servicingVehicle: ServicingVehicleState =
        ServicingVehicleState.dockedAt(servicingStation),
    val repairSession: RoboticRepairSession? = null
) {

    /**
     * Returns the currently selected spacecraft.
     */
    val selectedSatellite: SatelliteState
        get() = satellites.first {
            it.definition.id == selectedSatelliteId
        }

    /**
     * Counts spacecraft with active faults or damage.
     */
    val faultCount: Int
        get() = satellites.count { it.hasFault }

    /**
     * Returns unresolved mission alerts.
     */
    val activeAlerts: List<MissionEvent>
        get() = events.filter { it.isActive }

    /**
     * Counts alerts still awaiting acknowledgement.
     */
    val unacknowledgedAlertCount: Int
        get() = activeAlerts.count { !it.isAcknowledged }

    /**
     * Returns unresolved repair work orders.
     */
    val activeRepairOrders: List<RepairWorkOrder>
        get() = repairOrders.filter { it.isActive }

    /**
     * Counts maintenance requests awaiting servicing.
     */
    val queuedRepairCount: Int
        get() = activeRepairOrders.count {
            it.status == RepairStatus.QUEUED
        }
}
