
package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks camera navigation and
 * procedural repair hotspot alignment.
 */
class Satellite3DGeometryTest {

    private fun session() = RoboticRepairSession(
        workOrderId = 1L,
        satelliteId = "SAT-003",
        damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE
    )

    @Test
    fun centerProjectsToViewportCenter() {

        val p = Satellite3DGeometry.project(
            SatellitePoint3D(0f, 0f, 0f),
            SatelliteCamera()
        )

        assertEquals(200f, p.x, 0.001f)
        assertEquals(150f, p.y, 0.001f)
    }

    @Test
    fun dragCanReachFullRearAndTopViews() {

        val camera = SatelliteCamera().rotated(
            300f,
            -300f
        )

        assertEquals(
            180f,
            camera.yawDegrees,
            0.001f
        )

        assertEquals(
            -84f,
            camera.pitchDegrees,
            0.001f
        )
    }

    @Test
    fun zoomRemainsBounded() {

        assertEquals(
            1.25f,
            SatelliteCamera().zoomed(5f).zoom,
            0.001f
        )

        assertEquals(
            0.70f,
            SatelliteCamera().zoomed(-5f).zoom,
            0.001f
        )
    }

    @Test
    fun cameraRotationChangesOffCenterProjection() {

        val point = SatellitePoint3D(
            0.5f,
            0.2f,
            0.4f
        )

        val a = Satellite3DGeometry.project(
            point,
            SatelliteCamera(0f, 0f)
        )

        val b = Satellite3DGeometry.project(
            point,
            SatelliteCamera(90f, 0f)
        )

        assertNotEquals(a.x, b.x)
    }

    @Test
    fun rotatedScrewRemainsSelectable() {

        val camera = SatelliteCamera(
            yawDegrees = 35f,
            pitchDegrees = 10f
        )

        val initial = session()

        val projected =
            Satellite3DGeometry.project(
                Satellite3DGeometry.hotspotPosition(
                    RepairHotspot.SCREW_1,
                    initial
                ),
                camera
            )

        assertEquals(
            RepairHotspot.SCREW_1,
            Satellite3DGeometry.hitTest(
                projected.x,
                projected.y,
                camera,
                initial
            )
        )
    }

    @Test
    fun hingeMovesOpenPanel() {

        val origin = SatellitePoint3D(
            0f,
            0f,
            0.41f
        )

        assertEquals(
            origin,
            Satellite3DGeometry.hingedPanelPoint(
                origin,
                0f
            )
        )

        assertNotEquals(
            origin,
            Satellite3DGeometry.hingedPanelPoint(
                origin,
                1f
            )
        )
    }

    @Test
    fun openPanelTouchFollowsItsHinge() {

        val current = session().copy(
            panelOpen = true,
            step = RepairProcedureStep.CLOSE_PANEL
        )

        val camera = SatelliteCamera()

        val point = Satellite3DGeometry.project(
            Satellite3DGeometry.hotspotPosition(
                RepairHotspot.ACCESS_PANEL,
                current
            ),
            camera
        )

        assertEquals(
            RepairHotspot.ACCESS_PANEL,
            Satellite3DGeometry.hitTest(
                point.x,
                point.y,
                camera,
                current
            )
        )
    }

    @Test
    fun removedFastenerCannotBeSelectedForRemoval() {

        val current = session().copy(
            loosenedScrews = setOf(
                RepairHotspot.SCREW_1
            )
        )

        assertFalse(
            RepairHotspot.SCREW_1 in
                    Satellite3DGeometry.activeHotspots(
                        current
                    )
        )
    }

    @Test
    fun verificationUsesOnlyDiagnosticPort() {

        val current = session().copy(
            step = RepairProcedureStep.VERIFY
        )

        assertEquals(
            listOf(RepairHotspot.DIAGNOSTIC_PORT),
            Satellite3DGeometry.activeHotspots(
                current
            )
        )
    }

    @Test
    fun completedRepairHasNoActiveTargets() {

        val current = session().copy(
            step = RepairProcedureStep.COMPLETE
        )

        assertTrue(
            Satellite3DGeometry.activeHotspots(
                current
            ).isEmpty()
        )
    }
}
