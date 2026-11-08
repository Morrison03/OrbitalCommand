
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
import com.example.orbitalcommand.model.VehiclePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies servicing vehicle deployment,
 * navigation eligibility, approach, hold,
 * return, and docking behavior.
 */
class ServicingVehicleNavigationTest {

    private val definition =
        SatelliteCatalog.satellites.first()

    private val station =
        OrbitalServicing.stationAt(0)

    /**
     * Creates a satellite target with persistent
     * solar array damage.
     */
    private fun target(
        sampleSecond: Int = 0,
        connected: Boolean = true
    ): SatelliteState {

        val telemetry =
            SatelliteSimulator.snapshot(
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
     * Creates a queued repair work order
     * for the test target.
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
     * Creates a mission plan for the queued order.
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
     * Creates a servicing vehicle docked
     * at the initial station location.
     */
    private fun dockedVehicle(): ServicingVehicleState {

        return ServicingVehicleState.dockedAt(station)
    }

    /**
     * Verifies that the initial vehicle
     * is docked and has no assigned mission.
     */
    @Test
    fun newVehicleStartsDocked() {

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
     * Verifies that a docked vehicle
     * follows its moving home station.
     */
    @Test
    fun dockedVehicleFollowsStation() {

        val vehicle = dockedVehicle()
        val nextStation = OrbitalServicing.stationAt(10)

        val updated = ServicingVehicleNavigation.advance(
            vehicle = vehicle,
            station = nextStation,
            target = null,
            atSecond = 10
        )

        val expected = OrbitalDisplayGeometry.onOrbit(
            nextStation.orbitAngleDegrees,
            nextStation.orbitRadiusFraction
        )

        assertEquals(expected.x, updated.position.x, 0.0001f)
        assertEquals(expected.y, updated.position.y, 0.0001f)
    }

    /**
     * Confirms that the vehicle receives
     * the prepared mission on deployment.
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
        assertEquals(1L, deployed.workOrderId)
        assertEquals(definition.id, deployed.targetSatelliteId)
    }

    /**
     * Prevents a second deployment while
     * the vehicle is already away from OS-001.
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
     * Verifies that queued repair work
     * and current telemetry permit dispatch.
     */
    @Test
    fun validQueuedRepairAllowsDispatch() {

        val ready = ServicingVehicleNavigation.isDispatchReady(
            plan = plan(),
            orders = listOf(queuedOrder()),
            satellites = listOf(target()),
            atSecond = 0
        )

        assertTrue(ready)
    }

    /**
     * Ensures that an open work order
     * cannot be dispatched.
     */
    @Test
    fun unqueuedWorkOrderBlocksDispatch() {

        val openOrder = queuedOrder().copy(
            status = RepairStatus.OPEN,
            queuedAtSecond = null
        )

        val ready = ServicingVehicleNavigation.isDispatchReady(
            plan = plan(),
            orders = listOf(openOrder),
            satellites = listOf(target()),
            atSecond = 0
        )

        assertFalse(ready)
    }

    /**
     * Ensures that target communication loss
     * prevents dispatch.
     */
    @Test
    fun disconnectedTargetBlocksDispatch() {

        val ready = ServicingVehicleNavigation.isDispatchReady(
            plan = plan(),
            orders = listOf(queuedOrder()),
            satellites = listOf(
                target(connected = false)
            ),
            atSecond = 0
        )

        assertFalse(ready)
    }

    /**
     * Ensures that outdated target telemetry
     * prevents dispatch.
     */
    @Test
    fun staleTelemetryBlocksDispatch() {

        val ready = ServicingVehicleNavigation.isDispatchReady(
            plan = plan(),
            orders = listOf(queuedOrder()),
            satellites = listOf(target(sampleSecond = 0)),
            atSecond = 20
        )

        assertFalse(ready)
    }

    /**
     * Verifies that approach transitions the
     * vehicle into a moving navigation state.
     */
    @Test
    fun approachBeginsAfterDeployment() {

        val deployed = ServicingVehicleNavigation.dispatch(
            dockedVehicle(),
            plan(),
            station
        )

        val approaching =
            ServicingVehicleNavigation.beginApproach(
                vehicle = deployed,
                target = target(),
                atSecond = 0
            )

        assertEquals(
            ServicingVehiclePhase.APPROACHING,
            approaching.phase
        )
    }

    /**
     * Verifies that a scripted approach
     * changes the vehicle's schematic position.
     */
    @Test
    fun approachMovesTowardTarget() {

        val deployed = ServicingVehicleNavigation.dispatch(
            dockedVehicle(),
            plan(),
            station
        )

        val approaching =
            ServicingVehicleNavigation.beginApproach(
                deployed,
                target(),
                0
            )

        val nextTarget = target(sampleSecond = 1)

        val updated = ServicingVehicleNavigation.advance(
            vehicle = approaching,
            station = OrbitalServicing.stationAt(1),
            target = nextTarget,
            atSecond = 1
        )

        assertTrue(
            updated.position != approaching.position
        )
    }

    /**
     * Confirms that an operator-requested hold
     * stops the vehicle without ending its mission.
     */
    @Test
    fun operatorHoldStopsApproach() {

        val deployed = ServicingVehicleNavigation.dispatch(
            dockedVehicle(),
            plan(),
            station
        )

        val approaching =
            ServicingVehicleNavigation.beginApproach(
                deployed,
                target(),
                0
            )

        val holding =
            ServicingVehicleNavigation.hold(approaching)

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

        assertEquals(1L, updated.workOrderId)
    }

    /**
     * Confirms that target communication loss
     * automatically stops scripted approach.
     */
    @Test
    fun lostTelemetryAutomaticallyHoldsVehicle() {

        val deployed = ServicingVehicleNavigation.dispatch(
            dockedVehicle(),
            plan(),
            station
        )

        val approaching =
            ServicingVehicleNavigation.beginApproach(
                deployed,
                target(),
                0
            )

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

        assertEquals(
            approaching.position,
            updated.position
        )
    }

    /**
     * Prevents resuming guidance while
     * target communications are unavailable.
     */
    @Test(expected = IllegalArgumentException::class)
    fun cannotResumeWithoutTargetTelemetry() {

        val holding = ServicingVehicleNavigation.dispatch(
            dockedVehicle(),
            plan(),
            station
        ).copy(
            phase = ServicingVehiclePhase.HOLDING,
            holdReason = VehicleHoldReason.TELEMETRY_UNAVAILABLE
        )

        ServicingVehicleNavigation.resumeApproach(
            vehicle = holding,
            target = target(connected = false),
            atSecond = 0
        )
    }

    /**
     * Verifies that approaching a target does not
     * remove its persistent equipment damage.
     */
    @Test
    fun targetStandoffDoesNotCompleteRepair() {

        val spacecraft = target()

        val targetPosition = OrbitalDisplayGeometry.onOrbit(
            spacecraft.telemetry.orbitAngleDegrees,
            spacecraft.definition.orbitRadiusFraction
        )

        val nearbyVehicle = ServicingVehicleNavigation.dispatch(
            dockedVehicle(),
            plan(),
            station
        ).copy(
            phase = ServicingVehiclePhase.APPROACHING,
            position = VehiclePosition(
                x = targetPosition.x + 0.065f,
                y = targetPosition.y
            )
        )

        val updated = ServicingVehicleNavigation.advance(
            vehicle = nearbyVehicle,
            station = station,
            target = spacecraft,
            atSecond = 0
        )

        assertEquals(
            ServicingVehiclePhase.HOLDING,
            updated.phase
        )

        assertEquals(
            VehicleHoldReason.TARGET_STANDOFF,
            updated.holdReason
        )

        assertTrue(spacecraft.requiresServicing)
    }

    /**
     * Confirms the vehicle can return to a
     * moving servicing station and dock.
     */
    @Test
    fun returningVehicleEventuallyDocks() {

        val deployed = ServicingVehicleNavigation.dispatch(
            dockedVehicle(),
            plan(),
            station
        )

        var vehicle =
            ServicingVehicleNavigation.beginReturn(deployed)

        for (second in 1..60) {

            vehicle = ServicingVehicleNavigation.advance(
                vehicle = vehicle,
                station = OrbitalServicing.stationAt(second),
                target = target(sampleSecond = second),
                atSecond = second
            )

            if (
                vehicle.phase == ServicingVehiclePhase.DOCKED
            ) {
                break
            }
        }

        assertEquals(
            ServicingVehiclePhase.DOCKED,
            vehicle.phase
        )

        assertFalse(vehicle.isAwayFromStation)
        assertEquals(null, vehicle.workOrderId)
        assertEquals(null, vehicle.targetSatelliteId)
    }

    /**
     * A docked vehicle cannot start
     * a return operation.
     */
    @Test(expected = IllegalArgumentException::class)
    fun dockedVehicleCannotBeginReturn() {

        ServicingVehicleNavigation.beginReturn(
            dockedVehicle()
        )
    }
}

