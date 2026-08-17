package com.lito.a5launcher

import android.content.SharedPreferences
import androidx.core.content.edit

internal data class PersistedRefuelState(
    val distanceKm: Double,
    val lastFuelLitres: Int?,
    val statisticsState: DistanceSinceRefuelStatisticsState,
)

internal class DistanceSinceRefuelStore(private val preferences: SharedPreferences) {
    fun read(): PersistedRefuelState {
        val currentSchema = preferences.getInt(SCHEMA, 0) == CURRENT_SCHEMA
        return PersistedRefuelState(
            distanceKm = if (currentSchema) preferences.nonNegativeDouble(DISTANCE_BITS) else 0.0,
            lastFuelLitres = preferences.getInt(LAST_FUEL_LITRES, 0).takeIf { it > 0 },
            statisticsState = if (currentSchema) readStatistics() else {
                DistanceSinceRefuelStatisticsState()
            },
        )
    }

    fun write(checkpoint: DistanceSinceRefuelPersistenceSnapshot) {
        val statistics = checkpoint.statisticsState
        preferences.edit {
            putInt(SCHEMA, CURRENT_SCHEMA)
            putLong(DISTANCE_BITS, checkpoint.distanceKm.toRawBits())
            checkpoint.lastFuelLitres?.let { putInt(LAST_FUEL_LITRES, it) }
                ?: remove(LAST_FUEL_LITRES)
            putLong(ELAPSED_MS, statistics.elapsedMs)
            putLong(MOVING_ELAPSED_MS, statistics.movingElapsedMs)
            putInt(MAXIMUM_SPEED_KMH, statistics.maximumSpeedKmh)
            putLong(FUEL_USED_BITS, statistics.fuelUsedLitres.toRawBits())
            putLong(CONFIRMED_CAN_FUEL_USED_BITS, statistics.confirmedCanFuelUsedLitres.toRawBits())
            statistics.initialObservedFuelLitres?.let {
                putInt(INITIAL_OBSERVED_FUEL_LITRES, it)
            } ?: remove(INITIAL_OBSERVED_FUEL_LITRES)
            statistics.currentObservedFuelLitres?.let {
                putInt(CURRENT_OBSERVED_FUEL_LITRES, it)
            } ?: remove(CURRENT_OBSERVED_FUEL_LITRES)
            statistics.sourceTripFuelUsage?.let {
                putLong(SOURCE_TRIP_FUEL_USED_BITS, it.estimatedLitres.toRawBits())
                putLong(SOURCE_TRIP_CONFIRMED_CAN_FUEL_USED_BITS, it.confirmedCanLitres.toRawBits())
            } ?: run {
                remove(SOURCE_TRIP_FUEL_USED_BITS)
                remove(SOURCE_TRIP_CONFIRMED_CAN_FUEL_USED_BITS)
            }
            statistics.sourceTripGeneration?.let { putLong(SOURCE_TRIP_GENERATION, it) }
                ?: remove(SOURCE_TRIP_GENERATION)
            putBoolean(STATISTICS_ACTIVE, statistics.active)
        }
    }

    private fun readStatistics() = DistanceSinceRefuelStatisticsState(
        elapsedMs = preferences.getLong(ELAPSED_MS, 0L).coerceAtLeast(0L),
        movingElapsedMs = preferences.getLong(MOVING_ELAPSED_MS, 0L).coerceAtLeast(0L),
        maximumSpeedKmh = preferences.getInt(MAXIMUM_SPEED_KMH, 0).coerceAtLeast(0),
        fuelUsedLitres = preferences.nonNegativeDouble(FUEL_USED_BITS),
        confirmedCanFuelUsedLitres = preferences.nonNegativeDouble(CONFIRMED_CAN_FUEL_USED_BITS),
        initialObservedFuelLitres = preferences.getInt(INITIAL_OBSERVED_FUEL_LITRES, 0)
            .takeIf { it > 0 },
        currentObservedFuelLitres = preferences.getInt(CURRENT_OBSERVED_FUEL_LITRES, 0)
            .takeIf { it > 0 },
        sourceTripFuelUsage = readSourceTripFuelUsage(),
        sourceTripGeneration = preferences.getLong(SOURCE_TRIP_GENERATION, Long.MIN_VALUE)
            .takeUnless { it == Long.MIN_VALUE },
        active = preferences.getBoolean(STATISTICS_ACTIVE, false),
    )

    private fun readSourceTripFuelUsage(): CumulativeFuelUsage? {
        val estimated = preferences.optionalNonNegativeDouble(SOURCE_TRIP_FUEL_USED_BITS)
        val confirmed = preferences.optionalNonNegativeDouble(
            SOURCE_TRIP_CONFIRMED_CAN_FUEL_USED_BITS,
        )
        if (estimated == null && confirmed == null) return null
        return CumulativeFuelUsage(estimated ?: 0.0, confirmed ?: 0.0)
    }

    private fun SharedPreferences.nonNegativeDouble(key: String): Double =
        Double.fromBits(getLong(key, 0.0.toRawBits()))
            .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

    private fun SharedPreferences.optionalNonNegativeDouble(key: String): Double? =
        takeIf { contains(key) }
            ?.let { Double.fromBits(it.getLong(key, 0L)) }
            ?.takeIf { it.isFinite() && it >= 0.0 }

    companion object {
        const val PREFERENCES_NAME = "distance_since_refuel"
        private const val CURRENT_SCHEMA = 1
        private const val SCHEMA = "schema"
        private const val DISTANCE_BITS = "distance_bits"
        private const val LAST_FUEL_LITRES = "last_fuel_litres"
        private const val ELAPSED_MS = "elapsed_ms"
        private const val MOVING_ELAPSED_MS = "moving_elapsed_ms"
        private const val MAXIMUM_SPEED_KMH = "maximum_speed_kmh"
        private const val FUEL_USED_BITS = "fuel_used_bits"
        private const val CONFIRMED_CAN_FUEL_USED_BITS = "confirmed_can_fuel_used_bits"
        private const val INITIAL_OBSERVED_FUEL_LITRES = "initial_observed_fuel_litres"
        private const val CURRENT_OBSERVED_FUEL_LITRES = "current_observed_fuel_litres"
        private const val SOURCE_TRIP_FUEL_USED_BITS = "source_trip_fuel_used_bits"
        private const val SOURCE_TRIP_CONFIRMED_CAN_FUEL_USED_BITS =
            "source_trip_confirmed_can_fuel_used_bits"
        private const val SOURCE_TRIP_GENERATION = "source_trip_generation"
        private const val STATISTICS_ACTIVE = "statistics_active"
    }
}
