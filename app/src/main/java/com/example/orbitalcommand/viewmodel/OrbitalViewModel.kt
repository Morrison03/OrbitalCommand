
package com.example.orbitalcommand.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Manages the satellite fleet simulation and operator actions.
 *
 * Every spacecraft maintains independent telemetry and fault
 * state. The ViewModel publishes a single immutable fleet
 * state through StateFlow for the Compose interface.
 */
class OrbitalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        createInitialState()
    )

    /**
     * Read-only state observed by the mission dashboard.
     */
    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Creates the initial state for every monitored spacecraft.
     */
    private fun createInitialState(): OrbitalUiState {

        val satellites = SatelliteCatalog.satellites.map {
                definition ->

            val initialTelemetry =
                SatelliteSimulator.snapshot(
                    second = 0,
                    satellite = definition
                )

            SatelliteState(
                definition = definition,
                telemetry = initialTelemetry,
                history = listOf(initialTelemetry)
            )
        }

        return OrbitalUiState(
            elapsedSeconds = 0,
            satellites = satellites,
            selectedSatelliteId =
                SatelliteCatalog.satellites.first().id
        )
    }

    /**
     * Advances simulation time once per second.
     *
     * Each connected spacecraft produces a new telemetry
     * packet, which is stored in its independent history.
     *
     * A spacecraft with a communication failure retains
     * its last received telemetry while the fleet clock
     * continues advancing.
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

                                val newTelemetry =
                                    SatelliteSimulator.snapshot(
                                        second = nextSecond,
                                        satellite = satellite.definition,
                                        fault = satellite.fault,
                                        faultStartedSecond =
                                            satellite.faultStartedSecond
                                    )

                                val newHistory =
                                    TelemetryHistory.append(
                                        history = satellite.history,
                                        sample = newTelemetry
                                    )

                                satellite.copy(
                                    telemetry = newTelemetry,
                                    history = newHistory
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
     * Selects the spacecraft displayed in the detailed
     * telemetry and diagnostics panels.
     *
     * Selection does not pause other satellites.
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
     * Applies or clears a simulated fault on the
     * currently selected spacecraft.
     *
     * Other spacecraft remain unaffected.
     *
     * A communication failure prevents new telemetry
     * packets from reaching the ground station.
     *
     * Clearing the communication failure immediately
     * receives a new telemetry packet.
     */
    fun setFault(fault: SatelliteFault) {

        _uiState.update { current ->

            val updatedSatellites =
                current.satellites.map { satellite ->

                    if (
                        satellite.definition.id !=
                        current.selectedSatelliteId
                    ) {

                        satellite

                    } else {

                        val faultStart =
                            if (fault == SatelliteFault.NONE) {
                                null
                            } else {
                                current.elapsedSeconds
                            }

                        val updatedSatellite =
                            satellite.copy(
                                fault = fault,
                                faultStartedSecond = faultStart
                            )

                        if (!updatedSatellite.isConnected) {

                            updatedSatellite

                        } else {

                            val newTelemetry =
                                SatelliteSimulator.snapshot(
                                    second = current.elapsedSeconds,
                                    satellite = satellite.definition,
                                    fault = fault,
                                    faultStartedSecond = faultStart
                                )

                            val newHistory =
                                TelemetryHistory.append(
                                    history = satellite.history,
                                    sample = newTelemetry
                                )

                            updatedSatellite.copy(
                                telemetry = newTelemetry,
                                history = newHistory
                            )
                        }
                    }
                }

            current.copy(
                satellites = updatedSatellites
            )
        }
    }

    /**
     * Removes the active fault from the selected satellite.
     */
    fun clearFault() {

        setFault(SatelliteFault.NONE)
    }

    /**
     * Restores all satellites to their initial conditions
     * and clears their telemetry history and faults.
     */
    fun resetSimulation() {

        _uiState.value = createInitialState()
    }
}
