
package com.example.orbitalcommand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.orbitalcommand.model.EquipmentDamage
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.PowerMode
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.ui.theme.OrbitalColors
import com.example.orbitalcommand.viewmodel.OrbitalViewModel
import java.util.Locale

/**
 * Collects observable fleet state and connects
 * operator actions to the ViewModel.
 */
@Composable
fun OrbitalRoute(
    viewModel: OrbitalViewModel = viewModel()
) {

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    OrbitalDashboard(
        state = state,
        onSelectSatellite = viewModel::selectSatellite,
        onSetFault = viewModel::setFault,
        onClearFault = viewModel::clearFault,
        onInjectDamage = viewModel::injectEquipmentDamage,
        onQueueRepair = viewModel::queueRepair,
        onCompleteRepair = viewModel::completeRepair,
        onPrepareMission = viewModel::prepareServicingMission,
        onCancelMission = viewModel::cancelServicingMission,
        onDispatchVehicle = viewModel::dispatchServicingVehicle,
        onBeginApproach = viewModel::beginVehicleApproach,
        onHoldVehicle = viewModel::holdServicingVehicle,
        onResumeApproach = viewModel::resumeVehicleApproach,
        onReturnVehicle = viewModel::returnServicingVehicle,
        onAcknowledgeAlert = viewModel::acknowledgeAlert,
        onReset = viewModel::resetSimulation
    )
}

/**
 * Displays fleet telemetry, mission alerts,
 * maintenance requests, and servicing vehicle controls.
 *
 * The interface adapts to phone and tablet widths.
 */
@Composable
fun OrbitalDashboard(
    state: OrbitalUiState,
    onSelectSatellite: (String) -> Unit,
    onSetFault: (SatelliteFault) -> Unit,
    onClearFault: () -> Unit,
    onInjectDamage: (EquipmentDamage) -> Unit,
    onQueueRepair: (Long) -> Unit,
    onCompleteRepair: (Long) -> Unit,
    onPrepareMission: (Long) -> Unit,
    onCancelMission: () -> Unit,
    onDispatchVehicle: () -> Unit,
    onBeginApproach: () -> Unit,
    onHoldVehicle: () -> Unit,
    onResumeApproach: () -> Unit,
    onReturnVehicle: () -> Unit,
    onAcknowledgeAlert: (Long) -> Unit,
    onReset: () -> Unit
) {

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OrbitalColors.Background)
    ) {

        val tabletLayout = maxWidth >= 700.dp
        val selected = state.selectedSatellite

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            DashboardHeader(state)

            SatelliteSelector(
                state = state,
                onSelectSatellite = onSelectSatellite
            )

            MissionAlertsPanel(
                alerts = state.activeAlerts,
                onSelectSatellite = onSelectSatellite,
                onAcknowledgeAlert = onAcknowledgeAlert
            )

            // Reusable panel groups keep phone and
            // tablet layouts consistent.
            val repairContent: @Composable () -> Unit = {

                RepairOperationsPanel(
                    state = state,
                    onInjectDamage = onInjectDamage,
                    onQueueRepair = onQueueRepair,
                    onCompleteRepair = onCompleteRepair,
                    onSelectSatellite = onSelectSatellite
                )
            }

            val servicingContent: @Composable () -> Unit = {

                ServicingMissionPanel(
                    state = state,
                    onPrepareMission = onPrepareMission,
                    onCancelMission = onCancelMission,
                    onDispatchVehicle = onDispatchVehicle,
                    onBeginApproach = onBeginApproach,
                    onHoldVehicle = onHoldVehicle,
                    onResumeApproach = onResumeApproach,
                    onReturnVehicle = onReturnVehicle,
                    onSelectSatellite = onSelectSatellite
                )
            }

            val statusContent: @Composable () -> Unit = {

                TelemetryPanel(
                    selected,
                    state.elapsedSeconds
                )

                SatellitePowerPanel(selected)

                TelemetryHistoryPanel(selected)

                SystemHealthPanel(selected)

                FaultControlPanel(
                    satellite = selected,
                    elapsedSeconds = state.elapsedSeconds,
                    onSetFault = onSetFault,
                    onClearFault = onClearFault,
                    onReset = onReset
                )
            }

            if (tabletLayout) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(16.dp)
                ) {

                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement =
                            Arrangement.spacedBy(16.dp)
                    ) {

                        OrbitPanel(state)

                        FleetOverview(state)

                        repairContent()

                        servicingContent()

                        MissionEventHistoryPanel(
                            events = state.events
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement =
                            Arrangement.spacedBy(16.dp)
                    ) {

                        statusContent()
                    }
                }

            } else {

                OrbitPanel(state)

                FleetOverview(state)

                statusContent()

                repairContent()

                servicingContent()

                MissionEventHistoryPanel(
                    events = state.events
                )
            }
        }
    }
}

/**
 * Displays fleet health and the current
 * servicing vehicle mission status.
 */
@Composable
private fun DashboardHeader(
    state: OrbitalUiState
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text(
            text = "ORBITAL COMMAND",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "SATELLITE FLEET OPERATIONS • SIMULATION",
            color = OrbitalColors.Muted,
            fontSize = 12.sp
        )

        HorizontalDivider(
            color = OrbitalColors.PanelRaised
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "${state.satellites.size} SATELLITES",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (state.activeAlerts.isEmpty())
                    "NO ACTIVE ALERTS"
                else
                    "${state.activeAlerts.size} ACTIVE ALERT(S)",
                color = if (state.activeAlerts.isEmpty())
                    OrbitalColors.Healthy
                else
                    OrbitalColors.Warning,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.unacknowledgedAlertCount > 0) {

            Text(
                text = "${state.unacknowledgedAlertCount} " +
                        "alert(s) require acknowledgement",
                color = OrbitalColors.Warning,
                fontSize = 12.sp
            )
        }

        if (state.activeRepairOrders.isNotEmpty()) {

            Text(
                text = "${state.activeRepairOrders.size} " +
                        "outstanding repair work order(s)",
                color = OrbitalColors.Accent,
                fontSize = 12.sp
            )
        }

        if (state.preparedMission != null) {

            Text(
                text = "SERVICING MISSION " +
                        "#${state.preparedMission.workOrderId} • " +
                        state.servicingVehicle.phase
                            .displayName.uppercase(),
                color = OrbitalColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Allows operators to select a satellite
 * for detailed inspection.
 */
@Composable
private fun SatelliteSelector(
    state: OrbitalUiState,
    onSelectSatellite: (String) -> Unit
) {

    DashboardPanel(title = "SELECT SATELLITE") {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            state.satellites.forEach { satellite ->

                val alert = satellite.hasFault ||
                        satellite.onboardTelemetry.powerMode !=
                        PowerMode.NORMAL

                FilterChip(
                    selected =
                        state.selectedSatelliteId ==
                                satellite.definition.id,
                    onClick = {
                        onSelectSatellite(
                            satellite.definition.id
                        )
                    },
                    label = {
                        Text(
                            satellite.definition.id +
                                    if (alert)
                                        " • ALERT"
                                    else
                                        ""
                        )
                    }
                )
            }
        }

        Text(
            text = state.selectedSatellite.definition.name,
            color = OrbitalColors.Muted,
            fontSize = 13.sp
        )
    }
}

/**
 * Displays the three satellites, orbital servicing
 * station, and deployed servicing vehicle.
 */
@Composable
private fun OrbitPanel(
    state: OrbitalUiState
) {

    DashboardPanel(title = "ORBITAL FLEET TRACKING") {

        OrbitVisualization(
            satellites = state.satellites,
            selectedSatelliteId = state.selectedSatelliteId,
            servicingStation = state.servicingStation,
            servicingVehicle = state.servicingVehicle
        )

        Text(
            text = "Selected: ${state.selectedSatelliteId}",
            color = OrbitalColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "OS-001: Green • SERVICER-01: Cyan ring • " +
                    "SAT-001: Cyan • SAT-002: Amber • " +
                    "SAT-003: Violet",
            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )

        if (!state.selectedSatellite.isConnected) {

            Text(
                text = "Selected satellite position is " +
                        "the last received position.",
                color = OrbitalColors.Warning,
                fontSize = 12.sp
            )
        }

        Text(
            text = "Scripted 2D orbital illustration • Not to scale",
            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )
    }
}

/**
 * Summarizes the operational state of all satellites,
 * the station, and its servicing vehicle.
 */
@Composable
private fun FleetOverview(
    state: OrbitalUiState
) {

    DashboardPanel(title = "FLEET STATUS") {

        state.satellites.forEach { satellite ->

            val mode = satellite.onboardTelemetry.powerMode

            val status = when {

                satellite.isPowerDepleted ->
                    "POWER OFFLINE (SIM)"

                satellite.fault ==
                        SatelliteFault.COMMUNICATION_LOSS ->
                    "SIGNAL LOST"

                satellite.requiresServicing ->
                    "HARDWARE DAMAGE"

                satellite.fault != SatelliteFault.NONE ->
                    satellite.fault.displayName.uppercase()

                mode == PowerMode.SAFE_MODE ->
                    "POWER SAFE MODE"

                mode == PowerMode.LOW_POWER ->
                    "LOW POWER"

                else ->
                    "NOMINAL"
            }

            StatusRow(
                label = satellite.definition.id,
                status = status,
                warning = status != "NOMINAL"
            )
        }

        HorizontalDivider(
            color = OrbitalColors.PanelRaised
        )

        StatusRow(
            label = state.servicingStation.id,
            status = "STATION ONLINE",
            warning = false
        )

        StatusRow(
            label = state.servicingVehicle.id,
            status = state.servicingVehicle.phase
                .displayName.uppercase(),
            warning = false
        )
    }
}

/**
 * Displays last received telemetry
 * for the selected spacecraft.
 */
@Composable
private fun TelemetryPanel(
    satellite: SatelliteState,
    elapsedSeconds: Int
) {

    val telemetry = satellite.telemetry

    DashboardPanel(
        title = "${satellite.definition.id} TELEMETRY"
    ) {

        if (!satellite.isConnected) {

            Text(
                text = "NO LIVE TELEMETRY — " +
                        "Displaying last received measurements.",
                color = OrbitalColors.Warning,
                fontSize = 12.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            TelemetryCard(
                label = "ALTITUDE",
                value = String.format(
                    Locale.US,
                    "%.1f km",
                    telemetry.altitudeKm
                ),
                modifier = Modifier.weight(1f)
            )

            TelemetryCard(
                label = "VELOCITY",
                value = String.format(
                    Locale.US,
                    "%.2f km/s",
                    telemetry.velocityKmS
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            TelemetryCard(
                label = "BATTERY",
                value = String.format(
                    Locale.US,
                    "%.1f%%",
                    telemetry.batteryPercent
                ),
                modifier = Modifier.weight(1f)
            )

            TelemetryCard(
                label = "TEMPERATURE",
                value = String.format(
                    Locale.US,
                    "%.1f °C",
                    telemetry.temperatureC
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Last telemetry received: " +
                    "${satellite.packetAgeSeconds(elapsedSeconds)} " +
                    "second(s) ago",
            color = if (satellite.isConnected)
                OrbitalColors.Muted
            else
                OrbitalColors.Warning,
            fontSize = 12.sp
        )
    }
}

/**
 * Displays the selected satellite's historical
 * received telemetry.
 */
@Composable
private fun TelemetryHistoryPanel(
    satellite: SatelliteState
) {

    DashboardPanel(title = "TELEMETRY HISTORY") {

        TelemetryHistoryChart(
            history = satellite.history,
            isLinkLost = !satellite.isConnected
        )
    }
}

/**
 * Displays power, thermal, payload,
 * and communication diagnostics.
 */
@Composable
private fun SystemHealthPanel(
    satellite: SatelliteState
) {

    val fault = satellite.fault
    val damage = satellite.equipmentDamage
    val telemetry = satellite.telemetry

    DashboardPanel(title = "SYSTEM DIAGNOSTICS") {

        if (!satellite.isConnected) {

            StatusRow(
                "Power subsystem",
                "UNVERIFIED",
                warning = true
            )

            StatusRow(
                "Thermal subsystem",
                "UNVERIFIED",
                warning = true
            )

            StatusRow(
                "Payload",
                "UNVERIFIED",
                warning = true
            )

        } else {

            val powerWarning =
                fault == SatelliteFault.POWER_DRAIN ||
                        damage ==
                        EquipmentDamage.SOLAR_ARRAY_DAMAGE ||
                        telemetry.batteryPercent < 25.0

            val thermalWarning =
                fault == SatelliteFault.THERMAL_OVERLOAD ||
                        damage ==
                        EquipmentDamage.THERMAL_RADIATOR_DAMAGE ||
                        telemetry.temperatureC > 40.0

            val payloadWarning =
                fault == SatelliteFault.PAYLOAD_FAILURE ||
                        damage ==
                        EquipmentDamage.PAYLOAD_SENSOR_DAMAGE

            val powerStatus = when (telemetry.powerMode) {

                PowerMode.SAFE_MODE ->
                    "SAFE MODE"

                PowerMode.LOW_POWER ->
                    "LOW POWER"

                else ->
                    if (powerWarning) "WARNING" else "NOMINAL"
            }

            StatusRow(
                "Power subsystem",
                powerStatus,
                powerWarning
            )

            StatusRow(
                "Thermal subsystem",
                if (thermalWarning) "WARNING" else "NOMINAL",
                thermalWarning
            )

            StatusRow(
                "Payload",
                when {
                    payloadWarning -> "FAILED"

                    telemetry.powerMode ==
                            PowerMode.SAFE_MODE ->
                        "POWER SAVING"

                    else -> "NOMINAL"
                },
                payloadWarning ||
                        telemetry.powerMode == PowerMode.SAFE_MODE
            )
        }

        StatusRow(
            "Communications",
            if (satellite.isConnected)
                "ONLINE"
            else
                "OFFLINE",
            warning = !satellite.isConnected
        )

        if (satellite.requiresServicing) {

            Text(
                text = "SIMULATED HARDWARE DAMAGE: " +
                        damage.displayName,
                color = OrbitalColors.Warning,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Provides temporary fault controls and
 * the fleet simulation reset action.
 */
@Composable
private fun FaultControlPanel(
    satellite: SatelliteState,
    elapsedSeconds: Int,
    onSetFault: (SatelliteFault) -> Unit,
    onClearFault: () -> Unit,
    onReset: () -> Unit
) {

    DashboardPanel(title = "FAULT SIMULATION") {

        Text(
            text = "Target: ${satellite.definition.id}",
            color = OrbitalColors.Muted,
            fontSize = 12.sp
        )

        Text(
            text = "Temporary diagnostic faults:",
            color = Color.White,
            fontSize = 13.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            SatelliteFault.entries
                .filter {
                    it != SatelliteFault.NONE
                }
                .forEach { fault ->

                    FilterChip(
                        selected = satellite.fault == fault,
                        onClick = {
                            onSetFault(fault)
                        },
                        label = {
                            Text(fault.displayName)
                        }
                    )
                }
        }

        Text(
            text = satellite.fault.description,
            color = if (
                satellite.fault != SatelliteFault.NONE
            ) {
                OrbitalColors.Warning
            } else {
                OrbitalColors.Muted
            },
            fontSize = 12.sp
        )

        OutlinedButton(
            onClick = onClearFault,
            enabled = satellite.fault != SatelliteFault.NONE,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Clear Temporary Fault")
        }

        Text(
            text = "Equipment damage requires completion " +
                    "of a repair work order.",
            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Reset Entire Simulation")
        }

        Text(
            text = "Mission elapsed time: $elapsedSeconds seconds",
            color = OrbitalColors.Muted,
            fontSize = 12.sp
        )
    }
}

/**
 * Provides a reusable dashboard panel container.
 */
@Composable
private fun DashboardPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {

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
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            content()
        }
    }
}

/**
 * Displays a formatted telemetry measurement.
 */
@Composable
private fun TelemetryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = OrbitalColors.PanelRaised
        )
    ) {

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            Text(
                text = label,
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )

            Text(
                text = value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Displays the operational status
 * of a satellite subsystem or entity.
 */
@Composable
private fun StatusRow(
    label: String,
    status: String,
    warning: Boolean
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(
            text = status,
            color = if (warning)
                OrbitalColors.Warning
            else
                OrbitalColors.Healthy,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
