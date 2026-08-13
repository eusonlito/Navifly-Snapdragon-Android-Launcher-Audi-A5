package com.lito.a5launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MapMotionSmootherTest {
    @Test
    fun `interpolates between fixes at delayed render time`() {
        val smoother = MapMotionSmoother(interpolationDelayMs = 250L)
        smoother.add(MapMotionSample(40.0, -3.0, 350f, 10f, 1_000L))
        smoother.add(MapMotionSample(40.0005, -2.9995, 10f, 10f, 2_000L))

        val rendered = requireNotNull(smoother.positionAt(1_750L))

        assertEquals(40.00025, rendered.latitude, 0.000001)
        assertEquals(-2.99975, rendered.longitude, 0.000001)
        assertEquals(0f, rendered.bearing, 0.01f)
    }

    @Test
    fun `predicts briefly from speed and bearing then stops`() {
        val smoother = MapMotionSmoother(
            interpolationDelayMs = 250L,
            predictionHorizonMs = 1_000L,
        )
        smoother.add(MapMotionSample(40.0, -3.0, 0f, 20f, 1_000L))

        val predicted = requireNotNull(smoother.positionAt(2_250L))
        val stopped = requireNotNull(smoother.positionAt(8_000L))

        assertTrue(predicted.latitude > 40.00017)
        assertEquals(predicted.latitude, stopped.latitude, 0.0000001)
        assertEquals(predicted.longitude, stopped.longitude, 0.0000001)
        assertSame(predicted, stopped)
    }

    @Test
    fun `resets instead of animating an implausible jump`() {
        val smoother = MapMotionSmoother(interpolationDelayMs = 250L)
        smoother.add(MapMotionSample(40.0, -3.0, 0f, 10f, 1_000L))
        smoother.add(MapMotionSample(43.0, -8.0, 0f, 10f, 2_000L))

        val rendered = requireNotNull(smoother.positionAt(2_100L))

        assertEquals(43.0, rendered.latitude, 0.0)
        assertEquals(-8.0, rendered.longitude, 0.0)
    }

    @Test
    fun `does not predict movement below walking speed`() {
        val smoother = MapMotionSmoother(interpolationDelayMs = 250L)
        smoother.add(MapMotionSample(40.0, -3.0, 90f, .4f, 1_000L))

        val rendered = requireNotNull(smoother.positionAt(2_000L))

        assertEquals(40.0, rendered.latitude, 0.0)
        assertEquals(-3.0, rendered.longitude, 0.0)
    }

    @Test
    fun `new fix during a turn preserves the rendered camera position`() {
        val smoother = MapMotionSmoother(
            interpolationDelayMs = 250L,
            predictionHorizonMs = 1_000L,
        )
        val tenMetresLatitude = Math.toDegrees(10.0 / 6_371_000.0)
        val latitude = 43.0
        val longitude = -8.0
        val northFix = MapMotionSample(
            latitude = latitude + tenMetresLatitude,
            longitude = longitude,
            bearing = 0f,
            speedMps = 10f,
            elapsedRealtimeMs = 2_000L,
        )
        smoother.add(MapMotionSample(latitude, longitude, 0f, 10f, 1_000L))
        smoother.add(northFix)

        val beforeTurn = requireNotNull(smoother.positionAt(3_000L))
        val tenMetresLongitude = Math.toDegrees(
            10.0 / (6_371_000.0 * kotlin.math.cos(Math.toRadians(northFix.latitude)))
        )
        smoother.add(
            MapMotionSample(
                latitude = northFix.latitude,
                longitude = longitude + tenMetresLongitude,
                bearing = 90f,
                speedMps = 10f,
                elapsedRealtimeMs = 3_000L,
            )
        )

        val afterTurn = requireNotNull(smoother.positionAt(3_000L))

        assertEquals(beforeTurn.latitude, afterTurn.latitude, 0.000001)
        assertEquals(beforeTurn.longitude, afterTurn.longitude, 0.000001)
        assertEquals(beforeTurn.bearing, afterTurn.bearing, 0.1f)
    }

    @Test
    fun `camera threshold accumulates sub-pixel movement`() {
        val previous = MapMotionSample(43.0, -8.0, 0f, 20f, 1_000L)
        val subPixel = previous.copy(latitude = 43.000001)
        val visible = previous.copy(latitude = 43.00001)

        assertEquals(false, subPixel.differsVisuallyFrom(previous, zoom = 16.0))
        assertEquals(true, visible.differsVisuallyFrom(previous, zoom = 16.0))
    }

    @Test
    fun `camera threshold preserves meaningful bearing changes`() {
        val previous = MapMotionSample(43.0, -8.0, 10f, 0f, 1_000L)
        val sameDirection = previous.copy(bearing = 10.1f)
        val changedDirection = previous.copy(bearing = 10.2f)

        assertEquals(false, sameDirection.differsVisuallyFrom(previous, zoom = 16.0))
        assertEquals(true, changedDirection.differsVisuallyFrom(previous, zoom = 16.0))
    }

    @Test
    fun `manual exploration pauses tracked camera updates`() {
        val rendered = MapMotionSample(43.0, -8.0, 10f, 0f, 1_000L)
        val tracking = MapCameraTrackingState()
        tracking.startExploration()

        assertEquals(
            false,
            tracking.shouldUpdate(
                frameDue = true,
                gestureActive = false,
                rendered = rendered,
                previous = null,
                zoom = 16.0,
            ),
        )
    }

    @Test
    fun `recenter forces a camera update while vehicle is stationary`() {
        val stationary = MapMotionSample(43.0, -8.0, 10f, 0f, 1_000L)
        val tracking = MapCameraTrackingState()
        tracking.markUpdated()
        tracking.startExploration()
        tracking.recenter()

        assertEquals(
            true,
            tracking.shouldUpdate(
                frameDue = true,
                gestureActive = false,
                rendered = stationary,
                previous = stationary,
                zoom = 16.0,
            ),
        )
    }

    @Test
    fun `only one-finger movement starts manual exploration`() {
        assertEquals(true, shouldStartMapExploration(pointerCount = 1))
        assertEquals(false, shouldStartMapExploration(pointerCount = 2))
    }
}
