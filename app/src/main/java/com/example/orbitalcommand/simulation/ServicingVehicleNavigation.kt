
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
import kotlin.math.asin

/**
 * Controls scripted servicing vehicle movement,
 * approach, formation following, and return to station.
 *
 * Formation following maintains a fixed schematic
 * separation behind a moving satellite on its orbital
 * display track.
 *
 * This is a visualization and operator-interaction model,
 * not a physically accurate orbital guidance system.
 */
object ServicingVehicleNavigation {

    private const val DEPLOY_OFFSET = 0.035f

    private const val APPROACH_STEP = 0.045f
    private const val RETURN_STEP = 0.055f

    /**
     * Desired visual separation between the satellite
     * and servicing vehicle in normalized display units.
     */
    const val FORMATION_SEPARATION = 0.065f

    private const val FORMATION_TOLERANCE = 0.006f
    private const val DOCK_TOLERANCE = 0.025f

    private const val MAX_TELEMETRY_AGE_SECONDS = 5

    /**
     * Indicates whether received satellite telemetry
     * is current enough for scripted formation guidance.
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
     * Revalidates a prepared mission before dispatch.
     *
     * The associated work order must remain queued,
     * its equipment damage must still be active, and
     * the target must have current received telemetry.
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
                hasCurrentTargetTelemetry(target, atSecond)
    }

    /**
     * Determines the desired formation-following position.
     *
     * The servicer follows behind the satellite on the
     * same schematic orbital circle. The angular offset
     * is calculated to preserve approximately the same
     * visual separation for satellites at different radii.
     */
    fun formationPosition(
        target: SatelliteState
    ): VehiclePosition {

        val radius = target.definition.orbitRadiusFraction

        require(radius > FORMATION_SEPARATION / 2f) {
            "Target orbit is too small for formation spacing."
        }

        val halfAngle = asin(
            (
                    FORMATION_SEPARATION /
                            (2f * radius)
                    ).toDouble()
        )

        val angularSeparationDegrees = Math.toDegrees(
            2.0 * halfAngle
        ).toFloat()

        val followingAngle =
            target.telemetry.orbitAngleDegrees -
                    angularSeparationDegrees

        return OrbitalDisplayGeometry.onOrbit(
            angleDegrees = followingAngle,
            radiusFraction = radius
        )
    }

    /**
     * Deploys the vehicle near the servicing station.
     *
     * Deployment assigns the vehicle to the selected
     * repair work order without completing maintenance.
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
     * Begins guided movement toward the assigned
     * satellite's formation-following position.
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
            vehicle.targetSatelliteId == target.definition.id
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
     * Pauses an operator-controlled scripted approach.
     *
     * The vehicle's current display position is retained.
     * Active formation following is not automatically
     * stopped by reaching its target position.
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
     * Resumes guidance toward the moving target after
     * confirming that current telemetry is available.
     *
     * When the vehicle reaches its formation position,
     * it automatically returns to formation following.
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
            vehicle.targetSatelliteId == target.definition.id
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
     * Commands the vehicle to navigate back to OS-001.
     *
     * Return is available during deployment, approach,
     * formation following, or a navigation hold.
     */
    fun beginReturn(
        vehicle: ServicingVehicleState
    ): ServicingVehicleState {

        require(
            vehicle.phase == ServicingVehiclePhase.DEPLOYED ||
                    vehicle.phase == ServicingVehiclePhase.APPROACHING ||
                    vehicle.phase ==
                    ServicingVehiclePhase.FORMATION_FOLLOWING ||
                    vehicle.phase == ServicingVehiclePhase.HOLDING
        ) {
            "Vehicle cannot begin return from this state."
        }

        return vehicle.copy(
            phase = ServicingVehiclePhase.RETURNING,
            holdReason = null
        )
    }

    /**
     * Advances scripted vehicle movement by one
     * simulation second.
     *
     * During formation following, the vehicle's display
     * position is recalculated using the target's latest
     * received orbital angle. This causes the satellite
     * and servicer to orbit together.
     *
     * Unavailable telemetry suspends automatic guidance
     * without using stale position data.
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

                if (!isTargetTrackable(
                        vehicle,
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

                    val formationTarget = formationPosition(
                        requireNotNull(target)
                    )

                    val distance = vehicle.position.distanceTo(
                        formationTarget
                    )

                    if (distance <= FORMATION_TOLERANCE) {

                        vehicle.copy(
                            phase =
                                ServicingVehiclePhase.FORMATION_FOLLOWING,
                            position = formationTarget,
                            holdReason = null
                        )

                    } else {

                        val next = moveToward(
                            from = vehicle.position,
                            to = formationTarget,
                            maxStep = APPROACH_STEP
                        )

                        val inFormation =
                            next.distanceTo(formationTarget) <=
                                    FORMATION_TOLERANCE

                        vehicle.copy(
                            position = if (inFormation)
                                formationTarget
                            else
                                next,
                            phase = if (inFormation)
                                ServicingVehiclePhase.FORMATION_FOLLOWING
                            else
                                ServicingVehiclePhase.APPROACHING,
                            holdReason = null
                        )
                    }
                }
            }

            ServicingVehiclePhase.FORMATION_FOLLOWING -> {

                if (!isTargetTrackable(
                        vehicle,
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

                    vehicle.copy(
                        position = formationPosition(
                            requireNotNull(target)
                        ),
                        holdReason = null
                    )
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
     * Verifies that the assigned target is available
     * for current orbital formation guidance.
     */
    private fun isTargetTrackable(
        vehicle: ServicingVehicleState,
        target: SatelliteState?,
        atSecond: Int
    ): Boolean {

        return target != null &&
                vehicle.targetSatelliteId == target.definition.id &&
                hasCurrentTargetTelemetry(target, atSecond)
    }

    /**
     * Moves toward a target position while limiting
     * travel to a fixed schematic distance per step.
     */
    private fun moveToward(
        from: VehiclePosition,
        to: VehiclePosition,
        maxStep: Float
    ): VehiclePosition {

        val distance = from.distanceTo(to)

        if (distance <= maxStep) {
            return to
        }

        val fraction = maxStep / distance

        return VehiclePosition(
            x = from.x + (to.x - from.x) * fraction,
            y = from.y + (to.y - from.y) * fraction
        )
    }
}
