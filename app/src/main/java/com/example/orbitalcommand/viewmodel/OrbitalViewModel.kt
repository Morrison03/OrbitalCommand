package com.example.orbitalcommand.viewmodel

import com.example.orbitalcommand.notifications.OrbitalNotificationCenter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.example.orbitalcommand.persistence.OrbitalSavedState
import com.example.orbitalcommand.persistence.OrbitalStateStore
import androidx.lifecycle.viewModelScope
import com.example.orbitalcommand.model.*
import com.example.orbitalcommand.simulation.SatelliteSimulator
import com.example.orbitalcommand.simulation.ServicingVehicleNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns fleet telemetry, mission alerts, work orders,
 * vehicle navigation, and interactive satellite servicing.
 *
 * The digital service view is a non-flight simulation.
 * Only successful final verification completes an
 * in-formation repair work order.
 */
class OrbitalViewModel(application: Application) : AndroidViewModel(application) {

    companion object {

        private const val MANUAL_FAULT_SOURCE =
            "MANUAL_FAULT"

        private const val POWER_STATUS_SOURCE =
            "POWER_STATUS"

        private const val EQUIPMENT_DAMAGE_SOURCE =
            "EQUIPMENT_DAMAGE"
    }

    /** App-private, atomic snapshot store for mission state. */
    private val persistentStateStore = OrbitalStateStore(application.applicationContext)

    /** Restore the previous checkpoint before any new simulation tick. */
    private val restoredSnapshot = persistentStateStore.load()

    private val _uiState = MutableStateFlow(
        restoredSnapshot?.uiState ?: createInitialState()
    )

    val uiState: StateFlow<OrbitalUiState> =
        _uiState.asStateFlow()

    /** Operator-controlled pause persists even after force-stop/restart. */
    private val _isSimulationPaused = MutableStateFlow(
        restoredSnapshot?.isSimulationPaused ?: persistentStateStore.isSimulationPaused()
    )

    val isSimulationPaused: StateFlow<Boolean> =
        _isSimulationPaused.asStateFlow()

    /** Monotonic time anchor including Android device sleep. */
    private var lastSimulationTickElapsedRealtimeMillis =
        SystemClock.elapsedRealtime()

    init {
        // Deliver satellite and mission events while this process is alive.
        val notificationCenter = OrbitalNotificationCenter(getApplication<Application>())
        notificationCenter.createChannels()
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.map { it.events }.distinctUntilChanged().collect { events ->
                notificationCenter.publishNewEvents(events)
            }
        }

        // Repair/model state and the separate physical rail attachment must
        // be restored together, before navigation reads either one.
        ServicerAttachmentRegistry.clear()
        restoredSnapshot?.attachment?.let { attachment ->
            ServicerAttachmentRegistry.prepareMission(
                workOrderId = attachment.workOrderId,
                satelliteId = attachment.satelliteId
            )
            ServicerAttachmentRegistry.update(attachment.docking)
        }

        startMissionStatePersistence()
        startSimulation()
    }

    /**
     * Save every changed simulator or rail-grip checkpoint on a background
     * thread. conflate() avoids a write backlog if telemetry updates faster
     * than the disk. NonCancellable finishes a write already in progress.
     */
    private fun startMissionStatePersistence() {
        viewModelScope.launch {
            combine(
                _uiState,
                ServicerAttachmentRegistry.current,
                _isSimulationPaused
            ) { state, attachment, paused ->
                OrbitalSavedState(
                    uiState = state,
                    attachment = attachment,
                    isSimulationPaused = paused
                )
            }.conflate().collect { snapshot ->
                withContext(Dispatchers.IO + NonCancellable) {
                    persistentStateStore.save(snapshot)
                }
            }
        }
    }

    /**
     * Initializes satellite telemetry, mission events,
     * and the docked servicing vehicle.
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

        return OrbitalUiState(
            elapsedSeconds = 0,
            satellites = satellites,
            selectedSatelliteId =
                SatelliteCatalog.satellites.first().id,
            servicingStation = OrbitalServicing.stationAt(0),
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
     * Advances satellites and vehicle movement
     * once per simulation second.
     *
     * Repair progress persists during telemetry loss.
     * Controls are locked while formation is unavailable.
     */
    private fun startSimulation() {

        viewModelScope.launch {

            while (isActive) {

                // Suspend the simulation until the operator resumes it.
                if (_isSimulationPaused.value) {
                    _isSimulationPaused.first { paused -> !paused }
                    lastSimulationTickElapsedRealtimeMillis =
                        SystemClock.elapsedRealtime()
                    continue
                }

                delay(1000L)

                // Re-check after delay so no tick runs while paused.
                if (_isSimulationPaused.value) {
                    lastSimulationTickElapsedRealtimeMillis =
                        SystemClock.elapsedRealtime()
                    continue
                }

                // Catch up after Android background suspension or deep sleep.
                val tickNow = SystemClock.elapsedRealtime()
                val secondsPassed = (
                    (tickNow - lastSimulationTickElapsedRealtimeMillis) / 1000L
                ).coerceAtLeast(0L)

                if (secondsPassed == 0L) continue

                // Retain sub-second fractions to prevent long-running drift.
                lastSimulationTickElapsedRealtimeMillis +=
                    secondsPassed * 1000L

                // Catch-up can replay many offline seconds; do not block the UI thread.
                withContext(Dispatchers.Default) {
                    _uiState.update { current ->
                    // Do not apply an in-flight replay after Pause is tapped.
                    if (_isSimulationPaused.value) return@update current

                    val second =
                        (current.elapsedSeconds.toLong() + secondsPassed)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()

                    var events = current.events

                    val satellites = current.satellites.map { satellite ->
                        // Live clock and simulation telemetry are related but
                        // may be out of sync after restoring an offline mission.
                        // The simulator requires an exact ONE-SECOND interval.
                        var onboard = satellite.onboardTelemetry
                        while (onboard.sampleSecond < second) {
                            // Discard partial catch-up if the operator pauses.
                            if (_isSimulationPaused.value) return@update current
                            val nextSecond = onboard.sampleSecond + 1
                            val previousMode = onboard.powerMode

                            onboard = SatelliteSimulator.advance(
                                previous = onboard,
                                second = nextSecond,
                                satellite = satellite.definition,
                                fault = satellite.fault,
                                faultStartedSecond = satellite.faultStartedSecond,
                                equipmentDamage = satellite.equipmentDamage,
                                equipmentDamageStartedSecond =
                                    satellite.equipmentDamageStartedSecond
                            )

                            if (onboard.powerMode != previousMode) {
                                events = updatePowerEvents(
                                    history = events,
                                    satelliteId = satellite.definition.id,
                                    previousMode = previousMode,
                                    currentMode = onboard.powerMode,
                                    atSecond = nextSecond
                                )
                            }
                        }

                        withReceivedTelemetry(
                            satellite.copy(onboardTelemetry = onboard),
                            onboard
                        )
                    }

                    val station =
                        OrbitalServicing.stationAt(second)

                    val previous =
                        current.servicingVehicle

                    val target = satellites.firstOrNull {
                        it.definition.id ==
                                previous.targetSatelliteId
                    }

                    // Physical rail attachment is independent of
                    // downlink availability and the vehicle HOLD phase.
                    val grip = ServicerAttachmentRegistry.current.value
                    val attachedToTarget =
                        grip?.isRailGripped == true &&
                        grip.workOrderId == previous.workOrderId &&
                        grip.satelliteId == previous.targetSatelliteId &&
                        grip.satelliteId == target?.definition?.id

                    val vehicle =
                        ServicingVehicleNavigation.advance(
                            vehicle = previous,
                            station = station,
                            target = target,
                            atSecond = second,
                            attachedToTarget = attachedToTarget
                        )

                    val established =
                        previous.phase ==
                                ServicingVehiclePhase.APPROACHING &&
                                vehicle.phase ==
                                ServicingVehiclePhase.FORMATION_FOLLOWING

                    if (established) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                vehicle.targetSatelliteId
                                    ?: "FLEET",
                            atSecond = second,
                            title =
                                "Formation following established",
                            description =
                                "SERVICER-01 matched the target's " +
                                        "schematic orbital track.",
                            severity =
                                MissionEventSeverity.INFO,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    val lost =
                        (
                                previous.phase ==
                                        ServicingVehiclePhase.APPROACHING ||
                                        previous.phase ==
                                        ServicingVehiclePhase.FORMATION_FOLLOWING
                                ) &&
                                vehicle.phase ==
                                ServicingVehiclePhase.HOLDING &&
                                vehicle.holdReason ==
                                VehicleHoldReason.TELEMETRY_UNAVAILABLE

                    if (lost) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                vehicle.targetSatelliteId
                                    ?: "FLEET",
                            atSecond = second,
                            title = if (attachedToTarget)
                                "Attached servicer guidance suspended"
                            else
                                "Vehicle tracking suspended",
                            description = if (attachedToTarget)
                                "Target telemetry unavailable. Rail grip " +
                                    "retained; SERVICER-01 follows the " +
                                    "simulated target orbit. Repair locked."
                            else
                                "Target telemetry is unavailable. " +
                                    "Robotic servicing controls are locked.",
                            severity =
                                MissionEventSeverity.WARNING,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    // Only rail-gripped vehicles return to automatic
                    // formation tracking when communications recover.
                    val regained = attachedToTarget &&
                        previous.phase == ServicingVehiclePhase.HOLDING &&
                        previous.holdReason ==
                            VehicleHoldReason.TELEMETRY_UNAVAILABLE &&
                        vehicle.phase ==
                            ServicingVehiclePhase.FORMATION_FOLLOWING

                    if (regained) {
                        events = appendEvent(
                            history = events,
                            satelliteId =
                                vehicle.targetSatelliteId ?: "FLEET",
                            atSecond = second,
                            title = "Attached servicer link restored",
                            description =
                                "SERVICER-01 remained rail-gripped " +
                                    "and resumed verified servicing guidance.",
                            severity = MissionEventSeverity.INFO,
                            kind = MissionEventKind.ACTIVITY
                        )
                    }

                    val docked =
                        previous.phase ==
                                ServicingVehiclePhase.RETURNING &&
                                vehicle.phase ==
                                ServicingVehiclePhase.DOCKED

                    if (docked) {

                        events = appendEvent(
                            history = events,
                            satelliteId =
                                previous.targetSatelliteId
                                    ?: "FLEET",
                            atSecond = second,
                            title = "Servicing vehicle docked",
                            description =
                                "SERVICER-01 returned to OS-001; " +
                                        "deployment closed.",
                            severity =
                                MissionEventSeverity.INFO,
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
                            RepairProgressRetention.keepUnfinished(
                                current.repairSession,
                                current.repairOrders
                            )
                        else
                            current.repairSession
                    )
                }
                }

                // ViewModel owns cleanup even if the repair panel
                // is not currently being displayed.
                val latest = _uiState.value
                if (
                    latest.servicingVehicle.phase ==
                        ServicingVehiclePhase.DOCKED &&
                    latest.preparedMission == null &&
                    ServicerAttachmentRegistry.current.value != null
                ) {
                    ServicerAttachmentRegistry.clear()
                }
            }
        }
    }

    /**
     * Selects a satellite for detailed monitoring.
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
     * Applies or clears a temporary fault.
     *
     * Persistent equipment damage is unaffected.
     */
    fun setFault(fault: SatelliteFault) {

        _uiState.update { current ->

            val selected = current.selectedSatellite

            if (selected.fault == fault) {
                return@update current
            }

            val now = current.elapsedSeconds

            val started =
                if (fault == SatelliteFault.NONE)
                    null
                else
                    now

            val satellites = current.satellites.map {
                    satellite ->

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
                            satellite = satellite.definition,
                            fault = fault,
                            faultStartedSecond = started,
                            equipmentDamage =
                                satellite.equipmentDamage,
                            equipmentDamageStartedSecond =
                                satellite.equipmentDamageStartedSecond
                        )

                    withReceivedTelemetry(
                        satellite.copy(
                            fault = fault,
                            faultStartedSecond = started,
                            onboardTelemetry = onboard
                        ),
                        onboard
                    )
                }
            }

            var events = current.events

            if (
                selected.fault != SatelliteFault.NONE
            ) {

                events =
                    MissionEventLog.resolveActiveForSource(
                        history = events,
                        satelliteId =
                            selected.definition.id,
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
    fun clearFault() =
        setFault(SatelliteFault.NONE)

    /**
     * Injects persistent hardware damage and
     * creates the corresponding repair work order.
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

                    val onboard =
                        SatelliteSimulator.refresh(
                            previous =
                                satellite.onboardTelemetry,
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

            val nextId = (
                    current.repairOrders.maxOfOrNull {
                        it.id
                    } ?: 0L
                    ) + 1L

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
                            "Required servicing: " +
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
     * Moves an open repair request into the servicing queue.
     */
    fun queueRepair(orderId: Long) {

        _uiState.update { current ->

            val order = current.repairOrders.firstOrNull {
                it.id == orderId &&
                        it.status == RepairStatus.OPEN
            } ?: return@update current

            val orders = RepairWorkOrders.queue(
                orders = current.repairOrders,
                orderId = orderId,
                atSecond = current.elapsedSeconds
            )

            val events = appendEvent(
                history = current.events,
                satelliteId = order.satelliteId,
                atSecond = current.elapsedSeconds,
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
     * Prepares a queued mission while the robot is docked.
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

            if (
                target.equipmentDamage != order.damage
            ) {
                return@update current
            }

            val plan = OrbitalServicing.prepare(
                orders = current.repairOrders,
                satellites = current.satellites,
                workOrderId = orderId,
                atSecond = current.elapsedSeconds
            )

            val events = appendEvent(
                history = current.events,
                satelliteId = order.satelliteId,
                atSecond = current.elapsedSeconds,
                title = "Servicing mission prepared",
                description =
                    "OS-001 prepared work order #$orderId. " +
                            "No repair performed.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                preparedMission = plan,
                repairSession = RepairProgressRetention.forOrder(
                    current.repairSession,
                    order
                ),
                events = events
            )
        }

        // Initialize shared attachment state for the prepared
        // mission outside the atomic StateFlow update lambda.
        val activePlan = _uiState.value.preparedMission
        if (
            activePlan?.workOrderId == orderId &&
            _uiState.value.servicingVehicle.phase ==
                ServicingVehiclePhase.DOCKED
        ) {
            ServicerAttachmentRegistry.prepareMission(
                workOrderId = activePlan.workOrderId,
                satelliteId = activePlan.targetSatelliteId
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
                    "Work order #${plan.workOrderId} " +
                            "remains queued.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                preparedMission = null,
                repairSession = RepairProgressRetention.keepUnfinished(
                    current.repairSession,
                    current.repairOrders
                ),
                events = events
            )
        }

        if (
            _uiState.value.preparedMission == null &&
            _uiState.value.servicingVehicle.phase ==
                ServicingVehiclePhase.DOCKED
        ) {
            ServicerAttachmentRegistry.clear()
        }
    }

    /**
     * Dispatches the vehicle when the target
     * has current received telemetry.
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

            val vehicle =
                ServicingVehicleNavigation.dispatch(
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
                    "SERVICER-01 departed OS-001 for " +
                            "work order #${plan.workOrderId}.",
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
     * Begins the vehicle's approach to its assigned target.
     */
    fun beginVehicleApproach() {

        _uiState.update { current ->

            val vehicle = current.servicingVehicle

            if (
                vehicle.phase !=
                ServicingVehiclePhase.DEPLOYED
            ) {
                return@update current
            }

            val target = current.satellites.firstOrNull {
                it.definition.id ==
                        vehicle.targetSatelliteId
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

            val next =
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
                servicingVehicle = next,
                events = events
            )
        }
    }

    /**
     * Pauses scripted approach without clearing repair progress.
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

            val next =
                ServicingVehicleNavigation.hold(vehicle)

            val events = appendEvent(
                history = current.events,
                satelliteId =
                    vehicle.targetSatelliteId ?: "FLEET",
                atSecond = current.elapsedSeconds,
                title = "Vehicle hold requested",
                description =
                    "Operator paused scripted guidance.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                servicingVehicle = next,
                events = events
            )
        }
    }

    /**
     * Resumes approach after fresh telemetry is available.
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
                it.definition.id ==
                        vehicle.targetSatelliteId
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

            val next =
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
                servicingVehicle = next,
                events = events
            )
        }
    }

    /**
     * Returns the deployed vehicle to OS-001.
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

            val next =
                ServicingVehicleNavigation.beginReturn(
                    vehicle
                )

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
                servicingVehicle = next,
                events = events
            )
        }

        // An explicit return command releases the satellite
        // rail; loss of telemetry alone never calls release.
        val returning = _uiState.value.servicingVehicle
        if (returning.phase == ServicingVehiclePhase.RETURNING) {
            returning.workOrderId?.let { workOrderId ->
                ServicerAttachmentRegistry.release(workOrderId)
            }
        }
    }

    /**
     * Checks mission assignment, current telemetry,
     * and verified formation before a servicing action.
     */
    private fun canOperateRepair(
        state: OrbitalUiState
    ): Boolean {

        val session = state.repairSession
            ?: return false

        if (session.isComplete) {
            return false
        }

        val order = state.repairOrders.firstOrNull {
            it.id == session.workOrderId
        } ?: return false

        val physicalAttachment = ServicerAttachmentRegistry.current.value
        val grippingMission = physicalAttachment?.workOrderId == order.id &&
                physicalAttachment.satelliteId == order.satelliteId &&
                physicalAttachment.docking.phase == ServicerDockPhase.GRIPPED &&
                !physicalAttachment.docking.choosingFace

        return grippingMission &&
                session.satelliteId == order.satelliteId &&
                session.damage == order.damage &&
                state.preparedMission?.workOrderId == order.id &&
                RepairCompletionPolicy.evaluate(
                    state,
                    order
                ) == RepairCompletionStatus.READY_IN_FORMATION
    }

    /**
     * Opens the interactive service model for
     * the active repair work order.
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
                RepairCompletionPolicy.evaluate(
                    current,
                    order
                ) !=
                RepairCompletionStatus.READY_IN_FORMATION
            ) {
                return@update current
            }

            // Enforce physical rail attachment in the ViewModel, not only UI.
            val grip = ServicerAttachmentRegistry.current.value
            if (grip?.workOrderId != order.id ||
                grip.satelliteId != order.satelliteId ||
                grip.docking.phase != ServicerDockPhase.GRIPPED) {
                return@update current
            }

            val session = RoboticRepairSimulator.start(order)

            val events = appendEvent(
                history = current.events,
                satelliteId = order.satelliteId,
                atSecond = current.elapsedSeconds,
                title = "Satellite access procedure started",
                description =
                    "SERVICER-01 opened the interactive " +
                            "service model for work order " +
                            "#${order.id}.",
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
     * Changes the selected tool without
     * advancing the repair procedure.
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
                    RoboticRepairSimulator.selectTool(
                        session,
                        tool
                    )
            )
        }
    }

    /**
     * Chooses between component repair and replacement.
     */
    fun chooseRepairMethod(
        method: RepairServiceMethod
    ) {

        _uiState.update { current ->

            if (!canOperateRepair(current)) {
                return@update current
            }

            val session = current.repairSession
                ?: return@update current

            val face = ServicerAttachmentRegistry.current.value?.docking?.activeFace
            if (face == null || !RepairServiceAccessPolicy.canAccess(session, face)) {
                return@update current
            }

            val result =
                RoboticRepairSimulator.chooseMethod(
                    session,
                    method
                )

            recordInteraction(
                state = current,
                result = result,
                activityTitle = "Service method selected"
            )
        }
    }

    /**
     * Processes a tap on a screw, cover, exposed component,
     * mounting socket, or diagnostics connector.
     *
     * Final verification completes the work order
     * atomically with the repair session state.
     */
    fun interactWithRepairModel(
        hotspot: RepairHotspot
    ) {

        _uiState.update { current ->

            if (!canOperateRepair(current)) {
                return@update current
            }

            val session = current.repairSession
                ?: return@update current

            val face = ServicerAttachmentRegistry.current.value?.docking?.activeFace
            if (face == null || !RepairServiceAccessPolicy.canAccess(session, face, hotspot)) {
                return@update current
            }

            val result =
                RoboticRepairSimulator.interact(
                    session,
                    hotspot
                )

            val updated = recordInteraction(
                state = current,
                result = result,
                activityTitle = "Satellite servicing action"
            )

            if (!result.completed) {
                return@update updated
            }

            val order = updated.repairOrders.firstOrNull {
                it.id == session.workOrderId &&
                        it.status == RepairStatus.QUEUED
            } ?: return@update updated

            finishRepair(
                current = updated,
                order = order,
                completedInFormation = true
            )
        }
    }

    /**
     * Records successful servicing state changes
     * in the mission event history.
     */
    private fun recordInteraction(
        state: OrbitalUiState,
        result: RepairInteractionResult,
        activityTitle: String
    ): OrbitalUiState {

        var updated = state.copy(
            repairSession = result.session
        )

        if (
            result.succeeded &&
            result.stepChanged
        ) {

            updated = updated.copy(
                events = appendEvent(
                    history = state.events,
                    satelliteId =
                        result.session.satelliteId,
                    atSecond = state.elapsedSeconds,
                    title = activityTitle,
                    description =
                        "Work order " +
                                "#${result.session.workOrderId}: " +
                                result.session.feedback,
                    severity = MissionEventSeverity.INFO,
                    kind = MissionEventKind.ACTIVITY
                )
            )
        }

        return updated
    }

    /**
     * Restarts an incomplete service procedure
     * while formation and telemetry are verified.
     */
    fun restartRoboticRepair() {

        _uiState.update { current ->

            if (!canOperateRepair(current)) {
                return@update current
            }

            val session = current.repairSession
                ?: return@update current

            val order = current.repairOrders.firstOrNull {
                it.id == session.workOrderId &&
                        it.status == RepairStatus.QUEUED
            } ?: return@update current

            val events = appendEvent(
                history = current.events,
                satelliteId = session.satelliteId,
                atSecond = current.elapsedSeconds,
                title = "Satellite servicing restarted",
                description =
                    "Operator reset the local service model " +
                            "for work order #${order.id}.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(
                repairSession =
                    RoboticRepairSimulator.start(order),
                events = events
            )
        }
    }

    /**
     * Preserves the docked-only debug completion shortcut.
     *
     * Normal in-orbit repair completion must be performed
     * through the interactive satellite service model.
     */
    fun completeRepair(orderId: Long) {

        _uiState.update { current ->

            val order = current.repairOrders.firstOrNull {
                it.id == orderId
            } ?: return@update current

            if (
                RepairCompletionPolicy.evaluate(
                    current,
                    order
                ) !=
                RepairCompletionStatus.READY_AT_STATION
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
     * Completes the work order, restores damaged equipment,
     * and resolves only its corresponding damage alert.
     *
     * Successful orbital servicing keeps SERVICER-01
     * following its target until the operator returns home.
     */
    private fun finishRepair(
        current: OrbitalUiState,
        order: RepairWorkOrder,
        completedInFormation: Boolean
    ): OrbitalUiState {

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
            orderId = order.id,
            atSecond = now
        )

        var events =
            MissionEventLog.resolveActiveForSource(
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

                "SERVICER-01 completed panel servicing " +
                        "for work order #${order.id}. " +
                        "Return to OS-001 when ready."

            } else {

                "Work order #${order.id} was completed " +
                        "using the docked debug shortcut."
            },
            severity = MissionEventSeverity.INFO,
            kind = MissionEventKind.ACTIVITY
        )

        val closePlan =
            !completedInFormation &&
                    current.preparedMission?.workOrderId ==
                    order.id

        return current.copy(
            satellites = satellites,
            repairOrders = orders,
            events = events,
            preparedMission = if (closePlan)
                null
            else
                current.preparedMission,
            repairSession = if (closePlan)
                null
            else
                current.repairSession
        )
    }

    /**
     * Records received telemetry only while
     * the spacecraft has an operational downlink.
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
     * Acknowledges an operational alert without
     * clearing its underlying fault or damage.
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
                    "Operator acknowledged " +
                            "${alert.title}; condition remains active.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )

            current.copy(events = events)
        }
    }

    /**
     * Maintains independent automatic power alerts
     * when spacecraft power operating modes change.
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
                    "Simulated electrical power " +
                            "returned to normal.",
                severity = MissionEventSeverity.INFO,
                kind = MissionEventKind.ACTIVITY
            )
        }

        val (title, description, severity) =
            when (currentMode) {

                PowerMode.LOW_POWER -> Triple(
                    "Low battery",
                    "The spacecraft reduced electrical consumption.",
                    MissionEventSeverity.WARNING
                )

                PowerMode.SAFE_MODE -> Triple(
                    "Power safe mode",
                    "Nonessential systems are disabled in the simulator.",
                    MissionEventSeverity.CRITICAL
                )

                PowerMode.OFFLINE -> Triple(
                    "Simulated power depletion",
                    "Ground telemetry is unavailable due to depleted power.",
                    MissionEventSeverity.CRITICAL
                )

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
     * Maps temporary faults to mission alert severity.
     */
    private fun severityForFault(
        fault: SatelliteFault
    ): MissionEventSeverity = when (fault) {

        SatelliteFault.COMMUNICATION_LOSS,
        SatelliteFault.PAYLOAD_FAILURE ->
            MissionEventSeverity.CRITICAL

        SatelliteFault.POWER_DRAIN,
        SatelliteFault.THERMAL_OVERLOAD ->
            MissionEventSeverity.WARNING

        SatelliteFault.NONE ->
            MissionEventSeverity.INFO
    }

    /**
     * Maps equipment damage to mission alert severity.
     */
    private fun severityForDamage(
        damage: EquipmentDamage
    ): MissionEventSeverity = when (damage) {

        EquipmentDamage.PAYLOAD_SENSOR_DAMAGE ->
            MissionEventSeverity.CRITICAL

        EquipmentDamage.SOLAR_ARRAY_DAMAGE,
        EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE,
        EquipmentDamage.THERMAL_RADIATOR_DAMAGE ->
            MissionEventSeverity.WARNING

        EquipmentDamage.NONE ->
            MissionEventSeverity.INFO
    }

    /**
     * Adds a source-scoped event to the mission history.
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
                history.maxOfOrNull {
                    it.id
                } ?: 0L
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
     * Pauses or resumes the complete simulation, including the offline clock.
     * The preference is committed immediately so a quick app close won't
     * accidentally resume the live timer at the next startup.
     *
     * Resetting the mission does not implicitly resume a paused simulation.
     */
    fun toggleSimulationPaused() {
        val paused = !_isSimulationPaused.value
        if (!persistentStateStore.persistSimulationPaused(paused)) return
        lastSimulationTickElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        _isSimulationPaused.value = paused
    }

    /**
     * Resets satellites, maintenance records,
     * servicing station, vehicle, and repair model.
     */
    fun resetSimulation() {

        // Do not carry a pending background interval across a reset.
        lastSimulationTickElapsedRealtimeMillis = SystemClock.elapsedRealtime()

        ServicerAttachmentRegistry.clear()
        ServicerAttachmentRegistry.clear()
        _uiState.value = createInitialState()
    }
}
