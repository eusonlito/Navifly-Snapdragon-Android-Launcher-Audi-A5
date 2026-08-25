package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class JourneyHistoryManagerTest {
    @Test
    fun partialResetArchivesThePreviousPeriodAndStartsANewOne() = withManager { manager ->
        manager.initializePartial(startedAtEpochMs = 1_000L)
        val statistics = statistics(distanceKm = 42.0)

        assertTrue(manager.closePartial(statistics, endedAtEpochMs = 5_000L))

        val snapshot = manager.snapshot()
        assertEquals(1, snapshot.records.size)
        assertEquals(JourneyHistoryKind.PARTIAL, snapshot.records.single().kind)
        assertEquals(1_000L, snapshot.records.single().startedAtEpochMs)
        assertEquals(5_000L, snapshot.records.single().endedAtEpochMs)
        assertEquals(5_000L, manager.partialStartedAtEpochMs())
    }

    @Test
    fun disabledHistorySkipsRecordsButStillAdvancesPartialBoundary() = withManager { manager ->
        manager.initializePartial(startedAtEpochMs = 1_000L)
        manager.setEnabled(false)

        assertFalse(manager.closePartial(statistics(), endedAtEpochMs = 5_000L))
        assertFalse(manager.snapshot().enabled)
        assertFalse(manager.closeTrip(7L, 1_000L, 5_000L, statistics()))
        manager.setEnabled(true)
        assertFalse(manager.closeTrip(7L, 1_000L, 6_000L, statistics()))

        assertTrue(manager.snapshot().records.isEmpty())
        assertEquals(5_000L, manager.partialStartedAtEpochMs())
    }

    @Test
    fun completedTripIsArchivedAtMostOnceAcrossManagerRestarts() {
        val root = createTempDirectory("journey-history-manager-").toFile()
        val preferences = MemorySharedPreferences()
        try {
            val first = JourneyHistoryManager(JourneyHistoryStore(root), preferences)
            assertTrue(first.closeTrip(7L, 1_000L, 5_000L, statistics()))

            val restored = JourneyHistoryManager(JourneyHistoryStore(root), preferences)
            assertFalse(restored.closeTrip(7L, 1_000L, 6_000L, statistics()))
            assertEquals(1, restored.snapshot().records.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun emptyTripsAndPartialsAreNotStored() = withManager { manager ->
        manager.initializePartial(startedAtEpochMs = 1_000L)

        assertFalse(manager.closeTrip(1L, 1_000L, 2_000L, JourneyStatisticsSnapshot()))
        assertFalse(manager.closePartial(JourneyStatisticsSnapshot(), 2_000L))
        assertTrue(manager.snapshot().records.isEmpty())
    }

    private fun statistics(distanceKm: Double = 10.0) = JourneyStatisticsSnapshot(
        elapsedMs = 600_000L,
        movingElapsedMs = 500_000L,
        distanceKm = distanceKm,
        maximumSpeedKmh = 90,
        averageSpeedKmh = 60.0,
        movingAverageSpeedKmh = 72.0,
        calculatedConsumption = 6.5,
        observedCanConsumption = 6.0,
        fuelUsedLitres = .65,
        confirmedCanFuelUsedLitres = .6,
        observedFuelSpentLitres = 1.0,
    )

    private inline fun withManager(block: (JourneyHistoryManager) -> Unit) {
        val root = createTempDirectory("journey-history-manager-").toFile()
        try {
            block(JourneyHistoryManager(JourneyHistoryStore(root), MemorySharedPreferences()))
        } finally {
            root.deleteRecursively()
        }
    }
}
