package com.lito.a5launcher.ui.components

import com.lito.a5launcher.JourneyHistoryKind
import com.lito.a5launcher.JourneyStatisticsSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class JourneyHistoryExportTest {
    @Test
    fun exportContainsOnlyFilteredRowsAndRecordsActiveFilters() {
        val trip = item("trip-1", JourneyHistoryKind.TRIP, 1_000L, 2_000L)
        val partial = item("partial-1", JourneyHistoryKind.PARTIAL, 2_000L, null)
        val filtered = filterJourneyHistory(
            records = listOf(trip, partial),
            type = JourneyHistoryTypeFilter.PARTIALS,
            fromEpochMs = 1_500L,
            untilEpochMs = 3_000L,
            nowEpochMs = 2_500L,
        )

        val json = JSONObject(
            encodeJourneyHistoryExport(
                JourneyHistoryExportRequest(
                    records = filtered,
                    typeFilter = JourneyHistoryTypeFilter.PARTIALS,
                    fromEpochMs = 1_500L,
                    untilEpochMs = 3_000L,
                    exportedAtEpochMs = 2_500L,
                ),
            ),
        )

        assertEquals(1, json.getInt("count"))
        assertEquals("partials", json.getJSONObject("filters").getString("type"))
        assertEquals(1_500L, json.getJSONObject("filters").getLong("fromEpochMs"))
        assertEquals(3_000L, json.getJSONObject("filters").getLong("untilEpochMsExclusive"))
        assertEquals("partial-1", json.getJSONArray("records").getJSONObject(0).getString("id"))
        assertEquals(
            "partial",
            json.getJSONArray("records").getJSONObject(0).getString("kind"),
        )
    }

    @Test
    fun exportPreservesOpenStateAndEveryDisplayedStatistic() {
        val statistics = JourneyStatisticsSnapshot(
            elapsedMs = 3_600_000L,
            movingElapsedMs = 3_000_000L,
            distanceKm = 82.5,
            maximumSpeedKmh = 121,
            averageSpeedKmh = 82.5,
            movingAverageSpeedKmh = 99.0,
            calculatedConsumption = 6.4,
            observedCanConsumption = 6.1,
            fuelUsedLitres = 5.28,
            confirmedCanFuelUsedLitres = 5.0,
            observedFuelSpentLitres = 4.8,
        )
        val json = JSONObject(
            encodeJourneyHistoryExport(
                JourneyHistoryExportRequest(
                    records = listOf(
                        JourneyHistoryItem(
                            id = CURRENT_TRIP_ID,
                            kind = JourneyHistoryKind.TRIP,
                            startedAtEpochMs = 1_000L,
                            endedAtEpochMs = null,
                            statistics = statistics,
                        ),
                    ),
                    typeFilter = JourneyHistoryTypeFilter.ALL,
                    fromEpochMs = null,
                    untilEpochMs = null,
                    exportedAtEpochMs = 2_000L,
                ),
            ),
        )
        val record = json.getJSONArray("records").getJSONObject(0)
        val exportedStatistics = record.getJSONObject("statistics")

        assertTrue(record.getBoolean("open"))
        assertTrue(record.isNull("endedAtEpochMs"))
        assertTrue(json.getJSONObject("filters").isNull("fromEpochMs"))
        assertTrue(json.getJSONObject("filters").isNull("untilEpochMsExclusive"))
        assertEquals(3_600_000L, exportedStatistics.getLong("elapsedMs"))
        assertEquals(3_000_000L, exportedStatistics.getLong("movingElapsedMs"))
        assertEquals(82.5, exportedStatistics.getDouble("distanceKm"), 0.0)
        assertEquals(121, exportedStatistics.getInt("maximumSpeedKmh"))
        assertEquals(82.5, exportedStatistics.getDouble("averageSpeedKmh"), 0.0)
        assertEquals(99.0, exportedStatistics.getDouble("movingAverageSpeedKmh"), 0.0)
        assertEquals(6.4, exportedStatistics.getDouble("calculatedConsumption"), 0.0)
        assertEquals(6.1, exportedStatistics.getDouble("observedCanConsumption"), 0.0)
        assertEquals(5.28, exportedStatistics.getDouble("fuelUsedLitres"), 0.0)
        assertEquals(5.0, exportedStatistics.getDouble("confirmedCanFuelUsedLitres"), 0.0)
        assertEquals(4.8, exportedStatistics.getDouble("observedFuelSpentLitres"), 0.0)
    }

    @Test
    fun failedDocumentWriteDeletesTheIncompleteDestination() {
        var deleted = false
        val request = JourneyHistoryExportRequest(
            records = emptyList(),
            typeFilter = JourneyHistoryTypeFilter.ALL,
            fromEpochMs = null,
            untilEpochMs = null,
            exportedAtEpochMs = 2_000L,
        )

        val exported = writeJourneyHistoryDocument(
            request = request,
            openOutput = { null },
            deleteFailedDocument = { deleted = true },
        )

        assertFalse(exported)
        assertTrue(deleted)
    }

    @Test
    fun exceptionDuringDocumentWriteDeletesTheIncompleteDestination() {
        var deleted = false
        val request = JourneyHistoryExportRequest(
            records = emptyList(),
            typeFilter = JourneyHistoryTypeFilter.ALL,
            fromEpochMs = null,
            untilEpochMs = null,
            exportedAtEpochMs = 2_000L,
        )

        val exported = writeJourneyHistoryDocument(
            request = request,
            openOutput = {
                object : OutputStream() {
                    override fun write(value: Int) = throw IOException("storage unavailable")
                }
            },
            deleteFailedDocument = { deleted = true },
        )

        assertFalse(exported)
        assertTrue(deleted)
    }

    @Test
    fun successfulDocumentWriteKeepsDestination() {
        var deleted = false
        val output = ByteArrayOutputStream()
        val request = JourneyHistoryExportRequest(
            records = emptyList(),
            typeFilter = JourneyHistoryTypeFilter.ALL,
            fromEpochMs = null,
            untilEpochMs = null,
            exportedAtEpochMs = 2_000L,
        )

        val exported = writeJourneyHistoryDocument(
            request = request,
            openOutput = { output },
            deleteFailedDocument = { deleted = true },
        )

        assertTrue(exported)
        assertFalse(deleted)
        assertEquals(0, JSONObject(output.toString(Charsets.UTF_8)).getInt("count"))
    }

    private fun item(
        id: String,
        kind: JourneyHistoryKind,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long?,
    ) = JourneyHistoryItem(
        id = id,
        kind = kind,
        startedAtEpochMs = startedAtEpochMs,
        endedAtEpochMs = endedAtEpochMs,
        statistics = JourneyStatisticsSnapshot(elapsedMs = 1L),
    )
}
