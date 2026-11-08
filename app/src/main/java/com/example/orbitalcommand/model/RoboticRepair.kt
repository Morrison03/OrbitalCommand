
package com.example.orbitalcommand.model

import kotlin.math.hypot

/**
 * Tools supported by the schematic robotic repair console.
 */
enum class RepairTool(val displayName: String) {
    NONE("No tool"),
    INSPECTION_SCANNER("Inspection scanner"),
    ISOLATION_CLAMP("Isolation clamp"),
    SOLAR_DRIVER("Solar array driver"),
    THERMAL_WRENCH("Thermal wrench"),
    SENSOR_DRIVER("Sensor driver")
}

/**
 * Ordered tasks required before a simulated hardware
 * repair can be recorded as completed.
 */
enum class RepairProcedureStep(
    val displayName: String,
    val instruction: String
) {
    INSPECT(
        "Inspect",
        "Scan the damaged subsystem to confirm the fault."
    ),
    ISOLATE(
        "Isolate",
        "Isolate the affected subsystem before maintenance."
    ),
    REPLACE(
        "Replace",
        "Replace the failed component using the matching tool."
    ),
    VERIFY(
        "Verify",
        "Scan the repaired component and validate the result."
    ),
    COMPLETE(
        "Complete",
        "All simulated servicing checks passed."
    )
}

/**
 * A position in the repair console's normalized
 * two-dimensional work area.
 */
data class RepairArmPosition(
    val x: Float,
    val y: Float
) {

    /**
     * Returns the distance to another work-area position.
     */
    fun distanceTo(other: RepairArmPosition): Float =
        hypot(other.x - x, other.y - y)
}

/**
 * A direction commanded using touchscreen controls.
 */
enum class RepairArmDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

/**
 * Preserves operator progress through one repair mission.
 */
data class RoboticRepairSession(
    val workOrderId: Long,
    val satelliteId: String,
    val damage: EquipmentDamage,
    val step: RepairProcedureStep =
        RepairProcedureStep.INSPECT,
    val armPosition: RepairArmPosition =
        RepairArmPosition(0.5f, 0.5f),
    val tool: RepairTool = RepairTool.NONE,
    val feedback: String =
        "Move the arm to the marked inspection zone."
) {

    /**
     * Indicates that every required servicing
     * task has been completed.
     */
    val isComplete: Boolean
        get() = step == RepairProcedureStep.COMPLETE
}

/**
 * The outcome of attempting the current servicing task.
 */
data class RepairProcedureResult(
    val session: RoboticRepairSession,
    val succeeded: Boolean,
    val finished: Boolean
)

/**
 * Runs a touchscreen-driven robotic maintenance simulation.
 *
 * Operators position the repair arm at predefined work zones
 * and select appropriate tools before executing tasks.
 *
 * These are schematic operator-interaction checks, not
 * validated physical repair or spacecraft-control mechanics.
 */
object RoboticRepairSimulator {

    const val MOVEMENT_STEP = 0.10f
    const val WORK_ZONE_RADIUS = 0.13f

    /**
     * Begins a repair procedure for a queued work order.
     */
    fun start(
        order: RepairWorkOrder
    ): RoboticRepairSession {

        require(order.status == RepairStatus.QUEUED) {
            "Only queued repair orders can begin a procedure."
        }

        require(order.damage != EquipmentDamage.NONE) {
            "A repair procedure requires equipment damage."
        }

        return RoboticRepairSession(
            workOrderId = order.id,
            satelliteId = order.satelliteId,
            damage = order.damage
        )
    }

    /**
     * Returns the required work zone for each task.
     *
     * All coordinates use the range 0.0 to 1.0.
     */
    fun workZone(
        step: RepairProcedureStep
    ): RepairArmPosition {

        return when (step) {

            RepairProcedureStep.INSPECT ->
                RepairArmPosition(0.20f, 0.25f)

            RepairProcedureStep.ISOLATE ->
                RepairArmPosition(0.80f, 0.25f)

            RepairProcedureStep.REPLACE ->
                RepairArmPosition(0.80f, 0.75f)

            RepairProcedureStep.VERIFY ->
                RepairArmPosition(0.20f, 0.75f)

            RepairProcedureStep.COMPLETE ->
                RepairArmPosition(0.50f, 0.50f)
        }
    }

    /**
     * Determines which tool is required for a task.
     *
     * Replacement tools depend on the equipment failure.
     */
    fun requiredTool(
        step: RepairProcedureStep,
        damage: EquipmentDamage
    ): RepairTool {

        return when (step) {

            RepairProcedureStep.INSPECT,
            RepairProcedureStep.VERIFY ->
                RepairTool.INSPECTION_SCANNER

            RepairProcedureStep.ISOLATE ->
                RepairTool.ISOLATION_CLAMP

            RepairProcedureStep.REPLACE -> when (damage) {

                EquipmentDamage.SOLAR_ARRAY_DAMAGE ->
                    RepairTool.SOLAR_DRIVER

                EquipmentDamage.THERMAL_RADIATOR_DAMAGE ->
                    RepairTool.THERMAL_WRENCH

                EquipmentDamage.PAYLOAD_SENSOR_DAMAGE ->
                    RepairTool.SENSOR_DRIVER

                EquipmentDamage.NONE ->
                    RepairTool.NONE
            }

            RepairProcedureStep.COMPLETE ->
                RepairTool.NONE
        }
    }

    /**
     * Moves the robotic arm one bounded increment.
     *
     * The arm cannot move outside the work area.
     */
    fun move(
        session: RoboticRepairSession,
        direction: RepairArmDirection
    ): RoboticRepairSession {

        if (session.isComplete) {
            return session
        }

        val x = session.armPosition.x
        val y = session.armPosition.y

        val next = when (direction) {

            RepairArmDirection.UP ->
                RepairArmPosition(x, y - MOVEMENT_STEP)

            RepairArmDirection.DOWN ->
                RepairArmPosition(x, y + MOVEMENT_STEP)

            RepairArmDirection.LEFT ->
                RepairArmPosition(x - MOVEMENT_STEP, y)

            RepairArmDirection.RIGHT ->
                RepairArmPosition(x + MOVEMENT_STEP, y)
        }

        return session.copy(
            armPosition = RepairArmPosition(
                x = next.x.coerceIn(0.05f, 0.95f),
                y = next.y.coerceIn(0.05f, 0.95f)
            ),
            feedback =
                "Position the arm inside the highlighted service zone."
        )
    }

    /**
     * Selects the active servicing tool.
     */
    fun selectTool(
        session: RoboticRepairSession,
        tool: RepairTool
    ): RoboticRepairSession {

        return if (session.isComplete) {
            session
        } else {
            session.copy(
                tool = tool,
                feedback = "Selected ${tool.displayName}."
            )
        }
    }

    /**
     * Attempts the current task.
     *
     * The operator must use the required tool and place
     * the arm within the correct work zone.
     *
     * A successful task advances to the next step.
     * The final verification step marks the procedure
     * complete, allowing the ViewModel to close the
     * corresponding maintenance work order.
     */
    fun execute(
        session: RoboticRepairSession
    ): RepairProcedureResult {

        if (session.isComplete) {

            return RepairProcedureResult(
                session = session.copy(
                    feedback = "This repair is already complete."
                ),
                succeeded = false,
                finished = true
            )
        }

        val required = requiredTool(
            step = session.step,
            damage = session.damage
        )

        if (session.tool != required) {

            return RepairProcedureResult(
                session = session.copy(
                    feedback =
                        "Select ${required.displayName} to perform " +
                                "${session.step.displayName.lowercase()}."
                ),
                succeeded = false,
                finished = false
            )
        }

        val distance = session.armPosition.distanceTo(
            workZone(session.step)
        )

        if (distance > WORK_ZONE_RADIUS) {

            return RepairProcedureResult(
                session = session.copy(
                    feedback =
                        "Move the arm into the highlighted " +
                                "${session.step.displayName.lowercase()} zone."
                ),
                succeeded = false,
                finished = false
            )
        }

        val next = when (session.step) {

            RepairProcedureStep.INSPECT ->
                RepairProcedureStep.ISOLATE

            RepairProcedureStep.ISOLATE ->
                RepairProcedureStep.REPLACE

            RepairProcedureStep.REPLACE ->
                RepairProcedureStep.VERIFY

            RepairProcedureStep.VERIFY ->
                RepairProcedureStep.COMPLETE

            RepairProcedureStep.COMPLETE ->
                RepairProcedureStep.COMPLETE
        }

        val feedback = if (
            next == RepairProcedureStep.COMPLETE
        ) {
            "Verification passed. Simulated repair completed."
        } else {
            "${session.step.displayName} complete. " +
                    "Next: ${next.displayName.lowercase()}."
        }

        return RepairProcedureResult(
            session = session.copy(
                step = next,
                feedback = feedback
            ),
            succeeded = true,
            finished = next == RepairProcedureStep.COMPLETE
        )
    }
}

