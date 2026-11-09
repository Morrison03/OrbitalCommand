
package com.example.orbitalcommand.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.orbitalcommand.model.RepairHotspot
import com.example.orbitalcommand.model.RepairPartCondition
import com.example.orbitalcommand.model.RoboticRepairSession
import com.example.orbitalcommand.model.Satellite3DGeometry
import com.example.orbitalcommand.model.SatelliteCamera
import com.example.orbitalcommand.model.SatellitePoint3D
import kotlin.math.abs

/**
 * Renders the satellite service model with
 * animated, removable hardware.
 *
 * Dragging changes the camera, while taps are
 * forwarded to the existing repair state machine.
 *
 * This is a procedural 3D projection,
 * not a GLB model viewer.
 */
@Composable
fun Satellite3DScene(
    satelliteId: String,
    session: RoboticRepairSession,
    enabled: Boolean,
    camera: SatelliteCamera,
    onCameraChange: (SatelliteCamera) -> Unit,
    onHotspot: (RepairHotspot) -> Unit
) {

    val coverOpen by animateFloatAsState(
        targetValue = if (session.panelOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "hinged service cover"
    )

    val screw1 by animateFloatAsState(
        targetValue = if (
            RepairHotspot.SCREW_1 in session.loosenedScrews
        ) 1f else 0f,
        animationSpec = tween(350),
        label = "screw one extraction"
    )

    val screw2 by animateFloatAsState(
        targetValue = if (
            RepairHotspot.SCREW_2 in session.loosenedScrews
        ) 1f else 0f,
        animationSpec = tween(350),
        label = "screw two extraction"
    )

    val screw3 by animateFloatAsState(
        targetValue = if (
            RepairHotspot.SCREW_3 in session.loosenedScrews
        ) 1f else 0f,
        animationSpec = tween(350),
        label = "screw three extraction"
    )

    val screw4 by animateFloatAsState(
        targetValue = if (
            RepairHotspot.SCREW_4 in session.loosenedScrews
        ) 1f else 0f,
        animationSpec = tween(350),
        label = "screw four extraction"
    )

    val extracted by animateFloatAsState(
        targetValue = if (
            session.partCondition ==
            RepairPartCondition.REMOVED ||
            session.partCondition ==
            RepairPartCondition.REPLACED
        ) 1f else 0f,
        animationSpec = tween(600),
        label = "remove damaged module"
    )

    val installed by animateFloatAsState(
        targetValue = if (
            session.partCondition ==
            RepairPartCondition.REPLACED
        ) 1f else 0f,
        animationSpec = tween(600),
        label = "install new module"
    )

    val repaired by animateFloatAsState(
        targetValue = if (
            session.partCondition ==
            RepairPartCondition.REPAIRED
        ) 1f else 0f,
        animationSpec = tween(400),
        label = "repair module"
    )

    val fasteners = mapOf(
        RepairHotspot.SCREW_1 to screw1,
        RepairHotspot.SCREW_2 to screw2,
        RepairHotspot.SCREW_3 to screw3,
        RepairHotspot.SCREW_4 to screw4
    )

    val currentCamera by rememberUpdatedState(camera)
    val currentCameraCallback by rememberUpdatedState(
        onCameraChange
    )
    val currentHotspotCallback by rememberUpdatedState(
        onHotspot
    )

    val coverIsMoving = abs(
        coverOpen - if (session.panelOpen) 1f else 0f
    ) > 0.025f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(
                Color(0xFF07111F),
                RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {

                detectDragGestures { change, drag ->

                    change.consume()

                    currentCameraCallback(
                        currentCamera.rotated(
                            deltaYaw = drag.x * 0.30f,
                            deltaPitch = drag.y * 0.30f
                        )
                    )
                }
            }
            .pointerInput(
                enabled,
                session,
                coverIsMoving
            ) {

                detectTapGestures { position ->

                    if (
                        enabled &&
                        !coverIsMoving &&
                        size.width > 0 &&
                        size.height > 0
                    ) {

                        val x = position.x *
                                Satellite3DGeometry.VIEW_WIDTH /
                                size.width

                        val y = position.y *
                                Satellite3DGeometry.VIEW_HEIGHT /
                                size.height

                        Satellite3DGeometry.hitTest(
                            x = x,
                            y = y,
                            camera = currentCamera,
                            session = session
                        )?.let(currentHotspotCallback)
                    }
                }
            }
    ) {

        scale(
            scaleX = size.width /
                    Satellite3DGeometry.VIEW_WIDTH,
            scaleY = size.height /
                    Satellite3DGeometry.VIEW_HEIGHT,
            pivot = Offset.Zero
        ) {

            renderServiceScene(
                satelliteId = satelliteId,
                session = session,
                camera = camera,
                panelProgress = coverOpen,
                screwProgress = fasteners,
                damagedExtraction = extracted,
                replacementInsertion = installed,
                repairProgress = repaired
            )
        }
    }
}

/**
 * Represents one cuboid component
 * in the spacecraft service model.
 */
private data class ModelBox(
    val center: SatellitePoint3D,
    val halfX: Float,
    val halfY: Float,
    val halfZ: Float,
    val color: Color,
    val hingedCover: Boolean = false
)

/**
 * A projected polygon with depth
 * for painter-order rendering.
 */
private data class SceneFace(
    val points: List<Offset>,
    val depth: Float,
    val color: Color
)

/**
 * Converts a cuboid into six projected faces.
 *
 * The faces can be sorted together with faces
 * from other components for consistent depth.
 */
private fun boxFaces(
    box: ModelBox,
    camera: SatelliteCamera,
    panelProgress: Float
): List<SceneFace> {

    val c = box.center
    val x = box.halfX
    val y = box.halfY
    val z = box.halfZ

    val vertices = listOf(
        SatellitePoint3D(c.x-x, c.y-y, c.z-z),
        SatellitePoint3D(c.x+x, c.y-y, c.z-z),
        SatellitePoint3D(c.x+x, c.y+y, c.z-z),
        SatellitePoint3D(c.x-x, c.y+y, c.z-z),
        SatellitePoint3D(c.x-x, c.y-y, c.z+z),
        SatellitePoint3D(c.x+x, c.y-y, c.z+z),
        SatellitePoint3D(c.x+x, c.y+y, c.z+z),
        SatellitePoint3D(c.x-x, c.y+y, c.z+z)
    ).map { vertex ->

        Satellite3DGeometry.project(
            point = if (box.hingedCover) {
                Satellite3DGeometry.hingedPanelPoint(
                    vertex,
                    panelProgress
                )
            } else {
                vertex
            },
            camera = camera
        )
    }

    val faces = listOf(
        listOf(0, 1, 2, 3),
        listOf(4, 5, 6, 7),
        listOf(0, 1, 5, 4),
        listOf(1, 2, 6, 5),
        listOf(2, 3, 7, 6),
        listOf(3, 0, 4, 7)
    )

    val shading = listOf(
        0.39f, 1f, 0.70f,
        0.62f, 0.78f, 0.58f
    )

    return faces.mapIndexed { index, indices ->

        val color = box.color
        val shade = shading[index]

        SceneFace(
            points = indices.map { i ->
                Offset(vertices[i].x, vertices[i].y)
            },
            depth = indices.map { i ->
                vertices[i].depth
            }.average().toFloat(),
            color = color.copy(
                red = color.red * shade,
                green = color.green * shade,
                blue = color.blue * shade
            )
        )
    }
}

/**
 * Interpolates between 3D locations.
 *
 * Used to animate removal and installation
 * of satellite equipment modules.
 */
private fun lerpPoint(
    a: SatellitePoint3D,
    b: SatellitePoint3D,
    t: Float
): SatellitePoint3D = SatellitePoint3D(
    a.x + (b.x - a.x) * t,
    a.y + (b.y - a.y) * t,
    a.z + (b.z - a.z) * t
)

/**
 * Builds and renders a depth-sorted satellite
 * whose appearance follows the repair state.
 */
private fun DrawScope.renderServiceScene(
    satelliteId: String,
    session: RoboticRepairSession,
    camera: SatelliteCamera,
    panelProgress: Float,
    screwProgress: Map<RepairHotspot, Float>,
    damagedExtraction: Float,
    replacementInsertion: Float,
    repairProgress: Float
) {

    val accent = when (satelliteId) {
        "SAT-001" -> Color(0xFF38BDF8)
        "SAT-002" -> Color(0xFFFBBF24)
        "SAT-003" -> Color(0xFFA78BFA)
        else -> Color(0xFF93C5FD)
    }

    // Draw the inspection viewport background.
    for (i in 0..7) {
        val x = 20f + 52f * i

        drawLine(
            color = Color.White.copy(alpha = 0.075f),
            start = Offset(x, 0f),
            end = Offset(x, 300f)
        )
    }

    for (i in 0..5) {
        val y = 20f + 52f * i

        drawLine(
            color = Color.White.copy(alpha = 0.075f),
            start = Offset(0f, y),
            end = Offset(400f, y)
        )
    }

    val boxes = mutableListOf<ModelBox>()

    // Structural booms, solar wings, and cells.
    for (side in listOf(-1f, 1f)) {

        boxes += ModelBox(
            SatellitePoint3D(side * 0.87f, 0f, -0.10f),
            0.28f, 0.045f, 0.055f,
            Color(0xFF64748B)
        )

        boxes += ModelBox(
            SatellitePoint3D(side * 1.36f, 0f, -0.08f),
            0.55f, 0.52f, 0.055f,
            Color(0xFF1E40AF)
        )

        for (i in -1..1) {
            for (j in -1..1) {

                boxes += ModelBox(
                    SatellitePoint3D(
                        side * 1.36f + i * 0.32f,
                        j * 0.315f,
                        -0.012f
                    ),
                    0.14f, 0.14f, 0.009f,
                    if ((i + j) % 2 == 0)
                        Color(0xFF2563EB)
                    else
                        Color(0xFF0EA5E9)
                )
            }
        }
    }

    // Main spacecraft bus.
    boxes += ModelBox(
        SatellitePoint3D(0f, 0f, 0f),
        0.68f, 0.79f, 0.30f,
        Color(0xFF64748B)
    )

    // Distinct external instruments.
    when (satelliteId) {

        "SAT-001" -> {
            boxes += ModelBox(
                SatellitePoint3D(0f, 1.01f, -0.06f),
                0.23f, 0.27f, 0.25f,
                Color(0xFF38BDF8)
            )
        }

        "SAT-002" -> {

            boxes += ModelBox(
                SatellitePoint3D(0f, -1.02f, -0.09f),
                0.38f, 0.10f, 0.25f,
                accent
            )

            boxes += ModelBox(
                SatellitePoint3D(0f, -1.15f, 0.05f),
                0.16f, 0.07f, 0.16f,
                Color(0xFFE2E8F0)
            )
        }

        "SAT-003" -> {

            boxes += ModelBox(
                SatellitePoint3D(0f, -1.05f, 0f),
                0.08f, 0.38f, 0.09f,
                accent
            )

            boxes += ModelBox(
                SatellitePoint3D(0f, -1.38f, 0f),
                0.17f, 0.08f, 0.17f,
                Color(0xFF7DD3FC)
            )
        }
    }

    // Opening the cover exposes the electronics tray.
    if (panelProgress > 0.005f) {

        boxes += ModelBox(
            SatellitePoint3D(0f, 0f, 0.33f),
            0.52f, 0.64f, 0.025f,
            Color(0xFF0F172A).copy(
                alpha = panelProgress
            )
        )

        boxes += ModelBox(
            SatellitePoint3D(0f, 0f, 0.36f),
            0.38f, 0.47f, 0.025f,
            Color(0xFF334155).copy(
                alpha = panelProgress
            )
        )

        val socket = SatellitePoint3D(
            0f, 0f, 0.46f
        )

        val discard = SatellitePoint3D(
            1.12f, 0.74f, 0.67f
        )

        val supply = SatellitePoint3D(
            -1.12f, 0.72f, 0.67f
        )

        // An in-place repair changes the existing
        // component from damaged red to healthy green.
        val oldPartColor = Color(
            red = 0.94f * (1f - repairProgress) +
                    0.18f * repairProgress,
            green = 0.20f * (1f - repairProgress) +
                    0.84f * repairProgress,
            blue = 0.27f * (1f - repairProgress) +
                    0.45f * repairProgress,
            alpha = panelProgress
        )

        // A removed damaged module moves out
        // and remains in the discard area.
        boxes += ModelBox(
            center = lerpPoint(
                socket,
                discard,
                damagedExtraction
            ),
            halfX = 0.29f,
            halfY = 0.34f,
            halfZ = 0.11f,
            color = oldPartColor
        )

        if (
            session.partCondition ==
            RepairPartCondition.REMOVED ||
            session.partCondition ==
            RepairPartCondition.REPLACED
        ) {

            // A healthy replacement moves from
            // the supply area into the empty socket.
            boxes += ModelBox(
                center = lerpPoint(
                    supply,
                    socket,
                    replacementInsertion
                ),
                halfX = 0.29f,
                halfY = 0.34f,
                halfZ = 0.11f,
                color = Color(0xFF4ADE80).copy(
                    alpha = panelProgress
                )
            )
        }
    }

    // The service cover rotates about a hinge.
    boxes += ModelBox(
        center = SatellitePoint3D(0f, 0f, 0.41f),
        halfX = 0.52f,
        halfY = 0.65f,
        halfZ = 0.11f,
        color = Color(0xFF94A3B8),
        hingedCover = true
    )

    // Sort faces across all components instead
    // of drawing each component independently.
    boxes.flatMap {
        boxFaces(it, camera, panelProgress)
    }
        .sortedBy { it.depth }
        .forEach { face ->

            val path = Path()

            face.points.forEachIndexed { index, point ->

                if (index == 0) {
                    path.moveTo(point.x, point.y)
                } else {
                    path.lineTo(point.x, point.y)
                }
            }

            path.close()

            drawPath(path, face.color)

            drawPath(
                path = path,
                color = Color.White.copy(
                    alpha = 0.14f * face.color.alpha
                ),
                style = Stroke(width = 0.8f)
            )
        }

    // Screw mounts remain in place while individual
    // screws animate out and back in.
    for ((screw, extraction) in screwProgress) {

        val base = Satellite3DGeometry.hotspotPosition(
            screw,
            session
        )

        val hole = Satellite3DGeometry.project(
            base,
            camera
        )

        val holeCenter = Offset(
            hole.x,
            hole.y
        )

        drawCircle(
            color = Color(0xFF0F172A),
            radius = 8f,
            center = holeCenter
        )

        drawCircle(
            color = Color(0xFF64748B),
            radius = 6f,
            center = holeCenter,
            style = Stroke(width = 1.5f)
        )

        if (extraction < 0.995f) {

            val moving = Satellite3DGeometry.project(
                base.copy(
                    z = base.z + 0.20f * extraction
                ),
                camera
            )

            val fastenerColor =
                Color(0xFFE2E8F0).copy(
                    alpha = 1f - extraction
                )

            val screwCenter = Offset(
                moving.x,
                moving.y
            )

            drawCircle(
                color = fastenerColor,
                radius = 6.2f,
                center = screwCenter
            )

            drawLine(
                color = Color(0xFF334155).copy(
                    alpha = 1f - extraction
                ),
                start = Offset(
                    screwCenter.x - 3.5f,
                    screwCenter.y
                ),
                end = Offset(
                    screwCenter.x + 3.5f,
                    screwCenter.y
                ),
                strokeWidth = 1.7f
            )
        }
    }

    // Render the diagnostics connector.
    val port = Satellite3DGeometry.project(
        Satellite3DGeometry.hotspotPosition(
            RepairHotspot.DIAGNOSTIC_PORT,
            session
        ),
        camera
    )

    val portCenter = Offset(port.x, port.y)

    drawCircle(
        color = Color(0xFF0F172A),
        radius = 10f,
        center = portCenter
    )

    drawCircle(
        color = accent,
        radius = 8f,
        center = portCenter,
        style = Stroke(width = 2f)
    )

    drawCircle(
        color = Color.White,
        radius = 2.5f,
        center = portCenter
    )

    // Highlight available targets at their
    // current projected 3D positions.
    for (
    hotspot in
    Satellite3DGeometry.activeHotspots(session)
    ) {

        val point = Satellite3DGeometry.project(
            Satellite3DGeometry.hotspotPosition(
                hotspot,
                session
            ),
            camera
        )

        val targetCenter = Offset(
            point.x,
            point.y
        )

        drawCircle(
            color = Color(0xFF4ADE80).copy(
                alpha = 0.17f
            ),
            radius = 21f,
            center = targetCenter
        )

        drawCircle(
            color = Color(0xFF4ADE80),
            radius = 15f,
            center = targetCenter,
            style = Stroke(width = 2f)
        )
    }
}
