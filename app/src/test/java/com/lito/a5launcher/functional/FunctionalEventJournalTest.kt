package com.lito.a5launcher.functional

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlin.io.path.createTempDirectory

class FunctionalEventJournalTest {
    @Test
    fun `append persists monotonic sequence across restart and wall clock correction`() {
        withRoot { root ->
            FunctionalEventJournal(root, sequenceBlockSize = 4).use { journal ->
                assertTrue(journal.append(draft(epochMs = 2_000, index = 1)))
                journal.flush()
                assertEquals(1L, journal.page(limit = 10).events.single().sequence)
            }
            FunctionalEventJournal(root, sequenceBlockSize = 4).use { journal ->
                assertTrue(journal.append(draft(epochMs = 1_000, index = 2)))
                journal.flush()
                val events = journal.page(limit = 10).events
                assertEquals(listOf(5L, 1L), events.map(FunctionalEvent::sequence))
                assertTrue(events.first().capturedAtEpochMs < events.last().capturedAtEpochMs)
            }
        }
    }

    @Test
    fun `pages newest first without duplicates and skips corrupt line`() {
        withRoot { root ->
            FunctionalEventJournal(root, maxEventsPerSegment = 2).use { journal ->
                repeat(5) { journal.append(draft(epochMs = it.toLong(), index = it)) }
                journal.flush()
                journal.sealSnapshot().segments.first().file.appendText("{truncated\n")

                val first = journal.page(limit = 2)
                val second = journal.page(beforeSequence = first.nextBeforeSequence, limit = 2)
                val third = journal.page(beforeSequence = second.nextBeforeSequence, limit = 2)

                assertEquals(listOf(5L, 4L), first.events.map(FunctionalEvent::sequence))
                assertEquals(listOf(3L, 2L), second.events.map(FunctionalEvent::sequence))
                assertEquals(listOf(1L), third.events.map(FunctionalEvent::sequence))
                assertEquals(5, (first.events + second.events + third.events).distinctBy { it.sequence }.size)
                assertTrue(journal.stats().corruptLines >= 1)
                assertEquals(
                    mapOf(
                        FunctionalEventCategory.TRIP_SESSION to 3L,
                        FunctionalEventCategory.GEAR_ESTIMATION to 2L,
                    ),
                    journal.stats().categoryCounts,
                )
            }
        }
    }

    @Test
    fun `corrupt high water mark reconciles existing maximum before next append`() {
        withRoot { root ->
            FunctionalEventJournal(root, sequenceBlockSize = 2).use { journal ->
                repeat(3) { journal.append(draft(index = it)) }
                journal.flush()
            }
            File(root, FunctionalEventJournal.HIGH_WATER_FILE).writeText("not-a-number")
            FunctionalEventJournal(root, sequenceBlockSize = 2).use { journal ->
                journal.append(draft(index = 4))
                journal.flush()
                val sequences = journal.page(limit = 10).events.map(FunctionalEvent::sequence)
                assertEquals(sequences.distinct().size, sequences.size)
                assertTrue(sequences.zipWithNext().all { (a, b) -> a > b })
            }
        }
    }

    @Test
    fun `bounded queue rejects overflow without blocking caller`() {
        withRoot { root ->
            val release = CountDownLatch(1)
            val entered = CountDownLatch(1)
            val journal = FunctionalEventJournal(
                root = root,
                queueCapacity = 1,
                beforeWrite = { entered.countDown(); release.await(5, TimeUnit.SECONDS) },
            )
            try {
                assertTrue(journal.append(draft(index = 1)))
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                assertTrue(journal.append(draft(index = 2)))
                assertFalse(journal.append(draft(index = 3)))
                assertEquals(1L, journal.operationalState().droppedEvents)
            } finally {
                release.countDown()
                journal.close()
            }
        }
    }

    @Test
    fun `writer failure is observable and does not escape to producer`() {
        withRoot { root ->
            FunctionalEventJournal(root, beforeWrite = { error("disk unavailable") }).use { journal ->
                assertTrue(journal.append(draft(index = 1)))
                journal.flush()

                assertEquals(1L, journal.operationalState().failedWrites)
                assertTrue(journal.operationalState().lastError?.contains("disk unavailable") == true)
                assertTrue(journal.page(limit = 10).events.isEmpty())
            }
        }
    }

    @Test
    fun `writer initialization failure still completes control operations`() {
        val invalidRoot = createTempDirectory("functional-journal-invalid-").toFile()
        invalidRoot.deleteRecursively()
        invalidRoot.writeText("not a directory")
        val journal = FunctionalEventJournal(invalidRoot)
        try {
            assertTrue(journal.append(draft(index = 1)))
            journal.flush()
            assertTrue(journal.operationalState().failedWrites >= 1)
            assertTrue(runCatching { journal.sealSnapshot() }.isFailure)
        } finally {
            journal.close()
            invalidRoot.delete()
        }
    }

    @Test
    fun `metadata-less crash segment is recovered before pagination`() {
        withRoot { root ->
            FunctionalEventJournal(root).use { journal ->
                journal.append(draft(index = 1))
                journal.flush()
            }
            val crashSegment = File(root, "segment-crash.jsonl")
            crashSegment.writeText(
                FunctionalEventCodec().encode(draft(index = 2).withSequence(100)) + "\n",
            )

            FunctionalEventJournal(root).use { journal ->
                assertEquals(100L, journal.page(limit = 1).events.single().sequence)
                assertTrue(FunctionalEventJournal.metadataFile(crashSegment).isFile)
            }
        }
    }

    @Test
    fun `close returns within its budget when writer is stuck`() {
        withRoot { root ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val journal = FunctionalEventJournal(
                root = root,
                closeTimeoutMs = 100,
                beforeWrite = {
                    entered.countDown()
                    while (release.count > 0) runCatching { release.await() }
                },
            )
            journal.append(draft(index = 1))
            assertTrue(entered.await(1, TimeUnit.SECONDS))

            val elapsed = measureTimeMillis { journal.close() }

            release.countDown()
            assertTrue("close took ${elapsed}ms", elapsed < 1_000)
            assertTrue(journal.operationalState().failedWrites >= 1)
        }
    }

    @Test
    fun `seal failure completes caller exceptionally instead of hanging`() {
        withRoot { root ->
            val journal = FunctionalEventJournal(root)
            try {
                journal.append(draft(index = 1))
                journal.flush()
                root.deleteRecursively()
                root.writeText("not a directory")

                val elapsed = measureTimeMillis {
                    assertTrue(runCatching { journal.sealSnapshot() }.isFailure)
                }
                assertTrue("seal took ${elapsed}ms", elapsed < 1_000)
            } finally {
                journal.close()
                root.delete()
            }
        }
    }

    @Test
    fun `control operation times out when writer is stuck`() {
        withRoot { root ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val journal = FunctionalEventJournal(
                root = root,
                controlTimeoutMs = 100,
                beforeWrite = {
                    entered.countDown()
                    while (release.count > 0) runCatching { release.await() }
                },
            )
            try {
                journal.append(draft(index = 1))
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                val elapsed = measureTimeMillis {
                    assertTrue(runCatching { journal.sealSnapshot() }.isFailure)
                }
                assertTrue("seal took ${elapsed}ms", elapsed < 1_000)
            } finally {
                release.countDown()
                journal.close()
            }
        }
    }

    @Test
    fun `timed out seal is cancelled and never runs later`() {
        withRoot { root ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            var firstWrite = true
            val journal = FunctionalEventJournal(
                root = root,
                controlTimeoutMs = 100,
                beforeWrite = {
                    if (firstWrite) {
                        firstWrite = false
                        entered.countDown()
                        while (release.count > 0) runCatching { release.await() }
                    }
                },
            )
            try {
                journal.append(draft(index = 1))
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                assertTrue(runCatching { journal.sealSnapshot() }.isFailure)
                release.countDown()
                journal.flush()
                journal.append(draft(index = 2))
            } finally {
                release.countDown()
                journal.close()
            }

            val segments = root.listFiles().orEmpty().filter { it.extension == "jsonl" }
            assertEquals(1, segments.size)
            assertEquals(2, segments.single().readLines().size)
        }
    }

    private fun draft(epochMs: Long = 1_000, index: Int) = FunctionalEventDraft(
        bootSession = 2,
        capturedAtEpochMs = epochMs,
        capturedAtElapsedMs = index.toLong(),
        source = FunctionalEventSource.EVENT_CENTER,
        category = if (index % 2 == 0) FunctionalEventCategory.TRIP_SESSION else FunctionalEventCategory.GEAR_ESTIMATION,
        type = FunctionalEventType("test.$index"),
        context = mapOf("index" to FunctionalEventValue.Integer(index.toLong())),
    )

    private inline fun withRoot(block: (File) -> Unit) {
        val root = createTempDirectory("functional-journal-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
