
package com.example.orbitalcommand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for synchronizing procedural
 * screw interactions with onboard POV animations.
 */
class ServicerScrewMotionTest {

    @Test
    fun selectingLowerLeftFastenerTargetsScrewOne() {

        val operations = ServicerScrewMotion.detect(
            before = emptySet(),
            after = setOf(
                RepairHotspot.SCREW_1
            ),
            currentStep =
                RepairProcedureStep.UNSCREW
        )

        assertEquals(
            listOf(
                ServicerScrewOperation(
                    RepairHotspot.SCREW_1,
                    ServicerScrewDirection.REMOVE
                )
            ),
            operations
        )

        assertTrue(
            ServicerScrewMotion.screwPosition(
                RepairHotspot.SCREW_1
            ).y < 0f
        )
    }

    @Test
    fun fourthRemovalStillAnimatesAfterStepAdvances() {

        val previous = setOf(
            RepairHotspot.SCREW_1,
            RepairHotspot.SCREW_2,
            RepairHotspot.SCREW_3
        )

        val next = previous +
                RepairHotspot.SCREW_4

        assertEquals(
            listOf(
                ServicerScrewOperation(
                    RepairHotspot.SCREW_4,
                    ServicerScrewDirection.REMOVE
                )
            ),
            ServicerScrewMotion.detect(
                previous,
                next,
                RepairProcedureStep.LIFT_PANEL
            )
        )
    }

    @Test
    fun securingFastenerReversesDriverAndScrew() {

        val operation = ServicerScrewMotion.detect(
            before = setOf(
                RepairHotspot.SCREW_1
            ),
            after = emptySet(),
            currentStep =
                RepairProcedureStep.SECURE_SCREWS
        ).single()

        assertEquals(
            ServicerScrewDirection.INSTALL,
            operation.direction
        )

        assertEquals(
            1f,
            ServicerScrewMotion.screwBackout(
                operation,
                0f
            ),
            0.001f
        )

        assertEquals(
            0f,
            ServicerScrewMotion.screwBackout(
                operation,
                1f
            ),
            0.001f
        )

        assertTrue(
            ServicerScrewMotion.screwdriverDegrees(
                operation,
                1f
            ) > 0f
        )
    }

    @Test
    fun restartingRepairDoesNotQueueInstallations() {

        assertTrue(
            ServicerScrewMotion.detect(
                before = ServicerScrewMotion
                    .screws
                    .toSet(),
                after = emptySet(),
                currentStep =
                    RepairProcedureStep.UNSCREW
            ).isEmpty()
        )
    }

    @Test
    fun armReachesHoldsAndRetracts() {

        assertEquals(
            0f,
            ServicerScrewMotion.reach(0f),
            0.001f
        )

        assertEquals(
            1f,
            ServicerScrewMotion.reach(0.5f),
            0.001f
        )

        assertEquals(
            0f,
            ServicerScrewMotion.reach(1f),
            0.001f
        )
    }
}
