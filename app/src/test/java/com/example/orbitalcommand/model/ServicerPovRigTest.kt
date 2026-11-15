
package com.example.orbitalcommand.model

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.hypot

/**
 * Tests the independent onboard camera's
 * three-dimensional movement around a satellite.
 */
class ServicerPovRigTest {

    private fun distance(
        p: ServicerPovPose
    ): Float = hypot(
        hypot(p.eyeX, p.eyeY),
        p.eyeZ
    )

    @Test
    fun dockingMovesCameraCloser() {

        val far = ServicerPovRig.pose(
            20f, -12f, 0f, 0f
        )

        val close = ServicerPovRig.pose(
            20f, -12f, 1f, 0f
        )

        assertTrue(
            distance(close) < distance(far)
        )
    }

    @Test
    fun facesProduceDistinctViewpoints() {

        val service = ServicerPovRig.pose(
            20f, -12f, 1f, 0f
        )

        val port = ServicerPovRig.pose(
            -90f, 0f, 1f, 0f
        )

        val starboard = ServicerPovRig.pose(
            90f, 0f, 1f, 0f
        )

        val rear = ServicerPovRig.pose(
            180f, 0f, 1f, 0f
        )

        assertTrue(port.eyeX < 0f)

        assertTrue(starboard.eyeX > 0f)

        assertTrue(rear.eyeZ < 0f)

        assertTrue(service.eyeZ > 0f)
    }

    @Test
    fun topAndBottomAppearOnOppositeSides() {

        val top = ServicerPovRig.pose(
            0f, -80f, 1f, 0f
        )

        val bottom = ServicerPovRig.pose(
            0f, 80f, 1f, 0f
        )

        assertTrue(top.eyeY > 0f)

        assertTrue(bottom.eyeY < 0f)
    }

    @Test
    fun transitClearsBodyBeforeArriving() {

        val start = ServicerPovRig.pose(
            0f, 0f, 1f, 0f
        )

        val middle = ServicerPovRig.pose(
            0f, 0f, 1f, .5f
        )

        val finish = ServicerPovRig.pose(
            0f, 0f, 1f, 1f
        )

        assertTrue(
            distance(middle) > distance(start)
        )

        assertEquals(
            distance(start),
            distance(finish),
            .001f
        )
    }
}
