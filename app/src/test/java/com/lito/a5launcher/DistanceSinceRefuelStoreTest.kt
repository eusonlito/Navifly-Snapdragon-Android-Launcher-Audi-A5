package com.lito.a5launcher

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DistanceSinceRefuelStoreTest {
    @Test
    fun totalStatisticsSurviveRefuelsPartialResetsProcessRestorationAndNewTrips() {
        val store = DistanceSinceRefuelStore(MemorySharedPreferences())
        val total = DistanceSinceRefuelTracker(refuelDetector = null)
        val partial = DistanceSinceRefuelTracker(refuelDetector = null)
        listOf(total, partial).forEach { tracker ->
            tracker.advanceWithFuelDecision(
                60, 40, 0L, ConfirmedFuelLevelChange.Initialized,
                CumulativeFuelUsage(0.0, 0.0), 7L,
            )
        }
        total.advanceWithFuelDecision(
            60, 39, 1_000L, ConfirmedFuelLevelChange.Drop(1),
            CumulativeFuelUsage(.1, 1.0), 7L,
        )
        partial.resetManually(39, 1_000L, CumulativeFuelUsage(.1, 1.0), 7L)
        val refuelled = total.advanceWithFuelDecision(
            0, 45, 2_000L, ConfirmedFuelLevelChange.Refuel(45, 39, 2),
            CumulativeFuelUsage(.2, 1.0), 7L, resetStatisticsOnRefuel = false,
        ).statistics
        assertEquals(2_000L, refuelled.elapsedMs)
        assertEquals(60, refuelled.maximumSpeedKmh)
        assertEquals(.2, refuelled.fuelUsedLitres, .000_001)
        assertEquals(1.0, refuelled.observedFuelSpentLitres)
        assertEquals(0.0, partial.onTick(1_000L).statistics.distanceKm, .000_001)

        store.write(total.persistenceSnapshot())
        val state = store.read()
        val restored = DistanceSinceRefuelTracker(
            state.distanceKm, state.lastFuelLitres, state.statisticsState, refuelDetector = null,
        )
        assertEquals(refuelled, restored.onTick(3_000L, CumulativeFuelUsage(.2, 1.0), 7L).statistics)
        assertEquals(refuelled, restored.onTick(3_000L, CumulativeFuelUsage(.2, 1.0), 7L).statistics)

        // A new boot has its own fuel counters and monotonic clock.
        val rebooted = DistanceSinceRefuelTracker(
            state.distanceKm, state.lastFuelLitres, state.statisticsState, refuelDetector = null,
        )
        rebooted.onTick(0L, CumulativeFuelUsage(0.0, 0.0), 8L)
        rebooted.advanceWithFuelDecision(30, 45, 0L, null, CumulativeFuelUsage(0.0, 0.0), 8L)
        val nextTrip = rebooted.advanceWithFuelDecision(
            30, 44, 1_000L, ConfirmedFuelLevelChange.Drop(1), CumulativeFuelUsage(.1, 1.0), 8L,
        ).statistics
        assertEquals(3_000L, nextTrip.elapsedMs)
        assertEquals(3_000L, nextTrip.movingElapsedMs)
        assertEquals(60, nextTrip.maximumSpeedKmh)
        assertEquals(refuelled.distanceKm + 30.0 / 3_600, nextTrip.distanceKm, .000_001)
        assertEquals(.3, nextTrip.fuelUsedLitres, .000_001)
        assertEquals(2.0, nextTrip.confirmedCanFuelUsedLitres, .000_001)
        assertEquals(2.0, nextTrip.observedFuelSpentLitres)
        assertEquals(nextTrip.distanceKm / (3_000.0 / 3_600_000), nextTrip.averageSpeedKmh, .000_001)
    }

    @Test
    fun observedCanFuelLevelsSurviveStoreRoundTrip() {
        val store = DistanceSinceRefuelStore(MemorySharedPreferences())
        val statistics = DistanceSinceRefuelStatisticsState(
            elapsedMs = 120_000L,
            movingElapsedMs = 90_000L,
            maximumSpeedKmh = 110,
            fuelUsedLitres = 1.2,
            confirmedCanFuelUsedLitres = 5.0,
            initialObservedFuelLitres = 57,
            currentObservedFuelLitres = 52,
            sourceTripFuelUsage = CumulativeFuelUsage(1.2, 5.0),
            sourceTripGeneration = 8L,
            active = true,
        )

        store.write(DistanceSinceRefuelPersistenceSnapshot(25.0, 52, statistics))
        val restored = store.read()
        val restoredStatistics = DistanceSinceRefuelTracker(
            initialDistanceKm = restored.distanceKm,
            initialFuelLitres = restored.lastFuelLitres,
            initialStatisticsState = restored.statisticsState,
            refuelDetector = null,
        ).onTick(0L).statistics

        assertEquals(25.0, restored.distanceKm, .000_001)
        assertEquals(52, restored.lastFuelLitres)
        assertEquals(statistics, restored.statisticsState)
        assertEquals(5.0, restoredStatistics.observedFuelSpentLitres)
    }

    @Test
    fun absentOrInvalidObservedFuelLevelsRemainUnavailable() {
        val preferences = MemorySharedPreferences()
        val store = DistanceSinceRefuelStore(preferences)

        store.write(
            DistanceSinceRefuelPersistenceSnapshot(
                distanceKm = 0.0,
                lastFuelLitres = null,
                statisticsState = DistanceSinceRefuelStatisticsState(
                    initialObservedFuelLitres = 0,
                    currentObservedFuelLitres = -1,
                ),
            ),
        )
        val restored = store.read()

        assertNull(restored.statisticsState.initialObservedFuelLitres)
        assertNull(restored.statisticsState.currentObservedFuelLitres)
        assertFalse(restored.statisticsState.active)
    }

    @Test
    fun implausiblePersistedMaximumSpeedIsDiscarded() {
        val preferences = MemorySharedPreferences()
        val store = DistanceSinceRefuelStore(preferences)
        store.write(
            DistanceSinceRefuelPersistenceSnapshot(
                distanceKm = 20.0,
                lastFuelLitres = 40,
                statisticsState = DistanceSinceRefuelStatisticsState(maximumSpeedKmh = 655),
            ),
        )

        assertEquals(0, store.read().statisticsState.maximumSpeedKmh)
    }

    @Test
    fun pendingRefuelConfirmationSurvivesStoreRoundTrip() {
        val store = DistanceSinceRefuelStore(MemorySharedPreferences())
        val pending = PendingRefuelConfirmation(
            baselineFuelLitres = 48,
            candidateFuelLitres = 52,
            confirmationSamples = 2,
        )

        store.write(
            DistanceSinceRefuelPersistenceSnapshot(
                distanceKm = 195.5,
                lastFuelLitres = 48,
                statisticsState = DistanceSinceRefuelStatisticsState(active = true),
                pendingRefuelConfirmation = pending,
            ),
        )

        assertEquals(pending, store.read().pendingRefuelConfirmation)

        store.write(
            DistanceSinceRefuelPersistenceSnapshot(
                distanceKm = 195.5,
                lastFuelLitres = 52,
                statisticsState = DistanceSinceRefuelStatisticsState(active = true),
            ),
        )

        assertNull(store.read().pendingRefuelConfirmation)
    }
}

internal class MemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = MemoryEditor(values)
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}

private class MemoryEditor(
    private val values: MutableMap<String, Any?>,
) : SharedPreferences.Editor {
    private val updates = linkedMapOf<String, Any?>()
    private val removals = linkedSetOf<String>()
    private var clearRequested = false

    override fun putString(key: String, value: String?) = update(key, value)
    override fun putStringSet(key: String, values: Set<String>?) = update(key, values?.toSet())
    override fun putInt(key: String, value: Int) = update(key, value)
    override fun putLong(key: String, value: Long) = update(key, value)
    override fun putFloat(key: String, value: Float) = update(key, value)
    override fun putBoolean(key: String, value: Boolean) = update(key, value)
    override fun remove(key: String): SharedPreferences.Editor = apply {
        updates.remove(key)
        removals += key
    }
    override fun clear(): SharedPreferences.Editor = apply {
        clearRequested = true
        updates.clear()
        removals.clear()
    }
    override fun commit(): Boolean {
        applyChanges()
        return true
    }
    override fun apply() = applyChanges()

    private fun update(key: String, value: Any?): SharedPreferences.Editor = apply {
        removals.remove(key)
        updates[key] = value
    }

    private fun applyChanges() {
        if (clearRequested) values.clear()
        removals.forEach(values::remove)
        updates.forEach { (key, value) ->
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
