package com.example.orbitalcommand.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A world-space eye position and the target the onboard camera looks at. */
data class ServicerPovPose(
    val eyeX: Float,
    val eyeY: Float,
    val eyeZ: Float,
    val targetX: Float = 0f,
    val targetY: Float = 0f,
    val targetZ: Float = 0f
)

/**
 * Computes a continuous orbital camera path around a spacecraft at the origin.
 * This controls only the simulated POV, never the interactive procedural camera.
 *
 * Yaw selects a satellite face. Pitch gives a top or bottom viewpoint.
 * Approach changes camera standoff distance; transit adds a modest clearance arc.
 */
object ServicerPovRig {

    fun pose(
        yawDegrees: Float,
        pitchDegrees: Float,
        approachAmount: Float,
        transitAmount: Float
    ): ServicerPovPose {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.coerceIn(-84f, 84f).toDouble())
        val approach = approachAmount.coerceIn(0f, 1f)
        val transit = transitAmount.coerceIn(0f, 1f)
        val clearance = (0.22 * sin(PI * transit)).toFloat()
        val radius = 4.9f - 1.65f * approach + clearance
        val horizontal = (radius * cos(pitch)).toFloat()

        return ServicerPovPose(
            eyeX = (horizontal * sin(yaw)).toFloat(),
            eyeY = (-radius * sin(pitch)).toFloat(),
            eyeZ = (horizontal * cos(yaw)).toFloat()
        )
    }
}
