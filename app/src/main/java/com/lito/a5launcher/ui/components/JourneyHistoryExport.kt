package com.lito.a5launcher.ui.components

import com.lito.a5launcher.JourneyHistoryKind
import com.lito.a5launcher.encodeJourneyStatistics
import java.io.OutputStream
import java.io.StringWriter
import java.io.Writer
import org.json.JSONObject

internal data class JourneyHistoryExportRequest(
    val records: List<JourneyHistoryItem>,
    val typeFilter: JourneyHistoryTypeFilter,
    val fromEpochMs: Long?,
    val untilEpochMs: Long?,
    val exportedAtEpochMs: Long,
)

internal fun encodeJourneyHistoryExport(request: JourneyHistoryExportRequest): String =
    StringWriter().also { writeJourneyHistoryExport(request, it) }.toString()

internal fun writeJourneyHistoryExport(request: JourneyHistoryExportRequest, writer: Writer) {
    val filters = JSONObject()
        .put("type", request.typeFilter.exportValue)
        .put("fromEpochMs", request.fromEpochMs ?: JSONObject.NULL)
        .put("untilEpochMsExclusive", request.untilEpochMs ?: JSONObject.NULL)

    writer.append('{')
        .append("\"schema\":").append(JOURNEY_HISTORY_EXPORT_SCHEMA.toString())
        .append(",\"exportedAtEpochMs\":").append(request.exportedAtEpochMs.toString())
        .append(",\"count\":").append(request.records.size.toString())
        .append(",\"filters\":").append(filters.toString())
        .append(",\"records\":[")
    request.records.forEachIndexed { index, record ->
        if (index > 0) writer.append(',')
        writer.append(encodeJourneyHistoryItem(record).toString())
    }
    writer.append("]}")
}

internal fun writeJourneyHistoryDocument(
    request: JourneyHistoryExportRequest,
    openOutput: () -> OutputStream?,
    deleteFailedDocument: () -> Unit,
): Boolean {
    val exported = runCatching {
        val output = openOutput() ?: return@runCatching false
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writeJourneyHistoryExport(request, writer)
        }
        true
    }.getOrDefault(false)
    if (!exported) runCatching(deleteFailedDocument)
    return exported
}

private fun encodeJourneyHistoryItem(item: JourneyHistoryItem): JSONObject = JSONObject()
    .put("id", item.id)
    .put("kind", item.kind.exportValue)
    .put("open", item.isOpen)
    .put("startedAtEpochMs", item.startedAtEpochMs)
    .put("endedAtEpochMs", item.endedAtEpochMs ?: JSONObject.NULL)
    .put("statistics", encodeJourneyStatistics(item.statistics))

private val JourneyHistoryTypeFilter.exportValue: String
    get() = when (this) {
        JourneyHistoryTypeFilter.ALL -> "all"
        JourneyHistoryTypeFilter.TRIPS -> "trips"
        JourneyHistoryTypeFilter.PARTIALS -> "partials"
    }

private val JourneyHistoryKind.exportValue: String
    get() = when (this) {
        JourneyHistoryKind.TRIP -> "trip"
        JourneyHistoryKind.PARTIAL -> "partial"
    }

private const val JOURNEY_HISTORY_EXPORT_SCHEMA = 1
