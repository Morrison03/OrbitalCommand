
package com.example.orbitalcommand.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.orbitalcommand.model.EquipmentDamage
import com.example.orbitalcommand.model.RepairHotspot
import com.example.orbitalcommand.model.RepairPartCondition
import com.example.orbitalcommand.model.RepairTool
import com.example.orbitalcommand.model.RepairProcedureStep
import com.example.orbitalcommand.model.RoboticRepairSession
import com.example.orbitalcommand.model.SatelliteServiceFace
import com.example.orbitalcommand.model.ServicerDockPhase
import com.example.orbitalcommand.model.ServicerPovPose
import com.example.orbitalcommand.model.ServicerSolarPovMotion
import com.example.orbitalcommand.model.ComponentPovAction
import com.example.orbitalcommand.model.ComponentPovOperation
import com.example.orbitalcommand.model.ServicerComponentPovMotion
import com.example.orbitalcommand.model.ServicerRailContact
import com.example.orbitalcommand.model.SolarPovOperation
import com.example.orbitalcommand.model.SolarPovAction
import com.example.orbitalcommand.model.ServicerScrewDirection
import com.example.orbitalcommand.model.ServicerScrewMotion
import com.example.orbitalcommand.model.ServicerScrewOperation
import io.github.sceneview.Scene
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect

/**
 * The compact readouts shown beside CAM-01 instead of over the picture.
 *
 * [activity] is derived from the actual queued POV animation.
 * The parent docking stage supplies link, grip, and face information.
 */
data class ServicerPovReadout(
    val activity: String,
    val tool: String
)

/**
 * Non-interactive 3D camera feed carried by SERVICER-01.
 *
 * Successful repair interactions update the authoritative
 * RoboticRepairSession, and those changes trigger animations.
 *
 * Supported POV operations:
 * - Screwdriver reaches and rotates individual fasteners.
 * - The service cover opens and closes.
 * - Solar wings detach, move, and are replaced.
 * - The communications dish is serviced.
 * - Payload and thermal components are serviced.
 * - The left gripper follows a physical rail contact.
 *
 * The current arm GLBs are rigid assemblies. Reaching is
 * simulated through mesh translation rather than joint IK.
 *
 * This component renders no text over the video.
 * Readout information is delivered through [onReadoutChange].
 */
@Composable
fun ServicerPovCamera(
    satelliteId: String,
    session: RoboticRepairSession,
    face: SatelliteServiceFace,
    phase: ServicerDockPhase,
    tool: RepairTool,
    pose: ServicerPovPose,
    approachAmount: Float,
    navigationReady: Boolean,
    cameraRollDegrees: Float = 0f,
    onReadoutChange: (ServicerPovReadout) -> Unit = {},
    modifier: Modifier = Modifier
) {
    key(satelliteId) {

        val engine = rememberEngine()
        val loader = rememberModelLoader(engine)

        /**
         * Independent onboard camera controlled by
         * the docking and repositioning system.
         */
        val camera = rememberCameraNode(engine) {
            position = Position(z = 4.9f)

            lookAt(
                Position(0f, 0f, 0f),
                smooth = false
            )
        }

        /**
         * The screwdriver model's bit tip is at:
         * X = +0.37, Y = -0.28, Z = -1.86.
         *
         * A pivot allows rotation around the tip.
         */
        val screwdriverPivot = remember(engine) {
            Node(engine).apply {
                name = "screwdriver_pivot"
                parent = camera
                isTouchable = false
            }
        }

        val busFile = when (satelliteId) {
            "SAT-001" -> "sat_001_bus_serviceable.glb"
            "SAT-002" -> "sat_002_bus_serviceable.glb"
            "SAT-003" -> "sat_003_bus_serviceable.glb"
            else -> "sat_001_bus_serviceable.glb"
        }

        /**
         * Load model nodes once, then animate
         * their transforms instead of recreating assets.
         */
        val nodes = rememberNodes {

            add(
                glbNode(
                    loader,
                    "bus",
                    busFile
                )
            )

            add(
                glbNode(
                    loader,
                    "cover",
                    "service_cover.glb",
                    Position(x = 0.52f, z = 0.55f)
                )
            )

            add(
                glbNode(
                    loader,
                    "damaged",
                    "module_damaged.glb",
                    Position(z = 0.48f)
                )
            )

            add(
                glbNode(
                    loader,
                    "healthy",
                    "module_healthy.glb",
                    Position(z = 0.48f)
                )
            )

            /**
             * Component-specific model assets.
             */
            add(
                glbNode(
                    loader,
                    "dish_good",
                    "sat_dish_healthy.glb",
                    Position(
                        x = 0f,
                        y = 1.27f,
                        z = 0.19f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "dish_bad",
                    "sat_dish_damaged.glb",
                    Position(
                        x = 0f,
                        y = 1.27f,
                        z = 0.19f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "payload_module",
                    "sat_payload_module.glb",
                    Position(
                        x = -0.25f,
                        y = 0f,
                        z = 0.50f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "thermal_module",
                    "sat_thermal_module.glb",
                    Position(
                        x = 0.25f,
                        y = 0f,
                        z = 0.50f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "payload_replacement",
                    "sat_payload_module.glb",
                    Position(
                        x = -0.25f,
                        y = 0f,
                        z = 0.50f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "thermal_replacement",
                    "sat_thermal_module.glb",
                    Position(
                        x = 0.25f,
                        y = 0f,
                        z = 0.50f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "payload_fault",
                    "sat_module_fault_patch.glb",
                    Position(
                        x = -0.25f,
                        y = 0f,
                        z = 0.60f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "thermal_fault",
                    "sat_module_fault_patch.glb",
                    Position(
                        x = 0.25f,
                        y = 0f,
                        z = 0.60f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "solar_fault_a",
                    "sat_solar_fault_patch.glb",
                    Position(
                        x = -1.36f,
                        y = 0f,
                        z = 0.08f
                    )
                )
            )

            add(
                glbNode(
                    loader,
                    "solar_fault_b",
                    "sat_solar_fault_patch.glb",
                    Position(
                        x = 1.36f,
                        y = 0f,
                        z = 0.08f
                    )
                )
            )

            /**
             * The spacecraft bus has no baked solar wings.
             *
             * Each solar wing is a detachable mesh.
             * Original and replacement wings are controlled
             * independently to avoid overlapping geometry.
             */
            for (
            (name, wing, rootX) in listOf(
                Triple(
                    "a",
                    "port",
                    ServicerSolarPovMotion.PORT_ROOT_X
                ),
                Triple(
                    "b",
                    "starboard",
                    ServicerSolarPovMotion.STARBOARD_ROOT_X
                )
            )
            ) {
                add(
                    glbNode(
                        loader,
                        "wing_original_$name",
                        "sat_solar_wing_$wing.glb",
                        Position(x = rootX)
                    )
                )

                add(
                    glbNode(
                        loader,
                        "wing_replacement_$name",
                        "sat_solar_wing_$wing.glb",
                        Position(x = rootX)
                    )
                )
            }

            /**
             * Physical attachment rails and contact clamp.
             */
            add(
                glbNode(
                    loader,
                    "edge_rails",
                    "sat_servicer_edge_rails.glb"
                )
            )

            add(
                glbNode(
                    loader,
                    "rail_clamp",
                    "sat_servicer_rail_clamp.glb"
                )
            )

            /**
             * Emissive spark effects for servicing actions.
             */
            add(
                glbNode(
                    loader,
                    "solar_spark",
                    "sat_solar_connector_sparks.glb"
                )
            )

            add(
                glbNode(
                    loader,
                    "service_spark",
                    "sat_solar_connector_sparks.glb"
                )
            )

            /**
             * Four independently removable panel fasteners.
             */
            ServicerScrewMotion.screws.forEach { screw ->
                val point =
                    ServicerScrewMotion.screwPosition(screw)

                add(
                    glbNode(
                        loader,
                        "fastener_${screw.name}",
                        "service_screw.glb",
                        Position(
                            point.x,
                            point.y,
                            point.z
                        )
                    )
                )
            }

            /**
             * Left robotic arm maintains the rail grip.
             */
            add(
                glbNode(
                    loader,
                    "arm_left",
                    "servicer_arm_left.glb"
                ).apply {
                    parent = camera
                }
            )

            /**
             * Right robotic arm performs servicing tasks.
             */
            add(
                glbNode(
                    loader,
                    "arm_right",
                    "servicer_arm_right.glb"
                ).apply {
                    parent = camera
                }
            )

            add(screwdriverPivot)

            /**
             * Screwdriver is parented under the bit pivot.
             */
            add(
                glbNode(
                    loader,
                    "tool_screwdriver",
                    "servicer_tool_screwdriver.glb"
                ).apply {
                    parent = screwdriverPivot

                    position = Position(
                        x = -0.37f,
                        y = 0.28f,
                        z = 1.86f
                    )
                }
            )

            /**
             * Remaining tool attachments.
             */
            for (
            attachment in listOf(
                "scanner",
                "extractor",
                "repair_kit",
                "installer"
            )
            ) {
                add(
                    glbNode(
                        loader,
                        "tool_$attachment",
                        "servicer_tool_$attachment.glb"
                    ).apply {
                        parent = camera
                    }
                )
            }
        }

        /**
         * SceneView names are nullable.
         * Exclude unnamed nodes from the lookup map.
         */
        val indexed: Map<String, ModelNode> =
            remember(nodes) {
                nodes
                    .filterIsInstance<ModelNode>()
                    .mapNotNull { node ->
                        node.name?.let { name ->
                            name to node
                        }
                    }
                    .toMap()
            }

        /**
         * Independent component-operation queue.
         */
        val pendingComponents = remember(
            session.workOrderId
        ) {
            Channel<ComponentPovOperation>(
                Channel.UNLIMITED
            )
        }

        val componentProgress = remember(
            session.workOrderId
        ) {
            Animatable(0f)
        }

        var activeComponent by remember(
            session.workOrderId
        ) {
            mutableStateOf<ComponentPovOperation?>(null)
        }

        /**
         * Access current state in rendering callbacks.
         */
        val currentPose by rememberUpdatedState(pose)

        val currentSession by rememberUpdatedState(session)

        val currentTool by rememberUpdatedState(tool)

        val currentApproach by rememberUpdatedState(
            approachAmount
        )

        val currentPhase by rememberUpdatedState(phase)

        val currentFace by rememberUpdatedState(face)

        val currentReady by rememberUpdatedState(
            navigationReady
        )

        val currentCameraRoll by rememberUpdatedState(
            cameraRollDegrees
        )

        val latestOnReadoutChange by rememberUpdatedState(
            onReadoutChange
        )

        /**
         * Queue an operation whenever the repair simulator
         * reports a successful component-state change.
         */
        LaunchedEffect(session.workOrderId) {
            var previous = currentSession

            snapshotFlow {
                currentSession
            }.collect { updated ->

                ServicerComponentPovMotion.changes(
                    previous,
                    updated
                ).forEach { operation ->
                    pendingComponents.trySend(operation)
                }

                previous = updated
            }
        }

        /**
         * Animate component operations sequentially.
         * Communication loss pauses but does not discard them.
         */
        LaunchedEffect(
            session.workOrderId,
            navigationReady
        ) {
            if (!navigationReady) {
                return@LaunchedEffect
            }

            while (true) {
                if (activeComponent == null) {
                    val next = pendingComponents.receive()

                    componentProgress.snapTo(0f)
                    activeComponent = next
                }

                val remaining = (
                        (1f - componentProgress.value) *
                                ServicerComponentPovMotion.DURATION_MILLIS
                        ).toInt().coerceAtLeast(1)

                componentProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        remaining,
                        easing = LinearEasing
                    )
                )

                activeComponent = null
                componentProgress.snapTo(0f)
            }
        }

        /**
         * Independent solar-wing animation queue.
         */
        val pendingSolar = remember(
            session.workOrderId
        ) {
            Channel<SolarPovOperation>(
                Channel.UNLIMITED
            )
        }

        val solarProgress = remember(
            session.workOrderId
        ) {
            Animatable(0f)
        }

        var activeSolar by remember(
            session.workOrderId
        ) {
            mutableStateOf<SolarPovOperation?>(null)
        }

        LaunchedEffect(session.workOrderId) {
            var previous = currentSession.partCondition

            snapshotFlow {
                currentSession.partCondition
            }.collect { condition ->

                if (
                    currentSession.damage ==
                    EquipmentDamage.SOLAR_ARRAY_DAMAGE
                ) {
                    ServicerSolarPovMotion.fromChange(
                        previous,
                        condition,
                        currentSession.faultySolarPanel
                    )?.let { operation ->
                        pendingSolar.trySend(operation)
                    }
                }

                previous = condition
            }
        }

        /**
         * Process solar removal, repair, and installation.
         */
        LaunchedEffect(
            session.workOrderId,
            navigationReady
        ) {
            if (!navigationReady) {
                return@LaunchedEffect
            }

            while (true) {
                if (activeSolar == null) {
                    val next = pendingSolar.receive()

                    solarProgress.snapTo(0f)
                    activeSolar = next
                }

                val remaining = (
                        (1f - solarProgress.value) *
                                ServicerSolarPovMotion.DURATION_MILLIS
                        ).toInt().coerceAtLeast(1)

                solarProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        remaining,
                        easing = LinearEasing
                    )
                )

                activeSolar = null
                solarProgress.snapTo(0f)
            }
        }

        /**
         * Independent fastener-operation queue.
         */
        val pendingScrews = remember(
            session.workOrderId
        ) {
            Channel<ServicerScrewOperation>(
                Channel.UNLIMITED
            )
        }

        val progress = remember(
            session.workOrderId
        ) {
            Animatable(0f)
        }

        var activeScrew by remember(
            session.workOrderId
        ) {
            mutableStateOf<ServicerScrewOperation?>(null)
        }

        /**
         * Detect individual fastener state changes.
         *
         * The repair simulator remains authoritative:
         * only successful screw actions enter this queue.
         */
        LaunchedEffect(session.workOrderId) {
            var previous =
                currentSession.loosenedScrews.toSet()

            snapshotFlow {
                currentSession.step to
                        currentSession.loosenedScrews.toSet()
            }.collect { (step, screwsNow) ->

                val actions = ServicerScrewMotion.detect(
                    before = previous,
                    after = screwsNow,
                    currentStep = step
                )

                actions.forEach { action ->
                    pendingScrews.trySend(action)
                }

                previous = screwsNow
            }
        }

        /**
         * Play each queued screwdriver action in order.
         *
         * During telemetry loss the current operation
         * retains its progress for later resumption.
         */
        LaunchedEffect(
            session.workOrderId,
            navigationReady
        ) {
            if (!navigationReady) {
                return@LaunchedEffect
            }

            while (true) {
                activeScrew ?: pendingScrews.receive().also {
                    progress.snapTo(0f)
                    activeScrew = it
                }

                val remaining = (
                        (1f - progress.value) *
                                ServicerScrewMotion.DURATION_MILLIS
                        ).toInt().coerceAtLeast(1)

                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remaining,
                        easing = LinearEasing
                    )
                )

                activeScrew = null
                progress.snapTo(0f)
            }
        }

        /**
         * Determine the current operator-facing camera activity.
         */
        val cameraStatus = when {
            !navigationReady ->
                "GUIDANCE HOLD"

            activeComponent != null -> {
                val operation = checkNotNull(activeComponent)

                "${operation.action.name.replace('_', ' ')} " +
                        operation.target.name.replace('_', ' ')
            }

            activeSolar != null -> {
                val operation = checkNotNull(activeSolar)

                val wingName =
                    if (
                        operation.wing ==
                        RepairHotspot.SOLAR_PANEL_A
                    ) {
                        "A"
                    } else {
                        "B"
                    }

                "${operation.action.name} SOLAR $wingName"
            }

            activeScrew != null -> {
                val operation = checkNotNull(activeScrew)

                val number = operation.screw.name
                    .removePrefix("SCREW_")

                if (
                    operation.direction ==
                    ServicerScrewDirection.REMOVE
                ) {
                    "UNSCREWING FASTENER $number"
                } else {
                    "SECURING FASTENER $number"
                }
            }

            session.step ==
                    RepairProcedureStep.SCAN_SOLAR_PANELS ->
                "SOLAR ARRAY A/B SCAN"

            session.step ==
                    RepairProcedureStep.SCAN_DISH ->
                "DISH FEED DIAGNOSTICS"

            session.step ==
                    RepairProcedureStep.REPAIR_IN_PLACE ->
                "RESTORING ${
                    session.target.name.replace('_', ' ')
                }"

            session.step ==
                    RepairProcedureStep.REMOVE_PART ->
                "EXTRACTING ${
                    session.target.name.replace('_', ' ')
                }"

            session.step ==
                    RepairProcedureStep.INSTALL_REPLACEMENT ->
                "INSTALLING ${
                    session.target.name.replace('_', ' ')
                }"

            else -> when (phase) {
                ServicerDockPhase.ORBITING ->
                    "FORMATION TRACKING"

                ServicerDockPhase.APPROACHING ->
                    "APPROACHING RAIL"

                ServicerDockPhase.MOVING ->
                    "REPOSITIONING"

                ServicerDockPhase.GRIPPED ->
                    session.step.displayName.uppercase()
            }
        }

        /**
         * Share only essential status information with
         * the readout displayed beside CAM-01.
         */
        val readout = ServicerPovReadout(
            activity = cameraStatus,
            tool = if (activeScrew != null) {
                "PRECISION SCREWDRIVER"
            } else {
                tool.displayName.uppercase()
            }
        )

        LaunchedEffect(readout) {
            latestOnReadoutChange(readout)
        }

        /**
         * GPU-rendered, non-interactive camera window.
         *
         * No Text components are drawn over the image.
         */
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF030812))
                .border(
                    1.dp,
                    Color(0xFF64748B),
                    RoundedCornerShape(9.dp)
                )
        ) {
            Scene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = loader,
                cameraNode = camera,
                childNodes = nodes,
                cameraManipulator = null,
                onTouchEvent = { _, _ -> true },
                onFrame = {
                    applyPovFrame(
                        camera = camera,
                        nodes = indexed,
                        screwdriverPivot = screwdriverPivot,
                        pose = currentPose,
                        session = currentSession,
                        tool = currentTool,
                        approach = currentApproach,
                        activeScrew = activeScrew,
                        screwProgress = progress.value,
                        activeComponent = activeComponent,
                        componentProgress = componentProgress.value,
                        activeSolar = activeSolar,
                        solarProgress = solarProgress.value,
                        cameraRollDegrees = currentCameraRoll,
                        currentFace = currentFace,
                        currentPhase = currentPhase,
                        canAnimateAtService =
                            currentReady &&
                                    currentPhase ==
                                    ServicerDockPhase.GRIPPED &&
                                    currentFace ==
                                    SatelliteServiceFace.SERVICE
                    )
                }
            )
        }
    }
}

/**
 * Loads one named GLB model from the assets directory.
 *
 * All onboard camera meshes are non-interactive.
 */
private fun glbNode(
    loader: ModelLoader,
    name: String,
    file: String,
    initialPosition: Position = Position(0f)
): ModelNode {
    return ModelNode(
        modelInstance = loader.createModelInstance(
            assetFileLocation = "models/$file"
        ),
        autoAnimate = false
    ).apply {
        this.name = name
        position = initialPosition
        isTouchable = false
    }
}

/**
 * Updates the onboard camera, spacecraft components,
 * robotic arms, and servicing tools every rendering frame.
 *
 * World-space repair targets are converted into camera-local
 * positions for the gripper and tool animations.
 */
private fun applyPovFrame(
    camera: CameraNode,
    nodes: Map<String, ModelNode>,
    screwdriverPivot: Node,
    pose: ServicerPovPose,
    session: RoboticRepairSession,
    tool: RepairTool,
    approach: Float,
    activeScrew: ServicerScrewOperation?,
    screwProgress: Float,
    activeComponent: ComponentPovOperation?,
    componentProgress: Float,
    activeSolar: SolarPovOperation?,
    solarProgress: Float,
    cameraRollDegrees: Float,
    currentFace: SatelliteServiceFace,
    currentPhase: ServicerDockPhase,
    canAnimateAtService: Boolean
) {
    /**
     * Move the onboard camera to its current world position.
     */
    camera.position = Position(
        pose.eyeX,
        pose.eyeY,
        pose.eyeZ
    )

    camera.lookAt(
        Position(
            pose.targetX,
            pose.targetY,
            pose.targetZ
        ),
        smooth = false
    )

    /**
     * Port solar servicing uses a forward-facing
     * 180-degree camera roll.
     */
    if (cameraRollDegrees != 0f) {
        val aligned = camera.rotation

        camera.rotation = Rotation(
            x = aligned.x,
            y = aligned.y,
            z = aligned.z + cameraRollDegrees
        )
    }

    /**
     * Animate the service cover and its hinge.
     */
    val coverOp = activeComponent?.takeIf {
        it.target == RepairHotspot.ACCESS_PANEL
    }

    val coverAngle =
        ServicerComponentPovMotion.coverAngle(
            coverOp,
            componentProgress,
            session.panelOpen
        )

    nodes["cover"]?.apply {
        position = Position(
            x = 0.52f,
            z = 0.55f
        )

        rotation = Rotation(
            y = coverAngle
        )
    }

    val solarJob =
        session.damage == EquipmentDamage.SOLAR_ARRAY_DAMAGE

    val dishJob =
        session.damage ==
                EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE

    val payloadJob =
        session.damage ==
                EquipmentDamage.PAYLOAD_SENSOR_DAMAGE

    val thermalJob =
        session.damage ==
                EquipmentDamage.THERMAL_RADIATOR_DAMAGE

    val damaged =
        session.partCondition == RepairPartCondition.DAMAGED

    val repaired =
        session.partCondition == RepairPartCondition.REPAIRED

    val replaced =
        session.partCondition == RepairPartCondition.REPLACED

    /**
     * The original generic module would obscure
     * the two independent internal components.
     */
    nodes["damaged"]?.isVisible = false
    nodes["healthy"]?.isVisible = false

    val partOp = activeComponent?.takeIf {
        it.target != RepairHotspot.ACCESS_PANEL
    }

    val detachActive =
        partOp?.action == ComponentPovAction.DETACH

    val installActive =
        partOp?.action == ComponentPovAction.INSTALL

    val repairActive =
        partOp?.action == ComponentPovAction.REPAIR

    val visualRepairDone =
        !repairActive || componentProgress > 0.72f

    val partOffset = partOp?.let {
        ServicerComponentPovMotion.partOffset(
            it,
            componentProgress
        )
    }

    /**
     * Communications dish.
     *
     * The old dish leaves its mount during removal;
     * the replacement approaches the empty mount.
     */
    val dishRoot = Position(
        0f,
        1.27f,
        0.19f
    )

    val dishDetach =
        dishJob &&
                detachActive &&
                partOp?.target ==
                RepairHotspot.COMMUNICATIONS_DISH

    val dishInstall =
        dishJob &&
                installActive &&
                partOp?.target ==
                RepairHotspot.COMMUNICATIONS_DISH

    val dishRepair =
        dishJob &&
                repairActive &&
                partOp?.target ==
                RepairHotspot.COMMUNICATIONS_DISH

    nodes["dish_bad"]?.apply {
        isVisible =
            dishJob &&
                    session.inspectedDish &&
                    (
                            damaged ||
                                    (
                                            dishDetach &&
                                                    componentProgress < 0.84f
                                            ) ||
                                    (
                                            dishRepair &&
                                                    !visualRepairDone
                                            )
                            )

        val offset =
            if (dishDetach) partOffset else null

        position = Position(
            dishRoot.x + (offset?.x ?: 0f),
            dishRoot.y + (offset?.y ?: 0f),
            dishRoot.z + (offset?.z ?: 0f)
        )
    }

    nodes["dish_good"]?.apply {
        isVisible =
            !dishJob ||
                    !session.inspectedDish ||
                    (repaired && visualRepairDone) ||
                    replaced ||
                    dishInstall

        val offset =
            if (dishInstall) partOffset else null

        position = Position(
            dishRoot.x + (offset?.x ?: 0f),
            dishRoot.y + (offset?.y ?: 0f),
            dishRoot.z + (offset?.z ?: 0f)
        )
    }

    /**
     * Separate payload and thermal modules.
     *
     * The neighboring healthy module stays in place
     * while the damaged component is serviced.
     */
    for (
    (name, moduleTarget, moduleX) in listOf(
        Triple(
            "payload",
            RepairHotspot.PAYLOAD_SENSOR,
            -0.25f
        ),
        Triple(
            "thermal",
            RepairHotspot.THERMAL_CONTROLLER,
            0.25f
        )
    )
    ) {
        val selected =
            (
                    moduleTarget ==
                            RepairHotspot.PAYLOAD_SENSOR &&
                            payloadJob
                    ) ||
                    (
                            moduleTarget ==
                                    RepairHotspot.THERMAL_CONTROLLER &&
                                    thermalJob
                            )

        val activeForModule =
            partOp?.target == moduleTarget

        val takingOut =
            activeForModule && detachActive

        val puttingIn =
            activeForModule && installActive

        val fixing =
            activeForModule && repairActive

        val offset =
            if (takingOut) partOffset else null

        nodes["${name}_module"]?.apply {
            isVisible =
                session.panelOpen &&
                        (
                                !selected ||
                                        (
                                                takingOut &&
                                                        componentProgress < 0.84f
                                                ) ||
                                        (
                                                fixing &&
                                                        !visualRepairDone
                                                ) ||
                                        (
                                                damaged &&
                                                        !puttingIn
                                                ) ||
                                        (
                                                repaired &&
                                                        !puttingIn
                                                )
                                )

            position = Position(
                moduleX + (offset?.x ?: 0f),
                offset?.y ?: 0f,
                0.50f + (offset?.z ?: 0f)
            )
        }

        nodes["${name}_replacement"]?.apply {
            isVisible =
                session.panelOpen &&
                        selected &&
                        (replaced || puttingIn)

            val replacementOffset =
                if (puttingIn) partOffset else null

            position = Position(
                moduleX +
                        (replacementOffset?.x ?: 0f),
                replacementOffset?.y ?: 0f,
                0.50f +
                        (replacementOffset?.z ?: 0f)
            )
        }

        nodes["${name}_fault"]?.isVisible =
            session.panelOpen &&
                    selected &&
                    damaged &&
                    session.inspectedInternalModule
    }

    /**
     * Contact spark during in-place component servicing.
     */
    nodes["service_spark"]?.apply {
        isVisible =
            partOp != null &&
                    ServicerComponentPovMotion.sparkVisible(
                        partOp,
                        componentProgress
                    )

        if (partOp != null) {
            val point =
                ServicerComponentPovMotion.targetPoint(
                    partOp.target
                )

            position = Position(
                point.x,
                point.y,
                point.z
            )

            rotation = Rotation(
                z = 580f * componentProgress
            )
        }
    }

    /**
     * Display a fault marker on the diagnosed solar wing.
     */
    nodes["solar_fault_a"]?.isVisible =
        solarJob &&
                damaged &&
                session.faultySolarPanel ==
                RepairHotspot.SOLAR_PANEL_A &&
                RepairHotspot.SOLAR_PANEL_A in
                session.scannedSolarPanels

    nodes["solar_fault_b"]?.isVisible =
        solarJob &&
                damaged &&
                session.faultySolarPanel ==
                RepairHotspot.SOLAR_PANEL_B &&
                RepairHotspot.SOLAR_PANEL_B in
                session.scannedSolarPanels

    /**
     * True 3D detachable solar wings.
     *
     * Original wings and replacements are separate meshes,
     * not overlays drawn over baked bus geometry.
     */
    val physicalSolarOperation = activeSolar?.takeIf {
        solarJob &&
                ServicerSolarPovMotion.isSolarFace(currentFace)
    }

    for (
    (name, wing) in listOf(
        "a" to RepairHotspot.SOLAR_PANEL_A,
        "b" to RepairHotspot.SOLAR_PANEL_B
    )
    ) {
        val affected =
            solarJob &&
                    session.faultySolarPanel == wing

        val working = physicalSolarOperation?.takeIf {
            affected && it.wing == wing
        }

        val root =
            ServicerSolarPovMotion.rootX(wing)

        val originalVisible = when {
            !affected ->
                true

            working?.action == SolarPovAction.DETACH ->
                solarProgress < 0.94f

            working?.action == SolarPovAction.REPAIR ->
                true

            else ->
                session.partCondition ==
                        RepairPartCondition.DAMAGED ||
                        session.partCondition ==
                        RepairPartCondition.REPAIRED
        }

        val replacementVisible = when {
            !affected ->
                false

            working?.action == SolarPovAction.INSTALL ->
                true

            else ->
                session.partCondition ==
                        RepairPartCondition.REPLACED
        }

        nodes["wing_original_$name"]?.apply {
            isVisible = originalVisible

            val offset =
                if (
                    working?.action ==
                    SolarPovAction.DETACH
                ) {
                    ServicerSolarPovMotion.wingOffset(
                        SolarPovAction.DETACH,
                        wing,
                        solarProgress
                    )
                } else {
                    com.example.orbitalcommand.model
                        .SatellitePoint3D(0f, 0f, 0f)
                }

            position = Position(
                root + offset.x,
                offset.y,
                offset.z
            )
        }

        nodes["wing_replacement_$name"]?.apply {
            isVisible = replacementVisible

            val offset =
                if (
                    working?.action ==
                    SolarPovAction.INSTALL
                ) {
                    ServicerSolarPovMotion.wingOffset(
                        SolarPovAction.INSTALL,
                        wing,
                        solarProgress
                    )
                } else {
                    com.example.orbitalcommand.model
                        .SatellitePoint3D(0f, 0f, 0f)
                }

            position = Position(
                root + offset.x,
                offset.y,
                offset.z
            )
        }
    }

    /**
     * Solar connector spark.
     */
    nodes["solar_spark"]?.apply {
        val operation = physicalSolarOperation

        isVisible =
            operation != null &&
                    ServicerSolarPovMotion.sparkVisible(
                        operation.action,
                        solarProgress
                    )

        if (operation != null) {
            val root =
                ServicerSolarPovMotion.wingConnector(
                    operation.wing
                )

            position = Position(
                root.x,
                root.y,
                root.z
            )

            rotation = Rotation(
                z = solarProgress * 620f
            )
        }
    }

    /**
     * Left arm: physical rail contact.
     *
     * Gripper contact follows an anchor in satellite
     * coordinates as the camera slides or rotates.
     *
     * Rail grip is retained during communication loss.
     */
    val retreat = (
            1f - approach.coerceIn(0f, 1f)
            ) * 0.48f

    val gripPoint =
        ServicerRailContact.anchor(currentFace)

    val localGrip =
        camera.getLocalPosition(
            Position(
                gripPoint.x,
                gripPoint.y,
                gripPoint.z
            )
        )

    val gripAmount = when (currentPhase) {
        ServicerDockPhase.ORBITING ->
            0f

        ServicerDockPhase.APPROACHING ->
            approach.coerceIn(0f, 1f)

        ServicerDockPhase.GRIPPED,
        ServicerDockPhase.MOVING ->
            1f
    }

    val homeLeft = Position(
        -0.37f,
        -0.30f,
        -1.56f + retreat
    )

    nodes["arm_left"]?.position = Position(
        x =
            (localGrip.x - homeLeft.x) *
                    gripAmount,
        y =
            (localGrip.y - homeLeft.y) *
                    gripAmount,
        z =
            retreat +
                    (localGrip.z - homeLeft.z) *
                    gripAmount
    )

    nodes["rail_clamp"]?.apply {
        isVisible =
            currentPhase ==
                    ServicerDockPhase.GRIPPED ||
                    currentPhase ==
                    ServicerDockPhase.MOVING

        position = Position(
            gripPoint.x,
            gripPoint.y,
            gripPoint.z
        )

        rotation = Rotation(
            z = if (
                currentFace ==
                SatelliteServiceFace.DORSAL ||
                currentFace ==
                SatelliteServiceFace.VENTRAL
            ) {
                90f
            } else {
                0f
            }
        )
    }

    /**
     * Screwdriver animation is allowed only
     * when servicing the accessible service face.
     */
    val operation = activeScrew?.takeIf {
        canAnimateAtService
    }

    val armReach = if (operation != null) {
        ServicerScrewMotion.reach(
            screwProgress
        )
    } else {
        0f
    }

    val homeTip = Position(
        x = 0.37f,
        y = -0.28f,
        z = -1.86f + retreat
    )

    /**
     * Convert the selected screw position from
     * satellite coordinates to camera-local coordinates.
     */
    val targetTip = if (operation != null) {
        val point =
            ServicerScrewMotion.screwPosition(
                operation.screw
            )

        val backout =
            ServicerScrewMotion.screwBackout(
                operation,
                screwProgress
            )

        camera.getLocalPosition(
            Position(
                point.x,
                point.y,
                point.z + 0.08f +
                        0.22f * backout
            )
        )
    } else {
        homeTip
    }

    /**
     * Move the bit smoothly toward the selected fastener.
     */
    val bitPosition = Position(
        x = lerp(
            homeTip.x,
            targetTip.x,
            armReach
        ),
        y = lerp(
            homeTip.y,
            targetTip.y,
            armReach
        ),
        z = lerp(
            homeTip.z,
            targetTip.z,
            armReach
        )
    )

    val shiftX =
        bitPosition.x - homeTip.x

    val shiftY =
        bitPosition.y - homeTip.y

    val shiftZ =
        bitPosition.z - homeTip.z

    /**
     * Move the free/right arm to the active repair target.
     *
     * Component operations take priority over solar and
     * screwdriver animations.
     */
    val solarReach = physicalSolarOperation?.let {
        ServicerSolarPovMotion.armReach(
            solarProgress
        )
    } ?: 0f

    if (activeComponent != null) {
        val hand =
            ServicerComponentPovMotion.handTarget(
                activeComponent,
                componentProgress
            )

        val target = camera.getLocalPosition(
            Position(
                hand.x,
                hand.y,
                hand.z
            )
        )

        val homeJaw = Position(
            0.37f,
            -0.30f,
            -1.56f + retreat
        )

        val reach =
            ServicerComponentPovMotion.armReach(
                componentProgress
            )

        nodes["arm_right"]?.position = Position(
            x =
                (target.x - homeJaw.x) * reach,
            y =
                (target.y - homeJaw.y) * reach,
            z =
                retreat +
                        (target.z - homeJaw.z) * reach
        )

    } else if (physicalSolarOperation != null) {
        val wing =
            physicalSolarOperation.wing

        val anchor =
            ServicerSolarPovMotion.wingConnector(
                wing
            )

        val offset =
            ServicerSolarPovMotion.wingOffset(
                physicalSolarOperation.action,
                wing,
                solarProgress
            )

        val target = camera.getLocalPosition(
            Position(
                anchor.x + offset.x,
                anchor.y + offset.y,
                anchor.z + offset.z
            )
        )

        val homeJaw = Position(
            0.37f,
            -0.30f,
            -1.56f + retreat
        )

        nodes["arm_right"]?.position = Position(
            x =
                (target.x - homeJaw.x) *
                        solarReach,
            y =
                (target.y - homeJaw.y) *
                        solarReach,
            z =
                retreat +
                        (target.z - homeJaw.z) *
                        solarReach
        )

    } else {
        nodes["arm_right"]?.position = Position(
            shiftX,
            shiftY,
            retreat + shiftZ
        )
    }

    /**
     * Spin the screwdriver around the bit pivot.
     */
    screwdriverPivot.position = bitPosition

    screwdriverPivot.rotation = Rotation(
        z = if (operation != null) {
            ServicerScrewMotion.screwdriverDegrees(
                operation,
                screwProgress
            )
        } else {
            0f
        }
    )

    nodes["tool_screwdriver"]?.isVisible =
        activeComponent == null &&
                physicalSolarOperation == null &&
                (
                        operation != null ||
                                tool == RepairTool.SCREWDRIVER
                        )

    /**
     * Select the correct tool for the active operation.
     */
    val otherTool =
        if (activeComponent != null) {
            when (activeComponent.action) {
                ComponentPovAction.SCAN ->
                    "scanner"

                ComponentPovAction.DETACH ->
                    "extractor"

                ComponentPovAction.INSTALL ->
                    "installer"

                ComponentPovAction.REPAIR ->
                    "repair_kit"

                ComponentPovAction.OPEN_COVER,
                ComponentPovAction.CLOSE_COVER ->
                    null
            }
        } else if (physicalSolarOperation != null) {
            when (physicalSolarOperation.action) {
                SolarPovAction.DETACH ->
                    "extractor"

                SolarPovAction.INSTALL ->
                    "installer"

                SolarPovAction.REPAIR ->
                    "repair_kit"
            }
        } else if (operation != null) {
            null
        } else {
            when (tool) {
                RepairTool.SCANNER ->
                    "scanner"

                RepairTool.EXTRACTOR ->
                    "extractor"

                RepairTool.REPAIR_KIT ->
                    "repair_kit"

                RepairTool.INSTALLER ->
                    "installer"

                else ->
                    null
            }
        }

    for (
    name in listOf(
        "scanner",
        "extractor",
        "repair_kit",
        "installer"
    )
    ) {
        nodes["tool_$name"]?.apply {
            isVisible = name == otherTool

            position = if (
                activeComponent != null ||
                physicalSolarOperation != null
            ) {
                nodes["arm_right"]?.position
                    ?: Position(z = retreat)
            } else {
                Position(z = retreat)
            }
        }
    }

    /**
     * Render four independently movable screws.
     *
     * Selected screws rotate and back away from
     * the panel, then disappear after extraction.
     */
    for (screw in ServicerScrewMotion.screws) {
        val screwPosition =
            ServicerScrewMotion.screwPosition(
                screw
            )

        val selected =
            operation?.screw == screw

        val backout = if (selected) {
            ServicerScrewMotion.screwBackout(
                operation!!,
                screwProgress
            )
        } else {
            0f
        }

        nodes["fastener_${screw.name}"]?.apply {
            isVisible = if (selected) {
                operation!!.direction ==
                        ServicerScrewDirection.INSTALL ||
                        screwProgress < 0.80f
            } else {
                screw !in session.loosenedScrews
            }

            position = Position(
                screwPosition.x,
                screwPosition.y,
                screwPosition.z +
                        0.22f * backout
            )

            rotation = Rotation(
                z = if (selected) {
                    ServicerScrewMotion.screwdriverDegrees(
                        operation!!,
                        screwProgress
                    )
                } else {
                    0f
                }
            )
        }
    }
}

/**
 * Linear interpolation between two camera-local coordinates.
 */
private fun lerp(
    from: Float,
    to: Float,
    fraction: Float
): Float {
    return from +
            (to - from) *
            fraction.coerceIn(0f, 1f)
}
