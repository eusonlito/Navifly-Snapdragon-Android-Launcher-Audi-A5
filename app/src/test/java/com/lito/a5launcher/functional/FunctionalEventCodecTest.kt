package com.lito.a5launcher.functional

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalEventCodecTest {
    @Test
    fun `new settings are disabled with every capture option selected`() {
        val store = MemoryPreferenceStore()
        val settings = FunctionalEventSettings(store)

        assertFalse(settings.snapshot().enabled)
        assertEquals(FunctionalEventCategory.captureOptions.toSet(), settings.snapshot().categories)

        settings.setEnabled(true)
        settings.setCategoryEnabled(FunctionalEventCategory.PARTIAL_RESET, false)

        val restored = FunctionalEventSettings(store).snapshot()
        assertTrue(restored.enabled)
        assertFalse(FunctionalEventCategory.PARTIAL_RESET in restored.categories)
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

    @Test
    fun `legacy refuelling records keep their historical category and type`() {
        val decoded = FunctionalEventCodec().decode(
            """{"schema":1,"sequence":8,"bootSession":2,"capturedAtEpochMs":1000,"capturedAtElapsedMs":900,"source":"eventcenter","category":"refuel-partial","type":"refuel.rejected","context":{"baselineFuelLitres":20,"observedFuelLitres":21}}""",
        ).event

        assertEquals(FunctionalEventCategory.REFUEL_AND_PARTIAL, decoded?.category)
        assertEquals(FunctionalEventTypes.REFUEL_REJECTED, decoded?.type)
    }

    @Test
    fun `partial reset uses a distinct persisted category`() {
        val codec = FunctionalEventCodec()
        val encoded = codec.encode(
            event(sequence = 9, epochMs = 1_000).copy(
                category = FunctionalEventCategory.PARTIAL_RESET,
                type = FunctionalEventTypes.PARTIAL_RESET,
            ),
        )

        assertEquals(FunctionalEventCategory.PARTIAL_RESET, codec.decode(encoded).event?.category)
        assertTrue(encoded.contains("\"category\":\"partial-reset\""))
    }

    @Test
    fun `maximum speed event keeps its stable persisted codes`() {
        val codec = FunctionalEventCodec()
        val encoded = codec.encode(
            event(sequence = 10, epochMs = 1_000).copy(
                category = FunctionalEventCategory.MAXIMUM_SPEED,
                type = FunctionalEventTypes.PARTIAL_MAXIMUM_SPEED,
            ),
        )
        val decoded = codec.decode(encoded).event

        assertEquals(FunctionalEventCategory.MAXIMUM_SPEED, decoded?.category)
        assertEquals(FunctionalEventTypes.PARTIAL_MAXIMUM_SPEED, decoded?.type)
        assertTrue(encoded.contains("\"category\":\"maximum-speed\""))
        assertTrue(encoded.contains("\"type\":\"partial.maximum-speed\""))
    }

    @Test
    fun `publisher obeys global and category settings and isolates sink failures`() {
        val store = MemoryPreferenceStore()
        val settings = FunctionalEventSettings(store)
        val captured = mutableListOf<FunctionalEventDraft>()
        val publisher = FunctionalEventPublisher(settings) { captured += it; true }
        val draft = draft(FunctionalEventCategory.PARTIAL_RESET)

        assertFalse(publisher.publish(draft))
        settings.setEnabled(true)
        settings.setCategoryEnabled(FunctionalEventCategory.PARTIAL_RESET, false)
        assertFalse(publisher.publish(draft))
        settings.setCategoryEnabled(FunctionalEventCategory.PARTIAL_RESET, true)
        assertTrue(publisher.publish(draft))
        assertEquals(listOf(draft), captured)

        val failing = FunctionalEventPublisher(settings) { error("disk failure") }
        assertFalse(failing.publish(draft))
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

    private fun draft(category: FunctionalEventCategory) = FunctionalEventDraft(
        bootSession = 1,
        capturedAtEpochMs = 1,
        capturedAtElapsedMs = 1,
        source = FunctionalEventSource.EVENT_CENTER,
        category = category,
        type = FunctionalEventTypes.TRIP_RESTORED,
        context = emptyMap(),
    )

    private class MemoryPreferenceStore : FunctionalEventPreferenceStore {
        private val values = mutableMapOf<String, Boolean>()
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    }
}
