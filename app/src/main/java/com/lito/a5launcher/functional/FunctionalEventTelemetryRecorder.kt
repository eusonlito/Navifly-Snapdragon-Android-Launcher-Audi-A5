package com.lito.a5launcher.functional

import com.lito.a5launcher.ConfirmedFuelLevelChange
import com.lito.a5launcher.CoreTelemetry
import com.lito.a5launcher.GearDecision
import com.lito.a5launcher.TripModelTransition

internal class FunctionalEventTelemetryRecorder(
    private val publish: (
        FunctionalEventCategory,
        FunctionalEventType,
        Map<String, FunctionalEventValue>,
        FunctionalEventSource,
    ) -> Unit,
) {
    fun recordFuelDecision(
        decision: ConfirmedFuelLevelChange?,
        partialBeforeKm: Double,
        partialAfterKm: Double,
        telemetry: CoreTelemetry,
        source: FunctionalEventSource,
    ) {
        when (decision) {
            is ConfirmedFuelLevelChange.Refuel -> publish(
                FunctionalEventCategory.REFUEL_AND_PARTIAL,
                FunctionalEventTypes.REFUEL_CONFIRMED,
                mapOf(
                    "baselineFuelLitres" to decision.baselineFuelLitres.eventValue(),
                    "observedFuelLitres" to decision.fuelLitres.eventValue(),
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
            is ConfirmedFuelLevelChange.Rejected -> publish(
                FunctionalEventCategory.REFUEL_AND_PARTIAL,
                FunctionalEventTypes.REFUEL_REJECTED,
                mapOf(
                    "reason" to FunctionalEventValue.Text(decision.reason.code),
                    "baselineFuelLitres" to decision.baselineFuelLitres.eventValue(),
                    "candidateFuelLitres" to decision.candidateFuelLitres.eventValue(),
                    "observedFuelLitres" to decision.observedFuelLitres.eventValue(),
                    "confirmationSamples" to decision.confirmationSamples.eventValue(),
                    "speedKmh" to telemetry.speed.eventValue(),
                    "rpm" to telemetry.rpm.eventValue(),
                    "partialKm" to partialAfterKm.eventValue(),
                ),
                source,
            )
            else -> Unit
        }
    }

    fun recordTripTransitions(
        transitions: List<TripModelTransition>,
        telemetry: CoreTelemetry,
        source: FunctionalEventSource,
    ) {
        transitions.forEach { transition ->
            val payload = transition.payload() ?: return@forEach
            publish(
                FunctionalEventCategory.CONSUMPTION_AND_RANGE,
                payload.first,
                payload.second + mapOf(
                    "speedKmh" to telemetry.speed.eventValue(),
                    "rpm" to telemetry.rpm.eventValue(),
                ),
                source,
            )
        }
    }

    fun recordGearDecision(decision: GearDecision?, source: FunctionalEventSource) {
        when (decision) {
            is GearDecision.Change -> publish(
                FunctionalEventCategory.GEAR_ESTIMATION,
                FunctionalEventTypes.GEAR_CHANGED,
                gearContext(decision.previousGear, decision.speedKmh, decision.rpm) +
                    mapOf("gear" to FunctionalEventValue.Text(decision.gear)) +
                    ratioContext(decision.observedRatio, decision.expectedRatio),
                source,
            )
            is GearDecision.Inconsistency -> publish(
                FunctionalEventCategory.GEAR_ESTIMATION,
                FunctionalEventTypes.GEAR_INCONSISTENCY,
                gearContext(decision.previousGear, decision.speedKmh, decision.rpm) +
                    ratioContext(decision.observedRatio, decision.expectedRatio),
                source,
            )
            null -> Unit
        }
    }

    private fun TripModelTransition.payload(): Pair<FunctionalEventType, Map<String, FunctionalEventValue>>? =
        when (this) {
            is TripModelTransition.RefuelApplied -> null
            is TripModelTransition.CalibrationChanged -> FunctionalEventTypes.CONSUMPTION_CALIBRATED to
                mapOf(
                    "previousFactor" to previousFactor.eventValue(),
                    "factor" to factor.eventValue(),
                    "observedDropLitres" to observedDropLitres.eventValue(),
                )
            is TripModelTransition.VirtualFuelCorrected -> FunctionalEventTypes.FUEL_CORRECTED to
                mapOf(
                    "previousFuelLitres" to previousFuelLitres.eventValue(),
                    "fuelLitres" to fuelLitres.eventValue(),
                )
            is TripModelTransition.RangeReferenceChanged -> FunctionalEventTypes.RANGE_REFERENCE_CHANGED to
                mapOf(
                    "previousConsumption" to previousConsumption.eventValue(),
                    "consumption" to consumption.eventValue(),
                )
            is TripModelTransition.ConsumptionLimitChanged ->
                (if (limited) {
                    FunctionalEventTypes.CONSUMPTION_LIMIT_ENTERED
                } else {
                    FunctionalEventTypes.CONSUMPTION_LIMIT_EXITED
                }) to mapOf(
                    "limited" to FunctionalEventValue.Flag(limited),
                    "rawConsumption" to rawConsumption.eventValue(),
                )
        }

    private fun gearContext(previousGear: String, speedKmh: Int, rpm: Int) = mapOf(
        "previousGear" to FunctionalEventValue.Text(previousGear),
        "speedKmh" to speedKmh.eventValue(),
        "rpm" to rpm.eventValue(),
    )

    private fun ratioContext(observed: Double?, expected: Double?) =
        observed?.let { mapOf("observedRatio" to it.eventValue()) }.orEmpty() +
            expected?.let { mapOf("expectedRatio" to it.eventValue()) }.orEmpty()

    private fun Int.eventValue(): FunctionalEventValue = FunctionalEventValue.Integer(toLong())
    private fun Double.eventValue(): FunctionalEventValue = FunctionalEventValue.Decimal(this)
}
