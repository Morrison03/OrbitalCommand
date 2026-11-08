
package com.example.orbitalcommand.simulation

import com.example.orbitalcommand.model.EquipmentDamage
import com.example.orbitalcommand.model.OrbitalDisplayGeometry
import com.example.orbitalcommand.model.OrbitalServicing
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairWorkOrder
import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.ServicingMissionPlan
import com.example.orbitalcommand.model.ServicingVehiclePhase
import com.example.orbitalcommand.model.ServicingVehicleState
import com.example.orbitalcommand.model.VehicleHoldReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Verifies servicing vehicle deployment, approach,
 * synchronized formation following, telemetry safety,
 * return navigation, and docking behavior.
 */
class ServicingVehicleNavigationTest {

    private val definition =
        SatelliteCatalog.satellites.first()

    private val station =
        OrbitalServicing.stationAt(0)

    /**
     * Creates a satellite with persistent equipment
     * damage for navigation testing.
     */
    private fun target(
        sampleSecond: Int = 0,
        connected: Boolean = true
    ): SatelliteState {

        val telemetry = SatelliteSimulator.snapshot(
            second = sampleSecond,
            satellite = definition
        )

        return SatelliteState(
            definition = definition,
            telemetry = telemetry,
            onboardTelemetry = telemetry,
            fault = if (connected)
                SatelliteFault.NONE
            else
                SatelliteFault.COMMUNICATION_LOSS,
            equipmentDamage =
                EquipmentDamage.SOLAR_ARRAY_DAMAGE
        )
    }

    /**
     * Creates an eligible queued maintenance order.
     */
    private fun queuedOrder(): RepairWorkOrder {

        return RepairWorkOrder(
            id = 1L,
            satelliteId = definition.id,
            damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            createdAtSecond = 0,
            status = RepairStatus.QUEUED,
            queuedAtSecond = 0
        )
    }

    /**
     * Creates a mission plan targeting the
     * queued repair work order.
     */
    private fun plan(): ServicingMissionPlan {

        return ServicingMissionPlan(
            workOrderId = 1L,
            targetSatelliteId = definition.id,
            damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            preparedAtSecond = 0,
            lastReceivedTelemetrySecond = 0,
            telemetryVerificationRequired = false
        )
    }

    /**
     * Creates an unassigned vehicle docked at OS-001.
     */
    private fun dockedVehicle(): ServicingVehicleState {

        return ServicingVehicleState.dockedAt(station)
    }

    /**
     * Creates a vehicle beginning its guided approach.
     */
    private fun approachingVehicle(): ServicingVehicleState {

        val deployed = ServicingVehicleNavigation.dispatch(
            vehicle = dockedVehicle(),
            plan = plan(),
            station = station
        )

        return ServicingVehicleNavigation.beginApproach(
            vehicle = deployed,
            target = target(),
            atSecond = 0
        )
    }

    /**
     * Advances scripted approach until the vehicle
     * establishes formation with the moving satellite.
     *
     * Returns the vehicle and the simulation second
     * at which formation was established.
     */
    private fun reachFormation():
            Pair<ServicingVehicleState, Int> {

        var vehicle = approachingVehicle()

        for (second in 1..120) {

            vehicle = ServicingVehicleNavigation.advance(
                vehicle = vehicle,
                station = OrbitalServicing.stationAt(second),
                target = target(sampleSecond = second),
                atSecond = second
            )

            if (
                vehicle.phase ==
                ServicingVehiclePhase.FORMATION_FOLLOWING
            ) {
                return vehicle to second
            }
        }

        error(
            "Vehicle did not establish formation."
        )
    }

    /**
     * Verifies the initial vehicle state.
     */
    @Test
    fun vehicleStartsDocked() {

        val vehicle = dockedVehicle()

        assertEquals(
            ServicingVehiclePhase.DOCKED,
            vehicle.phase
        )

        assertFalse(vehicle.isAwayFromStation)
        assertEquals(null, vehicle.workOrderId)
        assertEquals(null, vehicle.targetSatelliteId)
    }

    /**
     * Confirms that a docked vehicle follows
     * its independently moving home station.
     */
    @Test
    fun dockedVehicleFollowsStation() {

        val nextStation = OrbitalServicing.stationAt(10)

        val updated = ServicingVehicleNavigation.advance(
            vehicle = dockedVehicle(),
            station = nextStation,
            target = null,
            atSecond = 10
        )

        val expected = OrbitalDisplayGeometry.onOrbit(
            angleDegrees = nextStation.orbitAngleDegrees,
            radiusFraction = nextStation.orbitRadiusFraction
        )

        assertEquals(
            expected.x,
            updated.position.x,
            0.0001f
        )

        assertEquals(
            expected.y,
            updated.position.y,
            0.0001f
        )
    }

    /**
     * Verifies that dispatch assigns the correct
     * maintenance mission to the vehicle.
     */
    @Test
    fun dispatchAssignsVehicleMission() {

        val deployed = ServicingVehicleNavigation.dispatch(
            vehicle = dockedVehicle(),
            plan = plan(),
            station = station
        )

        assertEquals(
            ServicingVehiclePhase.DEPLOYED,
            deployed.phase
        )

        assertTrue(deployed.isAwayFromStation)

        assertEquals(
            1L,
            deployed.workOrderId
        )

        assertEquals(
            definition.id,
            deployed.targetSatelliteId
        )
    }

    /**
     * Prevents a second deployment while
     * the vehicle is already deployed.
     */
    @Test(expected = IllegalArgumentException::class)
    fun deployedVehicleCannotBeDispatchedAgain() {

        val deployed = ServicingVehicleNavigation.dispatch(
            dockedVehicle(),
            plan(),
            station
        )

        ServicingVehicleNavigation.dispatch(
            deployed,
            plan(),
            station
        )
    }

    /**
     * Verifies that a valid queued repair and
     * current target telemetry permit dispatch.
     */
    @Test
    fun validQueuedRepairAllowsDispatch() {

        assertTrue(
            ServicingVehicleNavigation.isDispatchReady(
                plan = plan(),
                orders = listOf(queuedOrder()),
                satellites = listOf(target()),
                atSecond = 0
            )
        )
    }

    /**
     * Prevents dispatching an unqueued repair order.
     */
    @Test
    fun unqueuedRepairBlocksDispatch() {

        val openOrder = queuedOrder().copy(
            status = RepairStatus.OPEN,
            queuedAtSecond = null
        )

        assertFalse(
            ServicingVehicleNavigation.isDispatchReady(
                plan = plan(),
                orders = listOf(openOrder),
                satellites = listOf(target()),
                atSecond = 0
            )
        )
    }

    /**
     * Prevents dispatch when received target
     * telemetry is unavailable.
     */
    @Test
    fun disconnectedTargetBlocksDispatch() {

        assertFalse(
            ServicingVehicleNavigation.isDispatchReady(
                plan = plan(),
                orders = listOf(queuedOrder()),
                satellites = listOf(
                    target(connected = false)
                ),
                atSecond = 0
            )
        )
    }

    /**
     * Prevents dispatch using excessively old
     * target position information.
     */
    @Test
    fun staleTelemetryBlocksDispatch() {

        assertFalse(
            ServicingVehicleNavigation.isDispatchReady(
                plan = plan(),
                orders = listOf(queuedOrder()),
                satellites = listOf(
                    target(sampleSecond = 0)
                ),
                atSecond = 20
            )
        )
    }

    /**
     * Verifies that the formation position lies
     * on the target satellite's orbital track.
     */
    @Test
    fun formationPositionUsesSatelliteOrbit() {

        val spacecraft = target()

        val formation =
            ServicingVehicleNavigation.formationPosition(
                spacecraft
            )

        val radius = hypot(
            formation.x,
            formation.y
        )

        assertEquals(
            spacecraft.definition.orbitRadiusFraction,
            radius,
            0.0001f
        )
    }

    /**
     * Confirms that formation following maintains
     * the configured separation from the satellite.
     */
    @Test
    fun formationPositionMaintainsSeparation() {

        val spacecraft = target()

        val satellitePosition =
            OrbitalDisplayGeometry.onOrbit(
                spacecraft.telemetry.orbitAngleDegrees,
                spacecraft.definition.orbitRadiusFraction
            )

        val formation =
            ServicingVehicleNavigation.formationPosition(
                spacecraft
            )

        assertEquals(
            ServicingVehicleNavigation.FORMATION_SEPARATION,
            formation.distanceTo(satellitePosition),
            0.0001f
        )
    }

    /**
     * Verifies that beginning approach changes
     * the vehicle into the approaching phase.
     */
    @Test
    fun approachBeginsAfterDeployment() {

        assertEquals(
            ServicingVehiclePhase.APPROACHING,
            approachingVehicle().phase
        )
    }

    /**
     * Confirms that scripted approach moves
     * toward the satellite's formation position.
     */
    @Test
    fun approachChangesVehiclePosition() {

        val approaching = approachingVehicle()

        val updated = ServicingVehicleNavigation.advance(
            vehicle = approaching,
            station = OrbitalServicing.stationAt(1),
            target = target(sampleSecond = 1),
            atSecond = 1
        )

        assertTrue(
            updated.position != approaching.position
        )
    }

    /**
     * Confirms that reaching the satellite causes
     * formation following rather than a stationary hold.
     */
    @Test
    fun reachingSatelliteStartsFormationFollowing() {

        val (vehicle, _) = reachFormation()

        assertEquals(
            ServicingVehiclePhase.FORMATION_FOLLOWING,
            vehicle.phase
        )

        assertEquals(
            null,
            vehicle.holdReason
        )
    }

    /**
     * Verifies that the servicing vehicle continues
     * orbiting as its target satellite moves.
     */
    @Test
    fun formationFollowingMovesWithSatellite() {

        val (initialVehicle, formedAt) = reachFormation()

        val nextSecond = formedAt + 1

        val spacecraft = target(
            sampleSecond = nextSecond
        )

        val updated = ServicingVehicleNavigation.advance(
            vehicle = initialVehicle,
            station = OrbitalServicing.stationAt(nextSecond),
            target = spacecraft,
            atSecond = nextSecond
        )

        assertEquals(
            ServicingVehiclePhase.FORMATION_FOLLOWING,
            updated.phase
        )

        assertTrue(
            updated.position != initialVehicle.position
        )

        val expected =
            ServicingVehicleNavigation.formationPosition(
                spacecraft
            )

        assertEquals(
            expected.x,
            updated.position.x,
            0.0001f
        )

        assertEquals(
            expected.y,
            updated.position.y,
            0.0001f
        )
    }

    /**
     * Verifies that separation remains constant
     * throughout multiple satellite orbital updates.
     */
    @Test
    fun formationSeparationRemainsStableOverTime() {

        val (formedVehicle, formedAt) = reachFormation()

        var vehicle = formedVehicle

        for (second in formedAt + 1..formedAt + 20) {

            val spacecraft = target(
                sampleSecond = second
            )

            vehicle = ServicingVehicleNavigation.advance(
                vehicle = vehicle,
                station = OrbitalServicing.stationAt(second),
                target = spacecraft,
                atSecond = second
            )

            val satellitePosition =
                OrbitalDisplayGeometry.onOrbit(
                    spacecraft.telemetry.orbitAngleDegrees,
                    spacecraft.definition.orbitRadiusFraction
                )

            assertEquals(
                ServicingVehicleNavigation.FORMATION_SEPARATION,
                vehicle.position.distanceTo(
                    satellitePosition
                ),
                0.001f
            )

            assertEquals(
                ServicingVehiclePhase.FORMATION_FOLLOWING,
                vehicle.phase
            )
        }
    }

    /**
     * Confirms that the operator can explicitly
     * pause the scripted approach.
     */
    @Test
    fun operatorCanPauseApproach() {

        val approaching = approachingVehicle()

        val holding = ServicingVehicleNavigation.hold(
            approaching
        )

        val updated = ServicingVehicleNavigation.advance(
            vehicle = holding,
            station = OrbitalServicing.stationAt(1),
            target = target(sampleSecond = 1),
            atSecond = 1
        )

        assertEquals(
            ServicingVehiclePhase.HOLDING,
            updated.phase
        )

        assertEquals(
            VehicleHoldReason.OPERATOR,
            updated.holdReason
        )

        assertEquals(
            holding.position,
            updated.position
        )
    }

    /**
     * Verifies that losing target communication
     * during approach suspends automatic guidance.
     */
    @Test
    fun lostTelemetryStopsApproach() {

        val approaching = approachingVehicle()

        val updated = ServicingVehicleNavigation.advance(
            vehicle = approaching,
            station = OrbitalServicing.stationAt(1),
            target = target(
                sampleSecond = 0,
                connected = false
            ),
            atSecond = 1
        )

        assertEquals(
            ServicingVehiclePhase.HOLDING,
            updated.phase
        )

        assertEquals(
            VehicleHoldReason.TELEMETRY_UNAVAILABLE,
            updated.holdReason
        )
    }

    /**
     * Verifies that a formation-following vehicle
     * suspends navigation when telemetry is lost.
     */
    @Test
    fun lostTelemetrySuspendsFormationFollowing() {

        val (following, formedAt) = reachFormation()

        val updated = ServicingVehicleNavigation.advance(
            vehicle = following,
            station = OrbitalServicing.stationAt(formedAt + 1),
            target = target(
                sampleSecond = formedAt,
                connected = false
            ),
            atSecond = formedAt + 1
        )

        assertEquals(
            ServicingVehiclePhase.HOLDING,
            updated.phase
        )

        assertEquals(
            VehicleHoldReason.TELEMETRY_UNAVAILABLE,
            updated.holdReason
        )

        assertEquals(
            following.position,
            updated.position
        )
    }

    /**
     * Prevents guidance from resuming while
     * target telemetry remains unavailable.
     */
    @Test(expected = IllegalArgumentException::class)
    fun cannotResumeWithoutTelemetry() {

        val holding = ServicingVehicleNavigation.hold(
            approachingVehicle()
        )

        ServicingVehicleNavigation.resumeApproach(
            vehicle = holding,
            target = target(connected = false),
            atSecond = 0
        )
    }

    /**
     * Confirms that guidance can resume once
     * the satellite's telemetry becomes available.
     */
    @Test
    fun restoredTelemetryAllowsApproachToResume() {

        val holding = ServicingVehicleNavigation.hold(
            approachingVehicle()
        )

        val resumed =
            ServicingVehicleNavigation.resumeApproach(
                vehicle = holding,
                target = target(sampleSecond = 2),
                atSecond = 2
            )

        assertEquals(
            ServicingVehiclePhase.APPROACHING,
            resumed.phase
        )

        assertEquals(
            null,
            resumed.holdReason
        )
    }

    /**
     * Verifies that formation following does
     * not automatically repair damaged equipment.
     */
    @Test
    fun formationFollowingDoesNotCompleteRepair() {

        val spacecraft = target()
        val order = queuedOrder()

        val (vehicle, _) = reachFormation()

        assertEquals(
            ServicingVehiclePhase.FORMATION_FOLLOWING,
            vehicle.phase
        )

        assertTrue(
            spacecraft.requiresServicing
        )

        assertEquals(
            RepairStatus.QUEUED,
            order.status
        )
    }

    /**
     * Verifies that the servicing vehicle can begin
     * returning directly from formation following.
     */
    @Test
    fun vehicleCanReturnFromFormation() {

        val (following, _) = reachFormation()

        val returning =
            ServicingVehicleNavigation.beginReturn(
                following
            )

        assertEquals(
            ServicingVehiclePhase.RETURNING,
            returning.phase
        )
    }

    /**
     * Confirms that return guidance eventually
     * docks the vehicle at its moving home station.
     */
    @Test
    fun returningVehicleEventuallyDocks() {

        val (following, formedAt) = reachFormation()

        var vehicle =
            ServicingVehicleNavigation.beginReturn(
                following
            )

        for (second in formedAt + 1..formedAt + 120) {

            vehicle = ServicingVehicleNavigation.advance(
                vehicle = vehicle,
                station = OrbitalServicing.stationAt(second),
                target = target(sampleSecond = second),
                atSecond = second
            )

            if (
                vehicle.phase ==
                ServicingVehiclePhase.DOCKED
            ) {
                break
            }
        }

        assertEquals(
            ServicingVehiclePhase.DOCKED,
            vehicle.phase
        )

        assertFalse(
            vehicle.isAwayFromStation
        )

        assertEquals(
            null,
            vehicle.workOrderId
        )

        assertEquals(
            null,
            vehicle.targetSatelliteId
        )
    }

    /**
     * Prevents a docked vehicle from
     * starting an unnecessary return.
     */
    @Test(expected = IllegalArgumentException::class)
    fun dockedVehicleCannotBeginReturn() {

        ServicingVehicleNavigation.beginReturn(
            dockedVehicle()
        )
    }
}
