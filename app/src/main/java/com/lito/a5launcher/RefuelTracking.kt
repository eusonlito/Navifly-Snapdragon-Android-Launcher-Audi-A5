package com.lito.a5launcher

data class JourneyStatisticsSnapshot(
    val elapsedMs: Long = 0L,
    val movingElapsedMs: Long = 0L,
    val distanceKm: Double = 0.0,
    val maximumSpeedKmh: Int = 0,
    val averageSpeedKmh: Double = 0.0,
    val movingAverageSpeedKmh: Double = 0.0,
    val calculatedConsumption: Double = 0.0,
    val observedCanConsumption: Double = 0.0,
    val fuelUsedLitres: Double = 0.0,
    val confirmedCanFuelUsedLitres: Double = 0.0,
    val observedFuelSpentLitres: Double? = null,
)

data class CumulativeFuelUsage(
    val estimatedLitres: Double,
    val confirmedCanLitres: Double,
)

data class DistanceSinceRefuelStatisticsState(
    val elapsedMs: Long = 0L,
    val movingElapsedMs: Long = 0L,
    val maximumSpeedKmh: Int = 0,
    val fuelUsedLitres: Double = 0.0,
    val confirmedCanFuelUsedLitres: Double = 0.0,
    val initialObservedFuelLitres: Int? = null,
    val currentObservedFuelLitres: Int? = null,
    val sourceTripFuelUsage: CumulativeFuelUsage? = null,
    val sourceTripGeneration: Long? = null,
    val active: Boolean = false,
)

data class DistanceSinceRefuelPersistenceSnapshot(
    val distanceKm: Double,
    val lastFuelLitres: Int?,
    val statisticsState: DistanceSinceRefuelStatisticsState,
)

data class DistanceSinceRefuelSnapshot(
    val distanceKm: Double,
    val lastFuelLitres: Int?,
    val refuelDetected: Boolean,
    val statistics: JourneyStatisticsSnapshot = JourneyStatisticsSnapshot(distanceKm = distanceKm),
    val maximumSpeedChange: PartialMaximumSpeedChange? = null,
)

data class PartialMaximumSpeedChange(
    val previousSpeedKmh: Int,
    val currentSpeedKmh: Int,
)

sealed interface ConfirmedFuelLevelChange {
    data object Initialized : ConfirmedFuelLevelChange
    data class Drop(val litres: Int) : ConfirmedFuelLevelChange
    data class Refuel(
        val fuelLitres: Int,
        val baselineFuelLitres: Int = 0,
        val confirmationSamples: Int = 0,
    ) : ConfirmedFuelLevelChange
    data class Rejected(
        val baselineFuelLitres: Int,
        val candidateFuelLitres: Int,
        val observedFuelLitres: Int,
        val confirmationSamples: Int,
        val reason: RefuelRejectionReason,
    ) : ConfirmedFuelLevelChange
}

enum class RefuelRejectionReason(val code: String) {
    VEHICLE_MOVED("VEHICLE_MOVED"),
    BELOW_THRESHOLD("BELOW_THRESHOLD"),
    LEVEL_DROPPED("LEVEL_DROPPED"),
    INVALID_READING("INVALID_READING"),
}

/**
 * Confirms refuelling from the coarse integer fuel level shared by the trip and
 * distance trackers. The baseline only follows confirmed decreases while moving.
 * A single decrease large enough to look like a later refuel is rejected as an
 * ambiguous startup/sensor jump. Increases must reach three litres while stationary
 * and remain at or above that threshold for two samples.
 */
class ConfirmedRefuelDetector(initialFuelLitres: Int? = null) {
    private var baselineFuelLitres = initialFuelLitres?.takeIf { it > 0 }
    private var pendingFuelLitres: Int? = null
    private var pendingSamples = 0
    private var pendingBaselineFuelLitres: Int? = null
    private var pendingBaselineSamples = 0

    fun observe(speedKmh: Int, fuelLitres: Int): ConfirmedFuelLevelChange? =
        observeDetailed(speedKmh, fuelLitres).takeUnless { it is ConfirmedFuelLevelChange.Rejected }

    fun observeDetailed(speedKmh: Int, fuelLitres: Int): ConfirmedFuelLevelChange? {
        if (fuelLitres <= 0) {
            val rejection = rejectPending(fuelLitres, RefuelRejectionReason.INVALID_READING)
            clearPending()
            return rejection
        }
        val baseline = baselineFuelLitres
        if (baseline == null) {
            baselineFuelLitres = fuelLitres
            return ConfirmedFuelLevelChange.Initialized
        }
        if (fuelLitres < baseline) {
            val rejection = rejectPending(fuelLitres, RefuelRejectionReason.LEVEL_DROPPED)
            clearPending()
            val dropLitres = baseline - fuelLitres
            if (
                speedKmh <= MAX_STATIONARY_SPEED_KMH ||
                dropLitres >= MIN_REFUEL_LITRES
            ) {
                clearPendingBaseline()
                return rejection
            }
            if (pendingBaselineFuelLitres == fuelLitres) {
                pendingBaselineSamples++
            } else {
                pendingBaselineFuelLitres = fuelLitres
                pendingBaselineSamples = 1
            }
            if (pendingBaselineSamples >= BASELINE_CONFIRMATION_SAMPLES) {
                baselineFuelLitres = fuelLitres
                clearPendingBaseline()
                return ConfirmedFuelLevelChange.Drop(dropLitres)
            }
            return rejection
        }
        clearPendingBaseline()
        if (speedKmh > MAX_STATIONARY_SPEED_KMH || fuelLitres - baseline < MIN_REFUEL_LITRES) {
            val rejection = rejectPending(
                fuelLitres,
                if (speedKmh > MAX_STATIONARY_SPEED_KMH) {
                    RefuelRejectionReason.VEHICLE_MOVED
                } else {
                    RefuelRejectionReason.BELOW_THRESHOLD
                },
            )
            clearPending()
            return rejection
        }

        val previousCandidate = pendingFuelLitres
        pendingSamples = if (previousCandidate == null || fuelLitres < previousCandidate) {
            1
        } else {
            pendingSamples + 1
        }
        pendingFuelLitres = fuelLitres
        if (pendingSamples < REFUEL_CONFIRMATION_SAMPLES) return null

        val confirmationSamples = pendingSamples
        baselineFuelLitres = fuelLitres
        clearPending()
        clearPendingBaseline()
        return ConfirmedFuelLevelChange.Refuel(fuelLitres, baseline, confirmationSamples)
    }

    fun baselineFuelLitres(): Int? = baselineFuelLitres

    private fun clearPending() {
        pendingFuelLitres = null
        pendingSamples = 0
    }

    private fun rejectPending(
        observedFuelLitres: Int,
        reason: RefuelRejectionReason,
    ): ConfirmedFuelLevelChange.Rejected? {
        val baseline = baselineFuelLitres ?: return null
        val candidate = pendingFuelLitres ?: return null
        return ConfirmedFuelLevelChange.Rejected(
            baselineFuelLitres = baseline,
            candidateFuelLitres = candidate,
            observedFuelLitres = observedFuelLitres,
            confirmationSamples = pendingSamples,
            reason = reason,
        )
    }

    private fun clearPendingBaseline() {
        pendingBaselineFuelLitres = null
        pendingBaselineSamples = 0
    }

    private companion object {
        const val MIN_REFUEL_LITRES = 3
        const val MAX_STATIONARY_SPEED_KMH = 1
        const val REFUEL_CONFIRMATION_SAMPLES = 2
        const val BASELINE_CONFIRMATION_SAMPLES = 2
    }
}

/**
 * Accumulates travelled distance independently from the UI and resets only after
 * two stationary readings confirm a fuel increase of at least three litres.
 * Requiring confirmation avoids resets caused by normal fuel-level sensor noise.
 */
class DistanceSinceRefuelTracker(
    initialDistanceKm: Double = 0.0,
    initialFuelLitres: Int? = null,
    initialStatisticsState: DistanceSinceRefuelStatisticsState =
        DistanceSinceRefuelStatisticsState(active = initialDistanceKm > 0.0),
    private val refuelDetector: ConfirmedRefuelDetector? = ConfirmedRefuelDetector(initialFuelLitres),
) {
    private var lastElapsedRealtimeMs: Long? = null
    private var lastTelemetryElapsedMs: Long? = null
    private var previousSpeedKmh = 0
    private var distanceKm = initialDistanceKm.validMetric()
    private var lastFuelLitres = initialFuelLitres?.takeIf { it > 0 }
    private var statisticsElapsedMs = initialStatisticsState.elapsedMs.coerceAtLeast(0L)
    private var statisticsMovingElapsedMs = initialStatisticsState.movingElapsedMs.coerceAtLeast(0L)
    private var maximumSpeedKmh = validMaximumSpeedKmh(initialStatisticsState.maximumSpeedKmh)
    private var statisticsFuelUsedLitres = initialStatisticsState.fuelUsedLitres.validMetric()
    private var statisticsConfirmedCanFuelUsedLitres =
        initialStatisticsState.confirmedCanFuelUsedLitres.validMetric()
    private var initialObservedFuelLitres = initialStatisticsState.initialObservedFuelLitres
        ?.takeIf { it > 0 }
    private var currentObservedFuelLitres = initialStatisticsState.currentObservedFuelLitres
        ?.takeIf { it > 0 }
    private var sourceTripFuelUsage = initialStatisticsState.sourceTripFuelUsage?.normalized()
    private var sourceTripGeneration = initialStatisticsState.sourceTripGeneration
    private var statisticsActive = initialStatisticsState.active || initialDistanceKm > 0.0

    @Synchronized
    fun advance(
        speedKmh: Int,
        fuelLitres: Int,
        elapsedRealtimeMs: Long,
        evaluateFuel: Boolean = true,
        tripFuelUsage: CumulativeFuelUsage? = null,
        tripGeneration: Long? = null,
    ): DistanceSinceRefuelSnapshot {
        val fuelDecision = if (evaluateFuel) refuelDetector?.observeDetailed(speedKmh, fuelLitres) else null
        return advanceWithFuelDecision(
            speedKmh,
            fuelLitres,
            elapsedRealtimeMs,
            fuelDecision,
            tripFuelUsage,
            tripGeneration,
        )
    }

    @Synchronized
    fun advanceWithFuelDecision(
        speedKmh: Int,
        fuelLitres: Int,
        elapsedRealtimeMs: Long,
        fuelDecision: ConfirmedFuelLevelChange?,
        tripFuelUsage: CumulativeFuelUsage? = null,
        tripGeneration: Long? = null,
    ): DistanceSinceRefuelSnapshot {
        advanceTo(elapsedRealtimeMs)
        val safeSpeed = validVehicleSpeedKmh(speedKmh) ?: 0
        val previousMaximumSpeedKmh = maximumSpeedKmh
        if (!statisticsActive && safeSpeed > 0) statisticsActive = true
        previousSpeedKmh = safeSpeed
        lastTelemetryElapsedMs = elapsedRealtimeMs
        maximumSpeedKmh = maxOf(maximumSpeedKmh, safeSpeed)
        val fuelDelta = observeTripFuelUsage(tripFuelUsage, tripGeneration)
        statisticsFuelUsedLitres += fuelDelta.estimatedLitres
        statisticsConfirmedCanFuelUsedLitres += fuelDelta.confirmedCanLitres
        fuelLitres.takeIf { it > 0 }?.let { observed ->
            if (initialObservedFuelLitres == null) initialObservedFuelLitres = observed
            currentObservedFuelLitres = observed
        }

        when (fuelDecision) {
            ConfirmedFuelLevelChange.Initialized -> lastFuelLitres = fuelLitres.takeIf { it > 0 }
            is ConfirmedFuelLevelChange.Drop -> lastFuelLitres = (lastFuelLitres?.minus(fuelDecision.litres))
            is ConfirmedFuelLevelChange.Refuel -> lastFuelLitres = fuelDecision.fuelLitres
            is ConfirmedFuelLevelChange.Rejected, null -> Unit
        }
        refuelDetector?.baselineFuelLitres()?.let { lastFuelLitres = it }
        val refuelDetected = fuelDecision is ConfirmedFuelLevelChange.Refuel
        if (refuelDetected) {
            distanceKm = 0.0
            statisticsElapsedMs = 0L
            statisticsMovingElapsedMs = 0L
            maximumSpeedKmh = 0
            statisticsFuelUsedLitres = 0.0
            statisticsConfirmedCanFuelUsedLitres = 0.0
            initialObservedFuelLitres = fuelDecision.fuelLitres
            currentObservedFuelLitres = fuelDecision.fuelLitres
            statisticsActive = true
        }
        return DistanceSinceRefuelSnapshot(
            distanceKm = distanceKm,
            lastFuelLitres = lastFuelLitres,
            refuelDetected = refuelDetected,
            statistics = journeyStatistics(
                elapsedMs = statisticsElapsedMs,
                movingElapsedMs = statisticsMovingElapsedMs,
                distanceKm = distanceKm,
                maximumSpeedKmh = maximumSpeedKmh,
                fuelUsedLitres = statisticsFuelUsedLitres,
                confirmedCanFuelUsedLitres = statisticsConfirmedCanFuelUsedLitres,
                initialObservedFuelLitres = initialObservedFuelLitres,
                currentObservedFuelLitres = currentObservedFuelLitres,
            ),
            maximumSpeedChange = if (!refuelDetected && maximumSpeedKmh > previousMaximumSpeedKmh) {
                PartialMaximumSpeedChange(previousMaximumSpeedKmh, maximumSpeedKmh)
            } else null,
        )
    }

    @Synchronized
    fun onTick(
        elapsedRealtimeMs: Long,
        tripFuelUsage: CumulativeFuelUsage? = null,
        tripGeneration: Long? = null,
    ): DistanceSinceRefuelSnapshot {
        advanceTo(elapsedRealtimeMs)
        val fuelDelta = observeTripFuelUsage(tripFuelUsage, tripGeneration)
        statisticsFuelUsedLitres += fuelDelta.estimatedLitres
        statisticsConfirmedCanFuelUsedLitres += fuelDelta.confirmedCanLitres
        return snapshot(refuelDetected = false)
    }

    @Synchronized
    fun state() = DistanceSinceRefuelStatisticsState(
        elapsedMs = statisticsElapsedMs,
        movingElapsedMs = statisticsMovingElapsedMs,
        maximumSpeedKmh = maximumSpeedKmh,
        fuelUsedLitres = statisticsFuelUsedLitres,
        confirmedCanFuelUsedLitres = statisticsConfirmedCanFuelUsedLitres,
        initialObservedFuelLitres = initialObservedFuelLitres,
        currentObservedFuelLitres = currentObservedFuelLitres,
        sourceTripFuelUsage = sourceTripFuelUsage,
        sourceTripGeneration = sourceTripGeneration,
        active = statisticsActive,
    )

    @Synchronized
    fun persistenceSnapshot() = DistanceSinceRefuelPersistenceSnapshot(
        distanceKm = distanceKm,
        lastFuelLitres = lastFuelLitres,
        statisticsState = state(),
    )

    private fun advanceTo(elapsedRealtimeMs: Long) {
        val previousElapsed = lastElapsedRealtimeMs
        lastElapsedRealtimeMs = elapsedRealtimeMs
        if (previousElapsed == null || elapsedRealtimeMs < previousElapsed) return
        val telemetryExpiresAt = lastTelemetryElapsedMs?.plus(TELEMETRY_FRESHNESS_MS) ?: return
        val effectiveEnd = minOf(elapsedRealtimeMs, telemetryExpiresAt)
        val elapsedMs = (effectiveEnd - previousElapsed)
            .coerceIn(0L, MAX_INTEGRATION_INTERVAL_MS)
        if (elapsedMs <= 0L) return
        distanceKm += previousSpeedKmh * elapsedMs / 3_600_000.0
        if (statisticsActive) statisticsElapsedMs += elapsedMs
        if (statisticsActive && previousSpeedKmh > MAX_IDLE_SPEED_KMH) {
            statisticsMovingElapsedMs += elapsedMs
        }
    }

    private fun snapshot(refuelDetected: Boolean) = DistanceSinceRefuelSnapshot(
        distanceKm = distanceKm,
        lastFuelLitres = lastFuelLitres,
        refuelDetected = refuelDetected,
        statistics = journeyStatistics(
            elapsedMs = statisticsElapsedMs,
            movingElapsedMs = statisticsMovingElapsedMs,
            distanceKm = distanceKm,
            maximumSpeedKmh = maximumSpeedKmh,
            fuelUsedLitres = statisticsFuelUsedLitres,
            confirmedCanFuelUsedLitres = statisticsConfirmedCanFuelUsedLitres,
            initialObservedFuelLitres = initialObservedFuelLitres,
            currentObservedFuelLitres = currentObservedFuelLitres,
        ),
    )

    private fun observeTripFuelUsage(
        current: CumulativeFuelUsage?,
        generation: Long?,
    ): CumulativeFuelUsage {
        val safeCurrent = current?.normalized() ?: return CumulativeFuelUsage(0.0, 0.0)
        if (sourceTripGeneration != generation) {
            sourceTripFuelUsage = safeCurrent
            sourceTripGeneration = generation
            return CumulativeFuelUsage(0.0, 0.0)
        }
        val previous = sourceTripFuelUsage
        val delta = cumulativeDelta(safeCurrent, previous)
        sourceTripFuelUsage = if (previous == null) safeCurrent else CumulativeFuelUsage(
            estimatedLitres = maxOf(previous.estimatedLitres, safeCurrent.estimatedLitres),
            confirmedCanLitres = maxOf(previous.confirmedCanLitres, safeCurrent.confirmedCanLitres),
        )
        return delta
    }

    private fun cumulativeDelta(
        current: CumulativeFuelUsage?,
        previous: CumulativeFuelUsage?,
    ): CumulativeFuelUsage {
        val safeCurrent = current?.normalized() ?: return CumulativeFuelUsage(0.0, 0.0)
        val safePrevious = previous?.normalized() ?: return CumulativeFuelUsage(0.0, 0.0)
        return CumulativeFuelUsage(
            estimatedLitres = (safeCurrent.estimatedLitres - safePrevious.estimatedLitres)
                .coerceAtLeast(0.0),
            confirmedCanLitres = (safeCurrent.confirmedCanLitres - safePrevious.confirmedCanLitres)
                .coerceAtLeast(0.0),
        )
    }

    private fun CumulativeFuelUsage.normalized() = CumulativeFuelUsage(
        estimatedLitres = estimatedLitres.validMetric(),
        confirmedCanLitres = confirmedCanLitres.validMetric(),
    )

    private companion object {
        const val MAX_INTEGRATION_INTERVAL_MS = 30_000L
        const val TELEMETRY_FRESHNESS_MS = 2_000L
    }

}
