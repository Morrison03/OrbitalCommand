package com.example.orbitalcommand.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test

/** Regression tests for mission-wide physical rail attachment. */
class ServicerAttachmentRegistryTest {
    @After
    fun tearDown() {
        ServicerAttachmentRegistry.clear()
    }

    @Test
    fun railGripSurvivesMissionReobservation() {
        val id = 42L
        ServicerAttachmentRegistry.prepareMission(id, "SAT-001")
        ServicerAttachmentRegistry.update(
            ServicerDockingState(id).copy(
                phase = ServicerDockPhase.GRIPPED
            )
        )

        // Called by the repair view on recomposition; must not undock.
        ServicerAttachmentRegistry.prepareMission(id, "SAT-001")

        assertTrue(ServicerAttachmentRegistry.isAttachedTo("SAT-001"))
        assertFalse(ServicerAttachmentRegistry.isAttachedTo("SAT-002"))
    }

    @Test
    fun returnReleasesOnlyTheActiveMission() {
        val id = 42L
        ServicerAttachmentRegistry.prepareMission(id, "SAT-001")
        ServicerAttachmentRegistry.update(
            ServicerDockingState(id).copy(
                phase = ServicerDockPhase.GRIPPED
            )
        )

        ServicerAttachmentRegistry.release(13L)
        assertTrue(ServicerAttachmentRegistry.isAttachedTo("SAT-001"))

        ServicerAttachmentRegistry.release(id)
        assertFalse(ServicerAttachmentRegistry.isAttachedTo("SAT-001"))
    }

    @Test
    fun movingMaintainsPhysicalGrip() {
        val id = 42L
        ServicerAttachmentRegistry.prepareMission(id, "SAT-001")
        ServicerAttachmentRegistry.update(
            ServicerDockingState(id).copy(
                phase = ServicerDockPhase.MOVING
            )
        )
        assertTrue(ServicerAttachmentRegistry.isAttachedTo("SAT-001"))
        assertEquals(
            ServicerDockPhase.MOVING,
            ServicerAttachmentRegistry.current.value?.docking?.phase
        )
    }
}
