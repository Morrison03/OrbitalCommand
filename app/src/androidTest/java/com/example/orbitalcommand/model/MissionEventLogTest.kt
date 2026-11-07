
package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies event recording, acknowledgement,
 * resolution, ordering, and history retention.
 */
class MissionEventLogTest {

    /**
     * Creates a simulated alert for unit testing.
     */
    private fun createAlert(
        id: Long,
        satelliteId: String = "SAT-001"
    ): MissionEvent {

        return MissionEvent(
            id = id,
            satelliteId = satelliteId,
            occurredAtSecond = id.toInt(),
            title = "Simulated fault",
            description = "A test fault is active.",
            severity = MissionEventSeverity.WARNING,
            kind = MissionEventKind.ALERT
        )
    }

    /**
     * Creates an informational mission event.
     */
    private fun createActivity(
        id: Long
    ): MissionEvent {

        return MissionEvent(
            id = id,
            satelliteId = "FLEET",
            occurredAtSecond = id.toInt(),
            title = "Simulation activity",
            description = "A test event was recorded.",
            severity = MissionEventSeverity.INFO,
            kind = MissionEventKind.ACTIVITY
        )
    }

    /**
     * Verifies that new events appear before older events.
     */
    @Test
    fun newestEventsAppearFirst() {

        var history = emptyList<MissionEvent>()

        history = MissionEventLog.record(
            history,
            createActivity(1)
        )

        history = MissionEventLog.record(
            history,
            createActivity(2)
        )

        assertEquals(
            listOf(2L, 1L),
            history.map { it.id }
        )
    }

    /**
     * Verifies that acknowledging an alert does
     * not resolve its underlying fault.
     */
    @Test
    fun acknowledgementDoesNotResolveAlert() {

        val history = listOf(
            createAlert(1)
        )

        val updated = MissionEventLog.acknowledge(
            history = history,
            eventId = 1L,
            atSecond = 20
        )

        val alert = updated.first()

        assertTrue(alert.isAcknowledged)
        assertTrue(alert.isActive)

        assertEquals(
            20,
            alert.acknowledgedAtSecond
        )

        assertEquals(
            null,
            alert.resolvedAtSecond
        )
    }

    /**
     * Confirms that resolving an alert preserves
     * its previous acknowledgement.
     */
    @Test
    fun resolutionPreservesAcknowledgement() {

        val alert = createAlert(1).copy(
            acknowledgedAtSecond = 15
        )

        val updated =
            MissionEventLog.resolveActiveForSatellite(
                history = listOf(alert),
                satelliteId = "SAT-001",
                atSecond = 30
            )

        val resolved = updated.first()

        assertFalse(resolved.isActive)
        assertTrue(resolved.isAcknowledged)

        assertEquals(
            30,
            resolved.resolvedAtSecond
        )
    }

    /**
     * Ensures that resolving one satellite's
     * alert does not affect another spacecraft.
     */
    @Test
    fun resolutionOnlyAffectsTargetSatellite() {

        val first = createAlert(
            id = 1L,
            satelliteId = "SAT-001"
        )

        val second = createAlert(
            id = 2L,
            satelliteId = "SAT-002"
        )

        val updated =
            MissionEventLog.resolveActiveForSatellite(
                history = listOf(second, first),
                satelliteId = "SAT-001",
                atSecond = 40
            )

        val firstResult = updated.first {
            it.satelliteId == "SAT-001"
        }

        val secondResult = updated.first {
            it.satelliteId == "SAT-002"
        }

        assertFalse(firstResult.isActive)
        assertTrue(secondResult.isActive)
    }

    /**
     * Verifies that informational events are not
     * considered active operational alerts.
     */
    @Test
    fun activityEventsAreNotActiveAlerts() {

        val activity = createActivity(1)

        assertFalse(activity.isActive)
        assertFalse(activity.isAcknowledged)
    }

    /**
     * Ensures that archived history remains bounded
     * while active alerts are always retained.
     */
    @Test
    fun activeAlertsSurviveHistoryLimit() {

        var history = listOf(
            createAlert(1)
        )

        for (id in 2L..150L) {

            history = MissionEventLog.record(
                history = history,
                event = createActivity(id)
            )
        }

        val activeAlerts = history.filter {
            it.isActive
        }

        val archivedEvents = history.filterNot {
            it.isActive
        }

        assertEquals(1, activeAlerts.size)

        assertEquals(
            MissionEventLog.MAX_ARCHIVED_EVENTS,
            archivedEvents.size
        )

        assertTrue(
            history.any { it.id == 1L && it.isActive }
        )
    }

    /**
     * Verifies that duplicate identifiers are rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun duplicateEventIdentifiersAreRejected() {

        val history = listOf(
            createActivity(1)
        )

        MissionEventLog.record(
            history = history,
            event = createActivity(1)
        )
    }

    /**
     * Confirms that acknowledging a resolved alert
     * does not change the recorded event.
     */
    @Test
    fun resolvedAlertCannotBeAcknowledgedAgain() {

        val resolved = createAlert(1).copy(
            resolvedAtSecond = 30
        )

        val updated = MissionEventLog.acknowledge(
            history = listOf(resolved),
            eventId = 1L,
            atSecond = 40
        )

        assertEquals(
            resolved,
            updated.first()
        )
    }
}

