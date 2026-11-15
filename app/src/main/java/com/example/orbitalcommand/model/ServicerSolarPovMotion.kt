package com.example.orbitalcommand.model

/** The three visible solar servicing operations in SERVICER-01's POV. */
enum class SolarPovAction { DETACH, INSTALL, REPAIR }

/** A queued animation for a particular physical solar wing. */
data class SolarPovOperation(
    val wing: RepairHotspot,
    val action: SolarPovAction
)

/**
 * Physical layout and timing shared by the camera and test suite.
 *
 * The solar wings are extracted from the original spacecraft GLBs and
 * pivot at the exposed boom connector (X = -0.87 for Port, +0.87 for Starboard).
 * The old, overlapping replacement-panel overlay is deliberately not used.
 *
 * Coordinates are in Filament world space (Y upward), with the service
 * bay facing +Z. Rail-grip hand targets lie along the four perimeter bars.
 */
object ServicerSolarPovMotion {
    const val DURATION_MILLIS = 3300
    const val PORT_ROOT_X = -0.87f
    const val STARBOARD_ROOT_X = 0.87f
    const val CAMERA_SLIDE = 1.40f

    fun isSolarFace(face: SatelliteServiceFace): Boolean =
        face == SatelliteServiceFace.PORT || face == SatelliteServiceFace.STARBOARD

    /** Never orbit ±90 degrees when working on a solar wing. */
    fun targetYaw(face: SatelliteServiceFace): Float =
        if (isSolarFace(face)) 0f else face.viewYaw

    fun targetPitch(face: SatelliteServiceFace): Float =
        if (isSolarFace(face)) 0f else face.viewPitch

    /** Positive slide centers Starboard; negative centers Port. */
    fun cameraSlide(face: SatelliteServiceFace): Float = when (face) {
        SatelliteServiceFace.PORT -> -CAMERA_SLIDE
        SatelliteServiceFace.STARBOARD -> CAMERA_SLIDE
        else -> 0f
    }

    /** Port keeps the same front-facing view but rolls the camera upside down. */
    fun cameraRoll(face: SatelliteServiceFace): Float =
        if (face == SatelliteServiceFace.PORT) 180f else 0f

    fun rootX(wing: RepairHotspot): Float = when (wing) {
        RepairHotspot.SOLAR_PANEL_A -> PORT_ROOT_X
        RepairHotspot.SOLAR_PANEL_B -> STARBOARD_ROOT_X
        else -> error("Expected a physical solar wing, got $wing")
    }

    /** The connector where the right arm approaches and the spark occurs. */
    fun wingConnector(wing: RepairHotspot) = SatellitePoint3D(
        rootX(wing), 0f, 0.04f
    )

    /** A fresh left-hand rail grip at the outer edge of each working face. */
    fun leftGripRail(face: SatelliteServiceFace) = when (face) {
        SatelliteServiceFace.PORT -> SatellitePoint3D(-0.74f, 0.43f, 0.42f)
        SatelliteServiceFace.STARBOARD -> SatellitePoint3D(0.74f, -0.43f, 0.42f)
        else -> SatellitePoint3D(-0.74f, -0.43f, 0.42f)
    }

    /** Reaches before release, follows the wing, and withdraws afterward. */
    fun armReach(progress: Float): Float = when {
        progress <= .22f -> smooth(progress / .22f)
        progress <= .80f -> 1f
        else -> 1f - smooth((progress - .80f) / .20f)
    }

    /** The panel cannot separate until the connector spark has finished. */
    fun detachment(progress: Float): Float = smooth(
        (progress - .30f) / .48f
    )

    /** Installation approaches from outside, then seats into the stem. */
    fun installation(progress: Float): Float = smooth(
        (progress - .18f) / .60f
    )

    /** Small emission burst immediately before detachment, not after it. */
    fun sparkVisible(action: SolarPovAction, progress: Float): Boolean = when (action) {
        SolarPovAction.DETACH -> progress in .23f.. .30f
        SolarPovAction.REPAIR -> progress in .42f.. .51f
        SolarPovAction.INSTALL -> false
    }

    fun wingOffset(action: SolarPovAction, wing: RepairHotspot, progress: Float): SatellitePoint3D {
        val fraction = when (action) {
            SolarPovAction.DETACH -> detachment(progress)
            SolarPovAction.INSTALL -> 1f - installation(progress)
            SolarPovAction.REPAIR -> 0f
        }
        // Normalize the fully seated/resting position to positive zero.
        // The Port wing's negative direction would otherwise produce -0.0f,
        // which is mathematically equivalent to 0.0f but fails exact
        // Float equality in JUnit's two-argument assertEquals overload.
        if (fraction == 0f) {
            return SatellitePoint3D(0f, 0f, 0f)
        }

        val sign = if (rootX(wing) < 0f) -1f else 1f
        return SatellitePoint3D(sign * .60f * fraction, .28f * fraction, .95f * fraction)
    }

    /** Converts a successful session state change into a single camera operation. */
    fun fromChange(
        before: RepairPartCondition,
        after: RepairPartCondition,
        wing: RepairHotspot
    ): SolarPovOperation? = when {
        before == after -> null
        before == RepairPartCondition.DAMAGED && after == RepairPartCondition.REMOVED ->
            SolarPovOperation(wing, SolarPovAction.DETACH)
        before == RepairPartCondition.REMOVED && after == RepairPartCondition.REPLACED ->
            SolarPovOperation(wing, SolarPovAction.INSTALL)
        before == RepairPartCondition.DAMAGED && after == RepairPartCondition.REPAIRED ->
            SolarPovOperation(wing, SolarPovAction.REPAIR)
        else -> null
    }

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
