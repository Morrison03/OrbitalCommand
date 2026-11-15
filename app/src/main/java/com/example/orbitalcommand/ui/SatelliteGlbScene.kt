
package com.example.orbitalcommand.ui


import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.orbitalcommand.model.RepairHotspot
import com.example.orbitalcommand.model.RepairPartCondition
import com.example.orbitalcommand.model.RepairProcedureStep
import com.example.orbitalcommand.model.RoboticRepairSession
import com.example.orbitalcommand.model.Satellite3DGeometry
import io.github.sceneview.Scene
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node

/**
 * Renders imported GLB spacecraft meshes with
 * Google Filament through SceneView 2.3.0.
 *
 * Separate model nodes represent the service cover,
 * fasteners, mounting socket, diagnostic connector,
 * and removable equipment module.
 *
 * Operator interactions are forwarded to the existing
 * repair state machine; the renderer never changes
 * maintenance progress independently.
 */
@Composable
fun SatelliteGlbScene(
    satelliteId: String,
    session: RoboticRepairSession,
    enabled: Boolean,
    onHotspot: (RepairHotspot) -> Unit
) {

    // A new spacecraft receives its own scene resources.
    // Regular telemetry updates keep the same renderer.
    key(satelliteId) {

        val engine = rememberEngine()

        val modelLoader = rememberModelLoader(engine)

        val cameraNode = rememberCameraNode(engine) {
            position = Position(z = 5.7f)
        }

        val satelliteFile = when (satelliteId) {
            "SAT-001" -> "sat_001_bus.glb"
            "SAT-002" -> "sat_002_bus.glb"
            "SAT-003" -> "sat_003_bus.glb"
            else -> "sat_001_bus.glb"
        }

        // Each independent part is a separate Filament
        // model node. Reuse these nodes across state updates.
        val nodes = rememberNodes {

            addAsset(
                loader = modelLoader,
                id = "bus",
                file = satelliteFile
            )

            addAsset(
                loader = modelLoader,
                id = "cover",
                file = "service_cover.glb",
                at = Position(x = 0.52f, z = 0.55f)
            )

            addAsset(
                loader = modelLoader,
                id = "socket",
                file = "module_socket.glb",
                at = Position(z = 0.37f)
            )

            addAsset(
                loader = modelLoader,
                id = "module_damaged",
                file = "module_damaged.glb",
                at = Position(z = 0.48f)
            )

            addAsset(
                loader = modelLoader,
                id = "module_healthy",
                file = "module_healthy.glb",
                at = Position(z = 0.48f)
            )

            addAsset(
                loader = modelLoader,
                id = "diagnostic_port",
                file = "diagnostic_port.glb",
                at = Position(
                    x = 0.89f,
                    y = -0.85f,
                    z = 0.48f
                )
            )

            val positions = listOf(
                Position(x = -0.41f, y = 0.49f, z = 0.67f),
                Position(x = 0.41f, y = 0.49f, z = 0.67f),
                Position(x = -0.41f, y = -0.49f, z = 0.67f),
                Position(x = 0.41f, y = -0.49f, z = 0.67f)
            )

            positions.forEachIndexed { index, position ->

                addAsset(
                    loader = modelLoader,
                    id = "screw_${index + 1}",
                    file = "service_screw.glb",
                    at = position
                )

                addAsset(
                    loader = modelLoader,
                    id = "mount_${index + 1}",
                    file = "screw_mount.glb",
                    at = position.copy(z = 0.625f)
                )
            }
        }

        val indexedNodes = remember(nodes) {
            nodes.filterIsInstance<ModelNode>()
                .associateBy { it.name }
        }

        // Drive the mesh transforms from the repair state.
        val coverAmount by animateFloatAsState(
            targetValue = if (session.panelOpen) 1f else 0f,
            animationSpec = tween(650),
            label = "GLB access cover"
        )

        val extractedAmount by animateFloatAsState(
            targetValue = if (
                session.partCondition ==
                RepairPartCondition.REMOVED ||
                session.partCondition ==
                RepairPartCondition.REPLACED
            ) 1f else 0f,
            animationSpec = tween(550),
            label = "GLB damaged module extraction"
        )

        val installedAmount by animateFloatAsState(
            targetValue = if (
                session.partCondition ==
                RepairPartCondition.REPLACED
            ) 1f else 0f,
            animationSpec = tween(550),
            label = "GLB replacement installation"
        )

        val screwOne by animateFloatAsState(
            targetValue = if (
                RepairHotspot.SCREW_1 in session.loosenedScrews
            ) 1f else 0f,
            animationSpec = tween(330),
            label = "GLB screw one"
        )

        val screwTwo by animateFloatAsState(
            targetValue = if (
                RepairHotspot.SCREW_2 in session.loosenedScrews
            ) 1f else 0f,
            animationSpec = tween(330),
            label = "GLB screw two"
        )

        val screwThree by animateFloatAsState(
            targetValue = if (
                RepairHotspot.SCREW_3 in session.loosenedScrews
            ) 1f else 0f,
            animationSpec = tween(330),
            label = "GLB screw three"
        )

        val screwFour by animateFloatAsState(
            targetValue = if (
                RepairHotspot.SCREW_4 in session.loosenedScrews
            ) 1f else 0f,
            animationSpec = tween(330),
            label = "GLB screw four"
        )

        val fasteners = listOf(
            Triple(
                RepairHotspot.SCREW_1,
                "screw_1",
                screwOne
            ),
            Triple(
                RepairHotspot.SCREW_2,
                "screw_2",
                screwTwo
            ),
            Triple(
                RepairHotspot.SCREW_3,
                "screw_3",
                screwThree
            ),
            Triple(
                RepairHotspot.SCREW_4,
                "screw_4",
                screwFour
            )
        )

        val activeHotspots =
            Satellite3DGeometry.activeHotspots(session)

        SideEffect {

            // The panel's authored origin is its hinge.
            indexedNodes["cover"]?.apply {
                position = Position(x = 0.52f, z = 0.55f)
                rotation = Rotation(y = 112f * coverAmount)
                isTouchable = enabled &&
                        RepairHotspot.ACCESS_PANEL in activeHotspots
            }

            val bayOpen = coverAmount > 0.90f

            indexedNodes["socket"]?.apply {
                isVisible = bayOpen
                isTouchable = enabled &&
                        RepairHotspot.COMPONENT_SOCKET in
                        activeHotspots
            }

            val damaged = indexedNodes["module_damaged"]

            damaged?.apply {

                isVisible = bayOpen &&
                        session.partCondition !=
                        RepairPartCondition.REPAIRED

                position = Position(
                    x = 1.05f * extractedAmount,
                    y = 0.67f * extractedAmount,
                    z = 0.48f +
                            0.12f * extractedAmount
                )

                isTouchable = enabled &&
                        RepairHotspot.COMPONENT in activeHotspots
            }

            indexedNodes["module_healthy"]?.apply {

                isVisible = bayOpen && (
                        session.partCondition ==
                                RepairPartCondition.REPAIRED ||
                                session.partCondition ==
                                RepairPartCondition.REPLACED
                        )

                position = if (
                    session.partCondition ==
                    RepairPartCondition.REPLACED
                ) {

                    Position(
                        x = -1.05f * (1f - installedAmount),
                        y = 0.67f * (1f - installedAmount),
                        z = 0.48f
                    )

                } else {

                    Position(z = 0.48f)
                }
            }

            indexedNodes["diagnostic_port"]?.apply {
                isTouchable = enabled &&
                        RepairHotspot.DIAGNOSTIC_PORT in
                        activeHotspots
            }

            val screwPositions = listOf(
                Pair(-0.41f, 0.49f),
                Pair(0.41f, 0.49f),
                Pair(-0.41f, -0.49f),
                Pair(0.41f, -0.49f)
            )

            fasteners.forEachIndexed { index, fastener ->

                val (spot, nodeName, progress) = fastener
                val (x, y) = screwPositions[index]

                indexedNodes[nodeName]?.apply {

                    position = Position(
                        x = x,
                        y = y,
                        z = 0.67f + progress * 0.27f
                    )

                    isVisible = progress < 0.98f

                    isTouchable = enabled &&
                            spot in activeHotspots &&
                            session.step ==
                            RepairProcedureStep.UNSCREW
                }

                indexedNodes["mount_${index + 1}"]?.apply {

                    isTouchable = enabled &&
                            spot in activeHotspots &&
                            session.step ==
                            RepairProcedureStep.SECURE_SCREWS
                }
            }

            indexedNodes["bus"]?.isTouchable = false
        }

        // Keep the latest repair state available inside
        // SceneView's gesture callback.
        val currentSession by rememberUpdatedState(session)
        val currentEnabled by rememberUpdatedState(enabled)
        val currentCallback by rememberUpdatedState(onHotspot)

        Scene(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(12.dp)),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = nodes,
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { _, hitNode ->

                    if (currentEnabled) {

                        val available =
                            Satellite3DGeometry.activeHotspots(
                                currentSession
                            )

                        var candidate: Node? = hitNode

                        // A GLB mesh may report a child node
                        // rather than the named model root.
                        while (candidate != null) {

                            val selected =
                                repairHotspotForNode(
                                    candidate.name
                                )

                            if (
                                selected != null &&
                                selected in available
                            ) {

                                currentCallback(selected)
                                break
                            }

                            candidate = candidate.parent
                        }
                    }
                }
            )
        )
    }
}

/**
 * Adds a named GLB asset to a SceneView node collection.
 *
 * Model loading occurs once when the scene is created.
 */
private fun MutableList<Node>.addAsset(
    loader: ModelLoader,
    id: String,
    file: String,
    at: Position = Position(0f)
) {

    add(
        ModelNode(
            modelInstance = loader.createModelInstance(
                assetFileLocation = "models/$file"
            ),
            autoAnimate = false
        ).apply {
            name = id
            position = at
        }
    )
}

/**
 * Resolves named 3D mesh nodes to existing repair
 * hotspots without duplicating maintenance rules.
 */
private fun repairHotspotForNode(
    name: String?
): RepairHotspot? {

    return when (name) {

        "cover" -> RepairHotspot.ACCESS_PANEL

        "module_damaged",
        "module_healthy" -> RepairHotspot.COMPONENT

        "socket" -> RepairHotspot.COMPONENT_SOCKET

        "diagnostic_port" ->
            RepairHotspot.DIAGNOSTIC_PORT

        "screw_1",
        "mount_1" -> RepairHotspot.SCREW_1

        "screw_2",
        "mount_2" -> RepairHotspot.SCREW_2

        "screw_3",
        "mount_3" -> RepairHotspot.SCREW_3

        "screw_4",
        "mount_4" -> RepairHotspot.SCREW_4

        else -> null
    }
}
