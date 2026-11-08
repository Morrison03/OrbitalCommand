
package com.example.orbitalcommand.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Distinguishes the stages of a simulated
 * servicing vehicle mission.
 */
enum class ServicingVehiclePhase(
    val displayName: String
) {
    DOCKED("Docked"),
    DEPLOYED("Deployed"),
    APPROACHING("Approaching"),
    HOLDING("Holding"),
    RETURNING("Returning")
}

/**
 * Records why a vehicle stopped its
 * scripted target approach.
 */
enum class VehicleHoldReason(
    val displayName: String
) {
    OPERATOR("Operator requested hold"),
    TELEMETRY_UNAVAILABLE("Target telemetry unavailable"),
    TARGET_STANDOFF("Target standoff reached")
}

/**
 * A position in normalized, schematic
 * orbital-display coordinates.
 */
data class VehiclePosition(
    val x: Float,
    val y: Float
) {

    fun distanceTo(
        other: VehiclePosition
    ): Float = kotlin.math.hypot(
        other.x - x,
        other.y - y
    )
}

/**
 * Shared geometry for the orbital visualization
 * and simulated vehicle guidance.
 *
 * Coordinates describe positions relative to
 * the center of the orbital display.
 */
object OrbitalDisplayGeometry {

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
 * Represents the servicing vehicle's operational state.
 *
 * Positions use schematic 2D visualization units,
 * not physical distances or validated trajectories.
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
     * deployed outside its home station.
     */
    val isAwayFromStation: Boolean
        get() = phase != ServicingVehiclePhase.DOCKED

    companion object {

        /**
         * Creates an unassigned servicing vehicle
         * docked at its home station.
         */
        fun dockedAt(
            station: ServicingStation
        ): ServicingVehicleState {

            return ServicingVehicleState(
                id = "SERVICER-01",
                phase = ServicingVehiclePhase.DOCKED,
                position = OrbitalDisplayGeometry.onOrbit(
                    station.orbitAngleDegrees,
                    station.orbitRadiusFraction
                )
            )
        }
    }
}

