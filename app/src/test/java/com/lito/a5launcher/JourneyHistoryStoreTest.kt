package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class JourneyHistoryStoreTest {
    @Test
    fun recordsRoundTripEveryDisplayedStatisticNewestFirst() = withStore { store, _ ->
        val older = record(
            id = "trip-10",
            kind = JourneyHistoryKind.TRIP,
            startedAtEpochMs = 1_000L,
            endedAtEpochMs = 2_000L,
        )
        val newer = record(
            id = "partial-11",
            kind = JourneyHistoryKind.PARTIAL,
            startedAtEpochMs = 3_000L,
            endedAtEpochMs = 4_000L,
        )

        assertTrue(store.append(older))
        assertTrue(store.append(newer))

        assertEquals(listOf(newer, older), store.readAll())
    }

    @Test
    fun stableIdentifiersMakeArchivingIdempotent() = withStore { store, _ ->
        val first = record("trip-7", JourneyHistoryKind.TRIP, 1_000L, 2_000L)
        val duplicate = first.copy(endedAtEpochMs = 3_000L)

        assertTrue(store.append(first))
        assertFalse(store.append(duplicate))
        assertEquals(listOf(first), store.readAll())
    }

    @Test
    fun corruptFilesAreIgnoredWithoutHidingValidRecords() = withStore { store, root ->
        val valid = record("trip-8", JourneyHistoryKind.TRIP, 1_000L, 2_000L)
        assertTrue(store.append(valid))
        File(root, "record-broken.json").writeText("{not-json")

        assertEquals(listOf(valid), store.readAll())
    }

    @Test
    fun recordsCanBeDeletedIndividuallyOrTogether() = withStore { store, _ ->
        val trip = record("trip-8", JourneyHistoryKind.TRIP, 1_000L, 2_000L)
        val partial = record("partial-9", JourneyHistoryKind.PARTIAL, 2_000L, 3_000L)
        store.append(trip)
        store.append(partial)

        assertTrue(store.delete(trip.id))
        assertEquals(listOf(partial), store.readAll())
        assertEquals(1, store.deleteAll())
        assertTrue(store.readAll().isEmpty())
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
        statistics = JourneyStatisticsSnapshot(
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
            observedFuelSpentLitres = 5.0,
        ),
    )

    private inline fun withStore(block: (JourneyHistoryStore, File) -> Unit) {
        val root = createTempDirectory("journey-history-").toFile()
        try {
            block(JourneyHistoryStore(root), root)
        } finally {
            root.deleteRecursively()
        }
    }
}
