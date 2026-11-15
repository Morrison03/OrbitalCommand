
package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for scripted satellite docking,
 * rail attachment, and repositioning.
 */
class ServicerDockingControllerTest {

    private fun orbiting() = ServicerDockingState(
        missionOrderId = 12L
    )

    @Test
    fun dockingRequiresVerifiedNavigation() {

        val start = orbiting()

        assertEquals(
            start,
            ServicerDockingController.beginDock(
                start,
                false
            )
        )

        assertEquals(
            ServicerDockPhase.APPROACHING,
            ServicerDockingController.beginDock(
                start,
                true
            ).phase
        )
    }

    @Test
    fun railGripOccursAfterApproachFinishes() {

        val approaching =
            ServicerDockingController.beginDock(
                orbiting(),
                true
            )

        assertFalse(approaching.grippingRail)

        val attached =
            ServicerDockingController.finishDock(
                approaching,
                true
            )

        assertTrue(attached.grippingRail)

        assertEquals(
            SatelliteServiceFace.SERVICE,
            attached.activeFace
        )
    }

    @Test
    fun communicationLossPausesDocking() {

        val approaching =
            ServicerDockingController.beginDock(
                orbiting(),
                true
            )

        assertEquals(
            approaching,
            ServicerDockingController.finishDock(
                approaching,
                false
            )
        )
    }

    @Test
    fun moveIsUnavailableUntilRailIsGripped() {

        val state = orbiting()

        assertFalse(
            ServicerDockingController.toggleMove(
                state,
                true
            ).choosingFace
        )
    }

    @Test
    fun choosingCurrentFaceDoesNotReposition() {

        val attached =
            ServicerDockingController.finishDock(
                ServicerDockingController.beginDock(
                    orbiting(),
                    true
                ),
                true
            )

        val choosing =
            ServicerDockingController.toggleMove(
                attached,
                true
            )

        val same =
            ServicerDockingController.selectFace(
                choosing,
                SatelliteServiceFace.SERVICE,
                true
            )

        assertEquals(
            ServicerDockPhase.GRIPPED,
            same.phase
        )

        assertFalse(same.choosingFace)
    }

    @Test
    fun movingMaintainsAtLeastOneRailGrip() {

        val attached =
            ServicerDockingController.finishDock(
                ServicerDockingController.beginDock(
                    orbiting(),
                    true
                ),
                true
            )

        val choosing =
            ServicerDockingController.toggleMove(
                attached,
                true
            )

        val moving =
            ServicerDockingController.selectFace(
                choosing,
                SatelliteServiceFace.PORT,
                true
            )

        assertEquals(
            ServicerDockPhase.MOVING,
            moving.phase
        )

        assertTrue(moving.grippingRail)

        assertEquals(
            SatelliteServiceFace.SERVICE,
            moving.activeFace
        )

        assertEquals(
            SatelliteServiceFace.PORT,
            moving.targetFace
        )
    }

    @Test
    fun communicationLossPausesSideMovement() {

        val attached =
            ServicerDockingController.finishDock(
                ServicerDockingController.beginDock(
                    orbiting(),
                    true
                ),
                true
            )

        val moving =
            ServicerDockingController.selectFace(
                ServicerDockingController.toggleMove(
                    attached,
                    true
                ),
                SatelliteServiceFace.AFT,
                true
            )

        assertEquals(
            moving,
            ServicerDockingController.finishMove(
                moving,
                false
            )
        )

        val finished =
            ServicerDockingController.finishMove(
                moving,
                true
            )

        assertEquals(
            ServicerDockPhase.GRIPPED,
            finished.phase
        )

        assertEquals(
            SatelliteServiceFace.AFT,
            finished.activeFace
        )
    }

    @Test
    fun allSixFacesHaveDifferentTargetViewAngles() {

        val angles =
            SatelliteServiceFace.entries.map {
                it.viewYaw to it.viewPitch
            }

        assertEquals(
            6,
            angles.distinct().size
        )

        assertEquals(
            180f,
            SatelliteServiceFace.AFT.viewYaw,
            0.001f
        )
    }
}
