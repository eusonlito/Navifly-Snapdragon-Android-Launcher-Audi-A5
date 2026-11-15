package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefuelStartupTest {
    @Test
    fun movingStartupWaitsForConfirmationBeforeResettingTripAndPartial() {
        val detector = ConfirmedRefuelDetector(initialFuelLitres = 35)
        val trip = TripSessionTracker(
            initialState = TripSessionState(virtualFuelLitres = 35.0, lastFuelLitres = 35),
            refuelDetector = null,
        )
        val partial = DistanceSinceRefuelTracker(25.0, 35, refuelDetector = null)

        for (time in listOf(1_000L, 2_000L)) {
            val decision = detector.observeDetailed(50, 50)
            val tripResult = trip.onTelemetryWithFuelDecision(50, 1_500, 50, time, decision)
            val partialResult = partial.advanceWithFuelDecision(50, 50, time, decision)
            assertEquals(35, trip.state().lastFuelLitres)
            assertEquals(35.0 - tripResult.fuelUsedLitres, tripResult.virtualFuelLitres, .000_001)
            assertFalse(partialResult.statisticsReset)
            assertTrue(partialResult.distanceKm >= 25.0)
        }
        assertEquals(PendingRefuelConfirmation(35, 50, 2), detector.readyConfirmation)
        val confirmed = requireNotNull(detector.confirmPendingRefuel())
        val tripResult = trip.onTelemetryWithFuelDecision(50, 1_500, 50, 3_000L, confirmed)
        val partialResult = partial.advanceWithFuelDecision(50, 50, 3_000L, confirmed)
        assertEquals(50.0, tripResult.virtualFuelLitres, .000_001)
        assertTrue(partialResult.statisticsReset)
        assertEquals(0.0, partialResult.distanceKm, .000_001)
        assertEquals(50, detector.baselineFuelLitres())
        assertNull(detector.readyConfirmation)
    }

    @Test
    fun startupRequiresMatchingConsecutiveValidSamples() {
        val detector = ConfirmedRefuelDetector(initialFuelLitres = 35)
        for (fuel in listOf(0, 35, 50, 51, 50, 0, 50)) {
            detector.observeDetailed(50, fuel)
            assertNull(detector.readyConfirmation)
        }
        detector.observeDetailed(50, 50)
        assertEquals(PendingRefuelConfirmation(35, 50, 2), detector.readyConfirmation)
    }

    @Test
    fun stableInitialLevelsCloseTheMovingWindowWithoutDisablingStationaryDetection() {
        for (initialLevel in listOf(35, 36, 34, 31)) {
            val detector = ConfirmedRefuelDetector(initialFuelLitres = 35)
            repeat(2) { detector.observeDetailed(50, initialLevel) }
            for (fuel in listOf(50, 50, 0, 50, 50)) {
                detector.observeDetailed(50, fuel)
                assertNull(detector.readyConfirmation)
            }
            detector.observeDetailed(0, 50)
            detector.observeDetailed(0, 50)
            assertEquals(50, detector.readyConfirmation?.candidateFuelLitres)
        }
    }

    @Test
    fun missingReferenceAndSmallIncreasesDoNotPromptAtStartup() {
        for (reference in listOf(null, 0, -1)) {
            val detector = ConfirmedRefuelDetector(initialFuelLitres = reference)
            detector.observeDetailed(50, 35)
            repeat(3) { detector.observeDetailed(50, 50) }
            assertNull(detector.readyConfirmation)
            assertEquals(35, detector.baselineFuelLitres())
        }
        val detector = ConfirmedRefuelDetector(initialFuelLitres = 48)
        repeat(3) { detector.observeDetailed(50, 51) }
        assertNull(detector.readyConfirmation)
        assertEquals(48, detector.baselineFuelLitres())
        val minimumFill = ConfirmedRefuelDetector(initialFuelLitres = 48)
        repeat(2) { minimumFill.observeDetailed(50, 52) }
        assertEquals(PendingRefuelConfirmation(48, 52, 2), minimumFill.readyConfirmation)
    }

    @Test
    fun manuallyAdoptingFuelClosesTheStartupWindow() {
        val detector = ConfirmedRefuelDetector(initialFuelLitres = 35)
        detector.adoptFuelLevel(50)
        repeat(3) { detector.observeDetailed(50, 60) }
        assertNull(detector.readyConfirmation)
        assertEquals(50, detector.baselineFuelLitres())
    }

    @Test
    fun startupConfirmationSurvivesRestartAndCanBeDeclinedWithoutRepeatedPrompts() {
        val detector = ConfirmedRefuelDetector(initialFuelLitres = 35)
        repeat(2) { detector.observeDetailed(50, 50) }
        val savedPrompt = requireNotNull(detector.pendingConfirmation)
        val restored = ConfirmedRefuelDetector(35, savedPrompt)
        restored.observeDetailed(50, 0)
        assertNull(restored.readyConfirmation)
        restored.observeDetailed(50, 50)
        assertNull(restored.readyConfirmation)
        restored.observeDetailed(50, 50)
        assertEquals(savedPrompt, restored.readyConfirmation)
        restored.rejectPendingRefuel()
        repeat(3) { restored.observeDetailed(50, 50) }
        assertNull(restored.readyConfirmation)
        assertEquals(50, restored.baselineFuelLitres())
    }

    @Test
    fun previouslySavedPromptStillRevalidatesWithRisingLevelsWhileMoving() {
        val restored = ConfirmedRefuelDetector(35, PendingRefuelConfirmation(35, 50, 2))
        restored.observeDetailed(50, 50)
        assertNull(restored.readyConfirmation)
        restored.observeDetailed(50, 51)
        assertEquals(PendingRefuelConfirmation(35, 51, 2), restored.readyConfirmation)
    }
}
