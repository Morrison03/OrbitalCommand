
package com.example.orbitalcommand.ui
import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
    val simulationPaused by viewModel.isSimulationPaused.collectAsStateWithLifecycle()

    MissionNotificationEffects()

    OrbitalDashboard(
        state = state,
        isSimulationPaused = simulationPaused,
        onToggleSimulationPaused = viewModel::toggleSimulationPaused,
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
 * Main Orbital Command screen with three persistent destinations.
 *
 * Fleet: satellite selection, orbital tracking,
 * telemetry, and diagnostics.
 *
 * Operations: repair work orders and SERVICER-01
 * mission control.
 *
 * History: complete mission event history.
 *
 * All pages share the same OrbitalUiState and
 * OrbitalViewModel.
 *
 * Switching pages does not reset the simulation
 * or the current repair procedure.
 *
 * The dashboard also respects Android's system
 * status bar to prevent headings from appearing
 * underneath notifications and system icons.
 */
@Composable
fun OrbitalDashboard(
    state: OrbitalUiState,
    isSimulationPaused: Boolean,
    onToggleSimulationPaused: () -> Unit,
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

    /*
     * Remember which page is selected.
     *
     * rememberSaveable preserves navigation
     * selection during activity recreation.
     */
    // Persist the selected tab separately from the repair mission snapshot.
    val context = LocalContext.current.applicationContext
    val navigationPrefs = remember(context) {
        context.getSharedPreferences("orbital_command_persistent_ui", Context.MODE_PRIVATE)
    }

    var selectedPage by rememberSaveable {
        mutableStateOf(navigationPrefs.getInt("selected_page", 0).coerceIn(0, 2))
    }

    fun selectPage(page: Int) {
        selectedPage = page
        navigationPrefs.edit().putInt("selected_page", page).apply()
    }

    /*
     * Each page maintains its own scroll position.
     *
     * Switching away and returning does not force
     * the operator to scroll from the top again.
     */
    val fleetScroll = rememberScrollState(navigationPrefs.getInt("fleet_scroll", 0))

    val operationsScroll = rememberScrollState(navigationPrefs.getInt("operations_scroll", 0))

    val historyScroll = rememberScrollState(navigationPrefs.getInt("history_scroll", 0))

    // Debounce continuous scrolling to reduce preference writes.
    LaunchedEffect(fleetScroll, operationsScroll, historyScroll) {
        snapshotFlow {
            Triple(fleetScroll.value, operationsScroll.value, historyScroll.value)
        }.collectLatest { (fleet, operations, history) ->
            delay(300L)
            navigationPrefs.edit()
                .putInt("fleet_scroll", fleet)
                .putInt("operations_scroll", operations)
                .putInt("history_scroll", history)
                .apply()
        }
    }

    /*
     * MAIN DASHBOARD CONTAINER
     *
     * statusBarsPadding() prevents the page title
     * from overlapping the Android notification
     * and system status bar.
     *
     * The padding adjusts automatically to
     * the current device.
     */
    MissionNotificationRedirect(
        onSelectPage = { page -> selectPage(page) },
        onSelectSatellite = onSelectSatellite
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OrbitalColors.Background)
            .statusBarsPadding()
    ) {

        /*
         * Switch between phone and tablet layouts.
         */
        val tabletLayout = maxWidth >= 700.dp

        val selected = state.selectedSatellite

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            /*
             * MAIN PAGE CONTENT
             *
             * This section scrolls independently
             * of the bottom navigation bar.
             */
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(
                        when (selectedPage) {

                            1 -> operationsScroll

                            2 -> historyScroll

                            else -> fleetScroll
                        }
                    )
                    .padding(16.dp),

                verticalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                when (selectedPage) {

                    /*
                     * PAGE 1 — FLEET OVERVIEW
                     *
                     * Displays satellite selection,
                     * orbital tracking, telemetry,
                     * diagnostics, and fault controls.
                     */
                    0 -> {

                        DashboardHeader(state)

                        SatelliteSelector(
                            state = state,
                            onSelectSatellite = onSelectSatellite
                        )

                        MissionAlertsPanel(
                            alerts = state.activeAlerts,
                            onSelectSatellite = { alertSatelliteId ->
                                onSelectSatellite(alertSatelliteId)
                                if (state.activeAlerts.any { alert ->
                                        alert.satelliteId == alertSatelliteId &&
                                        MissionAlertRouting.isRepairRelated(alert)
                                    }) {
                                    selectPage(1)
                                }
                            },
                            onAcknowledgeAlert =
                                onAcknowledgeAlert
                        )

                        if (tabletLayout) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        16.dp
                                    )
                            ) {

                                /*
                                 * Left tablet column:
                                 * orbital map and fleet status.
                                 */
                                Column(
                                    modifier = Modifier
                                        .weight(1.2f),

                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            16.dp
                                        )
                                ) {

                                    OrbitPanel(state)

                                    FleetOverview(state)
                                }

                                /*
                                 * Right tablet column:
                                 * telemetry and diagnostics.
                                 */
                                Column(
                                    modifier = Modifier
                                        .weight(1f),

                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            16.dp
                                        )
                                ) {

                                    TelemetryPanel(
                                        selected,
                                        state.elapsedSeconds
                                    )

                                    SatellitePowerPanel(
                                        selected
                                    )

                                    TelemetryHistoryPanel(
                                        selected
                                    )

                                    SystemHealthPanel(
                                        selected
                                    )

                                    FaultControlPanel(
                                        satellite = selected,
                                        elapsedSeconds =
                                            state.elapsedSeconds,
                                        onSetFault = onSetFault,
                                        onClearFault = onClearFault,
                                        onReset = onReset,
                                        isSimulationPaused = isSimulationPaused,
                                        onToggleSimulationPaused = onToggleSimulationPaused
                                    )
                                }
                            }

                        } else {

                            /*
                             * Phone layout:
                             * all Fleet panels appear
                             * in a vertical scroll.
                             */
                            OrbitPanel(state)

                            FleetOverview(state)

                            TelemetryPanel(
                                selected,
                                state.elapsedSeconds
                            )

                            SatellitePowerPanel(selected)

                            TelemetryHistoryPanel(selected)

                            SystemHealthPanel(selected)

                            FaultControlPanel(
                                satellite = selected,
                                elapsedSeconds =
                                    state.elapsedSeconds,
                                onSetFault = onSetFault,
                                onClearFault = onClearFault,
                                onReset = onReset,
                                isSimulationPaused = isSimulationPaused,
                                onToggleSimulationPaused = onToggleSimulationPaused
                            )
                        }
                    }

                    /*
                     * PAGE 2 — REPAIR & SERVICING
                     *
                     * Displays maintenance work orders
                     * and SERVICER-01 mission operations.
                     *
                     * The existing robotic simulator
                     * remains unchanged.
                     */
                    1 -> {

                        DashboardPageHeading(
                            title = "REPAIR & SERVICING",
                            subtitle =
                                "Work orders • SERVICER-01 • " +
                                        "robotic maintenance"
                        )

                        Text(
                            text =
                                "Selected satellite: " +
                                        state.selectedSatelliteId,
                            color = OrbitalColors.Accent,
                            fontSize = 12.sp
                        )

                        if (tabletLayout) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        16.dp
                                    )
                            ) {

                                /*
                                 * Tablet:
                                 * repair work orders.
                                 */
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                ) {

                                    RepairOperationsPanel(
                                        state = state,
                                        onInjectDamage =
                                            onInjectDamage,
                                        onQueueRepair =
                                            onQueueRepair,
                                        onCompleteRepair =
                                            onCompleteRepair,
                                        onSelectSatellite =
                                            onSelectSatellite
                                    )
                                }

                                /*
                                 * Tablet:
                                 * servicing robot controls.
                                 */
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                ) {

                                    ServicingMissionPanel(
                                        state = state,
                                        onPrepareMission =
                                            onPrepareMission,
                                        onCancelMission =
                                            onCancelMission,
                                        onDispatchVehicle =
                                            onDispatchVehicle,
                                        onBeginApproach =
                                            onBeginApproach,
                                        onHoldVehicle =
                                            onHoldVehicle,
                                        onResumeApproach =
                                            onResumeApproach,
                                        onReturnVehicle =
                                            onReturnVehicle,
                                        onSelectSatellite =
                                            onSelectSatellite
                                    )
                                }
                            }

                        } else {

                            /*
                             * Phone:
                             * repair operations appear
                             * above the servicing controls.
                             */
                            RepairOperationsPanel(
                                state = state,
                                onInjectDamage =
                                    onInjectDamage,
                                onQueueRepair =
                                    onQueueRepair,
                                onCompleteRepair =
                                    onCompleteRepair,
                                onSelectSatellite =
                                    onSelectSatellite
                            )

                            ServicingMissionPanel(
                                state = state,
                                onPrepareMission =
                                    onPrepareMission,
                                onCancelMission =
                                    onCancelMission,
                                onDispatchVehicle =
                                    onDispatchVehicle,
                                onBeginApproach =
                                    onBeginApproach,
                                onHoldVehicle =
                                    onHoldVehicle,
                                onResumeApproach =
                                    onResumeApproach,
                                onReturnVehicle =
                                    onReturnVehicle,
                                onSelectSatellite =
                                    onSelectSatellite
                            )
                        }
                    }

                    /*
                     * PAGE 3 — MISSION HISTORY
                     *
                     * Displays the recorded mission
                     * events on their own page.
                     */
                    else -> {

                        DashboardPageHeading(
                            title = "MISSION EVENT HISTORY",
                            subtitle =
                                "Simulation activity, alerts " +
                                        "and servicing events"
                        )

                        MissionEventHistoryPanel(
                            events = state.events
                        )
                    }
                }
            }

            /*
             * BOTTOM NAVIGATION
             *
             * The navigation bar is outside the
             * vertical scrolling area.
             *
             * This keeps page switching available
             * even when the operator scrolls
             * deep into a dashboard page.
             */
            NavigationBar(
                containerColor = OrbitalColors.Panel,
                tonalElevation = 4.dp
            ) {

                /*
                 * Fleet destination.
                 */
                NavigationBarItem(
                    selected = selectedPage == 0,

                    onClick = { selectPage(0) },

                    icon = {
                        Text(
                            text = "01",
                            fontSize = 15.sp
                        )
                    },

                    label = {
                        Text("Fleet")
                    },

                    alwaysShowLabel = true,

                    colors =
                        dashboardNavigationColors()
                )

                /*
                 * Operations destination.
                 */
                NavigationBarItem(
                    selected = selectedPage == 1,

                    onClick = { selectPage(1) },

                    icon = {
                        Text(
                            text = "02",
                            fontSize = 15.sp
                        )
                    },

                    label = {
                        Text("Operations")
                    },

                    alwaysShowLabel = true,

                    colors =
                        dashboardNavigationColors()
                )

                /*
                 * Mission History destination.
                 */
                NavigationBarItem(
                    selected = selectedPage == 2,

                    onClick = { selectPage(2) },

                    icon = {
                        Text(
                            text = "03",
                            fontSize = 15.sp
                        )
                    },

                    label = {
                        Text("History")
                    },

                    alwaysShowLabel = true,

                    colors =
                        dashboardNavigationColors()
                )
            }
        }
    }
}

/**
 * Colors shared by the three navigation destinations.
 */
@Composable
private fun dashboardNavigationColors() =
    NavigationBarItemDefaults.colors(

        selectedIconColor =
            OrbitalColors.Accent,

        selectedTextColor =
            OrbitalColors.Accent,

        indicatorColor =
            OrbitalColors.PanelRaised,

        unselectedIconColor =
            OrbitalColors.Muted,

        unselectedTextColor =
            OrbitalColors.Muted
    )

/**
 * Displays the heading on secondary pages.
 */
@Composable
private fun DashboardPageHeading(
    title: String,
    subtitle: String
) {

    Column(
        verticalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {

        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = subtitle,
            color = OrbitalColors.Muted,
            fontSize = 12.sp
        )

        HorizontalDivider(
            color = OrbitalColors.PanelRaised
        )
    }
}

/**
 * Displays fleet health and current
 * servicing vehicle mission status.
 */
@Composable
private fun DashboardHeader(
    state: OrbitalUiState
) {

    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        Text(
            text = "ORBITAL COMMAND",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text =
                "SATELLITE FLEET OPERATIONS • SIMULATION",
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

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "${state.satellites.size} SATELLITES",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (state.activeAlerts.isEmpty()) {
                    "NO ACTIVE ALERTS"
                } else {
                    "${state.activeAlerts.size} ACTIVE ALERT(S)"
                },

                color = if (state.activeAlerts.isEmpty()) {
                    OrbitalColors.Healthy
                } else {
                    OrbitalColors.Warning
                },

                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.unacknowledgedAlertCount > 0) {

            Text(
                text =
                    "${state.unacknowledgedAlertCount} " +
                            "alert(s) require acknowledgement",

                color = OrbitalColors.Warning,
                fontSize = 12.sp
            )
        }

        if (state.activeRepairOrders.isNotEmpty()) {

            Text(
                text =
                    "${state.activeRepairOrders.size} " +
                            "outstanding repair work order(s)",

                color = OrbitalColors.Accent,
                fontSize = 12.sp
            )
        }

        if (state.preparedMission != null) {

            Text(
                text =
                    "SERVICING MISSION " +
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
 * for detailed monitoring.
 */
@Composable
private fun SatelliteSelector(
    state: OrbitalUiState,
    onSelectSatellite: (String) -> Unit
) {

    DashboardPanel(
        title = "SELECT SATELLITE"
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                ),

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            state.satellites.forEach { satellite ->

                val alert =
                    satellite.hasFault ||
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
                                    if (alert) {
                                        " • ALERT"
                                    } else {
                                        ""
                                    }
                        )
                    }
                )
            }
        }

        Text(
            text =
                state.selectedSatellite.definition.name,

            color = OrbitalColors.Muted,
            fontSize = 13.sp
        )
    }
}

/**
 * Displays satellites, orbital servicing station,
 * and the deployed servicing vehicle.
 */
@Composable
private fun OrbitPanel(
    state: OrbitalUiState
) {

    DashboardPanel(
        title = "ORBITAL FLEET TRACKING"
    ) {

        OrbitVisualization(
            satellites = state.satellites,
            selectedSatelliteId =
                state.selectedSatelliteId,
            servicingStation =
                state.servicingStation,
            servicingVehicle =
                state.servicingVehicle
        )

        Text(
            text =
                "Selected: ${state.selectedSatelliteId}",

            color = OrbitalColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text =
                "OS-001: Green • " +
                        "SERVICER-01: Cyan ring • " +
                        "SAT-001: Cyan • " +
                        "SAT-002: Amber • " +
                        "SAT-003: Violet",

            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )

        if (!state.selectedSatellite.isConnected) {

            Text(
                text =
                    "Selected satellite position is " +
                            "the last received position.",

                color = OrbitalColors.Warning,
                fontSize = 12.sp
            )
        }

        Text(
            text =
                "Scripted 2D orbital illustration • " +
                        "Not to scale",

            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )
    }
}

/**
 * Summarizes the operational state of all
 * satellites, the servicing station,
 * and its robotic vehicle.
 */
@Composable
private fun FleetOverview(
    state: OrbitalUiState
) {

    DashboardPanel(
        title = "FLEET STATUS"
    ) {

        state.satellites.forEach { satellite ->

            val mode =
                satellite.onboardTelemetry.powerMode

            val status = when {

                satellite.isPowerDepleted ->
                    "POWER OFFLINE (SIM)"

                satellite.fault ==
                        SatelliteFault.COMMUNICATION_LOSS ->
                    "SIGNAL LOST"

                satellite.requiresServicing ->
                    "HARDWARE DAMAGE"

                satellite.fault != SatelliteFault.NONE ->
                    satellite.fault
                        .displayName.uppercase()

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
            status =
                state.servicingVehicle.phase
                    .displayName.uppercase(),
            warning = false
        )
    }
}

/**
 * Displays the last received telemetry
 * for the selected satellite.
 */
@Composable
private fun TelemetryPanel(
    satellite: SatelliteState,
    elapsedSeconds: Int
) {

    val telemetry =
        satellite.telemetry

    DashboardPanel(
        title =
            "${satellite.definition.id} TELEMETRY"
    ) {

        if (!satellite.isConnected) {

            Text(
                text =
                    "NO LIVE TELEMETRY — " +
                            "Displaying last received measurements.",

                color = OrbitalColors.Warning,
                fontSize = 12.sp
            )
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
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
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
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
            text =
                "Last telemetry received: " +
                        "${satellite.packetAgeSeconds(elapsedSeconds)} " +
                        "second(s) ago",

            color = if (satellite.isConnected) {
                OrbitalColors.Muted
            } else {
                OrbitalColors.Warning
            },

            fontSize = 12.sp
        )
    }
}

/**
 * Displays the satellite's historical
 * received telemetry.
 */
@Composable
private fun TelemetryHistoryPanel(
    satellite: SatelliteState
) {

    DashboardPanel(
        title = "TELEMETRY HISTORY"
    ) {

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

    DashboardPanel(
        title = "SYSTEM DIAGNOSTICS"
    ) {

        if (!satellite.isConnected) {

            StatusRow(
                label = "Power subsystem",
                status = "UNVERIFIED",
                warning = true
            )

            StatusRow(
                label = "Thermal subsystem",
                status = "UNVERIFIED",
                warning = true
            )

            StatusRow(
                label = "Payload",
                status = "UNVERIFIED",
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

            val powerStatus =
                when (telemetry.powerMode) {

                    PowerMode.SAFE_MODE ->
                        "SAFE MODE"

                    PowerMode.LOW_POWER ->
                        "LOW POWER"

                    else ->
                        if (powerWarning) {
                            "WARNING"
                        } else {
                            "NOMINAL"
                        }
                }

            StatusRow(
                label = "Power subsystem",
                status = powerStatus,
                warning = powerWarning
            )

            StatusRow(
                label = "Thermal subsystem",

                status = if (thermalWarning) {
                    "WARNING"
                } else {
                    "NOMINAL"
                },

                warning = thermalWarning
            )

            StatusRow(
                label = "Payload",

                status = when {

                    payloadWarning ->
                        "FAILED"

                    telemetry.powerMode ==
                            PowerMode.SAFE_MODE ->
                        "POWER SAVING"

                    else ->
                        "NOMINAL"
                },

                warning =
                    payloadWarning ||
                            telemetry.powerMode ==
                            PowerMode.SAFE_MODE
            )
        }

        StatusRow(
            label = "Communications",

            status = if (satellite.isConnected) {
                "ONLINE"
            } else {
                "OFFLINE"
            },

            warning = !satellite.isConnected
        )

        if (satellite.requiresServicing) {

            Text(
                text =
                    "SIMULATED HARDWARE DAMAGE: " +
                            damage.displayName,

                color = OrbitalColors.Warning,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Provides temporary fault simulation controls
 * and the ability to reset the fleet simulation.
 */
@Composable
private fun FaultControlPanel(
    satellite: SatelliteState,
    elapsedSeconds: Int,
    onSetFault: (SatelliteFault) -> Unit,
    onClearFault: () -> Unit,
    onReset: () -> Unit,
    isSimulationPaused: Boolean,
    onToggleSimulationPaused: () -> Unit
) {

    DashboardPanel(
        title = "FAULT SIMULATION"
    ) {

        Text(
            text =
                "Target: ${satellite.definition.id}",

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
                .horizontalScroll(
                    rememberScrollState()
                ),

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            SatelliteFault.entries
                .filter {
                    it != SatelliteFault.NONE
                }
                .forEach { fault ->

                    FilterChip(
                        selected =
                            satellite.fault == fault,

                        onClick = {
                            onSetFault(fault)
                        },

                        label = {
                            Text(
                                fault.displayName
                            )
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

            enabled =
                satellite.fault != SatelliteFault.NONE,

            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                "Clear Temporary Fault"
            )
        }

        Text(
            text =
                "Equipment damage requires completion " +
                        "of a repair work order.",

            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                "Reset Entire Simulation"
            )
        }

        // Pausing is separate from Reset: the mission and repair state remain.
        OutlinedButton(
            onClick = onToggleSimulationPaused,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSimulationPaused) "Resume Simulation" else "Pause Simulation")
        }

        Text(
            text = if (isSimulationPaused) {
                "Simulation paused. Elapsed time, telemetry, and mission movement " +
                    "will remain frozen even after closing the app."
            } else {
                "Simulation live. Mission time continues while the app is closed."
            },
            color = if (isSimulationPaused) OrbitalColors.Warning else OrbitalColors.Muted,
            fontSize = 11.sp
        )

        Text(
            text =
                "Mission elapsed time: " +
                        "$elapsedSeconds seconds",

            color = OrbitalColors.Muted,
            fontSize = 12.sp
        )
    }
}

/**
 * Provides a reusable dashboard panel container
 * for fleet data and monitoring controls.
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
 * Displays one formatted telemetry measurement.
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
            containerColor =
                OrbitalColors.PanelRaised
        )
    ) {

        Column(
            modifier = Modifier.padding(12.dp),

            verticalArrangement =
                Arrangement.spacedBy(6.dp)
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
 * Displays one system or mission status row.
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

        verticalAlignment =
            Alignment.CenterVertically
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

            color = if (warning) {
                OrbitalColors.Warning
            } else {
                OrbitalColors.Healthy
            },

            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
