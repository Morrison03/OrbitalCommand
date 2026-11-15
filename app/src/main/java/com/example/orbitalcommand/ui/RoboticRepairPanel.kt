package com.example.orbitalcommand.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.orbitalcommand.model.*
import com.example.orbitalcommand.simulation.ServicingVehicleNavigation
import com.example.orbitalcommand.ui.theme.OrbitalColors
import com.example.orbitalcommand.viewmodel.OrbitalViewModel

/** Routes repair controls through the existing mission ViewModel. */
@Composable
fun RoboticRepairRoute(
    state: OrbitalUiState,
    viewModel: OrbitalViewModel = viewModel()
) {
    RoboticRepairPanel(
        state = state,
        onStart = viewModel::startRoboticRepair,
        onSelectTool = viewModel::selectRepairTool,
        onChooseMethod = viewModel::chooseRepairMethod,
        onInteract = viewModel::interactWithRepairModel,
        onRestart = viewModel::restartRoboticRepair
    )
}

/**
 * Mission-wide physical attachment and component-specific service console.
 *
 * Unlike the original generic-module panel, the available actions are
 * computed from the work-order's physical component and docking face.
 * The separate SceneView POV and the interactive Canvas remain unchanged
 * in size and layout. An existing grip survives telemetry outages.
 */
@Composable
fun RoboticRepairPanel(
    state: OrbitalUiState,
    onStart: () -> Unit,
    onSelectTool: (RepairTool) -> Unit,
    onChooseMethod: (RepairServiceMethod) -> Unit,
    onInteract: (RepairHotspot) -> Unit,
    onRestart: () -> Unit
) {
    val plan = state.preparedMission
    val order = state.repairOrders.firstOrNull { it.id == plan?.workOrderId }
    val target = state.satellites.firstOrNull { it.definition.id == plan?.targetSatelliteId }
    val session = state.repairSession
    val vehicle = state.servicingVehicle
    val attachment by ServicerAttachmentRegistry.current.collectAsState()

    // Preparing a mission does not overwrite a confirmed grip on that mission.
    LaunchedEffect(plan?.workOrderId, plan?.targetSatelliteId, vehicle.phase) {
        if (plan != null) {
            ServicerAttachmentRegistry.prepareMission(plan.workOrderId, plan.targetSatelliteId)
            if (vehicle.phase == ServicingVehiclePhase.RETURNING ||
                vehicle.phase == ServicingVehiclePhase.DOCKED) {
                ServicerAttachmentRegistry.release(plan.workOrderId)
            }
        } else if (vehicle.phase == ServicingVehiclePhase.DOCKED) {
            ServicerAttachmentRegistry.clear()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OrbitalColors.Panel)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("SERVICER-01 • SATELLITE SERVICE VIEW", color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Subsystem-specific robotic maintenance • interactive model + CAM-01",
                color = OrbitalColors.Accent, fontSize = 12.sp)

            if (plan == null || order == null || target == null) {
                Text("Prepare a queued mission and dispatch SERVICER-01 before docking.",
                    color = OrbitalColors.Muted, fontSize = 12.sp)
            } else {
                val dockingReady = vehicle.phase == ServicingVehiclePhase.FORMATION_FOLLOWING &&
                    vehicle.targetSatelliteId == target.definition.id &&
                    vehicle.workOrderId == order.id &&
                    ServicingVehicleNavigation.hasCurrentTargetTelemetry(target, state.elapsedSeconds)
                val policyReady = RepairCompletionPolicy.evaluate(state, order) ==
                    RepairCompletionStatus.READY_IN_FORMATION
                val docking = attachment?.takeIf {
                    it.workOrderId == plan.workOrderId &&
                        it.satelliteId == plan.targetSatelliteId
                }?.docking ?: ServicerDockingState(plan.workOrderId)
                val completed = order.status == RepairStatus.COMPLETED
                val canCommand = policyReady && docking.grippingRail &&
                    docking.phase == ServicerDockPhase.GRIPPED && !docking.choosingFace && !completed
                val preview = session ?: if (order.status == RepairStatus.QUEUED) {
                    remember(order.id) { RoboticRepairSimulator.start(order) }
                } else null

                Text("${target.definition.id} • ${target.definition.name}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Work order #${order.id} • ${RoboticRepairSimulator.componentLabel(order.damage)}",
                    color = OrbitalColors.Muted, fontSize = 12.sp)

                if (docking.grippingRail && !dockingReady) {
                    Text("COMMUNICATION LOSS • Rail grip retained; commands paused. " +
                        "The attached robot continues with the satellite's predicted orbit.",
                        color = OrbitalColors.Warning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Keep the 3D views mounted during signal loss if physically attached.
                val showStage = vehicle.phase == ServicingVehiclePhase.FORMATION_FOLLOWING ||
                    (vehicle.phase == ServicingVehiclePhase.HOLDING && docking.grippingRail)
                if (preview != null && showStage) {
                    ServicerDockingStage(
                        satelliteId = target.definition.id,
                        session = preview,
                        docking = docking,
                        navigationReady = dockingReady,
                        repairEnabled = canCommand && session != null,
                        onDockingChange = { ServicerAttachmentRegistry.update(it) },
                        onRepairHotspot = { hotspot ->
                            if (session != null && canCommand &&
                                RepairServiceAccessPolicy.canAccess(session, docking.activeFace, hotspot)) {
                                onInteract(hotspot)
                            }
                        }
                    )
                } else {
                    Text("The service model appears after formation is established.",
                        color = OrbitalColors.Warning, fontSize = 12.sp)
                }

                when {
                    completed || session?.isComplete == true -> {
                        Text("REPAIR VERIFIED • Return SERVICER-01 to OS-001 when ready.",
                            color = OrbitalColors.Healthy, fontWeight = FontWeight.Bold)
                    }
                    session == null -> {
                        Button(onClick = onStart, enabled = canCommand,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Begin ${RoboticRepairSimulator.componentLabel(order.damage)} Repair")
                        }
                        Text("Dock to the satellite rail to begin. Move to Port/Starboard for " +
                            "solar wings, Top for the dish, or Service for internal modules.",
                            color = OrbitalColors.Muted, fontSize = 12.sp)
                    }
                    else -> {
                        Text("TASK: ${session.step.displayName.uppercase()}",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(session.step.instruction, color = OrbitalColors.Muted, fontSize = 12.sp)

                        val nextTargets = Satellite3DGeometry.activeHotspots(session)
                        val faceHint = if (session.step == RepairProcedureStep.SCAN_SOLAR_PANELS) {
                            "Scan the wings one at a time; use Move to reach each side."
                        } else {
                            "Required face: ${RepairServiceAccessPolicy.requiredFace(session).displayName}. " +
                                "Use Move if the camera is on another face."
                        }
                        Text(faceHint, color = OrbitalColors.Accent, fontSize = 12.sp)

                        if (session.scannedSolarPanels.isNotEmpty()) {
                            Text("Solar inspections: " + session.scannedSolarPanels.joinToString { 
                                if (it == RepairHotspot.SOLAR_PANEL_A) "A" else "B" },
                                color = OrbitalColors.Muted, fontSize = 11.sp)
                        }

                        if (!canCommand) {
                            Text("SERVICE COMMANDS LOCKED • Confirm docking, formation and telemetry.",
                                color = OrbitalColors.Warning, fontSize = 12.sp)
                        }

                        if (session.step == RepairProcedureStep.CHOOSE_METHOD) {
                            val correctFace = RepairServiceAccessPolicy.canAccess(session, docking.activeFace)
                            RepairServiceMethod.entries.forEach { method ->
                                Button(onClick = { onChooseMethod(method) },
                                    enabled = canCommand && correctFace,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(method.displayName)
                                }
                            }
                        } else {
                            Text("SELECT SERVICE TOOL", color = Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RepairTool.entries.filter { it != RepairTool.NONE }.forEach { tool ->
                                    FilterChip(
                                        selected = session.tool == tool,
                                        enabled = canCommand,
                                        onClick = { onSelectTool(tool) },
                                        label = { Text(tool.displayName) }
                                    )
                                }
                            }
                            Text("Required: ${RoboticRepairSimulator.requiredTool(session.step).displayName}",
                                color = OrbitalColors.Accent, fontSize = 12.sp)
                            RepairHotspotButtons(
                                session = session,
                                active = nextTargets,
                                enabled = canCommand,
                                face = docking.activeFace,
                                onInteract = onInteract
                            )
                        }
                        OutlinedButton(onClick = onRestart, enabled = canCommand,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Restart Repair Procedure")
                        }
                        Text(session.feedback, color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            HorizontalDivider(color = OrbitalColors.PanelRaised)
            Text("Scripted servicing simulation; not flight-qualified robotic control.",
                color = OrbitalColors.Muted, fontSize = 11.sp)
        }
    }
}

/** Accessible alternatives to touch targets on a rotated satellite. */
@Composable
private fun RepairHotspotButtons(
    session: RoboticRepairSession,
    active: List<RepairHotspot>,
    enabled: Boolean,
    face: SatelliteServiceFace,
    onInteract: (RepairHotspot) -> Unit
) {
    if (active.isEmpty()) return
    val screws = session.step == RepairProcedureStep.UNSCREW ||
        session.step == RepairProcedureStep.SECURE_SCREWS
    if (screws) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            active.forEach { hotspot ->
                OutlinedButton(
                    onClick = { onInteract(hotspot) },
                    enabled = enabled && RepairServiceAccessPolicy.canAccess(session, face, hotspot),
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)
                ) {
                    Text(hotspot.name.removePrefix("SCREW_"))
                }
            }
        }
    } else {
        active.forEach { hotspot ->
            OutlinedButton(
                onClick = { onInteract(hotspot) },
                enabled = enabled && RepairServiceAccessPolicy.canAccess(session, face, hotspot),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(RepairServiceAccessPolicy.label(hotspot))
            }
        }
    }
}
