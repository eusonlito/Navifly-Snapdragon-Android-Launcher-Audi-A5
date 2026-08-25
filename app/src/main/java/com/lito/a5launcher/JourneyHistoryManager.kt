package com.lito.a5launcher

import android.content.SharedPreferences
import androidx.core.content.edit

internal class JourneyHistoryManager(
    private val store: JourneyHistoryStore,
    private val preferences: SharedPreferences,
) {
    @Synchronized
    fun snapshot(): JourneyHistorySnapshot = JourneyHistorySnapshot(
        enabled = isEnabled(),
        records = store.readAll(),
    )

    @Synchronized
    fun setEnabled(enabled: Boolean): JourneyHistorySnapshot {
        preferences.edit { putBoolean(ENABLED, enabled) }
        return snapshot()
    }

    @Synchronized
    fun initializePartial(startedAtEpochMs: Long) {
        if (partialStartedAtEpochMs() != null || startedAtEpochMs <= 0L) return
        preferences.edit(commit = true) { putLong(PARTIAL_STARTED_AT_EPOCH_MS, startedAtEpochMs) }
    }

    @Synchronized
    fun partialStartedAtEpochMs(): Long? = preferences
        .getLong(PARTIAL_STARTED_AT_EPOCH_MS, 0L)
        .takeIf { it > 0L }

    @Synchronized
    fun closePartial(
        statistics: JourneyStatisticsSnapshot,
        endedAtEpochMs: Long,
    ): Boolean {
        val startedAt = partialStartedAtEpochMs() ?: endedAtEpochMs
        val stored = if (isEnabled() && statistics.hasJourneyActivity()) {
            store.append(
                JourneyHistoryRecord(
                    id = nextRecordId(JourneyHistoryKind.PARTIAL),
                    kind = JourneyHistoryKind.PARTIAL,
                    startedAtEpochMs = minOf(startedAt, endedAtEpochMs),
                    endedAtEpochMs = endedAtEpochMs,
                    statistics = statistics,
                ),
            )
        } else false
        preferences.edit(commit = true) { putLong(PARTIAL_STARTED_AT_EPOCH_MS, endedAtEpochMs) }
        return stored
    }

    @Synchronized
    fun closeTrip(
        generation: Long,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long,
        statistics: JourneyStatisticsSnapshot,
    ): Boolean {
        if (generation <= 0L || generation == processedTripGeneration()) return false
        val record = JourneyHistoryRecord(
            id = "trip-$generation",
            kind = JourneyHistoryKind.TRIP,
            startedAtEpochMs = minOf(startedAtEpochMs, endedAtEpochMs),
            endedAtEpochMs = endedAtEpochMs,
            statistics = statistics,
        )
        if (!isEnabled() || !record.isValid()) {
            markTripProcessed(generation)
            return false
        }
        val stored = store.append(record)
        if (stored || store.contains(record.id)) markTripProcessed(generation)
        return stored
    }

    @Synchronized
    fun delete(id: String): JourneyHistorySnapshot {
        store.delete(id)
        return snapshot()
    }

    @Synchronized
    fun deleteAll(): JourneyHistorySnapshot {
        store.deleteAll()
        return snapshot()
    }

    private fun isEnabled(): Boolean = preferences.getBoolean(ENABLED, true)

    private fun processedTripGeneration(): Long =
        preferences.getLong(PROCESSED_TRIP_GENERATION, 0L)

    private fun markTripProcessed(generation: Long) {
        preferences.edit(commit = true) { putLong(PROCESSED_TRIP_GENERATION, generation) }
    }

    private fun nextRecordId(kind: JourneyHistoryKind): String {
        val sequence = preferences.getLong(NEXT_SEQUENCE, 0L).coerceAtLeast(0L) + 1L
        preferences.edit(commit = true) { putLong(NEXT_SEQUENCE, sequence) }
        return "${kind.name.lowercase()}-$sequence"
    }

    companion object {
        const val PREFERENCES_NAME = "journey_history"
        private const val ENABLED = "enabled"
        private const val PARTIAL_STARTED_AT_EPOCH_MS = "partial_started_at_epoch_ms"
        private const val PROCESSED_TRIP_GENERATION = "processed_trip_generation"
        private const val NEXT_SEQUENCE = "next_sequence"
    }
}
