
package com.example.orbitalcommand.ui

import androidx.compose.foundation.background
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
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.ui.theme.OrbitalColors
import com.example.orbitalcommand.viewmodel.OrbitalViewModel
import java.util.Locale

/**
 * Connects the mission-control ViewModel to the dashboard.
 *
 * Collects observable application state in a lifecycle-aware
 * manner and forwards operator actions to the ViewModel.
 */
@Composable
fun OrbitalRoute(
    viewModel: OrbitalViewModel = viewModel()
) {

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    OrbitalDashboard(
        state = state,
        onToggleConnection = viewModel::toggleConnection,
        onReset = viewModel::resetSimulation
    )
}

/**
 * Displays the mission-control interface.
 *
 * Uses a vertical layout on narrow screens and a
 * two-column layout on wider tablet displays.
 */
@Composable
fun OrbitalDashboard(
    state: OrbitalUiState,
    onToggleConnection: () -> Unit,
    onReset: () -> Unit
) {

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OrbitalColors.Background)
    ) {

        val useTabletLayout = maxWidth >= 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            DashboardHeader(state)

            if (useTabletLayout) {

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

                        SystemHealthPanel(state)
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement =
                            Arrangement.spacedBy(16.dp)
                    ) {

                        TelemetryPanel(state)

                        TelemetryHistoryPanel(state)

                        SimulationControls(
                            state = state,
                            onToggleConnection = onToggleConnection,
                            onReset = onReset
                        )
                    }
                }

            } else {

                OrbitPanel(state)

                TelemetryPanel(state)

                TelemetryHistoryPanel(state)

                SystemHealthPanel(state)

                SimulationControls(
                    state = state,
                    onToggleConnection = onToggleConnection,
                    onReset = onReset
                )
            }
        }
    }
}

/**
 * Displays the application title, satellite identifier,
 * and communication status.
 */
@Composable
private fun DashboardHeader(state: OrbitalUiState) {

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
            text = "SATELLITE OPERATIONS • SIMULATION",
            color = OrbitalColors.Muted,
            fontSize = 12.sp
        )

        HorizontalDivider(
            color = OrbitalColors.PanelRaised
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "SAT-001",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (state.linkLost)
                    "SIGNAL LOST"
                else
                    "SYSTEM ONLINE",
                color = if (state.linkLost)
                    OrbitalColors.Warning
                else
                    OrbitalColors.Healthy,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Displays the latest received satellite position
 * in a stylized orbital visualization.
 */
@Composable
private fun OrbitPanel(state: OrbitalUiState) {

    DashboardPanel(title = "ORBITAL TRACKING") {

        OrbitVisualization(
            angleDegrees = state.telemetry.orbitAngleDegrees
        )

        Text(
            text = if (state.linkLost)
                "LAST KNOWN SATELLITE POSITION"
            else
                "RECEIVING SATELLITE POSITION",
            color = if (state.linkLost)
                OrbitalColors.Warning
            else
                OrbitalColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Accelerated orbit illustration • Not to scale",
            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )
    }
}

/**
 * Displays the most recently received telemetry measurements.
 *
 * Readings remain visible during communication loss and are
 * identified as stale until the connection is restored.
 */
@Composable
private fun TelemetryPanel(state: OrbitalUiState) {

    val telemetry = state.telemetry

    DashboardPanel(title = "SATELLITE TELEMETRY") {

        if (state.linkLost) {

            Text(
                text = "WARNING: Showing last received measurements",
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
            text = "Last telemetry: " +
                    if (state.packetAgeSeconds == 0)
                        "Just now"
                    else
                        "${state.packetAgeSeconds} seconds ago",
            color = if (state.linkLost)
                OrbitalColors.Warning
            else
                OrbitalColors.Muted,
            fontSize = 12.sp
        )
    }
}

/**
 * Displays a selectable chart of received telemetry.
 *
 * History comes directly from observable dashboard state.
 * Chart selection is handled by the chart component.
 */
@Composable
private fun TelemetryHistoryPanel(state: OrbitalUiState) {

    DashboardPanel(title = "TELEMETRY HISTORY") {

        TelemetryHistoryChart(
            history = state.history,
            isLinkLost = state.linkLost
        )
    }
}

/**
 * Displays the health of the monitored satellite subsystems.
 */
@Composable
private fun SystemHealthPanel(state: OrbitalUiState) {

    val lowBattery =
        state.telemetry.batteryPercent < 15.0

    val highTemperature =
        state.telemetry.temperatureC > 40.0

    DashboardPanel(title = "SYSTEM HEALTH") {

        StatusRow(
            label = "Power subsystem",
            status = if (lowBattery)
                "LOW POWER"
            else
                "NOMINAL",
            warning = lowBattery
        )

        StatusRow(
            label = "Thermal subsystem",
            status = if (highTemperature)
                "WARNING"
            else
                "NOMINAL",
            warning = highTemperature
        )

        StatusRow(
            label = "Communications",
            status = if (state.linkLost)
                "OFFLINE"
            else
                "ONLINE",
            warning = state.linkLost
        )
    }
}

/**
 * Provides operator controls for the local simulation.
 */
@Composable
private fun SimulationControls(
    state: OrbitalUiState,
    onToggleConnection: () -> Unit,
    onReset: () -> Unit
) {

    DashboardPanel(title = "SIMULATION CONTROLS") {

        Button(
            onClick = onToggleConnection,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = if (state.linkLost)
                    "Restore Connection"
                else
                    "Simulate Communication Failure"
            )
        }

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Reset Simulation")
        }

        Text(
            text = "Mission elapsed time: " +
                    "${state.elapsedSeconds} seconds",
            color = OrbitalColors.Muted,
            fontSize = 12.sp
        )
    }
}

/**
 * Provides a reusable visual container for dashboard sections.
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
 * Displays a subsystem's current health status.
 */
@Composable
private fun StatusRow(
    label: String,
    status: String,
    warning: Boolean
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp
        )

        Text(
            text = status,
            color = if (warning)
                OrbitalColors.Warning
            else
                OrbitalColors.Healthy,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
