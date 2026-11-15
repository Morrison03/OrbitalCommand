package com.example.orbitalcommand.model

/**
 * Separates the lifecycle of a robotic repair from the trip that delivers
 * SERVICER-01 to and from the satellite.
 *
 * Returning to OS-001 closes the deployment and releases the rail grip,
 * but it does not undo mechanical work already completed on the target.
 * Only a queued and still-valid repair may be resumed. Final verification
 * must be performed after re-establishing formation and rail attachment.
 */
object RepairProgressRetention {

    /**
     * Keeps an unfinished repair after arriving at OS-001 or cancelling
     * a prepared (but undispatched) return mission.
     *
     * Work orders that are missing, completed, or no longer match the
     * satellite/damage do not retain obsolete repair sessions.
     */
    fun keepUnfinished(
        session: RoboticRepairSession?,
        orders: List<RepairWorkOrder>
    ): RoboticRepairSession? {
        if (session == null || session.isComplete) return null
        val order = orders.firstOrNull { it.id == session.workOrderId }
            ?: return null
        return forOrder(session, order)
    }

    /**
     * Restores the session only when the operator prepares its original
     * queued work order. The caller is responsible for checking formation,
     * telemetry, and an actual rail grip before allowing VERIFY.
     */
    fun forOrder(
        session: RoboticRepairSession?,
        order: RepairWorkOrder
    ): RoboticRepairSession? = session?.takeIf {
        !it.isComplete &&
            order.status == RepairStatus.QUEUED &&
            order.id == it.workOrderId &&
            order.satelliteId == it.satelliteId &&
            order.damage == it.damage
    }
}
