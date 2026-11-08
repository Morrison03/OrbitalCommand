
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
 * Coordinates satellite telemetry, mission alerts,
 * equipment maintenance, and servicing vehicle navigation.
 *
 * The vehicle can approach a satellite, establish
 * formation following, and return to its home station.
 *
 * All positions and movements use schematic orbital
 * coordinates rather than physical spacecraft guidance.
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
     * Read-only application state observed by
     * the Jetpack Compose dashboard.
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
     * Advances all satellites, station motion, and
     * servicing vehicle guidance once per second.
     *
     * The vehicle follows the target's orbital movement
     * after reaching its formation position.
     *
     * If current target telemetry is unavailable,
     * automatic tracking is suspended.
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

                    // Record establishment of synchronized
                    // formation following.
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
                                "SERVICER-01 is following the target " +
                                        "on its schematic orbital track. " +
                                        "Equipment servicing has not started.",
                            severity = MissionEventSeverity.INFO,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    // Suspend approach or formation tracking
                    // when received target telemetry is lost.
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
                                "Target telemetry is unavailable or " +
                                        "stale. SERVICER-01 has suspended " +
                                        "automatic navigation.",
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
     * Selects a satellite for detailed monitoring
     * without affecting other simulation entities.
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
     * Applies or removes a temporary diagnostic fault.
     *
     * Persistent equipment damage and repair work
     * orders are not modified.
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
     */
    fun clearFault() {
        setFault(SatelliteFault.NONE)
    }

    /**
     * Injects persistent equipment damage and
     * creates a matching repair work order.
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
     * Places an open maintenance work order
     * into the servicing queue.
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
     * Prepares a servicing mission without
     * dispatching the vehicle or performing repairs.
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
                            "Target verification is required at dispatch. " +
                            "No repair was performed.",
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
     * Cancels mission preparation while
     * the servicing vehicle remains docked.
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
                events = events
            )
        }
    }

    /**
     * Dispatches the servicing vehicle after
     * revalidating the work order and target telemetry.
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
     * Begins guided movement toward the moving
     * satellite's formation-following position.
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
                    "SERVICER-01 began approach toward a " +
                            "moving orbital formation position.",
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
     * Stops an active scripted approach at
     * the operator's request.
     *
     * Formation following is not stopped automatically
     * after reaching the satellite.
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
     * Resumes guidance after target telemetry
     * becomes available again.
     *
     * Reaching the formation position automatically
     * activates formation following.
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
                    "SERVICER-01 resumed approach toward " +
                            "the moving target formation.",
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
     * Commands an active vehicle to return to OS-001.
     *
     * Return is available during approach,
     * formation following, or a navigation hold.
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
                    "SERVICER-01 began scripted return to OS-001.",
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
     * Completes a queued repair using the existing
     * test-only maintenance action.
     *
     * Assigned repairs cannot be completed while
     * their servicing vehicle is deployed.
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
                orders = current.repairOrders,
                orderId = orderId,
                atSecond = now
            )

            var events =
                MissionEventLog.resolveActiveForSource(
                    history = current.events,
                    satelliteId = order.satelliteId,
                    sourceKey = EQUIPMENT_DAMAGE_SOURCE,
                    atSecond = now
                )

            val closesPlan =
                current.preparedMission?.workOrderId == orderId

            if (closesPlan) {

                events = appendEvent(
                    history = events,
                    satelliteId = order.satelliteId,
                    atSecond = now,
                    title = "Prepared mission closed",
                    description =
                        "Work order #$orderId was completed " +
                                "through the test-only maintenance action.",
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
                    "Work order #$orderId: " +
                            "${order.damage.repairAction}. " +
                            "Equipment restored in the simulator.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
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
     * Stores telemetry only when the spacecraft
     * is capable of transmitting to mission control.
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
     * Acknowledges a mission alert without
     * resolving the underlying problem.
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
     * Updates automatic power alerts when
     * the simulated spacecraft changes power mode.
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
                    "The simulated electrical power system " +
                            "returned to normal operation.",
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
                    "The internal model reports depleted power. " +
                            "Ground telemetry is unavailable."
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
     * Returns operator-facing severity
     * for a temporary diagnostic fault.
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
     * Returns operator-facing severity
     * for persistent equipment damage.
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
     * Adds a new event to the bounded
     * in-memory mission history.
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
     * Resets satellite monitoring, equipment damage,
     * repairs, station movement, and vehicle state.
     */
    fun resetSimulation() {
        _uiState.value = createInitialState()
    }
}
