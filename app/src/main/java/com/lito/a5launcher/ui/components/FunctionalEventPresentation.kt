package com.lito.a5launcher.ui.components

import androidx.annotation.StringRes
import com.lito.a5launcher.R
import com.lito.a5launcher.functional.FunctionalEventCategory
import com.lito.a5launcher.functional.FunctionalEventContextKeys
import com.lito.a5launcher.functional.FunctionalEventType
import com.lito.a5launcher.functional.FunctionalEventTypes

internal object FunctionalEventPresentation {
    @StringRes
    fun categoryLabelRes(category: FunctionalEventCategory): Int = when (category) {
        FunctionalEventCategory.REFUEL_AND_PARTIAL ->
            R.string.functional_logs_category_refuel_legacy
        FunctionalEventCategory.PARTIAL_RESET ->
            R.string.functional_logs_category_refuel_partial
        FunctionalEventCategory.TRIP_SESSION ->
            R.string.functional_logs_category_trip_session
        FunctionalEventCategory.CONSUMPTION_AND_RANGE ->
            R.string.functional_logs_category_consumption_range
        FunctionalEventCategory.GEAR_ESTIMATION ->
            R.string.functional_logs_category_gear_estimation
    }

    @StringRes
    fun summaryRes(type: FunctionalEventType): Int = when (type) {
        FunctionalEventTypes.PARTIAL_RESET,
        FunctionalEventTypes.REFUEL_CONFIRMED,
        -> R.string.functional_logs_summary_refuel_confirmed
        FunctionalEventTypes.REFUEL_REJECTED -> R.string.functional_logs_summary_refuel_rejected
        FunctionalEventTypes.TRIP_RESTORED -> R.string.functional_logs_summary_trip_restored
        FunctionalEventTypes.TRIP_RESET -> R.string.functional_logs_summary_trip_reset
        FunctionalEventTypes.CONSUMPTION_CALIBRATED ->
            R.string.functional_logs_summary_consumption_calibrated
        FunctionalEventTypes.FUEL_CORRECTED -> R.string.functional_logs_summary_fuel_corrected
        FunctionalEventTypes.RANGE_REFERENCE_CHANGED ->
            R.string.functional_logs_summary_range_changed
        FunctionalEventTypes.CONSUMPTION_LIMIT_ENTERED ->
            R.string.functional_logs_summary_limit_entered
        FunctionalEventTypes.CONSUMPTION_LIMIT_EXITED ->
            R.string.functional_logs_summary_limit_exited
        FunctionalEventTypes.GEAR_CHANGED -> R.string.functional_logs_summary_gear_changed
        FunctionalEventTypes.GEAR_INCONSISTENCY ->
            R.string.functional_logs_summary_gear_inconsistency
        else -> R.string.functional_logs_summary_unknown
    }

    @StringRes
    fun contextLabelRes(key: String): Int? = when (key) {
        "reason" -> R.string.functional_logs_context_reason
        "restored" -> R.string.functional_logs_context_restored
        "speedKmh" -> R.string.functional_logs_context_speed
        "rpm" -> R.string.functional_logs_context_rpm
        FunctionalEventContextKeys.FUEL_BEFORE_LITRES -> R.string.functional_logs_context_fuel_before
        FunctionalEventContextKeys.FUEL_AFTER_LITRES -> R.string.functional_logs_context_fuel_after
        "fuelLitres", "observedFuelLitres" -> R.string.functional_logs_context_fuel
        "previousFuelLitres" -> R.string.functional_logs_context_previous_fuel
        "fuelBaselineLitres", "baselineFuelLitres" ->
            R.string.functional_logs_context_baseline_fuel
        "candidateFuelLitres" -> R.string.functional_logs_context_candidate_fuel
        "increaseLitres" -> R.string.functional_logs_context_increase
        "confirmationSamples" -> R.string.functional_logs_context_confirmation_samples
        "partialBeforeKm" -> R.string.functional_logs_context_partial_before
        "partialAfterKm" -> R.string.functional_logs_context_partial_after
        "partialKm" -> R.string.functional_logs_context_partial
        "storedDistanceKm" -> R.string.functional_logs_context_stored_distance
        "appliedDistanceKm" -> R.string.functional_logs_context_applied_distance
        "storedFuelUsedLitres" -> R.string.functional_logs_context_stored_fuel_used
        "appliedFuelUsedLitres" -> R.string.functional_logs_context_applied_fuel_used
        "previousFactor" -> R.string.functional_logs_context_previous_factor
        "factor" -> R.string.functional_logs_context_factor
        "observedDropLitres" -> R.string.functional_logs_context_observed_drop
        "previousConsumption" -> R.string.functional_logs_context_previous_consumption
        "consumption" -> R.string.functional_logs_context_consumption
        "limited" -> R.string.functional_logs_context_limited
        "rawConsumption" -> R.string.functional_logs_context_raw_consumption
        "previousGear" -> R.string.functional_logs_context_previous_gear
        "gear" -> R.string.functional_logs_context_gear
        "observedRatio" -> R.string.functional_logs_context_observed_ratio
        "expectedRatio" -> R.string.functional_logs_context_expected_ratio
        else -> null
    }

    fun fallbackContextLabel(key: String): String = key
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replaceFirstChar { it.titlecase() }
}
