package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against Canvas-to-SceneView vertical-axis mismatches.
 *
 * The procedural operator view uses positive local Y downward on screen;
 * the imported POV geometry uses positive world Y upward.
 */
class SatellitePovOrientationTest {

    private val session = RoboticRepairSimulator.start(
        RepairWorkOrder(
            id = 44L,
            satelliteId = "SAT-001",
            damage = EquipmentDamage.PAYLOAD_SENSOR_DAMAGE,
            createdAtSecond = 0,
            status = RepairStatus.QUEUED
        )
    )

    @Test
    fun dishIsAboveBusInDefaultProceduralView() {
        val dish = Satellite3DGeometry.hotspotPosition(
            RepairHotspot.COMMUNICATIONS_DISH,
            session
        )
        val camera = SatelliteCamera(yawDegrees = 0f, pitchDegrees = 0f)
        val dishOnScreen = Satellite3DGeometry.project(dish, camera)
        val busOnScreen = Satellite3DGeometry.project(
            SatellitePoint3D(0f, 0f, 0f), camera
        )
        assertTrue("Dish must appear above the bus", dishOnScreen.y < busOnScreen.y)
        assertTrue("Dorsal procedural Y must be negative", dish.y < 0f)
    }

    @Test
    fun povScrewsMatchProceduralScreenQuadrants() {
        val camera = SatelliteCamera(yawDegrees = 0f, pitchDegrees = 0f)
        val expected = listOf(
            RepairHotspot.SCREW_1, RepairHotspot.SCREW_2,
            RepairHotspot.SCREW_3, RepairHotspot.SCREW_4
        )
        for (screw in expected) {
            val procedural = Satellite3DGeometry.hotspotPosition(screw, session)
            val pov = ServicerScrewMotion.screwPosition(screw)
            val screen = Satellite3DGeometry.project(procedural, camera)
            // The horizontal orientation is shared between both views.
            assertEquals(procedural.x > 0f, pov.x > 0f)
            // The vertical orientation must be mirrored for SceneView.
            assertEquals(procedural.y > 0f, pov.y < 0f)
            // Confirm each screw's actual on-screen quadrant.
            assertEquals(procedural.y > 0f, screen.y > Satellite3DGeometry.VIEW_HEIGHT / 2f)
        }
        assertTrue(ServicerScrewMotion.screwPosition(RepairHotspot.SCREW_1).y > 0f)
        assertTrue(ServicerScrewMotion.screwPosition(RepairHotspot.SCREW_2).y > 0f)
        assertTrue(ServicerScrewMotion.screwPosition(RepairHotspot.SCREW_3).y < 0f)
        assertTrue(ServicerScrewMotion.screwPosition(RepairHotspot.SCREW_4).y < 0f)
    }
}
