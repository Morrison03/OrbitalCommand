
package com.example.orbitalcommand.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitalcommand.model.MissionEvent
import com.example.orbitalcommand.model.MissionEventKind
import com.example.orbitalcommand.model.MissionEventLog
import com.example.orbitalcommand.model.MissionEventSeverity
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.PowerMode
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
 * Manages fleet state, simulated spacecraft power,
 * telemetry reception, faults, and operational alerts.
 *
 * All observable application state is published
 * through a read-only StateFlow.
 */
class OrbitalViewModel : ViewModel() {

    companion object {
        private const val MANUAL_FAULT_SOURCE = "MANUAL_FAULT"
        private const val POWER_STATUS_SOURCE = "POWER_STATUS"
    }

    private val _uiState = MutableStateFlow(
        createInitialState()
    )

    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Creates the initial spacecraft fleet and
     * mission event history.
     */
    private fun createInitialState(): OrbitalUiState {

        val satellites = SatelliteCatalog.satellites.map {
                definition ->

            val initialTelemetry =
                SatelliteSimulator.initialTelemetry(
                    definition
                )

            SatelliteState(
                definition = definition,
                telemetry = initialTelemetry,
                onboardTelemetry = initialTelemetry,
                history = listOf(initialTelemetry)
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
     * Updates every simulated satellite once per second.
     *
     * Onboard state advances regardless of the downlink
     * condition. Received telemetry is updated only when
     * the satellite has an operational connection.
     *
     * Changes in power operating mode generate events.
     */
    private fun startSimulation() {

        viewModelScope.launch {

            while (isActive) {

                delay(1000L)

                _uiState.update { current ->

                    val nextSecond =
                        current.elapsedSeconds + 1

                    var updatedEvents = current.events

                    val updatedSatellites =
                        current.satellites.map { satellite ->

                            val previousMode =
                                satellite.onboardTelemetry.powerMode

                            val onboard =
                                SatelliteSimulator.advance(
                                    previous =
                                        satellite.onboardTelemetry,
                                    second = nextSecond,
                                    satellite =
                                        satellite.definition,
                                    fault = satellite.fault,
                                    faultStartedSecond =
                                        satellite.faultStartedSecond
                                )

                            val updatedSatellite =
                                satellite.copy(
                                    onboardTelemetry = onboard
                                )

                            updatedEvents = updatePowerEvents(
                                history = updatedEvents,
                                satelliteId =
                                    satellite.definition.id,
                                previousMode = previousMode,
                                currentMode = onboard.powerMode,
                                atSecond = nextSecond
                            )

                            if (!updatedSatellite.isConnected) {

                                updatedSatellite

                            } else {

                                val history =
                                    TelemetryHistory.append(
                                        history = satellite.history,
                                        sample = onboard
                                    )

                                updatedSatellite.copy(
                                    telemetry = onboard,
                                    history = history
                                )
                            }
                        }

                    current.copy(
                        elapsedSeconds = nextSecond,
                        satellites = updatedSatellites,
                        events = updatedEvents
                    )
                }
            }
        }
    }

    /**
     * Selects the spacecraft displayed in the
     * detailed telemetry and diagnostic panels.
     */
    fun selectSatellite(satelliteId: String) {

        _uiState.update { current ->

            if (
                current.satellites.any {
                    it.definition.id == satelliteId
                }
            ) {

                current.copy(
                    selectedSatelliteId = satelliteId
                )

            } else {
                current
            }
        }
    }

    /**
     * Applies or clears an injected satellite fault.
     *
     * The onboard battery charge is preserved when
     * a fault changes. Electrical effects accumulate
     * during subsequent simulation updates.
     *
     * Manual fault alerts are resolved independently
     * of automatic power status alerts.
     */
    fun setFault(fault: SatelliteFault) {

        _uiState.update { current ->

            val selected = current.selectedSatellite

            if (selected.fault == fault) {
                return@update current
            }

            val now = current.elapsedSeconds

            val faultStart =
                if (fault == SatelliteFault.NONE) {
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

                        val onboard =
                            SatelliteSimulator.refresh(
                                previous =
                                    satellite.onboardTelemetry,
                                satellite =
                                    satellite.definition,
                                fault = fault,
                                faultStartedSecond = faultStart
                            )

                        val updated = satellite.copy(
                            fault = fault,
                            faultStartedSecond = faultStart,
                            onboardTelemetry = onboard
                        )

                        if (!updated.isConnected) {

                            updated

                        } else {

                            val history =
                                TelemetryHistory.append(
                                    history = satellite.history,
                                    sample = onboard
                                )

                            updated.copy(
                                telemetry = onboard,
                                history = history
                            )
                        }
                    }
                }

            var updatedEvents = current.events

            if (selected.fault != SatelliteFault.NONE) {

                updatedEvents =
                    MissionEventLog.resolveActiveForSource(
                        history = updatedEvents,
                        satelliteId = selected.definition.id,
                        sourceKey = MANUAL_FAULT_SOURCE,
                        atSecond = now
                    )

                val recoveryTitle =
                    if (
                        selected.fault ==
                        SatelliteFault.COMMUNICATION_LOSS
                    ) {
                        "Communications fault cleared"
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

            if (fault != SatelliteFault.NONE) {

                updatedEvents = appendEvent(
                    history = updatedEvents,
                    satelliteId = selected.definition.id,
                    atSecond = now,
                    title = fault.displayName,
                    description = fault.description,
                    severity = severityForFault(fault),
                    kind = MissionEventKind.ALERT,
                    sourceKey = MANUAL_FAULT_SOURCE
                )
            }

            current.copy(
                satellites = updatedSatellites,
                events = updatedEvents
            )
        }
    }

    /**
     * Removes the injected fault from the
     * currently selected spacecraft.
     */
    fun clearFault() {

        setFault(SatelliteFault.NONE)
    }

    /**
     * Records acknowledgement of an active alert.
     *
     * Acknowledging does not resolve either a
     * power condition or an injected fault.
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
                            "The underlying condition remains active.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                events = updatedEvents
            )
        }
    }

    /**
     * Generates a power alert when a spacecraft enters
     * a new warning or offline operating state.
     *
     * Power alerts are based on internal simulation
     * truth. When telemetry is unavailable, the
     * application must identify the state as simulated
     * rather than independently verified by downlink.
     */
    private fun updatePowerEvents(
        history: List<MissionEvent>,
        satelliteId: String,
        previousMode: PowerMode,
        currentMode: PowerMode,
        atSecond: Int
    ): List<MissionEvent> {

        if (previousMode == currentMode) {
            return history
        }

        var updated = MissionEventLog.resolveActiveForSource(
            history = history,
            satelliteId = satelliteId,
            sourceKey = POWER_STATUS_SOURCE,
            atSecond = atSecond
        )

        if (currentMode == PowerMode.NORMAL) {

            return appendEvent(
                history = updated,
                satelliteId = satelliteId,
                atSecond = atSecond,
                title = "Normal power restored",
                description =
                    "The simulated battery has recovered " +
                            "to normal operating charge.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )
        }

        val title: String
        val description: String
        val severity: MissionEventSeverity

        when (currentMode) {

            PowerMode.LOW_POWER -> {
                title = "Low battery"
                description =
                    "The simulated spacecraft is reducing " +
                            "power consumption."
                severity = MissionEventSeverity.WARNING
            }

            PowerMode.SAFE_MODE -> {
                title = "Power safe mode"
                description =
                    "Nonessential simulated systems are " +
                            "disabled to conserve battery charge."
                severity = MissionEventSeverity.CRITICAL
            }

            PowerMode.OFFLINE -> {
                title = "Simulated power depletion"
                description =
                    "The internal simulation reports power " +
                            "depletion. Ground telemetry is unavailable."
                severity = MissionEventSeverity.CRITICAL
            }

            PowerMode.NORMAL -> {
                return updated
            }
        }

        updated = appendEvent(
            history = updated,
            satelliteId = satelliteId,
            atSecond = atSecond,
            title = title,
            description = description,
            severity = severity,
            kind = MissionEventKind.ALERT,
            sourceKey = POWER_STATUS_SOURCE
        )

        return updated
    }

    /**
     * Returns the operator-facing severity
     * for an injected fault.
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
     * Adds an event using the next available identifier.
     *
     * Event identifiers are derived from the most recent
     * retained event and are unique within the current
     * in-memory simulation.
     */
    private fun appendEvent(
        history: List<MissionEvent>,
        satelliteId: String,
        atSecond: Int,
        title: String,
        description: String,
        severity: MissionEventSeverity,
        kind: MissionEventKind,
        sourceKey: String? = null
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
            kind = kind,
            sourceKey = sourceKey
        )

        return MissionEventLog.record(
            history = history,
            event = event
        )
    }

    /**
     * Resets the satellite fleet, power model,
     * telemetry histories, and mission event history.
     */
    fun resetSimulation() {

        _uiState.value = createInitialState()
    }
}
