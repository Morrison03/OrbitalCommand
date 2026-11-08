
package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies satellite panel, fastener, component,
 * replacement, and final verification interactions.
 */
class RoboticRepairSimulatorTest {

    /**
     * Creates a queued maintenance work order.
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
     * Selects the appropriate tool and executes
     * one valid satellite-component interaction.
     */
    private fun interact(
        session: RoboticRepairSession,
        spot: RepairHotspot
    ): RoboticRepairSession {

        val tool = RoboticRepairSimulator.requiredTool(
            session.step
        )

        val selected = RoboticRepairSimulator.selectTool(
            session,
            tool
        )

        val result = RoboticRepairSimulator.interact(
            selected,
            spot
        )

        assertTrue(
            result.session.feedback,
            result.succeeded
        )

        return result.session
    }

    /**
     * Returns all four distinct fastener positions.
     */
    private fun screws() = listOf(
        RepairHotspot.SCREW_1,
        RepairHotspot.SCREW_2,
        RepairHotspot.SCREW_3,
        RepairHotspot.SCREW_4
    )

    /**
     * Removes all screws and opens the panel
     * to expose internal equipment.
     */
    private fun exposeComponent(
        session: RoboticRepairSession
    ): RoboticRepairSession {

        var current = session

        for (screw in screws()) {
            current = interact(current, screw)
        }

        assertEquals(
            RepairProcedureStep.LIFT_PANEL,
            current.step
        )

        return interact(
            current,
            RepairHotspot.ACCESS_PANEL
        )
    }

    /**
     * Completes a repair or replacement procedure,
     * including reassembly and final diagnostics.
     */
    private fun finishSession(
        damage: EquipmentDamage,
        method: RepairServiceMethod
    ): RoboticRepairSession {

        var current = exposeComponent(
            RoboticRepairSimulator.start(order(damage))
        )

        current = interact(
            current,
            RepairHotspot.COMPONENT
        )

        val choice = RoboticRepairSimulator.chooseMethod(
            current,
            method
        )

        assertTrue(choice.succeeded)

        current = choice.session

        if (
            method == RepairServiceMethod.REPLACE_COMPONENT
        ) {

            current = interact(
                current,
                RepairHotspot.COMPONENT
            )

            assertEquals(
                RepairPartCondition.REMOVED,
                current.partCondition
            )

            current = interact(
                current,
                RepairHotspot.COMPONENT_SOCKET
            )

        } else {

            current = interact(
                current,
                RepairHotspot.COMPONENT
            )
        }

        current = interact(
            current,
            RepairHotspot.ACCESS_PANEL
        )

        for (screw in screws()) {
            current = interact(current, screw)
        }

        assertEquals(
            RepairProcedureStep.VERIFY,
            current.step
        )

        return interact(
            current,
            RepairHotspot.DIAGNOSTIC_PORT
        )
    }

    /**
     * Verifies that servicing starts with
     * damaged equipment and installed screws.
     */
    @Test
    fun repairStartsWithInstalledFasteners() {

        val session = RoboticRepairSimulator.start(
            order()
        )

        assertEquals(
            RepairProcedureStep.UNSCREW,
            session.step
        )

        assertEquals(
            RepairPartCondition.DAMAGED,
            session.partCondition
        )

        assertFalse(session.panelOpen)

        assertEquals(
            4,
            session.fastenersRemaining
        )
    }

    /**
     * Prevents servicing an unqueued repair.
     */
    @Test(
        expected = IllegalArgumentException::class
    )
    fun openOrderCannotStart() {

        RoboticRepairSimulator.start(
            order().copy(
                status = RepairStatus.OPEN
            )
        )
    }

    /**
     * Prevents removing screws without
     * the correct tool.
     */
    @Test
    fun wrongToolDoesNotRemoveScrews() {

        val session = RoboticRepairSimulator.start(
            order()
        )

        val failed = RoboticRepairSimulator.interact(
            session,
            RepairHotspot.SCREW_1
        )

        assertFalse(failed.succeeded)

        assertEquals(
            0,
            failed.session.loosenedScrews.size
        )
    }

    /**
     * Prevents the same screw from being
     * counted as removed twice.
     */
    @Test
    fun fastenerMustBeUnique() {

        val session = interact(
            RoboticRepairSimulator.start(order()),
            RepairHotspot.SCREW_1
        )

        val duplicate = RoboticRepairSimulator.interact(
            session,
            RepairHotspot.SCREW_1
        )

        assertFalse(duplicate.succeeded)

        assertEquals(
            1,
            duplicate.session.loosenedScrews.size
        )
    }

    /**
     * Prevents lifting the cover before
     * all fasteners have been removed.
     */
    @Test
    fun panelRequiresFourRemovedFasteners() {

        val session = RoboticRepairSimulator.selectTool(
            RoboticRepairSimulator.start(order()),
            RepairTool.GRIPPER
        )

        val failed = RoboticRepairSimulator.interact(
            session,
            RepairHotspot.ACCESS_PANEL
        )

        assertFalse(failed.succeeded)
        assertFalse(failed.session.panelOpen)
    }

    /**
     * Confirms that lifting the panel exposes
     * the component for inspection.
     */
    @Test
    fun panelOpensToExamination() {

        val session = exposeComponent(
            RoboticRepairSimulator.start(order())
        )

        assertTrue(session.panelOpen)

        assertEquals(
            RepairProcedureStep.EXAMINE,
            session.step
        )
    }

    /**
     * Requires component examination before
     * selecting a servicing method.
     */
    @Test
    fun cannotSelectMethodBeforeInspection() {

        val session = exposeComponent(
            RoboticRepairSimulator.start(order())
        )

        val result = RoboticRepairSimulator.chooseMethod(
            session,
            RepairServiceMethod.REPLACE_COMPONENT
        )

        assertFalse(result.succeeded)

        assertEquals(
            RepairProcedureStep.EXAMINE,
            result.session.step
        )
    }

    /**
     * Requires component removal before
     * replacement installation.
     */
    @Test
    fun replacementRequiresRemovalAndInstallation() {

        var session = exposeComponent(
            RoboticRepairSimulator.start(order())
        )

        session = interact(
            session,
            RepairHotspot.COMPONENT
        )

        session = RoboticRepairSimulator.chooseMethod(
            session,
            RepairServiceMethod.REPLACE_COMPONENT
        ).session

        val before = RoboticRepairSimulator.selectTool(
            session,
            RepairTool.INSTALLER
        )

        val invalid = RoboticRepairSimulator.interact(
            before,
            RepairHotspot.COMPONENT_SOCKET
        )

        assertFalse(invalid.succeeded)

        assertEquals(
            RepairPartCondition.DAMAGED,
            invalid.session.partCondition
        )

        session = interact(
            session,
            RepairHotspot.COMPONENT
        )

        assertEquals(
            RepairPartCondition.REMOVED,
            session.partCondition
        )

        session = interact(
            session,
            RepairHotspot.COMPONENT_SOCKET
        )

        assertEquals(
            RepairPartCondition.REPLACED,
            session.partCondition
        )
    }

    /**
     * Verifies that an in-place repair
     * preserves the existing component.
     */
    @Test
    fun inPlaceRepairKeepsPartInstalled() {

        var session = exposeComponent(
            RoboticRepairSimulator.start(order())
        )

        session = interact(
            session,
            RepairHotspot.COMPONENT
        )

        session = RoboticRepairSimulator.chooseMethod(
            session,
            RepairServiceMethod.REPAIR_IN_PLACE
        ).session

        session = interact(
            session,
            RepairHotspot.COMPONENT
        )

        assertEquals(
            RepairPartCondition.REPAIRED,
            session.partCondition
        )

        assertEquals(
            RepairProcedureStep.CLOSE_PANEL,
            session.step
        )
    }

    /**
     * Blocks final verification before the
     * access panel has been reassembled.
     */
    @Test
    fun cannotVerifyBeforeClosingPanel() {

        var session = exposeComponent(
            RoboticRepairSimulator.start(order())
        )

        session = interact(
            session,
            RepairHotspot.COMPONENT
        )

        session = RoboticRepairSimulator.chooseMethod(
            session,
            RepairServiceMethod.REPAIR_IN_PLACE
        ).session

        session = interact(
            session,
            RepairHotspot.COMPONENT
        )

        val skipped = RoboticRepairSimulator.interact(
            RoboticRepairSimulator.selectTool(
                session,
                RepairTool.SCANNER
            ),
            RepairHotspot.DIAGNOSTIC_PORT
        )

        assertFalse(skipped.succeeded)

        assertEquals(
            RepairProcedureStep.CLOSE_PANEL,
            skipped.session.step
        )
    }

    /**
     * Verifies both service methods across
     * every supported equipment failure.
     */
    @Test
    fun allDamageTypesSupportBothServiceMethods() {

        for (
        damage in EquipmentDamage.entries.filter {
            it != EquipmentDamage.NONE
        }
        ) {

            for (method in RepairServiceMethod.entries) {

                val result = finishSession(
                    damage,
                    method
                )

                assertTrue(result.isComplete)
                assertFalse(result.panelOpen)

                assertTrue(
                    result.loosenedScrews.isEmpty()
                )

                assertEquals(
                    if (
                        method ==
                        RepairServiceMethod.REPLACE_COMPONENT
                    ) {
                        RepairPartCondition.REPLACED
                    } else {
                        RepairPartCondition.REPAIRED
                    },
                    result.partCondition
                )
            }
        }
    }

    /**
     * Prevents a completed procedure
     * from executing again.
     */
    @Test
    fun completedProcedureCannotRunTwice() {

        val session = finishSession(
            EquipmentDamage.SOLAR_ARRAY_DAMAGE,
            RepairServiceMethod.REPAIR_IN_PLACE
        )

        val result = RoboticRepairSimulator.interact(
            session,
            RepairHotspot.DIAGNOSTIC_PORT
        )

        assertFalse(result.succeeded)
        assertTrue(result.session.isComplete)
    }

    /**
     * Verifies that touch detection matches the
     * screws, cover, component, and diagnostic port.
     */
    @Test
    fun geometryMatchesInteractiveParts() {

        val session = RoboticRepairSimulator.start(
            order()
        )

        for (
        (screw, center) in
        SatelliteRepairGeometry.screwLocations
        ) {

            assertEquals(
                screw,
                SatelliteRepairGeometry.hitTest(
                    center.first,
                    center.second,
                    session
                )
            )
        }

        assertEquals(
            RepairHotspot.ACCESS_PANEL,
            SatelliteRepairGeometry.hitTest(
                200f,
                160f,
                session
            )
        )

        val open = session.copy(
            panelOpen = true
        )

        assertEquals(
            RepairHotspot.COMPONENT,
            SatelliteRepairGeometry.hitTest(
                200f,
                163f,
                open
            )
        )

        assertEquals(
            RepairHotspot.ACCESS_PANEL,
            SatelliteRepairGeometry.hitTest(
                315f,
                160f,
                open
            )
        )

        assertEquals(
            RepairHotspot.COMPONENT_SOCKET,
            SatelliteRepairGeometry.hitTest(
                200f,
                163f,
                open.copy(
                    partCondition =
                        RepairPartCondition.REMOVED
                )
            )
        )

        assertEquals(
            RepairHotspot.DIAGNOSTIC_PORT,
            SatelliteRepairGeometry.hitTest(
                343f,
                43f,
                session
            )
        )
    }
}
