package com.lito.a5launcher.functional

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalEventCodecTest {
    @Test
    fun `new settings are disabled with every category selected`() {
        val store = MemoryPreferenceStore()
        val settings = FunctionalEventSettings(store)

        assertFalse(settings.snapshot().enabled)
        assertEquals(FunctionalEventCategory.entries.toSet(), settings.snapshot().categories)

        settings.setEnabled(true)
        settings.setCategoryEnabled(FunctionalEventCategory.GEAR_ESTIMATION, false)

        val restored = FunctionalEventSettings(store).snapshot()
        assertTrue(restored.enabled)
        assertFalse(FunctionalEventCategory.GEAR_ESTIMATION in restored.categories)
        assertTrue(FunctionalEventCategory.TRIP_SESSION in restored.categories)
    }

    @Test
    fun `codec round trips structured event independently from wall clock order`() {
        val codec = FunctionalEventCodec()
        val first = event(sequence = 10, epochMs = 2_000)
        val second = event(sequence = 11, epochMs = 1_000)

        assertEquals(first, codec.decode(codec.encode(first)).event)
        assertEquals(second, codec.decode(codec.encode(second)).event)
        assertTrue(second.sequence > first.sequence)
        assertTrue(second.capturedAtEpochMs < first.capturedAtEpochMs)
    }

    @Test
    fun `codec skips malformed and incompatible lines but tolerates extra fields`() {
        val codec = FunctionalEventCodec()
        assertNull(codec.decode("{broken").event)
        assertNull(codec.decode("{\"schema\":999}").event)

        val compatible = codec.encode(event(sequence = 2, epochMs = 3))
            .dropLast(1) + ",\"futureField\":{\"nested\":true}}"
        val decoded = codec.decode(compatible)

        assertEquals(2L, decoded.event?.sequence)
        assertTrue(decoded.unknownFields.containsKey("futureField"))
    }

    @Test
    fun `unknown event type is retained as safe technical code`() {
        val codec = FunctionalEventCodec()
        val json = codec.encode(event(sequence = 3, epochMs = 4))
            .replace("gear.changed", "future.unrecognized")

        assertEquals("future.unrecognized", codec.decode(json).event?.type?.code)
    }

    private fun event(sequence: Long, epochMs: Long) = FunctionalEvent(
        sequence = sequence,
        bootSession = 7,
        capturedAtEpochMs = epochMs,
        capturedAtElapsedMs = 900,
        source = FunctionalEventSource.REPLAY,
        category = FunctionalEventCategory.GEAR_ESTIMATION,
        type = FunctionalEventType("gear.changed"),
        context = mapOf(
            "speedKmh" to FunctionalEventValue.Decimal(42.5),
            "rpm" to FunctionalEventValue.Integer(1_923),
            "accepted" to FunctionalEventValue.Flag(true),
            "gear" to FunctionalEventValue.Text("3"),
        ),
    )

    private class MemoryPreferenceStore : FunctionalEventPreferenceStore {
        private val values = mutableMapOf<String, Boolean>()
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    }
}
