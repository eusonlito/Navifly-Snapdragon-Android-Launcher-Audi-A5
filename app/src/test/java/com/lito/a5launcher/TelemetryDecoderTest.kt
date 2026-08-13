package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryDecoderTest {
    @Test
    fun coreTelemetryIsDecodedByProductionCode() {
        val bytes = ByteArray(23)
        put16(bytes, 5, 420)
        put16(bytes, 9, 73)
        put16(bytes, 11, 120)
        put16(bytes, 13, 3_200)
        put16(bytes, 15, 45)
        put16(bytes, 17, 225)
        bytes[20] = 0x03
        bytes[21] = 0x5B
        bytes[22] = 0x60

        val result = requireNotNull(TelemetryDecoder.decodeCore(bytes))

        assertEquals(120, result.speed)
        assertEquals(73, result.averageSpeed)
        assertEquals(3_200, result.rpm)
        assertEquals(45, result.fuelLitres)
        assertEquals(420, result.nativeRangeKm)
        assertEquals(220_000, result.odometerKm)
        assertEquals(22.5, result.outsideTemperatureCelsius, .001)
    }

    @Test
    fun negativeTemperatureUsesSignedCanValue() {
        val bytes = ByteArray(20)
        put16(bytes, 17, 0xFFCA)

        assertEquals(
            -5.4,
            requireNotNull(TelemetryDecoder.decodeCore(bytes)).outsideTemperatureCelsius,
            .001,
        )
    }

    @Test
    fun shortCoreFramesAreRejected() {
        assertNull(TelemetryDecoder.decodeCore(ByteArray(19)))
    }

    @Test
    fun doorsAreDecodedByProductionCode() {
        val bytes = ByteArray(6)
        bytes[5] = 0x28

        val doors = requireNotNull(TelemetryDecoder.decodeDoors(bytes))

        assertTrue(doors.driverOpen)
        assertFalse(doors.passengerOpen)
        assertFalse(doors.rearLeftOpen)
        assertFalse(doors.rearRightOpen)
        assertTrue(doors.trunkOpen)
    }

    @Test
    fun manualGearEstimatorUsesAudiA5Ratios() {
        val cases = listOf(
            Triple("1", 12, 1_380),
            Triple("2", 30, 1_830),
            Triple("3", 50, 1_925),
            Triple("4", 60, 1_740),
            Triple("5", 80, 1_840),
            Triple("6", 100, 1_820),
        )

        cases.forEach { (expected, speed, rpm) ->
            val estimator = ManualGearEstimator()
            estimator.update(4, speed, rpm)
            assertEquals(expected, estimator.update(4, speed, rpm))
        }
    }

    @Test
    fun manualGearEstimatorDetectsSecondAtOneThousandRpm() {
        val estimator = ManualGearEstimator()

        assertEquals("N", estimator.update(4, 16, 1_000))
        assertEquals("2", estimator.update(4, 16, 1_000))
    }

    @Test
    fun manualGearEstimatorKeepsReliableReverseAndNeutralModes() {
        val estimator = ManualGearEstimator()

        assertEquals("R", estimator.update(2, 0, 0))
        assertEquals("N", estimator.update(3, 0, 0))
    }

    @Test
    fun manualGearEstimatorRejectsTransientAndClutchSamples() {
        val estimator = ManualGearEstimator()
        estimator.update(4, 50, 1_925)
        assertEquals("3", estimator.update(4, 50, 1_925))

        assertEquals("3", estimator.update(4, 30, 1_830))
        assertEquals("3", estimator.update(4, 50, 1_925))

        assertEquals("N", estimator.update(4, 50, 899))
    }

    @Test
    fun gearCoordinatorCountsEachDrivingFrameOnce() {
        val coordinator = GearTelemetryCoordinator()
        val secondGearFrame = DrivingSample(speed = 30, rpm = 1_830, rawGearType = 4)

        assertEquals("N", coordinator.update(secondGearFrame))
        assertEquals("2", coordinator.update(secondGearFrame))
    }

    @Test
    fun manualGearEstimatorRejectsOutOfToleranceSamples() {
        val estimator = ManualGearEstimator()
        estimator.update(4, 50, 1_925)
        assertEquals("3", estimator.update(4, 50, 1_925))

        repeat(3) {
            assertEquals("3", estimator.update(4, 50, 4_000))
        }
        assertEquals("N", estimator.update(4, 50, 4_000))
    }

    @Test
    fun manualGearEstimatorShowsFirstWhileCreepingForward() {
        val estimator = ManualGearEstimator()

        assertEquals("N", estimator.update(4, 2, 900))
        assertEquals("1", estimator.update(4, 2, 900))
        assertEquals("1", estimator.update(4, 6, 950))
    }

    @Test
    fun manualGearEstimatorHonoursCreepBoundaries() {
        val minimum = ManualGearEstimator()
        assertEquals("N", minimum.update(4, 1, 900))
        assertEquals("1", minimum.update(4, 1, 900))

        val maximum = ManualGearEstimator()
        assertEquals("N", maximum.update(4, 10, 900))
        assertEquals("1", maximum.update(4, 10, 900))

        val belowMinimumRpm = ManualGearEstimator()
        repeat(4) {
            assertEquals("N", belowMinimumRpm.update(4, 2, 899))
        }
    }

    @Test
    fun manualGearEstimatorTransitionsFromCreepToRatioEstimate() {
        val estimator = ManualGearEstimator()
        estimator.update(4, 2, 900)
        assertEquals("1", estimator.update(4, 2, 900))

        assertEquals("1", estimator.update(4, 30, 1_830))
        assertEquals("2", estimator.update(4, 30, 1_830))
    }

    @Test
    fun consumptionEstimatorReturnsFuelFlowWhileDrivingAndAtIdle() {
        assertEquals(.7, TripConsumptionEstimator.fuelFlowLitresPerHour(0, 900), .001)
        assertEquals(0.0, TripConsumptionEstimator.fuelFlowLitresPerHour(0, 500), .001)
        assertEquals(
            5.67,
            TripConsumptionEstimator.fuelFlowLitresPerHour(90, 1_800),
            .001,
        )
    }

    @Test
    fun tripDistanceAccumulatesOnlyWhileVehicleIsMoving() {
        val session = TripDistanceAccumulator()

        session.advance(speedKmh = 0, elapsedRealtimeMs = 1_000)
        session.advance(speedKmh = 36, elapsedRealtimeMs = 2_000)
        val moving = session.advance(speedKmh = 36, elapsedRealtimeMs = 12_000)
        val stopped = session.advance(speedKmh = 0, elapsedRealtimeMs = 17_000)
        val stillStopped = session.advance(speedKmh = 0, elapsedRealtimeMs = 27_000)

        assertEquals(.1, moving, .000_001)
        assertEquals(.15, stopped, .000_001)
        assertEquals(stopped, stillStopped, .000_001)
    }

    @Test
    fun tripDistanceIgnoresInvalidSpeedAndResetsItsClockAfterTimeMovesBackwards() {
        val session = TripDistanceAccumulator()

        session.advance(speedKmh = -10, elapsedRealtimeMs = 5_000)
        session.advance(speedKmh = 50, elapsedRealtimeMs = 4_000)
        val result = session.advance(speedKmh = 50, elapsedRealtimeMs = 7_600)

        assertEquals(.05, result, .000_001)
    }

    @Test
    fun tripDistanceCanResumeWithinTheSameDeviceBoot() {
        val session = TripDistanceAccumulator(initialDistanceKm = 12.5)

        session.advance(speedKmh = 0, elapsedRealtimeMs = 1_000)

        assertEquals(12.5, session.advance(0, 2_000), .000_001)
    }

    @Test
    fun tripSessionStartsOnFirstPositiveSpeedAndKeepsCountingWhileStopped() {
        val session = TripSessionTracker()

        assertEquals(0L, session.onTelemetry(0, 850, 40, 10_000).elapsedMs)
        assertEquals(0L, session.onTick(15_000).elapsedMs)
        assertEquals(0L, session.onTelemetry(1, 900, 40, 20_000).elapsedMs)
        assertEquals(5_000L, session.onTelemetry(0, 850, 40, 25_000).elapsedMs)
        assertEquals(15_000L, session.onTick(35_000).elapsedMs)
    }

    @Test
    fun tripSessionRestoresMonotonicStartDistanceAndConsumption() {
        val session = TripSessionTracker(
            TripSessionState(
                startedAtElapsedMs = 20_000,
                distanceKm = 12.5,
                fuelUsedLitres = .75,
                virtualFuelLitres = 35.0,
                calibrationFactor = 1.0,
            )
        )

        val restored = session.onTelemetry(0, 850, 35, 50_000)

        assertEquals(30_000L, restored.elapsedMs)
        assertEquals(12.5, restored.distanceKm, .000_001)
        assertEquals(6.0, restored.averageConsumption, .000_001)
        assertEquals(.75, restored.fuelUsedLitres, .000_001)
        assertEquals(20_000L, restored.startedAtElapsedMs)
    }

    @Test
    fun tripSessionAccumulatesRepeatedTelemetryOutsideTheUiLifecycle() {
        val session = TripSessionTracker()

        session.onTelemetry(36, 1_800, 40, 1_000)
        var firstTick = session.onTick(1_000)
        repeat(10) { second ->
            firstTick = session.onTelemetry(36, 1_800, 40, (second + 2L) * 1_000L)
        }
        var secondTick = firstTick
        repeat(10) { second ->
            secondTick = session.onTelemetry(36, 1_800, 40, (second + 12L) * 1_000L)
        }

        assertEquals(.2, secondTick.distanceKm, .000_001)
        assertEquals(20_000L, secondTick.elapsedMs)
        assertEquals(6.3, firstTick.averageConsumption, .000_001)
        assertEquals(firstTick.averageConsumption, secondTick.averageConsumption, .000_001)
        assertEquals(.0126, secondTick.fuelUsedLitres, .000_001)
    }

    @Test
    fun tripSessionIncludesIdleFuelAfterTheTripStarts() {
        val session = TripSessionTracker()

        session.onTelemetry(36, 1_800, 40, 1_000)
        repeat(10) { second -> session.onTelemetry(36, 1_800, 40, (second + 2L) * 1_000L) }
        var result = session.onTelemetry(0, 850, 40, 11_000)
        repeat(10) { second ->
            result = session.onTelemetry(0, 850, 40, (second + 12L) * 1_000L)
        }

        assertEquals(.1, result.distanceKm, .000_001)
        assertEquals(.008244444, result.fuelUsedLitres, .000_001)
        assertEquals(8.244444, result.averageConsumption, .000_001)
    }

    @Test
    fun tripSessionAccountsForIdleFuelBeforeMovementWithoutStartingTheTimer() {
        val session = TripSessionTracker()

        session.onTelemetry(0, 850, 40, 1_000)
        var result = session.onTick(1_000)
        repeat(600) { second ->
            result = session.onTelemetry(0, 850, 40, (second + 2L) * 1_000L)
        }

        assertEquals(0L, result.elapsedMs)
        assertEquals(0.0, result.distanceKm, .000_001)
        assertEquals(0.116666, result.fuelUsedLitres, .000_001)
        assertEquals(0.0, result.averageConsumption, .000_001)
        assertTrue(result.estimatedRangeKm in 664..666)
    }

    @Test
    fun tripSessionDoesNotUseTheInitialConsumptionSpikeForRange() {
        val session = TripSessionTracker()

        session.onTelemetry(0, 850, 40, 1_000)
        repeat(600) { second -> session.onTelemetry(0, 850, 40, (second + 2L) * 1_000L) }
        session.onTelemetry(36, 1_800, 40, 602_000)
        val result = session.onTick(602_100)

        assertEquals(15.0, result.averageConsumption, .000_001)
        assertTrue(result.estimatedRangeKm in 664..666)
    }

    @Test
    fun learnedRangeConsumptionSurvivesARecreatedTripSession() {
        val rangeState = RangeConsumptionState(
            learnedConsumption = 7.2,
            pendingSegmentDistanceKm = .4,
            pendingSegmentFuelLitres = .03,
        )
        val session = TripSessionTracker(TripSessionState(rangeConsumptionState = rangeState))

        val restored = session.onTelemetry(0, 0, 40, 1_000)

        assertTrue(restored.estimatedRangeKm in 555..556)
        assertEquals(rangeState, session.state().rangeConsumptionState)
    }

    @Test
    fun tripSessionStopsExtrapolatingFuelWhenCanTelemetryIsStale() {
        val session = TripSessionTracker()

        session.onTelemetry(0, 850, 40, 1_000)
        val fresh = session.onTick(3_000)
        val stale = session.onTick(601_000)

        assertEquals(.000388888, fresh.fuelUsedLitres, .000_001)
        assertEquals(fresh.fuelUsedLitres, stale.fuelUsedLitres, .000_001)
    }

    @Test
    fun idleFuelDoesNotCreateARangeCliffAtTheFirstKilometre() {
        val session = TripSessionTracker()
        session.onTelemetry(0, 850, 40, 1_000)
        repeat(600) { second -> session.onTelemetry(0, 850, 40, (second + 2L) * 1_000L) }
        session.onTelemetry(36, 1_800, 40, 602_000)
        repeat(99) { second -> session.onTelemetry(36, 1_800, 40, 602_000L + (second + 1L) * 1_000L) }
        val beforeBoundary = session.onTelemetry(36, 1_800, 40, 701_900)
        val afterBoundary = session.onTelemetry(36, 1_800, 40, 702_100)

        assertTrue(beforeBoundary.distanceKm < 1.0)
        assertTrue(afterBoundary.distanceKm > 1.0)
        assertTrue(kotlin.math.abs(afterBoundary.estimatedRangeKm - beforeBoundary.estimatedRangeKm) <= 1)
    }

    @Test
    fun rangeConsumptionGainsRecentInfluenceOnlyWithDrivenDistance() {
        val estimator = RangeConsumptionEstimator()

        assertEquals(6.0, estimator.estimate(15.0, 0.0), .000_001)
        assertEquals(6.54, estimator.estimate(15.0, 1.0), .000_001)
        assertEquals(11.4, estimator.estimate(15.0, 10.0), .000_001)
    }

    @Test
    fun rangeConsumptionLearnsSlowlyFromCompletedKilometres() {
        val estimator = RangeConsumptionEstimator()

        estimator.add(distanceKm = 1.0, fuelLitres = .09)

        assertEquals(6.3, estimator.state().learnedConsumption, .000_001)
        assertEquals(6.0, estimator.estimate(0.0, 0.0), .000_001)
        assertEquals(
            6.3,
            RangeConsumptionEstimator(estimator.state()).estimate(0.0, 0.0),
            .000_001,
        )
    }

    @Test
    fun rangeConsumptionKeepsTheCurrentTripBaselineFixedAcrossLearningBoundaries() {
        val estimator = RangeConsumptionEstimator()

        estimator.add(distanceKm = .999, fuelLitres = .08991)
        val beforeBoundary = estimator.estimate(recentConsumption = 9.0, recentDistanceKm = .999)
        estimator.add(distanceKm = .002, fuelLitres = .00018)
        val afterBoundary = estimator.estimate(recentConsumption = 9.0, recentDistanceKm = 1.001)

        assertEquals(6.0, estimator.baselineConsumption(), .000_001)
        assertEquals(6.3, estimator.state().learnedConsumption, .000_001)
        assertTrue(kotlin.math.abs(afterBoundary - beforeBoundary) < .001)
    }

    @Test
    fun rangeConsumptionProcessesEveryKilometreAndPreservesRemainderFromLargeDelta() {
        val estimator = RangeConsumptionEstimator()

        estimator.add(distanceKm = 2.4, fuelLitres = .216)

        val state = estimator.state()
        assertEquals(6.57, state.learnedConsumption, .000_001)
        assertEquals(.4, state.pendingSegmentDistanceKm, .000_001)
        assertEquals(.036, state.pendingSegmentFuelLitres, .000_001)
        assertEquals(6.0, estimator.baselineConsumption(), .000_001)
    }

    @Test
    fun tripSessionShowsConsumptionFromTheFirstPositiveDistance() {
        val session = TripSessionTracker()

        session.onTelemetry(36, 1_800, 40, 1_000)
        val result = session.onTick(1_100)

        assertEquals(.001, result.distanceKm, .000_001)
        assertEquals(6.3, result.averageConsumption, .000_001)
        assertTrue(result.estimatedRangeKm > 0)
    }

    @Test
    fun tripSessionCapsConsumptionWhenAStartedTripStopsAtIdle() {
        val session = TripSessionTracker()

        session.onTelemetry(1, 900, 40, 1_000)
        val result = session.onTelemetry(0, 900, 40, 31_000)

        assertEquals(15.0, result.averageConsumption, .000_001)
        assertEquals(0.0, result.recentConsumption, .000_001)
    }

    @Test
    fun recentConsumptionKeepsOnlyTheLatestTwentyKilometres() {
        val recent = RecentConsumptionTracker()
        repeat(80) { recent.add(.25, .0125) }
        repeat(4) { recent.add(.25, .025) }

        assertEquals(5.25, recent.averageConsumption(), .000_001)
        assertEquals(80, recent.state().completedSegments.size)
    }

    @Test
    fun recentConsumptionStateRoundTripsForSameBootPersistence() {
        val original = RecentConsumptionState(
            completedSegments = listOf(FuelDistanceSegment(.25, .015)),
            currentDistanceKm = .1,
            currentFuelLitres = .008,
        )

        assertEquals(original, decodeRecentConsumptionState(original.encode()))
    }

    @Test
    fun distanceSinceRefuelAccumulatesAcrossDrivingSamples() {
        val tracker = DistanceSinceRefuelTracker(initialDistanceKm = 12.5, initialFuelLitres = 40)

        tracker.advance(speedKmh = 36, fuelLitres = 40, elapsedRealtimeMs = 1_000)
        val result = tracker.advance(speedKmh = 36, fuelLitres = 39, elapsedRealtimeMs = 11_000)

        assertEquals(12.6, result.distanceKm, .000_001)
        assertFalse(result.refuelDetected)
    }

    @Test
    fun distanceSinceRefuelIgnoresFuelNoise() {
        val tracker = DistanceSinceRefuelTracker(initialDistanceKm = 25.0, initialFuelLitres = 40)

        tracker.advance(0, 42, 1_000)
        val result = tracker.advance(0, 42, 2_000)

        assertEquals(25.0, result.distanceKm, .000_001)
        assertFalse(result.refuelDetected)
    }

    @Test
    fun distanceSinceRefuelResetsAfterConfirmedStationaryFuelIncrease() {
        val tracker = DistanceSinceRefuelTracker(initialDistanceKm = 25.0, initialFuelLitres = 35)

        val first = tracker.advance(0, 50, 1_000)
        val confirmed = tracker.advance(0, 50, 2_000)

        assertEquals(25.0, first.distanceKm, .000_001)
        assertFalse(first.refuelDetected)
        assertEquals(0.0, confirmed.distanceKm, .000_001)
        assertTrue(confirmed.refuelDetected)
        assertEquals(50, confirmed.lastFuelLitres)
    }

    @Test
    fun distanceSinceRefuelDoesNotResetForFuelIncreaseWhileMoving() {
        val tracker = DistanceSinceRefuelTracker(initialDistanceKm = 25.0, initialFuelLitres = 35)

        tracker.advance(50, 50, 1_000)
        val moving = tracker.advance(50, 50, 2_000)
        tracker.advance(0, 50, 3_000)
        val stoppedAndConfirmed = tracker.advance(0, 50, 4_000)

        assertTrue(moving.distanceKm > 25.0)
        assertFalse(moving.refuelDetected)
        assertTrue(stoppedAndConfirmed.refuelDetected)
        assertEquals(0.0, stoppedAndConfirmed.distanceKm, .000_001)
    }

    private fun put16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 8).toByte()
        target[offset + 1] = value.toByte()
    }
}
