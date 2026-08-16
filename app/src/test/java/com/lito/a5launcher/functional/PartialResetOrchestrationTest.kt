package com.lito.a5launcher.functional

import com.lito.a5launcher.ConfirmedFuelLevelChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PartialResetOrchestrationTest {
    @Test
    fun `confirmed refuel records the captured partial only after applying the reset`() {
        val calls = mutableListOf<String>()
        val decision = ConfirmedFuelLevelChange.Refuel(
            baselineFuelLitres = 20,
            fuelLitres = 25,
            confirmationSamples = 2,
        )

        val update = applyFuelDecisionAndRecordPartialReset(
            confirmedRefuel = decision to 84.6,
            applyFuelDecision = {
                calls += "reset"
                0.0
            },
            partialAfterKm = { it },
            recordPartialReset = { recordedDecision, before, after ->
                calls += "record"
                assertEquals(decision, recordedDecision)
                assertEquals(84.6, before, 0.0)
                assertEquals(0.0, after, 0.0)
            },
        )

        assertEquals(0.0, update, 0.0)
        assertEquals(listOf("reset", "record"), calls)
    }

    @Test
    fun `ordinary fuel decisions apply without creating a partial reset record`() {
        var recorded = false

        val update = applyFuelDecisionAndRecordPartialReset(
            confirmedRefuel = null,
            applyFuelDecision = { 12.5 },
            partialAfterKm = { it },
            recordPartialReset = { _, _, _ -> recorded = true },
        )

        assertEquals(12.5, update, 0.0)
        assertFalse(recorded)
    }
}
