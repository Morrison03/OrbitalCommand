
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.RepairCompletionPolicy
import com.example.orbitalcommand.model.RepairCompletionStatus
import com.example.orbitalcommand.model.RepairHotspot
import com.example.orbitalcommand.model.RepairProcedureStep
import com.example.orbitalcommand.model.RepairServiceMethod
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairTool
import com.example.orbitalcommand.model.RoboticRepairSession
import com.example.orbitalcommand.model.RoboticRepairSimulator
import com.example.orbitalcommand.model.Satellite3DGeometry
import com.example.orbitalcommand.model.SatelliteCamera
import com.example.orbitalcommand.ui.theme.OrbitalColors
import com.example.orbitalcommand.viewmodel.OrbitalViewModel

/**
 * Connects the interactive servicing console
 * to the shared mission ViewModel.
 */
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
 * Operator-facing animated spacecraft
 * servicing console.
 *
 * The camera changes only presentation state.
 * All repair actions are validated by the
 * ViewModel against mission and procedure state.
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

    val order = state.repairOrders.firstOrNull {
        it.id == plan?.workOrderId
    }

    val target = state.satellites.firstOrNull {
        it.definition.id == plan?.targetSatelliteId
    }

    val session = state.repairSession

    val completed =
        order?.status == RepairStatus.COMPLETED

    val canOperate =
        order != null &&
                target != null &&
                RepairCompletionPolicy.evaluate(
                    state,
                    order
                ) == RepairCompletionStatus.READY_IN_FORMATION

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = OrbitalColors.Panel
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Text(
                text = "ANIMATED 3D SATELLITE SERVICING",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Drag to rotate • Zoom to inspect • " +
                        "Tap highlighted parts",
                color = OrbitalColors.Accent,
                fontSize = 12.sp
            )

            if (
                plan == null ||
                order == null ||
                target == null
            ) {

                Text(
                    text = "Prepare a work order, dispatch " +
                            "SERVICER-01, and establish formation " +
                            "to access the satellite's service model.",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )

            } else {

                Text(
                    text = "${target.definition.id} • " +
                            target.definition.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Work order #${order.id} • " +
                            RoboticRepairSimulator.componentLabel(
                                order.damage
                            ),
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )

                if (session == null) {

                    Text(
                        text = if (canOperate) {
                            "Formation verified. The satellite " +
                                    "is ready for service."
                        } else {
                            "Service unavailable: restore current " +
                                    "telemetry and formation."
                        },
                        color = if (canOperate)
                            OrbitalColors.Healthy
                        else
                            OrbitalColors.Warning,
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = onStart,
                        enabled = canOperate,
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text("Open 3D Satellite Service Model")
                    }

                } else {

                    var camera by remember(
                        session.satelliteId
                    ) {
                        mutableStateOf(SatelliteCamera())
                    }

                    Text(
                        text = session.step.displayName.uppercase(),
                        color = if (session.isComplete)
                            OrbitalColors.Healthy
                        else
                            Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = session.step.instruction,
                        color = OrbitalColors.Muted,
                        fontSize = 12.sp
                    )

                    Satellite3DScene(
                        satelliteId = session.satelliteId,
                        session = session,
                        enabled = canOperate && !completed,
                        camera = camera,
                        onCameraChange = {
                            camera = it
                        },
                        onHotspot = onInteract
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedButton(
                            onClick = {
                                camera = camera.zoomed(-0.1f)
                            },
                            enabled = camera.zoom > 0.70f,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = 4.dp
                            )
                        ) {
                            Text("Zoom −", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                camera = SatelliteCamera()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = 4.dp
                            )
                        ) {
                            Text("Reset View", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                camera = camera.zoomed(0.1f)
                            },
                            enabled = camera.zoom < 1.25f,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = 4.dp
                            )
                        ) {
                            Text("Zoom +", fontSize = 12.sp)
                        }
                    }

                    Text(
                        text = "The green target rings follow each " +
                                "part as the model rotates. The cover, " +
                                "fasteners, and modules animate when " +
                                "their state changes.",
                        color = OrbitalColors.Muted,
                        fontSize = 11.sp
                    )

                    if (!canOperate && !completed) {

                        Text(
                            text = "SERVICE SUSPENDED — Progress is " +
                                    "preserved. Restore communications " +
                                    "and reestablish formation.",
                            color = OrbitalColors.Warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (completed || session.isComplete) {

                        Text(
                            text = "REPAIR VERIFIED",
                            color = OrbitalColors.Healthy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Text(
                            text = "The restored module is secured. " +
                                    "Return SERVICER-01 to OS-001 " +
                                    "when ready.",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                    } else if (
                        session.step ==
                        RepairProcedureStep.CHOOSE_METHOD
                    ) {

                        Text(
                            text = "SELECT REPAIR METHOD",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        RepairServiceMethod.entries.forEach {
                                method ->

                            Button(
                                onClick = {
                                    onChooseMethod(method)
                                },
                                enabled = canOperate,
                                modifier = Modifier.fillMaxWidth()
                            ) {

                                Text(method.displayName)
                            }
                        }

                    } else {

                        Text(
                            text = "SELECT SERVICE TOOL",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                ),
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {

                            RepairTool.entries
                                .filter {
                                    it != RepairTool.NONE
                                }
                                .forEach { tool ->

                                    FilterChip(
                                        selected =
                                            session.tool == tool,
                                        enabled = canOperate,
                                        onClick = {
                                            onSelectTool(tool)
                                        },
                                        label = {
                                            Text(tool.displayName)
                                        }
                                    )
                                }
                        }

                        Text(
                            text = "Required: " +
                                    RoboticRepairSimulator
                                        .requiredTool(
                                            session.step
                                        ).displayName,
                            color = OrbitalColors.Accent,
                            fontSize = 12.sp
                        )

                        RepairPartControls(
                            session = session,
                            enabled = canOperate,
                            onInteract = onInteract
                        )
                    }

                    if (
                        !completed &&
                        !session.isComplete
                    ) {

                        OutlinedButton(
                            onClick = onRestart,
                            enabled = canOperate,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text("Restart Repair Procedure")
                        }
                    }

                    Text(
                        text = session.feedback,
                        color = if (completed)
                            OrbitalColors.Healthy
                        else
                            Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            Text(
                text = "This is a scripted procedural 3D " +
                        "illustration of satellite maintenance, " +
                        "not a CAD or flight-qualified simulator.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Provides alternative accessible controls
 * for currently interactive components.
 *
 * These also provide a straightforward
 * interface for future gamepad navigation.
 */
@Composable
private fun RepairPartControls(
    session: RoboticRepairSession,
    enabled: Boolean,
    onInteract: (RepairHotspot) -> Unit
) {

    val hotspots =
        Satellite3DGeometry.activeHotspots(session)

    if (hotspots.isEmpty()) {
        return
    }

    val fastenerStep =
        session.step == RepairProcedureStep.UNSCREW ||
                session.step == RepairProcedureStep.SECURE_SCREWS

    if (fastenerStep) {

        Text(
            text = "Tap the highlighted screws or " +
                    "use these numbered controls:",
            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            hotspots.forEach { screw ->

                OutlinedButton(
                    onClick = {
                        onInteract(screw)
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {

                    Text(
                        when (screw) {

                            RepairHotspot.SCREW_1 -> "1"
                            RepairHotspot.SCREW_2 -> "2"
                            RepairHotspot.SCREW_3 -> "3"
                            RepairHotspot.SCREW_4 -> "4"

                            else -> "•"
                        }
                    )
                }
            }
        }

    } else {

        OutlinedButton(
            onClick = {
                onInteract(hotspots.first())
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                "Interact: ${session.step.displayName}"
            )
        }
    }
}
