package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Regression tests for physical rail grip and non-solar part manipulation. */
class ServicerComponentPovMotionTest {
    private fun session(damage: EquipmentDamage): RoboticRepairSession = RoboticRepairSession(
        workOrderId = 42L,
        satelliteId = "SAT-001",
        damage = damage,
        step = RepairProcedureStep.UNSCREW
    )

    @Test fun sideRailsAreAnchoredInFrontFacePlane() {
        val port = ServicerRailContact.anchor(SatelliteServiceFace.PORT)
        val starboard = ServicerRailContact.anchor(SatelliteServiceFace.STARBOARD)
        assertEquals(-.74f, port.x)
        assertEquals(.74f, starboard.x)
        assertEquals(port.z, starboard.z)
        assertTrue(ServicerRailContact.liesOnRail(port))
        assertTrue(ServicerRailContact.liesOnRail(starboard))
        assertTrue(ServicerRailContact.liesOnRail(
            ServicerRailContact.anchor(SatelliteServiceFace.DORSAL)))
        assertFalse(ServicerRailContact.liesOnRail(SatellitePoint3D(.74f,0f,1.2f)))
    }

    @Test fun openingAndClosingPanelQueueDistinctActions() {
        val closed = session(EquipmentDamage.PAYLOAD_SENSOR_DAMAGE)
        val opened = closed.copy(panelOpen = true, step = RepairProcedureStep.EXAMINE)
        assertEquals(ComponentPovAction.OPEN_COVER,
            ServicerComponentPovMotion.changes(closed, opened).single().action)
        val closedAgain = opened.copy(panelOpen = false, step = RepairProcedureStep.SECURE_SCREWS)
        assertEquals(ComponentPovAction.CLOSE_COVER,
            ServicerComponentPovMotion.changes(opened, closedAgain).single().action)
        val operation = ComponentPovOperation(RepairHotspot.ACCESS_PANEL, ComponentPovAction.OPEN_COVER)
        assertEquals(0f, ServicerComponentPovMotion.coverAngle(operation, 0f, true))
        assertEquals(112f, ServicerComponentPovMotion.coverAngle(operation, 1f, true))
    }

    @Test fun dishDetachesAndReinstallsAtOriginalMount() {
        val damaged = session(EquipmentDamage.COMMUNICATIONS_DISH_DAMAGE)
        val removed = damaged.copy(partCondition = RepairPartCondition.REMOVED,
            step = RepairProcedureStep.INSTALL_REPLACEMENT)
        val replaced = removed.copy(partCondition = RepairPartCondition.REPLACED,
            step = RepairProcedureStep.VERIFY)
        assertEquals(ComponentPovAction.DETACH,
            ServicerComponentPovMotion.changes(damaged, removed).single().action)
        assertEquals(ComponentPovAction.INSTALL,
            ServicerComponentPovMotion.changes(removed, replaced).single().action)
        val install = ComponentPovOperation(RepairHotspot.COMMUNICATIONS_DISH,
            ComponentPovAction.INSTALL)
        assertTrue(ServicerComponentPovMotion.partOffset(install, 0f).y > 0f)
        assertEquals(0f, ServicerComponentPovMotion.partOffset(install, 1f).y)
    }

    @Test fun payloadAndThermalTargetDifferentLocations() {
        val payload = ServicerComponentPovMotion.targetPoint(RepairHotspot.PAYLOAD_SENSOR)
        val thermal = ServicerComponentPovMotion.targetPoint(RepairHotspot.THERMAL_CONTROLLER)
        assertTrue(payload.x < 0f)
        assertTrue(thermal.x > 0f)
        for (damage in listOf(EquipmentDamage.PAYLOAD_SENSOR_DAMAGE,
            EquipmentDamage.THERMAL_RADIATOR_DAMAGE)) {
            val damaged = session(damage)
            val removed = damaged.copy(partCondition = RepairPartCondition.REMOVED)
            val replaced = removed.copy(partCondition = RepairPartCondition.REPLACED)
            assertEquals(ComponentPovAction.DETACH,
                ServicerComponentPovMotion.changes(damaged, removed).single().action)
            assertEquals(ComponentPovAction.INSTALL,
                ServicerComponentPovMotion.changes(removed, replaced).single().action)
        }
    }

    @Test fun repairInPlaceAndScanTriggerArmMotion() {
        val damaged = session(EquipmentDamage.PAYLOAD_SENSOR_DAMAGE)
        val fixed = damaged.copy(partCondition = RepairPartCondition.REPAIRED)
        val op = ServicerComponentPovMotion.changes(damaged, fixed).single()
        assertEquals(ComponentPovAction.REPAIR, op.action)
        assertTrue(ServicerComponentPovMotion.sparkVisible(op, .43f))
        assertFalse(ServicerComponentPovMotion.sparkVisible(op, .8f))
        assertEquals(0f, ServicerComponentPovMotion.armReach(0f))
        assertEquals(1f, ServicerComponentPovMotion.armReach(.4f))
        assertEquals(0f, ServicerComponentPovMotion.armReach(1f))
        assertEquals(ComponentPovAction.SCAN,
            ServicerComponentPovMotion.changes(damaged,
                damaged.copy(inspectedInternalModule = true)).single().action)
    }

    @Test fun restartAndUnrelatedToolSelectionCannotTriggerFalseManipulation() {
        val inProgress = session(EquipmentDamage.PAYLOAD_SENSOR_DAMAGE).copy(
            panelOpen = true, partCondition = RepairPartCondition.REPAIRED,
            step = RepairProcedureStep.CLOSE_PANEL)
        val restarted = session(EquipmentDamage.PAYLOAD_SENSOR_DAMAGE)
        assertTrue(ServicerComponentPovMotion.changes(inProgress, restarted).isEmpty())
        assertTrue(ServicerComponentPovMotion.changes(restarted,
            restarted.copy(tool = RepairTool.SCANNER)).isEmpty())
    }
}
