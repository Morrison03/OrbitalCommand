
package com.example.orbitalcommand.model

/**
 * Identifies persistent equipment damage that requires
 * a simulated servicing operation to resolve.
 *
 * Equipment damage is separate from temporary faults
 * used for diagnostic testing.
 */
enum class EquipmentDamage(
    val displayName: String,
    val subsystem: String,
    val repairAction: String,
    val description: String
) {

    NONE(
        "No Damage",
        "None",
        "No repair required",
        "No persistent equipment damage is present."
    ),

    SOLAR_ARRAY_DAMAGE(
        "Solar Array Damage",
        "Electrical Power",
        "Replace solar array",
        "Damaged solar panels generate significantly " +
                "less electricity during sunlight."
    ),

    THERMAL_RADIATOR_DAMAGE(
        "Thermal Radiator Damage",
        "Thermal Control",
        "Repair thermal radiator",
        "Damaged thermal equipment causes the satellite " +
                "temperature to rise over time."
    ),

    PAYLOAD_SENSOR_DAMAGE(
        "Payload Sensor Damage",
        "Scientific Payload",
        "Replace sensor module",
        "A damaged payload sensor prevents normal " +
                "scientific payload operation."
    )
}

/**
 * Describes the current lifecycle of a repair work order.
 */
enum class RepairStatus {
    OPEN,
    QUEUED,
    COMPLETED
}

/**
 * Represents an equipment maintenance request.
 *
 * Work orders preserve their original damage type
 * and timestamps as they move through the workflow.
 */
data class RepairWorkOrder(
    val id: Long,
    val satelliteId: String,
    val damage: EquipmentDamage,
    val createdAtSecond: Int,
    val status: RepairStatus = RepairStatus.OPEN,
    val queuedAtSecond: Int? = null,
    val completedAtSecond: Int? = null
) {

    /**
     * Indicates whether the work order still requires
     * maintenance activity.
     */
    val isActive: Boolean
        get() = status != RepairStatus.COMPLETED
}

/**
 * Maintains immutable repair work-order history.
 *
 * Active orders are always preserved. Completed orders
 * are retained in a bounded history.
 */
object RepairWorkOrders {

    const val MAX_COMPLETED_ORDERS = 50

    /**
     * Creates a maintenance request for a damaged satellite.
     *
     * Only one active repair request is permitted for
     * each satellite in the current simulation.
     */
    fun open(
        orders: List<RepairWorkOrder>,
        id: Long,
        satelliteId: String,
        damage: EquipmentDamage,
        atSecond: Int
    ): List<RepairWorkOrder> {

        require(id > 0L) {
            "Work order identifiers must be positive."
        }

        require(atSecond >= 0) {
            "Simulation time cannot be negative."
        }

        require(satelliteId.isNotBlank()) {
            "A satellite identifier is required."
        }

        require(damage != EquipmentDamage.NONE) {
            "A repair request must identify equipment damage."
        }

        require(orders.none { it.id == id }) {
            "Duplicate work order identifier."
        }

        require(
            orders.none {
                it.satelliteId == satelliteId && it.isActive
            }
        ) {
            "The satellite already has an active work order."
        }

        val newOrder = RepairWorkOrder(
            id = id,
            satelliteId = satelliteId,
            damage = damage,
            createdAtSecond = atSecond
        )

        return trim(listOf(newOrder) + orders)
    }

    /**
     * Moves an open repair request into the servicing queue.
     *
     * Other work orders remain unchanged.
     */
    fun queue(
        orders: List<RepairWorkOrder>,
        orderId: Long,
        atSecond: Int
    ): List<RepairWorkOrder> {

        require(atSecond >= 0) {
            "Simulation time cannot be negative."
        }

        return orders.map { order ->

            if (
                order.id == orderId &&
                order.status == RepairStatus.OPEN
            ) {

                order.copy(
                    status = RepairStatus.QUEUED,
                    queuedAtSecond = atSecond
                )

            } else {
                order
            }
        }
    }

    /**
     * Marks a queued repair as completed in the simulator.
     *
     * An open request cannot be completed until an operator
     * has placed it in the servicing queue.
     */
    fun complete(
        orders: List<RepairWorkOrder>,
        orderId: Long,
        atSecond: Int
    ): List<RepairWorkOrder> {

        require(atSecond >= 0) {
            "Simulation time cannot be negative."
        }

        val updated = orders.map { order ->

            if (
                order.id == orderId &&
                order.status == RepairStatus.QUEUED
            ) {

                order.copy(
                    status = RepairStatus.COMPLETED,
                    completedAtSecond = atSecond
                )

            } else {
                order
            }
        }

        return trim(updated)
    }

    /**
     * Retains all unresolved requests and the most
     * recent completed work orders.
     */
    private fun trim(
        orders: List<RepairWorkOrder>
    ): List<RepairWorkOrder> {

        val active = orders.filter {
            it.isActive
        }

        val completed = orders
            .filterNot { it.isActive }
            .sortedByDescending { it.id }
            .take(MAX_COMPLETED_ORDERS)

        return (active + completed)
            .sortedByDescending { it.id }
    }
}

