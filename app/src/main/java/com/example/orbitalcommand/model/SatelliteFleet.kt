
package com.example.orbitalcommand.model

/**
 * Defines temporary diagnostic faults supported
 * by the satellite simulation.
 *
 * These faults are independent of persistent
 * equipment damage requiring maintenance.
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
 * Orbital visualization radii preserve relative altitude
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
 * Provides the spacecraft monitored by Orbital Command.
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
 * telemetry is the last packet successfully received
 * by mission control.
 *
 * onboardTelemetry contains the internally modeled
 * state, including during a communications outage.
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
     * Indicates whether telemetry transmission
     * is currently possible.
     */
    val isConnected: Boolean
        get() = fault != SatelliteFault.COMMUNICATION_LOSS &&
                onboardTelemetry.powerMode != PowerMode.OFFLINE

    /**
     * Indicates whether any simulated diagnostic
     * fault or persistent equipment damage is active.
     */
    val hasFault: Boolean
        get() = fault != SatelliteFault.NONE ||
                equipmentDamage != EquipmentDamage.NONE

    /**
     * Indicates whether a maintenance operation
     * is required to repair physical equipment.
     */
    val requiresServicing: Boolean
        get() = equipmentDamage != EquipmentDamage.NONE

    /**
     * Indicates whether the internal power system
     * is in the depleted/offline state.
     */
    val isPowerDepleted: Boolean
        get() = onboardTelemetry.powerMode == PowerMode.OFFLINE

    /**
     * Returns the age of the last successfully
     * received telemetry packet.
     */
    fun packetAgeSeconds(elapsedSeconds: Int): Int {

        return (
                elapsedSeconds - telemetry.sampleSecond
                ).coerceAtLeast(0)
    }
}

/**
 * Represents all observable mission-control state.
 *
 * Includes satellite telemetry, operational events,
 * repair work orders, the servicing station, and
 * the currently prepared servicing mission.
 */
data class OrbitalUiState(
    val elapsedSeconds: Int,
    val satellites: List<SatelliteState>,
    val selectedSatelliteId: String,
    val events: List<MissionEvent> = emptyList(),
    val repairOrders: List<RepairWorkOrder> = emptyList(),
    val servicingStation: ServicingStation =
        OrbitalServicing.stationAt(0),
    val preparedMission: ServicingMissionPlan? = null
) {

    /**
     * Returns the satellite currently selected
     * for detailed inspection.
     */
    val selectedSatellite: SatelliteState
        get() = satellites.first {
            it.definition.id == selectedSatelliteId
        }

    /**
     * Counts satellites experiencing temporary
     * faults or persistent equipment damage.
     */
    val faultCount: Int
        get() = satellites.count { it.hasFault }

    /**
     * Returns all unresolved mission alerts.
     */
    val activeAlerts: List<MissionEvent>
        get() = events.filter { it.isActive }

    /**
     * Counts alerts that have not yet been
     * acknowledged by an operator.
     */
    val unacknowledgedAlertCount: Int
        get() = activeAlerts.count {
            !it.isAcknowledged
        }

    /**
     * Returns repair requests that have not
     * been marked as completed.
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
