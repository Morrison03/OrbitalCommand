
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
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairWorkOrder
import com.example.orbitalcommand.model.ServicingMissionPlan
import com.example.orbitalcommand.ui.theme.OrbitalColors
import java.util.Locale

/**
 * Displays the simulated servicing station and
 * operator controls for preparing maintenance missions.
 *
 * Mission preparation does not dispatch a spacecraft,
 * change equipment state, or complete repair work.
 */
@Composable
fun ServicingMissionPanel(
    state: OrbitalUiState,
    onPrepareMission: (Long) -> Unit,
    onCancelMission: () -> Unit,
    onSelectSatellite: (String) -> Unit
) {

    val station = state.servicingStation

    val queuedOrders = state.activeRepairOrders.filter {
        it.status == RepairStatus.QUEUED
    }

    val preparedMission = state.preparedMission

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
                text = "ORBITAL SERVICING STATION",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp)
                ) {

                    Text(
                        text = station.id,
                        color = OrbitalColors.Accent,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = station.name,
                        color = OrbitalColors.Muted,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "PLANNING ONLINE",
                    color = OrbitalColors.Healthy,
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

            ServicingDetailRow(
                label = "Mission preparation",
                value = if (preparedMission == null)
                    "AVAILABLE"
                else
                    "PREPARED",
                valueColor = if (preparedMission == null)
                    OrbitalColors.Healthy
                else
                    Color(0xFFFBBF24)
            )

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            if (preparedMission != null) {

                PreparedMissionCard(
                    plan = preparedMission,
                    state = state,
                    onInspect = {
                        onSelectSatellite(
                            preparedMission.targetSatelliteId
                        )
                    },
                    onCancel = onCancelMission
                )

            } else {

                Text(
                    text = "NO MISSION PREPARED",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
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
                            "Create equipment damage and queue " +
                            "its work order in Repair Operations.",
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )

            } else {

                queuedOrders.forEach { order ->

                    ServicingRequestCard(
                        order = order,
                        canPrepare = preparedMission == null,
                        isPrepared =
                            preparedMission?.workOrderId ==
                                    order.id,
                        onPrepare = {
                            onPrepareMission(order.id)
                        }
                    )
                }
            }

            Text(
                text = "Mission preparation records the target " +
                        "and servicing objective. Vehicle dispatch, " +
                        "rendezvous, and repair controls are not yet enabled.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Displays the details of a prepared servicing mission.
 *
 * The current target connection is shown separately
 * from its condition at preparation time.
 */
@Composable
private fun PreparedMissionCard(
    plan: ServicingMissionPlan,
    state: OrbitalUiState,
    onInspect: () -> Unit,
    onCancel: () -> Unit
) {

    val target = state.satellites.firstOrNull {
        it.definition.id == plan.targetSatelliteId
    }

    val hasLiveTelemetry = target?.isConnected == true

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Text(
                text = "MISSION PREPARED",
                color = Color(0xFFFBBF24),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            ServicingDetailRow(
                label = "Repair work order",
                value = "#${plan.workOrderId}"
            )

            ServicingDetailRow(
                label = "Target satellite",
                value = plan.targetSatelliteId
            )

            ServicingDetailRow(
                label = "Repair objective",
                value = plan.damage.repairAction
            )

            ServicingDetailRow(
                label = "Prepared at",
                value = "T+${plan.preparedAtSecond}s"
            )

            ServicingDetailRow(
                label = "Current target telemetry",
                value = if (hasLiveTelemetry)
                    "AVAILABLE"
                else
                    "UNAVAILABLE",
                valueColor = if (hasLiveTelemetry)
                    OrbitalColors.Healthy
                else
                    OrbitalColors.Warning
            )

            if (plan.telemetryVerificationRequired) {

                Text(
                    text = "PREPARATION WARNING: " +
                            "The target's position or telemetry " +
                            "requires independent verification " +
                            "before a future dispatch.",
                    color = OrbitalColors.Warning,
                    fontSize = 12.sp
                )
            }

            if (!hasLiveTelemetry) {

                Text(
                    text = "The orbital view may show the " +
                            "target's last received position, " +
                            "not its verified current position.",
                    color = OrbitalColors.Warning,
                    fontSize = 12.sp
                )
            }

            Text(
                text = "Status: Awaiting future dispatch capability",
                color = OrbitalColors.Muted,
                fontSize = 12.sp
            )

            OutlinedButton(
                onClick = onInspect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Inspect Target Satellite")
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel Mission Preparation")
            }
        }
    }
}

/**
 * Displays a queued maintenance request that
 * is eligible for mission preparation.
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

            ServicingDetailRow(
                label = "Target",
                value = order.satelliteId
            )

            ServicingDetailRow(
                label = "Equipment",
                value = order.damage.displayName
            )

            ServicingDetailRow(
                label = "Service action",
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
 * Displays a labeled value within a servicing panel.
 */
@Composable
private fun ServicingDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
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

