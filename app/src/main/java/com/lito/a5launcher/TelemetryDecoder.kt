package com.lito.a5launcher

import com.lito.a5launcher.model.DoorStatus

private fun Double.validMetric(): Double = takeIf { isFinite() && this >= 0.0 } ?: 0.0
internal const val DEFAULT_RANGE_CONSUMPTION_L_PER_100_KM = 6.0

data class CoreTelemetry(
    val speed: Int,
    val averageSpeed: Int,
    val rpm: Int,
    val fuelLitres: Int,
    val nativeRangeKm: Int,
    val odometerKm: Int?,
    val outsideTemperatureCelsius: Double,
)

data class DrivingSample(
    val speed: Int,
    val rpm: Int,
    val rawGearType: Int,
)

object TelemetryDecoder {
    fun decodeCore(bytes: ByteArray): CoreTelemetry? {
        if (bytes.size < 20) return null
        return CoreTelemetry(
            speed = unsigned16(bytes, 11),
            averageSpeed = unsigned16(bytes, 9),
            rpm = unsigned16(bytes, 13),
            fuelLitres = unsigned16(bytes, 15),
            nativeRangeKm = unsigned16(bytes, 5),
            odometerKm = if (bytes.size >= 23) unsigned24(bytes, 20) else null,
            outsideTemperatureCelsius = signed16(bytes, 17) * 0.1,
        )
    }

    fun decodeDoors(bytes: ByteArray): DoorStatus? {
        if (bytes.size < 6) return null
        val flags = bytes[5].toInt() and 0xFF
        return DoorStatus(
            driverOpen = flags and 0x20 != 0,
            passengerOpen = flags and 0x10 != 0,
            rearLeftOpen = flags and 0x80 != 0,
            rearRightOpen = flags and 0x40 != 0,
            trunkOpen = flags and 0x08 != 0,
        )
    }

    private fun unsigned16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)

    private fun unsigned24(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)

    private fun signed16(bytes: ByteArray, offset: Int): Int =
        unsigned16(bytes, offset).toShort().toInt()
}

class ManualGearEstimator {
    private var displayedGear = "N"
    private var pendingGear: String? = null
    private var pendingSamples = 0
    private var invalidSamples = 0

    fun update(rawGearType: Int, speed: Int, rpm: Int): String {
        when (rawGearType) {
            2, 13 -> return selectImmediately("R")
            1, 3, 14, 15 -> return selectImmediately("N")
        }
        if (rpm < MIN_GEAR_RPM) return selectImmediately("N")

        val candidate = estimateNumericGear(speed, rpm)
        if (candidate == null) {
            pendingGear = null
            pendingSamples = 0
            invalidSamples++
            if (invalidSamples >= INVALID_SAMPLES_BEFORE_NEUTRAL) {
                displayedGear = "N"
            }
            return displayedGear
        }

        invalidSamples = 0
        if (candidate == displayedGear) {
            pendingGear = null
            pendingSamples = 0
            return displayedGear
        }

        if (candidate == pendingGear) {
            pendingSamples++
        } else {
            pendingGear = candidate
            pendingSamples = 1
        }
        if (pendingSamples >= MATCHING_SAMPLES_TO_CHANGE) {
            displayedGear = candidate
            pendingGear = null
            pendingSamples = 0
        }
        return displayedGear
    }

    private fun selectImmediately(gear: String): String {
        displayedGear = gear
        pendingGear = null
        pendingSamples = 0
        invalidSamples = 0
        return gear
    }

    private fun estimateNumericGear(speed: Int, rpm: Int): String? {
        if (speed in 1..MAX_CREEP_SPEED_KMH) return "1"
        if (speed < MIN_ESTIMATION_SPEED_KMH) return null
        val observedRatio = rpm.toDouble() / speed
        val nearest = A5_20_TDI_MANUAL_RATIOS
            .mapIndexed { index, expected -> GearMatch((index + 1).toString(), expected) }
            .minBy { match -> kotlin.math.abs(observedRatio - match.ratio) / match.ratio }
        val relativeError = kotlin.math.abs(observedRatio - nearest.ratio) / nearest.ratio
        return nearest.gear.takeIf { relativeError <= MAX_RELATIVE_ERROR }
    }

    private data class GearMatch(val gear: String, val ratio: Double)

    private companion object {
        const val MIN_ESTIMATION_SPEED_KMH = 4
        const val MAX_CREEP_SPEED_KMH = 10
        const val MIN_GEAR_RPM = 900
        const val MAX_RELATIVE_ERROR = .16
        const val MATCHING_SAMPLES_TO_CHANGE = 2
        const val INVALID_SAMPLES_BEFORE_NEUTRAL = 4

        // Expected engine RPM per km/h for the longitudinal Audi 0B1 six-speed
        // manual profile. Gears 1-3 are calibrated from the recorded A5 trip;
        // gears 4-6 follow the 0B1 ratio progression for the 2.0 TDI.
        val A5_20_TDI_MANUAL_RATIOS = doubleArrayOf(115.0, 61.0, 38.5, 29.0, 23.0, 18.2)
    }
}

class GearTelemetryCoordinator(
    private val estimator: ManualGearEstimator = ManualGearEstimator(),
) {
    fun update(sample: DrivingSample): String =
        estimator.update(sample.rawGearType, sample.speed, sample.rpm)
}

object TripConsumptionEstimator {
    fun fuelFlowLitresPerHour(speed: Int, rpm: Int): Double {
        if (rpm <= ENGINE_OFF_RPM) return 0.0
        if (speed <= MOVING_SPEED_KMH) return IDLE_FUEL_FLOW_LPH
        val rpmFactor = (rpm.toDouble() / 1_800.0) * 1.5
        val speedDrag = if (speed > 110) (speed - 110) * 0.04 else 0.0
        val consumptionLitresPer100Km = 4.8 + rpmFactor + speedDrag
        return maxOf(
            IDLE_FUEL_FLOW_LPH,
            consumptionLitresPer100Km * speed / 100.0,
        )
    }

    private const val ENGINE_OFF_RPM = 500
    private const val MOVING_SPEED_KMH = 1
    private const val IDLE_FUEL_FLOW_LPH = .7
}

data class FuelDistanceSegment(
    val distanceKm: Double,
    val fuelLitres: Double,
)

data class RecentConsumptionState(
    val completedSegments: List<FuelDistanceSegment> = emptyList(),
    val currentDistanceKm: Double = 0.0,
    val currentFuelLitres: Double = 0.0,
)

fun RecentConsumptionState.encode(): String {
    val segments = completedSegments.joinToString(";") { "${it.distanceKm},${it.fuelLitres}" }
    return "$segments|$currentDistanceKm,$currentFuelLitres"
}

fun decodeRecentConsumptionState(encoded: String?): RecentConsumptionState {
    if (encoded.isNullOrBlank()) return RecentConsumptionState()
    val sections = encoded.split('|', limit = 2)
    val completed = sections.firstOrNull().orEmpty().split(';').mapNotNull { value ->
        if (value.isBlank()) return@mapNotNull null
        val fields = value.split(',', limit = 2)
        val distance = fields.getOrNull(0)?.toDoubleOrNull()
        val fuel = fields.getOrNull(1)?.toDoubleOrNull()
        if (distance == null || fuel == null || !distance.isFinite() || !fuel.isFinite() ||
            distance < 0.0 || fuel < 0.0
        ) null else FuelDistanceSegment(distance, fuel)
    }
    val current = sections.getOrNull(1).orEmpty().split(',', limit = 2)
    return RecentConsumptionState(
        completedSegments = completed,
        currentDistanceKm = current.getOrNull(0)?.toDoubleOrNull()?.takeIf {
            it.isFinite() && it >= 0.0
        } ?: 0.0,
        currentFuelLitres = current.getOrNull(1)?.toDoubleOrNull()?.takeIf {
            it.isFinite() && it >= 0.0
        } ?: 0.0,
    )
}

class RecentConsumptionTracker(initialState: RecentConsumptionState = RecentConsumptionState()) {
    private val completedSegments = ArrayDeque<FuelDistanceSegment>()
    private var currentDistanceKm = initialState.currentDistanceKm.validMetric()
    private var currentFuelLitres = initialState.currentFuelLitres.validMetric()
    private var completedDistanceKm = 0.0
    private var completedFuelLitres = 0.0

    init {
        initialState.completedSegments.forEach { segment ->
            val distance = segment.distanceKm.validMetric()
            val fuel = segment.fuelLitres.validMetric()
            if (distance > 0.0 || fuel > 0.0) addCompleted(FuelDistanceSegment(distance, fuel))
        }
        trimToWindow()
    }

    fun add(distanceKm: Double, fuelLitres: Double) {
        currentDistanceKm += distanceKm.validMetric()
        currentFuelLitres += fuelLitres.validMetric()
        if (currentDistanceKm >= SEGMENT_DISTANCE_KM) {
            addCompleted(FuelDistanceSegment(currentDistanceKm, currentFuelLitres))
            currentDistanceKm = 0.0
            currentFuelLitres = 0.0
            trimToWindow()
        }
    }

    fun averageConsumption(): Double {
        val distance = completedDistanceKm + currentDistanceKm
        val fuel = completedFuelLitres + currentFuelLitres
        return if (distance > 0.0) fuel / distance * 100.0 else 0.0
    }

    fun distanceKm(): Double = completedDistanceKm + currentDistanceKm

    fun state(): RecentConsumptionState = RecentConsumptionState(
        completedSegments = completedSegments.toList(),
        currentDistanceKm = currentDistanceKm,
        currentFuelLitres = currentFuelLitres,
    )

    private fun trimToWindow() {
        var totalDistance = completedDistanceKm + currentDistanceKm
        while (completedSegments.isNotEmpty() && totalDistance > WINDOW_DISTANCE_KM) {
            val overflow = totalDistance - WINDOW_DISTANCE_KM
            val first = completedSegments.removeFirst()
            completedDistanceKm -= first.distanceKm
            completedFuelLitres -= first.fuelLitres
            if (first.distanceKm > overflow) {
                val retainedRatio = (first.distanceKm - overflow) / first.distanceKm
                val retained = FuelDistanceSegment(
                    distanceKm = first.distanceKm - overflow,
                    fuelLitres = first.fuelLitres * retainedRatio,
                )
                completedSegments.addFirst(retained)
                completedDistanceKm += retained.distanceKm
                completedFuelLitres += retained.fuelLitres
                totalDistance = WINDOW_DISTANCE_KM
            } else {
                totalDistance -= first.distanceKm
            }
        }
    }

    private fun addCompleted(segment: FuelDistanceSegment) {
        completedSegments.addLast(segment)
        completedDistanceKm += segment.distanceKm
        completedFuelLitres += segment.fuelLitres
    }

    private companion object {
        const val SEGMENT_DISTANCE_KM = .25
        const val WINDOW_DISTANCE_KM = 20.0
    }
}

data class RangeConsumptionState(
    val learnedConsumption: Double = DEFAULT_RANGE_CONSUMPTION_L_PER_100_KM,
    val pendingSegmentDistanceKm: Double = 0.0,
    val pendingSegmentFuelLitres: Double = 0.0,
)

/**
 * Keeps distance-to-empty independent from the volatile consumption average at
 * the start of a journey. The learned baseline changes only after a meaningful
 * driven distance, while the latest 20 km gain influence progressively.
 */
class RangeConsumptionEstimator(
    initialState: RangeConsumptionState = RangeConsumptionState(),
    baselineConsumption: Double? = null,
) {
    private var learnedConsumption = initialState.learnedConsumption
        .takeIf { it.isFinite() }
        ?.coerceIn(MIN_CONSUMPTION, MAX_CONSUMPTION)
        ?: DEFAULT_CONSUMPTION
    private var pendingSegmentDistanceKm = initialState.pendingSegmentDistanceKm.validMetric()
    private var pendingSegmentFuelLitres = initialState.pendingSegmentFuelLitres.validMetric()
    private val baselineConsumption = baselineConsumption
        ?.takeIf { it.isFinite() }
        ?.coerceIn(MIN_CONSUMPTION, MAX_CONSUMPTION)
        ?: learnedConsumption

    fun add(distanceKm: Double, fuelLitres: Double) {
        var remainingDistanceKm = distanceKm.validMetric()
        var remainingFuelLitres = fuelLitres.validMetric()
        if (remainingDistanceKm <= 0.0) {
            pendingSegmentFuelLitres += remainingFuelLitres
            return
        }

        while (remainingDistanceKm > 0.0) {
            val distanceToBoundary = LEARNING_SEGMENT_KM - pendingSegmentDistanceKm
            val segmentDistance = minOf(remainingDistanceKm, distanceToBoundary)
            val segmentFuel = remainingFuelLitres * segmentDistance / remainingDistanceKm
            pendingSegmentDistanceKm += segmentDistance
            pendingSegmentFuelLitres += segmentFuel
            remainingDistanceKm -= segmentDistance
            remainingFuelLitres = (remainingFuelLitres - segmentFuel).coerceAtLeast(0.0)

            if (pendingSegmentDistanceKm >= LEARNING_SEGMENT_KM) {
                learnCompletedSegment()
            }
        }
    }

    fun estimate(recentConsumption: Double, recentDistanceKm: Double): Double {
        val recent = recentConsumption.takeIf { it.isFinite() && it > 0.0 }
            ?.coerceIn(MIN_CONSUMPTION, MAX_CONSUMPTION)
            ?: return baselineConsumption
        val recentWeight = (
            recentDistanceKm.validMetric() / FULL_RECENT_INFLUENCE_KM
            ).coerceIn(0.0, 1.0) * MAX_RECENT_WEIGHT
        return baselineConsumption * (1.0 - recentWeight) + recent * recentWeight
    }

    fun baselineConsumption(): Double = baselineConsumption

    fun state(): RangeConsumptionState = RangeConsumptionState(
        learnedConsumption = learnedConsumption,
        pendingSegmentDistanceKm = pendingSegmentDistanceKm,
        pendingSegmentFuelLitres = pendingSegmentFuelLitres,
    )

    private fun learnCompletedSegment() {
        val observed = (pendingSegmentFuelLitres / pendingSegmentDistanceKm * 100.0)
            .takeIf { it.isFinite() }
            ?.coerceIn(MIN_CONSUMPTION, MAX_CONSUMPTION)
            ?: learnedConsumption
        learnedConsumption = (
            learnedConsumption * (1.0 - LEARNING_WEIGHT) + observed * LEARNING_WEIGHT
            ).coerceIn(MIN_CONSUMPTION, MAX_CONSUMPTION)
        pendingSegmentDistanceKm = 0.0
        pendingSegmentFuelLitres = 0.0
    }

    private companion object {
        const val DEFAULT_CONSUMPTION = DEFAULT_RANGE_CONSUMPTION_L_PER_100_KM
        const val MIN_CONSUMPTION = 3.0
        const val MAX_CONSUMPTION = 15.0
        const val LEARNING_SEGMENT_KM = 1.0
        const val LEARNING_WEIGHT = .1
        const val FULL_RECENT_INFLUENCE_KM = 10.0
        const val MAX_RECENT_WEIGHT = .6
    }
}

class TripDistanceAccumulator(initialDistanceKm: Double = 0.0) {
    private var lastElapsedRealtimeMs: Long? = null
    private var previousSpeedKmh = 0
    private var distanceKm = initialDistanceKm.coerceAtLeast(0.0)

    fun advance(speedKmh: Int, elapsedRealtimeMs: Long): Double {
        val previousElapsed = lastElapsedRealtimeMs
        if (previousElapsed != null && elapsedRealtimeMs >= previousElapsed) {
            val elapsedMs = elapsedRealtimeMs - previousElapsed
            distanceKm += previousSpeedKmh * elapsedMs / 3_600_000.0
        }
        lastElapsedRealtimeMs = elapsedRealtimeMs
        previousSpeedKmh = speedKmh.coerceAtLeast(0)
        return distanceKm
    }
}

data class DistanceSinceRefuelSnapshot(
    val distanceKm: Double,
    val lastFuelLitres: Int?,
    val refuelDetected: Boolean,
)

/**
 * Accumulates travelled distance independently from the UI and resets only after
 * two stationary readings confirm a fuel increase of at least three litres.
 * Requiring confirmation avoids resets caused by normal fuel-level sensor noise.
 */
class DistanceSinceRefuelTracker(
    initialDistanceKm: Double = 0.0,
    initialFuelLitres: Int? = null,
) {
    private var lastElapsedRealtimeMs: Long? = null
    private var previousSpeedKmh = 0
    private var distanceKm = initialDistanceKm.validMetric()
    private var baselineFuelLitres = initialFuelLitres?.takeIf { it > 0 }
    private var pendingFuelLitres: Int? = null
    private var pendingSamples = 0

    @Synchronized
    fun advance(
        speedKmh: Int,
        fuelLitres: Int,
        elapsedRealtimeMs: Long,
        evaluateFuel: Boolean = true,
    ): DistanceSinceRefuelSnapshot {
        val safeSpeed = speedKmh.coerceAtLeast(0)
        lastElapsedRealtimeMs?.let { previousElapsed ->
            if (elapsedRealtimeMs >= previousElapsed) {
                distanceKm += previousSpeedKmh *
                    (elapsedRealtimeMs - previousElapsed) / 3_600_000.0
            }
        }
        lastElapsedRealtimeMs = elapsedRealtimeMs
        previousSpeedKmh = safeSpeed

        val refuelDetected = evaluateFuel && detectRefuel(safeSpeed, fuelLitres)
        if (refuelDetected) distanceKm = 0.0
        return DistanceSinceRefuelSnapshot(
            distanceKm = distanceKm,
            lastFuelLitres = baselineFuelLitres,
            refuelDetected = refuelDetected,
        )
    }

    private fun detectRefuel(speedKmh: Int, fuelLitres: Int): Boolean {
        if (fuelLitres <= 0) return false
        val baseline = baselineFuelLitres
        if (baseline == null) {
            baselineFuelLitres = fuelLitres
            return false
        }
        if (fuelLitres < baseline) {
            baselineFuelLitres = fuelLitres
            clearPendingRefuel()
            return false
        }
        if (speedKmh > MAX_REFUEL_SPEED_KMH || fuelLitres - baseline < MIN_REFUEL_LITRES) {
            clearPendingRefuel()
            return false
        }

        if (pendingFuelLitres == fuelLitres) {
            pendingSamples++
        } else {
            pendingFuelLitres = fuelLitres
            pendingSamples = 1
        }
        if (pendingSamples < REFUEL_CONFIRMATION_SAMPLES) return false

        baselineFuelLitres = fuelLitres
        clearPendingRefuel()
        return true
    }

    private fun clearPendingRefuel() {
        pendingFuelLitres = null
        pendingSamples = 0
    }

    private companion object {
        const val MIN_REFUEL_LITRES = 3
        const val MAX_REFUEL_SPEED_KMH = 1
        const val REFUEL_CONFIRMATION_SAMPLES = 2
    }
}

data class TripMetricsSnapshot(
    val startedAtElapsedMs: Long?,
    val elapsedMs: Long,
    val distanceKm: Double,
    val averageConsumption: Double,
    val recentConsumption: Double,
    val fuelUsedLitres: Double,
    val virtualFuelLitres: Double,
    val estimatedRangeKm: Int,
)

data class TripSessionState(
    val startedAtElapsedMs: Long? = null,
    val distanceKm: Double = 0.0,
    val fuelUsedLitres: Double = 0.0,
    val virtualFuelLitres: Double = 0.0,
    val calibrationFactor: Double = 1.0,
    val lastFuelLitres: Int? = null,
    val calibrationAnchorFuelLitres: Int? = null,
    val uncalibratedFuelSinceAnchorLitres: Double = 0.0,
    val recentConsumptionState: RecentConsumptionState = RecentConsumptionState(),
    val rangeConsumptionState: RangeConsumptionState = RangeConsumptionState(),
    val rangeBaselineConsumption: Double? = null,
)

class TripSessionTracker(
    initialState: TripSessionState = TripSessionState(),
) {
    private var startedAtElapsedMs = initialState.startedAtElapsedMs?.takeIf { it >= 0L }
    private var lastElapsedRealtimeMs: Long? = null
    private var speedKmh = 0
    private var rpm = 0
    private var lastTelemetryElapsedMs: Long? = null
    private var distanceKm = initialState.distanceKm.validMetric()
    private var fuelUsedLitres = initialState.fuelUsedLitres.validMetric()
    private var virtualFuelLitres = initialState.virtualFuelLitres.validMetric()
    private var calibrationFactor = initialState.calibrationFactor
        .takeIf { it.isFinite() }?.coerceIn(MIN_CALIBRATION_FACTOR, MAX_CALIBRATION_FACTOR) ?: 1.0
    private var lastFuelLitres = initialState.lastFuelLitres?.takeIf { it > 0 }
    private var calibrationAnchorFuelLitres = initialState.calibrationAnchorFuelLitres?.takeIf { it > 0 }
    private var uncalibratedFuelSinceAnchorLitres = initialState.uncalibratedFuelSinceAnchorLitres.validMetric()
    private val recentConsumption = RecentConsumptionTracker(initialState.recentConsumptionState)
    private val rangeEstimator = RangeConsumptionEstimator(
        initialState.rangeConsumptionState,
        initialState.rangeBaselineConsumption,
    )
    private var persistenceVersion = 0L

    @Synchronized
    fun onTelemetry(
        speedKmh: Int,
        rpm: Int,
        fuelLitres: Int,
        elapsedRealtimeMs: Long,
    ): TripMetricsSnapshot {
        advanceTo(elapsedRealtimeMs)
        this.speedKmh = speedKmh.coerceAtLeast(0)
        this.rpm = rpm.coerceAtLeast(0)
        lastTelemetryElapsedMs = elapsedRealtimeMs
        if (startedAtElapsedMs == null && this.speedKmh > 0) {
            startedAtElapsedMs = elapsedRealtimeMs
            persistenceVersion++
        }
        observeFuelLevel(fuelLitres)
        return snapshot(elapsedRealtimeMs)
    }

    @Synchronized
    fun onTick(elapsedRealtimeMs: Long): TripMetricsSnapshot {
        advanceTo(elapsedRealtimeMs)
        return snapshot(elapsedRealtimeMs)
    }

    private fun advanceTo(elapsedRealtimeMs: Long) {
        val previousElapsed = lastElapsedRealtimeMs
        lastElapsedRealtimeMs = elapsedRealtimeMs
        if (previousElapsed == null || elapsedRealtimeMs < previousElapsed) return
        val telemetryExpiresAt = lastTelemetryElapsedMs?.plus(TELEMETRY_FRESHNESS_MS) ?: return
        val effectiveEnd = minOf(elapsedRealtimeMs, telemetryExpiresAt)
        val elapsedMs = (effectiveEnd - previousElapsed)
            .coerceIn(0L, MAX_INTEGRATION_INTERVAL_MS)
        if (elapsedMs <= 0L) return

        val elapsedHours = elapsedMs / 3_600_000.0
        val distanceDeltaKm = speedKmh * elapsedHours
        val rawFuelDelta = TripConsumptionEstimator
            .fuelFlowLitresPerHour(speedKmh, rpm) * elapsedHours
        val fuelDelta = rawFuelDelta * calibrationFactor
        distanceKm += distanceDeltaKm
        fuelUsedLitres += fuelDelta
        uncalibratedFuelSinceAnchorLitres += rawFuelDelta
        if (virtualFuelLitres > 0.0) {
            virtualFuelLitres = (virtualFuelLitres - fuelDelta).coerceAtLeast(0.0)
        }
        val movingFuelDelta = if (speedKmh > MAX_IDLE_SPEED_KMH) fuelDelta else 0.0
        recentConsumption.add(distanceDeltaKm, movingFuelDelta)
        rangeEstimator.add(distanceDeltaKm, movingFuelDelta)
        if (distanceDeltaKm > 0.0 || rawFuelDelta > 0.0) persistenceVersion++
    }

    private fun observeFuelLevel(fuelLitres: Int) {
        if (fuelLitres <= 0) return
        val previousFuel = lastFuelLitres
        var changed = false
        if (virtualFuelLitres <= 0.0) {
            virtualFuelLitres = fuelLitres.toDouble()
            changed = true
        }
        if (calibrationAnchorFuelLitres == null) {
            calibrationAnchorFuelLitres = fuelLitres
            changed = true
        }

        if (previousFuel != null && fuelLitres - previousFuel >= REFUEL_INCREASE_LITRES) {
            virtualFuelLitres = fuelLitres.toDouble()
            calibrationAnchorFuelLitres = fuelLitres
            uncalibratedFuelSinceAnchorLitres = 0.0
            changed = true
        } else {
            val anchor = calibrationAnchorFuelLitres
            val observedDrop = if (anchor != null) anchor - fuelLitres else 0
            if (observedDrop >= CALIBRATION_DROP_LITRES && uncalibratedFuelSinceAnchorLitres > 0.0) {
                val observedFactor = (observedDrop / uncalibratedFuelSinceAnchorLitres)
                    .coerceIn(MIN_CALIBRATION_FACTOR, MAX_CALIBRATION_FACTOR)
                calibrationFactor = (
                    calibrationFactor * (1.0 - CALIBRATION_ADJUSTMENT_WEIGHT) +
                        observedFactor * CALIBRATION_ADJUSTMENT_WEIGHT
                    ).coerceIn(MIN_CALIBRATION_FACTOR, MAX_CALIBRATION_FACTOR)
                virtualFuelLitres += (fuelLitres - virtualFuelLitres) * VIRTUAL_FUEL_CORRECTION_WEIGHT
                calibrationAnchorFuelLitres = fuelLitres
                uncalibratedFuelSinceAnchorLitres = 0.0
                changed = true
            }
        }
        if (lastFuelLitres != fuelLitres) {
            lastFuelLitres = fuelLitres
            changed = true
        }
        if (changed) persistenceVersion++
    }

    private fun snapshot(elapsedRealtimeMs: Long): TripMetricsSnapshot {
        val start = startedAtElapsedMs?.takeIf { it <= elapsedRealtimeMs }
        val tripConsumption = cappedConsumption(fuelUsedLitres, distanceKm)
        val recent = cappedConsumption(recentConsumption.averageConsumption())
        val rangeConsumptionEstimate = rangeEstimator.estimate(
            recentConsumption = recent,
            recentDistanceKm = recentConsumption.distanceKm(),
        )
        return TripMetricsSnapshot(
            startedAtElapsedMs = start,
            elapsedMs = start?.let { elapsedRealtimeMs - it } ?: 0L,
            distanceKm = distanceKm,
            averageConsumption = tripConsumption,
            recentConsumption = recent,
            fuelUsedLitres = fuelUsedLitres,
            virtualFuelLitres = virtualFuelLitres,
            estimatedRangeKm = if (virtualFuelLitres > 0.0) {
                kotlin.math.round(virtualFuelLitres / rangeConsumptionEstimate * 100.0).toInt()
            } else 0,
        )
    }

    private fun cappedConsumption(fuelLitres: Double, distanceKm: Double): Double =
        if (distanceKm > 0.0) cappedConsumption(fuelLitres / distanceKm * 100.0) else 0.0

    private fun cappedConsumption(consumptionLitresPer100Km: Double): Double =
        consumptionLitresPer100Km.takeIf { it.isFinite() }?.coerceIn(0.0, MAX_DISPLAY_CONSUMPTION) ?: 0.0

    @Synchronized
    fun persistenceVersion(): Long = persistenceVersion

    @Synchronized
    fun state(): TripSessionState = TripSessionState(
        startedAtElapsedMs = startedAtElapsedMs,
        distanceKm = distanceKm,
        fuelUsedLitres = fuelUsedLitres,
        virtualFuelLitres = virtualFuelLitres,
        calibrationFactor = calibrationFactor,
        lastFuelLitres = lastFuelLitres,
        calibrationAnchorFuelLitres = calibrationAnchorFuelLitres,
        uncalibratedFuelSinceAnchorLitres = uncalibratedFuelSinceAnchorLitres,
        recentConsumptionState = recentConsumption.state(),
        rangeConsumptionState = rangeEstimator.state(),
        rangeBaselineConsumption = rangeEstimator.baselineConsumption(),
    )

    private companion object {
        const val MAX_INTEGRATION_INTERVAL_MS = 30_000L
        const val TELEMETRY_FRESHNESS_MS = 2_000L
        const val MAX_IDLE_SPEED_KMH = 1
        const val REFUEL_INCREASE_LITRES = 3
        const val CALIBRATION_DROP_LITRES = 3
        const val MIN_CALIBRATION_FACTOR = .7
        const val MAX_CALIBRATION_FACTOR = 1.3
        const val CALIBRATION_ADJUSTMENT_WEIGHT = .2
        const val VIRTUAL_FUEL_CORRECTION_WEIGHT = .2
        const val MAX_DISPLAY_CONSUMPTION = 15.0
    }
}
