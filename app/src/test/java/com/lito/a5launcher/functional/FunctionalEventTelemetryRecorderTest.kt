package com.lito.a5launcher.functional

import com.lito.a5launcher.ConfirmedFuelLevelChange
import com.lito.a5launcher.CoreTelemetry
import com.lito.a5launcher.PartialMaximumSpeedChange
import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionalEventTelemetryRecorderTest {
    @Test
    fun `records only a confirmed partial reset with fuel values before and after`() {
        val captured = mutableListOf<Captured>()
        val recorder = FunctionalEventTelemetryRecorder { category, type, context, source ->
            captured += Captured(category, type, context, source)
        }
        val telemetry = CoreTelemetry(12, 0, 1_700, 38, 0, null, 18.0)

        recorder.recordPartialReset(
            ConfirmedFuelLevelChange.Refuel(38, baselineFuelLitres = 33, confirmationSamples = 2),
            partialBeforeKm = 120.5,
            partialAfterKm = 0.0,
            telemetry = telemetry,
            source = FunctionalEventSource.REPLAY,
        )

        assertEquals(1, captured.size)
        val event = captured.single()
        assertEquals(FunctionalEventTypes.PARTIAL_RESET, event.type)
        assertEquals(FunctionalEventCategory.PARTIAL_RESET, event.category)
        assertEquals(FunctionalEventValue.Integer(33L), event.context["fuelBeforeLitres"])
        assertEquals(FunctionalEventValue.Integer(38L), event.context["fuelAfterLitres"])
        assertEquals(FunctionalEventValue.Integer(5L), event.context["increaseLitres"])
        assertEquals(FunctionalEventValue.Decimal(120.5), event.context["partialBeforeKm"])
        assertEquals(FunctionalEventValue.Decimal(0.0), event.context["partialAfterKm"])
        assertEquals(FunctionalEventSource.REPLAY, event.source)
    }

    @Test
    fun `records a new partial maximum with the previous value and driving context`() {
        val captured = mutableListOf<Captured>()
        val recorder = FunctionalEventTelemetryRecorder { category, type, context, source ->
            captured += Captured(category, type, context, source)
        }
        val telemetry = CoreTelemetry(121, 0, 2_300, 38, 0, null, 18.0)

        recorder.recordPartialMaximumSpeed(
            maximumSpeedChange = PartialMaximumSpeedChange(118, 121),
            partialKm = 84.5,
            telemetry = telemetry,
            source = FunctionalEventSource.EVENT_CENTER,
        )

        val event = captured.single()
        assertEquals(FunctionalEventCategory.MAXIMUM_SPEED, event.category)
        assertEquals(FunctionalEventTypes.PARTIAL_MAXIMUM_SPEED, event.type)
        assertEquals(
            FunctionalEventValue.Integer(118),
            event.context[FunctionalEventContextKeys.PREVIOUS_MAXIMUM_SPEED_KMH],
        )
        assertEquals(
            FunctionalEventValue.Integer(121),
            event.context[FunctionalEventContextKeys.MAXIMUM_SPEED_KMH],
        )
        assertEquals(FunctionalEventValue.Decimal(84.5), event.context["partialKm"])
        assertEquals(FunctionalEventValue.Integer(2_300), event.context["rpm"])
    }

    private data class Captured(
        val category: FunctionalEventCategory,
        val type: FunctionalEventType,
        val context: Map<String, FunctionalEventValue>,
        val source: FunctionalEventSource,
    )
}
