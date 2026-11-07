
package com.example.orbitalcommand.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that alerts from independent subsystems
 * can be resolved without affecting one another.
 */
class PowerAlertIsolationTest {

    /**
     * Ensures that resolving a power system alert
     * does not resolve a manual fault alert
     * belonging to the same spacecraft.
     */
    @Test
    fun resolvingPowerAlertPreservesManualFault() {

        val powerAlert = MissionEvent(
            id = 1L,
            satelliteId = "SAT-001",
            occurredAtSecond = 10,
            title = "Low battery",
            description = "Battery is low.",
            severity = MissionEventSeverity.WARNING,
            kind = MissionEventKind.ALERT,
            sourceKey = "POWER_STATUS"
        )

        val manualFault = MissionEvent(
            id = 2L,
            satelliteId = "SAT-001",
            occurredAtSecond = 15,
            title = "Payload Failure",
            description = "Payload is unavailable.",
            severity = MissionEventSeverity.CRITICAL,
            kind = MissionEventKind.ALERT,
            sourceKey = "MANUAL_FAULT"
        )

        val updated =
            MissionEventLog.resolveActiveForSource(
                history = listOf(
                    manualFault,
                    powerAlert
                ),
                satelliteId = "SAT-001",
                sourceKey = "POWER_STATUS",
                atSecond = 20
            )

        val resolvedPower = updated.first {
            it.id == 1L
        }

        val remainingFault = updated.first {
            it.id == 2L
        }

        assertFalse(resolvedPower.isActive)
        assertTrue(remainingFault.isActive)
    }

    /**
     * Verifies that clearing a manually injected fault
     * leaves an unrelated power status alert active.
     */
    @Test
    fun resolvingManualFaultPreservesPowerAlert() {

        val powerAlert = MissionEvent(
            id = 1L,
            satelliteId = "SAT-002",
            occurredAtSecond = 10,
            title = "Power safe mode",
            description = "Low battery charge.",
            severity = MissionEventSeverity.CRITICAL,
            kind = MissionEventKind.ALERT,
            sourceKey = "POWER_STATUS"
        )

        val manualFault = MissionEvent(
            id = 2L,
            satelliteId = "SAT-002",
            occurredAtSecond = 12,
            title = "Power Drain",
            description = "Abnormal electrical load.",
            severity = MissionEventSeverity.WARNING,
            kind = MissionEventKind.ALERT,
            sourceKey = "MANUAL_FAULT"
        )

        val updated =
            MissionEventLog.resolveActiveForSource(
                history = listOf(
                    manualFault,
                    powerAlert
                ),
                satelliteId = "SAT-002",
                sourceKey = "MANUAL_FAULT",
                atSecond = 30
            )

        assertTrue(
            updated.first { it.id == 1L }.isActive
        )

        assertFalse(
            updated.first { it.id == 2L }.isActive
        )
    }
}

