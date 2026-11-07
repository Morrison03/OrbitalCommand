package com.example.orbitalcommand.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitalcommand.model.OrbitalUiState
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
 * Manages satellite simulation state and operator actions.
 *
 * Publishes current telemetry, historical measurements,
 * and communication status through a read-only StateFlow.
 */
class OrbitalViewModel : ViewModel() {

    private val initialTelemetry =
        SatelliteSimulator.snapshot(0)

    private val _uiState = MutableStateFlow(
        OrbitalUiState(
            elapsedSeconds = 0,
            telemetry = initialTelemetry,
            linkLost = false,
            history = listOf(initialTelemetry)
        )
    )

    /**
     * Observable dashboard state consumed by the UI.
     */
    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Advances the simulation clock once per second.
     *
     * A new telemetry packet is added to the history
     * only when the communication link is active.
     *
     * During an outage, elapsed simulation time
     * continues advancing while received data remains
     * frozen.
     */
    private fun startSimulation() {

        viewModelScope.launch {

            while (isActive) {

                delay(1000L)

                _uiState.update { current ->

                    val nextSecond =
                        current.elapsedSeconds + 1

                    if (current.linkLost) {

                        current.copy(
                            elapsedSeconds = nextSecond
                        )

                    } else {

                        val newTelemetry =
                            SatelliteSimulator.snapshot(
                                nextSecond
                            )

                        val updatedHistory =
                            TelemetryHistory.append(
                                history = current.history,
                                sample = newTelemetry
                            )

                        current.copy(
                            elapsedSeconds = nextSecond,
                            telemetry = newTelemetry,
                            history = updatedHistory
                        )
                    }
                }
            }
        }
    }

    /**
     * Simulates losing or restoring communications.
     *
     * Restoring the connection immediately receives
     * an updated telemetry packet and adds it to
     * the historical measurements.
     */
    fun toggleConnection() {

        _uiState.update { current ->

            if (current.linkLost) {

                val newTelemetry =
                    SatelliteSimulator.snapshot(
                        current.elapsedSeconds
                    )

                val updatedHistory =
                    TelemetryHistory.append(
                        history = current.history,
                        sample = newTelemetry
                    )

                current.copy(
                    linkLost = false,
                    telemetry = newTelemetry,
                    history = updatedHistory
                )

            } else {

                current.copy(
                    linkLost = true
                )
            }
        }
    }

    /**
     * Returns the satellite simulation to its
     * initial state and clears historical telemetry.
     */
    fun resetSimulation() {

        val initialTelemetry =
            SatelliteSimulator.snapshot(0)

        _uiState.value = OrbitalUiState(
            elapsedSeconds = 0,
            telemetry = initialTelemetry,
            linkLost = false,
            history = listOf(initialTelemetry)
        )
    }
}

