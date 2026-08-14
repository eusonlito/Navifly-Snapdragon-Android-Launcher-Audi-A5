package com.lito.a5launcher.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val ACTIVE_LOCATION_MAX_AGE_MS = 30_000L
private const val RECENT_GPS_PRIORITY_MS = 15_000L
private const val MAXIMUM_IMPLIED_SPEED_MPS = 90.0
private const val LOCATION_PREFERENCES = "cockpit_map_location"

internal enum class LocationProvider { GPS, NETWORK }

internal data class LocationSample(
    val provider: LocationProvider,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val bearing: Float?,
    val speedMps: Float,
    val elapsedRealtimeNanos: Long,
)

internal data class ValidatedLocation(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val speedMps: Float,
    val acceptedElapsedMillis: Long?,
)

internal data class LocationState(
    val position: ValidatedLocation? = null,
    val lastGpsFixElapsedMillis: Long = 0L,
) {
    fun activePosition(
        nowElapsedMillis: Long,
        maximumAgeMillis: Long = ACTIVE_LOCATION_MAX_AGE_MS,
    ): ValidatedLocation? {
        val acceptedAt = position?.acceptedElapsedMillis ?: return null
        val age = (nowElapsedMillis - acceptedAt).coerceAtLeast(0L)
        return position.takeIf { age <= maximumAgeMillis }
    }

    fun gpsAvailable(nowElapsedMillis: Long): Boolean =
        lastGpsFixElapsedMillis > 0L &&
            nowElapsedMillis - lastGpsFixElapsedMillis <= ACTIVE_LOCATION_MAX_AGE_MS
}

internal enum class LocationRejection { RECENT_GPS, ACCURACY, IMPOSSIBLE_JUMP, INVALID_COORDINATES }

internal sealed interface LocationDecision {
    data class Accepted(val location: ValidatedLocation, val gpsFixElapsedMillis: Long) : LocationDecision
    data class Rejected(val reason: LocationRejection) : LocationDecision
}

internal class LocationValidator(initialBearing: Float = 0f) {
    private var lastAcceptedSample: LocationSample? = null
    private var lastGpsFixElapsedMillis = 0L
    private var filteredBearing = initialBearing

    fun evaluate(sample: LocationSample, nowElapsedMillis: Long): LocationDecision {
        if (!sample.latitude.isFinite() || sample.latitude !in -90.0..90.0 ||
            !sample.longitude.isFinite() || sample.longitude !in -180.0..180.0
        ) {
            return LocationDecision.Rejected(LocationRejection.INVALID_COORDINATES)
        }
        if (
            sample.provider == LocationProvider.NETWORK &&
            lastGpsFixElapsedMillis > 0L &&
            nowElapsedMillis - lastGpsFixElapsedMillis < RECENT_GPS_PRIORITY_MS
        ) {
            return LocationDecision.Rejected(LocationRejection.RECENT_GPS)
        }
        val maximumAccuracy = if (sample.provider == LocationProvider.GPS) 50f else 100f
        if (sample.accuracy != null && sample.accuracy > maximumAccuracy) {
            return LocationDecision.Rejected(LocationRejection.ACCURACY)
        }
        lastAcceptedSample?.let { previous ->
            val elapsedSeconds =
                (sample.elapsedRealtimeNanos - previous.elapsedRealtimeNanos).coerceAtLeast(1L) /
                    1_000_000_000.0
            val impliedSpeed = distanceMetres(previous, sample) / elapsedSeconds
            if (impliedSpeed > MAXIMUM_IMPLIED_SPEED_MPS) {
                return LocationDecision.Rejected(LocationRejection.IMPOSSIBLE_JUMP)
            }
        }
        if (
            sample.provider == LocationProvider.GPS &&
            sample.bearing != null &&
            sample.speedMps >= 2.5f
        ) {
            val delta = shortestBearingDelta(filteredBearing, sample.bearing)
            filteredBearing = normalizeDegrees(filteredBearing + delta * .35f)
        }
        lastAcceptedSample = sample
        if (sample.provider == LocationProvider.GPS) {
            lastGpsFixElapsedMillis = nowElapsedMillis
        }
        return LocationDecision.Accepted(
            location = ValidatedLocation(
                latitude = sample.latitude,
                longitude = sample.longitude,
                bearing = filteredBearing,
                speedMps = sample.speedMps,
                acceptedElapsedMillis = sample.elapsedRealtimeNanos
                    .takeIf { it > 0L }
                    ?.div(1_000_000L),
            ),
            gpsFixElapsedMillis = lastGpsFixElapsedMillis,
        )
    }

    private fun distanceMetres(first: LocationSample, second: LocationSample): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
        return 6_371_000.0 * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun shortestBearingDelta(from: Float, to: Float): Float =
        ((to - from + 540f) % 360f) - 180f

    private fun normalizeDegrees(value: Float): Float = (value % 360f + 360f) % 360f
}

internal class LocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(LOCATION_PREFERENCES, Context.MODE_PRIVATE)
    private val initialPosition = if (preferences.contains("latitude")) {
        ValidatedLocation(
            latitude = Double.fromBits(preferences.getLong("latitude", 0L)),
            longitude = Double.fromBits(preferences.getLong("longitude", 0L)),
            bearing = preferences.getFloat("bearing", 0f),
            speedMps = 0f,
            acceptedElapsedMillis = null,
        )
    } else {
        null
    }
    private val validator = LocationValidator(initialPosition?.bearing ?: 0f)
    private val _state = MutableStateFlow(LocationState(position = initialPosition))
    val state: StateFlow<LocationState> = _state.asStateFlow()
    private val observers = linkedSetOf<(String) -> Unit>()
    private var manager: LocationManager? = null
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start(diagnostic: (String) -> Unit = {}): () -> Unit {
        observers += diagnostic
        if (listener == null) {
            val locationManager = appContext.getSystemService(LocationManager::class.java)
            if (locationManager != null) {
                val createdListener = LocationListener(::accept)
                manager = locationManager
                listener = createdListener
                runCatching {
                    val enabledProviders = listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                    ).filter {
                        locationManager.allProviders.contains(it) && locationManager.isProviderEnabled(it)
                    }
                    if (_state.value.position == null) {
                        enabledProviders.mapNotNull(locationManager::getLastKnownLocation)
                            .maxByOrNull(Location::getElapsedRealtimeNanos)
                            ?.let(::accept)
                    }
                    enabledProviders.forEach {
                        locationManager.requestLocationUpdates(it, 1_000L, 2f, createdListener)
                    }
                }.onFailure {
                    emit("GPS REGISTRO ERROR | ${it.message.orEmpty()}")
                    stop(diagnostic)
                }
            }
        }
        return { stop(diagnostic) }
    }

    private fun stop(diagnostic: (String) -> Unit) {
        observers -= diagnostic
        if (observers.isNotEmpty()) return
        listener?.let { current -> runCatching { manager?.removeUpdates(current) } }
        listener = null
        manager = null
    }

    private fun accept(location: Location) {
        val provider = if (location.provider == LocationManager.GPS_PROVIDER) {
            LocationProvider.GPS
        } else {
            LocationProvider.NETWORK
        }
        val sample = LocationSample(
            provider = provider,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy.takeIf { location.hasAccuracy() },
            bearing = location.bearing.takeIf { location.hasBearing() },
            speedMps = location.speed.takeIf { location.hasSpeed() } ?: 0f,
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
        )
        when (val decision = validator.evaluate(sample, android.os.SystemClock.elapsedRealtime())) {
            is LocationDecision.Accepted -> {
                _state.value = LocationState(decision.location, decision.gpsFixElapsedMillis)
                preferences.edit {
                    putLong("latitude", decision.location.latitude.toBits())
                    putLong("longitude", decision.location.longitude.toBits())
                    putFloat("bearing", decision.location.bearing)
                }
                emit(
                    "GPS MUESTRA ACEPTADA | proveedor=${location.provider}" +
                        " | lat=${location.latitude} | lon=${location.longitude}" +
                        " | precisión=${location.accuracy}m | velocidad=${decision.location.speedMps}m/s" +
                        " | rumbo=${decision.location.bearing}"
                )
            }
            is LocationDecision.Rejected -> emit(
                "GPS MUESTRA RECHAZADA | proveedor=${location.provider} | motivo=${decision.reason.name}"
            )
        }
    }

    private fun emit(message: String) = observers.forEach { it(message) }
}
