package com.lito.a5launcher.functional

import org.json.JSONObject

data class FunctionalEventDecodeResult(
    val event: FunctionalEvent?,
    val unknownFields: Map<String, String> = emptyMap(),
    val error: String? = null,
)

class FunctionalEventCodec {
    fun encode(event: FunctionalEvent): String {
        val context = JSONObject()
        event.context.toSortedMap().forEach { (key, value) ->
            context.put(key, value.toJsonValue())
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("sequence", event.sequence)
            .put("bootSession", event.bootSession)
            .put("capturedAtEpochMs", event.capturedAtEpochMs)
            .put("capturedAtElapsedMs", event.capturedAtElapsedMs)
            .put("source", event.source.code)
            .put("category", event.category.code)
            .put("type", event.type.code)
            .put("context", context)
            .toString()
    }

    fun decode(line: String): FunctionalEventDecodeResult = runCatching {
        val json = JSONObject(line)
        require(json.optInt("schema", -1) == SCHEMA) { "Unsupported schema" }
        val category = FunctionalEventCategory.fromCode(json.getString("category"))
            ?: error("Unknown category")
        val source = FunctionalEventSource.fromCode(json.getString("source"))
            ?: error("Unknown source")
        val contextJson = json.optJSONObject("context") ?: JSONObject()
        val context = buildMap {
            contextJson.keys().forEach { key ->
                contextJson.opt(key).toFunctionalValue()?.let { put(key, it) }
            }
        }
        val event = FunctionalEvent(
            sequence = json.getLong("sequence"),
            bootSession = json.getInt("bootSession"),
            capturedAtEpochMs = json.getLong("capturedAtEpochMs"),
            capturedAtElapsedMs = json.getLong("capturedAtElapsedMs"),
            source = source,
            category = category,
            type = FunctionalEventType(json.getString("type")),
            context = context,
        )
        val unknown = buildMap {
            json.keys().forEach { key ->
                if (key !in KNOWN_FIELDS) put(key, json.opt(key)?.toString().orEmpty())
            }
        }
        FunctionalEventDecodeResult(event, unknown)
    }.getOrElse { FunctionalEventDecodeResult(event = null, error = it.message ?: it.javaClass.simpleName) }

    private fun FunctionalEventValue.toJsonValue(): Any = when (this) {
        is FunctionalEventValue.Text -> value
        is FunctionalEventValue.Integer -> value
        is FunctionalEventValue.Decimal -> value
        is FunctionalEventValue.Flag -> value
    }

    private fun Any?.toFunctionalValue(): FunctionalEventValue? = when (this) {
        is String -> FunctionalEventValue.Text(this)
        is Boolean -> FunctionalEventValue.Flag(this)
        is Number -> when (this) {
            is Byte, is Short, is Int, is Long, is java.math.BigInteger ->
                FunctionalEventValue.Integer(toLong())
            else -> toDouble().takeIf(Double::isFinite)?.let(FunctionalEventValue::Decimal)
        }
        else -> null
    }

    companion object {
        const val SCHEMA = 1
        private val KNOWN_FIELDS = setOf(
            "schema", "sequence", "bootSession", "capturedAtEpochMs", "capturedAtElapsedMs",
            "source", "category", "type", "context",
        )
    }
}
