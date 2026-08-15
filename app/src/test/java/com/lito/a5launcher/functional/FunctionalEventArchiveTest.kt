package com.lito.a5launcher.functional

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
