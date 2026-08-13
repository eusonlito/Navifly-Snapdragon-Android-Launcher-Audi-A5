package com.lito.a5launcher.assistant

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationValidatorTest {
    @Test
    fun acceptsNamedFiniteCoordinates() {
        val result = DestinationValidator.validate(
            NavigationDestination("Ordes", 43.076, -8.407),
        )
        assertTrue(result is DestinationValidation.Valid)
    }

    @Test
    fun rejectsOutOfRangeCoordinates() {
        val result = DestinationValidator.validate(
            NavigationDestination("Imposible", 92.0, -8.0),
        )
        assertTrue(result is DestinationValidation.Invalid)
        assertEquals(DestinationIssue.INVALID_LATITUDE, (result as DestinationValidation.Invalid).issue)
    }

    @Test
    fun requestsClarificationForAmbiguousDestination() {
        val result = DestinationValidator.validate(
            NavigationDestination("Centro", 43.0, -8.0, ambiguous = true),
        )
        assertTrue(result is DestinationValidation.NeedsClarification)
        assertEquals(DestinationIssue.AMBIGUOUS, (result as DestinationValidation.NeedsClarification).issue)
    }

    @Test
    fun rejectsBlankName() {
        val result = DestinationValidator.validate(NavigationDestination(" ", 43.0, -8.0))
        assertEquals(DestinationIssue.EMPTY_NAME, (result as DestinationValidation.Invalid).issue)
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeLongitude() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -181.0, 181.0).forEach { longitude ->
            val result = DestinationValidator.validate(NavigationDestination("Destino", 43.0, longitude))
            assertEquals(DestinationIssue.INVALID_LONGITUDE, (result as DestinationValidation.Invalid).issue)
        }
    }

    @Test
    fun acceptsCoordinateBoundaries() {
        listOf(-90.0 to -180.0, 90.0 to 180.0).forEach { (latitude, longitude) ->
            assertTrue(
                DestinationValidator.validate(NavigationDestination("Límite", latitude, longitude))
                    is DestinationValidation.Valid,
            )
        }
    }
}
