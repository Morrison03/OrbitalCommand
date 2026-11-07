
package com.example.orbitalcommand.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.orbitalcommand.model.MissionEvent
import com.example.orbitalcommand.model.MissionEventKind
import com.example.orbitalcommand.model.MissionEventSeverity
import com.example.orbitalcommand.ui.theme.OrbitalColors

/**
 * Defines the filtering options available for
 * the mission event history.
 */
private enum class HistoryFilter(
    val label: String
) {
    ALL("All"),
    ALERTS("Alerts"),
    ACTIVITY("Operations")
}

/**
 * Returns the interface color corresponding to
 * an event's severity.
 */
private fun severityColor(
    severity: MissionEventSeverity
): Color {

    return when (severity) {

        MissionEventSeverity.INFO ->
            OrbitalColors.Accent

        MissionEventSeverity.WARNING ->
            Color(0xFFFBBF24)

        MissionEventSeverity.CRITICAL ->
            OrbitalColors.Warning
    }
}

/**
 * Displays all unresolved fleet alerts.
 *
 * Operators can inspect an affected satellite or
 * acknowledge an alert without resolving its fault.
 */
@Composable
fun MissionAlertsPanel(
    alerts: List<MissionEvent>,
    onSelectSatellite: (String) -> Unit,
    onAcknowledgeAlert: (Long) -> Unit
) {

    val sortedAlerts = alerts.sortedWith(
        compareByDescending<MissionEvent> {
            it.severity.ordinal
        }.thenByDescending {
            it.id
        }
    )

    val unacknowledgedCount = sortedAlerts.count {
        !it.isAcknowledged
    }

    MissionPanel(
        title = "MISSION ALERTS"
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "${sortedAlerts.size} active alert(s)",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "$unacknowledgedCount unacknowledged",
                color = if (unacknowledgedCount > 0)
                    OrbitalColors.Warning
                else
                    OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }

        if (sortedAlerts.isEmpty()) {

            Text(
                text = "No active mission alerts.",
                color = OrbitalColors.Healthy,
                fontSize = 14.sp
            )

            Text(
                text = "All simulated satellite faults " +
                        "are currently cleared.",
                color = OrbitalColors.Muted,
                fontSize = 12.sp
            )

        } else {

            sortedAlerts.forEach { alert ->

                AlertCard(
                    event = alert,
                    onInspect = {
                        onSelectSatellite(alert.satelliteId)
                    },
                    onAcknowledge = {
                        onAcknowledgeAlert(alert.id)
                    }
                )
            }
        }
    }
}

/**
 * Displays one active mission alert with controls
 * for inspection and acknowledgement.
 */
@Composable
private fun AlertCard(
    event: MissionEvent,
    onInspect: () -> Unit,
    onAcknowledge: () -> Unit
) {

    val color = severityColor(event.severity)

    Card(
        modifier = Modifier.fillMaxWidth(),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = event.satelliteId,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Text(
                    text = event.severity.name,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = event.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = event.description,
                color = OrbitalColors.Muted,
                fontSize = 12.sp
            )

            Text(
                text = "Detected T+${event.occurredAtSecond}s",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )

            Text(
                text = if (event.isAcknowledged)
                    "ACKNOWLEDGED — FAULT STILL ACTIVE"
                else
                    "UNACKNOWLEDGED — ACTIVE",
                color = if (event.isAcknowledged)
                    OrbitalColors.Accent
                else
                    color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = onInspect,
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "Inspect",
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = onAcknowledge,
                    enabled = !event.isAcknowledged,
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = if (event.isAcknowledged)
                            "Acknowledged"
                        else
                            "Acknowledge",
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Displays recent mission events in reverse
 * chronological order.
 *
 * Operators can filter the history by event category.
 */
@Composable
fun MissionEventHistoryPanel(
    events: List<MissionEvent>
) {

    var selectedFilter by rememberSaveable {
        mutableStateOf(HistoryFilter.ALL.name)
    }

    val filter = HistoryFilter.valueOf(
        selectedFilter
    )

    val filteredEvents = events.filter { event ->

        when (filter) {

            HistoryFilter.ALL -> true

            HistoryFilter.ALERTS ->
                event.kind == MissionEventKind.ALERT

            HistoryFilter.ACTIVITY ->
                event.kind == MissionEventKind.ACTIVITY
        }
    }.take(15)

    MissionPanel(
        title = "MISSION EVENT HISTORY"
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            HistoryFilter.entries.forEach { option ->

                FilterChip(
                    selected = filter == option,
                    onClick = {
                        selectedFilter = option.name
                    },
                    label = {
                        Text(option.label)
                    }
                )
            }
        }

        if (filteredEvents.isEmpty()) {

            Text(
                text = "No events match the selected filter.",
                color = OrbitalColors.Muted,
                fontSize = 13.sp
            )

        } else {

            filteredEvents.forEach { event ->

                EventHistoryEntry(event)
            }
        }

        Text(
            text = "Showing up to 15 recent events. " +
                    "Timestamps use simulated mission time.",
            color = OrbitalColors.Muted,
            fontSize = 11.sp
        )
    }
}

/**
 * Displays the details and current lifecycle state
 * of one recorded mission event.
 */
@Composable
private fun EventHistoryEntry(
    event: MissionEvent
) {

    val color = severityColor(event.severity)

    val status = when {

        event.isActive && event.isAcknowledged ->
            "ACTIVE • ACKNOWLEDGED"

        event.isActive ->
            "ACTIVE • UNACKNOWLEDGED"

        event.kind == MissionEventKind.ALERT ->
            "RESOLVED"

        else ->
            "RECORDED"
    }

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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = "T+${event.occurredAtSecond}s",
                    color = OrbitalColors.Muted,
                    fontSize = 11.sp
                )

                Text(
                    text = event.satelliteId,
                    color = OrbitalColors.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = event.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = event.description,
                color = OrbitalColors.Muted,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = event.severity.name,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = status,
                    color = if (
                        event.kind == MissionEventKind.ALERT &&
                        event.isActive
                    ) {
                        color
                    } else {
                        OrbitalColors.Muted
                    },
                    fontSize = 10.sp
                )
            }

            if (event.resolvedAtSecond != null) {

                Text(
                    text = "Resolved T+${event.resolvedAtSecond}s",
                    color = OrbitalColors.Healthy,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Provides a shared visual container for mission
 * alert and event history sections.
 */
@Composable
private fun MissionPanel(
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

