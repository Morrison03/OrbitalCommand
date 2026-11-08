
package com.example.orbitalcommand.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orbitalcommand.model.EquipmentDamage
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairWorkOrder
import com.example.orbitalcommand.ui.theme.OrbitalColors

/**
 * Displays persistent equipment damage and
 * fleet-wide maintenance work orders.
 *
 * A work order assigned to a deployed servicing
 * vehicle cannot be completed through the
 * test-only maintenance control.
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
        .filter {
            it.status == RepairStatus.COMPLETED
        }
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
                fontSize = 13.sp
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
                    .filter {
                        it != EquipmentDamage.NONE
                    }
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

                Text(
                    text = "Temporary fault controls cannot " +
                            "repair damaged equipment.",
                    color = OrbitalColors.Muted,
                    fontSize = 11.sp
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

                    val vehicleAssignedAndAway =
                        state.servicingVehicle
                            .isAwayFromStation &&
                                state.servicingVehicle
                                    .workOrderId == order.id

                    RepairOrderCard(
                        order = order,
                        completionAllowed =
                            !vehicleAssignedAndAway,
                        onInspect = {
                            onSelectSatellite(order.satelliteId)
                        },
                        onQueue = {
                            onQueueRepair(order.id)
                        },
                        onComplete = {
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
                                order.damage.displayName +
                                " • T+${order.completedAtSecond}s",
                        color = OrbitalColors.Healthy,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "Repair completion remains a " +
                        "simulation-only test action. " +
                        "Approaching a satellite does not " +
                        "repair its equipment.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Displays an individual repair request
 * and the available maintenance actions.
 */
@Composable
private fun RepairOrderCard(
    order: RepairWorkOrder,
    completionAllowed: Boolean,
    onInspect: () -> Unit,
    onQueue: () -> Unit,
    onComplete: () -> Unit
) {

    val queued = order.status == RepairStatus.QUEUED

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Text(
                    text = if (queued)
                        "QUEUED"
                    else
                        "OPEN",
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

                Button(
                    onClick = onComplete,
                    enabled = completionAllowed,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = "Simulate Repair Completion",
                        fontSize = 12.sp
                    )
                }

                if (!completionAllowed) {

                    Text(
                        text = "Repair completion is disabled " +
                                "while the assigned servicing vehicle " +
                                "is away from OS-001.",
                        color = OrbitalColors.Warning,
                        fontSize = 12.sp
                    )
                } else {

                    Text(
                        text = "Test action: assumes successful " +
                                "maintenance without robotic servicing.",
                        color = OrbitalColors.Muted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
