package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLaunchGateTest {

    @Test
    fun rejectsRepeatedLaunchesUntilFiveSecondsHaveElapsed() {
        val gate = NavigationLaunchGate()

        assertTrue(gate.tryAcquire(1_000L))
        assertFalse(gate.tryAcquire(1_001L))
        assertEquals(1L, gate.remainingMs(5_999L))
        assertTrue(gate.tryAcquire(6_000L))
    }
}
