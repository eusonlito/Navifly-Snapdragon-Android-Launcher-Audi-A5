package com.lito.a5launcher

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val MAX_PLAUSIBLE_LOCATION_SPEED_MPS = 90.0

internal fun geodesicDistanceMetres(
    firstLatitude: Double,
    firstLongitude: Double,
    secondLatitude: Double,
    secondLongitude: Double,
): Double {
    val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
    val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
    val firstLatitudeRadians = Math.toRadians(firstLatitude)
    val secondLatitudeRadians = Math.toRadians(secondLatitude)
    val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
        cos(firstLatitudeRadians) * cos(secondLatitudeRadians) *
        sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
    return EARTH_RADIUS_METRES * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
}

internal fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

internal fun shortestBearingDelta(from: Float, to: Float): Float =
    ((to - from + 540f) % 360f) - 180f

private const val EARTH_RADIUS_METRES = 6_371_000.0
