package com.lito.a5launcher

internal data class ReplayTimestamp(
    val timestampMillis: Long?,
    val elapsedRealtimeNanos: Long?,
)

internal enum class ReplayClock {
    ELAPSED_REALTIME_NANOS,
    TIMESTAMP_MILLIS,
}

/**
 * One clock is selected for a complete CAN/GPS replay cycle. New logger files
 * use elapsed realtime; wall time is used only when any replayable record lacks
 * the monotonic field, keeping both transports on the same legacy timeline.
 */
internal class ReplayTimeline private constructor(
    val clock: ReplayClock,
    private val originTick: Long,
    private val endTick: Long,
) {
    val durationMillis: Long
        get() = ticksToMillis((endTick - originTick).coerceAtLeast(0L))

    fun offsetMillis(timestamp: ReplayTimestamp): Long? = timestamp.tick(clock)?.let { tick ->
        ticksToMillis((tick - originTick).coerceAtLeast(0L))
    }

    private fun ticksToMillis(ticks: Long): Long = when (clock) {
        ReplayClock.ELAPSED_REALTIME_NANOS -> ticks / NANOS_PER_MILLISECOND
        ReplayClock.TIMESTAMP_MILLIS -> ticks
    }

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        fun from(timestamps: Iterable<ReplayTimestamp>): ReplayTimeline? =
            from(timestamps.asSequence())

        fun from(timestamps: Sequence<ReplayTimestamp>): ReplayTimeline? {
            var count = 0
            var completeMonotonicClock = true
            var completeWallClock = true
            var minimumMonotonic = Long.MAX_VALUE
            var maximumMonotonic = Long.MIN_VALUE
            var minimumWall = Long.MAX_VALUE
            var maximumWall = Long.MIN_VALUE

            timestamps.forEach { timestamp ->
                count++
                timestamp.elapsedRealtimeNanos?.let { tick ->
                    minimumMonotonic = minOf(minimumMonotonic, tick)
                    maximumMonotonic = maxOf(maximumMonotonic, tick)
                } ?: run { completeMonotonicClock = false }
                timestamp.timestampMillis?.let { tick ->
                    minimumWall = minOf(minimumWall, tick)
                    maximumWall = maxOf(maximumWall, tick)
                } ?: run { completeWallClock = false }
            }
            if (count == 0) return null
            return when {
                completeMonotonicClock -> ReplayTimeline(
                    ReplayClock.ELAPSED_REALTIME_NANOS,
                    minimumMonotonic,
                    maximumMonotonic,
                )
                completeWallClock -> ReplayTimeline(
                    ReplayClock.TIMESTAMP_MILLIS,
                    minimumWall,
                    maximumWall,
                )
                else -> null
            }
        }
    }
}

private fun ReplayTimestamp.tick(clock: ReplayClock): Long? = when (clock) {
    ReplayClock.ELAPSED_REALTIME_NANOS -> elapsedRealtimeNanos
    ReplayClock.TIMESTAMP_MILLIS -> timestampMillis
}
