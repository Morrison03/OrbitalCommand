
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
import com.example.orbitalcommand.model.RepairCompletionPolicy
import com.example.orbitalcommand.model.RepairCompletionStatus
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
 * Coordinates fleet telemetry, operational events,
 * equipment maintenance, and servicing vehicle navigation.
 *
 * A repair may be simulated from the home station
 * or during verified formation following.
 *
 * Completing a repair in formation preserves the
 * vehicle's active mission until it returns to OS-001.
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

    /**
     * Observable mission state for the Compose dashboard.
     */
    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Initializes all satellites, the servicing station,
     * docked vehicle, and mission event history.
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
     * Advances the satellite fleet, station, and servicing
     * vehicle once per simulation second.
     *
     * Formation following continues after repair completion.
     * The mission is closed only when the vehicle docks.
     */
    private fun startSimulation() {

        viewModelScope.launch {

            while (isActive) {

                delay(1000L)

                _uiState.update { current ->

                    val nextSecond = current.elapsedSeconds + 1
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

                    val station =
                        OrbitalServicing.stationAt(nextSecond)

                    val previousVehicle =
                        current.servicingVehicle

                    val target = satellites.firstOrNull {
                        it.definition.id ==
                                previousVehicle.targetSatelliteId
                    }

                    val vehicle =
                        ServicingVehicleNavigation.advance(
                            vehicle = previousVehicle,
                            station = station,
                            target = target,
                            atSecond = nextSecond
                        )

                    val formationEstablished =
                        previousVehicle.phase ==
                                ServicingVehiclePhase.APPROACHING &&
                                vehicle.phase ==
                                ServicingVehiclePhase.FORMATION_FOLLOWING

                    if (formationEstablished) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                vehicle.targetSatelliteId ?: "FLEET",
                            atSecond = nextSecond,
                            title = "Formation following established",
                            description =
                                "SERVICER-01 is following its " +
                                        "target satellite on the " +
                                        "schematic orbital track.",
                            severity = MissionEventSeverity.INFO,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    val trackingLost =
                        (
                                previousVehicle.phase ==
                                        ServicingVehiclePhase.APPROACHING ||
                                        previousVehicle.phase ==
                                        ServicingVehiclePhase.FORMATION_FOLLOWING
                                ) &&
                                vehicle.phase ==
                                ServicingVehiclePhase.HOLDING &&
                                vehicle.holdReason ==
                                VehicleHoldReason.TELEMETRY_UNAVAILABLE

                    if (trackingLost) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                vehicle.targetSatelliteId ?: "FLEET",
                            atSecond = nextSecond,
                            title = "Vehicle tracking suspended",
                            description =
                                "Target telemetry is unavailable " +
                                        "or stale. Automatic guidance " +
                                        "has been suspended.",
                            severity = MissionEventSeverity.WARNING,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    val returned =
                        previousVehicle.phase ==
                                ServicingVehiclePhase.RETURNING &&
                                vehicle.phase ==
                                ServicingVehiclePhase.DOCKED

                    if (returned) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                previousVehicle.targetSatelliteId
                                    ?: "FLEET",
                            atSecond = nextSecond,
                            title = "Servicing vehicle docked",
                            description =
                                "SERVICER-01 returned to OS-001. " +
                                        "The deployment is closed.",
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
     * Selects a satellite for detailed inspection.
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
     * Persistent equipment damage and repair work
     * orders are unaffected by this operation.
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
                        "${selected.fault.displayName} " +
                                "is no longer active.",
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
     */
    fun clearFault() {
        setFault(SatelliteFault.NONE)
    }

    /**
     * Injects persistent equipment damage and opens
     * a corresponding maintenance work order.
     */
    fun injectEquipmentDamage(damage: EquipmentDamage) {

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
                    "${damage.description} Required servicing: " +
                            "${damage.repairAction}.",
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
     * Moves an open repair work order into
     * the maintenance queue.
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
     * Prepares a servicing mission for an eligible
     * queued repair work order.
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
                orders = current.repairOrders,
                satellites = current.satellites,
                workOrderId = orderId,
                atSecond = now
            )

            val events = appendEvent(
                history = current.events,
                satelliteId = plan.targetSatelliteId,
                atSecond = now,
                title = "Servicing mission prepared",
                description =
                    "OS-001 prepared work order #$orderId. " +
                            "Target verification is required at " +
                            "dispatch. No repair was performed.",
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
     * Cancels an undepployed servicing mission plan
     * without changing its repair work order.
     */
    fun cancelServicingMission() {

        _uiState.update { current ->

            val plan = current.preparedMission
                ?: return@update current

            if (current.servicingVehicle.isAwayFromStation) {
                return@update current
            }

            val events = appendEvent(
                history = current.events,
                satelliteId = plan.targetSatelliteId,
                atSecond = current.elapsedSeconds,
                title = "Mission preparation cancelled",
                description =
                    "Work order #${plan.workOrderId} " +
                            "remains queued.",
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
     * Dispatches SERVICER-01 after revalidating
     * the mission, target, and current telemetry.
     */
    fun dispatchServicingVehicle() {

        _uiState.update { current ->

            val plan = current.preparedMission
                ?: return@update current

            if (
                current.servicingVehicle.isAwayFromStation ||
                !ServicingVehicleNavigation.isDispatchReady(
                    plan = plan,
                    orders = current.repairOrders,
                    satellites = current.satellites,
                    atSecond = current.elapsedSeconds
                )
            ) {
                return@update current
            }

            val vehicle = ServicingVehicleNavigation.dispatch(
                vehicle = current.servicingVehicle,
                plan = plan,
                station = current.servicingStation
            )

            val events = appendEvent(
                history = current.events,
                satelliteId = plan.targetSatelliteId,
                atSecond = current.elapsedSeconds,
                title = "Servicing vehicle deployed",
                description =
                    "SERVICER-01 departed OS-001 for work " +
                            "order #${plan.workOrderId}.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = vehicle,
                events = events
            )
        }
    }

    /**
     * Begins movement toward the assigned satellite's
     * formation-following position.
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
                    vehicle = vehicle,
                    target = target,
                    atSecond = current.elapsedSeconds
                )

            val events = appendEvent(
                history = current.events,
                satelliteId = target.definition.id,
                atSecond = current.elapsedSeconds,
                title = "Vehicle approach initiated",
                description =
                    "SERVICER-01 is approaching the " +
                            "moving formation position.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = updated,
                events = events
            )
        }
    }

    /**
     * Pauses an active guided approach.
     */
    fun holdServicingVehicle() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            if (
                vehicle.phase !=
                ServicingVehiclePhase.APPROACHING
            ) {
                return@update current
            }

            val updated =
                ServicingVehicleNavigation.hold(vehicle)

            val events = appendEvent(
                history = current.events,
                satelliteId =
                    vehicle.targetSatelliteId ?: "FLEET",
                atSecond = current.elapsedSeconds,
                title = "Vehicle hold requested",
                description =
                    "The operator paused scripted navigation.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = updated,
                events = events
            )
        }
    }

    /**
     * Resumes approach when target telemetry
     * is available again.
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
                    vehicle = vehicle,
                    target = target,
                    atSecond = current.elapsedSeconds
                )

            val events = appendEvent(
                history = current.events,
                satelliteId = target.definition.id,
                atSecond = current.elapsedSeconds,
                title = "Vehicle navigation resumed",
                description =
                    "SERVICER-01 resumed formation approach.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = updated,
                events = events
            )
        }
    }

    /**
     * Commands an active servicing vehicle
     * to return to OS-001.
     */
    fun returnServicingVehicle() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            val canReturn = when (vehicle.phase) {

                ServicingVehiclePhase.DEPLOYED,
                ServicingVehiclePhase.APPROACHING,
                ServicingVehiclePhase.FORMATION_FOLLOWING,
                ServicingVehiclePhase.HOLDING -> true

                ServicingVehiclePhase.DOCKED,
                ServicingVehiclePhase.RETURNING -> false
            }

            if (!canReturn) {
                return@update current
            }

            val updated =
                ServicingVehicleNavigation.beginReturn(vehicle)

            val events = appendEvent(
                history = current.events,
                satelliteId =
                    vehicle.targetSatelliteId ?: "FLEET",
                atSecond = current.elapsedSeconds,
                title = "Servicing vehicle returning",
                description =
                    "SERVICER-01 began its return to OS-001.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = updated,
                events = events
            )
        }
    }

    /**
     * Completes a queued simulated maintenance operation.
     *
     * Completion is allowed while the vehicle is docked,
     * or while it is actively following its assigned target
     * with current telemetry.
     *
     * During formation completion, the vehicle keeps
     * orbiting with the target. Its prepared mission remains
     * active until the operator commands return and docks.
     */
    fun completeRepair(orderId: Long) {

        _uiState.update { current ->

            val order = current.repairOrders.firstOrNull {
                it.id == orderId
            } ?: return@update current

            // Use exactly the same rule as the repair UI.
            val readiness = RepairCompletionPolicy.evaluate(
                state = current,
                order = order
            )

            if (!readiness.isAllowed) {
                return@update current
            }

            val now = current.elapsedSeconds

            val completedInFormation =
                readiness ==
                        RepairCompletionStatus.READY_IN_FORMATION

            // Restore equipment on the target without
            // resetting its battery or temporary faults.
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

            // A docked test-only completion closes the
            // prepared plan. Formation completion does not.
            val closePreparedMission =
                !completedInFormation &&
                        current.preparedMission?.workOrderId ==
                        orderId

            if (closePreparedMission) {

                events = appendEvent(
                    history = events,
                    satelliteId = order.satelliteId,
                    atSecond = now,
                    title = "Prepared mission closed",
                    description =
                        "Work order #$orderId was completed " +
                                "using the docked test-only control.",
                    severity = MissionEventSeverity.INFO,
                    kind = MissionEventKind.ACTIVITY
                )
            }

            events = appendEvent(
                history = events,
                satelliteId = order.satelliteId,
                atSecond = now,
                title = "Simulated repair completed",
                description = if (completedInFormation) {
                    "SERVICER-01 completed simulated " +
                            "maintenance for work order #$orderId: " +
                            "${order.damage.repairAction}. " +
                            "The vehicle remains in formation " +
                            "until commanded to return."
                } else {
                    "Work order #$orderId completed using " +
                            "the test-only maintenance control: " +
                            "${order.damage.repairAction}."
                },
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                satellites = satellites,
                repairOrders = orders,
                events = events,
                preparedMission = if (closePreparedMission)
                    null
                else
                    current.preparedMission

                // servicingVehicle is intentionally
                // preserved by this copy operation.
            )
        }
    }

    /**
     * Adds a telemetry packet to received history
     * only when the downlink is available.
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
                history = satellite.history,
                sample = onboard
            )
        )
    }

    /**
     * Acknowledges an active alert without
     * clearing its associated condition.
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
     * Tracks changes in simulated spacecraft
     * power operating mode and publishes alerts.
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
                    "The internal simulator reports " +
                            "power depletion. Ground telemetry " +
                            "is unavailable."
                severity = MissionEventSeverity.CRITICAL
            }

            PowerMode.NORMAL -> return resolved
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
     * Determines the severity of a temporary fault.
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
     * Determines the severity of persistent
     * equipment damage.
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
     * Records an event in the bounded mission history.
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
     * Resets fleet data, equipment damage, alerts,
     * work orders, and servicing vehicle state.
     */
    fun resetSimulation() {
        _uiState.value = createInitialState()
    }
}
