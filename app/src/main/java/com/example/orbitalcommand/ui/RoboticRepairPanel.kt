
package com.example.orbitalcommand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.orbitalcommand.model.*
import com.example.orbitalcommand.ui.theme.OrbitalColors
import com.example.orbitalcommand.viewmodel.OrbitalViewModel

/**
 * Binds the service console to the mission-control ViewModel.
 *
 * Uses the same ViewModelStoreOwner as the dashboard.
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
 * Displays a satellite-specific interactive repair model.
 *
 * Fasteners, covers, components, mounting sockets, and
 * the diagnostics connector respond to user interaction.
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

    val complete =
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
                .padding(14.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "SATELLITE SERVICE BAY",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Text(
                text = "Interactive satellite cutaway • " +
                        "Touch panels and components",
                color = OrbitalColors.Muted,
                fontSize = 12.sp
            )

            if (
                plan == null ||
                order == null ||
                target == null
            ) {

                Text(
                    text = "Prepare a repair mission and " +
                            "establish formation to open the " +
                            "satellite service model.",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )

            } else {

                Text(
                    text = "${target.definition.id} — " +
                            target.definition.name,
                    color = OrbitalColors.Accent,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Work order #${order.id} • " +
                            RoboticRepairSimulator.componentLabel(
                                order.damage
                            ),
                    color = Color.White,
                    fontSize = 12.sp
                )

                if (session == null) {

                    Text(
                        text = if (canOperate) {
                            "Formation confirmed. Open the " +
                                    "service bay to begin."
                        } else {
                            "Service bay unavailable: establish " +
                                    "verified formation and current telemetry."
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
                        Text("Open Interactive Satellite Model")
                    }

                } else {

                    Text(
                        text = session.step.displayName.uppercase() +
                                " • " +
                                session.partCondition.name.replace(
                                    '_',
                                    ' '
                                ),
                        color = if (session.isComplete)
                            OrbitalColors.Healthy
                        else
                            Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Text(
                        text = session.step.instruction,
                        color = OrbitalColors.Muted,
                        fontSize = 12.sp
                    )

                    SatelliteServiceCanvas(
                        satelliteId = session.satelliteId,
                        session = session,
                        enabled = canOperate && !complete,
                        onHotspot = onInteract
                    )

                    if (!canOperate && !complete) {

                        Text(
                            text = "CONTROLS SUSPENDED — " +
                                    "Procedure progress is saved. " +
                                    "Restore communications and formation " +
                                    "to continue.",
                            color = OrbitalColors.Warning,
                            fontSize = 12.sp
                        )
                    }

                    if (complete || session.isComplete) {

                        Text(
                            text = "REPAIR VERIFIED — " +
                                    "Component restored. " +
                                    "SERVICER-01 may now return to OS-001.",
                            color = OrbitalColors.Healthy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                    } else {

                        Text(
                            text = "TOOL SELECTION",
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

                        val required =
                            RoboticRepairSimulator.requiredTool(
                                session.step
                            )

                        if (required != RepairTool.NONE) {

                            Text(
                                text = "Required: " +
                                        required.displayName,
                                color = OrbitalColors.Accent,
                                fontSize = 12.sp
                            )
                        }

                        if (
                            session.step ==
                            RepairProcedureStep.CHOOSE_METHOD
                        ) {

                            Text(
                                text = "INSPECTION REPORT",
                                color = OrbitalColors.Healthy,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            Text(
                                text = "Damage confirmed. Choose " +
                                        "whether to restore the existing " +
                                        "component or replace it.",
                                color = Color.White,
                                fontSize = 12.sp
                            )

                            RepairServiceMethod.entries.forEach {
                                    method ->

                                Button(
                                    onClick = {
                                        onChooseMethod(method)
                                    },
                                    enabled = canOperate,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text(method.displayName)
                                }
                            }

                        } else {

                            AccessibleHotspotControls(
                                session = session,
                                enabled = canOperate,
                                onHotspot = onInteract
                            )
                        }

                        OutlinedButton(
                            onClick = onRestart,
                            enabled = canOperate,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Restart Procedure")
                        }
                    }

                    Text(
                        text = session.feedback,
                        color = if (session.isComplete)
                            OrbitalColors.Healthy
                        else
                            OrbitalColors.Muted,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            Text(
                text = "This is an interactive 2D technical " +
                        "mock-up, not a CAD model or a " +
                        "flight-qualified maintenance procedure.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Provides alternative buttons for each physical touch
 * target, including four independent screw controls.
 *
 * These controls also make the model easier to use
 * with accessibility services and future gamepad input.
 */
@Composable
private fun AccessibleHotspotControls(
    session: RoboticRepairSession,
    enabled: Boolean,
    onHotspot: (RepairHotspot) -> Unit
) {

    val screwStep =
        session.step == RepairProcedureStep.UNSCREW ||
                session.step == RepairProcedureStep.SECURE_SCREWS

    if (screwStep) {

        Text(
            text = "Tap each screw on the model, or use " +
                    "the fastener controls:",
            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            listOf(
                RepairHotspot.SCREW_1,
                RepairHotspot.SCREW_2,
                RepairHotspot.SCREW_3,
                RepairHotspot.SCREW_4
            ).forEachIndexed { index, screw ->

                val required = if (
                    session.step == RepairProcedureStep.UNSCREW
                ) {
                    screw !in session.loosenedScrews
                } else {
                    screw in session.loosenedScrews
                }

                OutlinedButton(
                    onClick = {
                        onHotspot(screw)
                    },
                    enabled = enabled && required,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("${index + 1}")
                }
            }
        }

    } else {

        val spot = when (session.step) {

            RepairProcedureStep.LIFT_PANEL,
            RepairProcedureStep.CLOSE_PANEL ->
                RepairHotspot.ACCESS_PANEL

            RepairProcedureStep.EXAMINE,
            RepairProcedureStep.REMOVE_PART,
            RepairProcedureStep.REPAIR_IN_PLACE ->
                RepairHotspot.COMPONENT

            RepairProcedureStep.INSTALL_REPLACEMENT ->
                RepairHotspot.COMPONENT_SOCKET

            RepairProcedureStep.VERIFY ->
                RepairHotspot.DIAGNOSTIC_PORT

            else -> null
        }

        if (spot != null) {

            OutlinedButton(
                onClick = {
                    onHotspot(spot)
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    "Use selected tool on " +
                            session.step.displayName.lowercase()
                )
            }
        }
    }
}

/**
 * Displays a spacecraft's interactive cutaway.
 *
 * The same 400x300 coordinate system is used for
 * touch recognition and rendering, keeping the
 * hotspots aligned with their drawn components.
 */
@Composable
private fun SatelliteServiceCanvas(
    satelliteId: String,
    session: RoboticRepairSession,
    enabled: Boolean,
    onHotspot: (RepairHotspot) -> Unit
) {

    val latestCallback by rememberUpdatedState(onHotspot)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(
                Color(0xFF091321),
                RoundedCornerShape(12.dp)
            )
            .semantics {
                contentDescription =
                    "Interactive service view of $satelliteId. " +
                            session.step.instruction
            }
            .pointerInput(enabled, session) {

                detectTapGestures { tap ->

                    if (
                        enabled &&
                        size.width > 0 &&
                        size.height > 0
                    ) {

                        val x =
                            tap.x *
                                    SatelliteRepairGeometry.WIDTH /
                                    size.width

                        val y =
                            tap.y *
                                    SatelliteRepairGeometry.HEIGHT /
                                    size.height

                        SatelliteRepairGeometry.hitTest(
                            x = x,
                            y = y,
                            session = session
                        )?.let(latestCallback)
                    }
                }
            }
    ) {

        scale(
            scaleX =
                size.width / SatelliteRepairGeometry.WIDTH,
            scaleY =
                size.height / SatelliteRepairGeometry.HEIGHT,
            pivot = Offset.Zero
        ) {

            drawSatelliteServiceScene(
                satelliteId = satelliteId,
                session = session
            )
        }
    }
}

/**
 * Draws the target satellite with exterior instruments,
 * a removable service panel, and a diagnostics connector.
 */
private fun DrawScope.drawSatelliteServiceScene(
    satelliteId: String,
    session: RoboticRepairSession
) {

    val accent = when (satelliteId) {

        "SAT-001" -> Color(0xFF22D3EE)
        "SAT-002" -> Color(0xFFFBBF24)
        "SAT-003" -> Color(0xFFA78BFA)

        else -> Color(0xFF94A3B8)
    }

    drawRect(
        color = Color(0xFF091321),
        size = Size(400f, 300f)
    )

    // Draw a technical inspection grid.
    for (i in 0..7) {

        val x = 15f + 48f * i

        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(x, 0f),
            end = Offset(x, 300f),
            strokeWidth = 1f
        )
    }

    for (i in 0..5) {

        val y = 12f + 52f * i

        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(0f, y),
            end = Offset(400f, y),
            strokeWidth = 1f
        )
    }

    // Draw solar arrays and structural supports.
    drawLine(
        color = Color(0xFF64748B),
        start = Offset(110f, 163f),
        end = Offset(290f, 163f),
        strokeWidth = 9f
    )

    drawSolarWing(
        x = 18f,
        y = 113f,
        width = 98f,
        height = 102f,
        accent = accent
    )

    drawSolarWing(
        x = 284f,
        y = 113f,
        width = 98f,
        height = 102f,
        accent = accent
    )

    // Draw distinctive instruments for each satellite.
    when (satelliteId) {

        "SAT-001" -> {

            // Earth-observation optical assembly.
            drawRect(
                color = Color(0xFF94A3B8),
                topLeft = Offset(188f, 244f),
                size = Size(24f, 22f)
            )

            drawCircle(
                color = Color(0xFF172554),
                radius = 19f,
                center = Offset(200f, 268f)
            )

            drawCircle(
                color = accent,
                radius = 12f,
                center = Offset(200f, 268f),
                style = Stroke(3f)
            )

            drawCircle(
                color = Color(0xFF0EA5E9),
                radius = 4f,
                center = Offset(200f, 268f)
            )
        }

        "SAT-002" -> {

            // Communications antenna and dish.
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(250f, 243f),
                end = Offset(315f, 261f),
                strokeWidth = 4f
            )

            drawCircle(
                color = Color(0xFF64748B),
                radius = 29f,
                center = Offset(329f, 260f)
            )

            drawCircle(
                color = accent,
                radius = 25f,
                center = Offset(329f, 260f),
                style = Stroke(3f)
            )

            drawCircle(
                color = Color(0xFFCBD5E1),
                radius = 6f,
                center = Offset(329f, 260f)
            )
        }

        "SAT-003" -> {

            // Scientific instrument mast and sensors.
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(200f, 83f),
                end = Offset(200f, 20f),
                strokeWidth = 4f
            )

            drawCircle(
                color = accent,
                radius = 9f,
                center = Offset(200f, 20f)
            )

            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(174f, 42f),
                end = Offset(226f, 42f),
                strokeWidth = 3f
            )

            drawCircle(
                color = Color(0xFF7DD3FC),
                radius = 6f,
                center = Offset(174f, 42f)
            )

            drawCircle(
                color = Color(0xFF7DD3FC),
                radius = 6f,
                center = Offset(226f, 42f)
            )
        }
    }

    // Draw the beveled satellite body.
    val body = Path().apply {

        moveTo(137f, 73f)
        lineTo(260f, 73f)
        lineTo(275f, 90f)
        lineTo(275f, 243f)
        lineTo(260f, 254f)
        lineTo(137f, 254f)
        lineTo(125f, 239f)
        lineTo(125f, 90f)
        close()
    }

    drawPath(
        path = body,
        color = Color(0xFF3B4B63)
    )

    drawPath(
        path = body,
        color = accent.copy(alpha = 0.6f),
        style = Stroke(2f)
    )

    drawRect(
        color = Color(0xFF19283D),
        topLeft = Offset(137f, 86f),
        size = Size(126f, 157f)
    )

    drawLine(
        color = Color.White.copy(alpha = 0.23f),
        start = Offset(138f, 87f),
        end = Offset(260f, 87f),
        strokeWidth = 2f
    )

    if (session.panelOpen) {

        // Expose the internal mounting tray.
        drawRoundRect(
            color = Color(0xFF07111D),
            topLeft = Offset(141f, 94f),
            size = Size(118f, 136f),
            cornerRadius = CornerRadius(7f)
        )

        drawRoundRect(
            color = accent.copy(alpha = 0.6f),
            topLeft = Offset(147f, 118f),
            size = Size(106f, 90f),
            cornerRadius = CornerRadius(5f),
            style = Stroke(2f)
        )

        drawComponent(
            session = session,
            accent = accent
        )

        // Move the lifted cover alongside the body.
        drawRoundRect(
            color = Color(0xFF475569),
            topLeft = Offset(280f, 96f),
            size = Size(96f, 130f),
            cornerRadius = CornerRadius(8f)
        )

        drawRoundRect(
            color = Color(0xFF94A3B8),
            topLeft = Offset(288f, 105f),
            size = Size(79f, 112f),
            cornerRadius = CornerRadius(5f),
            style = Stroke(2f)
        )

        for (i in 0..4) {

            val y = 124f + i * 18f

            drawLine(
                color = Color(0xFF64748B),
                start = Offset(297f, y),
                end = Offset(358f, y),
                strokeWidth = 3f
            )
        }

        drawLine(
            color = accent,
            start = Offset(258f, 111f),
            end = Offset(282f, 111f),
            strokeWidth = 3f
        )

        drawLine(
            color = accent,
            start = Offset(258f, 216f),
            end = Offset(282f, 216f),
            strokeWidth = 3f
        )

        // Highlight the cover when it must be closed.
        if (
            session.step == RepairProcedureStep.CLOSE_PANEL
        ) {

            drawRoundRect(
                color = Color(0xFF4ADE80),
                topLeft = Offset(279f, 95f),
                size = Size(98f, 132f),
                cornerRadius = CornerRadius(9f),
                style = Stroke(3f)
            )
        }

    } else {

        // Closed access-panel cover.
        drawRoundRect(
            color = Color(0xFF46566B),
            topLeft = Offset(141f, 94f),
            size = Size(118f, 136f),
            cornerRadius = CornerRadius(7f)
        )

        drawRoundRect(
            color = Color(0xFF718096),
            topLeft = Offset(149f, 104f),
            size = Size(102f, 116f),
            cornerRadius = CornerRadius(5f),
            style = Stroke(2f)
        )

        drawRect(
            color = Color(0xFF27364A),
            topLeft = Offset(176f, 141f),
            size = Size(48f, 40f)
        )

        drawLine(
            color = accent,
            start = Offset(185f, 160f),
            end = Offset(215f, 160f),
            strokeWidth = 3f
        )

        if (
            session.step == RepairProcedureStep.LIFT_PANEL
        ) {

            drawRoundRect(
                color = Color(0xFF4ADE80),
                topLeft = Offset(140f, 93f),
                size = Size(120f, 138f),
                cornerRadius = CornerRadius(7f),
                style = Stroke(3f)
            )
        }
    }

    // Draw the four physical screws or their empty mounts.
    SatelliteRepairGeometry.screwLocations.forEach {
            (hotspot, coordinate) ->

        val point = Offset(
            coordinate.first,
            coordinate.second
        )

        val removed =
            hotspot in session.loosenedScrews

        val highlighted =
            (
                    session.step == RepairProcedureStep.UNSCREW &&
                            !removed
                    ) ||
                    (
                            session.step ==
                                    RepairProcedureStep.SECURE_SCREWS &&
                                    removed
                            )

        if (highlighted) {

            drawCircle(
                color = Color(0xFF4ADE80).copy(
                    alpha = 0.25f
                ),
                radius = 17f,
                center = point
            )
        }

        drawCircle(
            color = Color(0xFF020617),
            radius = 11f,
            center = point
        )

        if (!removed) {

            drawCircle(
                color = Color(0xFFE2E8F0),
                radius = 8f,
                center = point
            )

            drawLine(
                color = Color(0xFF334155),
                start = Offset(point.x - 5f, point.y),
                end = Offset(point.x + 5f, point.y),
                strokeWidth = 2f
            )

        } else {

            drawCircle(
                color = Color(0xFF64748B),
                radius = 5f,
                center = point,
                style = Stroke(2f)
            )
        }
    }

    // Draw the diagnostics connector.
    val port = Offset(343f, 43f)

    if (session.step == RepairProcedureStep.VERIFY) {

        drawCircle(
            color = Color(0xFF4ADE80).copy(
                alpha = 0.23f
            ),
            radius = 27f,
            center = port
        )
    }

    drawCircle(
        color = Color(0xFF102B3B),
        radius = 20f,
        center = port
    )

    drawCircle(
        color = if (
            session.step == RepairProcedureStep.VERIFY
        ) {
            Color(0xFF4ADE80)
        } else {
            Color(0xFF7DD3FC)
        },
        radius = 17f,
        center = port,
        style = Stroke(3f)
    )

    for (i in -1..1) {

        drawCircle(
            color = Color.White,
            radius = 2.5f,
            center = Offset(
                343f + i * 7f,
                43f
            )
        )
    }
}

/**
 * Draws an articulated solar-array wing with
 * individually visible solar-cell divisions.
 */
private fun DrawScope.drawSolarWing(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    accent: Color
) {

    drawRect(
        color = Color(0xFF142E4B),
        topLeft = Offset(x, y),
        size = Size(width, height)
    )

    drawRect(
        color = accent.copy(alpha = 0.6f),
        topLeft = Offset(x, y),
        size = Size(width, height),
        style = Stroke(2.5f)
    )

    for (col in 1..3) {

        val px = x + width * col / 4

        drawLine(
            color = Color(0xFF38BDF8).copy(
                alpha = 0.6f
            ),
            start = Offset(px, y),
            end = Offset(px, y + height),
            strokeWidth = 1.5f
        )
    }

    for (row in 1..3) {

        val py = y + height * row / 4

        drawLine(
            color = Color(0xFF38BDF8).copy(
                alpha = 0.6f
            ),
            start = Offset(x, py),
            end = Offset(x + width, py),
            strokeWidth = 1.5f
        )
    }
}

/**
 * Draws the exposed equipment according to the fault
 * and the current disassembly/reassembly state.
 *
 * Damaged parts appear red, removed parts expose a
 * socket, and repaired or replaced parts appear green.
 */
private fun DrawScope.drawComponent(
    session: RoboticRepairSession,
    accent: Color
) {

    val condition = session.partCondition

    val color = when (condition) {

        RepairPartCondition.DAMAGED ->
            Color(0xFFFB7185)

        RepairPartCondition.REMOVED ->
            Color(0xFF64748B)

        RepairPartCondition.REPAIRED,
        RepairPartCondition.REPLACED ->
            Color(0xFF4ADE80)
    }

    drawRoundRect(
        color = Color(0xFF111827),
        topLeft = Offset(170f, 127f),
        size = Size(60f, 74f),
        cornerRadius = CornerRadius(6f)
    )

    if (condition == RepairPartCondition.REMOVED) {

        // Empty mounting socket.
        drawRoundRect(
            color = Color(0xFF64748B),
            topLeft = Offset(176f, 136f),
            size = Size(48f, 54f),
            cornerRadius = CornerRadius(4f),
            style = Stroke(3f)
        )

        for (dx in 0..2) {
            for (dy in 0..2) {

                drawCircle(
                    color = Color(0xFF64748B),
                    radius = 2f,
                    center = Offset(
                        184f + dx * 16f,
                        146f + dy * 15f
                    )
                )
            }
        }

    } else {

        drawRoundRect(
            color = color,
            topLeft = Offset(176f, 134f),
            size = Size(48f, 58f),
            cornerRadius = CornerRadius(5f)
        )

        when (session.damage) {

            EquipmentDamage.SOLAR_ARRAY_DAMAGE -> {

                // Solar power-controller conductors.
                for (i in 0..3) {

                    drawRect(
                        color = Color(0xFF0F172A),
                        topLeft = Offset(
                            182f,
                            140f + i * 12f
                        ),
                        size = Size(36f, 6f)
                    )
                }
            }

            EquipmentDamage.THERMAL_RADIATOR_DAMAGE -> {

                // Thermal circulation lines.
                for (i in 0..2) {

                    drawLine(
                        color = Color(0xFF0F172A),
                        start = Offset(
                            182f,
                            145f + i * 18f
                        ),
                        end = Offset(
                            218f,
                            145f + i * 18f
                        ),
                        strokeWidth = 4f
                    )
                }
            }

            EquipmentDamage.PAYLOAD_SENSOR_DAMAGE -> {

                // Sensor housing and optical center.
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 15f,
                    center = Offset(200f, 163f)
                )

                drawCircle(
                    color = accent,
                    radius = 9f,
                    center = Offset(200f, 163f)
                )
            }

            EquipmentDamage.NONE -> Unit
        }

        if (
            condition == RepairPartCondition.DAMAGED
        ) {

            drawLine(
                color = Color(0xFF7F1D1D),
                start = Offset(181f, 140f),
                end = Offset(220f, 185f),
                strokeWidth = 3f
            )
        }
    }

    // Highlight the exposed target part during servicing.
    if (
        session.step == RepairProcedureStep.EXAMINE ||
        session.step == RepairProcedureStep.REMOVE_PART ||
        session.step == RepairProcedureStep.REPAIR_IN_PLACE ||
        session.step ==
        RepairProcedureStep.INSTALL_REPLACEMENT
    ) {

        drawRoundRect(
            color = Color(0xFF4ADE80),
            topLeft = Offset(170f, 127f),
            size = Size(60f, 74f),
            cornerRadius = CornerRadius(7f),
            style = Stroke(3f)
        )
    }
}
