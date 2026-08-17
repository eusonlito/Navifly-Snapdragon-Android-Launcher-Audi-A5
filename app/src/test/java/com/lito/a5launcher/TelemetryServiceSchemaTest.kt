package com.lito.a5launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryServiceSchemaTest {

    @Test
    fun currentTripStateAcceptsOnlyTheCompleteStatisticsSchema() {
        assertFalse(isCompatibleTripSchema(2))
        assertFalse(isCompatibleTripSchema(3))
        assertFalse(isCompatibleTripSchema(1))
        assertFalse(isCompatibleTripSchema(4))
        assertTrue(isCompatibleTripSchema(5))
    }

    @Test
    fun newBootUsesDurableRefuelBaselineWhileDiscardingTripAccumulators() {
        val decision = decideTripRestoration(
            currentBootCount = 8,
            storedBootCount = 7,
            storedSchema = 5,
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
        val decision = decideTripRestoration(4, 4, 5, 50_000, 40_000, 32)

        assertEquals(TripRestoreReason.INVALID_ELAPSED_STATE, decision.reason)
        assertFalse(decision.restoreTripAccumulators)
        assertEquals(32, decision.fuelBaselineLitres)
    }

    @Test
    fun partialCheckpointRecoversFuelMissedByTheTripCheckpoint() {
        val restored = restoreTripFuelCheckpoint(
            storedFuel = CumulativeFuelUsage(1.0, .5),
            storedVirtualFuelLitres = 30.0,
            storedUncalibratedFuelLitres = .4,
            tripGeneration = 7L,
            partialState = DistanceSinceRefuelStatisticsState(
                sourceTripFuelUsage = CumulativeFuelUsage(1.2, .6),
                sourceTripGeneration = 7L,
            ),
        )

        assertEquals(1.2, restored.estimatedLitres, .000_001)
        assertEquals(.6, restored.confirmedCanLitres, .000_001)
        assertEquals(29.8, restored.virtualFuelLitres, .000_001)
        assertEquals(.6, restored.uncalibratedFuelLitres, .000_001)
    }

    @Test
    fun partialCheckpointFromAnotherTripIsNotApplied() {
        val restored = restoreTripFuelCheckpoint(
            storedFuel = CumulativeFuelUsage(1.0, .5),
            storedVirtualFuelLitres = 30.0,
            storedUncalibratedFuelLitres = .4,
            tripGeneration = 8L,
            partialState = DistanceSinceRefuelStatisticsState(
                sourceTripFuelUsage = CumulativeFuelUsage(8.0, 4.0),
                sourceTripGeneration = 7L,
            ),
        )

        assertEquals(1.0, restored.estimatedLitres, .000_001)
        assertEquals(.5, restored.confirmedCanLitres, .000_001)
        assertEquals(30.0, restored.virtualFuelLitres, .000_001)
        assertEquals(.4, restored.uncalibratedFuelLitres, .000_001)
    }

    @Test
    fun aResetAlwaysCreatesADifferentTripGeneration() {
        assertEquals(101L, nextTripGeneration(100L, 100L, 0L))
    }
}
