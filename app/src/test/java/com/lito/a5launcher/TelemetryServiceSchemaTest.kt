package com.lito.a5launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryServiceSchemaTest {

    @Test
    fun currentTripStateAcceptsTheProductionSchemaDuringUpgrade() {
        assertTrue(isCompatibleTripSchema(2))
        assertTrue(isCompatibleTripSchema(3))
        assertFalse(isCompatibleTripSchema(1))
        assertFalse(isCompatibleTripSchema(4))
    }
}
