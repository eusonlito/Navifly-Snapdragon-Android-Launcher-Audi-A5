package com.lito.a5launcher.functional

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
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
    ): FunctionalEventDeleteResult = synchronized(bulkLock) {
        var deleted = 0L
        var preserved = 0L
        var corrupt = 0L
        snapshot.segments.forEach { segment ->
            if (!segment.file.isFile) return@forEach
            val temporary = File(segment.file.parentFile, ".${segment.file.name}.rewrite")
            var first: Long? = null
            var last: Long? = null
            FileOutputStream(temporary).bufferedWriter(StandardCharsets.UTF_8).use { output ->
                segment.file.useLines { lines ->
                    lines.forEach { line ->
                        val event = codec.decode(line).event
                        when {
                            event == null -> {
                                corrupt++
                                output.append(line).append('\n')
                            }
                            event.category == category -> deleted++
                            else -> {
                                preserved++
                                if (first == null) first = event.sequence
                                last = event.sequence
                                output.append(line).append('\n')
                            }
                        }
                    }
                }
            }
            FileOutputStream(temporary, true).use { it.fd.sync() }
            replaceAtomically(temporary, segment.file)
            val metadata = FunctionalEventJournal.metadataFile(segment.file)
            if (first == null || last == null) {
                metadata.delete()
            } else {
                FunctionalEventJournal.atomicWrite(
                    metadata,
                    JSONObject().put("first", first).put("last", last).toString(),
                )
            }
        }
        FunctionalEventDeleteResult(deleted, preserved, corrupt)
    }

    fun deleteAll(snapshot: FunctionalEventSnapshot): Long = synchronized(bulkLock) {
        snapshot.segments.count { segment ->
            FunctionalEventJournal.metadataFile(segment.file).delete()
            !segment.file.exists() || segment.file.delete()
        }.toLong()
    }

    private fun inspect(
        snapshot: FunctionalEventSnapshot,
        codec: FunctionalEventCodec,
    ): FunctionalEventArchiveManifest {
        var valid = 0L
        var corrupt = 0L
        snapshot.segments.filter { it.file.isFile }.forEach { segment ->
            segment.file.useLines { lines ->
                lines.forEach { if (codec.decode(it).event == null) corrupt++ else valid++ }
            }
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

    private fun replaceAtomically(source: File, target: File) {
        runCatching {
            java.nio.file.Files.move(
                source.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            java.nio.file.Files.move(
                source.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
