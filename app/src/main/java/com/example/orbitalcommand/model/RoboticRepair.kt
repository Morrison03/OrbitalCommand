
package com.example.orbitalcommand.model

/** Operator-selected tool used in the satellite service view. */
enum class RepairTool(val displayName: String) {
    NONE("No tool"),
    SCREWDRIVER("Precision screwdriver"),
    GRIPPER("Panel gripper"),
    SCANNER("Inspection scanner"),
    EXTRACTOR("Component extractor"),
    REPAIR_KIT("Component repair kit"),
    INSTALLER("Replacement installer")
}

/** The current physical interaction required by the repair procedure. */
enum class RepairProcedureStep(
    val displayName: String,
    val instruction: String
) {
    UNSCREW(
        "Remove fasteners",
        "Select the screwdriver, then tap all four panel screws."
    ),
    LIFT_PANEL(
        "Lift access panel",
        "Select the gripper, then tap the service panel to lift it."
    ),
    EXAMINE(
        "Examine damaged component",
        "Select the scanner, then tap the exposed damaged component."
    ),
    CHOOSE_METHOD(
        "Choose service method",
        "Choose in-place repair or removal and replacement."
    ),
    REMOVE_PART(
        "Dismantle damaged part",
        "Select the extractor, then tap the damaged component."
    ),
    INSTALL_REPLACEMENT(
        "Install replacement",
        "Select the installer, then tap the empty component socket."
    ),
    REPAIR_IN_PLACE(
        "Repair existing part",
        "Select the repair kit, then tap the damaged component."
    ),
    CLOSE_PANEL(
        "Close access panel",
        "Select the gripper, then tap the lifted panel to close it."
    ),
    SECURE_SCREWS(
        "Secure fasteners",
        "Select the screwdriver, then tap all four screws to tighten them."
    ),
    VERIFY(
        "Verify repair",
        "Select the scanner, then tap the diagnostic port to run the final check."
    ),
    COMPLETE(
        "Repair complete",
        "The repair passed its simulated verification procedure."
    )
}

/** Available approaches to restoring damaged equipment. */
enum class RepairServiceMethod(val displayName: String) {
    REPAIR_IN_PLACE("Repair the existing part"),
    REPLACE_COMPONENT("Remove and install replacement")
}

/** Current visible condition of the serviceable component. */
enum class RepairPartCondition {
    DAMAGED,
    REMOVED,
    REPAIRED,
    REPLACED
}

/** Tappable physical components on the satellite model. */
enum class RepairHotspot {
    SCREW_1,
    SCREW_2,
    SCREW_3,
    SCREW_4,
    ACCESS_PANEL,
    COMPONENT,
    COMPONENT_SOCKET,
    DIAGNOSTIC_PORT;

    val isScrew: Boolean
        get() = this == SCREW_1 ||
                this == SCREW_2 ||
                this == SCREW_3 ||
                this == SCREW_4
}

/**
 * Stores the physical-looking configuration of one
 * satellite's interactive maintenance procedure.
 *
 * Its state persists during temporary telemetry loss.
 */
data class RoboticRepairSession(
    val workOrderId: Long,
    val satelliteId: String,
    val damage: EquipmentDamage,
    val step: RepairProcedureStep =
        RepairProcedureStep.UNSCREW,
    val tool: RepairTool = RepairTool.NONE,
    val loosenedScrews: Set<RepairHotspot> = emptySet(),
    val panelOpen: Boolean = false,
    val method: RepairServiceMethod? = null,
    val partCondition: RepairPartCondition =
        RepairPartCondition.DAMAGED,
    val feedback: String =
        "Remove the four highlighted access-panel screws."
) {

    val isComplete: Boolean
        get() = step == RepairProcedureStep.COMPLETE

    val fastenersRemaining: Int
        get() = when (step) {
            RepairProcedureStep.UNSCREW ->
                4 - loosenedScrews.size

            RepairProcedureStep.SECURE_SCREWS ->
                loosenedScrews.size

            else -> 0
        }
}

/** Result of an operator interaction with a satellite part. */
data class RepairInteractionResult(
    val session: RoboticRepairSession,
    val succeeded: Boolean,
    val completed: Boolean = false,
    val stepChanged: Boolean = false
)

/**
 * Defines deterministic satellite servicing rules.
 *
 * Actions are illustrative maintenance simulations,
 * not real spacecraft repair instructions.
 *
 * The ViewModel separately validates the work order,
 * vehicle formation, and target communications.
 */
object RoboticRepairSimulator {

    private val screws = setOf(
        RepairHotspot.SCREW_1,
        RepairHotspot.SCREW_2,
        RepairHotspot.SCREW_3,
        RepairHotspot.SCREW_4
    )

    /** Starts a service procedure for a queued work order. */
    fun start(
        order: RepairWorkOrder
    ): RoboticRepairSession {

        require(order.status == RepairStatus.QUEUED) {
            "The work order must be queued before servicing."
        }

        require(order.damage != EquipmentDamage.NONE) {
            "A service operation requires damaged equipment."
        }

        return RoboticRepairSession(
            workOrderId = order.id,
            satelliteId = order.satelliteId,
            damage = order.damage
        )
    }

    /** Selects a tool without advancing the procedure. */
    fun selectTool(
        session: RoboticRepairSession,
        tool: RepairTool
    ): RoboticRepairSession {

        if (session.isComplete) {
            return session
        }

        return session.copy(
            tool = tool,
            feedback =
                "Selected ${tool.displayName}. " +
                        session.step.instruction
        )
    }

    /** Identifies the serviceable component for a fault. */
    fun componentLabel(
        damage: EquipmentDamage
    ): String = when (damage) {

        EquipmentDamage.SOLAR_ARRAY_DAMAGE ->
            "Solar array power controller"

        EquipmentDamage.THERMAL_RADIATOR_DAMAGE ->
            "Thermal circulation module"

        EquipmentDamage.PAYLOAD_SENSOR_DAMAGE ->
            "Payload sensor assembly"

        EquipmentDamage.NONE ->
            "Unspecified component"
    }

    /** Returns the required tool for the current action. */
    fun requiredTool(
        step: RepairProcedureStep
    ): RepairTool = when (step) {

        RepairProcedureStep.UNSCREW,
        RepairProcedureStep.SECURE_SCREWS ->
            RepairTool.SCREWDRIVER

        RepairProcedureStep.LIFT_PANEL,
        RepairProcedureStep.CLOSE_PANEL ->
            RepairTool.GRIPPER

        RepairProcedureStep.EXAMINE,
        RepairProcedureStep.VERIFY ->
            RepairTool.SCANNER

        RepairProcedureStep.REMOVE_PART ->
            RepairTool.EXTRACTOR

        RepairProcedureStep.INSTALL_REPLACEMENT ->
            RepairTool.INSTALLER

        RepairProcedureStep.REPAIR_IN_PLACE ->
            RepairTool.REPAIR_KIT

        RepairProcedureStep.CHOOSE_METHOD,
        RepairProcedureStep.COMPLETE ->
            RepairTool.NONE
    }

    /**
     * Chooses either repair-in-place or removal
     * and installation of a replacement component.
     */
    fun chooseMethod(
        session: RoboticRepairSession,
        method: RepairServiceMethod
    ): RepairInteractionResult {

        if (
            session.step != RepairProcedureStep.CHOOSE_METHOD
        ) {
            return reject(
                session,
                "Examine the component before choosing a repair method."
            )
        }

        val next = when (method) {

            RepairServiceMethod.REPAIR_IN_PLACE ->
                RepairProcedureStep.REPAIR_IN_PLACE

            RepairServiceMethod.REPLACE_COMPONENT ->
                RepairProcedureStep.REMOVE_PART
        }

        return accept(
            before = session,
            after = session.copy(
                method = method,
                step = next
            ),
            message =
                "Method selected: ${method.displayName}. " +
                        next.instruction
        )
    }

    /**
     * Processes one physical interaction with the model.
     *
     * Invalid actions cannot bypass an earlier task.
     * The final valid diagnostics action is the only
     * interaction that produces a completion signal.
     */
    fun interact(
        session: RoboticRepairSession,
        hotspot: RepairHotspot
    ): RepairInteractionResult {

        if (session.isComplete) {
            return reject(
                session,
                "This repair is already complete."
            )
        }

        val required = requiredTool(session.step)

        if (
            session.step == RepairProcedureStep.CHOOSE_METHOD
        ) {
            return reject(
                session,
                "Choose Repair or Replace below the satellite model."
            )
        }

        if (session.tool != required) {
            return reject(
                session,
                "Select ${required.displayName} first."
            )
        }

        return when (session.step) {

            RepairProcedureStep.UNSCREW -> {

                if (!hotspot.isScrew) {
                    return reject(
                        session,
                        "Tap a highlighted panel screw."
                    )
                }

                if (hotspot in session.loosenedScrews) {
                    return reject(
                        session,
                        "That screw is already removed."
                    )
                }

                val removed =
                    session.loosenedScrews + hotspot

                val next = if (removed == screws) {
                    RepairProcedureStep.LIFT_PANEL
                } else {
                    RepairProcedureStep.UNSCREW
                }

                accept(
                    session,
                    session.copy(
                        step = next,
                        loosenedScrews = removed
                    ),
                    if (next == RepairProcedureStep.LIFT_PANEL)
                        "All four screws removed. Lift the access panel."
                    else
                        "Screw removed (${removed.size}/4)."
                )
            }

            RepairProcedureStep.LIFT_PANEL -> {

                if (hotspot != RepairHotspot.ACCESS_PANEL) {
                    return reject(
                        session,
                        "Tap the service panel to lift its cover."
                    )
                }

                accept(
                    session,
                    session.copy(
                        step = RepairProcedureStep.EXAMINE,
                        panelOpen = true
                    ),
                    "Panel lifted. The internal component is exposed for inspection."
                )
            }

            RepairProcedureStep.EXAMINE -> {

                if (hotspot != RepairHotspot.COMPONENT) {
                    return reject(
                        session,
                        "Scan the exposed component."
                    )
                }

                accept(
                    session,
                    session.copy(
                        step = RepairProcedureStep.CHOOSE_METHOD
                    ),
                    "Inspection confirms damage in the " +
                            "${componentLabel(session.damage)}. " +
                            "Choose a service method."
                )
            }

            RepairProcedureStep.REMOVE_PART -> {

                if (hotspot != RepairHotspot.COMPONENT) {
                    return reject(
                        session,
                        "Tap the damaged part to dismantle it."
                    )
                }

                accept(
                    session,
                    session.copy(
                        step =
                            RepairProcedureStep.INSTALL_REPLACEMENT,
                        partCondition = RepairPartCondition.REMOVED
                    ),
                    "Damaged part removed. The mounting socket is now empty."
                )
            }

            RepairProcedureStep.INSTALL_REPLACEMENT -> {

                if (
                    hotspot != RepairHotspot.COMPONENT_SOCKET
                ) {
                    return reject(
                        session,
                        "Tap the empty mounting socket."
                    )
                }

                if (
                    session.partCondition !=
                    RepairPartCondition.REMOVED
                ) {
                    return reject(
                        session,
                        "Remove the damaged component first."
                    )
                }

                accept(
                    session,
                    session.copy(
                        step = RepairProcedureStep.CLOSE_PANEL,
                        partCondition =
                            RepairPartCondition.REPLACED
                    ),
                    "Replacement installed. Close the access panel."
                )
            }

            RepairProcedureStep.REPAIR_IN_PLACE -> {

                if (hotspot != RepairHotspot.COMPONENT) {
                    return reject(
                        session,
                        "Tap the damaged part to repair it."
                    )
                }

                accept(
                    session,
                    session.copy(
                        step = RepairProcedureStep.CLOSE_PANEL,
                        partCondition =
                            RepairPartCondition.REPAIRED
                    ),
                    "Component repaired in place. Close the access panel."
                )
            }

            RepairProcedureStep.CLOSE_PANEL -> {

                if (hotspot != RepairHotspot.ACCESS_PANEL) {
                    return reject(
                        session,
                        "Tap the lifted cover to close the panel."
                    )
                }

                if (
                    session.partCondition ==
                    RepairPartCondition.DAMAGED ||
                    session.partCondition ==
                    RepairPartCondition.REMOVED
                ) {
                    return reject(
                        session,
                        "Restore the component before closing the panel."
                    )
                }

                accept(
                    session,
                    session.copy(
                        step =
                            RepairProcedureStep.SECURE_SCREWS,
                        panelOpen = false
                    ),
                    "Panel closed. Reinstall all four fasteners."
                )
            }

            RepairProcedureStep.SECURE_SCREWS -> {

                if (!hotspot.isScrew) {
                    return reject(
                        session,
                        "Tap an empty screw mount to tighten a fastener."
                    )
                }

                if (
                    hotspot !in session.loosenedScrews
                ) {
                    return reject(
                        session,
                        "That screw is already secured."
                    )
                }

                val remaining =
                    session.loosenedScrews - hotspot

                val next = if (remaining.isEmpty()) {
                    RepairProcedureStep.VERIFY
                } else {
                    RepairProcedureStep.SECURE_SCREWS
                }

                accept(
                    session,
                    session.copy(
                        step = next,
                        loosenedScrews = remaining
                    ),
                    if (next == RepairProcedureStep.VERIFY)
                        "Panel secured. Run final diagnostics."
                    else
                        "Screw secured (${4 - remaining.size}/4)."
                )
            }

            RepairProcedureStep.VERIFY -> {

                if (
                    hotspot !=
                    RepairHotspot.DIAGNOSTIC_PORT
                ) {
                    return reject(
                        session,
                        "Tap the diagnostic port for final verification."
                    )
                }

                if (
                    session.panelOpen ||
                    session.loosenedScrews.isNotEmpty() ||
                    session.partCondition ==
                    RepairPartCondition.DAMAGED ||
                    session.partCondition ==
                    RepairPartCondition.REMOVED
                ) {
                    return reject(
                        session,
                        "Panel closure and component restoration must be verified first."
                    )
                }

                accept(
                    session,
                    session.copy(
                        step = RepairProcedureStep.COMPLETE
                    ),
                    "Diagnostics passed. The simulated repair is complete.",
                    completed = true
                )
            }

            RepairProcedureStep.CHOOSE_METHOD,
            RepairProcedureStep.COMPLETE -> {
                reject(
                    session,
                    "This interaction is not available now."
                )
            }
        }
    }

    /** Rejects an action without advancing the procedure. */
    private fun reject(
        session: RoboticRepairSession,
        message: String
    ): RepairInteractionResult =
        RepairInteractionResult(
            session = session.copy(feedback = message),
            succeeded = false
        )

    /** Accepts an action and reports whether the task changed. */
    private fun accept(
        before: RoboticRepairSession,
        after: RoboticRepairSession,
        message: String,
        completed: Boolean = false
    ): RepairInteractionResult =
        RepairInteractionResult(
            session = after.copy(feedback = message),
            succeeded = true,
            completed = completed,
            stepChanged = before.step != after.step
        )
}

/**
 * Shared touch coordinates for the satellite service view.
 *
 * Coordinates are normalized to a 400 by 300 workspace,
 * which is scaled to the screen size by Jetpack Compose.
 */
object SatelliteRepairGeometry {

    const val WIDTH = 400f
    const val HEIGHT = 300f

    val screwLocations = mapOf(
        RepairHotspot.SCREW_1 to Pair(151f, 104f),
        RepairHotspot.SCREW_2 to Pair(249f, 104f),
        RepairHotspot.SCREW_3 to Pair(151f, 222f),
        RepairHotspot.SCREW_4 to Pair(249f, 222f)
    )

    /**
     * Identifies which service component the operator
     * touched on the displayed satellite.
     */
    fun hitTest(
        x: Float,
        y: Float,
        session: RoboticRepairSession
    ): RepairHotspot? {

        if (
            kotlin.math.hypot(
                x - 343f,
                y - 43f
            ) <= 27f
        ) {
            return RepairHotspot.DIAGNOSTIC_PORT
        }

        for ((screw, position) in screwLocations) {

            if (
                kotlin.math.hypot(
                    x - position.first,
                    y - position.second
                ) <= 20f
            ) {
                return screw
            }
        }

        if (session.panelOpen) {

            if (
                x in 171f..229f &&
                y in 129f..200f
            ) {
                return if (
                    session.partCondition ==
                    RepairPartCondition.REMOVED
                ) {
                    RepairHotspot.COMPONENT_SOCKET
                } else {
                    RepairHotspot.COMPONENT
                }
            }

            if (
                x in 277f..380f &&
                y in 94f..230f
            ) {
                return RepairHotspot.ACCESS_PANEL
            }

        } else if (
            x in 141f..259f &&
            y in 94f..233f
        ) {
            return RepairHotspot.ACCESS_PANEL
        }

        return null
    }
}
