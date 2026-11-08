
package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies interactive robotic arm controls,
 * tool requirements, task progression, and
 * simulated maintenance completion.
 */
class RoboticRepairSimulatorTest {

    /**
     * Creates a queued maintenance request.
     */
    private fun order(
        damage: EquipmentDamage =
            EquipmentDamage.SOLAR_ARRAY_DAMAGE
    ): RepairWorkOrder {

        return RepairWorkOrder(
            id = 1L,
            satelliteId = "SAT-003",
            damage = damage,
            createdAtSecond = 5,
            status = RepairStatus.QUEUED,
            queuedAtSecond = 6
        )
    }

    /**
     * Starts an interactive repair procedure.
     */
    private fun session(
        damage: EquipmentDamage =
            EquipmentDamage.SOLAR_ARRAY_DAMAGE
    ): RoboticRepairSession {

        return RoboticRepairSimulator.start(
            order(damage)
        )
    }

    /**
     * Positions the arm and selects the appropriate
     * tool to complete one test task.
     */
    private fun completeStep(
        session: RoboticRepairSession
    ): RepairProcedureResult {

        val aligned = session.copy(
            armPosition = RoboticRepairSimulator.workZone(
                session.step
            )
        )

        val tool = RoboticRepairSimulator.requiredTool(
            step = aligned.step,
            damage = aligned.damage
        )

        return RoboticRepairSimulator.execute(
            RoboticRepairSimulator.selectTool(
                session = aligned,
                tool = tool
            )
        )
    }

    /**
     * Confirms that a new repair starts with
     * inspection and an unselected tool.
     */
    @Test
    fun repairStartsAtInspection() {

        val procedure = session()

        assertEquals(
            RepairProcedureStep.INSPECT,
            procedure.step
        )

        assertEquals(
            RepairTool.NONE,
            procedure.tool
        )

        assertFalse(procedure.isComplete)
    }

    /**
     * Prevents starting maintenance for an
     * unqueued repair request.
     */
    @Test(expected = IllegalArgumentException::class)
    fun openRepairCannotStart() {

        RoboticRepairSimulator.start(
            order().copy(
                status = RepairStatus.OPEN,
                queuedAtSecond = null
            )
        )
    }

    /**
     * Verifies one-step movement in each direction.
     */
    @Test
    fun armMovesWithDirectionalCommands() {

        val initial = session()

        val left = RoboticRepairSimulator.move(
            initial,
            RepairArmDirection.LEFT
        )

        val up = RoboticRepairSimulator.move(
            initial,
            RepairArmDirection.UP
        )

        val right = RoboticRepairSimulator.move(
            initial,
            RepairArmDirection.RIGHT
        )

        val down = RoboticRepairSimulator.move(
            initial,
            RepairArmDirection.DOWN
        )

        assertTrue(
            left.armPosition.x < initial.armPosition.x
        )

        assertTrue(
            up.armPosition.y < initial.armPosition.y
        )

        assertTrue(
            right.armPosition.x > initial.armPosition.x
        )

        assertTrue(
            down.armPosition.y > initial.armPosition.y
        )
    }

    /**
     * Ensures repeated movement cannot move the arm
     * outside its normalized work area.
     */
    @Test
    fun armPositionIsBounded() {

        var procedure = session()

        repeat(30) {
            procedure = RoboticRepairSimulator.move(
                procedure,
                RepairArmDirection.LEFT
            )
        }

        repeat(30) {
            procedure = RoboticRepairSimulator.move(
                procedure,
                RepairArmDirection.UP
            )
        }

        assertEquals(
            0.05f,
            procedure.armPosition.x,
            0.001f
        )

        assertEquals(
            0.05f,
            procedure.armPosition.y,
            0.001f
        )
    }

    /**
     * Prevents completing inspection with
     * the wrong tool selected.
     */
    @Test
    fun wrongToolCannotCompleteTask() {

        val initial = session().copy(
            armPosition =
                RoboticRepairSimulator.workZone(
                    RepairProcedureStep.INSPECT
                )
        )

        val selected = RoboticRepairSimulator.selectTool(
            initial,
            RepairTool.SOLAR_DRIVER
        )

        val result = RoboticRepairSimulator.execute(selected)

        assertFalse(result.succeeded)

        assertEquals(
            RepairProcedureStep.INSPECT,
            result.session.step
        )
    }

    /**
     * Prevents completing inspection when the
     * arm is outside the required service zone.
     */
    @Test
    fun incorrectArmPositionBlocksTask() {

        val initial = session().copy(
            armPosition = RepairArmPosition(0.90f, 0.90f)
        )

        val selected = RoboticRepairSimulator.selectTool(
            initial,
            RepairTool.INSPECTION_SCANNER
        )

        val result = RoboticRepairSimulator.execute(selected)

        assertFalse(result.succeeded)

        assertEquals(
            RepairProcedureStep.INSPECT,
            result.session.step
        )
    }

    /**
     * Verifies successful inspection advances
     * the procedure to subsystem isolation.
     */
    @Test
    fun inspectionAdvancesToIsolation() {

        val result = completeStep(session())

        assertTrue(result.succeeded)

        assertEquals(
            RepairProcedureStep.ISOLATE,
            result.session.step
        )

        assertFalse(result.finished)
    }

    /**
     * Confirms replacement tools differ
     * according to the failed equipment.
     */
    @Test
    fun replacementToolMatchesEquipmentDamage() {

        assertEquals(
            RepairTool.SOLAR_DRIVER,
            RoboticRepairSimulator.requiredTool(
                RepairProcedureStep.REPLACE,
                EquipmentDamage.SOLAR_ARRAY_DAMAGE
            )
        )

        assertEquals(
            RepairTool.THERMAL_WRENCH,
            RoboticRepairSimulator.requiredTool(
                RepairProcedureStep.REPLACE,
                EquipmentDamage.THERMAL_RADIATOR_DAMAGE
            )
        )

        assertEquals(
            RepairTool.SENSOR_DRIVER,
            RoboticRepairSimulator.requiredTool(
                RepairProcedureStep.REPLACE,
                EquipmentDamage.PAYLOAD_SENSOR_DAMAGE
            )
        )
    }

    /**
     * Verifies the complete four-task sequence
     * for each supported equipment failure.
     */
    @Test
    fun allEquipmentTypesCanCompleteRepairProcedure() {

        val damageTypes = listOf(
            EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            EquipmentDamage.THERMAL_RADIATOR_DAMAGE,
            EquipmentDamage.PAYLOAD_SENSOR_DAMAGE
        )

        damageTypes.forEach { damage ->

            var procedure = session(damage)

            repeat(4) {

                val result = completeStep(procedure)

                assertTrue(result.succeeded)

                procedure = result.session
            }

            assertEquals(
                RepairProcedureStep.COMPLETE,
                procedure.step
            )

            assertTrue(procedure.isComplete)
        }
    }

    /**
     * Prevents an already completed procedure
     * from executing additional repair tasks.
     */
    @Test
    fun completedProcedureCannotAdvanceAgain() {

        var procedure = session()

        repeat(4) {
            procedure = completeStep(procedure).session
        }

        val result = RoboticRepairSimulator.execute(procedure)

        assertFalse(result.succeeded)
        assertTrue(result.finished)
        assertTrue(result.session.isComplete)
    }

    /**
     * Confirms that selecting another tool does not
     * erase previously completed maintenance tasks.
     */
    @Test
    fun changingToolsPreservesRepairProgress() {

        val inspected = completeStep(session()).session

        val changed = RoboticRepairSimulator.selectTool(
            session = inspected,
            tool = RepairTool.ISOLATION_CLAMP
        )

        assertEquals(
            RepairProcedureStep.ISOLATE,
            changed.step
        )
    }
}

