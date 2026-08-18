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
                evidenceLitres = 9.0,
            ),
        )

        assertEquals(
            ConsumptionCalibrationState(1.45, 38, 1.75, 9.0),
            ConsumptionCalibrationStore(preferences).read(),
        )

        ConsumptionCalibrationStore(preferences).write(
            ConsumptionCalibrationState(factor = 1.2, anchorFuelLitres = null),
        )

        assertNull(ConsumptionCalibrationStore(preferences).read().anchorFuelLitres)
    }

    @Test
    fun schemaOneCalibrationRestoresWithoutInventingEvidence() {
        assertEquals(
            ConsumptionCalibrationState(
                factor = 1.2,
                anchorFuelLitres = 40,
                uncalibratedFuelLitres = 1.5,
                evidenceLitres = 0.0,
            ),
            restoreConsumptionCalibration(
                storedSchema = 1,
                storedFactor = 1.2,
                storedAnchorFuelLitres = 40,
                storedUncalibratedFuelLitres = 1.5,
                storedEvidenceLitres = 20.0,
            ),
        )
    }
}
