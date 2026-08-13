package com.lito.a5launcher.ui.components

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class MapMotionSample(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val speedMps: Float,
    val elapsedRealtimeMs: Long,
)

/**
 * Converts low-frequency GPS fixes into a continuous, time-based camera position.
 *
 * Rendering deliberately trails the sensor slightly so normal fixes can be
 * interpolated. Once the newest fix is reached, movement is projected only for
 * a short bounded horizon; a missing GPS signal can therefore never create
 * indefinite dead reckoning.
 */
internal class MapMotionSmoother(
    private val interpolationDelayMs: Long = 250L,
    private val predictionHorizonMs: Long = 1_000L,
    private val correctionDurationMs: Long = 750L,
) {
    private var previous: MapMotionSample? = null
    private var latest: MapMotionSample? = null
    private var finalPrediction: MapMotionSample? = null
    private var lastRendered: MapMotionSample? = null
    private var lastRenderedAtMs: Long = 0L
    private var correction: MotionCorrection? = null

    @Synchronized
    fun add(sample: MapMotionSample) {
        finalPrediction = null
        val current = latest
        if (current == null || shouldReset(current, sample)) {
            previous = null
            latest = sample
            correction = null
            return
        }
        previous = current
        latest = sample
        val renderedBeforeFix = lastRendered ?: return
        val correctionStartMs = lastRenderedAtMs
        val uncorrectedAfterFix = rawPositionAt(correctionStartMs) ?: return
        correction = MotionCorrection(
            latitudeOffset = renderedBeforeFix.latitude - uncorrectedAfterFix.latitude,
            longitudeOffset = shortestLongitudeDelta(
                uncorrectedAfterFix.longitude,
                renderedBeforeFix.longitude,
            ),
            bearingOffset = shortestBearingDelta(
                uncorrectedAfterFix.bearing,
                renderedBeforeFix.bearing,
            ),
            speedOffset = renderedBeforeFix.speedMps - uncorrectedAfterFix.speedMps,
            startedAtMs = correctionStartMs,
        )
    }

    @Synchronized
    fun positionAt(nowElapsedRealtimeMs: Long): MapMotionSample? {
        val raw = rawPositionAt(nowElapsedRealtimeMs) ?: return null
        val activeCorrection = correction
        val rendered = if (activeCorrection == null || correctionDurationMs <= 0L) {
            raw
        } else {
            val progress = (
                (nowElapsedRealtimeMs - activeCorrection.startedAtMs).toDouble() /
                    correctionDurationMs
                ).coerceIn(0.0, 1.0)
            val remaining = 1.0 - smoothStep(progress)
            if (progress >= 1.0) correction = null
            raw.copy(
                latitude = raw.latitude + activeCorrection.latitudeOffset * remaining,
                longitude = normalizeLongitude(
                    raw.longitude + activeCorrection.longitudeOffset * remaining
                ),
                bearing = normalizeDegrees(
                    raw.bearing + activeCorrection.bearingOffset * remaining.toFloat()
                ),
                speedMps = raw.speedMps + activeCorrection.speedOffset * remaining.toFloat(),
            )
        }
        lastRendered = rendered
        lastRenderedAtMs = nowElapsedRealtimeMs
        return rendered
    }

    private fun rawPositionAt(nowElapsedRealtimeMs: Long): MapMotionSample? {
        val end = latest ?: return null
        val renderTime = nowElapsedRealtimeMs - interpolationDelayMs
        val start = previous
        if (
            start != null &&
            renderTime >= start.elapsedRealtimeMs &&
            renderTime <= end.elapsedRealtimeMs
        ) {
            val duration = (end.elapsedRealtimeMs - start.elapsedRealtimeMs).coerceAtLeast(1L)
            val fraction = (renderTime - start.elapsedRealtimeMs).toDouble() / duration
            return interpolate(start, end, fraction)
        }

        if (renderTime <= end.elapsedRealtimeMs || end.speedMps < MIN_PREDICTION_SPEED_MPS) {
            return end
        }

        val predictionMs = min(renderTime - end.elapsedRealtimeMs, predictionHorizonMs)
        if (predictionMs == predictionHorizonMs) {
            return finalPrediction ?: project(end, predictionMs / 1_000.0).also {
                finalPrediction = it
            }
        }
        return project(end, predictionMs / 1_000.0)
    }

    private fun smoothStep(progress: Double): Double =
        progress * progress * (3.0 - 2.0 * progress)

    private fun shouldReset(previous: MapMotionSample, next: MapMotionSample): Boolean {
        val elapsedMs = next.elapsedRealtimeMs - previous.elapsedRealtimeMs
        if (elapsedMs <= 0L || elapsedMs > RESET_AFTER_GAP_MS) return true
        val metresPerSecond = distanceMetres(previous, next) / (elapsedMs / 1_000.0)
        return metresPerSecond > MAX_PLAUSIBLE_SPEED_MPS
    }

    private fun interpolate(
        start: MapMotionSample,
        end: MapMotionSample,
        fraction: Double,
    ): MapMotionSample {
        val progress = fraction.coerceIn(0.0, 1.0)
        val bearingDelta = shortestBearingDelta(start.bearing, end.bearing)
        return MapMotionSample(
            latitude = start.latitude + (end.latitude - start.latitude) * progress,
            longitude = interpolateLongitude(start.longitude, end.longitude, progress),
            bearing = normalizeDegrees(start.bearing + bearingDelta * progress.toFloat()),
            speedMps = start.speedMps + (end.speedMps - start.speedMps) * progress.toFloat(),
            elapsedRealtimeMs = start.elapsedRealtimeMs +
                ((end.elapsedRealtimeMs - start.elapsedRealtimeMs) * progress).toLong(),
        )
    }

    private fun project(sample: MapMotionSample, elapsedSeconds: Double): MapMotionSample {
        val angularDistance = sample.speedMps * elapsedSeconds / EARTH_RADIUS_METRES
        val bearingRadians = Math.toRadians(sample.bearing.toDouble())
        val latitudeRadians = Math.toRadians(sample.latitude)
        val longitudeRadians = Math.toRadians(sample.longitude)
        val projectedLatitude = asin(
            sin(latitudeRadians) * cos(angularDistance) +
                cos(latitudeRadians) * sin(angularDistance) * cos(bearingRadians)
        )
        val projectedLongitude = longitudeRadians + atan2(
            sin(bearingRadians) * sin(angularDistance) * cos(latitudeRadians),
            cos(angularDistance) - sin(latitudeRadians) * sin(projectedLatitude),
        )
        return sample.copy(
            latitude = Math.toDegrees(projectedLatitude),
            longitude = normalizeLongitude(Math.toDegrees(projectedLongitude)),
            elapsedRealtimeMs = sample.elapsedRealtimeMs + (elapsedSeconds * 1_000).toLong(),
        )
    }

    private fun distanceMetres(first: MapMotionSample, second: MapMotionSample): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return 2 * EARTH_RADIUS_METRES * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun interpolateLongitude(start: Double, end: Double, progress: Double): Double {
        return normalizeLongitude(start + shortestLongitudeDelta(start, end) * progress)
    }

    private fun shortestLongitudeDelta(start: Double, end: Double): Double {
        var delta = end - start
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    private fun normalizeLongitude(value: Double): Double =
        ((value + 540.0) % 360.0) - 180.0

    private companion object {
        const val EARTH_RADIUS_METRES = 6_371_000.0
        const val MIN_PREDICTION_SPEED_MPS = .8f
        const val MAX_PLAUSIBLE_SPEED_MPS = 90.0
        const val RESET_AFTER_GAP_MS = 10_000L
    }

    private data class MotionCorrection(
        val latitudeOffset: Double,
        val longitudeOffset: Double,
        val bearingOffset: Float,
        val speedOffset: Float,
        val startedAtMs: Long,
    )
}
