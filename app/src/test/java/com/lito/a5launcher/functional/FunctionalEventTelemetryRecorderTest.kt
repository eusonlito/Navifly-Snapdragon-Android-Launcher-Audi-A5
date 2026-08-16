package com.lito.a5launcher.functional

import com.lito.a5launcher.ConfirmedFuelLevelChange
import com.lito.a5launcher.CoreTelemetry
import com.lito.a5launcher.GearDecision
import com.lito.a5launcher.TripModelTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalEventTelemetryRecorderTest {
    @Test
    fun `maps refuel trip and gear decisions to typed functional events`() {
        val captured = mutableListOf<Captured>()
        val recorder = FunctionalEventTelemetryRecorder { category, type, context, source ->
            captured += Captured(category, type, context, source)
        }
        val telemetry = CoreTelemetry(12, 0, 1_700, 38, 0, null, 18.0)

        recorder.recordFuelDecision(
            ConfirmedFuelLevelChange.Refuel(38, baselineFuelLitres = 33, confirmationSamples = 2),
            partialBeforeKm = 120.5,
            partialAfterKm = 0.0,
            telemetry = telemetry,
            source = FunctionalEventSource.REPLAY,
        )
        recorder.recordTripTransitions(
            listOf(
                TripModelTransition.RefuelApplied(33.0, 38),
                TripModelTransition.CalibrationChanged(1.0, .95, 3),
            ),
            telemetry,
            FunctionalEventSource.REPLAY,
        )
        recorder.recordGearDecision(
            GearDecision.Change("2", "3", 42, 1_620, 38.57, 38.5),
            FunctionalEventSource.REPLAY,
        )

        assertEquals(
            listOf(
                FunctionalEventTypes.REFUEL_CONFIRMED,
                FunctionalEventTypes.CONSUMPTION_CALIBRATED,
                FunctionalEventTypes.GEAR_CHANGED,
            ),
            captured.map(Captured::type),
        )
        assertEquals(FunctionalEventCategory.REFUEL_AND_PARTIAL, captured[0].category)
        assertEquals(FunctionalEventValue.Integer(5L), captured[0].context["increaseLitres"])
        assertEquals(FunctionalEventValue.Decimal(.95), captured[1].context["factor"])
        assertEquals(FunctionalEventValue.Text("3"), captured[2].context["gear"])
        assertTrue(captured.all { it.source == FunctionalEventSource.REPLAY })
        assertFalse(captured.any { it.type == FunctionalEventType("refuel.applied") })
    }

    private data class Captured(
        val category: FunctionalEventCategory,
        val type: FunctionalEventType,
        val context: Map<String, FunctionalEventValue>,
        val source: FunctionalEventSource,
    )
}
