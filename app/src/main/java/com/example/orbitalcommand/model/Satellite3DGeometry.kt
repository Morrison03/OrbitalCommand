
package com.example.orbitalcommand.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A location in the spacecraft's local
 * three-dimensional coordinate space.
 */
data class SatellitePoint3D(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * A perspective-projected position in
 * the 400 by 300 service viewport.
 */
data class SatelliteScreenPoint(
    val x: Float,
    val y: Float,
    val depth: Float
)

/**
 * User-controlled camera orientation
 * and magnification.
 */
data class SatelliteCamera(
    val yawDegrees: Float = 20f,
    val pitchDegrees: Float = -12f,
    val zoom: Float = 1f
) {

    /**
     * Turns the model while keeping its
     * service side accessible.
     */
    fun rotated(
        deltaYaw: Float,
        deltaPitch: Float
    ): SatelliteCamera = copy(
        yawDegrees = (yawDegrees + deltaYaw)
            .coerceIn(-55f, 55f),
        pitchDegrees = (pitchDegrees + deltaPitch)
            .coerceIn(-30f, 30f)
    )

    /**
     * Changes camera magnification without
     * allowing extreme clipping.
     */
    fun zoomed(
        delta: Float
    ): SatelliteCamera = copy(
        zoom = (zoom + delta).coerceIn(0.70f, 1.25f)
    )
}

/**
 * Shared three-dimensional projection and
 * maintenance-part coordinates.
 *
 * The renderer uses the same hinge transformation
 * and camera projection as hit testing.
 *
 * This keeps targets aligned with visible parts.
 */
object Satellite3DGeometry {

    const val VIEW_WIDTH = 400f
    const val VIEW_HEIGHT = 300f

    private const val FOCAL_DISTANCE = 4.5f
    private const val MODEL_SCALE = 104f
    private const val HIT_RADIUS = 23f

    private const val HINGE_X = 0.52f
    private const val HINGE_Z = 0.52f
    private const val COVER_OPEN_ANGLE_DEGREES = -112f

    /**
     * Projects a model-space point through
     * the operator's orbital camera.
     */
    fun project(
        point: SatellitePoint3D,
        camera: SatelliteCamera
    ): SatelliteScreenPoint {

        val yaw = Math.toRadians(
            camera.yawDegrees.toDouble()
        )

        val pitch = Math.toRadians(
            camera.pitchDegrees.toDouble()
        )

        val cy = cos(yaw).toFloat()
        val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()

        val rotatedX = point.x * cy + point.z * sy

        val yawDepth = -point.x * sy + point.z * cy

        val rotatedY =
            point.y * cp - yawDepth * sp

        val rotatedDepth =
            point.y * sp + yawDepth * cp

        val perspective =
            FOCAL_DISTANCE /
                    (FOCAL_DISTANCE - rotatedDepth)
                        .coerceAtLeast(0.5f)

        return SatelliteScreenPoint(
            x = VIEW_WIDTH / 2f +
                    rotatedX *
                    MODEL_SCALE *
                    perspective *
                    camera.zoom,
            y = VIEW_HEIGHT / 2f +
                    rotatedY *
                    MODEL_SCALE *
                    perspective *
                    camera.zoom,
            depth = rotatedDepth
        )
    }

    /**
     * Rotates a cover point around the
     * right-hand vertical hinge.
     *
     * openAmount ranges from 0 (closed)
     * to 1 (fully open).
     *
     * All cover vertices and its touch target
     * share this transformation.
     */
    fun hingedPanelPoint(
        point: SatellitePoint3D,
        openAmount: Float
    ): SatellitePoint3D {

        val clamped = openAmount.coerceIn(0f, 1f)

        val radians = Math.toRadians(
            (COVER_OPEN_ANGLE_DEGREES * clamped)
                .toDouble()
        )

        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()

        val dx = point.x - HINGE_X
        val dz = point.z - HINGE_Z

        return SatellitePoint3D(
            x = HINGE_X + dx * c + dz * s,
            y = point.y,
            z = HINGE_Z - dx * s + dz * c
        )
    }

    /**
     * Returns the final location of
     * a currently interactive component.
     */
    fun hotspotPosition(
        hotspot: RepairHotspot,
        session: RoboticRepairSession
    ): SatellitePoint3D = when (hotspot) {

        RepairHotspot.SCREW_1 ->
            SatellitePoint3D(-0.43f, -0.53f, 0.54f)

        RepairHotspot.SCREW_2 ->
            SatellitePoint3D(0.43f, -0.53f, 0.54f)

        RepairHotspot.SCREW_3 ->
            SatellitePoint3D(-0.43f, 0.53f, 0.54f)

        RepairHotspot.SCREW_4 ->
            SatellitePoint3D(0.43f, 0.53f, 0.54f)

        RepairHotspot.ACCESS_PANEL ->
            hingedPanelPoint(
                SatellitePoint3D(0f, 0f, 0.41f),
                if (session.panelOpen) 1f else 0f
            )

        RepairHotspot.COMPONENT,
        RepairHotspot.COMPONENT_SOCKET ->
            SatellitePoint3D(0f, 0f, 0.43f)

        RepairHotspot.DIAGNOSTIC_PORT ->
            SatellitePoint3D(0.89f, -0.85f, 0.48f)
    }

    /**
     * Returns only the components enabled
     * by the current procedure step.
     */
    fun activeHotspots(
        session: RoboticRepairSession
    ): List<RepairHotspot> = when (session.step) {

        RepairProcedureStep.UNSCREW ->
            screws().filterNot {
                it in session.loosenedScrews
            }

        RepairProcedureStep.SECURE_SCREWS ->
            screws().filter {
                it in session.loosenedScrews
            }

        RepairProcedureStep.LIFT_PANEL,
        RepairProcedureStep.CLOSE_PANEL ->
            listOf(RepairHotspot.ACCESS_PANEL)

        RepairProcedureStep.EXAMINE,
        RepairProcedureStep.REMOVE_PART,
        RepairProcedureStep.REPAIR_IN_PLACE ->
            listOf(RepairHotspot.COMPONENT)

        RepairProcedureStep.INSTALL_REPLACEMENT ->
            listOf(RepairHotspot.COMPONENT_SOCKET)

        RepairProcedureStep.VERIFY ->
            listOf(RepairHotspot.DIAGNOSTIC_PORT)

        RepairProcedureStep.CHOOSE_METHOD,
        RepairProcedureStep.COMPLETE ->
            emptyList()
    }

    /**
     * Finds the nearest enabled component
     * under an operator's touch.
     */
    fun hitTest(
        x: Float,
        y: Float,
        camera: SatelliteCamera,
        session: RoboticRepairSession
    ): RepairHotspot? = activeHotspots(session)
        .map { hotspot ->

            val point = project(
                hotspotPosition(hotspot, session),
                camera
            )

            hotspot to hypot(
                x - point.x,
                y - point.y
            )
        }
        .filter { (_, distance) ->
            distance <= HIT_RADIUS
        }
        .minByOrNull { (_, distance) ->
            distance
        }
        ?.first

    /**
     * Provides the four fasteners
     * securing the service panel.
     */
    private fun screws() = listOf(
        RepairHotspot.SCREW_1,
        RepairHotspot.SCREW_2,
        RepairHotspot.SCREW_3,
        RepairHotspot.SCREW_4
    )
}
