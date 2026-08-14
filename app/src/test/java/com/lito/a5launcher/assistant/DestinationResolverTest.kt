package com.lito.a5launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import okio.Buffer

class DestinationResolverTest {
    @Test
    fun textSearchUsesCurrentPositionAndMinimumFieldMask() {
        val request = GooglePlacesDestinationResolver.buildRequest(
            apiKey = "places-secret",
            search = DestinationSearch("gasolinera", DestinationSearchMode.NEAREST),
            location = KnownLocation(42.34, -7.86, 1_000),
            localeTag = "es-ES",
        )

        assertEquals("places-secret", request.header("X-Goog-Api-Key"))
        assertEquals(
            "places.displayName,places.formattedAddress,places.location",
            request.header("X-Goog-FieldMask"),
        )
        val body = request.bodyUtf8()
        assertTrue(body.contains("\"textQuery\":\"gasolinera\""))
        assertTrue(body.contains("\"pageSize\":1"))
        assertTrue(body.contains("\"rankPreference\":\"DISTANCE\""))
        assertTrue(body.contains("\"latitude\":42.34"))
        assertTrue(!request.url.toString().contains("places-secret"))
    }

    @Test
    fun parsesVerifiedCoordinatesFromFirstGooglePlace() {
        val result = GooglePlacesDestinationResolver.parseResponse(
            """{"places":[{"id":"place-1","displayName":{"text":"Ordes"},"formattedAddress":"Ordes, A Coruña","location":{"latitude":43.076,"longitude":-8.407}}]}""",
        )

        assertTrue(result is DestinationResolution.Found)
        val destination = (result as DestinationResolution.Found).destination
        assertEquals("Ordes", destination.name)
        assertEquals(43.076, destination.latitude, 0.0)
        assertEquals(-8.407, destination.longitude, 0.0)
        assertEquals("Ordes, A Coruña", destination.address)
    }

    @Test
    fun nearestRequestCannotBeBuiltWithoutValidatedLocation() {
        assertThrows(IllegalArgumentException::class.java) {
            GooglePlacesDestinationResolver.buildRequest(
                apiKey = "places-secret",
                search = DestinationSearch("gasolinera", DestinationSearchMode.NEAREST),
                location = null,
                localeTag = "es-ES",
            )
        }
    }
}

private fun okhttp3.Request.bodyUtf8(): String = Buffer().also { body?.writeTo(it) }.readUtf8()
