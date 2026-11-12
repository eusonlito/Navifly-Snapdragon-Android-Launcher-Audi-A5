package com.lito.a5launcher.location

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationValidatorTest {
    @Test
    fun availableProvidersAreRegisteredEvenWhenInitiallyDisabled() {
        assertEquals(
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
            availableLocationProviders(
                listOf(
                    LocationManager.PASSIVE_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.GPS_PROVIDER,
                ),
            ),
        )
    }

    @Test
    fun sharedValidatorRejectsInaccurateFixesAndImpossibleJumps() {
        val validator = LocationValidator()

        assertTrue(
            validator.evaluate(
                sample(provider = LocationProvider.GPS, accuracy = 51f),
                nowElapsedMillis = 1_000,
            ) is LocationDecision.Rejected,
        )
        assertTrue(
            validator.evaluate(
                sample(provider = LocationProvider.NETWORK, accuracy = 101f),
                nowElapsedMillis = 1_000,
            ) is LocationDecision.Rejected,
        )

        val accepted = validator.evaluate(
            sample(provider = LocationProvider.GPS, elapsedNanos = 1_000_000_000L),
            nowElapsedMillis = 1_000,
        )
        assertTrue(accepted is LocationDecision.Accepted)

        val jump = validator.evaluate(
            sample(
                provider = LocationProvider.GPS,
                latitude = 44.0,
                elapsedNanos = 2_000_000_000L,
            ),
            nowElapsedMillis = 2_000,
        )
        assertTrue(jump is LocationDecision.Rejected)
    }

    @Test
    fun recentGpsFixWinsOverNetworkAndBearingUsesRawGpsSamples() {
        val validator = LocationValidator(initialBearing = 350f)
        val gps = validator.evaluate(
            sample(
                provider = LocationProvider.GPS,
                bearing = 10f,
                speedMps = 5f,
            ),
            nowElapsedMillis = 1_000,
        ) as LocationDecision.Accepted

        assertEquals(357f, gps.location.bearing, 0.001f)
        assertEquals(10f, gps.location.travelBearing)

        val network = validator.evaluate(
            sample(provider = LocationProvider.NETWORK, elapsedNanos = 2_000_000_000L),
            nowElapsedMillis = 10_000,
        )
        assertEquals(LocationRejection.RECENT_GPS, (network as LocationDecision.Rejected).reason)
    }

    @Test
    fun onlyFreshRuntimeFixIsActiveForNearbySearches() {
        val persisted = LocationState(
            position = ValidatedLocation(43.0, -8.0, 0f, 0f, null),
        )
        assertEquals(null, persisted.activePosition(nowElapsedMillis = 1_000))

        val live = persisted.copy(
            position = persisted.position!!.copy(acceptedElapsedMillis = 2_000),
        )
        assertTrue(live.activePosition(nowElapsedMillis = 31_999) != null)
        assertEquals(null, live.activePosition(nowElapsedMillis = 32_001))
    }

    @Test
    fun travelHeadingRequiresMovementThenSurvivesAStopButNotNetworkPositions() {
        val validator = LocationValidator(initialBearing = 90f)
        fun accept(provider: LocationProvider, bearing: Float?, speed: Float, seconds: Long) =
            (validator.evaluate(sample(provider, bearing = bearing, speedMps = speed,
                elapsedNanos = seconds * 1_000_000_000), seconds * 1_000) as LocationDecision.Accepted).location

        assertEquals(null, accept(LocationProvider.GPS, 90f, 0f, 1).travelBearing)
        assertEquals(180f, accept(LocationProvider.GPS, 180f, 10f, 2).travelBearing)
        assertEquals(180f, accept(LocationProvider.GPS, null, 0f, 3).travelBearing)
        assertEquals(0f, accept(LocationProvider.GPS, 0f, 10f, 4).travelBearing)
        assertEquals(null, accept(LocationProvider.NETWORK, null, 0f, 21).travelBearing)
    }

    private fun sample(
        provider: LocationProvider,
        latitude: Double = 43.0,
        longitude: Double = -8.0,
        accuracy: Float? = 10f,
        bearing: Float? = null,
        speedMps: Float = 0f,
        elapsedNanos: Long = 1_000_000_000L,
    ) = LocationSample(
        provider = provider,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        bearing = bearing,
        speedMps = speedMps,
        elapsedRealtimeNanos = elapsedNanos,
    )
}
