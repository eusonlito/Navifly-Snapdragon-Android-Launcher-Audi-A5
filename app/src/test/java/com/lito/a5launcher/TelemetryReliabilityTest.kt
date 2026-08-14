package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryReliabilityTest {

    @Test
    fun calculatedRangeRemainsAuthoritativeWhenUnverifiedCanDistanceIsPositive() {
        val bytes = ByteArray(20)
        put16(bytes, 5, 900)
        val telemetry = requireNotNull(TelemetryDecoder.decodeCore(bytes))

        assertEquals(900, telemetry.unverifiedDistanceKm)
        val metrics = TripSessionTracker(
            TripSessionState(
                virtualFuelLitres = 42.0,
                rangeBaselineConsumption = 10.0,
            )
        ).onTick(0L)

        assertEquals(420, authoritativeRangeKm(metrics))
    }

    @Test
    fun reconnectAttemptsUseOneBoundedBackoffSequence() {
        val policy = EventServiceReconnectPolicy(
            delaysMs = longArrayOf(1_000L, 2_000L, 5_000L),
        )

        assertEquals(1_000L, policy.nextDelayMs())
        assertEquals(2_000L, policy.nextDelayMs())
        assertEquals(5_000L, policy.nextDelayMs())
        assertEquals(5_000L, policy.nextDelayMs())
    }

    @Test
    fun successfulEventServiceConnectionResetsBackoff() {
        val policy = EventServiceReconnectPolicy(
            delaysMs = longArrayOf(1_000L, 2_000L),
        )

        policy.nextDelayMs()
        policy.nextDelayMs()
        policy.reset()

        assertEquals(1_000L, policy.nextDelayMs())
    }

    private fun put16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 8).toByte()
        target[offset + 1] = value.toByte()
    }
}
