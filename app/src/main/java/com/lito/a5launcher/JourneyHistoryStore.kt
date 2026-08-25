package com.lito.a5launcher

import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class JourneyHistoryKind {
    TRIP,
    PARTIAL,
}

data class JourneyHistoryRecord(
    val id: String,
    val kind: JourneyHistoryKind,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val statistics: JourneyStatisticsSnapshot,
)

data class JourneyHistorySnapshot(
    val enabled: Boolean = true,
    val records: List<JourneyHistoryRecord> = emptyList(),
)

internal class JourneyHistoryStore(
    private val root: File,
    private val codec: JourneyHistoryCodec = JourneyHistoryCodec(),
) {
    @Synchronized
    fun append(record: JourneyHistoryRecord): Boolean {
        if (!record.isValid()) return false
        ensureRoot()
        val destination = recordFile(record.id) ?: return false
        if (destination.exists()) return false
        val pending = File.createTempFile(PENDING_PREFIX, JSON_SUFFIX, root)
        return try {
            pending.writeText(codec.encode(record))
            try {
                Files.move(
                    pending.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending.toPath(), destination.toPath())
            }
            true
        } catch (_: FileAlreadyExistsException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            pending.delete()
        }
    }

    @Synchronized
    fun readAll(): List<JourneyHistoryRecord> = root.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.name.startsWith(RECORD_PREFIX) && it.name.endsWith(JSON_SUFFIX) }
        .mapNotNull { file -> runCatching { codec.decode(file.readText()) }.getOrNull() }
        .filter(JourneyHistoryRecord::isValid)
        .sortedWith(
            compareByDescending<JourneyHistoryRecord> { it.endedAtEpochMs }
                .thenByDescending { it.startedAtEpochMs }
                .thenByDescending { it.id },
        )
        .toList()

    @Synchronized
    fun delete(id: String): Boolean = recordFile(id)?.let { file ->
        file.exists() && file.delete()
    } ?: false

    @Synchronized
    fun contains(id: String): Boolean = recordFile(id)?.isFile == true

    @Synchronized
    fun deleteAll(): Int = root.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.startsWith(RECORD_PREFIX) && it.name.endsWith(JSON_SUFFIX) }
        .count(File::delete)

    private fun ensureRoot() {
        check(root.isDirectory || root.mkdirs()) { "Journey history directory is unavailable" }
    }

    private fun recordFile(id: String): File? = id
        .takeIf { SAFE_ID.matches(it) }
        ?.let { File(root, "$RECORD_PREFIX$it$JSON_SUFFIX") }

    companion object {
        const val DIRECTORY_NAME = "journey-history"
        private const val RECORD_PREFIX = "record-"
        private const val PENDING_PREFIX = ".pending-"
        private const val JSON_SUFFIX = ".json"
        private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,128}")
    }
}

internal class JourneyHistoryCodec {
    fun encode(record: JourneyHistoryRecord): String = JSONObject()
        .put("schema", SCHEMA)
        .put("id", record.id)
        .put("kind", record.kind.name)
        .put("startedAtEpochMs", record.startedAtEpochMs)
        .put("endedAtEpochMs", record.endedAtEpochMs)
        .put("statistics", encodeStatistics(record.statistics))
        .toString()

    fun decode(value: String): JourneyHistoryRecord {
        val json = JSONObject(value)
        require(json.optInt("schema") == SCHEMA)
        val statistics = json.getJSONObject("statistics")
        return JourneyHistoryRecord(
            id = json.getString("id"),
            kind = JourneyHistoryKind.valueOf(json.getString("kind")),
            startedAtEpochMs = json.getLong("startedAtEpochMs"),
            endedAtEpochMs = json.getLong("endedAtEpochMs"),
            statistics = JourneyStatisticsSnapshot(
                elapsedMs = statistics.getLong("elapsedMs").coerceAtLeast(0L),
                movingElapsedMs = statistics.getLong("movingElapsedMs").coerceAtLeast(0L),
                distanceKm = statistics.nonNegativeDouble("distanceKm"),
                maximumSpeedKmh = validMaximumSpeedKmh(statistics.getInt("maximumSpeedKmh")),
                averageSpeedKmh = statistics.nonNegativeDouble("averageSpeedKmh"),
                movingAverageSpeedKmh = statistics.nonNegativeDouble("movingAverageSpeedKmh"),
                calculatedConsumption = statistics.nonNegativeDouble("calculatedConsumption"),
                observedCanConsumption = statistics.nonNegativeDouble("observedCanConsumption"),
                fuelUsedLitres = statistics.nonNegativeDouble("fuelUsedLitres"),
                confirmedCanFuelUsedLitres = statistics.nonNegativeDouble(
                    "confirmedCanFuelUsedLitres",
                ),
                observedFuelSpentLitres = statistics.optionalNonNegativeDouble(
                    "observedFuelSpentLitres",
                ),
            ),
        )
    }

    private fun encodeStatistics(statistics: JourneyStatisticsSnapshot) = JSONObject()
        .put("elapsedMs", statistics.elapsedMs)
        .put("movingElapsedMs", statistics.movingElapsedMs)
        .put("distanceKm", statistics.distanceKm)
        .put("maximumSpeedKmh", statistics.maximumSpeedKmh)
        .put("averageSpeedKmh", statistics.averageSpeedKmh)
        .put("movingAverageSpeedKmh", statistics.movingAverageSpeedKmh)
        .put("calculatedConsumption", statistics.calculatedConsumption)
        .put("observedCanConsumption", statistics.observedCanConsumption)
        .put("fuelUsedLitres", statistics.fuelUsedLitres)
        .put("confirmedCanFuelUsedLitres", statistics.confirmedCanFuelUsedLitres)
        .put("observedFuelSpentLitres", statistics.observedFuelSpentLitres ?: JSONObject.NULL)

    private fun JSONObject.nonNegativeDouble(key: String): Double = optDouble(key)
        .takeIf { it.isFinite() && it >= 0.0 }
        ?: 0.0

    private fun JSONObject.optionalNonNegativeDouble(key: String): Double? =
        takeUnless { isNull(key) }
            ?.optDouble(key)
            ?.takeIf { it.isFinite() && it >= 0.0 }

    private companion object {
        const val SCHEMA = 1
    }
}

internal fun JourneyHistoryRecord.isValid(): Boolean =
    id.isNotBlank() &&
        startedAtEpochMs > 0L &&
        endedAtEpochMs >= startedAtEpochMs &&
        statistics.hasJourneyActivity()

internal fun JourneyStatisticsSnapshot.hasJourneyActivity(): Boolean =
    elapsedMs > 0L || distanceKm > 0.0
