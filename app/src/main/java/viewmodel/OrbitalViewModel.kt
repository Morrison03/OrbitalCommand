
package com.example.orbitalcommand.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitalcommand.model.OrbitalUiState
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
 * Publishes immutable dashboard state through StateFlow
 * while keeping the underlying mutable state private.
 */
class OrbitalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        OrbitalUiState(
            elapsedSeconds = 0,
            telemetry = SatelliteSimulator.snapshot(0),
            linkLost = false
        )
    )

    /**
     * Observable dashboard state consumed by the interface.
     */
    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Advances the simulation clock once per second.
     *
     * During a communication outage, simulation time
     * continues advancing, but the dashboard retains
     * its last received telemetry packet.
     */
    private fun startSimulation() {

        viewModelScope.launch {

            while (isActive) {

                delay(1000L)

                _uiState.update { current ->

                    val nextSecond =
                        current.elapsedSeconds + 1

                    val updatedTelemetry =
                        if (current.linkLost) {
                            current.telemetry
                        } else {
                            SatelliteSimulator.snapshot(
                                nextSecond
                            )
                        }

                    current.copy(
                        elapsedSeconds = nextSecond,
                        telemetry = updatedTelemetry
                    )
                }
            }
        }
    }

    /**
     * Changes the simulated communication link state.
     *
     * Restoring the link immediately retrieves a new
     * telemetry snapshot for the current simulation time.
     */
    fun toggleConnection() {

        _uiState.update { current ->

            if (current.linkLost) {

                current.copy(
                    linkLost = false,
                    telemetry = SatelliteSimulator.snapshot(
                        current.elapsedSeconds
                    )
                )

            } else {

                current.copy(
                    linkLost = true
                )
            }
        }
    }

    /**
     * Restores the simulation to its initial conditions.
     */
    fun resetSimulation() {

        _uiState.value = OrbitalUiState(
            elapsedSeconds = 0,
            telemetry = SatelliteSimulator.snapshot(0),
            linkLost = false
        )
    }
}

