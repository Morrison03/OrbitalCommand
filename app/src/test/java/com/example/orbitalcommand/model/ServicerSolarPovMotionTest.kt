package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for detachable solar wings and distinct rail maneuvers. */
class ServicerSolarPovMotionTest {

    @Test
    fun starboardSlidesWithoutSideOrbitOrRoll() {
        assertEquals(0f, ServicerSolarPovMotion.targetYaw(SatelliteServiceFace.STARBOARD))
        assertEquals(0f, ServicerSolarPovMotion.targetPitch(SatelliteServiceFace.STARBOARD))
        assertTrue(ServicerSolarPovMotion.cameraSlide(SatelliteServiceFace.STARBOARD) > 0f)
        assertEquals(0f, ServicerSolarPovMotion.cameraRoll(SatelliteServiceFace.STARBOARD))
    }

    @Test
    fun portSlidesAndRollsUpsideDownWithoutSideOrbit() {
        assertEquals(0f, ServicerSolarPovMotion.targetYaw(SatelliteServiceFace.PORT))
        assertTrue(ServicerSolarPovMotion.cameraSlide(SatelliteServiceFace.PORT) < 0f)
        assertEquals(180f, ServicerSolarPovMotion.cameraRoll(SatelliteServiceFace.PORT))
        assertEquals(0f, ServicerSolarPovMotion.cameraSlide(SatelliteServiceFace.SERVICE))
    }

    @Test
    fun leftHandGripsDistinctPhysicalRails() {
        val port = ServicerSolarPovMotion.leftGripRail(SatelliteServiceFace.PORT)
        val starboard = ServicerSolarPovMotion.leftGripRail(SatelliteServiceFace.STARBOARD)
        assertTrue(port.x < 0f)
        assertTrue(starboard.x > 0f)
        assertEquals(port.z, starboard.z)
    }

    @Test
    fun sparkOccursBeforePanelDisconnects() {
        assertTrue(ServicerSolarPovMotion.sparkVisible(SolarPovAction.DETACH, .25f))
        assertEquals(0f, ServicerSolarPovMotion.detachment(.25f))
        assertFalse(ServicerSolarPovMotion.sparkVisible(SolarPovAction.DETACH, .34f))
        assertTrue(ServicerSolarPovMotion.detachment(.60f) > 0f)
    }

    @Test
    fun panelIsFullyRemovedAndReseatedInCorrectDirections() {
        val a = RepairHotspot.SOLAR_PANEL_A
        val b = RepairHotspot.SOLAR_PANEL_B
        assertTrue(ServicerSolarPovMotion.wingOffset(SolarPovAction.DETACH, a, 1f).x < 0f)
        assertTrue(ServicerSolarPovMotion.wingOffset(SolarPovAction.DETACH, b, 1f).x > 0f)
        assertEquals(0f, ServicerSolarPovMotion.wingOffset(SolarPovAction.INSTALL, a, 1f).x)
        assertEquals(0f, ServicerSolarPovMotion.wingOffset(SolarPovAction.INSTALL, b, 1f).z)
    }

    @Test
    fun successfulSessionChangesDriveTheAnimationQueue() {
        val wing = RepairHotspot.SOLAR_PANEL_A
        assertEquals(SolarPovAction.DETACH, ServicerSolarPovMotion.fromChange(
            RepairPartCondition.DAMAGED, RepairPartCondition.REMOVED, wing
        )?.action)
        assertEquals(SolarPovAction.INSTALL, ServicerSolarPovMotion.fromChange(
            RepairPartCondition.REMOVED, RepairPartCondition.REPLACED, wing
        )?.action)
        assertEquals(SolarPovAction.REPAIR, ServicerSolarPovMotion.fromChange(
            RepairPartCondition.DAMAGED, RepairPartCondition.REPAIRED, wing
        )?.action)
        assertEquals(null, ServicerSolarPovMotion.fromChange(
            RepairPartCondition.DAMAGED, RepairPartCondition.DAMAGED, wing
        ))
    }
}
