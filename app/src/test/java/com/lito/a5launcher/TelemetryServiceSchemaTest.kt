package com.lito.a5launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryServiceSchemaTest {

    @Test
    fun currentTripStateAcceptsTheProductionSchemaDuringUpgrade() {
        assertTrue(isCompatibleTripSchema(2))
        assertTrue(isCompatibleTripSchema(3))
        assertFalse(isCompatibleTripSchema(1))
        assertFalse(isCompatibleTripSchema(4))
    }

    @Test
    fun newBootUsesDurableRefuelBaselineWhileDiscardingTripAccumulators() {
        val decision = decideTripRestoration(
            currentBootCount = 8,
            storedBootCount = 7,
            storedSchema = 3,
            startedAtElapsedMs = 20_000,
            nowElapsedMs = 30_000,
            durableFuelBaseline = 35,
        )

        assertEquals(TripRestoreReason.NEW_BOOT, decision.reason)
        assertFalse(decision.restoreTripAccumulators)
        assertEquals(35, decision.fuelBaselineLitres)
    }

    @Test
    fun sameBootRestoresOnlyAValidMonotonicStart() {
        val decision = decideTripRestoration(4, 4, 3, 50_000, 40_000, 32)

        assertEquals(TripRestoreReason.INVALID_ELAPSED_STATE, decision.reason)
        assertFalse(decision.restoreTripAccumulators)
        assertEquals(32, decision.fuelBaselineLitres)
    }
}
