
package com.example.orbitalcommand.model

/**
 * Represents the location of the simulated orbital
 * servicing station.
 *
 * The station follows an accelerated, schematic circular
 * orbit and is not modeled as a telemetry satellite.
 */
data class ServicingStation(
    val id: String,
    val name: String,
    val orbitAngleDegrees: Float,
    val orbitRadiusFraction: Float
)

/**
 * Represents a prepared but not yet dispatched
 * satellite maintenance mission.
 *
 * The plan references an existing queued repair order.
 * Preparing a mission does not perform maintenance,
 * launch a vehicle, or change the order's status.
 */
data class ServicingMissionPlan(
    val workOrderId: Long,
    val targetSatelliteId: String,
    val damage: EquipmentDamage,
    val preparedAtSecond: Int,
    val lastReceivedTelemetrySecond: Int,
    val telemetryVerificationRequired: Boolean
)

/**
 * Provides deterministic station motion and validates
 * repair mission preparation.
 *
 * Mission planning is independent of the user interface
 * so the preparation rules can be unit tested.
 */
object OrbitalServicing {

    private const val STATION_ID = "OS-001"
    private const val STATION_NAME = "Orbital Servicing Station"

    private const val INITIAL_ANGLE_DEGREES = 40L
    private const val ANGULAR_RATE_DEGREES = 3L

    private const val STATION_ORBIT_RADIUS = 0.23f

    /**
     * Calculates the servicing station's orbital position
     * at a specified simulation second.
     *
     * The angle wraps into the range from 0 to 359 degrees.
     */
    fun stationAt(second: Int): ServicingStation {

        require(second >= 0) {
            "Simulation time cannot be negative."
        }

        val angle = (
                INITIAL_ANGLE_DEGREES +
                        second.toLong() * ANGULAR_RATE_DEGREES
                ) % 360L

        return ServicingStation(
            id = STATION_ID,
            name = STATION_NAME,
            orbitAngleDegrees = angle.toFloat(),
            orbitRadiusFraction = STATION_ORBIT_RADIUS
        )
    }

    /**
     * Prepares a servicing plan for an existing
     * queued repair work order.
     *
     * The work order must belong to a known satellite
     * with matching persistent equipment damage.
     *
     * A missing live telemetry connection does not
     * prevent planning, but it creates a verification
     * requirement for future dispatch operations.
     */
    fun prepare(
        orders: List<RepairWorkOrder>,
        satellites: List<SatelliteState>,
        workOrderId: Long,
        atSecond: Int
    ): ServicingMissionPlan {

        require(atSecond >= 0) {
            "Simulation time cannot be negative."
        }

        val order = orders.firstOrNull {
            it.id == workOrderId
        } ?: throw IllegalArgumentException(
            "Repair work order was not found."
        )

        require(order.status == RepairStatus.QUEUED) {
            "The work order must be queued before mission preparation."
        }

        val satellite = satellites.firstOrNull {
            it.definition.id == order.satelliteId
        } ?: throw IllegalArgumentException(
            "The work order target satellite was not found."
        )

        require(satellite.equipmentDamage == order.damage) {
            "The satellite's active equipment damage does not match the work order."
        }

        val lastReceivedSecond =
            satellite.telemetry.sampleSecond

        val telemetryAge = (
                atSecond - lastReceivedSecond
                ).coerceAtLeast(0)

        return ServicingMissionPlan(
            workOrderId = order.id,
            targetSatelliteId = satellite.definition.id,
            damage = order.damage,
            preparedAtSecond = atSecond,
            lastReceivedTelemetrySecond =
                lastReceivedSecond,
            telemetryVerificationRequired =
                !satellite.isConnected || telemetryAge > 5
        )
    }
}

