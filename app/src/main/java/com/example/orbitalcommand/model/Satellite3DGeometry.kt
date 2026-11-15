package com.example.orbitalcommand.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Point in the procedural satellite's model coordinate system. */
data class SatellitePoint3D(val x: Float, val y: Float, val z: Float)

/** Projected screen coordinate with view-space depth for painter sorting. */
data class SatelliteScreenPoint(val x: Float, val y: Float, val depth: Float)

/** Operator-controlled virtual camera, independent of SERVICER-01 CAM-01. */
data class SatelliteCamera(
    val yawDegrees: Float = 20f,
    val pitchDegrees: Float = -12f,
    val zoom: Float = 1f
) {
    fun rotated(deltaYaw: Float, deltaPitch: Float) = copy(
        yawDegrees = (yawDegrees + deltaYaw).coerceIn(-180f, 180f),
        pitchDegrees = (pitchDegrees + deltaPitch).coerceIn(-84f, 84f)
    )
    fun zoomed(delta: Float) = copy(zoom = (zoom + delta).coerceIn(0.70f, 1.25f))
}

/** Projection, service-panel hinge, and physical hotspot picking. */
object Satellite3DGeometry {
    const val VIEW_WIDTH = 400f
    const val VIEW_HEIGHT = 300f
    private const val FOCAL_DISTANCE = 4.5f
    private const val MODEL_SCALE = 104f
    private const val HIT_RADIUS = 23f
    private const val HINGE_X = 0.52f
    private const val HINGE_Z = 0.52f
    private const val COVER_OPEN_ANGLE_DEGREES = -112f

    /** Perspective-project a local spacecraft point through the operator camera. */
    fun project(point: SatellitePoint3D, camera: SatelliteCamera): SatelliteScreenPoint {
        val yaw = Math.toRadians(camera.yawDegrees.toDouble())
        val pitch = Math.toRadians(camera.pitchDegrees.toDouble())
        val cy = cos(yaw).toFloat()
        val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()
        val rx = point.x * cy + point.z * sy
        val dz = -point.x * sy + point.z * cy
        val ry = point.y * cp - dz * sp
        val depth = point.y * sp + dz * cp
        val perspective = FOCAL_DISTANCE / (FOCAL_DISTANCE - depth).coerceAtLeast(.5f)
        return SatelliteScreenPoint(
            x = VIEW_WIDTH / 2f + rx * MODEL_SCALE * perspective * camera.zoom,
            y = VIEW_HEIGHT / 2f + ry * MODEL_SCALE * perspective * camera.zoom,
            depth = depth
        )
    }

    /** Swing a panel point around the right-side hinge. */
    fun hingedPanelPoint(point: SatellitePoint3D, openAmount: Float): SatellitePoint3D {
        val angle = Math.toRadians((COVER_OPEN_ANGLE_DEGREES * openAmount.coerceIn(0f, 1f)).toDouble())
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val dx = point.x - HINGE_X
        val dz = point.z - HINGE_Z
        return SatellitePoint3D(
            HINGE_X + dx * c + dz * s,
            point.y,
            HINGE_Z - dx * s + dz * c
        )
    }

    /** Real visual locations of repair targets, including separate internal modules. */
    fun hotspotPosition(hotspot: RepairHotspot, session: RoboticRepairSession): SatellitePoint3D =
        when (hotspot) {
            RepairHotspot.SCREW_1 -> SatellitePoint3D(-.43f, -.53f, .54f)
            RepairHotspot.SCREW_2 -> SatellitePoint3D(.43f, -.53f, .54f)
            RepairHotspot.SCREW_3 -> SatellitePoint3D(-.43f, .53f, .54f)
            RepairHotspot.SCREW_4 -> SatellitePoint3D(.43f, .53f, .54f)
            RepairHotspot.ACCESS_PANEL -> hingedPanelPoint(
                SatellitePoint3D(0f, 0f, .41f), if (session.panelOpen) 1f else 0f
            )
            RepairHotspot.PAYLOAD_SENSOR, RepairHotspot.PAYLOAD_SOCKET ->
                SatellitePoint3D(-.25f, 0f, .47f)
            RepairHotspot.THERMAL_CONTROLLER, RepairHotspot.THERMAL_SOCKET ->
                SatellitePoint3D(.25f, 0f, .47f)
            RepairHotspot.COMPONENT, RepairHotspot.COMPONENT_SOCKET ->
                SatellitePoint3D(0f, 0f, .47f)
            RepairHotspot.SOLAR_PANEL_A, RepairHotspot.SOLAR_MOUNT_A ->
                SatellitePoint3D(-1.36f, 0f, .08f)
            RepairHotspot.SOLAR_PANEL_B, RepairHotspot.SOLAR_MOUNT_B ->
                SatellitePoint3D(1.36f, 0f, .08f)
            // The procedural Canvas projects positive Y DOWN the screen.
            // Its dorsal/top surface is therefore at NEGATIVE Y.
            // SceneView/Filament uses positive Y UP and places its dish there.
            RepairHotspot.COMMUNICATIONS_DISH, RepairHotspot.DISH_MOUNT ->
                SatellitePoint3D(0f, -1.35f, .20f)
            RepairHotspot.DIAGNOSTIC_PORT ->
                SatellitePoint3D(.89f, -.85f, .48f)
        }

    /** Targets that can be acted on at the current procedural step. */
    fun activeHotspots(session: RoboticRepairSession): List<RepairHotspot> = when (session.step) {
        RepairProcedureStep.SCAN_SOLAR_PANELS ->
            listOf(RepairHotspot.SOLAR_PANEL_A, RepairHotspot.SOLAR_PANEL_B)
                .filterNot { it in session.scannedSolarPanels }
        RepairProcedureStep.SCAN_DISH -> listOf(RepairHotspot.COMMUNICATIONS_DISH)
        RepairProcedureStep.UNSCREW -> screws().filterNot { it in session.loosenedScrews }
        RepairProcedureStep.SECURE_SCREWS -> screws().filter { it in session.loosenedScrews }
        RepairProcedureStep.LIFT_PANEL, RepairProcedureStep.CLOSE_PANEL ->
            listOf(RepairHotspot.ACCESS_PANEL)
        RepairProcedureStep.EXAMINE, RepairProcedureStep.REMOVE_PART,
        RepairProcedureStep.REPAIR_IN_PLACE -> listOf(session.target)
        RepairProcedureStep.INSTALL_REPLACEMENT -> listOf(session.replacementSocket)
        RepairProcedureStep.VERIFY -> listOf(RepairHotspot.DIAGNOSTIC_PORT)
        RepairProcedureStep.CHOOSE_METHOD, RepairProcedureStep.COMPLETE -> emptyList()
    }

    /** Select the closest ACTIVE projected hotspot inside its touch radius. */
    fun hitTest(
        x: Float, y: Float,
        camera: SatelliteCamera,
        session: RoboticRepairSession
    ): RepairHotspot? = activeHotspots(session)
        .map { hotspot ->
            val p = project(hotspotPosition(hotspot, session), camera)
            hotspot to hypot(x - p.x, y - p.y)
        }
        .filter { (_, distance) -> distance <= HIT_RADIUS }
        .minByOrNull { (_, distance) -> distance }
        ?.first

    private fun screws() = listOf(
        RepairHotspot.SCREW_1, RepairHotspot.SCREW_2,
        RepairHotspot.SCREW_3, RepairHotspot.SCREW_4
    )
}
