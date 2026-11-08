
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
import com.example.orbitalcommand.model.ServicingVehiclePhase
import com.example.orbitalcommand.model.TelemetryHistory
import com.example.orbitalcommand.model.VehicleHoldReason
import com.example.orbitalcommand.simulation.SatelliteSimulator
import com.example.orbitalcommand.simulation.ServicingVehicleNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates satellite monitoring, maintenance work
 * orders, and servicing vehicle state transitions.
 *
 * Vehicle movement is schematic. Approaching a target
 * never implies a physical repair has occurred.
 */
class OrbitalViewModel : ViewModel() {

    companion object {
        private const val MANUAL_FAULT_SOURCE = "MANUAL_FAULT"
        private const val POWER_STATUS_SOURCE = "POWER_STATUS"
        private const val EQUIPMENT_DAMAGE_SOURCE =
            "EQUIPMENT_DAMAGE"
    }

    private val _uiState = MutableStateFlow(
        createInitialState()
    )

    /** Observable application state for the dashboard. */
    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Creates the initial satellite fleet, servicing
     * station, and mission history.
     */
    private fun createInitialState(): OrbitalUiState {

        val satellites = SatelliteCatalog.satellites.map {
                definition ->

            val telemetry =
                SatelliteSimulator.initialTelemetry(definition)

            SatelliteState(
                definition = definition,
                telemetry = telemetry,
                onboardTelemetry = telemetry,
                history = listOf(telemetry)
            )
        }

        val station = OrbitalServicing.stationAt(0)

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
            servicingStation = station,
            preparedMission = null
        )
    }

    /**
     * Advances satellites and vehicle movement
     * once per simulation second.
     *
     * Vehicle guidance consumes newly received
     * target telemetry and stops approaching
     * when that telemetry becomes unavailable.
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

                        val onboard =
                            SatelliteSimulator.advance(
                                previous =
                                    satellite.onboardTelemetry,
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
                                    satellite.history,
                                    onboard
                                )
                            )

                        } else {
                            updated
                        }
                    }

                    val station =
                        OrbitalServicing.stationAt(nextSecond)

                    val target = satellites.firstOrNull {
                        it.definition.id ==
                                current.servicingVehicle.targetSatelliteId
                    }

                    val vehicle =
                        ServicingVehicleNavigation.advance(
                            vehicle = current.servicingVehicle,
                            station = station,
                            target = target,
                            atSecond = nextSecond
                        )

                    if (
                        current.servicingVehicle.phase ==
                        ServicingVehiclePhase.APPROACHING &&
                        vehicle.phase ==
                        ServicingVehiclePhase.HOLDING
                    ) {

                        val lostTelemetry =
                            vehicle.holdReason ==
                                    VehicleHoldReason.TELEMETRY_UNAVAILABLE

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                vehicle.targetSatelliteId ?: "FLEET",
                            atSecond = nextSecond,
                            title = if (lostTelemetry)
                                "Vehicle approach paused"
                            else
                                "Vehicle reached target standoff",
                            description = if (lostTelemetry)
                                "Target telemetry is unavailable or " +
                                        "stale. Scripted approach stopped."
                            else
                                "The vehicle reached its simulated " +
                                        "standoff point. No repair was performed.",
                            severity = if (lostTelemetry)
                                MissionEventSeverity.WARNING
                            else
                                MissionEventSeverity.INFO,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    val returned =
                        current.servicingVehicle.phase ==
                                ServicingVehiclePhase.RETURNING &&
                                vehicle.phase ==
                                ServicingVehiclePhase.DOCKED

                    if (returned) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                current.servicingVehicle
                                    .targetSatelliteId ?: "FLEET",
                            atSecond = nextSecond,
                            title = "Servicing vehicle docked",
                            description =
                                "SERVICER-01 returned to OS-001. " +
                                        "The repair work order remains queued.",
                            severity = MissionEventSeverity.INFO,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    current.copy(
                        elapsedSeconds = nextSecond,
                        satellites = satellites,
                        events = events,
                        servicingStation = station,
                        servicingVehicle = vehicle,
                        preparedMission = if (returned)
                            null
                        else
                            current.preparedMission
                    )
                }
            }
        }
    }

    /**
     * Selects the satellite shown in the
     * detailed telemetry panels.
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
     * Persistent equipment damage is not affected.
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

                    withReceivedTelemetry(
                        satellite.copy(
                            fault = fault,
                            faultStartedSecond = faultStart,
                            onboardTelemetry = onboard
                        ),
                        onboard
                    )
                }
            }

            var events = current.events

            if (selected.fault != SatelliteFault.NONE) {

                events =
                    MissionEventLog.resolveActiveForSource(
                        events,
                        selected.definition.id,
                        MANUAL_FAULT_SOURCE,
                        now
                    )

                events = appendEvent(
                    events,
                    selected.definition.id,
                    now,
                    "Temporary fault cleared",
                    "${selected.fault.displayName} is no longer active.",
                    MissionEventSeverity.INFO,
                    MissionEventKind.ACTIVITY
                )
            }

            if (fault != SatelliteFault.NONE) {

                events = appendEvent(
                    events,
                    selected.definition.id,
                    now,
                    fault.displayName,
                    fault.description,
                    severityForFault(fault),
                    MissionEventKind.ALERT,
                    MANUAL_FAULT_SOURCE
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
     */
    fun clearFault() {
        setFault(SatelliteFault.NONE)
    }

    /**
     * Introduces persistent hardware damage and
     * opens a corresponding repair work order.
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

                    withReceivedTelemetry(
                        satellite.copy(
                            equipmentDamage = damage,
                            equipmentDamageStartedSecond = now,
                            onboardTelemetry = onboard
                        ),
                        onboard
                    )
                }
            }

            val nextOrderId = (
                    current.repairOrders.maxOfOrNull {
                        it.id
                    } ?: 0L
                    ) + 1L

            val orders = RepairWorkOrders.open(
                current.repairOrders,
                nextOrderId,
                selected.definition.id,
                damage,
                now
            )

            val events = appendEvent(
                current.events,
                selected.definition.id,
                now,
                damage.displayName,
                "${damage.description} " +
                        "Required servicing: ${damage.repairAction}.",
                severityForDamage(damage),
                MissionEventKind.ALERT,
                EQUIPMENT_DAMAGE_SOURCE
            )

            current.copy(
                satellites = satellites,
                repairOrders = orders,
                events = events
            )
        }
    }

    /**
     * Places an open maintenance work order
     * in the servicing queue.
     */
    fun queueRepair(orderId: Long) {

        _uiState.update { current ->

            val order = current.repairOrders.firstOrNull {
                it.id == orderId &&
                        it.status == RepairStatus.OPEN
            } ?: return@update current

            val now = current.elapsedSeconds

            val orders = RepairWorkOrders.queue(
                current.repairOrders,
                orderId,
                now
            )

            val events = appendEvent(
                current.events,
                order.satelliteId,
                now,
                "Repair queued",
                "Work order #${order.id}: " +
                        "${order.damage.repairAction}.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                repairOrders = orders,
                events = events
            )
        }
    }

    /**
     * Prepares a mission without deploying the vehicle
     * or changing the equipment's condition.
     */
    fun prepareServicingMission(orderId: Long) {

        _uiState.update { current ->

            if (
                current.preparedMission != null ||
                current.servicingVehicle.isAwayFromStation
            ) {
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
                current.repairOrders,
                current.satellites,
                orderId,
                now
            )

            val events = appendEvent(
                current.events,
                plan.targetSatelliteId,
                now,
                "Servicing mission prepared",
                "OS-001 prepared work order #$orderId. " +
                        "Target verification is required at dispatch. " +
                        "No repair was performed.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                preparedMission = plan,
                events = events
            )
        }
    }

    /**
     * Cancels mission preparation while the vehicle
     * is docked at OS-001.
     *
     * Active deployments must return before cancellation.
     */
    fun cancelServicingMission() {

        _uiState.update { current ->

            val plan = current.preparedMission
                ?: return@update current

            if (current.servicingVehicle.isAwayFromStation) {
                return@update current
            }

            val events = appendEvent(
                current.events,
                plan.targetSatelliteId,
                current.elapsedSeconds,
                "Mission preparation cancelled",
                "Work order #${plan.workOrderId} remains queued.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                preparedMission = null,
                events = events
            )
        }
    }

    /**
     * Deploys the vehicle after revalidating the
     * repair order, damage, and target telemetry.
     */
    fun dispatchServicingVehicle() {

        _uiState.update { current ->

            val plan = current.preparedMission
                ?: return@update current

            if (
                current.servicingVehicle.isAwayFromStation ||
                !ServicingVehicleNavigation.isDispatchReady(
                    plan,
                    current.repairOrders,
                    current.satellites,
                    current.elapsedSeconds
                )
            ) {
                return@update current
            }

            val vehicle = ServicingVehicleNavigation.dispatch(
                current.servicingVehicle,
                plan,
                current.servicingStation
            )

            val events = appendEvent(
                current.events,
                plan.targetSatelliteId,
                current.elapsedSeconds,
                "Servicing vehicle deployed",
                "SERVICER-01 left OS-001 for work order " +
                        "#${plan.workOrderId}. No repair has occurred.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = vehicle,
                events = events
            )
        }
    }

    /**
     * Begins guided movement toward a target
     * whose telemetry is currently available.
     */
    fun beginVehicleApproach() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            if (
                vehicle.phase != ServicingVehiclePhase.DEPLOYED
            ) {
                return@update current
            }

            val target = current.satellites.firstOrNull {
                it.definition.id == vehicle.targetSatelliteId
            } ?: return@update current

            if (
                !ServicingVehicleNavigation
                    .hasCurrentTargetTelemetry(
                        target,
                        current.elapsedSeconds
                    )
            ) {
                return@update current
            }

            val updated =
                ServicingVehicleNavigation.beginApproach(
                    vehicle,
                    target,
                    current.elapsedSeconds
                )

            val events = appendEvent(
                current.events,
                target.definition.id,
                current.elapsedSeconds,
                "Vehicle approach initiated",
                "SERVICER-01 began scripted target approach.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = updated,
                events = events
            )
        }
    }

    /**
     * Stops an active guided approach.
     */
    fun holdServicingVehicle() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            if (
                vehicle.phase != ServicingVehiclePhase.APPROACHING
            ) {
                return@update current
            }

            val updated =
                ServicingVehicleNavigation.hold(vehicle)

            val events = appendEvent(
                current.events,
                vehicle.targetSatelliteId ?: "FLEET",
                current.elapsedSeconds,
                "Vehicle hold requested",
                "Operator stopped the scripted approach " +
                        "at its current position.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = updated,
                events = events
            )
        }
    }

    /**
     * Resumes approach only after rechecking
     * the target's received telemetry.
     */
    fun resumeVehicleApproach() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            if (
                vehicle.phase != ServicingVehiclePhase.HOLDING
            ) {
                return@update current
            }

            val target = current.satellites.firstOrNull {
                it.definition.id == vehicle.targetSatelliteId
            } ?: return@update current

            if (
                !ServicingVehicleNavigation
                    .hasCurrentTargetTelemetry(
                        target,
                        current.elapsedSeconds
                    )
            ) {
                return@update current
            }

            val updated =
                ServicingVehicleNavigation.resumeApproach(
                    vehicle,
                    target,
                    current.elapsedSeconds
                )

            val events = appendEvent(
                current.events,
                target.definition.id,
                current.elapsedSeconds,
                "Vehicle approach resumed",
                "SERVICER-01 resumed scripted target approach.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = updated,
                events = events
            )
        }
    }

    /**
     * Commands the vehicle to return to OS-001.
     */
    fun returnServicingVehicle() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            if (
                vehicle.phase != ServicingVehiclePhase.DEPLOYED &&
                vehicle.phase !=
                ServicingVehiclePhase.APPROACHING &&
                vehicle.phase !=
                ServicingVehiclePhase.HOLDING
            ) {
                return@update current
            }

            val updated =
                ServicingVehicleNavigation.beginReturn(vehicle)

            val events = appendEvent(
                current.events,
                vehicle.targetSatelliteId ?: "FLEET",
                current.elapsedSeconds,
                "Servicing vehicle returning",
                "SERVICER-01 began scripted return to OS-001.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = updated,
                events = events
            )
        }
    }

    /**
     * Completes a queued repair through the existing
     * test-only maintenance control.
     *
     * The repair cannot be completed by this control
     * while its assigned vehicle is away from OS-001.
     */
    fun completeRepair(orderId: Long) {

        _uiState.update { current ->

            val order = current.repairOrders.firstOrNull {
                it.id == orderId &&
                        it.status == RepairStatus.QUEUED
            } ?: return@update current

            if (
                current.servicingVehicle.isAwayFromStation &&
                current.servicingVehicle.workOrderId == orderId
            ) {
                return@update current
            }

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

                    withReceivedTelemetry(
                        spacecraft.copy(
                            equipmentDamage = EquipmentDamage.NONE,
                            equipmentDamageStartedSecond = null,
                            onboardTelemetry = onboard
                        ),
                        onboard
                    )
                }
            }

            val orders = RepairWorkOrders.complete(
                current.repairOrders,
                orderId,
                now
            )

            var events =
                MissionEventLog.resolveActiveForSource(
                    current.events,
                    order.satelliteId,
                    EQUIPMENT_DAMAGE_SOURCE,
                    now
                )

            val closesPlan =
                current.preparedMission?.workOrderId == orderId

            if (closesPlan) {

                events = appendEvent(
                    events,
                    order.satelliteId,
                    now,
                    "Prepared mission closed",
                    "Work order #$orderId completed through " +
                            "the test-only maintenance control.",
                    MissionEventSeverity.INFO,
                    MissionEventKind.ACTIVITY
                )
            }

            events = appendEvent(
                events,
                order.satelliteId,
                now,
                "Simulated repair completed",
                "Work order #$orderId: " +
                        "${order.damage.repairAction}. " +
                        "Equipment restored in the simulator.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(
                satellites = satellites,
                repairOrders = orders,
                events = events,
                preparedMission = if (closesPlan)
                    null
                else
                    current.preparedMission
            )
        }
    }

    /**
     * Updates received telemetry only while
     * communication is available.
     */
    private fun withReceivedTelemetry(
        satellite: SatelliteState,
        onboard: SatelliteTelemetry
    ): SatelliteState {

        if (!satellite.isConnected) {
            return satellite
        }

        return satellite.copy(
            telemetry = onboard,
            history = TelemetryHistory.append(
                satellite.history,
                onboard
            )
        )
    }

    /**
     * Acknowledges an active alert without
     * resolving its underlying cause.
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
                current.events,
                eventId,
                now
            )

            val events = appendEvent(
                acknowledged,
                alert.satelliteId,
                now,
                "Alert acknowledged",
                "Operator acknowledged ${alert.title}. " +
                        "The underlying condition remains active.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )

            current.copy(events = events)
        }
    }

    /**
     * Generates and resolves automatic alerts
     * when spacecraft power modes change.
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

        val resolved =
            MissionEventLog.resolveActiveForSource(
                history,
                satelliteId,
                POWER_STATUS_SOURCE,
                atSecond
            )

        if (currentMode == PowerMode.NORMAL) {

            return appendEvent(
                resolved,
                satelliteId,
                atSecond,
                "Normal power restored",
                "The simulated power system returned " +
                        "to normal operation.",
                MissionEventSeverity.INFO,
                MissionEventKind.ACTIVITY
            )
        }

        val title: String
        val description: String
        val severity: MissionEventSeverity

        when (currentMode) {

            PowerMode.LOW_POWER -> {
                title = "Low battery"
                description =
                    "The spacecraft is reducing electrical consumption."
                severity = MissionEventSeverity.WARNING
            }

            PowerMode.SAFE_MODE -> {
                title = "Power safe mode"
                description =
                    "Nonessential systems are disabled in the simulator."
                severity = MissionEventSeverity.CRITICAL
            }

            PowerMode.OFFLINE -> {
                title = "Simulated power depletion"
                description =
                    "The internal model reports depleted power; " +
                            "received telemetry is unavailable."
                severity = MissionEventSeverity.CRITICAL
            }

            PowerMode.NORMAL -> return resolved
        }

        return appendEvent(
            resolved,
            satelliteId,
            atSecond,
            title,
            description,
            severity,
            MissionEventKind.ALERT,
            POWER_STATUS_SOURCE
        )
    }

    /**
     * Maps temporary faults to operator-facing severity.
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
     * Maps equipment damage to operator-facing severity.
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
     * Appends a new event with a unique identifier
     * within the current in-memory history.
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
     * Resets the satellite fleet, servicing station,
     * vehicle, faults, repairs, and event history.
     */
    fun resetSimulation() {

        _uiState.value = createInitialState()
    }
}
