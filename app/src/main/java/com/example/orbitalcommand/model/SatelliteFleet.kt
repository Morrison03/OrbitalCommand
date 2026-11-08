
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
 * Represents the operating state of one spacecraft.
 *
 * telemetry contains the last packet received
 * by mission control.
 *
 * onboardTelemetry contains internally calculated
 * simulation state during communication outages.
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
     * Indicates whether the satellite is capable
     * of transmitting telemetry.
     */
    val isConnected: Boolean
        get() = fault != SatelliteFault.COMMUNICATION_LOSS &&
                onboardTelemetry.powerMode != PowerMode.OFFLINE

    /**
     * Indicates whether a temporary fault or
     * persistent equipment damage is active.
     */
    val hasFault: Boolean
        get() = fault != SatelliteFault.NONE ||
                equipmentDamage != EquipmentDamage.NONE

    /**
     * Indicates whether equipment servicing
     * is required.
     */
    val requiresServicing: Boolean
        get() = equipmentDamage != EquipmentDamage.NONE

    /**
     * Indicates whether the simulated electrical
     * power system is offline.
     */
    val isPowerDepleted: Boolean
        get() = onboardTelemetry.powerMode == PowerMode.OFFLINE

    /**
     * Returns the age of the most recently
     * received telemetry packet.
     */
    fun packetAgeSeconds(
        elapsedSeconds: Int
    ): Int {

        return (
                elapsedSeconds - telemetry.sampleSecond
                ).coerceAtLeast(0)
    }
}

/**
 * Represents the complete observable state of
 * Orbital Command's mission-control interface.
 *
 * The servicing station and servicing vehicle are
 * distinct simulation entities.
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
        ServicingVehicleState.dockedAt(servicingStation)
) {

    /**
     * Returns the currently selected satellite.
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
     * Counts unresolved alerts awaiting
     * operator acknowledgement.
     */
    val unacknowledgedAlertCount: Int
        get() = activeAlerts.count {
            !it.isAcknowledged
        }

    /**
     * Returns all outstanding repair requests.
     */
    val activeRepairOrders: List<RepairWorkOrder>
        get() = repairOrders.filter { it.isActive }

    /**
     * Counts repair requests queued for servicing.
     */
    val queuedRepairCount: Int
        get() = activeRepairOrders.count {
            it.status == RepairStatus.QUEUED
        }
}
