package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Regression coverage for completing maintenance across multiple trips. */
class RepairProgressRetentionTest {

    private fun order(
        id: Long = 42L,
        satelliteId: String = "SAT-001",
        damage: EquipmentDamage = EquipmentDamage.SOLAR_ARRAY_DAMAGE,
        status: RepairStatus = RepairStatus.QUEUED
    ) = RepairWorkOrder(
        id = id,
        satelliteId = satelliteId,
        damage = damage,
        createdAtSecond = 0,
        status = status
    )

    private fun verifying(
        order: RepairWorkOrder = order()
    ) = RoboticRepairSession(
        workOrderId = order.id,
        satelliteId = order.satelliteId,
        damage = order.damage,
        step = RepairProcedureStep.VERIFY,
        tool = RepairTool.SCANNER,
        partCondition = RepairPartCondition.REPLACED,
        feedback = "Hardware installed. Run final diagnostics."
    )

    @Test
    fun leavingBeforeVerifyKeepsAllRepairProgress() {
        val pending = verifying()
        val retained = RepairProgressRetention.keepUnfinished(
            pending, listOf(order())
        )
        assertSame(pending, retained)
        assertEquals(RepairProcedureStep.VERIFY, retained?.step)
        assertEquals(RepairPartCondition.REPLACED, retained?.partCondition)
        assertEquals(RepairTool.SCANNER, retained?.tool)
    }

    @Test
    fun preparingSameMissionResumesRatherThanStartsAgain() {
        val pending = verifying()
        val restored = RepairProgressRetention.forOrder(pending, order())
        assertSame(pending, restored)
        assertEquals(RepairProcedureStep.VERIFY, restored?.step)
    }

    @Test
    fun retainedInternalPanelWorkPreservesFastenersAndCover() {
        val internalOrder = order(damage = EquipmentDamage.PAYLOAD_SENSOR_DAMAGE)
        val pending = verifying(internalOrder).copy(
            step = RepairProcedureStep.SECURE_SCREWS,
            loosenedScrews = setOf(RepairHotspot.SCREW_3),
            panelOpen = false,
            inspectedInternalModule = true
        )
        val restored = RepairProgressRetention.keepUnfinished(
            pending, listOf(internalOrder)
        )
        assertSame(pending, restored)
        assertEquals(setOf(RepairHotspot.SCREW_3), restored?.loosenedScrews)
        assertEquals(RepairProcedureStep.SECURE_SCREWS, restored?.step)
    }

    @Test
    fun missingOrChangedOrderCannotResumeObsoleteSession() {
        val pending = verifying()
        assertNull(RepairProgressRetention.keepUnfinished(pending, emptyList()))
        assertNull(RepairProgressRetention.forOrder(pending, order(id = 99L)))
        assertNull(RepairProgressRetention.forOrder(
            pending, order(damage = EquipmentDamage.PAYLOAD_SENSOR_DAMAGE)
        ))
    }

    @Test
    fun completedWorkOrderClearsOldSession() {
        val pending = verifying()
        assertNull(RepairProgressRetention.keepUnfinished(
            pending, listOf(order(status = RepairStatus.COMPLETED))
        ))
        assertNull(RepairProgressRetention.keepUnfinished(
            pending.copy(step = RepairProcedureStep.COMPLETE), listOf(order())
        ))
    }
}
