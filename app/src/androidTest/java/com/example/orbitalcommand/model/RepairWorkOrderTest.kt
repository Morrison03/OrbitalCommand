
package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies repair work-order creation, queueing,
 * completion, and historical retention.
 */
class RepairWorkOrderTest {

    /**
     * Creates a maintenance request for use in tests.
     */
    private fun createOrder(
        id: Long = 1L,
        satelliteId: String = "SAT-001"
    ): RepairWorkOrder {

        return RepairWorkOrder(
            id = id,
            satelliteId = satelliteId,
            damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            createdAtSecond = 10
        )
    }

    /**
     * Verifies that opening a repair request creates
     * an active work order.
     */
    @Test
    fun newRepairOrderStartsOpen() {

        val orders = RepairWorkOrders.open(
            orders = emptyList(),
            id = 1L,
            satelliteId = "SAT-001",
            damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            atSecond = 10
        )

        assertEquals(1, orders.size)
        assertEquals(RepairStatus.OPEN, orders.first().status)
        assertTrue(orders.first().isActive)
    }

    /**
     * Confirms that an open repair request can
     * be placed into the servicing queue.
     */
    @Test
    fun openRepairCanBeQueued() {

        val updated = RepairWorkOrders.queue(
            orders = listOf(createOrder()),
            orderId = 1L,
            atSecond = 20
        )

        assertEquals(
            RepairStatus.QUEUED,
            updated.first().status
        )

        assertEquals(
            20,
            updated.first().queuedAtSecond
        )

        assertTrue(updated.first().isActive)
    }

    /**
     * Verifies that queued maintenance can be
     * completed in the simulation.
     */
    @Test
    fun queuedRepairCanBeCompleted() {

        val queued = RepairWorkOrders.queue(
            orders = listOf(createOrder()),
            orderId = 1L,
            atSecond = 20
        )

        val completed = RepairWorkOrders.complete(
            orders = queued,
            orderId = 1L,
            atSecond = 30
        )

        assertEquals(
            RepairStatus.COMPLETED,
            completed.first().status
        )

        assertEquals(
            30,
            completed.first().completedAtSecond
        )

        assertFalse(completed.first().isActive)
    }

    /**
     * Prevents an unqueued repair from being
     * marked as completed.
     */
    @Test
    fun openRepairCannotBeCompletedDirectly() {

        val original = listOf(createOrder())

        val updated = RepairWorkOrders.complete(
            orders = original,
            orderId = 1L,
            atSecond = 30
        )

        assertEquals(
            RepairStatus.OPEN,
            updated.first().status
        )
    }

    /**
     * Ensures that work orders belonging to other
     * satellites are not affected by queueing.
     */
    @Test
    fun queueingOneOrderPreservesOtherOrders() {

        val first = createOrder(
            id = 1L,
            satelliteId = "SAT-001"
        )

        val second = createOrder(
            id = 2L,
            satelliteId = "SAT-002"
        )

        val updated = RepairWorkOrders.queue(
            orders = listOf(second, first),
            orderId = 1L,
            atSecond = 40
        )

        assertEquals(
            RepairStatus.QUEUED,
            updated.first { it.id == 1L }.status
        )

        assertEquals(
            RepairStatus.OPEN,
            updated.first { it.id == 2L }.status
        )
    }

    /**
     * Rejects a second active work order
     * for the same spacecraft.
     */
    @Test(expected = IllegalArgumentException::class)
    fun duplicateActiveRepairIsRejected() {

        RepairWorkOrders.open(
            orders = listOf(createOrder()),
            id = 2L,
            satelliteId = "SAT-001",
            damage = EquipmentDamage.THERMAL_RADIATOR_DAMAGE,
            atSecond = 20
        )
    }

    /**
     * Confirms that completed work-order history
     * remains within the retention limit.
     */
    @Test
    fun completedOrderHistoryIsBounded() {

        var orders = emptyList<RepairWorkOrder>()

        for (id in 1L..60L) {

            val satelliteId = "SAT-$id"

            orders = RepairWorkOrders.open(
                orders = orders,
                id = id,
                satelliteId = satelliteId,
                damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
                atSecond = id.toInt()
            )

            orders = RepairWorkOrders.queue(
                orders = orders,
                orderId = id,
                atSecond = id.toInt() + 1
            )

            orders = RepairWorkOrders.complete(
                orders = orders,
                orderId = id,
                atSecond = id.toInt() + 2
            )
        }

        assertEquals(
            RepairWorkOrders.MAX_COMPLETED_ORDERS,
            orders.size
        )

        assertEquals(60L, orders.first().id)
    }

    /**
     * Ensures that invalid repair requests
     * cannot be created without equipment damage.
     */
    @Test(expected = IllegalArgumentException::class)
    fun repairRequiresEquipmentDamage() {

        RepairWorkOrders.open(
            orders = emptyList(),
            id = 1L,
            satelliteId = "SAT-001",
            damage = EquipmentDamage.NONE,
            atSecond = 10
        )
    }
}

