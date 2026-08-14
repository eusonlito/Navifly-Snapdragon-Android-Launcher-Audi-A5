package com.lito.a5launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationSearchRoutingTest {
    @Test
    fun nearestSearchWithoutValidatedLocationIsRejectedLocally() {
        val route = destinationSearchRoute(
            DestinationSearch("gasolinera", DestinationSearchMode.NEAREST),
            location = null,
        )

        assertTrue(route is DestinationSearchRoute.LocationUnavailable)
    }

    @Test
    fun relevanceSearchWithoutLocationStillUsesTextNavigation() {
        val route = destinationSearchRoute(
            DestinationSearch("centro de Ordes", DestinationSearchMode.RELEVANCE),
            location = null,
        )

        assertEquals(
            DestinationSearchRoute.TextNavigation("centro de Ordes"),
            route,
        )
    }

    @Test
    fun nearestSearchWithValidatedLocationIsSentToPlaces() {
        val location = KnownLocation(43.0, -8.0, 500)
        val search = DestinationSearch("gasolinera", DestinationSearchMode.NEAREST)

        val route = destinationSearchRoute(search, location)

        assertEquals(DestinationSearchRoute.NearbySearch(search, location), route)
    }
}
