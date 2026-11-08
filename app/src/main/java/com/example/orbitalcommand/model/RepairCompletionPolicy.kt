
package com.example.orbitalcommand.model

/**
 * Describes whether a queued repair may be completed
 * and, if not, why the action is unavailable.
 */
enum class RepairCompletionStatus(
    val description: String
) {

    READY_AT_STATION(
        "Test-only repair completion is available."
    ),

    READY_IN_FORMATION(
        "SERVICER-01 is following the correct satellite. " +
                "Simulated repair completion is available."
    ),

    WORK_ORDER_NOT_QUEUED(
        "The repair work order must be queued."
    ),

    EQUIPMENT_MISMATCH(
        "The target no longer has the equipment " +
                "damage specified by this work order."
    ),

    DIFFERENT_MISSION(
        "The deployed vehicle is assigned to " +
                "a different repair mission."
    ),

    VEHICLE_NOT_IN_FORMATION(
        "The servicing vehicle must reestablish " +
                "formation with the target before repair."
    ),

    TARGET_TELEMETRY_UNAVAILABLE(
        "Current target telemetry is required " +
                "before completing a repair in formation."
    );

    /**
     * Indicates whether the repair-completion
     * operation is permitted.
     */
    val isAllowed: Boolean
        get() = this == READY_AT_STATION ||
                this == READY_IN_FORMATION
}

/**
 * Evaluates whether a maintenance work order can
 * be completed in the current mission state.
 *
 * A repair may be simulated from the home station
 * using the original test-only shortcut, or while
 * SERVICER-01 is actively following the correct target.
 *
 * Repair during deployment, approach, hold, or return
 * is prohibited. Formation repair also requires
 * a valid mission assignment and current telemetry.
 */
object RepairCompletionPolicy {

    const val MAX_TELEMETRY_AGE_SECONDS = 5

    /**
     * Evaluates repair eligibility without changing
     * application state.
     */
    fun evaluate(
        state: OrbitalUiState,
        order: RepairWorkOrder
    ): RepairCompletionStatus {

        if (order.status != RepairStatus.QUEUED) {
            return RepairCompletionStatus.WORK_ORDER_NOT_QUEUED
        }

        val target = state.satellites.firstOrNull {
            it.definition.id == order.satelliteId
        }

        if (
            target == null ||
            target.equipmentDamage != order.damage
        ) {
            return RepairCompletionStatus.EQUIPMENT_MISMATCH
        }

        val vehicle = state.servicingVehicle

        // Preserve the original test-only completion
        // capability while the vehicle is docked.
        if (vehicle.phase == ServicingVehiclePhase.DOCKED) {
            return RepairCompletionStatus.READY_AT_STATION
        }

        // A deployed vehicle can only service
        // the work order assigned to its mission.
        if (
            vehicle.workOrderId != order.id ||
            vehicle.targetSatelliteId != order.satelliteId ||
            state.preparedMission?.workOrderId != order.id
        ) {
            return RepairCompletionStatus.DIFFERENT_MISSION
        }

        // HOLDING after communication loss is not
        // the same as actively following the target.
        if (
            vehicle.phase !=
            ServicingVehiclePhase.FORMATION_FOLLOWING
        ) {
            return RepairCompletionStatus.VEHICLE_NOT_IN_FORMATION
        }

        // Require a recent successfully received
        // target packet for formation repair.
        val currentTelemetry =
            target.isConnected &&
                    target.telemetry.sampleSecond <=
                    state.elapsedSeconds &&
                    target.packetAgeSeconds(state.elapsedSeconds) <=
                    MAX_TELEMETRY_AGE_SECONDS

        if (!currentTelemetry) {
            return RepairCompletionStatus
                .TARGET_TELEMETRY_UNAVAILABLE
        }

        return RepairCompletionStatus.READY_IN_FORMATION
    }
}

