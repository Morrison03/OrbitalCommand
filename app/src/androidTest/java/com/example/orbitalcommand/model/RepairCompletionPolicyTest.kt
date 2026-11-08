
package com.example.orbitalcommand.model

import com.example.orbitalcommand.simulation.SatelliteSimulator
import com.example.orbitalcommand.simulation.ServicingVehicleNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies repair eligibility across deployment,
 * communication loss, formation recovery, and return.
 *
 * These tests ensure the repair interface and ViewModel
 * use the same operational requirements.
 */
class RepairCompletionPolicyTest {

    private val definition =
        SatelliteCatalog.satellites.first()

    /**
     * Creates an eligible queued solar array repair.
     */
    private fun queuedOrder(): RepairWorkOrder {

        return RepairWorkOrder(
            id = 1L,
            satelliteId = definition.id,
            damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            createdAtSecond = 5,
            status = RepairStatus.QUEUED,
            queuedAtSecond = 6
        )
    }

    /**
     * Creates a damaged target with optional
     * communication loss and telemetry age.
     */
    private fun target(
        sampleSecond: Int = 20,
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
     * Creates a mission state in the specified
     * servicing vehicle phase.
     */
    private fun missionState(
        phase: ServicingVehiclePhase,
        connected: Boolean = true,
        sampleSecond: Int = 20,
        elapsedSecond: Int = 20,
        assignedOrderId: Long = 1L
    ): OrbitalUiState {

        val spacecraft = target(
            sampleSecond = sampleSecond,
            connected = connected
        )

        val station = OrbitalServicing.stationAt(
            elapsedSecond
        )

        val vehicle = if (
            phase == ServicingVehiclePhase.DOCKED
        ) {

            ServicingVehicleState.dockedAt(station)

        } else {

            ServicingVehicleState(
                id = "SERVICER-01",
                phase = phase,
                position =
                    ServicingVehicleNavigation.formationPosition(
                        spacecraft
                    ),
                workOrderId = assignedOrderId,
                targetSatelliteId = definition.id
            )
        }

        val plan = ServicingMissionPlan(
            workOrderId = 1L,
            targetSatelliteId = definition.id,
            damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            preparedAtSecond = 10,
            lastReceivedTelemetrySecond = 10,
            telemetryVerificationRequired = false
        )

        return OrbitalUiState(
            elapsedSeconds = elapsedSecond,
            satellites = listOf(spacecraft),
            selectedSatelliteId = definition.id,
            repairOrders = listOf(queuedOrder()),
            servicingStation = station,
            preparedMission = plan,
            servicingVehicle = vehicle
        )
    }

    /**
     * Preserves the original test-only repair
     * completion shortcut while docked.
     */
    @Test
    fun dockedVehicleAllowsTestCompletion() {

        val state = missionState(
            phase = ServicingVehiclePhase.DOCKED
        )

        val result = RepairCompletionPolicy.evaluate(
            state,
            queuedOrder()
        )

        assertEquals(
            RepairCompletionStatus.READY_AT_STATION,
            result
        )

        assertTrue(result.isAllowed)
    }

    /**
     * Confirms that a correctly assigned vehicle
     * can complete a simulated repair in formation.
     */
    @Test
    fun formationFollowingEnablesRepair() {

        val state = missionState(
            phase = ServicingVehiclePhase.FORMATION_FOLLOWING
        )

        val result = RepairCompletionPolicy.evaluate(
            state,
            queuedOrder()
        )

        assertEquals(
            RepairCompletionStatus.READY_IN_FORMATION,
            result
        )

        assertTrue(result.isAllowed)
    }

    /**
     * Prevents repair while the vehicle is
     * still approaching the satellite.
     */
    @Test
    fun approachingVehicleCannotCompleteRepair() {

        val state = missionState(
            phase = ServicingVehiclePhase.APPROACHING
        )

        assertFalse(
            RepairCompletionPolicy.evaluate(
                state,
                queuedOrder()
            ).isAllowed
        )
    }

    /**
     * Prevents repair while the vehicle is
     * holding after a communication failure.
     */
    @Test
    fun communicationLossHoldDisablesRepair() {

        val state = missionState(
            phase = ServicingVehiclePhase.HOLDING,
            connected = false
        )

        assertEquals(
            RepairCompletionStatus.VEHICLE_NOT_IN_FORMATION,
            RepairCompletionPolicy.evaluate(
                state,
                queuedOrder()
            )
        )
    }

    /**
     * Requires current received telemetry even
     * if the vehicle reports formation following.
     */
    @Test
    fun communicationLossDuringFormationDisablesRepair() {

        val state = missionState(
            phase = ServicingVehiclePhase.FORMATION_FOLLOWING,
            connected = false
        )

        assertEquals(
            RepairCompletionStatus.TARGET_TELEMETRY_UNAVAILABLE,
            RepairCompletionPolicy.evaluate(
                state,
                queuedOrder()
            )
        )
    }

    /**
     * Confirms that restoring communication and
     * reestablishing formation enables repair.
     *
     * This reproduces the original reported workflow.
     */
    @Test
    fun restoredCommunicationAndFormationEnableRepair() {

        val lostState = missionState(
            phase = ServicingVehiclePhase.HOLDING,
            connected = false
        )

        val restoredTelemetry = SatelliteSimulator.snapshot(
            second = 21,
            satellite = definition
        )

        val restoredTarget = lostState.satellites.first().copy(
            fault = SatelliteFault.NONE,
            telemetry = restoredTelemetry,
            onboardTelemetry = restoredTelemetry
        )

        val resumed = ServicingVehicleNavigation.resumeApproach(
            vehicle = lostState.servicingVehicle,
            target = restoredTarget,
            atSecond = 21
        )

        val aligned = resumed.copy(
            position =
                ServicingVehicleNavigation.formationPosition(
                    restoredTarget
                )
        )

        val following = ServicingVehicleNavigation.advance(
            vehicle = aligned,
            station = OrbitalServicing.stationAt(21),
            target = restoredTarget,
            atSecond = 21
        )

        assertEquals(
            ServicingVehiclePhase.FORMATION_FOLLOWING,
            following.phase
        )

        val recoveredState = lostState.copy(
            elapsedSeconds = 21,
            satellites = listOf(restoredTarget),
            servicingVehicle = following
        )

        assertEquals(
            RepairCompletionStatus.READY_IN_FORMATION,
            RepairCompletionPolicy.evaluate(
                recoveredState,
                queuedOrder()
            )
        )
    }

    /**
     * Prevents repair when a vehicle is
     * assigned to a different work order.
     */
    @Test
    fun incorrectVehicleAssignmentDisablesRepair() {

        val state = missionState(
            phase = ServicingVehiclePhase.FORMATION_FOLLOWING,
            assignedOrderId = 999L
        )

        assertEquals(
            RepairCompletionStatus.DIFFERENT_MISSION,
            RepairCompletionPolicy.evaluate(
                state,
                queuedOrder()
            )
        )
    }

    /**
     * Prevents completing a repair when the
     * target telemetry is older than five seconds.
     */
    @Test
    fun staleTargetTelemetryDisablesRepair() {

        val state = missionState(
            phase = ServicingVehiclePhase.FORMATION_FOLLOWING,
            sampleSecond = 10,
            elapsedSecond = 20
        )

        assertEquals(
            RepairCompletionStatus.TARGET_TELEMETRY_UNAVAILABLE,
            RepairCompletionPolicy.evaluate(
                state,
                queuedOrder()
            )
        )
    }

    /**
     * Prevents repair while the robot is
     * already returning to its home station.
     */
    @Test
    fun returningVehicleCannotCompleteRepair() {

        val state = missionState(
            phase = ServicingVehiclePhase.RETURNING
        )

        assertFalse(
            RepairCompletionPolicy.evaluate(
                state,
                queuedOrder()
            ).isAllowed
        )
    }

    /**
     * Prevents repairing work orders that
     * have already been completed.
     */
    @Test
    fun completedWorkOrderCannotBeCompletedAgain() {

        val state = missionState(
            phase = ServicingVehiclePhase.FORMATION_FOLLOWING
        )

        val completed = queuedOrder().copy(
            status = RepairStatus.COMPLETED,
            completedAtSecond = 20
        )

        assertEquals(
            RepairCompletionStatus.WORK_ORDER_NOT_QUEUED,
            RepairCompletionPolicy.evaluate(
                state,
                completed
            )
        )
    }

    /**
     * Ensures that the target must still have
     * the equipment damage being repaired.
     */
    @Test
    fun equipmentMustStillBeDamaged() {

        val original = missionState(
            phase = ServicingVehiclePhase.FORMATION_FOLLOWING
        )

        val repairedTarget = original.satellites.first().copy(
            equipmentDamage = EquipmentDamage.NONE
        )

        val state = original.copy(
            satellites = listOf(repairedTarget)
        )

        assertEquals(
            RepairCompletionStatus.EQUIPMENT_MISMATCH,
            RepairCompletionPolicy.evaluate(
                state,
                queuedOrder()
            )
        )
    }
}

