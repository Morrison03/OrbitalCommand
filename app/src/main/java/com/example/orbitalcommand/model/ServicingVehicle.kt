
package com.example.orbitalcommand.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Represents the operational phases of a robotic
 * servicing vehicle during a simulated mission.
 */
enum class ServicingVehiclePhase(
    val displayName: String
) {
    DOCKED("Docked"),
    DEPLOYED("Deployed"),
    APPROACHING("Approaching"),
    FORMATION_FOLLOWING("Formation Following"),
    HOLDING("Holding"),
    RETURNING("Returning")
}

/**
 * Identifies why the vehicle has stopped
 * its automatic navigation.
 *
 * Formation following is a separate active phase
 * and does not use a hold reason.
 */
enum class VehicleHoldReason(
    val displayName: String
) {
    OPERATOR("Operator requested hold"),
    TELEMETRY_UNAVAILABLE("Target telemetry unavailable")
}

/**
 * Represents a point in the orbital visualization's
 * normalized two-dimensional coordinate system.
 *
 * These coordinates are not physical distances.
 */
data class VehiclePosition(
    val x: Float,
    val y: Float
) {

    /**
     * Calculates the schematic distance
     * between two positions.
     */
    fun distanceTo(
        other: VehiclePosition
    ): Float {

        return hypot(
            other.x - x,
            other.y - y
        )
    }
}

/**
 * Converts schematic orbital parameters into
 * normalized two-dimensional positions.
 *
 * The coordinate system is shared by the
 * orbital visualization and vehicle navigation.
 */
object OrbitalDisplayGeometry {

    /**
     * Returns the position on a circular display
     * orbit at the specified angle and radius.
     */
    fun onOrbit(
        angleDegrees: Float,
        radiusFraction: Float
    ): VehiclePosition {

        val radians = Math.toRadians(
            angleDegrees.toDouble()
        )

        return VehiclePosition(
            x = radiusFraction * cos(radians).toFloat(),
            y = radiusFraction * sin(radians).toFloat()
        )
    }
}

/**
 * Stores the servicing vehicle's current mission
 * assignment, position, navigation phase, and hold state.
 */
data class ServicingVehicleState(
    val id: String,
    val phase: ServicingVehiclePhase,
    val position: VehiclePosition,
    val workOrderId: Long? = null,
    val targetSatelliteId: String? = null,
    val holdReason: VehicleHoldReason? = null
) {

    /**
     * Indicates whether the vehicle is currently
     * deployed away from OS-001.
     */
    val isAwayFromStation: Boolean
        get() = phase != ServicingVehiclePhase.DOCKED

    companion object {

        /**
         * Creates an unassigned servicing vehicle
         * docked at the station's current position.
         */
        fun dockedAt(
            station: ServicingStation
        ): ServicingVehicleState {

            return ServicingVehicleState(
                id = "SERVICER-01",
                phase = ServicingVehiclePhase.DOCKED,
                position = OrbitalDisplayGeometry.onOrbit(
                    angleDegrees = station.orbitAngleDegrees,
                    radiusFraction = station.orbitRadiusFraction
                )
            )
        }
    }
}
