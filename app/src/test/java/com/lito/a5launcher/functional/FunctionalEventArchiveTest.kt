package com.lito.a5launcher.functional

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.io.path.createTempDirectory

class FunctionalEventArchiveTest {
    @Test
    fun `export writes manifest and sealed segments without deleting originals`() {
        withJournal { journal, _ ->
            repeat(3) { journal.append(draft(it)) }
            journal.flush()
            val snapshot = journal.sealSnapshot()
            val output = ByteArrayOutputStream()

            val manifest = FunctionalEventArchive.export(snapshot, output)
            val names = zipNames(output.toByteArray())

            assertEquals(3L, manifest.validEvents)
            assertTrue("manifest.json" in names)
            assertTrue(names.any { it.startsWith("functional-event-journal/") && it.endsWith(".jsonl") })
            assertTrue(snapshot.segments.all { it.file.exists() })
            assertEquals(3L, journal.stats().validEvents)
        }
    }

    @Test
    fun `category deletion preserves other events corrupt lines and later appends`() {
        withJournal { journal, _ ->
            journal.append(draft(1, FunctionalEventCategory.GEAR_ESTIMATION))
            journal.append(draft(2, FunctionalEventCategory.TRIP_SESSION))
            journal.flush()
            val snapshot = journal.sealSnapshot()
            snapshot.segments.first().file.appendText("corrupt-line\n")
            journal.append(draft(3, FunctionalEventCategory.GEAR_ESTIMATION))

            val result = FunctionalEventArchive.deleteCategory(
                snapshot,
                FunctionalEventCategory.GEAR_ESTIMATION,
                FunctionalEventCodec(),
            )
            journal.flush()

            assertEquals(1L, result.deletedEvents)
            assertEquals(listOf("test.3", "test.2"), journal.page(limit = 10).events.map { it.type.code })
            assertTrue(journal.stats().corruptLines >= 1)
        }
    }

    @Test
    fun `event deletion removes only the selected sequence and preserves later appends`() {
        withJournal { journal, _ ->
            journal.append(draft(1, FunctionalEventCategory.GEAR_ESTIMATION))
            journal.append(draft(2, FunctionalEventCategory.TRIP_SESSION))
            journal.flush()
            val snapshot = journal.sealSnapshot()
            snapshot.segments.first().file.appendText("corrupt-line\n")
            journal.append(draft(3, FunctionalEventCategory.GEAR_ESTIMATION))

            val result = FunctionalEventArchive.deleteEvent(
                snapshot,
                sequence = 1L,
                codec = FunctionalEventCodec(),
            )
            journal.flush()

            assertEquals(1L, result.deletedEvents)
            assertEquals(listOf("test.3", "test.2"), journal.page(limit = 10).events.map { it.type.code })
            assertTrue(journal.stats().corruptLines >= 1)
        }
    }

    @Test
    fun `event deletion rewrites only the sealed segment containing the sequence`() {
        withJournal { journal, _ ->
            journal.append(draft(1))
            journal.flush()
            journal.sealSnapshot()
            journal.append(draft(2))
            journal.flush()
            val snapshot = journal.sealSnapshot()
            val target = journal.page(limit = 10).events.single { it.type.code == "test.2" }
            val preservedSegment = snapshot.segments.single { segment ->
                val first = requireNotNull(segment.firstSequence)
                val last = requireNotNull(segment.lastSequence)
                target.sequence !in first..last
            }
            val preservedBytes = preservedSegment.file.readBytes()
            val preservedMetadata = FunctionalEventJournal.metadataFile(preservedSegment.file).readBytes()

            val result = FunctionalEventArchive.deleteEvent(
                snapshot,
                sequence = target.sequence,
                codec = FunctionalEventCodec(),
            )

            assertEquals(1L, result.deletedEvents)
            assertEquals(listOf("test.1"), journal.page(limit = 10).events.map { it.type.code })
            assertTrue(preservedBytes.contentEquals(preservedSegment.file.readBytes()))
            assertTrue(
                preservedMetadata.contentEquals(
                    FunctionalEventJournal.metadataFile(preservedSegment.file).readBytes(),
                ),
            )
        }
    }

    @Test
    fun `deleting an absent event is a no-op without transaction residue`() {
        withJournal { journal, root ->
            journal.append(draft(1))
            journal.flush()
            val snapshot = journal.sealSnapshot()

            val result = FunctionalEventArchive.deleteEvent(
                snapshot,
                sequence = Long.MAX_VALUE,
                codec = FunctionalEventCodec(),
            )

            assertEquals(0L, result.deletedEvents)
            assertEquals(1L, result.preservedEvents)
            assertFalse(File(root, ".delete-transaction.json").exists())
            assertEquals("test.1", journal.page(limit = 1).events.single().type.code)
        }
    }

    @Test
    fun `delete all only removes the sealed snapshot`() {
        withJournal { journal, _ ->
            journal.append(draft(1))
            journal.flush()
            val snapshot = journal.sealSnapshot()
            journal.append(draft(2))
            assertEquals(1L, FunctionalEventArchive.deleteAll(snapshot))
            journal.flush()

            assertEquals(listOf("test.2"), journal.page(limit = 10).events.map { it.type.code })
        }
    }

    @Test
    fun `category deletion removes a segment when no lines remain`() {
        withJournal { journal, _ ->
            journal.append(draft(1, FunctionalEventCategory.GEAR_ESTIMATION))
            journal.flush()
            val snapshot = journal.sealSnapshot()

            val result = FunctionalEventArchive.deleteCategory(
                snapshot,
                FunctionalEventCategory.GEAR_ESTIMATION,
                FunctionalEventCodec(),
            )

            assertEquals(1L, result.deletedEvents)
            assertFalse(snapshot.segments.single().file.exists())
        }
    }

    @Test
    fun `deleting an absent category is a no-op without transaction residue`() {
        withJournal { journal, root ->
            journal.append(draft(1, FunctionalEventCategory.TRIP_SESSION))
            journal.flush()
            val snapshot = journal.sealSnapshot()

            val result = FunctionalEventArchive.deleteCategory(
                snapshot,
                FunctionalEventCategory.GEAR_ESTIMATION,
                FunctionalEventCodec(),
            )

            assertEquals(0L, result.deletedEvents)
            assertEquals(1L, result.preservedEvents)
            assertFalse(File(root, ".delete-transaction.json").exists())
            assertEquals("test.1", journal.page(limit = 1).events.single().type.code)
        }
    }

    @Test
    fun `append proceeds while an export owns a sealed snapshot`() {
        withJournal { journal, _ ->
            journal.append(draft(1))
            journal.flush()
            val snapshot = journal.sealSnapshot()
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val output = object : ByteArrayOutputStream() {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    super.write(b, off, len)
                }
            }
            val export = Thread { FunctionalEventArchive.export(snapshot, output) }.apply { start() }
            assertTrue(started.await(2, TimeUnit.SECONDS))

            assertTrue(journal.append(draft(2)))
            journal.flush()
            assertEquals("test.2", journal.page(limit = 1).events.single().type.code)
            release.countDown()
            export.join(2_000)
            assertFalse(export.isAlive)
        }
    }

    @Test
    fun `prepared delete transaction is rolled back after interruption`() {
        withJournal { journal, root ->
            journal.append(draft(1))
            journal.flush()
            val segment = journal.sealSnapshot().segments.single()
            val backup = File(root, ".delete-backup-test-${segment.file.name}")
            java.nio.file.Files.move(segment.file.toPath(), backup.toPath())
            segment.file.writeText("replacement-that-must-not-survive\n")
            File(root, ".delete-transaction.json").writeText(
                JSONObject()
                    .put("state", "prepared")
                    .put(
                        "entries",
                        JSONArray().put(
                            JSONObject()
                                .put("original", segment.file.name)
                                .put("backup", backup.name)
                                .put("hadOriginal", true),
                        ),
                    )
                    .toString(),
            )

            FunctionalEventArchive.recoverInterruptedDelete(root)

            assertEquals("test.1", journal.page(limit = 1).events.single().type.code)
            assertFalse(backup.exists())
            assertFalse(File(root, ".delete-transaction.json").exists())
        }
    }

    @Test
    fun `staging transaction removes orphan rewrites without touching original`() {
        withJournal { journal, root ->
            journal.append(draft(1))
            journal.flush()
            val segment = journal.sealSnapshot().segments.single()
            val staged = File(root, ".delete-stage-test-${segment.file.name}").apply {
                writeText("orphaned rewrite\n")
            }
            File(root, ".delete-transaction.json").writeText(
                JSONObject()
                    .put("state", "staging")
                    .put(
                        "entries",
                        JSONArray().put(
                            JSONObject()
                                .put("original", segment.file.name)
                                .put("staged", staged.name)
                                .put("backup", ".delete-backup-test-${segment.file.name}")
                                .put("hadOriginal", true),
                        ),
                    )
                    .toString(),
            )

            FunctionalEventArchive.recoverInterruptedDelete(root)

            assertEquals("test.1", journal.page(limit = 1).events.single().type.code)
            assertFalse(staged.exists())
            assertFalse(File(root, ".delete-transaction.json").exists())
        }
    }

    private fun draft(index: Int, category: FunctionalEventCategory = FunctionalEventCategory.TRIP_SESSION) =
        FunctionalEventDraft(
            bootSession = 1,
            capturedAtEpochMs = index.toLong(),
            capturedAtElapsedMs = index.toLong(),
            source = FunctionalEventSource.REPLAY,
            category = category,
            type = FunctionalEventType("test.$index"),
            context = emptyMap(),
        )

    private fun zipNames(bytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                entry = zip.nextEntry
            }
        }
        return names
    }

    private inline fun withJournal(block: (FunctionalEventJournal, File) -> Unit) {
        val root = createTempDirectory("functional-archive-").toFile()
        try { FunctionalEventJournal(root).use { block(it, root) } } finally { root.deleteRecursively() }
    }
}
