package com.lito.a5launcher.functional

import com.lito.a5launcher.ConfirmedFuelLevelChange
import com.lito.a5launcher.CoreTelemetry

/**
 * Converts confirmed vehicle decisions into the small set of diagnostic events
 * currently exposed by the launcher. The publisher and journal remain generic so
 * future event types can be added without changing their storage or UI contracts.
 */
internal class FunctionalEventTelemetryRecorder(
    private val publish: (
        FunctionalEventCategory,
        FunctionalEventType,
        Map<String, FunctionalEventValue>,
        FunctionalEventSource,
    ) -> Unit,
) {
    fun recordPartialReset(
        decision: ConfirmedFuelLevelChange.Refuel,
        partialBeforeKm: Double,
        partialAfterKm: Double,
        telemetry: CoreTelemetry,
        source: FunctionalEventSource,
    ) {
        publish(
            FunctionalEventCategory.PARTIAL_RESET,
            FunctionalEventTypes.PARTIAL_RESET,
            mapOf(
                FunctionalEventContextKeys.FUEL_BEFORE_LITRES to
                    decision.baselineFuelLitres.eventValue(),
                FunctionalEventContextKeys.FUEL_AFTER_LITRES to decision.fuelLitres.eventValue(),
                "increaseLitres" to
                    (decision.fuelLitres - decision.baselineFuelLitres).eventValue(),
                "confirmationSamples" to decision.confirmationSamples.eventValue(),
                "speedKmh" to telemetry.speed.eventValue(),
                "rpm" to telemetry.rpm.eventValue(),
                "partialBeforeKm" to partialBeforeKm.eventValue(),
                "partialAfterKm" to partialAfterKm.eventValue(),
            ),
            source,
        )
    }

    private fun Int.eventValue(): FunctionalEventValue = FunctionalEventValue.Integer(toLong())
    private fun Double.eventValue(): FunctionalEventValue = FunctionalEventValue.Decimal(this)
}

internal inline fun <T> applyFuelDecisionAndRecordPartialReset(
    confirmedRefuel: Pair<ConfirmedFuelLevelChange.Refuel, Double>?,
    applyFuelDecision: () -> T,
    partialAfterKm: (T) -> Double,
    recordPartialReset: (ConfirmedFuelLevelChange.Refuel, Double, Double) -> Unit,
): T {
    val update = applyFuelDecision()
    confirmedRefuel?.let { (decision, partialBeforeKm) ->
        recordPartialReset(decision, partialBeforeKm, partialAfterKm(update))
    }
    return update
}
