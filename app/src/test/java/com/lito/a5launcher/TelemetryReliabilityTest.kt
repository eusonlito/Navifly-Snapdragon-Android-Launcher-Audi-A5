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
        assertEquals(420, displayedEstimatedRange(420))
    }

    @Test
    fun unknownProviderReadingKeepsLastKnownSafetyState() {
        assertEquals(true, retainLastKnownBoolean(current = true, queried = null))
        assertEquals(false, retainLastKnownBoolean(current = false, queried = null))
        assertEquals(false, retainLastKnownBoolean(current = true, queried = false))
    }

    @Test
    fun allEventServiceFailuresUseOneBackoffSequence() {
        val policy = EventServiceReconnectPolicy(
            delaysMs = longArrayOf(1_000L, 2_000L, 5_000L),
        )

        assertEquals(1_000L, policy.delayAfter(EventServiceFailure.BIND_REJECTED))
        assertEquals(EventServiceFailure.BIND_REJECTED, policy.lastFailure())
        assertEquals(2_000L, policy.delayAfter(EventServiceFailure.CALLBACK_REGISTRATION_FAILED))
        assertEquals(5_000L, policy.delayAfter(EventServiceFailure.DISCONNECTED))
        assertEquals(5_000L, policy.delayAfter(EventServiceFailure.BIND_REJECTED))
    }

    @Test
    fun successfulEventServiceConnectionResetsBackoff() {
        val policy = EventServiceReconnectPolicy(
            delaysMs = longArrayOf(1_000L, 2_000L),
        )

        policy.delayAfter(EventServiceFailure.BIND_REJECTED)
        policy.delayAfter(EventServiceFailure.CALLBACK_REGISTRATION_FAILED)
        policy.onConnected()

        assertEquals(1_000L, policy.delayAfter(EventServiceFailure.DISCONNECTED))
    }

    private fun put16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 8).toByte()
        target[offset + 1] = value.toByte()
    }
}
