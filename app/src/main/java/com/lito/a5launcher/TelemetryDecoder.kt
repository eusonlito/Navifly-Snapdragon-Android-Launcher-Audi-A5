package com.lito.a5launcher

import com.lito.a5launcher.model.DoorStatus
import kotlin.math.pow

internal fun Double.validMetric(): Double = takeIf { isFinite() && this >= 0.0 } ?: 0.0
internal const val DEFAULT_RANGE_CONSUMPTION_L_PER_100_KM = 6.0
internal const val MAX_DISPLAY_CONSUMPTION = 15.0
internal const val MAX_IDLE_SPEED_KMH = 1

internal fun cappedConsumption(consumptionLitresPer100Km: Double): Double =
    consumptionLitresPer100Km.takeIf { it.isFinite() }
        ?.coerceIn(0.0, MAX_DISPLAY_CONSUMPTION) ?: 0.0

internal fun cappedConsumption(fuelLitres: Double, distanceKm: Double): Double =
    if (distanceKm > 0.0) cappedConsumption(fuelLitres / distanceKm * 100.0) else 0.0

data class CoreTelemetry(
    val speed: Int,
    val averageSpeed: Int,
    val rpm: Int,
    val fuelLitres: Int,
    val unverifiedDistanceKm: Int,
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
            unverifiedDistanceKm = unsigned16(bytes, 5),
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
            hoodOpen = flags and 0x08 != 0,
            trunkOpen = flags and 0x04 != 0,
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

    fun expectedRatio(gear: String): Double? = gear.toIntOrNull()
        ?.takeIf { it in 1..A5_20_TDI_MANUAL_RATIOS.size }
        ?.let { A5_20_TDI_MANUAL_RATIOS[it - 1] }

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
    private var currentGear = "N"
    private var inconsistent = false

    fun update(sample: DrivingSample): String = updateDetailed(sample).gear

    fun updateDetailed(sample: DrivingSample): GearTelemetryUpdate {
        val previous = currentGear
        val next = estimator.update(sample.rawGearType, sample.speed, sample.rpm)
        currentGear = next
        val ratio = sample.speed.takeIf { it > 0 }?.let { sample.rpm.toDouble() / it }
        val recognizedInconsistency = sample.rawGearType !in setOf(1, 2, 3, 13, 14, 15) &&
            sample.speed >= 4 && sample.rpm >= 900 && next == "N" && previous != "N"
        val transition = when {
            recognizedInconsistency && !inconsistent -> GearDecision.Inconsistency(
                previousGear = previous,
                speedKmh = sample.speed,
                rpm = sample.rpm,
                observedRatio = ratio,
                expectedRatio = estimator.expectedRatio(previous),
            )
            next != previous -> GearDecision.Change(
                previousGear = previous,
                gear = next,
                speedKmh = sample.speed,
                rpm = sample.rpm,
                observedRatio = ratio,
                expectedRatio = estimator.expectedRatio(next),
            )
            else -> null
        }
        if (next != "N" || sample.rawGearType in setOf(1, 2, 3, 13, 14, 15)) inconsistent = false
        if (recognizedInconsistency) inconsistent = true
        return GearTelemetryUpdate(next, transition)
    }
}

data class GearTelemetryUpdate(val gear: String, val transition: GearDecision?)

sealed interface GearDecision {
    data class Change(
        val previousGear: String,
        val gear: String,
        val speedKmh: Int,
        val rpm: Int,
        val observedRatio: Double?,
        val expectedRatio: Double?,
    ) : GearDecision

    data class Inconsistency(
        val previousGear: String,
        val speedKmh: Int,
        val rpm: Int,
        val observedRatio: Double?,
        val expectedRatio: Double?,
    ) : GearDecision
}

object TripConsumptionEstimator {
    fun fuelFlowLitresPerHour(speed: Int, rpm: Int): Double {
        if (rpm <= ENGINE_OFF_RPM) return 0.0
        if (speed <= MOVING_SPEED_KMH) return IDLE_FUEL_FLOW_LPH
        val rpmFactor = rpm.toDouble() / RPM_REFERENCE
        val speedDrag = if (speed > AERODYNAMIC_SPEED_THRESHOLD_KMH) {
            (speed - AERODYNAMIC_SPEED_THRESHOLD_KMH) * AERODYNAMIC_CONSUMPTION_PER_KMH
        } else 0.0
        val provisionalConsumption = BASE_CONSUMPTION_L_PER_100_KM + rpmFactor + speedDrag
        val consumptionLitresPer100Km = highSpeedReference(speed, rpm)?.let { reference ->
            val referenceWeight = (
                (speed - AERODYNAMIC_SPEED_THRESHOLD_KMH).toDouble() / REFERENCE_BLEND_DISTANCE_KMH
                ).coerceIn(0.0, 1.0) * MAX_REFERENCE_WEIGHT
            maxOf(
                provisionalConsumption,
                provisionalConsumption + (reference - provisionalConsumption) * referenceWeight,
            )
        } ?: provisionalConsumption
        return maxOf(
            IDLE_FUEL_FLOW_LPH,
            consumptionLitresPer100Km * speed / 100.0,
        )
    }

    private fun highSpeedReference(speed: Int, rpm: Int): Double? {
        if (speed <= AERODYNAMIC_SPEED_THRESHOLD_KMH) return null
        val boundedSpeed = speed.coerceAtMost(REFERENCE_POINTS.last().speedKmh)
        val upperIndex = REFERENCE_POINTS.indexOfFirst { boundedSpeed <= it.speedKmh }
            .coerceAtLeast(1)
        val lower = REFERENCE_POINTS[upperIndex - 1]
        val upper = REFERENCE_POINTS[upperIndex]
        val interpolation = (boundedSpeed - lower.speedKmh).toDouble() /
            (upper.speedKmh - lower.speedKmh)
        val baseConsumption = lower.consumptionLitresPer100Km +
            (upper.consumptionLitresPer100Km - lower.consumptionLitresPer100Km) * interpolation
        val sixthGearRpm = 1_000.0 * speed / SIXTH_GEAR_KMH_PER_1_000_RPM
        val regimeRatio = (rpm / sixthGearRpm).coerceIn(MIN_REGIME_RATIO, MAX_REGIME_RATIO)
        return baseConsumption * regimeRatio.pow(REGIME_EXPONENT)
    }

    private const val ENGINE_OFF_RPM = 500
    private const val MOVING_SPEED_KMH = 1
    private const val IDLE_FUEL_FLOW_LPH = .7
    private const val BASE_CONSUMPTION_L_PER_100_KM = 3.2
    private const val RPM_REFERENCE = 1_800.0
    private const val AERODYNAMIC_SPEED_THRESHOLD_KMH = 110
    private const val AERODYNAMIC_CONSUMPTION_PER_KMH = .02
    private const val REFERENCE_BLEND_DISTANCE_KMH = 20.0
    private const val MAX_REFERENCE_WEIGHT = .35
    private const val SIXTH_GEAR_KMH_PER_1_000_RPM = 54.798
    private const val REGIME_EXPONENT = .22
    private const val MIN_REGIME_RATIO = .75
    private const val MAX_REGIME_RATIO = 1.6
    private val REFERENCE_POINTS = arrayOf(
        ReferencePoint(110, 4.8),
        ReferencePoint(120, 5.6),
        ReferencePoint(130, 6.67),
        ReferencePoint(140, 7.73),
        ReferencePoint(150, 8.8),
        ReferencePoint(160, 10.07),
        ReferencePoint(170, 11.33),
        ReferencePoint(180, 12.6),
        ReferencePoint(190, 14.13),
        ReferencePoint(200, 15.67),
        ReferencePoint(210, 17.2),
        ReferencePoint(212, 17.5),
    )

    private data class ReferencePoint(
        val speedKmh: Int,
        val consumptionLitresPer100Km: Double,
    )
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

    fun add(distanceKm: Double, fuelLitres: Double): List<Pair<Double, Double>> {
        var changes: MutableList<Pair<Double, Double>>? = null
        var remainingDistanceKm = distanceKm.validMetric()
        var remainingFuelLitres = fuelLitres.validMetric()
        if (remainingDistanceKm <= 0.0) {
            pendingSegmentFuelLitres += remainingFuelLitres
            return emptyList()
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
                learnCompletedSegment()?.let { change ->
                    val target = changes ?: mutableListOf<Pair<Double, Double>>().also {
                        changes = it
                    }
                    target += change
                }
            }
        }
        return changes.orEmpty()
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

    private fun learnCompletedSegment(): Pair<Double, Double>? {
        val previous = learnedConsumption
        val observed = (pendingSegmentFuelLitres / pendingSegmentDistanceKm * 100.0)
            .takeIf { it.isFinite() }
            ?.coerceIn(MIN_CONSUMPTION, MAX_CONSUMPTION)
            ?: learnedConsumption
        learnedConsumption = (
            learnedConsumption * (1.0 - LEARNING_WEIGHT) + observed * LEARNING_WEIGHT
            ).coerceIn(MIN_CONSUMPTION, MAX_CONSUMPTION)
        pendingSegmentDistanceKm = 0.0
        pendingSegmentFuelLitres = 0.0
        return (previous to learnedConsumption).takeIf {
            kotlin.math.abs(it.second - it.first) >= MATERIAL_LEARNING_CHANGE
        }
    }

    private companion object {
        const val DEFAULT_CONSUMPTION = DEFAULT_RANGE_CONSUMPTION_L_PER_100_KM
        const val MIN_CONSUMPTION = 3.0
        const val MAX_CONSUMPTION = 15.0
        const val LEARNING_SEGMENT_KM = 1.0
        const val LEARNING_WEIGHT = .1
        const val FULL_RECENT_INFLUENCE_KM = 10.0
        const val MAX_RECENT_WEIGHT = .6
        const val MATERIAL_LEARNING_CHANGE = .001
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

data class TripMetricsSnapshot(
    val startedAtElapsedMs: Long?,
    val elapsedMs: Long,
    val distanceKm: Double,
    val averageConsumption: Double,
    val recentConsumption: Double,
    val fuelUsedLitres: Double,
    val confirmedCanFuelUsedLitres: Double,
    val observedCanConsumption: Double,
    val virtualFuelLitres: Double,
    val estimatedRangeKm: Int,
    val movingElapsedMs: Long,
    val maximumSpeedKmh: Int,
    val initialObservedFuelLitres: Int?,
    val currentObservedFuelLitres: Int?,
) {
    val statistics: JourneyStatisticsSnapshot
        get() = journeyStatistics(
            elapsedMs = elapsedMs,
            movingElapsedMs = movingElapsedMs,
            distanceKm = distanceKm,
            maximumSpeedKmh = maximumSpeedKmh,
            fuelUsedLitres = fuelUsedLitres,
            confirmedCanFuelUsedLitres = confirmedCanFuelUsedLitres,
            calculatedConsumption = averageConsumption,
            observedCanConsumption = observedCanConsumption,
            initialObservedFuelLitres = initialObservedFuelLitres,
            currentObservedFuelLitres = currentObservedFuelLitres,
        )
}

data class TripSessionState(
    val startedAtElapsedMs: Long? = null,
    val distanceKm: Double = 0.0,
    val fuelUsedLitres: Double = 0.0,
    val confirmedCanFuelUsedLitres: Double = 0.0,
    val virtualFuelLitres: Double = 0.0,
    val calibrationFactor: Double = 1.0,
    val lastFuelLitres: Int? = null,
    val calibrationAnchorFuelLitres: Int? = null,
    val uncalibratedFuelSinceAnchorLitres: Double = 0.0,
    val recentConsumptionState: RecentConsumptionState = RecentConsumptionState(),
    val rangeConsumptionState: RangeConsumptionState = RangeConsumptionState(),
    val rangeBaselineConsumption: Double? = null,
    val movingElapsedMs: Long = 0L,
    val maximumSpeedKmh: Int = 0,
    val initialObservedFuelLitres: Int? = null,
    val currentObservedFuelLitres: Int? = null,
)

class TripSessionTracker(
    initialState: TripSessionState = TripSessionState(),
    private val refuelDetector: ConfirmedRefuelDetector? =
        ConfirmedRefuelDetector(initialState.lastFuelLitres),
) {
    private var startedAtElapsedMs = initialState.startedAtElapsedMs?.takeIf { it >= 0L }
    private var lastElapsedRealtimeMs: Long? = null
    private var speedKmh = 0
    private var rpm = 0
    private var lastTelemetryElapsedMs: Long? = null
    private var distanceKm = initialState.distanceKm.validMetric()
    private var fuelUsedLitres = initialState.fuelUsedLitres.validMetric()
    private var confirmedCanFuelUsedLitres = initialState.confirmedCanFuelUsedLitres.validMetric()
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
    private var movingElapsedMs = initialState.movingElapsedMs.coerceAtLeast(0L)
    private var maximumSpeedKmh = initialState.maximumSpeedKmh.coerceAtLeast(0)
    private var initialObservedFuelLitres = initialState.initialObservedFuelLitres
        ?.takeIf { it > 0 }
    private var currentObservedFuelLitres = initialState.currentObservedFuelLitres
        ?.takeIf { it > 0 }

    @Synchronized
    fun onTelemetry(
        speedKmh: Int,
        rpm: Int,
        fuelLitres: Int,
        elapsedRealtimeMs: Long,
    ): TripMetricsSnapshot {
        val fuelDecision = refuelDetector?.observeDetailed(speedKmh, fuelLitres)
        return onTelemetryWithFuelDecision(
            speedKmh,
            rpm,
            fuelLitres,
            elapsedRealtimeMs,
            fuelDecision,
        )
    }

    @Synchronized
    fun onTelemetryWithFuelDecision(
        speedKmh: Int,
        rpm: Int,
        fuelLitres: Int,
        elapsedRealtimeMs: Long,
        fuelDecision: ConfirmedFuelLevelChange?,
    ): TripMetricsSnapshot {
        advanceTo(elapsedRealtimeMs)
        this.speedKmh = speedKmh.coerceAtLeast(0)
        this.rpm = rpm.coerceAtLeast(0)
        maximumSpeedKmh = maxOf(maximumSpeedKmh, this.speedKmh)
        lastTelemetryElapsedMs = elapsedRealtimeMs
        if (startedAtElapsedMs == null && this.speedKmh > 0) {
            startedAtElapsedMs = elapsedRealtimeMs
            persistenceVersion++
        }
        observeFuelLevel(fuelLitres, fuelDecision)
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
        if (speedKmh > MAX_IDLE_SPEED_KMH) movingElapsedMs += elapsedMs
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

    private fun observeFuelLevel(
        fuelLitres: Int,
        fuelLevelChange: ConfirmedFuelLevelChange?,
    ) {
        if (fuelLitres <= 0) return
        var changed = false
        if (initialObservedFuelLitres == null) {
            initialObservedFuelLitres = fuelLitres
            changed = true
        }
        if (currentObservedFuelLitres != fuelLitres) {
            currentObservedFuelLitres = fuelLitres
            changed = true
        }
        if (virtualFuelLitres <= 0.0) {
            virtualFuelLitres = fuelLitres.toDouble()
            changed = true
        }
        if (calibrationAnchorFuelLitres == null) {
            calibrationAnchorFuelLitres = fuelLitres
            changed = true
        }

        when (fuelLevelChange) {
            ConfirmedFuelLevelChange.Initialized -> {
                lastFuelLitres = fuelLitres
                changed = true
            }
            is ConfirmedFuelLevelChange.Drop -> {
                confirmedCanFuelUsedLitres += fuelLevelChange.litres
                lastFuelLitres = lastFuelLitres?.minus(fuelLevelChange.litres)
                changed = true
            }
            is ConfirmedFuelLevelChange.Refuel -> {
                // Keep the raw Trip fuel accounting continuous across refuelling.
                // Partial statistics reset separately to the new tank level.
                initialObservedFuelLitres = initialObservedFuelLitres?.plus(
                    fuelLevelChange.fuelLitres - fuelLevelChange.baselineFuelLitres,
                ) ?: fuelLevelChange.fuelLitres
                virtualFuelLitres = fuelLevelChange.fuelLitres.toDouble()
                lastFuelLitres = fuelLevelChange.fuelLitres
                calibrationAnchorFuelLitres = fuelLevelChange.fuelLitres
                uncalibratedFuelSinceAnchorLitres = 0.0
                changed = true
            }
            is ConfirmedFuelLevelChange.Rejected -> Unit
            null -> Unit
        }
        refuelDetector?.baselineFuelLitres()?.let { lastFuelLitres = it }
        if (fuelLevelChange !is ConfirmedFuelLevelChange.Refuel) {
            val anchor = calibrationAnchorFuelLitres
            val confirmedFuelLitres = lastFuelLitres
            val observedDrop = if (anchor != null && confirmedFuelLitres != null) {
                anchor - confirmedFuelLitres
            } else 0
            if (
                confirmedFuelLitres != null &&
                observedDrop >= CALIBRATION_DROP_LITRES &&
                uncalibratedFuelSinceAnchorLitres > 0.0
            ) {
                val observedFactor = (observedDrop / uncalibratedFuelSinceAnchorLitres)
                    .coerceIn(MIN_CALIBRATION_FACTOR, MAX_CALIBRATION_FACTOR)
                calibrationFactor = (
                    calibrationFactor * (1.0 - CALIBRATION_ADJUSTMENT_WEIGHT) +
                        observedFactor * CALIBRATION_ADJUSTMENT_WEIGHT
                    ).coerceIn(MIN_CALIBRATION_FACTOR, MAX_CALIBRATION_FACTOR)
                virtualFuelLitres +=
                    (confirmedFuelLitres - virtualFuelLitres) * VIRTUAL_FUEL_CORRECTION_WEIGHT
                calibrationAnchorFuelLitres = confirmedFuelLitres
                uncalibratedFuelSinceAnchorLitres = 0.0
                changed = true
            }
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
            confirmedCanFuelUsedLitres = confirmedCanFuelUsedLitres,
            observedCanConsumption = if (distanceKm > 0.0) {
                (confirmedCanFuelUsedLitres / distanceKm * 100.0).validMetric()
            } else 0.0,
            virtualFuelLitres = virtualFuelLitres,
            estimatedRangeKm = if (virtualFuelLitres > 0.0) {
                kotlin.math.round(virtualFuelLitres / rangeConsumptionEstimate * 100.0).toInt()
            } else 0,
            movingElapsedMs = movingElapsedMs,
            maximumSpeedKmh = maximumSpeedKmh,
            initialObservedFuelLitres = initialObservedFuelLitres,
            currentObservedFuelLitres = currentObservedFuelLitres,
        )
    }

    @Synchronized
    fun persistenceVersion(): Long = persistenceVersion

    @Synchronized
    fun state(): TripSessionState = TripSessionState(
        startedAtElapsedMs = startedAtElapsedMs,
        distanceKm = distanceKm,
        fuelUsedLitres = fuelUsedLitres,
        confirmedCanFuelUsedLitres = confirmedCanFuelUsedLitres,
        virtualFuelLitres = virtualFuelLitres,
        calibrationFactor = calibrationFactor,
        lastFuelLitres = lastFuelLitres,
        calibrationAnchorFuelLitres = calibrationAnchorFuelLitres,
        uncalibratedFuelSinceAnchorLitres = uncalibratedFuelSinceAnchorLitres,
        recentConsumptionState = recentConsumption.state(),
        rangeConsumptionState = rangeEstimator.state(),
        rangeBaselineConsumption = rangeEstimator.baselineConsumption(),
        movingElapsedMs = movingElapsedMs,
        maximumSpeedKmh = maximumSpeedKmh,
        initialObservedFuelLitres = initialObservedFuelLitres,
        currentObservedFuelLitres = currentObservedFuelLitres,
    )

    private companion object {
        const val MAX_INTEGRATION_INTERVAL_MS = 30_000L
        const val TELEMETRY_FRESHNESS_MS = 2_000L
        const val CALIBRATION_DROP_LITRES = 3
        const val MIN_CALIBRATION_FACTOR = .7
        const val MAX_CALIBRATION_FACTOR = 1.3
        const val CALIBRATION_ADJUSTMENT_WEIGHT = .2
        const val VIRTUAL_FUEL_CORRECTION_WEIGHT = .2
    }
}

internal fun journeyStatistics(
    elapsedMs: Long,
    movingElapsedMs: Long,
    distanceKm: Double,
    maximumSpeedKmh: Int,
    fuelUsedLitres: Double,
    confirmedCanFuelUsedLitres: Double,
    calculatedConsumption: Double = cappedConsumption(fuelUsedLitres, distanceKm),
    observedCanConsumption: Double = if (distanceKm > 0.0) {
        confirmedCanFuelUsedLitres.validMetric() / distanceKm.validMetric() * 100.0
    } else 0.0,
    initialObservedFuelLitres: Int? = null,
    currentObservedFuelLitres: Int? = null,
): JourneyStatisticsSnapshot {
    val safeDistanceKm = distanceKm.validMetric()
    val safeElapsedMs = elapsedMs.coerceAtLeast(0L)
    val safeMovingElapsedMs = movingElapsedMs.coerceAtLeast(0L)
    return JourneyStatisticsSnapshot(
        elapsedMs = safeElapsedMs,
        movingElapsedMs = safeMovingElapsedMs,
        distanceKm = safeDistanceKm,
        maximumSpeedKmh = maximumSpeedKmh.coerceAtLeast(0),
        averageSpeedKmh = if (safeElapsedMs > 0L) {
            safeDistanceKm / (safeElapsedMs / 3_600_000.0)
        } else 0.0,
        movingAverageSpeedKmh = if (safeMovingElapsedMs > 0L) {
            safeDistanceKm / (safeMovingElapsedMs / 3_600_000.0)
        } else 0.0,
        calculatedConsumption = calculatedConsumption.validMetric(),
        observedCanConsumption = observedCanConsumption.validMetric(),
        fuelUsedLitres = fuelUsedLitres.validMetric(),
        confirmedCanFuelUsedLitres = confirmedCanFuelUsedLitres.validMetric(),
        observedFuelSpentLitres = observedFuelSpent(
            initialObservedFuelLitres,
            currentObservedFuelLitres,
        ),
    )
}

internal fun observedFuelSpent(initialFuelLitres: Int?, currentFuelLitres: Int?): Double? {
    val initial = initialFuelLitres?.takeIf { it > 0 } ?: return null
    val current = currentFuelLitres?.takeIf { it > 0 } ?: return null
    return (initial - current).coerceAtLeast(0).toDouble()
}
