package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayTimingTest {
    @Test
    fun `monotonic clock ignores wall clock corrections`() {
        val samples = listOf(
            ReplayTimestamp(timestampMillis = 10_000L, elapsedRealtimeNanos = 1_000_000_000L),
            ReplayTimestamp(timestampMillis = 40_000L, elapsedRealtimeNanos = 2_000_000_000L),
            ReplayTimestamp(timestampMillis = 20_000L, elapsedRealtimeNanos = 3_000_000_000L),
        )

        val timeline = requireNotNull(ReplayTimeline.from(samples))

        assertEquals(ReplayClock.ELAPSED_REALTIME_NANOS, timeline.clock)
        assertEquals(0L, timeline.offsetMillis(samples[0]))
        assertEquals(1_000L, timeline.offsetMillis(samples[1]))
        assertEquals(2_000L, timeline.offsetMillis(samples[2]))
        assertEquals(2_000L, timeline.durationMillis)
    }

    @Test
    fun `legacy logs use wall timestamps for the entire replay`() {
        val samples = listOf(
            ReplayTimestamp(timestampMillis = 1_000L, elapsedRealtimeNanos = null),
            ReplayTimestamp(timestampMillis = 4_000L, elapsedRealtimeNanos = null),
        )

        val timeline = requireNotNull(ReplayTimeline.from(samples))

        assertEquals(ReplayClock.TIMESTAMP_MILLIS, timeline.clock)
        assertEquals(0L, timeline.offsetMillis(samples[0]))
        assertEquals(3_000L, timeline.offsetMillis(samples[1]))
        assertEquals(3_000L, timeline.durationMillis)
    }

    @Test
    fun `mixed logs fall back as one timeline instead of switching clocks`() {
        val samples = listOf(
            ReplayTimestamp(timestampMillis = 1_000L, elapsedRealtimeNanos = 1_000_000_000L),
            ReplayTimestamp(timestampMillis = 2_500L, elapsedRealtimeNanos = null),
        )

        val timeline = requireNotNull(ReplayTimeline.from(samples))

        assertEquals(ReplayClock.TIMESTAMP_MILLIS, timeline.clock)
        assertEquals(1_500L, timeline.offsetMillis(samples[1]))
        assertNull(
            ReplayTimeline.from(
                listOf(ReplayTimestamp(timestampMillis = null, elapsedRealtimeNanos = null)),
            ),
        )
    }
}
