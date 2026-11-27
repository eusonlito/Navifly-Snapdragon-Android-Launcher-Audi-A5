package com.lito.a5launcher.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightsWitnessTest {
    @Test
    fun witnessFollowsTheCarLightsOnly() {
        assertTrue(lightsWitnessActive(true))
        assertFalse(lightsWitnessActive(false))
    }

    @Test
    fun unknownLightsStateKeepsTheWitnessOff() {
        assertFalse(lightsWitnessActive(null))
    }
}
