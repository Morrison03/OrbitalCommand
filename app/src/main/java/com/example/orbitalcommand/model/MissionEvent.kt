
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
 * Distinguishes actionable alerts from
 * informational operational events.
 */
enum class MissionEventKind {
    ALERT,
    ACTIVITY
}

/**
 * Represents an event recorded during the simulation.
 *
 * sourceKey identifies the subsystem responsible for
 * an alert, allowing independent faults to be resolved
 * without affecting unrelated active alerts.
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
    val resolvedAtSecond: Int? = null,
    val sourceKey: String? = null
) {

    /**
     * Indicates whether an operational alert
     * remains unresolved.
     */
    val isActive: Boolean
        get() = kind == MissionEventKind.ALERT &&
                resolvedAtSecond == null

    /**
     * Indicates whether an operator acknowledged
     * the event.
     */
    val isAcknowledged: Boolean
        get() = acknowledgedAtSecond != null
}

/**
 * Maintains immutable, bounded mission event history.
 *
 * Active alerts are retained until resolution, while
 * archived events are limited to a fixed collection size.
 */
object MissionEventLog {

    const val MAX_ARCHIVED_EVENTS = 100

    /**
     * Records a new event and places it before
     * older events.
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
            "Duplicate event identifier."
        }

        return trim(
            listOf(event) + history
        )
    }

    /**
     * Acknowledges an active alert without resolving
     * its underlying operational problem.
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
     * Resolves all active alerts associated with
     * a particular satellite.
     *
     * This operation is retained for general fleet
     * maintenance and existing test compatibility.
     */
    fun resolveActiveForSatellite(
        history: List<MissionEvent>,
        satelliteId: String,
        atSecond: Int
    ): List<MissionEvent> {

        require(atSecond >= 0) {
            "Simulation time cannot be negative."
        }

        return trim(
            history.map { event ->

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
        )
    }

    /**
     * Resolves only alerts from the specified
     * spacecraft and subsystem source.
     *
     * For example, clearing a manually injected fault
     * must not clear an unrelated power status alert.
     */
    fun resolveActiveForSource(
        history: List<MissionEvent>,
        satelliteId: String,
        sourceKey: String,
        atSecond: Int
    ): List<MissionEvent> {

        require(atSecond >= 0) {
            "Simulation time cannot be negative."
        }

        return trim(
            history.map { event ->

                if (
                    event.satelliteId == satelliteId &&
                    event.sourceKey == sourceKey &&
                    event.isActive
                ) {

                    event.copy(
                        resolvedAtSecond = atSecond
                    )

                } else {
                    event
                }
            }
        )
    }

    /**
     * Retains every active alert and the most recent
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

