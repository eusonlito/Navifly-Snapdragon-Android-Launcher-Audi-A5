package com.lito.a5launcher.functional

import com.lito.a5launcher.R
import com.lito.a5launcher.ui.components.FunctionalEventPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FunctionalEventPresentationTest {
    @Test
    fun `all categories have localized labels`() {
        assertEquals(
            mapOf(
                FunctionalEventCategory.REFUEL_AND_PARTIAL to
                    R.string.functional_logs_category_refuel_legacy,
                FunctionalEventCategory.PARTIAL_RESET to
                    R.string.functional_logs_category_refuel_partial,
                FunctionalEventCategory.TRIP_SESSION to
                    R.string.functional_logs_category_trip_session,
                FunctionalEventCategory.CONSUMPTION_AND_RANGE to
                    R.string.functional_logs_category_consumption_range,
                FunctionalEventCategory.GEAR_ESTIMATION to
                    R.string.functional_logs_category_gear_estimation,
                FunctionalEventCategory.MAXIMUM_SPEED to
                    R.string.functional_logs_category_maximum_speed,
            ),
            FunctionalEventCategory.entries.associateWith(
                FunctionalEventPresentation::categoryLabelRes,
            ),
        )
    }

    @Test
    fun `known event types have localized summaries and unknown type falls back`() {
        assertEquals(
            R.string.functional_logs_summary_refuel_confirmed,
            FunctionalEventPresentation.summaryRes(FunctionalEventTypes.PARTIAL_RESET),
        )
        assertEquals(
            R.string.functional_logs_summary_unknown,
            FunctionalEventPresentation.summaryRes(FunctionalEventType("future.event")),
        )
        assertEquals(
            R.string.functional_logs_summary_partial_maximum_speed,
            FunctionalEventPresentation.summaryRes(FunctionalEventTypes.PARTIAL_MAXIMUM_SPEED),
        )
    }

    @Test
    fun `known context keys are localized and future keys remain readable`() {
        assertEquals(
            R.string.functional_logs_context_fuel_before,
            FunctionalEventPresentation.contextLabelRes("fuelBeforeLitres"),
        )
        assertEquals(
            R.string.functional_logs_context_fuel_after,
            FunctionalEventPresentation.contextLabelRes("fuelAfterLitres"),
        )
        assertNull(FunctionalEventPresentation.contextLabelRes("futureValue"))
        assertEquals("Future Value", FunctionalEventPresentation.fallbackContextLabel("futureValue"))
    }
}
