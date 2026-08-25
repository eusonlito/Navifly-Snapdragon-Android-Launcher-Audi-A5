package com.lito.a5launcher.ui.components

import com.lito.a5launcher.JourneyHistoryKind
import com.lito.a5launcher.JourneyHistoryRecord
import com.lito.a5launcher.JourneyStatisticsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyHistoryPresentationTest {
    @Test
    fun typeFilterSeparatesTripsPartialsAndAllRecords() {
        val trip = record("trip", JourneyHistoryKind.TRIP, 1_000L, 2_000L)
        val partial = record("partial", JourneyHistoryKind.PARTIAL, 2_000L, 3_000L)
        val records = listOf(partial, trip)

        assertEquals(records, filterJourneyHistory(records, JourneyHistoryTypeFilter.ALL, null, null))
        assertEquals(
            listOf(trip),
            filterJourneyHistory(records, JourneyHistoryTypeFilter.TRIPS, null, null),
        )
        assertEquals(
            listOf(partial),
            filterJourneyHistory(records, JourneyHistoryTypeFilter.PARTIALS, null, null),
        )
    }

    @Test
    fun dateRangeIncludesRecordsThatOverlapEitherBoundary() {
        val before = record("before", JourneyHistoryKind.TRIP, 500L, 999L)
        val crossesStart = record("start", JourneyHistoryKind.TRIP, 500L, 1_100L)
        val inside = record("inside", JourneyHistoryKind.PARTIAL, 1_500L, 1_900L)
        val crossesEnd = record("end", JourneyHistoryKind.TRIP, 1_900L, 2_500L)
        val after = record("after", JourneyHistoryKind.TRIP, 2_000L, 3_000L)

        assertEquals(
            listOf(crossesStart, inside, crossesEnd),
            filterJourneyHistory(
                listOf(before, crossesStart, inside, crossesEnd, after),
                JourneyHistoryTypeFilter.ALL,
                fromEpochMs = 1_000L,
                untilEpochMs = 2_000L,
            ),
        )
    }

    private fun record(
        id: String,
        kind: JourneyHistoryKind,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long,
    ) = JourneyHistoryRecord(
        id = id,
        kind = kind,
        startedAtEpochMs = startedAtEpochMs,
        endedAtEpochMs = endedAtEpochMs,
        statistics = JourneyStatisticsSnapshot(elapsedMs = 1L),
    )
}
