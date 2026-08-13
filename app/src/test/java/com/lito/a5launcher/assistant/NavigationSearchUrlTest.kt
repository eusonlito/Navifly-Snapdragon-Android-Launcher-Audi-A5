package com.lito.a5launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationSearchUrlTest {
    @Test
    fun wazeSearchUsesDestinationTextAndStartsNavigation() {
        assertEquals(
            "https://waze.com/ul?q=centro%20de%20Ordes&navigate=yes",
            navigationSearchUrl(NavigationTarget.WAZE, " centro de Ordes "),
        )
    }
}
