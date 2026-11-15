package com.example.orbitalcommand.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A visually simulated operation on the service cover, dish, or an instrument. */
enum class ComponentPovAction {
    SCAN,
    OPEN_COVER,
    CLOSE_COVER,
    DETACH,
    INSTALL,
    REPAIR
}

/** A successful state transition queued for the free/right manipulator. */
data class ComponentPovOperation(
    val target: RepairHotspot,
    val action: ComponentPovAction
)

/**
 * Pure movement curves and physical service-point coordinates for CAM-01.
 *
 * All points are expressed in spacecraft GLB coordinates: X right, Y up,
 * Z toward the forward-facing service camera. This module does not grant
 * repair permissions: [RoboticRepairSimulator] remains authoritative.
 */
object ServicerComponentPovMotion {
    const val DURATION_MILLIS = 3000
    const val COVER_OPEN_DEGREES = 112f

    /** A mission restart must not look like somebody closed the panel. */
    fun changes(
        before: RoboticRepairSession,
        after: RoboticRepairSession
    ): List<ComponentPovOperation> {
        if (before.workOrderId != after.workOrderId ||
            before.satelliteId != after.satelliteId ||
            before.damage != after.damage
        ) return emptyList()

        if (before.step != after.step && after.method == null &&
            after.partCondition == RepairPartCondition.DAMAGED &&
            after.loosenedScrews.isEmpty() && !after.panelOpen &&
            after.step == RepairProcedureStep.UNSCREW
        ) return emptyList()

        val actions = mutableListOf<ComponentPovOperation>()
        if (before.panelOpen != after.panelOpen) {
            actions += ComponentPovOperation(
                RepairHotspot.ACCESS_PANEL,
                if (after.panelOpen) ComponentPovAction.OPEN_COVER
                else ComponentPovAction.CLOSE_COVER
            )
        }
        if (!before.inspectedDish && after.inspectedDish) {
            actions += ComponentPovOperation(
                RepairHotspot.COMMUNICATIONS_DISH, ComponentPovAction.SCAN
            )
        }
        if (!before.inspectedInternalModule && after.inspectedInternalModule) {
            actions += ComponentPovOperation(after.target, ComponentPovAction.SCAN)
        }
        if (before.partCondition != after.partCondition) {
            val action = when (before.partCondition to after.partCondition) {
                RepairPartCondition.DAMAGED to RepairPartCondition.REMOVED ->
                    ComponentPovAction.DETACH
                RepairPartCondition.REMOVED to RepairPartCondition.REPLACED ->
                    ComponentPovAction.INSTALL
                RepairPartCondition.DAMAGED to RepairPartCondition.REPAIRED ->
                    ComponentPovAction.REPAIR
                else -> null
            }
            if (action != null && after.damage != EquipmentDamage.SOLAR_ARRAY_DAMAGE) {
                actions += ComponentPovOperation(after.target, action)
            }
        }
        return actions
    }

    /** The point approached by the free manipulator when working on a part. */
    fun targetPoint(target: RepairHotspot): SatellitePoint3D = when (target) {
        RepairHotspot.COMMUNICATIONS_DISH, RepairHotspot.DISH_MOUNT ->
            SatellitePoint3D(0f, 1.43f, 0.47f)
        RepairHotspot.PAYLOAD_SENSOR, RepairHotspot.PAYLOAD_SOCKET ->
            SatellitePoint3D(-0.25f, 0f, 0.65f)
        RepairHotspot.THERMAL_CONTROLLER, RepairHotspot.THERMAL_SOCKET ->
            SatellitePoint3D(0.25f, 0f, 0.65f)
        RepairHotspot.ACCESS_PANEL -> coverHandlePoint(0f)
        else -> error("Unsupported component for arm animation: $target")
    }

    /** A service-bay cover handle following its hinge as the cover opens. */
    fun coverHandlePoint(angleDegrees: Float): SatellitePoint3D {
        val a = Math.toRadians(angleDegrees.toDouble())
        return SatellitePoint3D(
            x = (0.52 - 0.89 * cos(a)).toFloat(),
            y = 0.01f,
            z = (0.55 + 0.89 * sin(a)).toFloat()
        )
    }

    /** The gripper reaches the part, stays engaged, and retracts. */
    fun armReach(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when {
            p < .20f -> smooth(p / .20f)
            p < .79f -> 1f
            else -> 1f - smooth((p - .79f) / .21f)
        }
    }

    /** A cover opens gradually rather than popping to its final orientation. */
    fun coverAngle(operation: ComponentPovOperation?, progress: Float, panelOpen: Boolean): Float {
        if (operation?.target != RepairHotspot.ACCESS_PANEL) {
            return if (panelOpen) COVER_OPEN_DEGREES else 0f
        }
        val movement = smooth((progress - .20f) / .60f)
        return if (operation.action == ComponentPovAction.OPEN_COVER) {
            COVER_OPEN_DEGREES * movement
        } else if (operation.action == ComponentPovAction.CLOSE_COVER) {
            COVER_OPEN_DEGREES * (1f - movement)
        } else if (panelOpen) COVER_OPEN_DEGREES else 0f
    }

    /**
     * The removed part follows the gripper away from its socket; the
     * replacement follows the reverse curve into the empty socket.
     */
    fun partOffset(
        operation: ComponentPovOperation,
        progress: Float
    ): SatellitePoint3D {
        val fraction = when (operation.action) {
            ComponentPovAction.DETACH -> smooth((progress - .26f) / .52f)
            ComponentPovAction.INSTALL -> 1f - smooth((progress - .20f) / .58f)
            else -> 0f
        }
        if (fraction == 0f) return SatellitePoint3D(0f, 0f, 0f)
        val direction = when (operation.target) {
            RepairHotspot.COMMUNICATIONS_DISH -> SatellitePoint3D(.30f, .75f, .58f)
            RepairHotspot.PAYLOAD_SENSOR -> SatellitePoint3D(-.47f, .42f, .55f)
            RepairHotspot.THERMAL_CONTROLLER -> SatellitePoint3D(.47f, .42f, .55f)
            else -> SatellitePoint3D(0f, 0f, 0f)
        }
        return SatellitePoint3D(
            direction.x * fraction,
            direction.y * fraction,
            direction.z * fraction
        )
    }

    /** The gripper follows the moving cover or physical component. */
    fun handTarget(operation: ComponentPovOperation, progress: Float): SatellitePoint3D {
        if (operation.target == RepairHotspot.ACCESS_PANEL) {
            return coverHandlePoint(coverAngle(operation, progress,
                operation.action == ComponentPovAction.OPEN_COVER))
        }
        val root = targetPoint(operation.target)
        val offset = partOffset(operation, progress)
        return SatellitePoint3D(root.x + offset.x, root.y + offset.y, root.z + offset.z)
    }

    /** Contact flashes during in-place repair, not during every movement. */
    fun sparkVisible(operation: ComponentPovOperation, progress: Float): Boolean =
        operation.action == ComponentPovAction.REPAIR && progress in .38f.. .48f

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/**
 * Geometrically valid rail grip locations on the spacecraft's forward face.
 * The Port and Starboard rails run along Y, while the upper/lower rails
 * run along X. All four rods share the same forward-facing Z plane.
 */
object ServicerRailContact {
    const val SIDE_X = .74f
    const val EDGE_Y = .83f
    const val FACE_Z = .42f

    fun anchor(face: SatelliteServiceFace): SatellitePoint3D = when (face) {
        SatelliteServiceFace.PORT -> SatellitePoint3D(-SIDE_X, .43f, FACE_Z)
        SatelliteServiceFace.STARBOARD -> SatellitePoint3D(SIDE_X, -.43f, FACE_Z)
        SatelliteServiceFace.DORSAL -> SatellitePoint3D(-.22f, EDGE_Y, FACE_Z)
        SatelliteServiceFace.VENTRAL -> SatellitePoint3D(-.22f, -EDGE_Y, FACE_Z)
        SatelliteServiceFace.AFT,
        SatelliteServiceFace.SERVICE -> SatellitePoint3D(-SIDE_X, -.43f, FACE_Z)
    }

    fun liesOnRail(point: SatellitePoint3D): Boolean {
        val side = abs(abs(point.x) - SIDE_X) < .001f && abs(point.y) <= .75f
        val end = abs(abs(point.y) - EDGE_Y) < .001f && abs(point.x) <= .71f
        return (side || end) && abs(point.z - FACE_Z) < .001f
    }
}
