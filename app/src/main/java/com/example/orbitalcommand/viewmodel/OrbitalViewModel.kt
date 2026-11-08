
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
import com.example.orbitalcommand.model.RepairArmDirection
import com.example.orbitalcommand.model.RepairCompletionPolicy
import com.example.orbitalcommand.model.RepairCompletionStatus
import com.example.orbitalcommand.model.RepairStatus
import com.example.orbitalcommand.model.RepairTool
import com.example.orbitalcommand.model.RepairWorkOrder
import com.example.orbitalcommand.model.RepairWorkOrders
import com.example.orbitalcommand.model.RoboticRepairSimulator
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
 * Coordinates satellite monitoring, mission alerts,
 * robotic servicing operations, and maintenance records.
 *
 * Interactive repair actions require current telemetry
 * and verified formation with the assigned spacecraft.
 *
 * A completed repair does not automatically return the
 * vehicle to its orbital servicing station.
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

    /** Observable state consumed by the Compose interface. */
    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    init {
        startSimulation()
    }

    /**
     * Builds the initial fleet, station, vehicle,
     * and operational event history.
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

        return OrbitalUiState(
            elapsedSeconds = 0,
            satellites = satellites,
            selectedSatelliteId =
                SatelliteCatalog.satellites.first().id,
            servicingStation = station,
            events = listOf(
                MissionEvent(
                    id = 1L,
                    satelliteId = "FLEET",
                    occurredAtSecond = 0,
                    title = "Simulation initialized",
                    description =
                        "Satellite fleet monitoring started.",
                    severity = MissionEventSeverity.INFO,
                    kind = MissionEventKind.ACTIVITY
                )
            )
        )
    }

    /**
     * Advances spacecraft, station, and vehicle motion
     * once per simulation second.
     *
     * Received telemetry freezes during outages.
     * An in-progress repair session retains its progress
     * if formation guidance is temporarily interrupted.
     */
    private fun startSimulation() {

        viewModelScope.launch {

            while (isActive) {

                delay(1000L)

                _uiState.update { current ->

                    val second = current.elapsedSeconds + 1
                    var events = current.events

                    val satellites = current.satellites.map {
                            satellite ->

                        val previousMode =
                            satellite.onboardTelemetry.powerMode

                        val onboard = SatelliteSimulator.advance(
                            previous = satellite.onboardTelemetry,
                            second = second,
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
                            atSecond = second
                        )

                        val updated = satellite.copy(
                            onboardTelemetry = onboard
                        )

                        withReceivedTelemetry(updated, onboard)
                    }

                    val station = OrbitalServicing.stationAt(second)
                    val previousVehicle = current.servicingVehicle

                    val target = satellites.firstOrNull {
                        it.definition.id ==
                                previousVehicle.targetSatelliteId
                    }

                    val vehicle = ServicingVehicleNavigation.advance(
                        vehicle = previousVehicle,
                        station = station,
                        target = target,
                        atSecond = second
                    )

                    val established =
                        previousVehicle.phase ==
                                ServicingVehiclePhase.APPROACHING &&
                                vehicle.phase ==
                                ServicingVehiclePhase.FORMATION_FOLLOWING

                    if (established) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                vehicle.targetSatelliteId ?: "FLEET",
                            atSecond = second,
                            title = "Formation following established",
                            description =
                                "SERVICER-01 is following the target " +
                                        "on its simulated orbital track.",
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
                            atSecond = second,
                            title = "Vehicle tracking suspended",
                            description =
                                "Target telemetry is unavailable. " +
                                        "Guidance and robotic servicing " +
                                        "commands are suspended.",
                            severity = MissionEventSeverity.WARNING,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    val docked =
                        previousVehicle.phase ==
                                ServicingVehiclePhase.RETURNING &&
                                vehicle.phase ==
                                ServicingVehiclePhase.DOCKED

                    if (docked) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                previousVehicle.targetSatelliteId
                                    ?: "FLEET",
                            atSecond = second,
                            title = "Servicing vehicle docked",
                            description =
                                "SERVICER-01 returned to OS-001. " +
                                        "The deployment is closed.",
                            severity = MissionEventSeverity.INFO,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    current.copy(
                        elapsedSeconds = second,
                        satellites = satellites,
                        servicingStation = station,
                        servicingVehicle = vehicle,
                        events = events,
                        preparedMission = if (docked)
                            null
                        else
                            current.preparedMission,
                        repairSession = if (docked)
                            null
                        else
                            current.repairSession
                    )
                }
            }
        }
    }

    /**
     * Selects a monitored spacecraft.
     */
    fun selectSatellite(satelliteId: String) {

        _uiState.update { current ->

            if (current.satellites.any {
                    it.definition.id == satelliteId
                }
            ) {
                current.copy(selectedSatelliteId = satelliteId)
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
            val faultStarted = if (
                fault == SatelliteFault.NONE
            ) null else now

            val satellites = current.satellites.map {
                    satellite ->

                if (satellite.definition.id != selected.definition.id) {

                    satellite

                } else {

                    val onboard = SatelliteSimulator.refresh(
                        previous = satellite.onboardTelemetry,
                        satellite = satellite.definition,
                        fault = fault,
                        faultStartedSecond = faultStarted,
                        equipmentDamage =
                            satellite.equipmentDamage,
                        equipmentDamageStartedSecond =
                            satellite.equipmentDamageStartedSecond
                    )

                    withReceivedTelemetry(
                        satellite.copy(
                            fault = fault,
                            faultStartedSecond = faultStarted,
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
     * Removes the selected spacecraft's temporary fault.
     */
    fun clearFault() {
        setFault(SatelliteFault.NONE)
    }

    /**
     * Injects persistent equipment damage and opens
     * a corresponding repair work order.
     */
    fun injectEquipmentDamage(damage: EquipmentDamage) {

        if (damage == EquipmentDamage.NONE) return

        _uiState.update { current ->

            val selected = current.selectedSatellite

            if (selected.requiresServicing) {
                return@update current
            }

            val now = current.elapsedSeconds

            val satellites = current.satellites.map {
                    satellite ->

                if (satellite.definition.id != selected.definition.id) {

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

            val nextId =
                (current.repairOrders.maxOfOrNull { it.id }
                    ?: 0L) + 1L

            val orders = RepairWorkOrders.open(
                orders = current.repairOrders,
                id = nextId,
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
     * Places an open maintenance request in the repair queue.
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
                    "Work order #$orderId: ${order.damage.repairAction}.",
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
     * Prepares a queued repair mission while the
     * servicing vehicle is docked at OS-001.
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
                satelliteId = order.satelliteId,
                atSecond = now,
                title = "Servicing mission prepared",
                description =
                    "OS-001 prepared work order #$orderId. " +
                            "No vehicle has been dispatched.",
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
     * Cancels an undispatched servicing mission.
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
                    "Work order #${plan.workOrderId} remains queued.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                preparedMission = null,
                repairSession = null,
                events = events
            )
        }
    }

    /**
     * Dispatches SERVICER-01 after validating
     * target telemetry and mission eligibility.
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
     * Starts scripted vehicle approach using
     * the target's received telemetry.
     */
    fun beginVehicleApproach() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            if (
                vehicle.phase != ServicingVehiclePhase.DEPLOYED
            ) return@update current

            val target = current.satellites.firstOrNull {
                it.definition.id == vehicle.targetSatelliteId
            } ?: return@update current

            if (!ServicingVehicleNavigation
                    .hasCurrentTargetTelemetry(
                        target,
                        current.elapsedSeconds
                    )
            ) return@update current

            val updated = ServicingVehicleNavigation.beginApproach(
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
                vehicle.phase != ServicingVehiclePhase.APPROACHING
            ) return@update current

            val updated = ServicingVehicleNavigation.hold(vehicle)

            val events = appendEvent(
                history = current.events,
                satelliteId =
                    vehicle.targetSatelliteId ?: "FLEET",
                atSecond = current.elapsedSeconds,
                title = "Vehicle hold requested",
                description = "Operator paused scripted guidance.",
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
     * Resumes guided approach when target telemetry
     * becomes available again.
     */
    fun resumeVehicleApproach() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            if (
                vehicle.phase != ServicingVehiclePhase.HOLDING
            ) return@update current

            val target = current.satellites.firstOrNull {
                it.definition.id == vehicle.targetSatelliteId
            } ?: return@update current

            if (!ServicingVehicleNavigation
                    .hasCurrentTargetTelemetry(
                        target,
                        current.elapsedSeconds
                    )
            ) return@update current

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
     * Commands SERVICER-01 to return to OS-001.
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

            if (!canReturn) return@update current

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
     * Checks that the active interactive repair matches
     * the current mission and has verified formation.
     */
    private fun canOperateRepair(
        state: OrbitalUiState
    ): Boolean {

        val session = state.repairSession
            ?: return false

        if (session.isComplete) return false

        val order = state.repairOrders.firstOrNull {
            it.id == session.workOrderId
        } ?: return false

        return session.satelliteId == order.satelliteId &&
                session.damage == order.damage &&
                state.preparedMission?.workOrderId == order.id &&
                RepairCompletionPolicy.evaluate(state, order) ==
                RepairCompletionStatus.READY_IN_FORMATION
    }

    /**
     * Starts a touchscreen repair procedure after the
     * vehicle establishes formation with its target.
     */
    fun startRoboticRepair() {

        _uiState.update { current ->

            if (current.repairSession != null) {
                return@update current
            }

            val plan = current.preparedMission
                ?: return@update current

            val order = current.repairOrders.firstOrNull {
                it.id == plan.workOrderId
            } ?: return@update current

            if (
                RepairCompletionPolicy.evaluate(current, order) !=
                RepairCompletionStatus.READY_IN_FORMATION
            ) return@update current

            val session = RoboticRepairSimulator.start(order)

            val events = appendEvent(
                history = current.events,
                satelliteId = order.satelliteId,
                atSecond = current.elapsedSeconds,
                title = "Robotic servicing started",
                description =
                    "SERVICER-01 started the interactive " +
                            "procedure for work order #${order.id}.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                repairSession = session,
                events = events
            )
        }
    }

    /**
     * Moves the robotic arm within its local service area.
     *
     * Arm commands are rejected if formation or target
     * telemetry is unavailable.
     */
    fun moveRepairArm(direction: RepairArmDirection) {

        _uiState.update { current ->

            if (!canOperateRepair(current)) {
                return@update current
            }

            val session = current.repairSession
                ?: return@update current

            current.copy(
                repairSession =
                    RoboticRepairSimulator.move(session, direction)
            )
        }
    }

    /**
     * Selects the tool used for the current repair task.
     */
    fun selectRepairTool(tool: RepairTool) {

        _uiState.update { current ->

            if (!canOperateRepair(current)) {
                return@update current
            }

            val session = current.repairSession
                ?: return@update current

            current.copy(
                repairSession =
                    RoboticRepairSimulator.selectTool(session, tool)
            )
        }
    }

    /**
     * Attempts the current repair task.
     *
     * Wrong tools or incorrect arm placement do not
     * advance the procedure or clear equipment damage.
     *
     * A successful final verification completes the
     * work order atomically within the same state update.
     */
    fun executeRepairStep() {

        _uiState.update { current ->

            if (!canOperateRepair(current)) {
                return@update current
            }

            val session = current.repairSession
                ?: return@update current

            val result = RoboticRepairSimulator.execute(session)

            if (!result.succeeded) {

                return@update current.copy(
                    repairSession = result.session
                )
            }

            var updated = current.copy(
                repairSession = result.session
            )

            val events = appendEvent(
                history = updated.events,
                satelliteId = session.satelliteId,
                atSecond = current.elapsedSeconds,
                title = "Robotic task completed",
                description =
                    "${session.step.displayName} completed " +
                            "for work order #${session.workOrderId}.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            updated = updated.copy(events = events)

            if (result.finished) {

                val order = updated.repairOrders.firstOrNull {
                    it.id == session.workOrderId &&
                            it.status == RepairStatus.QUEUED
                } ?: return@update updated

                finishRepair(
                    current = updated,
                    order = order,
                    completedInFormation = true
                )

            } else {
                updated
            }
        }
    }

    /**
     * Restarts an incomplete robotic procedure without
     * clearing equipment damage or closing the mission.
     *
     * The work order remains queued.
     */
    fun restartRoboticRepair() {

        _uiState.update { current ->

            val session = current.repairSession
                ?: return@update current

            if (session.isComplete) {
                return@update current
            }

            val order = current.repairOrders.firstOrNull {
                it.id == session.workOrderId &&
                        it.status == RepairStatus.QUEUED
            } ?: return@update current

            val events = appendEvent(
                history = current.events,
                satelliteId = session.satelliteId,
                atSecond = current.elapsedSeconds,
                title = "Robotic procedure restarted",
                description =
                    "Operator restarted work order #${order.id}.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                repairSession = RoboticRepairSimulator.start(order),
                events = events
            )
        }
    }

    /**
     * Retains the original docked, debug-only repair
     * completion shortcut for simulator testing.
     *
     * In-orbit repair must use the interactive console.
     */
    fun completeRepair(orderId: Long) {

        _uiState.update { current ->

            val order = current.repairOrders.firstOrNull {
                it.id == orderId
            } ?: return@update current

            val readiness =
                RepairCompletionPolicy.evaluate(current, order)

            if (
                readiness != RepairCompletionStatus.READY_AT_STATION
            ) {
                return@update current
            }

            finishRepair(
                current = current,
                order = order,
                completedInFormation = false
            )
        }
    }

    /**
     * Restores damaged equipment, completes its work order,
     * and resolves only the associated hardware alert.
     *
     * An interactive in-orbit repair preserves both the
     * robotic vehicle and its prepared mission until docking.
     */
    private fun finishRepair(
        current: OrbitalUiState,
        order: RepairWorkOrder,
        completedInFormation: Boolean
    ): OrbitalUiState {

        val now = current.elapsedSeconds

        val satellites = current.satellites.map {
                spacecraft ->

            if (spacecraft.definition.id != order.satelliteId) {

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
            orderId = order.id,
            atSecond = now
        )

        var events = MissionEventLog.resolveActiveForSource(
            history = current.events,
            satelliteId = order.satelliteId,
            sourceKey = EQUIPMENT_DAMAGE_SOURCE,
            atSecond = now
        )

        events = appendEvent(
            history = events,
            satelliteId = order.satelliteId,
            atSecond = now,
            title = "Simulated repair completed",
            description = if (completedInFormation) {
                "SERVICER-01 completed the interactive " +
                        "maintenance procedure for work order " +
                        "#${order.id}. Return to OS-001 when ready."
            } else {
                "Work order #${order.id} completed using " +
                        "the docked simulator debug action."
            },
            severity = MissionEventSeverity.INFO,
            kind = MissionEventKind.ACTIVITY
        )

        val closePlan =
            !completedInFormation &&
                    current.preparedMission?.workOrderId == order.id

        return current.copy(
            satellites = satellites,
            repairOrders = orders,
            events = events,
            preparedMission = if (closePlan)
                null
            else
                current.preparedMission
        )
    }

    /**
     * Stores newly received telemetry when communication
     * is available and preserves stale measurements otherwise.
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
     * Acknowledges an active mission alert without
     * resolving the underlying fault or damage.
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
     * Creates automatic power alerts when the simulated
     * spacecraft changes power operating modes.
     */
    private fun updatePowerEvents(
        history: List<MissionEvent>,
        satelliteId: String,
        previousMode: PowerMode,
        currentMode: PowerMode,
        atSecond: Int
    ): List<MissionEvent> {

        if (previousMode == currentMode) return history

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
                    "Ground telemetry is unavailable due to " +
                            "simulated power depletion."
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
     * Returns the operational severity of a temporary fault.
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
     * Returns the operational severity of hardware damage.
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
     * Adds an event to the bounded mission event history.
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

        val nextId =
            (history.maxOfOrNull { it.id } ?: 0L) + 1L

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
     * Resets fleet telemetry, faults, maintenance history,
     * servicing station, vehicle, and robotic procedure.
     */
    fun resetSimulation() {
        _uiState.value = createInitialState()
    }
}
