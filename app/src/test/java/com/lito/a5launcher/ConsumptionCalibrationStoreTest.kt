package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsumptionCalibrationStoreTest {

    @Test
    fun calibrationRoundTripPersistsAcrossStoreRecreationAndRemovesAStaleAnchor() {
        val preferences = MemorySharedPreferences()
        ConsumptionCalibrationStore(preferences).write(
            ConsumptionCalibrationState(
                factor = 1.45,
                anchorFuelLitres = 38,
                uncalibratedFuelLitres = 1.75,
            ),
        )

        assertEquals(
            ConsumptionCalibrationState(1.45, 38, 1.75),
            ConsumptionCalibrationStore(preferences).read(),
        )

        ConsumptionCalibrationStore(preferences).write(
            ConsumptionCalibrationState(factor = 1.2, anchorFuelLitres = null),
        )

        assertNull(ConsumptionCalibrationStore(preferences).read().anchorFuelLitres)
    }
}
