
package com.example.orbitalcommand.model

import com.example.orbitalcommand.simulation.SatelliteSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies servicing station motion, mission eligibility,
 * target validation, and telemetry verification requirements.
 */
class OrbitalServicingTest {

    private val definition =
        SatelliteCatalog.satellites.first()

    /**
     * Creates a satellite with a persistent equipment
     * failure for mission-planning tests.
     */
    private fun damagedSatellite(
        sampleSecond: Int = 12,
        damage: EquipmentDamage =
            EquipmentDamage.SOLAR_ARRAY_DAMAGE,
        disconnected: Boolean = false
    ): SatelliteState {

        val telemetry = SatelliteSimulator.snapshot(
            second = sampleSecond,
            satellite = definition
        )

        return SatelliteState(
            definition = definition,
            telemetry = telemetry,
            onboardTelemetry = telemetry,
            fault = if (disconnected)
                SatelliteFault.COMMUNICATION_LOSS
            else
                SatelliteFault.NONE,
            equipmentDamage = damage,
            equipmentDamageStartedSecond = 10
        )
    }

    /**
     * Creates a queued repair work order matching
     * the satellite's equipment damage.
     */
    private fun queuedOrder(): RepairWorkOrder {

        return RepairWorkOrder(
            id = 1L,
            satelliteId = definition.id,
            damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            createdAtSecond = 10,
            status = RepairStatus.QUEUED,
            queuedAtSecond = 11
        )
    }

    /**
     * Verifies that the servicing station has its
     * own identifier and an inner orbital track.
     */
    @Test
    fun stationHasIndependentConfiguration() {

        val station = OrbitalServicing.stationAt(0)

        assertEquals("OS-001", station.id)
        assertEquals(40f, station.orbitAngleDegrees, 0.001f)

        assertTrue(
            SatelliteCatalog.satellites.all {
                station.orbitRadiusFraction <
                        it.orbitRadiusFraction
            }
        )
    }

    /**
     * Verifies that the station moves independently
     * as the simulation clock advances.
     */
    @Test
    fun stationPositionChangesWithTime() {

        val initial = OrbitalServicing.stationAt(0)
        val next = OrbitalServicing.stationAt(1)

        assertEquals(
            43f,
            next.orbitAngleDegrees,
            0.001f
        )

        assertTrue(
            initial.orbitAngleDegrees !=
                    next.orbitAngleDegrees
        )
    }

    /**
     * Verifies that the station returns to its
     * original angular position after 120 seconds.
     */
    @Test
    fun stationOrbitAngleWrapsCorrectly() {

        val initial = OrbitalServicing.stationAt(0)
        val completed = OrbitalServicing.stationAt(120)

        assertEquals(
            initial.orbitAngleDegrees,
            completed.orbitAngleDegrees,
            0.001f
        )
    }

    /**
     * Rejects invalid negative simulation time.
     */
    @Test(expected = IllegalArgumentException::class)
    fun stationRejectsNegativeTime() {

        OrbitalServicing.stationAt(-1)
    }

    /**
     * Verifies that a queued repair with matching
     * equipment damage can produce a mission plan.
     */
    @Test
    fun queuedRepairCanBePrepared() {

        val order = queuedOrder()
        val satellite = damagedSatellite()

        val plan = OrbitalServicing.prepare(
            orders = listOf(order),
            satellites = listOf(satellite),
            workOrderId = order.id,
            atSecond = 13
        )

        assertEquals(order.id, plan.workOrderId)

        assertEquals(
            definition.id,
            plan.targetSatelliteId
        )

        assertEquals(
            EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            plan.damage
        )

        assertEquals(13, plan.preparedAtSecond)

        assertFalse(
            plan.telemetryVerificationRequired
        )
    }

    /**
     * Confirms that mission preparation does not
     * complete or modify the underlying repair.
     */
    @Test
    fun missionPreparationDoesNotCompleteRepair() {

        val order = queuedOrder()
        val satellite = damagedSatellite()

        OrbitalServicing.prepare(
            orders = listOf(order),
            satellites = listOf(satellite),
            workOrderId = order.id,
            atSecond = 13
        )

        assertEquals(
            RepairStatus.QUEUED,
            order.status
        )

        assertTrue(
            satellite.requiresServicing
        )
    }

    /**
     * Prevents mission preparation for a work
     * order that has not been queued.
     */
    @Test(expected = IllegalArgumentException::class)
    fun openWorkOrderCannotBePrepared() {

        val order = queuedOrder().copy(
            status = RepairStatus.OPEN,
            queuedAtSecond = null
        )

        OrbitalServicing.prepare(
            orders = listOf(order),
            satellites = listOf(damagedSatellite()),
            workOrderId = order.id,
            atSecond = 13
        )
    }

    /**
     * Prevents mission preparation for a completed
     * maintenance request.
     */
    @Test(expected = IllegalArgumentException::class)
    fun completedWorkOrderCannotBePrepared() {

        val order = queuedOrder().copy(
            status = RepairStatus.COMPLETED,
            completedAtSecond = 12
        )

        OrbitalServicing.prepare(
            orders = listOf(order),
            satellites = listOf(damagedSatellite()),
            workOrderId = order.id,
            atSecond = 13
        )
    }

    /**
     * Rejects unknown work-order identifiers.
     */
    @Test(expected = IllegalArgumentException::class)
    fun unknownWorkOrderCannotBePrepared() {

        OrbitalServicing.prepare(
            orders = listOf(queuedOrder()),
            satellites = listOf(damagedSatellite()),
            workOrderId = 999L,
            atSecond = 13
        )
    }

    /**
     * Rejects a maintenance request if the target
     * satellite no longer has matching damage.
     */
    @Test(expected = IllegalArgumentException::class)
    fun mismatchedEquipmentDamageIsRejected() {

        OrbitalServicing.prepare(
            orders = listOf(queuedOrder()),
            satellites = listOf(
                damagedSatellite(
                    damage =
                        EquipmentDamage.THERMAL_RADIATOR_DAMAGE
                )
            ),
            workOrderId = 1L,
            atSecond = 13
        )
    }

    /**
     * Allows mission preparation without a live
     * connection but flags the need for verification.
     */
    @Test
    fun disconnectedTargetRequiresVerification() {

        val plan = OrbitalServicing.prepare(
            orders = listOf(queuedOrder()),
            satellites = listOf(
                damagedSatellite(disconnected = true)
            ),
            workOrderId = 1L,
            atSecond = 13
        )

        assertTrue(
            plan.telemetryVerificationRequired
        )
    }

    /**
     * Requires additional target verification when
     * the most recent telemetry is more than five
     * simulation seconds old.
     */
    @Test
    fun staleTelemetryRequiresVerification() {

        val plan = OrbitalServicing.prepare(
            orders = listOf(queuedOrder()),
            satellites = listOf(
                damagedSatellite(sampleSecond = 12)
            ),
            workOrderId = 1L,
            atSecond = 30
        )

        assertTrue(
            plan.telemetryVerificationRequired
        )

        assertEquals(
            12,
            plan.lastReceivedTelemetrySecond
        )
    }

    /**
     * Rejects mission preparation when the repair
     * order references an unknown satellite.
     */
    @Test(expected = IllegalArgumentException::class)
    fun unknownTargetSatelliteIsRejected() {

        val order = queuedOrder().copy(
            satelliteId = "SAT-999"
        )

        OrbitalServicing.prepare(
            orders = listOf(order),
            satellites = listOf(damagedSatellite()),
            workOrderId = order.id,
            atSecond = 13
        )
    }
}

