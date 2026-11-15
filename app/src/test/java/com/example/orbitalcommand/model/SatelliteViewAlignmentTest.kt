package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for Port/Starboard camera coordination. */
class SatelliteViewAlignmentTest {

    @Test
    fun portShowsNegativeXSurfaceInProceduralView() {
        val camera = SatelliteViewAlignment.operatorCameraFor(SatelliteServiceFace.PORT)
        val port = Satellite3DGeometry.project(SatellitePoint3D(-1f, 0f, 0f), camera)
        val starboard = Satellite3DGeometry.project(SatellitePoint3D(1f, 0f, 0f), camera)
        assertTrue("Port surface must be closer when PORT is selected", port.depth > starboard.depth)
        assertEquals(90f, camera.yawDegrees, 0.001f)
    }

    @Test
    fun starboardShowsPositiveXSurfaceInProceduralView() {
        val camera = SatelliteViewAlignment.operatorCameraFor(SatelliteServiceFace.STARBOARD)
        val port = Satellite3DGeometry.project(SatellitePoint3D(-1f, 0f, 0f), camera)
        val starboard = Satellite3DGeometry.project(SatellitePoint3D(1f, 0f, 0f), camera)
        assertTrue("Starboard surface must be closer when STARBOARD is selected", starboard.depth > port.depth)
        assertEquals(-90f, camera.yawDegrees, 0.001f)
    }

    @Test
    fun serviceAndRearUseSamePhysicalFace() {
        assertEquals(-20f, SatelliteViewAlignment.operatorYawFor(SatelliteServiceFace.SERVICE), 0.001f)
        assertEquals(-180f, SatelliteViewAlignment.operatorYawFor(SatelliteServiceFace.AFT), 0.001f)
    }

    @Test
    fun topAndBottomPitchAreUnaffected() {
        for (face in listOf(SatelliteServiceFace.DORSAL, SatelliteServiceFace.VENTRAL)) {
            assertEquals(face.viewPitch, SatelliteViewAlignment.operatorCameraFor(face).pitchDegrees, 0.001f)
        }
    }
}
