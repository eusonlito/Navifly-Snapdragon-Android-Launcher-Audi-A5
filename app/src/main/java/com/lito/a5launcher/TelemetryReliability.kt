package com.lito.a5launcher

internal fun authoritativeRangeKm(metrics: TripMetricsSnapshot): Int =
    metrics.estimatedRangeKm.coerceAtLeast(0)

internal class EventServiceReconnectPolicy(
    private val delaysMs: LongArray = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L),
) {
    private var failureCount = 0

    init {
        require(delaysMs.isNotEmpty())
        require(delaysMs.all { it >= 0L })
    }

    fun nextDelayMs(): Long {
        val delay = delaysMs[failureCount.coerceAtMost(delaysMs.lastIndex)]
        failureCount++
        return delay
    }

    fun reset() {
        failureCount = 0
    }
}
