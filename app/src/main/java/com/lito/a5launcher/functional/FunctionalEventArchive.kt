package com.lito.a5launcher.functional

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class FunctionalEventArchiveManifest(
    val schema: Int,
    val validEvents: Long,
    val corruptLines: Long,
    val sizeBytes: Long,
    val segmentCount: Int,
)

data class FunctionalEventDeleteResult(
    val deletedEvents: Long,
    val preservedEvents: Long,
    val preservedCorruptLines: Long,
)

object FunctionalEventArchive {
    private val bulkLock = Any()

    private data class DeleteEntry(
        val originalName: String,
        val stagedName: String?,
        val backupName: String,
        val hadOriginal: Boolean,
    )

    private data class RewriteStage(
        val segment: FunctionalEventSegment,
        val token: String,
        val stagedSegment: File,
        val stagedMetadata: File,
    )

    fun export(snapshot: FunctionalEventSnapshot, output: OutputStream): FunctionalEventArchiveManifest =
        synchronized(bulkLock) {
            val manifest = inspect(snapshot, FunctionalEventCodec())
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifestJson(manifest, snapshot).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
                snapshot.segments.filter { it.file.isFile }.forEach { segment ->
                    zip.putNextEntry(ZipEntry("functional-event-journal/${segment.file.name}"))
                    segment.file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            manifest
        }

    fun deleteCategory(
        snapshot: FunctionalEventSnapshot,
        category: FunctionalEventCategory,
        codec: FunctionalEventCodec,
    ): FunctionalEventDeleteResult = deleteMatching(
        snapshot = snapshot,
        codec = codec,
        shouldRewrite = { segment -> (segment.categoryCounts[category] ?: 0L) > 0L },
        shouldDelete = { event -> event.category == category },
    )

    fun deleteEvent(
        snapshot: FunctionalEventSnapshot,
        sequence: Long,
        codec: FunctionalEventCodec,
    ): FunctionalEventDeleteResult = deleteMatching(
        snapshot = snapshot,
        codec = codec,
        shouldRewrite = { segment ->
            val first = segment.firstSequence
            val last = segment.lastSequence
            first == null || last == null || sequence in first..last
        },
        shouldDelete = { event -> event.sequence == sequence },
    )

    private fun deleteMatching(
        snapshot: FunctionalEventSnapshot,
        codec: FunctionalEventCodec,
        shouldRewrite: (FunctionalEventSegment) -> Boolean,
        shouldDelete: (FunctionalEvent) -> Boolean,
    ): FunctionalEventDeleteResult = synchronized(bulkLock) {
        val root = snapshot.segments.firstOrNull()?.file?.parentFile
            ?: return@synchronized FunctionalEventDeleteResult(0, 0, 0)
        recoverInterruptedDeleteLocked(root)
        var deleted = 0L
        var preserved = 0L
        var corrupt = 0L
        val entries = mutableListOf<DeleteEntry>()
        val stagedFiles = mutableListOf<File>()
        val resolvedSegments = snapshot.segments.filter { it.file.isFile }.map { segment ->
            FunctionalEventJournal.resolveSegmentStats(segment, codec)
        }
        val (stagesToRewrite, preservedSegments) = resolvedSegments.partition(shouldRewrite)
        preservedSegments.forEach { segment ->
            preserved += segment.validEvents ?: 0L
            corrupt += segment.corruptLines ?: 0L
        }
        val stages = stagesToRewrite.map { segment ->
            val token = UUID.randomUUID().toString()
            val metadata = FunctionalEventJournal.metadataFile(segment.file)
            RewriteStage(
                segment = segment,
                token = token,
                stagedSegment = File(root, ".delete-stage-$token-${segment.file.name}"),
                stagedMetadata = File(root, ".delete-stage-$token-${metadata.name}"),
            )
        }
        if (stages.isEmpty()) {
            return@synchronized FunctionalEventDeleteResult(0, preserved, corrupt)
        }
        writeTransaction(
            root,
            TRANSACTION_STAGING,
            stages.flatMap { stage ->
                listOf(
                    deleteEntry(stage.segment.file, stage.stagedSegment, stage.token),
                    deleteEntry(
                        FunctionalEventJournal.metadataFile(stage.segment.file),
                        stage.stagedMetadata,
                        stage.token,
                    ),
                )
            },
        )
        try {
            stages.forEach { stage ->
                val segment = stage.segment
                val stagedSegment = stage.stagedSegment
                var first: Long? = null
                var last: Long? = null
                var segmentValid = 0L
                var segmentCorrupt = 0L
                val segmentCategories = FunctionalEventCategory.entries
                    .associateWithTo(mutableMapOf()) { 0L }
                FileOutputStream(stagedSegment).bufferedWriter(StandardCharsets.UTF_8).use { output ->
                    segment.file.useLines { lines ->
                        lines.forEach { line ->
                            val event = codec.decode(line).event
                            when {
                                event == null -> {
                                    corrupt++
                                    segmentCorrupt++
                                    output.append(line).append('\n')
                                }
                                shouldDelete(event) -> deleted++
                                else -> {
                                    preserved++
                                    segmentValid++
                                    segmentCategories[event.category] =
                                        segmentCategories.getValue(event.category) + 1L
                                    first = minOf(first ?: event.sequence, event.sequence)
                                    last = maxOf(last ?: event.sequence, event.sequence)
                                    output.append(line).append('\n')
                                }
                            }
                        }
                    }
                }
                FileOutputStream(stagedSegment, true).use { it.fd.sync() }
                val keepSegment = stagedSegment.length() > 0L
                if (!keepSegment) stagedSegment.delete() else stagedFiles += stagedSegment
                entries += deleteEntry(
                    segment.file,
                    stagedSegment.takeIf { keepSegment },
                    stage.token,
                )

                val metadata = FunctionalEventJournal.metadataFile(segment.file)
                val stagedMetadata = if (keepSegment) {
                    stage.stagedMetadata.also { staged ->
                        FunctionalEventJournal.writeSegmentMetadata(
                            stagedSegment,
                            first,
                            last,
                            segmentValid,
                            segmentCorrupt,
                            segmentCategories,
                        )
                        stagedFiles += staged
                    }
                } else null
                entries += deleteEntry(metadata, stagedMetadata, stage.token)
            }
            commitDeleteTransaction(root, entries)
        } catch (error: Throwable) {
            stagedFiles.forEach(File::delete)
            runCatching { recoverInterruptedDeleteLocked(root) }.onFailure(error::addSuppressed)
            throw error
        }
        FunctionalEventDeleteResult(deleted, preserved, corrupt)
    }

    fun deleteAll(snapshot: FunctionalEventSnapshot): Long = synchronized(bulkLock) {
        val root = snapshot.segments.firstOrNull()?.file?.parentFile
            ?: return@synchronized inspect(snapshot, FunctionalEventCodec()).validEvents
        recoverInterruptedDeleteLocked(root)
        val deletedEvents = inspect(snapshot, FunctionalEventCodec()).validEvents
        val token = UUID.randomUUID().toString()
        val entries = snapshot.segments.flatMap { segment ->
            listOf(
                deleteEntry(segment.file, null, token),
                deleteEntry(FunctionalEventJournal.metadataFile(segment.file), null, token),
            )
        }
        commitDeleteTransaction(root, entries)
        deletedEvents
    }

    fun recoverInterruptedDelete(root: File) = synchronized(bulkLock) {
        recoverInterruptedDeleteLocked(root)
    }

    private fun deleteEntry(original: File, staged: File?, token: String): DeleteEntry = DeleteEntry(
        originalName = original.name,
        stagedName = staged?.name,
        backupName = ".delete-backup-$token-${original.name}",
        hadOriginal = original.isFile,
    )

    private fun commitDeleteTransaction(root: File, entries: List<DeleteEntry>) {
        if (entries.isEmpty()) return
        writeTransaction(root, TRANSACTION_PREPARED, entries)
        try {
            entries.forEach { entry ->
                val original = File(root, entry.originalName)
                val backup = File(root, entry.backupName)
                if (entry.hadOriginal && original.exists()) moveAtomically(original, backup)
                entry.stagedName?.let { stagedName ->
                    val staged = File(root, stagedName)
                    if (staged.exists()) moveAtomically(staged, original)
                }
            }
            writeTransaction(root, TRANSACTION_COMMITTED, entries)
            cleanupCommitted(root, entries)
        } catch (error: Throwable) {
            runCatching { recoverInterruptedDeleteLocked(root) }.onFailure(error::addSuppressed)
            throw error
        }
    }

    private fun recoverInterruptedDeleteLocked(root: File) {
        val transaction = File(root, DELETE_TRANSACTION_FILE)
        if (!transaction.isFile) return
        val json = JSONObject(transaction.readText())
        val state = json.getString("state")
        val values = json.getJSONArray("entries")
        val entries = buildList {
            for (index in 0 until values.length()) {
                val value = values.getJSONObject(index)
                add(
                    DeleteEntry(
                        originalName = value.getString("original"),
                        stagedName = value.optString("staged").takeIf(String::isNotBlank),
                        backupName = value.getString("backup"),
                        hadOriginal = value.getBoolean("hadOriginal"),
                    )
                )
            }
        }
        if (state == TRANSACTION_STAGING) {
            entries.forEach { entry ->
                entry.stagedName?.let { Files.deleteIfExists(File(root, it).toPath()) }
            }
            Files.deleteIfExists(transaction.toPath())
            return
        }
        if (state == TRANSACTION_COMMITTED) {
            cleanupCommitted(root, entries)
            return
        }
        entries.asReversed().forEach { entry ->
            val original = File(root, entry.originalName)
            val backup = File(root, entry.backupName)
            if (backup.exists()) {
                Files.deleteIfExists(original.toPath())
                moveAtomically(backup, original)
            } else if (!entry.hadOriginal) {
                Files.deleteIfExists(original.toPath())
            }
            entry.stagedName?.let { Files.deleteIfExists(File(root, it).toPath()) }
        }
        Files.deleteIfExists(transaction.toPath())
    }

    private fun cleanupCommitted(root: File, entries: List<DeleteEntry>) {
        entries.forEach { entry ->
            Files.deleteIfExists(File(root, entry.backupName).toPath())
            entry.stagedName?.let { Files.deleteIfExists(File(root, it).toPath()) }
        }
        Files.deleteIfExists(File(root, DELETE_TRANSACTION_FILE).toPath())
    }

    private fun writeTransaction(root: File, state: String, entries: List<DeleteEntry>) {
        val json = JSONObject()
            .put("state", state)
            .put(
                "entries",
                JSONArray(entries.map { entry ->
                    JSONObject()
                        .put("original", entry.originalName)
                        .putOpt("staged", entry.stagedName)
                        .put("backup", entry.backupName)
                        .put("hadOriginal", entry.hadOriginal)
                }),
            )
        FunctionalEventJournal.atomicWrite(File(root, DELETE_TRANSACTION_FILE), json.toString())
    }

    private fun inspect(
        snapshot: FunctionalEventSnapshot,
        codec: FunctionalEventCodec,
    ): FunctionalEventArchiveManifest {
        var valid = 0L
        var corrupt = 0L
        snapshot.segments.filter { it.file.isFile }.forEach { segment ->
            val resolved = FunctionalEventJournal.resolveSegmentStats(segment, codec)
            valid += resolved.validEvents ?: 0L
            corrupt += resolved.corruptLines ?: 0L
        }
        return FunctionalEventArchiveManifest(
            schema = FunctionalEventCodec.SCHEMA,
            validEvents = valid,
            corruptLines = corrupt,
            sizeBytes = snapshot.segments.sumOf { it.file.length() },
            segmentCount = snapshot.segments.count { it.file.isFile },
        )
    }

    private fun manifestJson(
        manifest: FunctionalEventArchiveManifest,
        snapshot: FunctionalEventSnapshot,
    ): String = JSONObject()
        .put("schema", manifest.schema)
        .put("validEvents", manifest.validEvents)
        .put("corruptLines", manifest.corruptLines)
        .put("sizeBytes", manifest.sizeBytes)
        .put("segmentCount", manifest.segmentCount)
        .put(
            "segments",
            JSONArray(snapshot.segments.map { segment ->
                JSONObject()
                    .put("name", segment.file.name)
                    .putOpt("firstSequence", segment.firstSequence)
                    .putOpt("lastSequence", segment.lastSequence)
                    .put("sizeBytes", segment.file.length())
            }),
        )
        .toString(2)

    private fun moveAtomically(source: File, target: File) {
        FunctionalEventJournal.moveReplacingAtomically(source, target)
    }

    private const val DELETE_TRANSACTION_FILE = ".delete-transaction.json"
    private const val TRANSACTION_STAGING = "staging"
    private const val TRANSACTION_PREPARED = "prepared"
    private const val TRANSACTION_COMMITTED = "committed"
}
