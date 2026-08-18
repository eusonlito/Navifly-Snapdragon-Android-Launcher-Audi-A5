package com.lito.a5launcher

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DistanceSinceRefuelStoreTest {
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
