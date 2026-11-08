
package com.example.orbitalcommand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.RepairArmDirection
import com.example.orbitalcommand.model.RepairCompletionPolicy
import com.example.orbitalcommand.model.RepairCompletionStatus
import com.example.orbitalcommand.model.RepairProcedureStep
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairTool
import com.example.orbitalcommand.model.RoboticRepairSession
import com.example.orbitalcommand.model.RoboticRepairSimulator
import com.example.orbitalcommand.ui.theme.OrbitalColors
import com.example.orbitalcommand.viewmodel.OrbitalViewModel

/**
 * Connects the existing mission ViewModel to the
 * interactive robotic repair console.
 *
 * The ViewModel is scoped to the same owner as
 * OrbitalRoute, so no second simulation is created.
 */
@Composable
fun RoboticRepairRoute(
    state: OrbitalUiState,
    viewModel: OrbitalViewModel = viewModel()
) {

    RoboticRepairPanel(
        state = state,
        onStart = viewModel::startRoboticRepair,
        onMove = viewModel::moveRepairArm,
        onSelectTool = viewModel::selectRepairTool,
        onExecute = viewModel::executeRepairStep,
        onRestart = viewModel::restartRoboticRepair
    )
}

/**
 * Displays the operator-controlled servicing workspace.
 *
 * Arm commands and task execution are enabled only
 * while the vehicle has verified formation with
 * its assigned repair satellite.
 */
@Composable
fun RoboticRepairPanel(
    state: OrbitalUiState,
    onStart: () -> Unit,
    onMove: (RepairArmDirection) -> Unit,
    onSelectTool: (RepairTool) -> Unit,
    onExecute: () -> Unit,
    onRestart: () -> Unit
) {

    val plan = state.preparedMission
    val session = state.repairSession

    val order = state.repairOrders.firstOrNull {
        it.id == plan?.workOrderId
    }

    val canService = order != null &&
            RepairCompletionPolicy.evaluate(
                state = state,
                order = order
            ) == RepairCompletionStatus.READY_IN_FORMATION

    val completed =
        order?.status == RepairStatus.COMPLETED

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
                text = "ROBOTIC REPAIR CONSOLE",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "SERVICER-01 • Touchscreen arm control",
                color = OrbitalColors.Accent,
                fontSize = 12.sp
            )

            if (plan == null || order == null) {

                Text(
                    text = "Prepare a queued repair mission and " +
                            "establish orbital formation to begin " +
                            "an interactive servicing procedure.",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )

            } else {

                Text(
                    text = "WORK ORDER #${order.id} • " +
                            order.damage.displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (completed) {

                    Text(
                        text = "REPAIR COMPLETE",
                        color = OrbitalColors.Healthy,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "All servicing checks passed. " +
                                "Equipment damage is cleared. " +
                                "SERVICER-01 remains deployed until " +
                                "you command Return to Servicing Station.",
                        color = Color.White,
                        fontSize = 12.sp
                    )

                } else if (session == null) {

                    if (canService) {

                        Text(
                            text = "Formation verified. Begin the " +
                                    "robotic repair procedure to " +
                                    "inspect and service the satellite.",
                            color = OrbitalColors.Healthy,
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Begin Interactive Repair")
                        }

                    } else {

                        Text(
                            text = "REPAIR CONSOLE LOCKED",
                            color = OrbitalColors.Warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "The servicing vehicle must be " +
                                    "following the assigned target with " +
                                    "current telemetry. Dispatch or " +
                                    "resume guidance in Orbital " +
                                    "Servicing Operations.",
                            color = OrbitalColors.Muted,
                            fontSize = 12.sp
                        )
                    }

                } else {

                    if (!canService) {

                        Text(
                            text = "CONTROLS SUSPENDED",
                            color = OrbitalColors.Warning,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Repair progress is preserved, " +
                                    "but movement and servicing commands " +
                                    "require verified formation and " +
                                    "current target telemetry.",
                            color = OrbitalColors.Warning,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = "TASK ${session.step.ordinal + 1} OF 4: " +
                                session.step.displayName.uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    LinearProgressIndicator(
                        progress = {
                            session.step.ordinal / 4f
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = OrbitalColors.Accent,
                        trackColor = OrbitalColors.PanelRaised
                    )

                    Text(
                        text = session.step.instruction,
                        color = OrbitalColors.Muted,
                        fontSize = 12.sp
                    )

                    RepairWorkspace(
                        session = session
                    )

                    val requiredTool =
                        RoboticRepairSimulator.requiredTool(
                            step = session.step,
                            damage = session.damage
                        )

                    Text(
                        text = "Required tool: " +
                                requiredTool.displayName,
                        color = OrbitalColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "SELECT TOOL",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        RepairTool.entries
                            .filter { it != RepairTool.NONE }
                            .forEach { tool ->

                                FilterChip(
                                    selected = session.tool == tool,
                                    enabled = canService,
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
                        text = "ARM MOVEMENT",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    RepairDirectionControls(
                        enabled = canService,
                        onMove = onMove
                    )

                    Text(
                        text = session.feedback,
                        color = if (
                            session.feedback.startsWith("Select") ||
                            session.feedback.startsWith("Move")
                        ) {
                            Color(0xFFFBBF24)
                        } else {
                            OrbitalColors.Muted
                        },
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = onExecute,
                        enabled = canService,
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "Execute ${session.step.displayName}"
                        )
                    }

                    OutlinedButton(
                        onClick = onRestart,
                        enabled = !session.isComplete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restart Repair Procedure")
                    }
                }
            }

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            Text(
                text = "This workspace simulates operator control " +
                        "of a service arm. Positions, tools, and " +
                        "procedures are illustrative and are not " +
                        "physical spacecraft servicing instructions.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Draws the current servicing task zone
 * and the position of the robotic arm.
 *
 * The highlighted ellipse represents the normalized
 * acceptance region used by RoboticRepairSimulator.
 */
@Composable
private fun RepairWorkspace(
    session: RoboticRepairSession
) {

    val zone = RoboticRepairSimulator.workZone(session.step)
    val radius = RoboticRepairSimulator.WORK_ZONE_RADIUS

    val aligned = session.armPosition.distanceTo(zone) <= radius

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(235.dp)
            .background(
                OrbitalColors.PanelRaised,
                RoundedCornerShape(12.dp)
            )
    ) {

        val gridColor =
            Color(0xFF64748B).copy(alpha = 0.22f)

        // Provide a reference grid for arm positioning.
        for (index in 1..9) {

            val fraction = index / 10f

            drawLine(
                color = gridColor,
                start = Offset(
                    x = fraction * size.width,
                    y = 0f
                ),
                end = Offset(
                    x = fraction * size.width,
                    y = size.height
                )
            )

            drawLine(
                color = gridColor,
                start = Offset(
                    x = 0f,
                    y = fraction * size.height
                ),
                end = Offset(
                    x = size.width,
                    y = fraction * size.height
                )
            )
        }

        val targetPoint = Offset(
            x = zone.x * size.width,
            y = zone.y * size.height
        )

        val armPoint = Offset(
            x = session.armPosition.x * size.width,
            y = session.armPosition.y * size.height
        )

        val zoneColor = if (aligned) {
            OrbitalColors.Healthy
        } else {
            Color(0xFFFBBF24)
        }

        // Draw the acceptable servicing work zone.
        drawOval(
            color = zoneColor.copy(alpha = 0.20f),
            topLeft = Offset(
                x = (zone.x - radius) * size.width,
                y = (zone.y - radius) * size.height
            ),
            size = Size(
                width = radius * 2f * size.width,
                height = radius * 2f * size.height
            )
        )

        drawOval(
            color = zoneColor,
            topLeft = Offset(
                x = (zone.x - radius) * size.width,
                y = (zone.y - radius) * size.height
            ),
            size = Size(
                width = radius * 2f * size.width,
                height = radius * 2f * size.height
            ),
            style = Stroke(width = 2.dp.toPx())
        )

        drawCircle(
            color = zoneColor,
            radius = 4.dp.toPx(),
            center = targetPoint
        )

        // Show the manipulator's position.
        drawLine(
            color = OrbitalColors.Accent.copy(alpha = 0.50f),
            start = Offset(
                x = size.width * 0.5f,
                y = size.height * 0.5f
            ),
            end = armPoint,
            strokeWidth = 2.dp.toPx()
        )

        drawCircle(
            color = OrbitalColors.Accent.copy(alpha = 0.20f),
            radius = 17.dp.toPx(),
            center = armPoint
        )

        drawCircle(
            color = OrbitalColors.Accent,
            radius = 9.dp.toPx(),
            center = armPoint
        )

        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = armPoint
        )
    }

    Text(
        text = if (aligned)
            "ARM ALIGNED WITH SERVICE ZONE"
        else
            "MOVE ARM TO HIGHLIGHTED ZONE",
        color = if (aligned)
            OrbitalColors.Healthy
        else
            Color(0xFFFBBF24),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

/**
 * Provides touch-friendly directional controls
 * for positioning the simulated robotic arm.
 */
@Composable
private fun RepairDirectionControls(
    enabled: Boolean,
    onMove: (RepairArmDirection) -> Unit
) {

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        DirectionButton(
            label = "↑",
            enabled = enabled,
            direction = RepairArmDirection.UP,
            onMove = onMove
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            DirectionButton(
                label = "←",
                enabled = enabled,
                direction = RepairArmDirection.LEFT,
                onMove = onMove
            )

            Spacer(modifier = Modifier.width(56.dp))

            DirectionButton(
                label = "→",
                enabled = enabled,
                direction = RepairArmDirection.RIGHT,
                onMove = onMove
            )
        }

        DirectionButton(
            label = "↓",
            enabled = enabled,
            direction = RepairArmDirection.DOWN,
            onMove = onMove
        )
    }
}

/**
 * Displays one arm-movement command.
 */
@Composable
private fun DirectionButton(
    label: String,
    enabled: Boolean,
    direction: RepairArmDirection,
    onMove: (RepairArmDirection) -> Unit
) {

    OutlinedButton(
        onClick = {
            onMove(direction)
        },
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {

        Text(
            text = label,
            color = if (enabled)
                Color.White
            else
                OrbitalColors.Muted,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


