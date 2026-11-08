
package com.example.orbitalcommand.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orbitalcommand.model.EquipmentDamage
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.RepairCompletionPolicy
import com.example.orbitalcommand.model.RepairCompletionStatus
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairWorkOrder
import com.example.orbitalcommand.ui.theme.OrbitalColors

/**
 * Displays persistent equipment damage and
 * fleet-wide maintenance work orders.
 *
 * Normal in-orbit repair completion occurs through
 * the interactive robotic repair procedure.
 *
 * The docked completion action remains available
 * for explicit simulator debugging.
 */
@Composable
fun RepairOperationsPanel(
    state: OrbitalUiState,
    onInjectDamage: (EquipmentDamage) -> Unit,
    onQueueRepair: (Long) -> Unit,
    onCompleteRepair: (Long) -> Unit,
    onSelectSatellite: (String) -> Unit
) {

    val selected = state.selectedSatellite

    val activeOrders = state.activeRepairOrders
        .sortedByDescending { it.id }

    val completedOrders = state.repairOrders
        .filter { it.status == RepairStatus.COMPLETED }
        .sortedByDescending { it.id }
        .take(3)

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
                text = "SATELLITE REPAIR OPERATIONS",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Equipment damage simulation",
                color = OrbitalColors.Muted,
                fontSize = 12.sp
            )

            Text(
                text = "Target: ${selected.definition.id}",
                color = OrbitalColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Select equipment to damage:",
                color = Color.White,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                EquipmentDamage.entries
                    .filter { it != EquipmentDamage.NONE }
                    .forEach { damage ->

                        FilterChip(
                            selected =
                                selected.equipmentDamage == damage,
                            enabled = !selected.requiresServicing,
                            onClick = {
                                onInjectDamage(damage)
                            },
                            label = {
                                Text(damage.displayName)
                            }
                        )
                    }
            }

            if (selected.requiresServicing) {

                Text(
                    text = "EQUIPMENT DAMAGE ACTIVE",
                    color = OrbitalColors.Warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = selected.equipmentDamage.description,
                    color = Color.White,
                    fontSize = 12.sp
                )

                Text(
                    text = "Required maintenance: " +
                            selected.equipmentDamage.repairAction,
                    color = OrbitalColors.Accent,
                    fontSize = 12.sp
                )

            } else {

                Text(
                    text = "No persistent equipment damage " +
                            "on the selected satellite.",
                    color = OrbitalColors.Healthy,
                    fontSize = 12.sp
                )
            }

            HorizontalDivider(
                color = OrbitalColors.PanelRaised
            )

            Text(
                text = "ACTIVE WORK ORDERS " +
                        "(${activeOrders.size})",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            if (activeOrders.isEmpty()) {

                Text(
                    text = "No outstanding repair requests.",
                    color = OrbitalColors.Healthy,
                    fontSize = 12.sp
                )

            } else {

                activeOrders.forEach { order ->

                    val readiness =
                        RepairCompletionPolicy.evaluate(
                            state = state,
                            order = order
                        )

                    RepairOrderCard(
                        order = order,
                        readiness = readiness,
                        onInspect = {
                            onSelectSatellite(order.satelliteId)
                        },
                        onQueue = {
                            onQueueRepair(order.id)
                        },
                        onDebugComplete = {
                            onCompleteRepair(order.id)
                        }
                    )
                }
            }

            if (completedOrders.isNotEmpty()) {

                HorizontalDivider(
                    color = OrbitalColors.PanelRaised
                )

                Text(
                    text = "RECENT COMPLETED REPAIRS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                completedOrders.forEach { order ->

                    Text(
                        text = "#${order.id} • " +
                                "${order.satelliteId} • " +
                                "${order.damage.displayName} • " +
                                "T+${order.completedAtSecond}s",
                        color = OrbitalColors.Healthy,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "In-orbit maintenance is performed in " +
                        "the Robotic Repair Console under " +
                        "Orbital Servicing Operations.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Displays an individual repair work order.
 *
 * In-orbit servicing requires the interactive
 * robotic repair console.
 */
@Composable
private fun RepairOrderCard(
    order: RepairWorkOrder,
    readiness: RepairCompletionStatus,
    onInspect: () -> Unit,
    onQueue: () -> Unit,
    onDebugComplete: () -> Unit
) {

    val queued = order.status == RepairStatus.QUEUED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
                    text = if (queued) "QUEUED" else "OPEN",
                    color = if (queued)
                        OrbitalColors.Accent
                    else
                        Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = order.satelliteId,
                color = OrbitalColors.Accent,
                fontSize = 12.sp
            )

            Text(
                text = order.damage.displayName,
                color = OrbitalColors.Warning,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Subsystem: ${order.damage.subsystem}",
                color = OrbitalColors.Muted,
                fontSize = 12.sp
            )

            Text(
                text = "Required: ${order.damage.repairAction}",
                color = Color.White,
                fontSize = 12.sp
            )

            Text(
                text = "Created T+${order.createdAtSecond}s",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )

            OutlinedButton(
                onClick = onInspect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Inspect Satellite")
            }

            if (order.status == RepairStatus.OPEN) {

                Button(
                    onClick = onQueue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Queue Repair")
                }

            } else if (queued) {

                if (
                    readiness ==
                    RepairCompletionStatus.READY_AT_STATION
                ) {

                    OutlinedButton(
                        onClick = onDebugComplete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Debug: Complete Without Robot",
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = "Simulator testing only. This " +
                                "bypasses the robotic procedure.",
                        color = OrbitalColors.Muted,
                        fontSize = 11.sp
                    )

                } else {

                    Text(
                        text = "Complete this work order using " +
                                "the interactive robotic repair " +
                                "console while SERVICER-01 is " +
                                "following its assigned satellite.",
                        color = OrbitalColors.Accent,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
