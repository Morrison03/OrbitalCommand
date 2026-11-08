
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.OrbitalDisplayGeometry
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairWorkOrder
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.ServicingMissionPlan
import com.example.orbitalcommand.model.ServicingStation
import com.example.orbitalcommand.model.ServicingVehiclePhase
import com.example.orbitalcommand.model.ServicingVehicleState
import com.example.orbitalcommand.model.VehicleHoldReason
import com.example.orbitalcommand.model.VehiclePosition

/**
 * Advances a servicing vehicle through a
 * simplified orbital maintenance demonstration.
 *
 * The movement algorithm is intended for testing
 * mission states and operator controls.
 *
 * It is not flight software, orbital guidance,
 * rendezvous planning, or collision avoidance.
 */
object ServicingVehicleNavigation {

    private const val DEPLOY_OFFSET = 0.035f
    private const val APPROACH_STEP = 0.045f
    private const val RETURN_STEP = 0.055f
    private const val TARGET_STANDOFF = 0.065f
    private const val DOCK_TOLERANCE = 0.025f
    private const val POSITION_EPSILON = 0.0005f

    private const val MAX_TELEMETRY_AGE_SECONDS = 5

    /**
     * Determines whether the target has sufficiently
     * recent received telemetry for guided movement.
     */
    fun hasCurrentTargetTelemetry(
        target: SatelliteState,
        atSecond: Int
    ): Boolean {

        return target.isConnected &&
                target.telemetry.sampleSecond <= atSecond &&
                target.packetAgeSeconds(atSecond) <=
                MAX_TELEMETRY_AGE_SECONDS
    }

    /**
     * Revalidates the work order and target at dispatch.
     *
     * Preparation alone is not enough to authorize
     * dispatch because the target's condition may change.
     */
    fun isDispatchReady(
        plan: ServicingMissionPlan,
        orders: List<RepairWorkOrder>,
        satellites: List<SatelliteState>,
        atSecond: Int
    ): Boolean {

        val order = orders.firstOrNull {
            it.id == plan.workOrderId
        } ?: return false

        val target = satellites.firstOrNull {
            it.definition.id == plan.targetSatelliteId
        } ?: return false

        return order.status == RepairStatus.QUEUED &&
                order.satelliteId == plan.targetSatelliteId &&
                order.damage == plan.damage &&
                target.equipmentDamage == plan.damage &&
                hasCurrentTargetTelemetry(
                    target,
                    atSecond
                )
    }

    /**
     * Dispatches a docked servicing vehicle from OS-001.
     *
     * Deployment places the vehicle a small visual
     * distance outside the station.
     */
    fun dispatch(
        vehicle: ServicingVehicleState,
        plan: ServicingMissionPlan,
        station: ServicingStation
    ): ServicingVehicleState {

        require(
            vehicle.phase == ServicingVehiclePhase.DOCKED
        ) {
            "Only a docked vehicle can be dispatched."
        }

        return vehicle.copy(
            phase = ServicingVehiclePhase.DEPLOYED,
            position = OrbitalDisplayGeometry.onOrbit(
                station.orbitAngleDegrees,
                station.orbitRadiusFraction + DEPLOY_OFFSET
            ),
            workOrderId = plan.workOrderId,
            targetSatelliteId = plan.targetSatelliteId,
            holdReason = null
        )
    }

    /**
     * Starts the scripted approach to the assigned
     * satellite after validating target telemetry.
     */
    fun beginApproach(
        vehicle: ServicingVehicleState,
        target: SatelliteState,
        atSecond: Int
    ): ServicingVehicleState {

        require(
            vehicle.phase == ServicingVehiclePhase.DEPLOYED
        ) {
            "Vehicle must be deployed to begin approach."
        }

        require(
            vehicle.targetSatelliteId ==
                    target.definition.id
        ) {
            "Incorrect target satellite."
        }

        require(
            hasCurrentTargetTelemetry(target, atSecond)
        ) {
            "Current target telemetry is required."
        }

        return vehicle.copy(
            phase = ServicingVehiclePhase.APPROACHING,
            holdReason = null
        )
    }

    /**
     * Stops an active approach at its current
     * schematic position.
     */
    fun hold(
        vehicle: ServicingVehicleState
    ): ServicingVehicleState {

        require(
            vehicle.phase == ServicingVehiclePhase.APPROACHING
        ) {
            "Only an approaching vehicle can be held."
        }

        return vehicle.copy(
            phase = ServicingVehiclePhase.HOLDING,
            holdReason = VehicleHoldReason.OPERATOR
        )
    }

    /**
     * Resumes approach after checking that target
     * telemetry is available and current.
     */
    fun resumeApproach(
        vehicle: ServicingVehicleState,
        target: SatelliteState,
        atSecond: Int
    ): ServicingVehicleState {

        require(
            vehicle.phase == ServicingVehiclePhase.HOLDING
        ) {
            "Vehicle must be holding to resume."
        }

        require(
            vehicle.targetSatelliteId ==
                    target.definition.id
        ) {
            "Incorrect target satellite."
        }

        require(
            hasCurrentTargetTelemetry(target, atSecond)
        ) {
            "Current target telemetry is required."
        }

        return vehicle.copy(
            phase = ServicingVehiclePhase.APPROACHING,
            holdReason = null
        )
    }

    /**
     * Commands a deployed vehicle to return
     * to its moving home station.
     */
    fun beginReturn(
        vehicle: ServicingVehicleState
    ): ServicingVehicleState {

        require(
            vehicle.phase == ServicingVehiclePhase.DEPLOYED ||
                    vehicle.phase ==
                    ServicingVehiclePhase.APPROACHING ||
                    vehicle.phase ==
                    ServicingVehiclePhase.HOLDING
        ) {
            "Vehicle cannot begin return from this state."
        }

        return vehicle.copy(
            phase = ServicingVehiclePhase.RETURNING,
            holdReason = null
        )
    }

    /**
     * Advances the vehicle by one simulation step.
     *
     * Guided approach stops when telemetry becomes
     * unavailable or the target standoff is reached.
     *
     * Reaching standoff does not repair the satellite.
     */
    fun advance(
        vehicle: ServicingVehicleState,
        station: ServicingStation,
        target: SatelliteState?,
        atSecond: Int
    ): ServicingVehicleState {

        val stationPosition =
            OrbitalDisplayGeometry.onOrbit(
                station.orbitAngleDegrees,
                station.orbitRadiusFraction
            )

        return when (vehicle.phase) {

            ServicingVehiclePhase.DOCKED -> {

                vehicle.copy(
                    position = stationPosition
                )
            }

            ServicingVehiclePhase.DEPLOYED,
            ServicingVehiclePhase.HOLDING -> {
                vehicle
            }

            ServicingVehiclePhase.APPROACHING -> {

                if (
                    target == null ||
                    target.definition.id !=
                    vehicle.targetSatelliteId ||
                    !hasCurrentTargetTelemetry(
                        target,
                        atSecond
                    )
                ) {

                    vehicle.copy(
                        phase = ServicingVehiclePhase.HOLDING,
                        holdReason =
                            VehicleHoldReason.TELEMETRY_UNAVAILABLE
                    )

                } else {

                    val targetPosition =
                        OrbitalDisplayGeometry.onOrbit(
                            target.telemetry.orbitAngleDegrees,
                            target.definition.orbitRadiusFraction
                        )

                    val distance =
                        vehicle.position.distanceTo(
                            targetPosition
                        )

                    if (
                        distance <=
                        TARGET_STANDOFF + POSITION_EPSILON
                    ) {

                        vehicle.copy(
                            phase = ServicingVehiclePhase.HOLDING,
                            holdReason =
                                VehicleHoldReason.TARGET_STANDOFF
                        )

                    } else {

                        val step = minOf(
                            APPROACH_STEP,
                            distance - TARGET_STANDOFF
                        )

                        val next = moveToward(
                            from = vehicle.position,
                            to = targetPosition,
                            maxStep = step
                        )

                        val arrived =
                            next.distanceTo(targetPosition) <=
                                    TARGET_STANDOFF +
                                    POSITION_EPSILON

                        vehicle.copy(
                            position = next,
                            phase = if (arrived)
                                ServicingVehiclePhase.HOLDING
                            else
                                ServicingVehiclePhase.APPROACHING,
                            holdReason = if (arrived)
                                VehicleHoldReason.TARGET_STANDOFF
                            else
                                null
                        )
                    }
                }
            }

            ServicingVehiclePhase.RETURNING -> {

                val next = moveToward(
                    from = vehicle.position,
                    to = stationPosition,
                    maxStep = RETURN_STEP
                )

                if (
                    next.distanceTo(stationPosition) <=
                    DOCK_TOLERANCE
                ) {

                    ServicingVehicleState.dockedAt(station)

                } else {

                    vehicle.copy(
                        position = next
                    )
                }
            }
        }
    }

    /**
     * Moves a point toward another point without
     * exceeding the permitted single-step distance.
     */
    private fun moveToward(
        from: VehiclePosition,
        to: VehiclePosition,
        maxStep: Float
    ): VehiclePosition {

        val distance = from.distanceTo(to)

        if (
            distance <= maxStep ||
            distance <= POSITION_EPSILON
        ) {
            return to
        }

        val fraction = maxStep / distance

        return VehiclePosition(
            x = from.x +
                    (to.x - from.x) * fraction,
            y = from.y +
                    (to.y - from.y) * fraction
        )
    }
}

