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
                    R.string.functional_logs_category_refuel_partial,
                FunctionalEventCategory.TRIP_SESSION to
                    R.string.functional_logs_category_trip_session,
                FunctionalEventCategory.CONSUMPTION_AND_RANGE to
                    R.string.functional_logs_category_consumption_range,
                FunctionalEventCategory.GEAR_ESTIMATION to
                    R.string.functional_logs_category_gear_estimation,
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
            FunctionalEventPresentation.summaryRes(FunctionalEventTypes.REFUEL_CONFIRMED),
        )
        assertEquals(
            R.string.functional_logs_summary_gear_inconsistency,
            FunctionalEventPresentation.summaryRes(FunctionalEventTypes.GEAR_INCONSISTENCY),
        )
        assertEquals(
            R.string.functional_logs_summary_unknown,
            FunctionalEventPresentation.summaryRes(FunctionalEventType("future.event")),
        )
    }

    @Test
    fun `known context keys are localized and future keys remain readable`() {
        assertEquals(
            R.string.functional_logs_context_speed,
            FunctionalEventPresentation.contextLabelRes("speedKmh"),
        )
        assertNull(FunctionalEventPresentation.contextLabelRes("futureValue"))
        assertEquals("Future Value", FunctionalEventPresentation.fallbackContextLabel("futureValue"))
    }
}
