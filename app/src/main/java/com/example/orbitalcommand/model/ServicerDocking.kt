
package com.example.orbitalcommand.model

/**
 * Six exterior faces that SERVICER-01 may inspect.
 */
enum class SatelliteServiceFace(
    val displayName: String,
    val viewYaw: Float,
    val viewPitch: Float
) {
    SERVICE("Service", 20f, -12f),
    PORT("Port", -90f, 0f),
    STARBOARD("Starboard", 90f, 0f),
    DORSAL("Top", 0f, -80f),
    VENTRAL("Bottom", 0f, 80f),
    AFT("Rear", 180f, 0f)
}

/**
 * The current state of the satellite-attachment sequence.
 */
enum class ServicerDockPhase {
    ORBITING,
    APPROACHING,
    GRIPPED,
    MOVING
}

/**
 * Local positioning state for one maintenance mission.
 *
 * A grip at the target satellite is different
 * from docking at the servicing station OS-001.
 *
 * This is a scripted simulation, not a physical
 * spacecraft guidance or robotics controller.
 */
data class ServicerDockingState(
    val missionOrderId: Long,
    val phase: ServicerDockPhase =
        ServicerDockPhase.ORBITING,
    val activeFace: SatelliteServiceFace =
        SatelliteServiceFace.SERVICE,
    val targetFace: SatelliteServiceFace =
        SatelliteServiceFace.SERVICE,
    val choosingFace: Boolean = false,
    val movementNumber: Int = 0
) {

    /**
     * Indicates that at least one gripper is
     * simulated as retaining a rail attachment.
     */
    val grippingRail: Boolean
        get() =
            phase == ServicerDockPhase.GRIPPED ||
                    phase == ServicerDockPhase.MOVING
}

/**
 * Controls transitions for the simulated
 * satellite docking and repositioning procedure.
 *
 * Telemetry and formation verification are
 * supplied by the existing mission-control system.
 */
object ServicerDockingController {

    /**
     * Starts a simulated approach from verified formation.
     */
    fun beginDock(
        state: ServicerDockingState,
        navigationReady: Boolean
    ): ServicerDockingState = if (
        navigationReady &&
        state.phase == ServicerDockPhase.ORBITING
    ) {

        state.copy(
            phase = ServicerDockPhase.APPROACHING,
            choosingFace = false
        )

    } else {
        state
    }

    /**
     * Completes the approach after its animation finishes.
     */
    fun finishDock(
        state: ServicerDockingState,
        navigationReady: Boolean
    ): ServicerDockingState = if (
        navigationReady &&
        state.phase == ServicerDockPhase.APPROACHING
    ) {

        state.copy(
            phase = ServicerDockPhase.GRIPPED
        )

    } else {
        state
    }

    /**
     * Shows or hides the six selectable satellite faces.
     */
    fun toggleMove(
        state: ServicerDockingState,
        navigationReady: Boolean
    ): ServicerDockingState = if (
        navigationReady &&
        state.phase == ServicerDockPhase.GRIPPED
    ) {

        state.copy(
            choosingFace = !state.choosingFace
        )

    } else {
        state
    }

    /**
     * Starts a rail-supported move to another face.
     */
    fun selectFace(
        state: ServicerDockingState,
        face: SatelliteServiceFace,
        navigationReady: Boolean
    ): ServicerDockingState {

        if (
            !navigationReady ||
            state.phase != ServicerDockPhase.GRIPPED ||
            !state.choosingFace
        ) {
            return state
        }

        if (face == state.activeFace) {

            return state.copy(
                choosingFace = false
            )
        }

        return state.copy(
            phase = ServicerDockPhase.MOVING,
            targetFace = face,
            choosingFace = false,
            movementNumber = state.movementNumber + 1
        )
    }

    /**
     * Completes repositioning only while
     * target telemetry remains verified.
     */
    fun finishMove(
        state: ServicerDockingState,
        navigationReady: Boolean
    ): ServicerDockingState = if (
        navigationReady &&
        state.phase == ServicerDockPhase.MOVING
    ) {

        state.copy(
            phase = ServicerDockPhase.GRIPPED,
            activeFace = state.targetFace
        )

    } else {
        state
    }
}
