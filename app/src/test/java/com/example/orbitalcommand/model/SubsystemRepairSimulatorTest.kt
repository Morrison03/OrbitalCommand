package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end state-machine regression tests for subsystem-specific repairs. */
class SubsystemRepairSimulatorTest {

    private fun start(damage: EquipmentDamage, id: Long = 10L) = RoboticRepairSimulator.start(
        RepairWorkOrder(id = id, satelliteId = "SAT-001", damage = damage,
            createdAtSecond = 0, status = RepairStatus.QUEUED)
    )

    private fun act(session: RoboticRepairSession, tool: RepairTool, target: RepairHotspot) =
        RoboticRepairSimulator.interact(RoboticRepairSimulator.selectTool(session, tool), target)

    @Test
    fun solarScanRequiresBothWingsAndDiagnosesOnlyOne() {
        val initial = start(EquipmentDamage.SOLAR_ARRAY_DAMAGE)
        assertEquals(RepairProcedureStep.SCAN_SOLAR_PANELS, initial.step)
        val first = act(initial, RepairTool.SCANNER, RepairHotspot.SOLAR_PANEL_A)
        assertTrue(first.succeeded)
        assertEquals(RepairProcedureStep.SCAN_SOLAR_PANELS, first.session.step)
        val duplicate = act(first.session, RepairTool.SCANNER, RepairHotspot.SOLAR_PANEL_A)
        assertFalse(duplicate.succeeded)
        val second = act(first.session, RepairTool.SCANNER, RepairHotspot.SOLAR_PANEL_B)
        assertTrue(second.succeeded)
        assertEquals(RepairProcedureStep.CHOOSE_METHOD, second.session.step)
        assertEquals(2, second.session.scannedSolarPanels.size)
        assertTrue(initial.faultySolarPanel in second.session.scannedSolarPanels)
    }

    @Test
    fun replacingSolarWingUsesCorrectEmptyMountAndVerifies() {
        var s = start(EquipmentDamage.SOLAR_ARRAY_DAMAGE, 11L)
        for (panel in listOf(RepairHotspot.SOLAR_PANEL_A, RepairHotspot.SOLAR_PANEL_B)) {
            s = act(s, RepairTool.SCANNER, panel).session
        }
        s = RoboticRepairSimulator.chooseMethod(s, RepairServiceMethod.REPLACE_COMPONENT).session
        assertEquals(RepairProcedureStep.REMOVE_PART, s.step)
        val wrong = if (s.target == RepairHotspot.SOLAR_PANEL_A) RepairHotspot.SOLAR_PANEL_B
            else RepairHotspot.SOLAR_PANEL_A
        assertFalse(act(s, RepairTool.EXTRACTOR, wrong).succeeded)
        s = act(s, RepairTool.EXTRACTOR, s.target).session
        assertEquals(RepairPartCondition.REMOVED, s.partCondition)
        assertFalse(act(s, RepairTool.INSTALLER, RepairHotspot.PAYLOAD_SOCKET).succeeded)
        s = act(s, RepairTool.INSTALLER, s.replacementSocket).session
        assertEquals(RepairPartCondition.REPLACED, s.partCondition)
        assertEquals(RepairProcedureStep.VERIFY, s.step)
        val complete = act(s, RepairTool.SCANNER, RepairHotspot.DIAGNOSTIC_PORT)
        assertTrue(complete.completed)
        assertTrue(complete.session.isComplete)
    }

    @Test
    fun dishCanBeRepairedWithoutOpeningServiceBay() {
        var s = start(EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE)
        assertEquals(RepairProcedureStep.SCAN_DISH, s.step)
        assertFalse(act(s, RepairTool.SCANNER, RepairHotspot.PAYLOAD_SENSOR).succeeded)
        s = act(s, RepairTool.SCANNER, RepairHotspot.COMMUNICATIONS_DISH).session
        s = RoboticRepairSimulator.chooseMethod(s, RepairServiceMethod.REPAIR_IN_PLACE).session
        s = act(s, RepairTool.REPAIR_KIT, RepairHotspot.COMMUNICATIONS_DISH).session
        assertEquals(RepairProcedureStep.VERIFY, s.step)
        assertFalse(s.panelOpen)
        assertTrue(act(s, RepairTool.SCANNER, RepairHotspot.DIAGNOSTIC_PORT).completed)
    }

    @Test
    fun payloadAndThermalHaveSeparateProtectedServiceBayTargets() {
        for (damage in listOf(EquipmentDamage.PAYLOAD_SENSOR_DAMAGE,
            EquipmentDamage.THERMAL_RADIATOR_DAMAGE)) {
            var s = start(damage)
            for (screw in listOf(RepairHotspot.SCREW_1, RepairHotspot.SCREW_2,
                RepairHotspot.SCREW_3, RepairHotspot.SCREW_4)) {
                s = act(s, RepairTool.SCREWDRIVER, screw).session
            }
            assertEquals(RepairProcedureStep.LIFT_PANEL, s.step)
            s = act(s, RepairTool.GRIPPER, RepairHotspot.ACCESS_PANEL).session
            assertTrue(s.panelOpen)
            val other = if (damage == EquipmentDamage.PAYLOAD_SENSOR_DAMAGE)
                RepairHotspot.THERMAL_CONTROLLER else RepairHotspot.PAYLOAD_SENSOR
            assertFalse(act(s, RepairTool.SCANNER, other).succeeded)
            s = act(s, RepairTool.SCANNER, s.target).session
            s = RoboticRepairSimulator.chooseMethod(s, RepairServiceMethod.REPAIR_IN_PLACE).session
            s = act(s, RepairTool.REPAIR_KIT, s.target).session
            assertEquals(RepairProcedureStep.CLOSE_PANEL, s.step)
            s = act(s, RepairTool.GRIPPER, RepairHotspot.ACCESS_PANEL).session
            assertEquals(RepairProcedureStep.SECURE_SCREWS, s.step)
            for (screw in listOf(RepairHotspot.SCREW_1, RepairHotspot.SCREW_2,
                RepairHotspot.SCREW_3, RepairHotspot.SCREW_4)) {
                s = act(s, RepairTool.SCREWDRIVER, screw).session
            }
            assertEquals(RepairProcedureStep.VERIFY, s.step)
            assertTrue(act(s, RepairTool.SCANNER, RepairHotspot.DIAGNOSTIC_PORT).completed)
        }
    }

    @Test
    fun facePolicyPreventsCrossFaceCommands() {
        val solar = start(EquipmentDamage.SOLAR_ARRAY_DAMAGE)
        assertTrue(RepairServiceAccessPolicy.canAccess(solar, SatelliteServiceFace.PORT,
            RepairHotspot.SOLAR_PANEL_A))
        assertFalse(RepairServiceAccessPolicy.canAccess(solar, SatelliteServiceFace.PORT,
            RepairHotspot.SOLAR_PANEL_B))
        val dish = start(EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE)
        assertEquals(SatelliteServiceFace.DORSAL,
            RepairServiceAccessPolicy.requiredFace(dish, RepairHotspot.COMMUNICATIONS_DISH))
        val payload = start(EquipmentDamage.PAYLOAD_SENSOR_DAMAGE)
        assertEquals(SatelliteServiceFace.SERVICE,
            RepairServiceAccessPolicy.requiredFace(payload, RepairHotspot.PAYLOAD_SENSOR))
    }

    @Test
    fun newTargetsProjectToDifferentPositions() {
        val s = start(EquipmentDamage.PAYLOAD_SENSOR_DAMAGE)
        val a = Satellite3DGeometry.hotspotPosition(RepairHotspot.PAYLOAD_SENSOR, s)
        val b = Satellite3DGeometry.hotspotPosition(RepairHotspot.THERMAL_CONTROLLER, s)
        assertTrue(a.x < b.x)
        val solarA = Satellite3DGeometry.hotspotPosition(RepairHotspot.SOLAR_PANEL_A, s)
        val solarB = Satellite3DGeometry.hotspotPosition(RepairHotspot.SOLAR_PANEL_B, s)
        assertTrue(solarA.x < solarB.x)
    }
}
