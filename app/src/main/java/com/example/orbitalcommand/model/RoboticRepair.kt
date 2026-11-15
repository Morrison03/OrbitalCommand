package com.example.orbitalcommand.model

/** Tool currently held by SERVICER-01's free manipulator. */
enum class RepairTool(val displayName: String) {
    NONE("No tool"),
    SCREWDRIVER("Precision screwdriver"),
    GRIPPER("Panel gripper"),
    SCANNER("Inspection scanner"),
    EXTRACTOR("Component extractor"),
    REPAIR_KIT("Component repair kit"),
    INSTALLER("Replacement installer")
}

/** Ordered, component-specific servicing actions. */
enum class RepairProcedureStep(val displayName: String, val instruction: String) {
    SCAN_SOLAR_PANELS("Diagnose solar arrays", "Scan BOTH solar wings to locate the failed array. Move to Port for Solar A and Starboard for Solar B."),
    SCAN_DISH("Inspect antenna dish", "Move to Top, select the scanner, and tap the communications dish."),
    UNSCREW("Remove fasteners", "Move to Service; use the screwdriver on all four access-panel screws."),
    LIFT_PANEL("Lift access panel", "Use the gripper to lift the service cover."),
    EXAMINE("Inspect internal module", "Scan the highlighted payload sensor or thermal controller, not the adjacent healthy module."),
    CHOOSE_METHOD("Choose repair method", "Choose in-place repair or component replacement."),
    REMOVE_PART("Extract damaged hardware", "Use the extractor on the diagnosed damaged component."),
    INSTALL_REPLACEMENT("Install replacement", "Use the installer on the empty mount/socket."),
    REPAIR_IN_PLACE("Repair hardware in place", "Use the repair kit on the diagnosed damaged component."),
    CLOSE_PANEL("Close service panel", "Use the gripper to close the service cover."),
    SECURE_SCREWS("Reinstall fasteners", "Use the screwdriver to reinstall all four panel screws."),
    VERIFY("Run final diagnostics", "Move to Service; scan the diagnostic port to verify the repaired subsystem."),
    COMPLETE("Repair complete", "The component-specific repair has passed simulated verification.")
}

/** Operator-selected service strategy. */
enum class RepairServiceMethod(val displayName: String) {
    REPAIR_IN_PLACE("Repair the existing part"),
    REPLACE_COMPONENT("Remove and install replacement")
}

/** Current condition of the component named by the work order. */
enum class RepairPartCondition { DAMAGED, REMOVED, REPAIRED, REPLACED }

/** Touchable spacecraft components; original hotspots remain for compatibility. */
enum class RepairHotspot {
    SCREW_1, SCREW_2, SCREW_3, SCREW_4,
    ACCESS_PANEL, COMPONENT, COMPONENT_SOCKET, DIAGNOSTIC_PORT,
    SOLAR_PANEL_A, SOLAR_PANEL_B, SOLAR_MOUNT_A, SOLAR_MOUNT_B,
    COMMUNICATIONS_DISH, DISH_MOUNT,
    PAYLOAD_SENSOR, PAYLOAD_SOCKET, THERMAL_CONTROLLER, THERMAL_SOCKET;

    val isScrew: Boolean
        get() = this in setOf(SCREW_1, SCREW_2, SCREW_3, SCREW_4)
}

/** A deterministic repair session whose progress survives temporary signal loss. */
data class RoboticRepairSession(
    val workOrderId: Long,
    val satelliteId: String,
    val damage: EquipmentDamage,
    val step: RepairProcedureStep = RepairProcedureStep.UNSCREW,
    val tool: RepairTool = RepairTool.NONE,
    val loosenedScrews: Set<RepairHotspot> = emptySet(),
    val panelOpen: Boolean = false,
    val method: RepairServiceMethod? = null,
    val partCondition: RepairPartCondition = RepairPartCondition.DAMAGED,
    val feedback: String = "Select the highlighted target to begin servicing.",
    val faultySolarPanel: RepairHotspot = RepairHotspot.SOLAR_PANEL_A,
    val scannedSolarPanels: Set<RepairHotspot> = emptySet(),
    val inspectedDish: Boolean = false,
    val inspectedInternalModule: Boolean = false
) {
    val isComplete: Boolean get() = step == RepairProcedureStep.COMPLETE

    val fastenersRemaining: Int get() = when (step) {
        RepairProcedureStep.UNSCREW -> 4 - loosenedScrews.size
        RepairProcedureStep.SECURE_SCREWS -> loosenedScrews.size
        else -> 0
    }

    /** The subsystem physically targeted by this work order. */
    val target: RepairHotspot get() = when (damage) {
        EquipmentDamage.SOLAR_ARRAY_DAMAGE -> faultySolarPanel
        EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE -> RepairHotspot.COMMUNICATIONS_DISH
        EquipmentDamage.PAYLOAD_SENSOR_DAMAGE -> RepairHotspot.PAYLOAD_SENSOR
        EquipmentDamage.THERMAL_RADIATOR_DAMAGE -> RepairHotspot.THERMAL_CONTROLLER
        EquipmentDamage.NONE -> RepairHotspot.COMPONENT
    }

    val replacementSocket: RepairHotspot get() = when (target) {
        RepairHotspot.SOLAR_PANEL_A -> RepairHotspot.SOLAR_MOUNT_A
        RepairHotspot.SOLAR_PANEL_B -> RepairHotspot.SOLAR_MOUNT_B
        RepairHotspot.COMMUNICATIONS_DISH -> RepairHotspot.DISH_MOUNT
        RepairHotspot.PAYLOAD_SENSOR -> RepairHotspot.PAYLOAD_SOCKET
        RepairHotspot.THERMAL_CONTROLLER -> RepairHotspot.THERMAL_SOCKET
        else -> RepairHotspot.COMPONENT_SOCKET
    }

    val isInternalRepair: Boolean get() = damage == EquipmentDamage.PAYLOAD_SENSOR_DAMAGE ||
        damage == EquipmentDamage.THERMAL_RADIATOR_DAMAGE

    val payloadCondition: RepairPartCondition get() =
        if (damage == EquipmentDamage.PAYLOAD_SENSOR_DAMAGE) partCondition else RepairPartCondition.REPAIRED

    val thermalCondition: RepairPartCondition get() =
        if (damage == EquipmentDamage.THERMAL_RADIATOR_DAMAGE) partCondition else RepairPartCondition.REPAIRED
}

/** Outcome of one operator action. Invalid actions never advance repair state. */
data class RepairInteractionResult(
    val session: RoboticRepairSession,
    val succeeded: Boolean,
    val completed: Boolean = false,
    val stepChanged: Boolean = false
)

/**
 * Pure, deterministic work-order state machine.
 *
 * Every subsystem has a different entry procedure. Solar arrays require
 * scanning both wings; the dish requires a separate external inspection;
 * payload and thermal work require opening the same service bay but touching
 * different physical modules. Repair success is reported only after VERIFY.
 */
object RoboticRepairSimulator {
    private val screws = setOf(
        RepairHotspot.SCREW_1, RepairHotspot.SCREW_2,
        RepairHotspot.SCREW_3, RepairHotspot.SCREW_4
    )
    private val solar = setOf(RepairHotspot.SOLAR_PANEL_A, RepairHotspot.SOLAR_PANEL_B)

    /** Starts the work order with the appropriate external/internal sequence. */
    fun start(order: RepairWorkOrder): RoboticRepairSession {
        require(order.status == RepairStatus.QUEUED) { "Work order must be queued." }
        require(order.damage != EquipmentDamage.NONE) { "Damaged hardware is required." }
        val first = when (order.damage) {
            EquipmentDamage.SOLAR_ARRAY_DAMAGE -> RepairProcedureStep.SCAN_SOLAR_PANELS
            EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE -> RepairProcedureStep.SCAN_DISH
            else -> RepairProcedureStep.UNSCREW
        }
        // Stable throughout the work order; opposite satellite/order parity can
        // change the failure location without randomly changing it after restart.
        val failed = if ((order.id + order.satelliteId.hashCode().toLong()) % 2L == 0L)
            RepairHotspot.SOLAR_PANEL_A else RepairHotspot.SOLAR_PANEL_B
        return RoboticRepairSession(
            workOrderId = order.id,
            satelliteId = order.satelliteId,
            damage = order.damage,
            step = first,
            faultySolarPanel = failed,
            feedback = first.instruction
        )
    }

    /** Changes tools without executing maintenance. */
    fun selectTool(session: RoboticRepairSession, tool: RepairTool): RoboticRepairSession =
        if (session.isComplete) session else session.copy(
            tool = tool, feedback = "Selected ${tool.displayName}. ${session.step.instruction}"
        )

    /** Human-readable physical part for each equipment damage type. */
    fun componentLabel(damage: EquipmentDamage): String = when (damage) {
        EquipmentDamage.SOLAR_ARRAY_DAMAGE -> "Solar wing (diagnose A/B)"
        EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE -> "High-gain communications dish"
        EquipmentDamage.THERMAL_RADIATOR_DAMAGE -> "Thermal controller / heat-pipe assembly"
        EquipmentDamage.PAYLOAD_SENSOR_DAMAGE -> "Payload sensor module"
        EquipmentDamage.NONE -> "Unspecified component"
    }

    /** Tool required to perform the current mechanical action. */
    fun requiredTool(step: RepairProcedureStep): RepairTool = when (step) {
        RepairProcedureStep.SCAN_SOLAR_PANELS,
        RepairProcedureStep.SCAN_DISH,
        RepairProcedureStep.EXAMINE,
        RepairProcedureStep.VERIFY -> RepairTool.SCANNER
        RepairProcedureStep.UNSCREW,
        RepairProcedureStep.SECURE_SCREWS -> RepairTool.SCREWDRIVER
        RepairProcedureStep.LIFT_PANEL,
        RepairProcedureStep.CLOSE_PANEL -> RepairTool.GRIPPER
        RepairProcedureStep.REMOVE_PART -> RepairTool.EXTRACTOR
        RepairProcedureStep.INSTALL_REPLACEMENT -> RepairTool.INSTALLER
        RepairProcedureStep.REPAIR_IN_PLACE -> RepairTool.REPAIR_KIT
        RepairProcedureStep.CHOOSE_METHOD,
        RepairProcedureStep.COMPLETE -> RepairTool.NONE
    }

    /** Selects a valid repair branch after diagnosis. */
    fun chooseMethod(session: RoboticRepairSession, method: RepairServiceMethod): RepairInteractionResult {
        if (session.step != RepairProcedureStep.CHOOSE_METHOD) {
            return reject(session, "Finish component diagnosis before choosing a method.")
        }
        val next = if (method == RepairServiceMethod.REPAIR_IN_PLACE)
            RepairProcedureStep.REPAIR_IN_PLACE else RepairProcedureStep.REMOVE_PART
        return accept(session, session.copy(method = method, step = next),
            "${method.displayName}. ${next.instruction}")
    }

    /** Applies an interaction to exactly the active subsystem. */
    fun interact(session: RoboticRepairSession, hotspot: RepairHotspot): RepairInteractionResult {
        if (session.isComplete) return reject(session, "This repair has already been verified.")
        if (session.step == RepairProcedureStep.CHOOSE_METHOD) {
            return reject(session, "Choose Repair or Replace below the model.")
        }
        if (session.tool != requiredTool(session.step)) {
            return reject(session, "Select ${requiredTool(session.step).displayName} first.")
        }
        return when (session.step) {
            RepairProcedureStep.SCAN_SOLAR_PANELS -> {
                if (hotspot !in solar) return reject(session, "Scan Solar A or Solar B.")
                if (hotspot in session.scannedSolarPanels) return reject(session, "That wing has already been scanned; inspect the other wing.")
                val scanned = session.scannedSolarPanels + hotspot
                val damaged = hotspot == session.faultySolarPanel
                val next = if (scanned == solar) RepairProcedureStep.CHOOSE_METHOD
                    else RepairProcedureStep.SCAN_SOLAR_PANELS
                accept(session, session.copy(scannedSolarPanels = scanned, step = next),
                    "${if (hotspot == RepairHotspot.SOLAR_PANEL_A) "Solar A" else "Solar B"}: " +
                        (if (damaged) "FAULT DETECTED — damaged photovoltaic cells/interconnects." else "PASS — normal output.") +
                        (if (scanned == solar) " Both wings diagnosed. Choose repair or replacement." else " Scan the other wing."))
            }
            RepairProcedureStep.SCAN_DISH -> {
                if (hotspot != RepairHotspot.COMMUNICATIONS_DISH) return reject(session, "Scan the communications dish.")
                accept(session, session.copy(inspectedDish = true, step = RepairProcedureStep.CHOOSE_METHOD),
                    "Antenna feed/mount fault confirmed. Choose realignment repair or dish replacement.")
            }
            RepairProcedureStep.UNSCREW -> {
                if (!hotspot.isScrew) return reject(session, "Select a highlighted panel screw.")
                if (hotspot in session.loosenedScrews) return reject(session, "That screw was already removed.")
                val removed = session.loosenedScrews + hotspot
                val next = if (removed == screws) RepairProcedureStep.LIFT_PANEL else session.step
                accept(session, session.copy(step = next, loosenedScrews = removed),
                    if (next == RepairProcedureStep.LIFT_PANEL) "All screws removed. Lift the cover."
                    else "Screw removed (${removed.size}/4).")
            }
            RepairProcedureStep.LIFT_PANEL -> {
                if (hotspot != RepairHotspot.ACCESS_PANEL) return reject(session, "Lift the access cover.")
                accept(session, session.copy(panelOpen = true, step = RepairProcedureStep.EXAMINE),
                    "Service bay open. Scan the highlighted ${componentLabel(session.damage)}.")
            }
            RepairProcedureStep.EXAMINE -> {
                if (hotspot != session.target) return reject(session,
                    "Scan the ${componentLabel(session.damage)}, not the neighboring healthy module.")
                accept(session, session.copy(inspectedInternalModule = true, step = RepairProcedureStep.CHOOSE_METHOD),
                    "${componentLabel(session.damage)} fault confirmed; adjacent module passes diagnostics.")
            }
            RepairProcedureStep.REMOVE_PART -> {
                if (hotspot != session.target) return reject(session,
                    "Extract the diagnosed ${componentLabel(session.damage)}.")
                accept(session, session.copy(partCondition = RepairPartCondition.REMOVED,
                    step = RepairProcedureStep.INSTALL_REPLACEMENT),
                    "Damaged hardware extracted. Install a replacement at the empty mount.")
            }
            RepairProcedureStep.INSTALL_REPLACEMENT -> {
                if (hotspot != session.replacementSocket || session.partCondition != RepairPartCondition.REMOVED) {
                    return reject(session, "Install replacement into the diagnosed component's empty mount.")
                }
                val next = if (session.isInternalRepair) RepairProcedureStep.CLOSE_PANEL else RepairProcedureStep.VERIFY
                accept(session, session.copy(partCondition = RepairPartCondition.REPLACED, step = next),
                    "Replacement secured. ${next.instruction}")
            }
            RepairProcedureStep.REPAIR_IN_PLACE -> {
                if (hotspot != session.target) return reject(session,
                    "Repair the diagnosed ${componentLabel(session.damage)}, not another component.")
                val next = if (session.isInternalRepair) RepairProcedureStep.CLOSE_PANEL else RepairProcedureStep.VERIFY
                accept(session, session.copy(partCondition = RepairPartCondition.REPAIRED, step = next),
                    "In-place restoration complete. ${next.instruction}")
            }
            RepairProcedureStep.CLOSE_PANEL -> {
                if (hotspot != RepairHotspot.ACCESS_PANEL) return reject(session, "Close the service cover.")
                if (!session.isInternalRepair || session.partCondition !in setOf(
                    RepairPartCondition.REPAIRED, RepairPartCondition.REPLACED
                )) return reject(session, "Restore the internal module before closing the cover.")
                accept(session, session.copy(panelOpen = false, step = RepairProcedureStep.SECURE_SCREWS),
                    "Cover closed. Reinstall the four retaining screws.")
            }
            RepairProcedureStep.SECURE_SCREWS -> {
                if (!hotspot.isScrew || hotspot !in session.loosenedScrews) {
                    return reject(session, "Select a removed panel fastener to reinstall.")
                }
                val remaining = session.loosenedScrews - hotspot
                val next = if (remaining.isEmpty()) RepairProcedureStep.VERIFY else session.step
                accept(session, session.copy(loosenedScrews = remaining, step = next),
                    if (next == RepairProcedureStep.VERIFY) "Cover secured. Run final diagnostics."
                    else "Screw secured (${4 - remaining.size}/4).")
            }
            RepairProcedureStep.VERIFY -> {
                if (hotspot != RepairHotspot.DIAGNOSTIC_PORT) {
                    return reject(session, "Scan the service diagnostic port.")
                }
                if (session.partCondition !in setOf(RepairPartCondition.REPAIRED, RepairPartCondition.REPLACED) ||
                    (session.isInternalRepair && (session.panelOpen || session.loosenedScrews.isNotEmpty())) ||
                    (session.damage == EquipmentDamage.SOLAR_ARRAY_DAMAGE && session.scannedSolarPanels != solar) ||
                    (session.damage == EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE && !session.inspectedDish) ||
                    (session.isInternalRepair && !session.inspectedInternalModule)
                ) return reject(session, "Mechanical closure or subsystem diagnosis is incomplete.")
                accept(session, session.copy(step = RepairProcedureStep.COMPLETE),
                    "Diagnostics PASSED: ${componentLabel(session.damage)} restored.", completed = true)
            }
            RepairProcedureStep.CHOOSE_METHOD, RepairProcedureStep.COMPLETE ->
                reject(session, "No physical interaction is available for this step.")
        }
    }

    private fun reject(session: RoboticRepairSession, reason: String) =
        RepairInteractionResult(session.copy(feedback = reason), succeeded = false)

    private fun accept(before: RoboticRepairSession, after: RoboticRepairSession,
                       message: String, completed: Boolean = false) = RepairInteractionResult(
        session = after.copy(feedback = message),
        succeeded = true, completed = completed, stepChanged = before.step != after.step
    )
}

/**
 * Maps a repair target to the face that SERVICER-01 must grip to reach it.
 * This policy is shared by the panel and ViewModel to prevent UI bypass.
 */
object RepairServiceAccessPolicy {
    fun requiredFace(session: RoboticRepairSession, hotspot: RepairHotspot? = null): SatelliteServiceFace = when (
        hotspot ?: when (session.step) {
            RepairProcedureStep.CHOOSE_METHOD, RepairProcedureStep.REMOVE_PART,
            RepairProcedureStep.INSTALL_REPLACEMENT, RepairProcedureStep.REPAIR_IN_PLACE -> session.target
            else -> RepairHotspot.DIAGNOSTIC_PORT
        }
    ) {
        RepairHotspot.SOLAR_PANEL_A, RepairHotspot.SOLAR_MOUNT_A -> SatelliteServiceFace.PORT
        RepairHotspot.SOLAR_PANEL_B, RepairHotspot.SOLAR_MOUNT_B -> SatelliteServiceFace.STARBOARD
        RepairHotspot.COMMUNICATIONS_DISH, RepairHotspot.DISH_MOUNT -> SatelliteServiceFace.DORSAL
        else -> SatelliteServiceFace.SERVICE
    }

    fun canAccess(session: RoboticRepairSession, face: SatelliteServiceFace, hotspot: RepairHotspot? = null) =
        face == requiredFace(session, hotspot)

    fun label(hotspot: RepairHotspot): String = when (hotspot) {
        RepairHotspot.SOLAR_PANEL_A -> "Scan Solar A (Port)"
        RepairHotspot.SOLAR_PANEL_B -> "Scan Solar B (Starboard)"
        RepairHotspot.SOLAR_MOUNT_A, RepairHotspot.SOLAR_MOUNT_B -> "Empty solar mount"
        RepairHotspot.COMMUNICATIONS_DISH -> "Communications dish (Top)"
        RepairHotspot.DISH_MOUNT -> "Dish attachment ring"
        RepairHotspot.PAYLOAD_SENSOR, RepairHotspot.PAYLOAD_SOCKET -> "Payload sensor"
        RepairHotspot.THERMAL_CONTROLLER, RepairHotspot.THERMAL_SOCKET -> "Thermal controller"
        RepairHotspot.DIAGNOSTIC_PORT -> "Diagnostic port (Service)"
        RepairHotspot.ACCESS_PANEL -> "Service panel"
        RepairHotspot.COMPONENT, RepairHotspot.COMPONENT_SOCKET -> "Component"
        else -> "Fastener ${hotspot.name.removePrefix("SCREW_")}"
    }
}

/** Legacy flat-layout touch coordinates kept for older tests/screens. */
object SatelliteRepairGeometry {
    const val WIDTH = 400f
    const val HEIGHT = 300f
    val screwLocations = mapOf(
        RepairHotspot.SCREW_1 to Pair(151f, 104f),
        RepairHotspot.SCREW_2 to Pair(249f, 104f),
        RepairHotspot.SCREW_3 to Pair(151f, 222f),
        RepairHotspot.SCREW_4 to Pair(249f, 222f)
    )

    fun hitTest(x: Float, y: Float, session: RoboticRepairSession): RepairHotspot? {
        for ((screw, position) in screwLocations) {
            if (kotlin.math.hypot(x - position.first, y - position.second) <= 20f) return screw
        }
        if (session.panelOpen && x in 171f..229f && y in 129f..200f) return session.target
        if (x in 141f..259f && y in 94f..233f) return RepairHotspot.ACCESS_PANEL
        if (kotlin.math.hypot(x - 343f, y - 43f) <= 27f) return RepairHotspot.DIAGNOSTIC_PORT
        return null
    }
}
