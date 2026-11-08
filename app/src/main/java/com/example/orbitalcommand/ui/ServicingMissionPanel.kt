
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
 * Displays the orbital servicing station, maintenance
 * mission details, and servicing vehicle controls.
 *
 * A repair completed during formation following leaves
 * the vehicle deployed until the operator commands
 * its return to OS-001.
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

    val vehicleColor = when (vehicle.phase) {

        ServicingVehiclePhase.DOCKED ->
            OrbitalColors.Healthy

        ServicingVehiclePhase.DEPLOYED,
        ServicingVehiclePhase.APPROACHING ->
            OrbitalColors.Accent

        ServicingVehiclePhase.FORMATION_FOLLOWING ->
            OrbitalColors.Healthy

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
                        text = "Target telemetry required verification " +
                                "at mission preparation. " +
                                "Current telemetry is checked again " +
                                "before dispatch and approach.",
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
                                text = "Dispatch requires matching " +
                                        "equipment damage, a queued repair, " +
                                        "and current target telemetry.",
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
                                text = "Guidance requires current target " +
                                        "telemetry. Return is still available.",
                                color = OrbitalColors.Warning,
                                fontSize = 12.sp
                            )
                        }

                        ReturnButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.APPROACHING -> {

                        Text(
                            text = "Approaching the target's moving " +
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

                        ReturnButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.FORMATION_FOLLOWING -> {

                        Text(
                            text = "FORMATION ESTABLISHED",
                            color = OrbitalColors.Healthy,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "SERVICER-01 is orbiting alongside " +
                                    "${plan.targetSatelliteId}, maintaining " +
                                    "a fixed schematic separation.",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        DetailRow(
                            label = "Formation spacing",
                            value = String.format(
                                Locale.US,
                                "%.3f display units",
                                ServicingVehicleNavigation
                                    .FORMATION_SEPARATION
                            ),
                            valueColor = OrbitalColors.Healthy
                        )

                        if (repairCompleted) {

                            Text(
                                text = "REPAIR COMPLETE — " +
                                        "RETURN TO STATION",
                                color = OrbitalColors.Healthy,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "The simulated equipment repair " +
                                        "has been completed. SERVICER-01 " +
                                        "continues following the satellite " +
                                        "until you command its return.",
                                color = Color.White,
                                fontSize = 12.sp
                            )

                        } else {

                            Text(
                                text = "REPAIR READY",
                                color = OrbitalColors.Accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Open Satellite Repair Operations " +
                                        "and select Simulate Repair Completion. " +
                                        "Returning to OS-001 is not required " +
                                        "before this action.",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }

                        ReturnButton(onReturnVehicle)
                    }

                    ServicingVehiclePhase.HOLDING -> {

                        Text(
                            text = if (
                                vehicle.holdReason ==
                                VehicleHoldReason.TELEMETRY_UNAVAILABLE
                            ) {
                                "Automatic tracking is suspended " +
                                        "because target telemetry " +
                                        "is unavailable."
                            } else {
                                "The operator paused " +
                                        "scripted navigation."
                            },
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp
                        )

                        if (repairCompleted) {

                            Text(
                                text = "Repair already completed. " +
                                        "You can resume tracking or " +
                                        "return to OS-001.",
                                color = OrbitalColors.Healthy,
                                fontSize = 12.sp
                            )
                        }

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
                            text = if (repairCompleted) {
                                "Repair completed. SERVICER-01 " +
                                        "is returning to OS-001. " +
                                        "The mission will close at docking."
                            } else {
                                "SERVICER-01 is returning to OS-001. " +
                                        "The repair order remains queued."
                            },
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
                    text = "No queued repair requests are available.",
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
                text = "Repair completion and orbital formation " +
                        "are simplified simulations, not validated " +
                        "spacecraft servicing procedures.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Displays the command that sends the vehicle
 * back toward the orbital servicing station.
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
 * Displays one queued repair request
 * eligible for servicing mission preparation.
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
 * Displays one labeled value within
 * the servicing mission interface.
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
