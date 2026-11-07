
package com.example.orbitalcommand.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitalcommand.model.MissionEvent
import com.example.orbitalcommand.model.MissionEventKind
import com.example.orbitalcommand.model.MissionEventLog
import com.example.orbitalcommand.model.MissionEventSeverity
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.TelemetryHistory
import com.example.orbitalcommand.simulation.SatelliteSimulator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages the satellite fleet, simulated faults,
 * telemetry history, and mission event lifecycle.
 *
 * Publishes immutable application state through StateFlow.
 */
class OrbitalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        createInitialState()
    )

    /**
     * Observable state consumed by the mission dashboard.
     */
    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Initializes the satellite fleet and event history.
     */
    private fun createInitialState(): OrbitalUiState {

        val satellites = SatelliteCatalog.satellites.map {
                definition ->

            val telemetry = SatelliteSimulator.snapshot(
                second = 0,
                satellite = definition
            )

            SatelliteState(
                definition = definition,
                telemetry = telemetry,
                history = listOf(telemetry)
            )
        }

        val initialEvent = MissionEvent(
            id = 1L,
            satelliteId = "FLEET",
            occurredAtSecond = 0,
            title = "Simulation initialized",
            description =
                "Satellite fleet monitoring has started.",
            severity = MissionEventSeverity.INFO,
            kind = MissionEventKind.ACTIVITY
        )

        return OrbitalUiState(
            elapsedSeconds = 0,
            satellites = satellites,
            selectedSatelliteId =
                SatelliteCatalog.satellites.first().id,
            events = listOf(initialEvent)
        )
    }

    /**
     * Advances simulation time once per second.
     *
     * Each connected satellite receives a new telemetry
     * packet. Satellites with a communication failure
     * retain their last received measurements.
     */
    private fun startSimulation() {

        viewModelScope.launch {

            while (isActive) {

                delay(1000L)

                _uiState.update { current ->

                    val nextSecond =
                        current.elapsedSeconds + 1

                    val updatedSatellites =
                        current.satellites.map { satellite ->

                            if (!satellite.isConnected) {

                                satellite

                            } else {

                                val telemetry =
                                    SatelliteSimulator.snapshot(
                                        second = nextSecond,
                                        satellite =
                                            satellite.definition,
                                        fault = satellite.fault,
                                        faultStartedSecond =
                                            satellite.faultStartedSecond
                                    )

                                val history =
                                    TelemetryHistory.append(
                                        history = satellite.history,
                                        sample = telemetry
                                    )

                                satellite.copy(
                                    telemetry = telemetry,
                                    history = history
                                )
                            }
                        }

                    current.copy(
                        elapsedSeconds = nextSecond,
                        satellites = updatedSatellites
                    )
                }
            }
        }
    }

    /**
     * Changes which satellite is displayed in the
     * detailed telemetry and diagnostic panels.
     */
    fun selectSatellite(satelliteId: String) {

        _uiState.update { current ->

            val exists = current.satellites.any {
                it.definition.id == satelliteId
            }

            if (exists) {
                current.copy(
                    selectedSatelliteId = satelliteId
                )
            } else {
                current
            }
        }
    }

    /**
     * Applies a simulated fault to the selected satellite.
     *
     * Replacing or clearing an existing fault resolves its
     * active alert and records a recovery event.
     *
     * Injecting a new fault creates an actionable alert.
     */
    fun setFault(fault: SatelliteFault) {

        _uiState.update { current ->

            val selected = current.selectedSatellite

            if (selected.fault == fault) {
                return@update current
            }

            val now = current.elapsedSeconds

            val faultStart = if (
                fault == SatelliteFault.NONE
            ) {
                null
            } else {
                now
            }

            val updatedSatellites =
                current.satellites.map { satellite ->

                    if (
                        satellite.definition.id !=
                        selected.definition.id
                    ) {

                        satellite

                    } else {

                        val updated = satellite.copy(
                            fault = fault,
                            faultStartedSecond = faultStart
                        )

                        if (!updated.isConnected) {

                            updated

                        } else {

                            val telemetry =
                                SatelliteSimulator.snapshot(
                                    second = now,
                                    satellite = satellite.definition,
                                    fault = fault,
                                    faultStartedSecond = faultStart
                                )

                            val history =
                                TelemetryHistory.append(
                                    history = satellite.history,
                                    sample = telemetry
                                )

                            updated.copy(
                                telemetry = telemetry,
                                history = history
                            )
                        }
                    }
                }

            var updatedEvents = current.events

            // Resolve the previous alert when its fault ends.
            if (selected.fault != SatelliteFault.NONE) {

                updatedEvents =
                    MissionEventLog.resolveActiveForSatellite(
                        history = updatedEvents,
                        satelliteId = selected.definition.id,
                        atSecond = now
                    )

                val recoveryTitle =
                    if (
                        selected.fault ==
                        SatelliteFault.COMMUNICATION_LOSS
                    ) {
                        "Communications restored"
                    } else {
                        "Fault cleared"
                    }

                updatedEvents = appendEvent(
                    history = updatedEvents,
                    satelliteId = selected.definition.id,
                    atSecond = now,
                    title = recoveryTitle,
                    description =
                        "${selected.fault.displayName} " +
                                "is no longer active.",
                    severity = MissionEventSeverity.INFO,
                    kind = MissionEventKind.ACTIVITY
                )
            }

            // Create an alert for the newly injected fault.
            if (fault != SatelliteFault.NONE) {

                updatedEvents = appendEvent(
                    history = updatedEvents,
                    satelliteId = selected.definition.id,
                    atSecond = now,
                    title = fault.displayName,
                    description = fault.description,
                    severity = severityForFault(fault),
                    kind = MissionEventKind.ALERT
                )
            }

            current.copy(
                satellites = updatedSatellites,
                events = updatedEvents
            )
        }
    }

    /**
     * Clears the fault affecting the selected spacecraft.
     */
    fun clearFault() {

        setFault(SatelliteFault.NONE)
    }

    /**
     * Records operator acknowledgement of an active alert.
     *
     * Acknowledgement does not clear the satellite fault
     * or change the alert's active status.
     */
    fun acknowledgeAlert(eventId: Long) {

        _uiState.update { current ->

            val alert = current.events.firstOrNull {
                it.id == eventId &&
                        it.isActive &&
                        !it.isAcknowledged
            } ?: return@update current

            val now = current.elapsedSeconds

            val acknowledgedEvents =
                MissionEventLog.acknowledge(
                    history = current.events,
                    eventId = eventId,
                    atSecond = now
                )

            val updatedEvents = appendEvent(
                history = acknowledgedEvents,
                satelliteId = alert.satelliteId,
                atSecond = now,
                title = "Alert acknowledged",
                description =
                    "Operator acknowledged ${alert.title}. " +
                            "The underlying fault remains active.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                events = updatedEvents
            )
        }
    }

    /**
     * Restores the fleet and mission event history
     * to their initial simulation state.
     */
    fun resetSimulation() {

        _uiState.value = createInitialState()
    }

    /**
     * Determines the operator-facing severity of
     * a simulated satellite fault.
     */
    private fun severityForFault(
        fault: SatelliteFault
    ): MissionEventSeverity {

        return when (fault) {

            SatelliteFault.COMMUNICATION_LOSS,
            SatelliteFault.PAYLOAD_FAILURE ->
                MissionEventSeverity.CRITICAL

            SatelliteFault.POWER_DRAIN,
            SatelliteFault.THERMAL_OVERLOAD ->
                MissionEventSeverity.WARNING

            SatelliteFault.NONE ->
                MissionEventSeverity.INFO
        }
    }

    /**
     * Appends a uniquely identified mission event
     * to the bounded event history.
     */
    private fun appendEvent(
        history: List<MissionEvent>,
        satelliteId: String,
        atSecond: Int,
        title: String,
        description: String,
        severity: MissionEventSeverity,
        kind: MissionEventKind
    ): List<MissionEvent> {

        val nextId = (
                history.maxOfOrNull { it.id } ?: 0L
                ) + 1L

        val event = MissionEvent(
            id = nextId,
            satelliteId = satelliteId,
            occurredAtSecond = atSecond,
            title = title,
            description = description,
            severity = severity,
            kind = kind
        )

        return MissionEventLog.record(
            history = history,
            event = event
        )
    }
}
