
package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Verifies spacecraft camera controls,
 * hinged cover geometry, and interactive
 * maintenance-part selection.
 */
class Satellite3DGeometryTest {

    /**
     * Creates an initial robotic maintenance session
     * for the scientific research satellite.
     */
    private fun session() = RoboticRepairSession(
        workOrderId = 1L,
        satelliteId = "SAT-003",
        damage = EquipmentDamage.SOLAR_ARRAY_DAMAGE
    )

    /**
     * Confirms that the satellite origin
     * projects to the viewport center.
     */
    @Test
    fun modelOriginProjectsToCenter() {

        val point = Satellite3DGeometry.project(
            SatellitePoint3D(0f, 0f, 0f),
            SatelliteCamera()
        )

        assertEquals(200f, point.x, 0.001f)
        assertEquals(150f, point.y, 0.001f)
    }

    /**
     * Confirms that camera rotation changes
     * an off-center component's appearance.
     */
    @Test
    fun rotationChangesProjection() {

        val point = SatellitePoint3D(
            0.5f, 0.2f, 0.4f
        )

        val before = Satellite3DGeometry.project(
            point,
            SatelliteCamera(0f, 0f)
        )

        val after = Satellite3DGeometry.project(
            point,
            SatelliteCamera(40f, 15f)
        )

        assertNotEquals(before.x, after.x)
    }

    /**
     * Verifies that rotation remains within
     * the intended servicing-view limits.
     */
    @Test
    fun cameraRotationIsClamped() {

        val camera = SatelliteCamera().rotated(
            deltaYaw = 300f,
            deltaPitch = -300f
        )

        assertEquals(
            55f,
            camera.yawDegrees,
            0.001f
        )

        assertEquals(
            -30f,
            camera.pitchDegrees,
            0.001f
        )
    }

    /**
     * Verifies that magnification remains
     * within usable viewing limits.
     */
    @Test
    fun zoomIsClamped() {

        val zoomedIn = SatelliteCamera().zoomed(5f)
        val zoomedOut = SatelliteCamera().zoomed(-5f)

        assertEquals(
            1.25f,
            zoomedIn.zoom,
            0.001f
        )

        assertEquals(
            0.70f,
            zoomedOut.zoom,
            0.001f
        )
    }

    /**
     * Confirms that zoom changes the projected
     * position of an off-center component.
     */
    @Test
    fun zoomChangesProjectedDistance() {

        val point = SatellitePoint3D(
            0.5f, 0f, 0.4f
        )

        val normal = Satellite3DGeometry.project(
            point,
            SatelliteCamera(zoom = 1f)
        )

        val enlarged = Satellite3DGeometry.project(
            point,
            SatelliteCamera(zoom = 1.2f)
        )

        assertTrue(
            kotlin.math.abs(enlarged.x - 200f) >
                    kotlin.math.abs(normal.x - 200f)
        )
    }

    /**
     * Verifies that screw selection uses
     * the current rotated camera projection.
     */
    @Test
    fun rotatedScrewsRemainSelectable() {

        val camera = SatelliteCamera(
            yawDegrees = 35f,
            pitchDegrees = 10f,
            zoom = 1.1f
        )

        val current = session()

        val screw = RepairHotspot.SCREW_1

        val position = Satellite3DGeometry.project(
            Satellite3DGeometry.hotspotPosition(
                screw,
                current
            ),
            camera
        )

        assertEquals(
            screw,
            Satellite3DGeometry.hitTest(
                position.x,
                position.y,
                camera,
                current
            )
        )
    }

    /**
     * Confirms that the hinged panel begins
     * at its original closed position.
     */
    @Test
    fun hingeAtZeroKeepsPanelClosed() {

        val original = SatellitePoint3D(
            0f, 0f, 0.41f
        )

        val transformed =
            Satellite3DGeometry.hingedPanelPoint(
                original,
                0f
            )

        assertEquals(
            original.x,
            transformed.x,
            0.001f
        )

        assertEquals(
            original.z,
            transformed.z,
            0.001f
        )
    }

    /**
     * Confirms that the hinge moves the cover
     * outward over the opening animation.
     */
    @Test
    fun hingedPanelMovesOutward() {

        val original = SatellitePoint3D(
            0f, 0f, 0.41f
        )

        val halfOpen =
            Satellite3DGeometry.hingedPanelPoint(
                original,
                0.5f
            )

        val fullyOpen =
            Satellite3DGeometry.hingedPanelPoint(
                original,
                1f
            )

        assertNotEquals(
            original,
            halfOpen
        )

        assertNotEquals(
            original,
            fullyOpen
        )

        assertTrue(
            hypot(
                fullyOpen.x - original.x,
                fullyOpen.z - original.z
            ) > 0.1f
        )
    }

    /**
     * Verifies that a fully opened cover's
     * touch position matches its hinge geometry.
     */
    @Test
    fun openCoverHitTargetMatchesGeometry() {

        val current = session().copy(
            step = RepairProcedureStep.CLOSE_PANEL,
            panelOpen = true
        )

        val camera = SatelliteCamera(
            yawDegrees = 25f,
            pitchDegrees = -15f
        )

        val center = Satellite3DGeometry.project(
            Satellite3DGeometry.hotspotPosition(
                RepairHotspot.ACCESS_PANEL,
                current
            ),
            camera
        )

        assertEquals(
            RepairHotspot.ACCESS_PANEL,
            Satellite3DGeometry.hitTest(
                center.x,
                center.y,
                camera,
                current
            )
        )
    }

    /**
     * Ensures a removed screw cannot be
     * removed a second time.
     */
    @Test
    fun removedScrewIsNotInteractive() {

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

    /**
     * Confirms that the access cover becomes
     * interactive after fastener removal.
     */
    @Test
    fun accessPanelBecomesInteractive() {

        val current = session().copy(
            step = RepairProcedureStep.LIFT_PANEL,
            loosenedScrews = setOf(
                RepairHotspot.SCREW_1,
                RepairHotspot.SCREW_2,
                RepairHotspot.SCREW_3,
                RepairHotspot.SCREW_4
            )
        )

        assertEquals(
            listOf(RepairHotspot.ACCESS_PANEL),
            Satellite3DGeometry.activeHotspots(
                current
            )
        )
    }

    /**
     * Verifies that component removal exposes
     * the replacement mounting socket.
     */
    @Test
    fun removedModuleExposesSocket() {

        val current = session().copy(
            step = RepairProcedureStep.INSTALL_REPLACEMENT,
            panelOpen = true,
            partCondition = RepairPartCondition.REMOVED
        )

        assertEquals(
            listOf(RepairHotspot.COMPONENT_SOCKET),
            Satellite3DGeometry.activeHotspots(
                current
            )
        )
    }

    /**
     * Ensures that final verification targets
     * only the diagnostics connector.
     */
    @Test
    fun verificationUsesDiagnosticsPort() {

        val current = session().copy(
            step = RepairProcedureStep.VERIFY,
            partCondition = RepairPartCondition.REPLACED,
            loosenedScrews = emptySet()
        )

        assertEquals(
            listOf(RepairHotspot.DIAGNOSTIC_PORT),
            Satellite3DGeometry.activeHotspots(
                current
            )
        )
    }

    /**
     * Prevents further component interactions
     * after successful repair verification.
     */
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
