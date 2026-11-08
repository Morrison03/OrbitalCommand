
package com.example.orbitalcommand.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orbitalcommand.model.OrbitalDisplayGeometry
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairWorkOrder
import com.example.orbitalcommand.model.ServicingVehiclePhase
import com.example.orbitalcommand.ui.theme.OrbitalColors
import com.example.orbitalcommand.simulation.ServicingVehicleNavigation
import java.util.Locale

/**
 * Displays servicing station status and operator
 * controls for preparing and deploying a repair vehicle.
 *
 * The current navigation system is a simplified
 * scripted simulation, not physical spacecraft guidance.
 */
@Composable
fun ServicingMissionPanel(
    state: OrbitalUiState,
    onPrepareMission: (Long) -> Unit,
    onCancelMission: () -> Unit,
    onDispatchVehicle: () -> Unit,
    onBeginApproach: () -> Unit,
    onHoldVehicle: () -> Unit,
    onResumeApproach: () -> Unit,
    onReturnVehicle: () -> Unit,
    onSelectSatellite: (String) -> Unit
) {

    val station = state.servicingStation
    val vehicle = state.servicingVehicle
    val plan = state.preparedMission

    val queuedOrders = state.activeRepairOrders.filter {
        it.status == RepairStatus.QUEUED
    }

    val target = state.satellites.firstOrNull {
        it.definition.id == plan?.targetSatelliteId
    }

    val targetVerified = target?.let {
        ServicingVehicleNavigation.hasCurrentTargetTelemetry(
            it,
            state.elapsedSeconds
        )
    } == true

    val dispatchReady = plan?.let {
        ServicingVehicleNavigation.isDispatchReady(
            plan = it,
            orders = state.repairOrders,
            satellites = state.satellites,
            atSecond = state.elapsedSeconds
        )
    } == true

    val vehicleColor = when (vehicle.phase) {

        ServicingVehiclePhase.DOCKED ->
            OrbitalColors.Healthy

        ServicingVehiclePhase.DEPLOYED,
        ServicingVehiclePhase.APPROACHING ->
            OrbitalColors.Accent

        ServicingVehiclePhase.HOLDING ->
            Color(0xFFFBBF24)

        ServicingVehiclePhase.RETURNING ->
            Color(0xFFA78BFA)
    }

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
                text = "ORBITAL SERVICING OPERATIONS",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${station.id} • ${station.name}",
                color = OrbitalColors.Accent,
                fontSize = 13.sp
            )

            Text(
                text = "SERVICING VEHICLE",
                color = OrbitalColors.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = vehicle.id,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = vehicle.phase.displayName.uppercase(),
                    color = vehicleColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            DetailRow(
                label = "Station orbit angle",
                value = String.format(
                    Locale.US,
                    "%.0f°",
                    station.orbitAngleDegrees
                )
            )

            DetailRow(
                label = "Queued work orders",
                value = queuedOrders.size.toString()
            )

            if (vehicle.holdReason != null) {

                Text(
                    text = "HOLD REASON: " +
                            vehicle.holdReason.displayName,
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            if (plan == null) {

                Text(
                    text = "NO MISSION PREPARED",
                    color = OrbitalColors.Muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                Text(
                    text = "Select a queued repair work order " +
                            "below to prepare a servicing mission.",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )

            } else {

                Text(
                    text = "CURRENT MISSION",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                DetailRow(
                    label = "Work order",
                    value = "#${plan.workOrderId}"
                )

                DetailRow(
                    label = "Target satellite",
                    value = plan.targetSatelliteId
                )

                DetailRow(
                    label = "Repair objective",
                    value = plan.damage.repairAction
                )

                DetailRow(
                    label = "Prepared at",
                    value = "T+${plan.preparedAtSecond}s"
                )

                DetailRow(
                    label = "Target telemetry",
                    value = if (targetVerified)
                        "CURRENT"
                    else
                        "UNAVAILABLE / STALE",
                    valueColor = if (targetVerified)
                        OrbitalColors.Healthy
                    else
                        OrbitalColors.Warning
                )

                if (plan.telemetryVerificationRequired) {

                    Text(
                        text = "Target verification was required " +
                                "when this mission was prepared. " +
                                "Current telemetry is checked again " +
                                "before dispatch or approach.",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp
                    )
                }

                if (vehicle.isAwayFromStation) {

                    val separation = if (
                        targetVerified && target != null
                    ) {

                        val targetPosition =
                            OrbitalDisplayGeometry.onOrbit(
                                target.telemetry.orbitAngleDegrees,
                                target.definition.orbitRadiusFraction
                            )

                        vehicle.position.distanceTo(
                            targetPosition
                        )

                    } else {
                        null
                    }

                    DetailRow(
                        label = "Target separation",
                        value = if (separation == null)
                            "UNVERIFIED"
                        else
                            String.format(
                                Locale.US,
                                "%.3f display units",
                                separation
                            )
                    )

                    Text(
                        text = "Display units are not physical " +
                                "distances or real navigation measurements.",
                        color = OrbitalColors.Muted,
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        onSelectSatellite(plan.targetSatelliteId)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text("Inspect Target Satellite")
                }

                when (vehicle.phase) {

                    ServicingVehiclePhase.DOCKED -> {

                        Button(
                            onClick = onDispatchVehicle,
                            enabled = dispatchReady,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text("Dispatch Servicing Vehicle")
                        }

                        if (!dispatchReady) {

                            Text(
                                text = "Dispatch unavailable: " +
                                        "the target must have current telemetry, " +
                                        "matching damage, and a queued work order.",
                                color = OrbitalColors.Warning,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onCancelMission,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text("Cancel Mission Preparation")
                        }
                    }

                    ServicingVehiclePhase.DEPLOYED -> {

                        Button(
                            onClick = onBeginApproach,
                            enabled = targetVerified,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text("Begin Guided Approach")
                        }

                        if (!targetVerified) {

                            Text(
                                text = "Guided approach requires current " +
                                        "target telemetry. You can still " +
                                        "command a return to the station.",
                                color = OrbitalColors.Warning,
                                fontSize = 12.sp
                            )
                        }

                        ReturnButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.APPROACHING -> {

                        Button(
                            onClick = onHoldVehicle,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text("Hold Vehicle Position")
                        }

                        ReturnButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.HOLDING -> {

                        Text(
                            text = if (
                                vehicle.holdReason ==
                                com.example.orbitalcommand.model
                                    .VehicleHoldReason.TARGET_STANDOFF
                            ) {
                                "Simulated target standoff reached. " +
                                        "No inspection or repair has been performed."
                            } else {
                                "The vehicle is stationary in the " +
                                        "schematic orbital display."
                            },
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = onResumeApproach,
                            enabled = targetVerified,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text("Resume Guided Approach")
                        }

                        ReturnButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.RETURNING -> {

                        Text(
                            text = "SERVICER-01 is navigating back " +
                                    "toward the moving OS-001 station. " +
                                    "Docking occurs automatically.",
                            color = OrbitalColors.Accent,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            Text(
                text = "AVAILABLE SERVICING REQUESTS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            if (queuedOrders.isEmpty()) {

                Text(
                    text = "No queued repairs are available. " +
                            "Create and queue a repair work order " +
                            "in Satellite Repair Operations.",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )

            } else {

                queuedOrders.forEach { order ->

                    ServicingRequestCard(
                        order = order,
                        canPrepare =
                            plan == null &&
                                    !vehicle.isAwayFromStation,
                        isPrepared =
                            plan?.workOrderId == order.id,
                        onPrepare = {
                            onPrepareMission(order.id)
                        }
                    )
                }
            }

            Text(
                text = "Guided vehicle movement is scripted. " +
                        "Gamepad control, physical rendezvous, " +
                        "and actual repair operations are not " +
                        "implemented in this version.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Displays the return command used by all active
 * vehicle operation states except returning.
 */
@Composable
private fun ReturnButton(
    onReturnVehicle: () -> Unit
) {

    OutlinedButton(
        onClick = onReturnVehicle,
        modifier = Modifier.fillMaxWidth()
    ) {

        Text("Return to Servicing Station")
    }
}

/**
 * Displays a queued maintenance request
 * available for mission preparation.
 */
@Composable
private fun ServicingRequestCard(
    order: RepairWorkOrder,
    canPrepare: Boolean,
    isPrepared: Boolean,
    onPrepare: () -> Unit
) {

    Card(
        colors = CardDefaults.cardColors(
            containerColor = OrbitalColors.PanelRaised
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = "WORK ORDER #${order.id}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "QUEUED",
                    color = OrbitalColors.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            DetailRow(
                label = "Target",
                value = order.satelliteId
            )

            DetailRow(
                label = "Equipment",
                value = order.damage.displayName
            )

            DetailRow(
                label = "Repair action",
                value = order.damage.repairAction
            )

            Button(
                onClick = onPrepare,
                enabled = canPrepare,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = if (isPrepared)
                        "Mission Already Prepared"
                    else
                        "Prepare Servicing Mission",
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Displays a labeled value inside the servicing UI.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = label,
            color = OrbitalColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}
