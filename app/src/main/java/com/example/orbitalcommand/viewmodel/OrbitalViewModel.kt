
package com.example.orbitalcommand.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitalcommand.model.EquipmentDamage
import com.example.orbitalcommand.model.MissionEvent
import com.example.orbitalcommand.model.MissionEventKind
import com.example.orbitalcommand.model.MissionEventLog
import com.example.orbitalcommand.model.MissionEventSeverity
import com.example.orbitalcommand.model.OrbitalServicing
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.PowerMode
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairWorkOrders
import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.SatelliteFault
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.SatelliteTelemetry
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
 * Manages the satellite fleet, equipment faults,
 * electrical power, mission events, and maintenance
 * operations.
 *
 * The servicing station has an independent simulated
 * orbital position. Preparing a maintenance mission
 * does not dispatch a vehicle or complete any repair.
 */
class OrbitalViewModel : ViewModel() {

    companion object {
        private const val MANUAL_FAULT_SOURCE = "MANUAL_FAULT"
        private const val POWER_STATUS_SOURCE = "POWER_STATUS"
        private const val EQUIPMENT_DAMAGE_SOURCE = "EQUIPMENT_DAMAGE"
    }

    private val _uiState = MutableStateFlow(
        createInitialState()
    )

    /**
     * Read-only fleet and mission state observed
     * by the application interface.
     */
    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Creates the initial fleet, servicing station,
     * and mission event history.
     */
    private fun createInitialState(): OrbitalUiState {

        val satellites = SatelliteCatalog.satellites.map {
                definition ->

            val telemetry =
                SatelliteSimulator.initialTelemetry(
                    definition
                )

            SatelliteState(
                definition = definition,
                telemetry = telemetry,
                onboardTelemetry = telemetry,
                history = listOf(telemetry)
            )
        }

        val initialEvent = MissionEvent(
            id = 1L,
            satelliteId = "FLEET",
            occurredAtSecond = 0,
            title = "Simulation initialized",
            description = "Satellite fleet monitoring started.",
            severity = MissionEventSeverity.INFO,
            kind = MissionEventKind.ACTIVITY
        )

        return OrbitalUiState(
            elapsedSeconds = 0,
            satellites = satellites,
            selectedSatelliteId =
                SatelliteCatalog.satellites.first().id,
            events = listOf(initialEvent),
            repairOrders = emptyList(),
            servicingStation = OrbitalServicing.stationAt(0),
            preparedMission = null
        )
    }

    /**
     * Advances satellite and servicing station motion
     * once per simulation second.
     *
     * The station moves independently of the satellites.
     * Onboard telemetry continues advancing during a
     * communication outage, while received telemetry
     * remains frozen until the connection is restored.
     */
    private fun startSimulation() {

        viewModelScope.launch {

            while (isActive) {

                delay(1000L)

                _uiState.update { current ->

                    val nextSecond =
                        current.elapsedSeconds + 1

                    var events = current.events

                    val satellites = current.satellites.map {
                            satellite ->

                        val previousMode =
                            satellite.onboardTelemetry.powerMode

                        val onboard = SatelliteSimulator.advance(
                            previous = satellite.onboardTelemetry,
                            second = nextSecond,
                            satellite = satellite.definition,
                            fault = satellite.fault,
                            faultStartedSecond =
                                satellite.faultStartedSecond,
                            equipmentDamage =
                                satellite.equipmentDamage,
                            equipmentDamageStartedSecond =
                                satellite.equipmentDamageStartedSecond
                        )

                        events = updatePowerEvents(
                            history = events,
                            satelliteId = satellite.definition.id,
                            previousMode = previousMode,
                            currentMode = onboard.powerMode,
                            atSecond = nextSecond
                        )

                        val updated = satellite.copy(
                            onboardTelemetry = onboard
                        )

                        if (updated.isConnected) {

                            updated.copy(
                                telemetry = onboard,
                                history = TelemetryHistory.append(
                                    history = satellite.history,
                                    sample = onboard
                                )
                            )

                        } else {
                            updated
                        }
                    }

                    current.copy(
                        elapsedSeconds = nextSecond,
                        satellites = satellites,
                        events = events,
                        servicingStation =
                            OrbitalServicing.stationAt(nextSecond)
                    )
                }
            }
        }
    }

    /**
     * Selects a spacecraft for detailed inspection
     * without pausing other mission entities.
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
     * Applies or clears a temporary diagnostic fault.
     *
     * The satellite's persistent equipment damage
     * and prepared servicing mission remain unchanged.
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

            val satellites = current.satellites.map {
                    satellite ->

                if (
                    satellite.definition.id !=
                    selected.definition.id
                ) {

                    satellite

                } else {

                    val onboard = SatelliteSimulator.refresh(
                        previous = satellite.onboardTelemetry,
                        satellite = satellite.definition,
                        fault = fault,
                        faultStartedSecond = faultStart,
                        equipmentDamage =
                            satellite.equipmentDamage,
                        equipmentDamageStartedSecond =
                            satellite.equipmentDamageStartedSecond
                    )

                    val updated = satellite.copy(
                        fault = fault,
                        faultStartedSecond = faultStart,
                        onboardTelemetry = onboard
                    )

                    withReceivedTelemetry(updated, onboard)
                }
            }

            var events = current.events

            if (selected.fault != SatelliteFault.NONE) {

                events = MissionEventLog.resolveActiveForSource(
                    history = events,
                    satelliteId = selected.definition.id,
                    sourceKey = MANUAL_FAULT_SOURCE,
                    atSecond = now
                )

                events = appendEvent(
                    history = events,
                    satelliteId = selected.definition.id,
                    atSecond = now,
                    title = "Temporary fault cleared",
                    description =
                        "${selected.fault.displayName} is no longer active.",
                    severity = MissionEventSeverity.INFO,
                    kind = MissionEventKind.ACTIVITY
                )
            }

            if (fault != SatelliteFault.NONE) {

                events = appendEvent(
                    history = events,
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
                satellites = satellites,
                events = events
            )
        }
    }

    /**
     * Clears the selected satellite's temporary fault.
     * Equipment damage remains until servicing completes.
     */
    fun clearFault() {
        setFault(SatelliteFault.NONE)
    }

    /**
     * Introduces persistent equipment damage and
     * creates its corresponding repair work order.
     *
     * Only one active equipment repair is supported
     * per satellite in the current simulation.
     */
    fun injectEquipmentDamage(
        damage: EquipmentDamage
    ) {

        if (damage == EquipmentDamage.NONE) {
            return
        }

        _uiState.update { current ->

            val selected = current.selectedSatellite

            if (selected.requiresServicing) {
                return@update current
            }

            val now = current.elapsedSeconds

            val satellites = current.satellites.map {
                    satellite ->

                if (
                    satellite.definition.id !=
                    selected.definition.id
                ) {

                    satellite

                } else {

                    val onboard = SatelliteSimulator.refresh(
                        previous = satellite.onboardTelemetry,
                        satellite = satellite.definition,
                        fault = satellite.fault,
                        faultStartedSecond =
                            satellite.faultStartedSecond,
                        equipmentDamage = damage,
                        equipmentDamageStartedSecond = now
                    )

                    val updated = satellite.copy(
                        equipmentDamage = damage,
                        equipmentDamageStartedSecond = now,
                        onboardTelemetry = onboard
                    )

                    withReceivedTelemetry(updated, onboard)
                }
            }

            val nextOrderId = (
                    current.repairOrders.maxOfOrNull {
                        it.id
                    } ?: 0L
                    ) + 1L

            val orders = RepairWorkOrders.open(
                orders = current.repairOrders,
                id = nextOrderId,
                satelliteId = selected.definition.id,
                damage = damage,
                atSecond = now
            )

            val events = appendEvent(
                history = current.events,
                satelliteId = selected.definition.id,
                atSecond = now,
                title = damage.displayName,
                description =
                    "${damage.description} " +
                            "Required servicing: ${damage.repairAction}.",
                severity = severityForDamage(damage),
                kind = MissionEventKind.ALERT,
                sourceKey = EQUIPMENT_DAMAGE_SOURCE
            )

            current.copy(
                satellites = satellites,
                repairOrders = orders,
                events = events
            )
        }
    }

    /**
     * Places an open repair request in the
     * servicing work-order queue.
     */
    fun queueRepair(orderId: Long) {

        _uiState.update { current ->

            val order = current.repairOrders.firstOrNull {
                it.id == orderId &&
                        it.status == RepairStatus.OPEN
            } ?: return@update current

            val now = current.elapsedSeconds

            val orders = RepairWorkOrders.queue(
                orders = current.repairOrders,
                orderId = orderId,
                atSecond = now
            )

            val events = appendEvent(
                history = current.events,
                satelliteId = order.satelliteId,
                atSecond = now,
                title = "Repair queued",
                description =
                    "Work order #${order.id}: " +
                            "${order.damage.repairAction}.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                repairOrders = orders,
                events = events
            )
        }
    }

    /**
     * Prepares a mission for a valid queued repair order.
     *
     * Only one servicing mission can be prepared at a time.
     * The selected work order remains queued, and no repair
     * is performed by this operation.
     */
    fun prepareServicingMission(orderId: Long) {

        _uiState.update { current ->

            if (current.preparedMission != null) {
                return@update current
            }

            val order = current.repairOrders.firstOrNull {
                it.id == orderId &&
                        it.status == RepairStatus.QUEUED
            } ?: return@update current

            val target = current.satellites.firstOrNull {
                it.definition.id == order.satelliteId
            } ?: return@update current

            if (target.equipmentDamage != order.damage) {
                return@update current
            }

            val now = current.elapsedSeconds

            val plan = OrbitalServicing.prepare(
                orders = current.repairOrders,
                satellites = current.satellites,
                workOrderId = orderId,
                atSecond = now
            )

            val verificationMessage =
                if (plan.telemetryVerificationRequired) {
                    " Target telemetry requires independent " +
                            "verification before future dispatch."
                } else {
                    " Target telemetry was current at preparation."
                }

            val events = appendEvent(
                history = current.events,
                satelliteId = plan.targetSatelliteId,
                atSecond = now,
                title = "Servicing mission prepared",
                description =
                    "${current.servicingStation.id} prepared " +
                            "work order #${plan.workOrderId}: " +
                            "${plan.damage.repairAction}. " +
                            "No servicing vehicle has been dispatched." +
                            verificationMessage,
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                preparedMission = plan,
                events = events
            )
        }
    }

    /**
     * Cancels the prepared mission without altering
     * the queued repair work order or equipment damage.
     */
    fun cancelServicingMission() {

        _uiState.update { current ->

            val plan = current.preparedMission
                ?: return@update current

            val events = appendEvent(
                history = current.events,
                satelliteId = plan.targetSatelliteId,
                atSecond = current.elapsedSeconds,
                title = "Mission preparation cancelled",
                description =
                    "Preparation for work order " +
                            "#${plan.workOrderId} was cancelled. " +
                            "The repair remains queued.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                preparedMission = null,
                events = events
            )
        }
    }

    /**
     * Completes a queued repair through the existing
     * test-only maintenance control.
     *
     * If the corresponding work order has a prepared
     * mission, that plan is closed automatically to
     * prevent it from referencing a completed repair.
     *
     * This operation does not simulate physical servicing.
     */
    fun completeRepair(orderId: Long) {

        _uiState.update { current ->

            val order = current.repairOrders.firstOrNull {
                it.id == orderId &&
                        it.status == RepairStatus.QUEUED
            } ?: return@update current

            val satellite = current.satellites.firstOrNull {
                it.definition.id == order.satelliteId
            } ?: return@update current

            if (satellite.equipmentDamage != order.damage) {
                return@update current
            }

            val now = current.elapsedSeconds

            val satellites = current.satellites.map {
                    spacecraft ->

                if (
                    spacecraft.definition.id !=
                    order.satelliteId
                ) {

                    spacecraft

                } else {

                    val onboard = SatelliteSimulator.refresh(
                        previous = spacecraft.onboardTelemetry,
                        satellite = spacecraft.definition,
                        fault = spacecraft.fault,
                        faultStartedSecond =
                            spacecraft.faultStartedSecond,
                        equipmentDamage = EquipmentDamage.NONE,
                        equipmentDamageStartedSecond = null
                    )

                    val repaired = spacecraft.copy(
                        equipmentDamage = EquipmentDamage.NONE,
                        equipmentDamageStartedSecond = null,
                        onboardTelemetry = onboard
                    )

                    withReceivedTelemetry(repaired, onboard)
                }
            }

            val orders = RepairWorkOrders.complete(
                orders = current.repairOrders,
                orderId = orderId,
                atSecond = now
            )

            var events = MissionEventLog.resolveActiveForSource(
                history = current.events,
                satelliteId = order.satelliteId,
                sourceKey = EQUIPMENT_DAMAGE_SOURCE,
                atSecond = now
            )

            val hasPreparedMission =
                current.preparedMission?.workOrderId == orderId

            if (hasPreparedMission) {

                events = appendEvent(
                    history = events,
                    satelliteId = order.satelliteId,
                    atSecond = now,
                    title = "Prepared mission closed",
                    description =
                        "Work order #$orderId was completed " +
                                "through the test-only maintenance control.",
                    severity = MissionEventSeverity.INFO,
                    kind = MissionEventKind.ACTIVITY
                )
            }

            events = appendEvent(
                history = events,
                satelliteId = order.satelliteId,
                atSecond = now,
                title = "Simulated repair completed",
                description =
                    "Work order #${order.id} completed: " +
                            "${order.damage.repairAction}. " +
                            "Equipment restored in the simulator.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                satellites = satellites,
                repairOrders = orders,
                events = events,
                preparedMission = if (hasPreparedMission) {
                    null
                } else {
                    current.preparedMission
                }
            )
        }
    }

    /**
     * Updates received telemetry only when the
     * satellite can successfully transmit.
     *
     * During an outage, last received measurements
     * and historical samples remain unchanged.
     */
    private fun withReceivedTelemetry(
        satellite: SatelliteState,
        onboard: SatelliteTelemetry
    ): SatelliteState {

        if (!satellite.isConnected) {
            return satellite
        }

        val history = TelemetryHistory.append(
            history = satellite.history,
            sample = onboard
        )

        return satellite.copy(
            telemetry = onboard,
            history = history
        )
    }

    /**
     * Acknowledges an active mission alert.
     *
     * Acknowledgement does not resolve its associated
     * fault, damaged equipment, or power condition.
     */
    fun acknowledgeAlert(eventId: Long) {

        _uiState.update { current ->

            val alert = current.events.firstOrNull {
                it.id == eventId &&
                        it.isActive &&
                        !it.isAcknowledged
            } ?: return@update current

            val now = current.elapsedSeconds

            val acknowledged = MissionEventLog.acknowledge(
                history = current.events,
                eventId = eventId,
                atSecond = now
            )

            val events = appendEvent(
                history = acknowledged,
                satelliteId = alert.satelliteId,
                atSecond = now,
                title = "Alert acknowledged",
                description =
                    "Operator acknowledged ${alert.title}. " +
                            "The underlying condition remains active.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(events = events)
        }
    }

    /**
     * Records changes in simulated power state.
     *
     * Power alerts have their own source key so
     * equipment repairs cannot resolve them by mistake.
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

        val resolved = MissionEventLog.resolveActiveForSource(
            history = history,
            satelliteId = satelliteId,
            sourceKey = POWER_STATUS_SOURCE,
            atSecond = atSecond
        )

        if (currentMode == PowerMode.NORMAL) {

            return appendEvent(
                history = resolved,
                satelliteId = satelliteId,
                atSecond = atSecond,
                title = "Normal power restored",
                description =
                    "The simulated power system returned " +
                            "to normal operation.",
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
                    "The spacecraft is reducing " +
                            "electrical consumption."
                severity = MissionEventSeverity.WARNING
            }

            PowerMode.SAFE_MODE -> {
                title = "Power safe mode"
                description =
                    "Nonessential systems are disabled " +
                            "in the simulator."
                severity = MissionEventSeverity.CRITICAL
            }

            PowerMode.OFFLINE -> {
                title = "Simulated power depletion"
                description =
                    "The internal simulator reports power " +
                            "depletion. Ground telemetry is unavailable."
                severity = MissionEventSeverity.CRITICAL
            }

            PowerMode.NORMAL -> {
                return resolved
            }
        }

        return appendEvent(
            history = resolved,
            satelliteId = satelliteId,
            atSecond = atSecond,
            title = title,
            description = description,
            severity = severity,
            kind = MissionEventKind.ALERT,
            sourceKey = POWER_STATUS_SOURCE
        )
    }

    /**
     * Returns operator-facing severity for
     * temporary diagnostic faults.
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
     * Returns operator-facing severity for
     * persistent equipment damage.
     */
    private fun severityForDamage(
        damage: EquipmentDamage
    ): MissionEventSeverity {

        return when (damage) {

            EquipmentDamage.PAYLOAD_SENSOR_DAMAGE ->
                MissionEventSeverity.CRITICAL

            EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            EquipmentDamage.THERMAL_RADIATOR_DAMAGE ->
                MissionEventSeverity.WARNING

            EquipmentDamage.NONE ->
                MissionEventSeverity.INFO
        }
    }

    /**
     * Records a new mission event with an identifier
     * unique within the retained simulation history.
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

        return MissionEventLog.record(
            history = history,
            event = MissionEvent(
                id = nextId,
                satelliteId = satelliteId,
                occurredAtSecond = atSecond,
                title = title,
                description = description,
                severity = severity,
                kind = kind,
                sourceKey = sourceKey
            )
        )
    }

    /**
     * Restores satellites, station position, telemetry,
     * faults, alerts, repair orders, and mission planning
     * to their initial simulation state.
     */
    fun resetSimulation() {
        _uiState.value = createInitialState()
    }
}

