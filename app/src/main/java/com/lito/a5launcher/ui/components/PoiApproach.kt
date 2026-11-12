package com.lito.a5launcher.ui.components

import com.lito.a5launcher.geodesicDistanceMetres
import com.lito.a5launcher.location.LocationState
import com.lito.a5launcher.normalizeDegrees
import com.lito.a5launcher.shortestBearingDelta
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal data class PoiApproach(
    val name: String,
    val color: String,
    val distanceMetres: Double,
)

internal class PoiApproachIndex(snapshot: PoiSnapshot) {
    private data class Point(val latitude: Double, val longitude: Double, val name: String, val color: String)

    private val points = snapshot.sources.flatMap { source ->
        source.features.mapNotNull { feature ->
            val style = snapshot.categoryCatalog?.styles?.get(feature.category)
                ?.takeIf { it.pulseEnabled } ?: return@mapNotNull null
            val coordinates = feature.json.getJSONObject("geometry").getJSONArray("coordinates")
            val name = feature.json.getJSONObject("properties").optString("name")
                .takeIf { it.isNotBlank() } ?: feature.category
            Point(coordinates.getDouble(1), coordinates.getDouble(0), name, style.pulseColor)
        }
    }.sortedBy(Point::latitude)

    fun nearest(location: LocationState, nowElapsedMillis: Long): PoiApproach? {
        if (!location.gpsAvailable(nowElapsedMillis)) return null
        val position = location.activePosition(nowElapsedMillis) ?: return null
        val heading = position.travelBearing?.takeIf { it.isFinite() } ?: return null
        val latitudeRange = Math.toDegrees(MAX_DISTANCE_METRES / 6_371_000.0)
        val minimumLatitude = position.latitude - latitudeRange
        val maximumLatitude = position.latitude + latitudeRange
        var low = 0
        var high = points.size
        while (low < high) {
            val middle = (low + high) / 2
            if (points[middle].latitude < minimumLatitude) low = middle + 1 else high = middle
        }
        var nearest: PoiApproach? = null
        val latitude = Math.toRadians(position.latitude)
        for (index in low until points.size) {
            val point = points[index]
            if (point.latitude > maximumLatitude) break
            val distance = geodesicDistanceMetres(position.latitude, position.longitude, point.latitude, point.longitude)
            if (distance > MAX_DISTANCE_METRES || distance >= (nearest?.distanceMetres ?: Double.POSITIVE_INFINITY)) continue
            val targetLatitude = Math.toRadians(point.latitude)
            val longitudeDelta = Math.toRadians(point.longitude - position.longitude)
            val bearing = normalizeDegrees(Math.toDegrees(atan2(
                sin(longitudeDelta) * cos(targetLatitude),
                cos(latitude) * sin(targetLatitude) - sin(latitude) * cos(targetLatitude) * cos(longitudeDelta),
            )).toFloat())
            if (distance > 0.001 && abs(shortestBearingDelta(heading, bearing)) > 90f) continue
            nearest = PoiApproach(point.name, point.color, distance)
        }
        return nearest
    }

    companion object {
        private const val MAX_DISTANCE_METRES = 1_000.0
    }
}
