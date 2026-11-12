package com.lito.a5launcher

import org.junit.Assert.*
import org.junit.Test

class PartialStatisticsTest {
    @Test
    fun pagesShowLatestSixWithoutLosingOrRepeatingPartials() {
        val records = (1..14).map { record(it.toString(), 10.0, 1.0, 1.0) }
        assertEquals(records.takeLast(6), partialStatisticsPage(records, 0))
        assertEquals(records.subList(2, 8), partialStatisticsPage(records, 1))
        assertEquals(records.take(2), partialStatisticsPage(records, 2))
        assertEquals(records, (2 downTo 0).flatMap { partialStatisticsPage(records, it) })
        assertEquals(records.take(2), partialStatisticsPage(records, 99))
        assertEquals(records.take(1), partialStatisticsPage(records.take(1), 0))
        assertTrue(partialStatisticsPage(emptyList(), 0).isEmpty())
    }

    @Test
    fun averagesAreDistanceWeightedAndTripsNeverContribute() {
        val result = buildPartialStatistics(listOf(
            record("a", 100.0, 8.0, 7.0),
            record("b", 300.0, 18.0, 21.0),
            record("trip", 900.0, 90.0, 90.0).copy(kind = JourneyHistoryKind.TRIP),
        ))
        assertEquals(2, result.records.size)
        assertEquals(400.0, result.distanceKm, 0.001)
        assertEquals(6.5, result.estimatedConsumption!!, 0.001)
        assertEquals(7.0, result.canConsumption!!, 0.001)
    }

    @Test
    fun missingCanAndZeroDistanceDoNotDiluteTheCanAverage() {
        val result = buildPartialStatistics(listOf(
            record("can", 100.0, 6.0, 7.0),
            record("missing", 200.0, 12.0, 0.0),
            record("stationary", 0.0, 1.0, 1.0),
        ))
        assertEquals(7.0, result.canConsumption!!, 0.001)
        assertNull(partialCanConsumption(record("missing", 200.0, 12.0, 0.0).statistics))
        assertNull(partialEstimatedConsumption(record("zero", 0.0, 1.0, 1.0).statistics))
        assertNull(buildPartialStatistics(emptyList()).estimatedConsumption)
        assertNull(buildPartialStatistics(emptyList()).canConsumption)
    }

    @Test
    fun filtersUseOverlappingIntervalsAndOrderPartialsChronologically() {
        val older = record("older", 10.0, 1.0, 1.0).copy(startedAtEpochMs = 10, endedAtEpochMs = 20)
        val newer = older.copy(id = "newer", startedAtEpochMs = 20, endedAtEpochMs = 30)
        assertEquals(listOf(older, newer), buildPartialStatistics(listOf(newer, older), 20, 30).records)
        assertEquals(listOf(older), buildPartialStatistics(listOf(newer, older), null, 20).records)
        assertTrue(buildPartialStatistics(listOf(older), 21, null).records.isEmpty())
    }

    private fun record(id: String, distance: Double, estimated: Double, can: Double) = JourneyHistoryRecord(
        id, JourneyHistoryKind.PARTIAL, 1, 2,
        JourneyStatisticsSnapshot(distanceKm = distance, fuelUsedLitres = estimated, confirmedCanFuelUsedLitres = can),
    )
}
