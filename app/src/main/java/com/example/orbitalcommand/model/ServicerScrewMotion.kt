
package com.example.orbitalcommand.model

/**
 * Whether the free manipulator is removing or
 * reinstalling a panel screw.
 */
enum class ServicerScrewDirection {
    REMOVE,
    INSTALL
}

/**
 * A screwdriver operation in the onboard camera.
 */
data class ServicerScrewOperation(
    val screw: RepairHotspot,
    val direction: ServicerScrewDirection
)

/**
 * Converts successful repair-state changes into
 * visual actions for SERVICER-01's onboard camera.
 *
 * The repair simulator remains authoritative.
 * This class never changes work-order progress.
 */
object ServicerScrewMotion {

    const val DURATION_MILLIS = 2600

    val screws = listOf(
        RepairHotspot.SCREW_1,
        RepairHotspot.SCREW_2,
        RepairHotspot.SCREW_3,
        RepairHotspot.SCREW_4
    )

    /**
     * Detects individual screw changes.
     *
     * A newly loosened screw triggers removal.
     * A resecured screw triggers installation.
     *
     * Restarting the procedure does not trigger
     * four unwanted installation animations.
     */
    fun detect(
        before: Set<RepairHotspot>,
        after: Set<RepairHotspot>,
        currentStep: RepairProcedureStep
    ): List<ServicerScrewOperation> {

        if (
            currentStep == RepairProcedureStep.UNSCREW &&
            after.isEmpty()
        ) {
            return emptyList()
        }

        return screws.mapNotNull { screw ->

            when {

                screw !in before && screw in after ->
                    ServicerScrewOperation(
                        screw,
                        ServicerScrewDirection.REMOVE
                    )

                screw in before && screw !in after ->
                    ServicerScrewOperation(
                        screw,
                        ServicerScrewDirection.INSTALL
                    )

                else -> null
            }
        }
    }

    /**
     * Returns each fastener's position in the
     * existing satellite GLB coordinate system.
     *
     * Numbering matches the procedural model:
     *
     * 1 = lower left
     * 2 = lower right
     * 3 = upper left
     * 4 = upper right
     */
    fun screwPosition(
        screw: RepairHotspot
    ): SatellitePoint3D = when (screw) {

        RepairHotspot.SCREW_1 ->
            SatellitePoint3D(-0.41f, 0.49f, 0.67f)

        RepairHotspot.SCREW_2 ->
            SatellitePoint3D(0.41f, 0.49f, 0.67f)

        RepairHotspot.SCREW_3 ->
            SatellitePoint3D(-0.41f, -0.49f, 0.67f)

        RepairHotspot.SCREW_4 ->
            SatellitePoint3D(0.41f, -0.49f, 0.67f)

        else ->
            error("Not a panel fastener: $screw")
    }

    /**
     * Controls the free arm's reach:
     *
     * 0–25%: approach the screw
     * 25–79%: maintain contact
     * 79–100%: retract
     */
    fun reach(progress: Float): Float {

        val t = progress.coerceIn(0f, 1f)

        return when {

            t < 0.25f ->
                smoothStep(t / 0.25f)

            t < 0.79f ->
                1f

            else ->
                1f - smoothStep(
                    (t - 0.79f) / 0.21f
                )
        }
    }

    /**
     * Describes progress through the actual
     * screwdriver-turning interval.
     */
    fun turn(progress: Float): Float =
        smoothStep(
            (progress.coerceIn(0f, 1f) - 0.25f) /
                    0.54f
        )

    /**
     * Simulates four screwdriver revolutions.
     *
     * Unscrewing and reinstallation use
     * opposite rotation directions.
     */
    fun screwdriverDegrees(
        operation: ServicerScrewOperation,
        progress: Float
    ): Float {

        val direction = if (
            operation.direction ==
            ServicerScrewDirection.REMOVE
        ) {
            -1f
        } else {
            1f
        }

        return direction * 1440f * turn(progress)
    }

    /**
     * Calculates how far a screw has moved
     * out of its panel socket.
     *
     * Removal increases from 0 to 1.
     * Installation decreases from 1 to 0.
     */
    fun screwBackout(
        operation: ServicerScrewOperation,
        progress: Float
    ): Float {

        val turned = turn(progress)

        return if (
            operation.direction ==
            ServicerScrewDirection.REMOVE
        ) {
            turned
        } else {
            1f - turned
        }
    }

    /**
     * Smooth acceleration and deceleration
     * for robotic-arm movement.
     */
    private fun smoothStep(value: Float): Float {

        val t = value.coerceIn(0f, 1f)

        return t * t * (3f - 2f * t)
    }
}
