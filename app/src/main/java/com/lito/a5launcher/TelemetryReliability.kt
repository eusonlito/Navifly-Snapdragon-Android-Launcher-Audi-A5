package com.lito.a5launcher

internal fun displayedEstimatedRange(estimatedRangeKm: Int): Int =
    estimatedRangeKm.coerceAtLeast(0)

internal fun retainLastKnownBoolean(current: Boolean, queried: Boolean?): Boolean =
    queried ?: current

internal enum class EventServiceFailure {
    BIND_REJECTED,
    CALLBACK_REGISTRATION_FAILED,
    DISCONNECTED,
}

internal class EventServiceReconnectPolicy(
    private val delaysMs: LongArray = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L),
) {
    private var failureCount = 0
    private var lastFailure: EventServiceFailure? = null

    init {
        require(delaysMs.isNotEmpty())
        require(delaysMs.all { it >= 0L })
    }

    fun delayAfter(failure: EventServiceFailure): Long {
        lastFailure = failure
        val delay = delaysMs[failureCount.coerceAtMost(delaysMs.lastIndex)]
        failureCount++
        return delay
    }

    fun onConnected() {
        failureCount = 0
        lastFailure = null
    }

    fun lastFailure(): EventServiceFailure? = lastFailure
}
