
package com.example.orbitalcommand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.example.orbitalcommand.model.VehicleHoldReason
import com.example.orbitalcommand.simulation.ServicingVehicleNavigation
import com.example.orbitalcommand.ui.theme.OrbitalColors
import java.util.Locale

/**
 * Displays orbital servicing mission preparation,
 * robotic vehicle controls, and the interactive
 * repair console.
 *
 * The mission remains active until SERVICER-01
 * returns to OS-001, even after a successful repair.
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

    val missionOrder = state.repairOrders.firstOrNull {
        it.id == plan?.workOrderId
    }

    val repairCompleted =
        missionOrder?.status == RepairStatus.COMPLETED

    val target = state.satellites.firstOrNull {
        it.definition.id == plan?.targetSatelliteId
    }

    val targetVerified = target?.let {
        ServicingVehicleNavigation.hasCurrentTargetTelemetry(
            target = it,
            atSecond = state.elapsedSeconds
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

    val phaseColor = when (vehicle.phase) {

        ServicingVehiclePhase.DOCKED,
        ServicingVehiclePhase.FORMATION_FOLLOWING ->
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = vehicle.id,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = vehicle.phase.displayName.uppercase(),
                    color = phaseColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            ServicingDetailRow(
                label = "Station orbit angle",
                value = String.format(
                    Locale.US,
                    "%.0f°",
                    station.orbitAngleDegrees
                )
            )

            ServicingDetailRow(
                label = "Queued work orders",
                value = queuedOrders.size.toString()
            )

            if (vehicle.holdReason != null) {

                Text(
                    text = "HOLD REASON: " +
                            vehicle.holdReason.displayName,
                    color = OrbitalColors.Warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            if (plan == null) {

                Text(
                    text = "NO MISSION PREPARED",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Select a queued maintenance request " +
                            "below to prepare a servicing mission.",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )

            } else {

                Text(
                    text = "CURRENT SERVICING MISSION",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                ServicingDetailRow(
                    label = "Work order",
                    value = "#${plan.workOrderId}"
                )

                ServicingDetailRow(
                    label = "Target",
                    value = plan.targetSatelliteId
                )

                ServicingDetailRow(
                    label = "Repair action",
                    value = plan.damage.repairAction
                )

                ServicingDetailRow(
                    label = "Repair status",
                    value = if (repairCompleted)
                        "COMPLETED"
                    else
                        "PENDING",
                    valueColor = if (repairCompleted)
                        OrbitalColors.Healthy
                    else
                        Color(0xFFFBBF24)
                )

                ServicingDetailRow(
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

                if (vehicle.isAwayFromStation) {

                    val separation = if (
                        targetVerified && target != null
                    ) {

                        val targetPosition =
                            OrbitalDisplayGeometry.onOrbit(
                                angleDegrees =
                                    target.telemetry.orbitAngleDegrees,
                                radiusFraction =
                                    target.definition.orbitRadiusFraction
                            )

                        vehicle.position.distanceTo(
                            targetPosition
                        )

                    } else {
                        null
                    }

                    ServicingDetailRow(
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
                                text = "Dispatch requires current " +
                                        "target telemetry and a valid " +
                                        "queued repair.",
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

                        ReturnVehicleButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.APPROACHING -> {

                        Text(
                            text = "Approaching the moving " +
                                    "formation position.",
                            color = OrbitalColors.Accent,
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = onHoldVehicle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pause Guided Approach")
                        }

                        ReturnVehicleButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.FORMATION_FOLLOWING -> {

                        Text(
                            text = "FORMATION ESTABLISHED",
                            color = OrbitalColors.Healthy,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "SERVICER-01 continues orbiting " +
                                    "alongside ${plan.targetSatelliteId}.",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        if (repairCompleted) {

                            Text(
                                text = "REPAIR COMPLETE — " +
                                        "RETURN TO STATION",
                                color = OrbitalColors.Healthy,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                        } else {

                            Text(
                                text = "Use the Robotic Repair Console " +
                                        "below to perform the maintenance.",
                                color = OrbitalColors.Accent,
                                fontSize = 12.sp
                            )
                        }

                        ReturnVehicleButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.HOLDING -> {

                        Text(
                            text = if (
                                vehicle.holdReason ==
                                VehicleHoldReason.TELEMETRY_UNAVAILABLE
                            ) {
                                "Tracking suspended due to " +
                                        "unavailable target telemetry. " +
                                        "Repair progress has been preserved."
                            } else {
                                "The operator paused " +
                                        "vehicle guidance."
                            },
                            color = OrbitalColors.Warning,
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = onResumeApproach,
                            enabled = targetVerified,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Resume Guided Approach")
                        }

                        ReturnVehicleButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.RETURNING -> {

                        Text(
                            text = "SERVICER-01 is returning to " +
                                    "OS-001. The mission will close " +
                                    "when it docks.",
                            color = OrbitalColors.Accent,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            // The repair console uses the same OrbitalViewModel
            // as the dashboard and remains synchronized with
            // live mission state and formation guidance.
            RoboticRepairRoute(state = state)

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
                    text = "No queued repairs are available.",
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
                text = "Vehicle navigation and robotic servicing " +
                        "are simulated in two dimensions. " +
                        "They are not flight-qualified control systems.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Displays the vehicle's return-to-station command.
 */
@Composable
private fun ReturnVehicleButton(
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
 * Displays a queued repair eligible for mission preparation.
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
        ),
        shape = RoundedCornerShape(12.dp)
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

            ServicingDetailRow(
                label = "Target",
                value = order.satelliteId
            )

            ServicingDetailRow(
                label = "Equipment",
                value = order.damage.displayName
            )

            ServicingDetailRow(
                label = "Required action",
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
 * Displays a labeled mission-detail value.
 */
@Composable
private fun ServicingDetailRow(
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

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}
