
package com.example.orbitalcommand.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orbitalcommand.model.EquipmentDamage
import com.example.orbitalcommand.model.ServicerSolarPovMotion
import com.example.orbitalcommand.model.RepairHotspot
import com.example.orbitalcommand.model.RoboticRepairSession
import com.example.orbitalcommand.model.SatelliteCamera
import com.example.orbitalcommand.model.SatelliteServiceFace
import com.example.orbitalcommand.model.SatelliteViewAlignment
import com.example.orbitalcommand.model.ServicerAttachmentRegistry
import com.example.orbitalcommand.model.ServicerDockPhase
import com.example.orbitalcommand.model.ServicerDockingController
import com.example.orbitalcommand.model.ServicerDockingState
import com.example.orbitalcommand.model.ServicerPovRig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Displays a procedural, touch-interactive service model above a
 * separate GPU-rendered onboard camera. The camera inset never
 * obstructs repair hotspots on the procedural satellite.
 *
 * Docking is a PHYSICAL attachment state, distinct from the orbital
 * guidance state and distinct from docking at station OS-001.
 *
 * The parent panel may still own the local Dock/Move UI state.
 * This component publishes every confirmed transition to the
 * mission-wide attachment registry, enabling navigation and the
 * orbital map to preserve rail grip during communication loss.
 */
@Composable
fun ServicerDockingStage(
    satelliteId: String,
    session: RoboticRepairSession,
    docking: ServicerDockingState,
    navigationReady: Boolean,
    repairEnabled: Boolean,
    onDockingChange: (ServicerDockingState) -> Unit,
    onRepairHotspot: (RepairHotspot) -> Unit
) {
    /**
     * Rebuild the onboard camera pose from the restored docking phase and face.
     * The physical attachment can survive process death independently of the
     * Compose Animatable objects, so initializing everything at Service/0f
     * would incorrectly make a rail-gripped servicer look undocked.
     */
    val solarJob = session.damage == EquipmentDamage.SOLAR_ARRAY_DAMAGE
    val restoredFace = docking.activeFace
    val restoredSolarFace = solarJob &&
        ServicerSolarPovMotion.isSolarFace(restoredFace)

    var operatorCamera by remember(docking.missionOrderId) {
        mutableStateOf(
            SatelliteViewAlignment.operatorCameraFor(
                if (restoredSolarFace) SatelliteServiceFace.SERVICE
                else restoredFace
            ).let { initial ->
                if (restoredSolarFace) {
                    initial.copy(yawDegrees = 0f, pitchDegrees = 0f)
                } else {
                    initial.copy(
                        yawDegrees = SatelliteViewAlignment.operatorYawFor(restoredFace),
                        pitchDegrees = restoredFace.viewPitch
                    )
                }
            }
        )
    }

    val yaw = remember(docking.missionOrderId) {
        Animatable(
            if (solarJob) ServicerSolarPovMotion.targetYaw(restoredFace)
            else restoredFace.viewYaw
        )
    }
    val pitch = remember(docking.missionOrderId) {
        Animatable(
            if (solarJob) ServicerSolarPovMotion.targetPitch(restoredFace)
            else restoredFace.viewPitch
        )
    }

    /**
     * Restore the solar slide and Port's 180-degree roll as well as direction.
     */
    val solarSlide = remember(docking.missionOrderId) {
        Animatable(
            if (solarJob) ServicerSolarPovMotion.cameraSlide(restoredFace)
            else 0f
        )
    }
    val solarRoll = remember(docking.missionOrderId) {
        Animatable(
            if (solarJob) ServicerSolarPovMotion.cameraRoll(restoredFace)
            else 0f
        )
    }

    /**
     * Critical restart fix: a restored grip is already at the rail.
     * `ServicerPovRig.pose()` uses approachAmount=0 for an undocked standoff
     * and approachAmount=1 for the close docked camera position.
     */
    val approach = remember(docking.missionOrderId) {
        Animatable(if (docking.grippingRail) 1f else 0f)
    }
    val transit = remember(docking.missionOrderId, docking.movementNumber) {
        Animatable(0f)
    }

    val latestDocking by rememberUpdatedState(docking)

    val latestNavigationReady by rememberUpdatedState(
        navigationReady
    )

    val latestOnDockingChange by rememberUpdatedState(
        onDockingChange
    )

    /**
     * Initialize the shared attachment record without
     * overwriting an existing confirmed rail grip.
     */
    LaunchedEffect(docking.missionOrderId, satelliteId) {
        ServicerAttachmentRegistry.prepareMission(
            workOrderId = docking.missionOrderId,
            satelliteId = satelliteId
        )
    }

    /**
     * Synchronize physical docking state with mission navigation.
     * Communication loss does not release the attachment.
     */
    LaunchedEffect(docking, satelliteId) {
        ServicerAttachmentRegistry.prepareMission(
            workOrderId = docking.missionOrderId,
            satelliteId = satelliteId
        )

        ServicerAttachmentRegistry.update(docking)
    }

    /**
     * Approach the satellite's service rail.
     *
     * Animation progress is preserved if guidance is
     * interrupted during the approach.
     */
    LaunchedEffect(
        docking.phase,
        navigationReady
    ) {
        when {
            docking.phase == ServicerDockPhase.ORBITING -> {
                approach.snapTo(0f)
            }

            docking.phase == ServicerDockPhase.APPROACHING &&
                    navigationReady -> {

                approach.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(1800)
                )

                latestOnDockingChange(
                    ServicerDockingController.finishDock(
                        latestDocking,
                        latestNavigationReady
                    )
                )
            }
        }
    }

    /**
     * Reposition SERVICER-01 while preserving its physical rail grip.
     *
     * During solar repair:
     * - Starboard translates toward the right-hand wing.
     * - Port translates toward the opposite wing and rolls 180 degrees.
     *
     * Other repairs retain the existing six-sided camera maneuver.
     * Loss of navigation pauses repositioning without releasing grip.
     */
    LaunchedEffect(
        docking.phase,
        docking.movementNumber,
        navigationReady
    ) {
        if (
            docking.phase == ServicerDockPhase.MOVING &&
            navigationReady
        ) {
            val destination = docking.targetFace

            val slideTarget = if (solarJob) {
                ServicerSolarPovMotion.cameraSlide(destination)
            } else {
                0f
            }

            val rollTarget = if (solarJob) {
                ServicerSolarPovMotion.cameraRoll(destination)
            } else {
                0f
            }

            val yawTarget = if (solarJob) {
                ServicerSolarPovMotion.targetYaw(destination)
            } else {
                destination.viewYaw
            }

            val pitchTarget = if (solarJob) {
                ServicerSolarPovMotion.targetPitch(destination)
            } else {
                destination.viewPitch
            }

            coroutineScope {
                launch {
                    yaw.animateTo(
                        shortestYawTarget(
                            current = yaw.value,
                            target = yawTarget
                        ),
                        tween(2200)
                    )
                }

                launch {
                    pitch.animateTo(
                        pitchTarget,
                        tween(2200)
                    )
                }

                launch {
                    solarSlide.animateTo(
                        slideTarget,
                        tween(2200)
                    )
                }

                launch {
                    solarRoll.animateTo(
                        rollTarget,
                        tween(2200)
                    )
                }

                launch {
                    transit.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(2200)
                    )
                }
            }

            latestOnDockingChange(
                ServicerDockingController.finishMove(
                    latestDocking,
                    latestNavigationReady
                )
            )
        }
    }

    /**
     * Preserve the established Port/Starboard yaw convention.
     *
     * During solar repairs, the procedural model remains broadside
     * so both solar wings are accessible to the operator.
     */
    LaunchedEffect(docking.movementNumber) {
        if (docking.movementNumber > 0) {
            val solarFace = solarJob &&
                    ServicerSolarPovMotion.isSolarFace(
                        docking.targetFace
                    )

            operatorCamera = operatorCamera.copy(
                yawDegrees = if (solarFace) {
                    0f
                } else {
                    SatelliteViewAlignment.operatorYawFor(
                        docking.targetFace
                    )
                },
                pitchDegrees = if (solarFace) {
                    0f
                } else {
                    docking.targetFace.viewPitch
                }
            )
        }
    }

    /**
     * Calculate the onboard camera's orbital pose.
     */
    val orbitalPose = ServicerPovRig.pose(
        yawDegrees = yaw.value,
        pitchDegrees = pitch.value,
        approachAmount = approach.value,
        transitAmount = transit.value
    )

    /**
     * Apply solar-wing camera translation without
     * changing the satellite's orbital simulation.
     */
    val cameraPose = if (solarJob) {
        orbitalPose.copy(
            eyeX = orbitalPose.eyeX + solarSlide.value,
            targetX = orbitalPose.targetX + solarSlide.value
        )
    } else {
        orbitalPose
    }

    val displayedFace = if (
        docking.phase == ServicerDockPhase.MOVING
    ) {
        docking.targetFace
    } else {
        docking.activeFace
    }

    /**
     * The POV camera reports the current physical repair action.
     *
     * The selected tool is read directly from the repair session,
     * ensuring that the displayed tool matches the operator's choice.
     */
    var povReadout by remember(docking.missionOrderId) {
        mutableStateOf(
            ServicerPovReadout(
                activity = "STANDBY",
                tool = session.tool.displayName.uppercase()
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF091422))
        ) {
            /**
             * Main interactive 3D satellite visualization.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            ) {
                Satellite3DScene(
                    satelliteId = satelliteId,
                    session = session,
                    enabled = repairEnabled &&
                            !docking.choosingFace,
                    camera = operatorCamera,
                    onCameraChange = {
                        operatorCamera = it
                    },
                    onHotspot = onRepairHotspot
                )

                /**
                 * Allow the operator to select a new satellite face
                 * when the servicer enters the movement-selection mode.
                 */
                if (docking.choosingFace) {
                    SatelliteFaceSelectionOverlay(
                        selected = docking.activeFace,
                        onSelect = { chosen ->
                            latestOnDockingChange(
                                ServicerDockingController.selectFace(
                                    docking,
                                    chosen,
                                    navigationReady
                                )
                            )
                        }
                    )
                }
            }

            /**
             * Separate POV camera bay below the interactive satellite.
             *
             * The camera feed occupies the left side.
             * Vehicle and repair status occupy the right side.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 7.4f)
                    .background(Color(0xFF07111F))
            ) {
                ServicerPovCamera(
                    satelliteId = satelliteId,
                    session = session,
                    face = displayedFace,
                    phase = docking.phase,
                    tool = session.tool,
                    pose = cameraPose,
                    approachAmount = approach.value,
                    navigationReady = navigationReady,
                    cameraRollDegrees = if (solarJob) {
                        solarRoll.value
                    } else {
                        0f
                    },
                    onReadoutChange = { readout ->
                        povReadout = readout
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .fillMaxWidth(0.48f)
                        .aspectRatio(16f / 10f)
                )

                /**
                 * Non-obstructive SERVICER-01 information column.
                 *
                 * All readout text appears to the right of CAM-01
                 * instead of covering the 3D camera footage.
                 */
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(0.50f)
                        .padding(
                            start = 4.dp,
                            end = 8.dp
                        ),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    /**
                     * Servicing vehicle identification.
                     */
                    Text(
                        text = "SERVICER-01",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    /**
                     * Camera identifier and current satellite face.
                     */
                    Text(
                        text = "CAM-01  •  " +
                                displayedFace.displayName.uppercase(),
                        color = Color(0xFF67E8F9),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    /**
                     * Physical rail-grip status.
                     */
                    Text(
                        text = when {
                            docking.grippingRail ->
                                "GRIP  RAIL SECURED"

                            docking.phase ==
                                    ServicerDockPhase.APPROACHING ->
                                "GRIP  APPROACHING"

                            else ->
                                "GRIP  NOT ATTACHED"
                        },
                        color = if (docking.grippingRail) {
                            Color(0xFF86EFAC)
                        } else {
                            Color(0xFF94A3B8)
                        },
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    /**
                     * CURRENTLY SELECTED TOOL
                     *
                     * Replaces the previous
                     * "CURRENT STEP / ACTION" heading.
                     *
                     * The name updates automatically when the
                     * operator changes tools in the repair interface.
                     */
                    Text(
                        text = session.tool.displayName.uppercase(),
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    /**
                     * Live action performed by the robotic system.
                     *
                     * Examples:
                     * - UNSCREWING FASTENER 2
                     * - INSTALL SOLAR A
                     * - FORMATION TRACKING
                     *
                     * This remains underneath the selected tool
                     * without displaying an additional heading.
                     */
                    Text(
                        text = povReadout.activity,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    /**
                     * Communication and tracking status.
                     */
                    Text(
                        text = if (navigationReady) {
                            "LINK  TRACKING"
                        } else {
                            "LINK  LOST / PAUSED"
                        },
                        color = if (navigationReady) {
                            Color(0xFF9FE8D8)
                        } else {
                            Color(0xFFFBBF24)
                        },
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        /**
         * Docking and movement controls.
         *
         * Commands are disabled when navigation is unavailable,
         * but existing physical rail attachment is preserved.
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    latestOnDockingChange(
                        ServicerDockingController.beginDock(
                            docking,
                            navigationReady
                        )
                    )
                },
                enabled = navigationReady &&
                        docking.phase ==
                        ServicerDockPhase.ORBITING,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    when (docking.phase) {
                        ServicerDockPhase.ORBITING ->
                            "Dock"

                        ServicerDockPhase.APPROACHING ->
                            "Approaching…"

                        ServicerDockPhase.GRIPPED,
                        ServicerDockPhase.MOVING ->
                            "Rail gripped"
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    latestOnDockingChange(
                        ServicerDockingController.toggleMove(
                            docking,
                            navigationReady
                        )
                    )
                },
                enabled = navigationReady &&
                        docking.phase ==
                        ServicerDockPhase.GRIPPED,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (docking.choosingFace) {
                        "Cancel Move"
                    } else {
                        "Move"
                    }
                )
            }
        }

        /**
         * Detailed docking and guidance information below
         * the onboard camera and its readout.
         */
        val status = when {
            !navigationReady && docking.grippingRail ->
                "LINK LOST • RAIL GRIP RETAINED. The servicer " +
                        "continues with the satellite's simulated orbit. " +
                        "Repair and movement commands are paused."

            !navigationReady ->
                "Guidance unavailable. Undocked navigation is holding " +
                        "until telemetry and formation recover."

            docking.phase == ServicerDockPhase.ORBITING ->
                "SERVICER-01 is following the satellite " +
                        "at formation distance."

            docking.phase == ServicerDockPhase.APPROACHING ->
                "Approaching the satellite and aligning with the rail."

            docking.phase == ServicerDockPhase.MOVING ->
                "Repositioning toward the " +
                        "${docking.targetFace.displayName} face."

            docking.choosingFace ->
                "Select a satellite face to reposition SERVICER-01."

            docking.activeFace == SatelliteServiceFace.SERVICE ->
                "Rail grip confirmed at Service. Internal maintenance " +
                        "and diagnostics available."

            else ->
                "Rail grip confirmed at " +
                        "${docking.activeFace.displayName}. " +
                        "Move to the face specified by the current repair step."
        }

        Text(
            text = status,
            color = if (navigationReady) {
                Color(0xFFCBD5E1)
            } else {
                Color(0xFFFBBF24)
            },
            fontSize = 11.sp
        )
    }
}

/**
 * Chooses the shortest angular movement toward the new
 * satellite service face.
 */
private fun shortestYawTarget(
    current: Float,
    target: Float
): Float {
    var delta = (target - current) % 360f

    if (delta > 180f) {
        delta -= 360f
    }

    if (delta < -180f) {
        delta += 360f
    }

    return current + delta
}

/**
 * Displays selectable satellite-face destinations over
 * the interactive procedural satellite model.
 */
@Composable
private fun SatelliteFaceSelectionOverlay(
    selected: SatelliteServiceFace,
    onSelect: (SatelliteServiceFace) -> Unit
) {
    val anchors = listOf(
        Triple(SatelliteServiceFace.SERVICE, 0.50f, 0.48f),
        Triple(SatelliteServiceFace.PORT, 0.20f, 0.47f),
        Triple(SatelliteServiceFace.STARBOARD, 0.78f, 0.47f),
        Triple(SatelliteServiceFace.DORSAL, 0.50f, 0.20f),
        Triple(SatelliteServiceFace.VENTRAL, 0.50f, 0.78f),
        Triple(SatelliteServiceFace.AFT, 0.79f, 0.79f)
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        anchors.forEach { (face, fx, fy) ->
            val horizontalLimit =
                (maxWidth - 72.dp).coerceAtLeast(0.dp)

            val verticalLimit =
                (maxHeight - 34.dp).coerceAtLeast(0.dp)

            val left = (
                    maxWidth * fx - 36.dp
                    ).coerceIn(
                    0.dp,
                    horizontalLimit
                )

            val top = (
                    maxHeight * fy - 17.dp
                    ).coerceIn(
                    0.dp,
                    verticalLimit
                )

            Button(
                onClick = {
                    onSelect(face)
                },
                modifier = Modifier
                    .offset(
                        x = left,
                        y = top
                    )
                    .width(72.dp)
                    .height(34.dp),
                contentPadding = PaddingValues(1.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (face == selected) {
                        Color(0xFF4ADE80)
                    } else {
                        Color(0xFFFBBF24)
                    },
                    contentColor = Color(0xFF0B1220)
                )
            ) {
                Text(
                    text = face.displayName,
                    fontSize = 10.sp
                )
            }
        }
    }
}
