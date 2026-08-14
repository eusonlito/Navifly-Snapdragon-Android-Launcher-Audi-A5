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

        fun from(timestamps: List<ReplayTimestamp>): ReplayTimeline? {
            if (timestamps.isEmpty()) return null
            val clock = if (timestamps.all { it.elapsedRealtimeNanos != null }) {
                ReplayClock.ELAPSED_REALTIME_NANOS
            } else {
                ReplayClock.TIMESTAMP_MILLIS
            }
            val ticks = timestamps.mapNotNull { it.tick(clock) }
            if (ticks.size != timestamps.size) return null
            return ReplayTimeline(clock, ticks.min(), ticks.max())
        }
    }
}

private fun ReplayTimestamp.tick(clock: ReplayClock): Long? = when (clock) {
    ReplayClock.ELAPSED_REALTIME_NANOS -> elapsedRealtimeNanos
    ReplayClock.TIMESTAMP_MILLIS -> timestampMillis
}
