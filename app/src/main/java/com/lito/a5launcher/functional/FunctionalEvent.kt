package com.lito.a5launcher.functional

enum class FunctionalEventCategory(val code: String) {
    REFUEL_AND_PARTIAL("refuel-partial"),
    TRIP_SESSION("trip-session"),
    CONSUMPTION_AND_RANGE("consumption-range"),
    GEAR_ESTIMATION("gear-estimation");

    companion object {
        fun fromCode(code: String): FunctionalEventCategory? = entries.firstOrNull { it.code == code }
    }
}

enum class FunctionalEventSource(val code: String) {
    EVENT_CENTER("eventcenter"),
    REPLAY("replay");

    companion object {
        fun fromCode(code: String): FunctionalEventSource? = entries.firstOrNull { it.code == code }
    }
}

@JvmInline
value class FunctionalEventType(val code: String) {
    init {
        require(code.length in 1..MAX_CODE_LENGTH && code.matches(CODE_PATTERN)) {
            "Invalid functional event type: $code"
        }
    }

    private companion object {
        const val MAX_CODE_LENGTH = 128
        val CODE_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    }
}

object FunctionalEventTypes {
    val REFUEL_CONFIRMED = FunctionalEventType("refuel.confirmed")
    val REFUEL_REJECTED = FunctionalEventType("refuel.rejected")
    val TRIP_RESTORED = FunctionalEventType("trip.restored")
    val TRIP_RESET = FunctionalEventType("trip.reset")
    val CONSUMPTION_CALIBRATED = FunctionalEventType("consumption.calibrated")
    val FUEL_CORRECTED = FunctionalEventType("consumption.fuel-corrected")
    val RANGE_REFERENCE_CHANGED = FunctionalEventType("range.reference-changed")
    val CONSUMPTION_LIMIT_ENTERED = FunctionalEventType("consumption.limit-entered")
    val CONSUMPTION_LIMIT_EXITED = FunctionalEventType("consumption.limit-exited")
    val GEAR_CHANGED = FunctionalEventType("gear.changed")
    val GEAR_INCONSISTENCY = FunctionalEventType("gear.inconsistency")
}

sealed interface FunctionalEventValue {
    data class Text(val value: String) : FunctionalEventValue
    data class Integer(val value: Long) : FunctionalEventValue
    data class Decimal(val value: Double) : FunctionalEventValue {
        init { require(value.isFinite()) }
    }
    data class Flag(val value: Boolean) : FunctionalEventValue
}

data class FunctionalEvent(
    val sequence: Long,
    val bootSession: Int,
    val capturedAtEpochMs: Long,
    val capturedAtElapsedMs: Long,
    val source: FunctionalEventSource,
    val category: FunctionalEventCategory,
    val type: FunctionalEventType,
    val context: Map<String, FunctionalEventValue>,
) {
    init {
        require(sequence > 0)
        require(capturedAtElapsedMs >= 0)
        require(context.keys.all { it.matches(CONTEXT_KEY_PATTERN) })
    }

    private companion object {
        val CONTEXT_KEY_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.-]*")
    }
}

data class FunctionalEventDraft(
    val bootSession: Int,
    val capturedAtEpochMs: Long,
    val capturedAtElapsedMs: Long,
    val source: FunctionalEventSource,
    val category: FunctionalEventCategory,
    val type: FunctionalEventType,
    val context: Map<String, FunctionalEventValue>,
) {
    fun withSequence(sequence: Long): FunctionalEvent = FunctionalEvent(
        sequence = sequence,
        bootSession = bootSession,
        capturedAtEpochMs = capturedAtEpochMs,
        capturedAtElapsedMs = capturedAtElapsedMs,
        source = source,
        category = category,
        type = type,
        context = context,
    )
}
