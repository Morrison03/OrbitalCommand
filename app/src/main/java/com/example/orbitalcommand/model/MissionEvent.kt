
package com.example.orbitalcommand.model

/**
 * Classifies the urgency of a mission event.
 */
enum class MissionEventSeverity {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * Distinguishes actionable alerts from ordinary
 * operational history entries.
 */
enum class MissionEventKind {
    ALERT,
    ACTIVITY
}

/**
 * Represents a recorded event in the mission simulation.
 *
 * Alert events can be acknowledged and resolved independently.
 * Activity events provide an informational audit trail.
 *
 * All timestamps use elapsed simulation seconds.
 */
data class MissionEvent(
    val id: Long,
    val satelliteId: String,
    val occurredAtSecond: Int,
    val title: String,
    val description: String,
    val severity: MissionEventSeverity,
    val kind: MissionEventKind,
    val acknowledgedAtSecond: Int? = null,
    val resolvedAtSecond: Int? = null
) {

    /**
     * Indicates whether this event represents an
     * unresolved operational alert.
     */
    val isActive: Boolean
        get() = kind == MissionEventKind.ALERT &&
                resolvedAtSecond == null

    /**
     * Indicates whether an operator has acknowledged
     * this event.
     */
    val isAcknowledged: Boolean
        get() = acknowledgedAtSecond != null
}

/**
 * Provides immutable operations for recording and updating
 * the mission event history.
 *
 * Active alerts are retained until they are resolved.
 * The number of archived events is bounded to prevent
 * unbounded memory growth.
 */
object MissionEventLog {

    const val MAX_ARCHIVED_EVENTS = 100

    /**
     * Records an event with a unique, increasing identifier.
     *
     * The newest event appears first in the returned history.
     */
    fun record(
        history: List<MissionEvent>,
        event: MissionEvent
    ): List<MissionEvent> {

        require(event.id > 0L) {
            "Event identifiers must be positive."
        }

        require(event.occurredAtSecond >= 0) {
            "Simulation time cannot be negative."
        }

        require(history.none { it.id == event.id }) {
            "An event with this identifier already exists."
        }

        return trim(
            listOf(event) + history
        )
    }

    /**
     * Acknowledges an active alert without resolving
     * its underlying fault.
     *
     * Missing, resolved, or previously acknowledged
     * alerts remain unchanged.
     */
    fun acknowledge(
        history: List<MissionEvent>,
        eventId: Long,
        atSecond: Int
    ): List<MissionEvent> {

        require(atSecond >= 0) {
            "Simulation time cannot be negative."
        }

        return history.map { event ->

            if (
                event.id == eventId &&
                event.isActive &&
                !event.isAcknowledged
            ) {

                event.copy(
                    acknowledgedAtSecond = atSecond
                )

            } else {
                event
            }
        }
    }

    /**
     * Resolves active alerts associated with a satellite.
     *
     * The acknowledgement state is preserved for
     * subsequent review in the event history.
     */
    fun resolveActiveForSatellite(
        history: List<MissionEvent>,
        satelliteId: String,
        atSecond: Int
    ): List<MissionEvent> {

        require(atSecond >= 0) {
            "Simulation time cannot be negative."
        }

        val updated = history.map { event ->

            if (
                event.satelliteId == satelliteId &&
                event.isActive
            ) {

                event.copy(
                    resolvedAtSecond = atSecond
                )

            } else {
                event
            }
        }

        return trim(updated)
    }

    /**
     * Retains all active alerts and the most recent
     * archived or informational events.
     */
    private fun trim(
        history: List<MissionEvent>
    ): List<MissionEvent> {

        val activeAlerts = history.filter {
            it.isActive
        }

        val archivedEvents = history
            .filterNot { it.isActive }
            .sortedByDescending { it.id }
            .take(MAX_ARCHIVED_EVENTS)

        return (activeAlerts + archivedEvents)
            .sortedByDescending { it.id }
    }
}

