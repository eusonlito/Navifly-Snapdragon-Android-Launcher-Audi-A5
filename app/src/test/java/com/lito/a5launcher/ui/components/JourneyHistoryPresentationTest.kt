package com.lito.a5launcher.ui.components

import com.lito.a5launcher.JourneyHistoryKind
import com.lito.a5launcher.JourneyHistoryRecord
import com.lito.a5launcher.JourneyStatisticsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyHistoryPresentationTest {
    @Test
    fun typeFilterSeparatesTripsPartialsAndAllRecords() {
        val trip = item("trip", JourneyHistoryKind.TRIP, 1_000L, 2_000L)
        val partial = item("partial", JourneyHistoryKind.PARTIAL, 2_000L, 3_000L)
        val records = listOf(partial, trip)

        assertEquals(
            records,
            filterJourneyHistory(records, JourneyHistoryTypeFilter.ALL, null, null, 3_000L),
        )
        assertEquals(
            listOf(trip),
            filterJourneyHistory(records, JourneyHistoryTypeFilter.TRIPS, null, null, 3_000L),
        )
        assertEquals(
            listOf(partial),
            filterJourneyHistory(records, JourneyHistoryTypeFilter.PARTIALS, null, null, 3_000L),
        )
    }

    @Test
    fun dateRangeIncludesRecordsThatOverlapEitherBoundary() {
        val before = item("before", JourneyHistoryKind.TRIP, 500L, 999L)
        val crossesStart = item("start", JourneyHistoryKind.TRIP, 500L, 1_100L)
        val inside = item("inside", JourneyHistoryKind.PARTIAL, 1_500L, 1_900L)
        val crossesEnd = item("end", JourneyHistoryKind.TRIP, 1_900L, 2_500L)
        val after = item("after", JourneyHistoryKind.TRIP, 2_000L, 3_000L)

        assertEquals(
            listOf(crossesStart, inside, crossesEnd),
            filterJourneyHistory(
                listOf(before, crossesStart, inside, crossesEnd, after),
                JourneyHistoryTypeFilter.ALL,
                fromEpochMs = 1_000L,
                untilEpochMs = 2_000L,
                nowEpochMs = 2_000L,
            ),
        )
    }

    @Test
    fun currentTripAndPartialAreOpenAndUseTheirElapsedTimeAsStart() {
        val items = buildJourneyHistoryItems(
            records = emptyList(),
            tripStatistics = JourneyStatisticsSnapshot(elapsedMs = 60_000L),
            partialStatistics = JourneyStatisticsSnapshot(elapsedMs = 120_000L),
            nowEpochMs = 1_000_000L,
        )

        assertEquals(
            listOf(
                JourneyHistoryItem(
                    id = CURRENT_TRIP_ID,
                    kind = JourneyHistoryKind.TRIP,
                    startedAtEpochMs = 940_000L,
                    endedAtEpochMs = null,
                    statistics = JourneyStatisticsSnapshot(elapsedMs = 60_000L),
                ),
                JourneyHistoryItem(
                    id = CURRENT_PARTIAL_ID,
                    kind = JourneyHistoryKind.PARTIAL,
                    startedAtEpochMs = 880_000L,
                    endedAtEpochMs = null,
                    statistics = JourneyStatisticsSnapshot(elapsedMs = 120_000L),
                ),
            ),
            items,
        )
    }

    @Test
    fun currentItemsUseNowAsTheirTemporaryEndWhenFilteringByDate() {
        val currentTrip = JourneyHistoryItem(
            id = CURRENT_TRIP_ID,
            kind = JourneyHistoryKind.TRIP,
            startedAtEpochMs = 1_000L,
            endedAtEpochMs = null,
            statistics = JourneyStatisticsSnapshot(elapsedMs = 1_000L),
        )

        assertEquals(
            listOf(currentTrip),
            filterJourneyHistory(
                records = listOf(currentTrip),
                type = JourneyHistoryTypeFilter.ALL,
                fromEpochMs = 1_500L,
                untilEpochMs = 2_500L,
                nowEpochMs = 2_000L,
            ),
        )
        assertEquals(
            emptyList<JourneyHistoryItem>(),
            filterJourneyHistory(
                records = listOf(currentTrip),
                type = JourneyHistoryTypeFilter.ALL,
                fromEpochMs = 2_001L,
                untilEpochMs = null,
                nowEpochMs = 2_000L,
            ),
        )
    }

    @Test
    fun multipleRowsCanRemainExpandedAndCollapseIndependently() {
        val firstExpanded = toggleJourneyHistoryExpansion(emptySet(), "trip-1")
        val bothExpanded = toggleJourneyHistoryExpansion(firstExpanded, "partial-2")

        assertEquals(setOf("trip-1", "partial-2"), bothExpanded)
        assertEquals(
            setOf("partial-2"),
            toggleJourneyHistoryExpansion(bothExpanded, "trip-1"),
        )
    }

    private fun item(
        id: String,
        kind: JourneyHistoryKind,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long,
    ) = JourneyHistoryItem(
        record(
            id = id,
            kind = kind,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
        ),
    )

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
