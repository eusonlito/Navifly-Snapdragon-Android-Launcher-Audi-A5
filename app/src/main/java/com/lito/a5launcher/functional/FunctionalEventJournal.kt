package com.lito.a5launcher.functional

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

data class FunctionalEventPage(
    val events: List<FunctionalEvent>,
    val nextBeforeSequence: Long?,
)

data class FunctionalEventJournalStats(
    val validEvents: Long,
    val sizeBytes: Long,
    val corruptLines: Long,
    val categoryCounts: Map<FunctionalEventCategory, Long> = emptyMap(),
)

data class FunctionalEventOperationalState(
    val droppedEvents: Long,
    val failedWrites: Long,
    val lastError: String?,
)

data class FunctionalEventSegment(
    val file: File,
    val firstSequence: Long?,
    val lastSequence: Long?,
    val validEvents: Long? = null,
    val corruptLines: Long? = null,
    val categoryCounts: Map<FunctionalEventCategory, Long> = emptyMap(),
    val recordedSizeBytes: Long? = null,
)

data class FunctionalEventSnapshot(
    val segments: List<FunctionalEventSegment>,
)

class FunctionalEventJournal(
    private val root: File,
    private val codec: FunctionalEventCodec = FunctionalEventCodec(),
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val sequenceBlockSize: Int = DEFAULT_SEQUENCE_BLOCK_SIZE,
    private val maxEventsPerSegment: Int = DEFAULT_MAX_EVENTS_PER_SEGMENT,
    private val closeTimeoutMs: Long = DEFAULT_CLOSE_TIMEOUT_MS,
    private val controlTimeoutMs: Long = DEFAULT_CONTROL_TIMEOUT_MS,
    private val beforeWrite: () -> Unit = {},
) : AutoCloseable {
    private sealed interface Command {
        data class Append(val draft: FunctionalEventDraft) : Command
        data class Flush(val result: CompletableFuture<Unit>) : Command
        data class Seal(val result: CompletableFuture<FunctionalEventSnapshot>) : Command
        data class Stop(val result: CompletableFuture<Unit>) : Command
    }

    private val appendQueueCapacity = queueCapacity.also { require(it > 0) }
    private val queue = ArrayBlockingQueue<Command>(queueCapacity + CONTROL_QUEUE_RESERVE)
    private val queuedAppends = AtomicLong()
    private val droppedEvents = AtomicLong()
    private val failedWrites = AtomicLong()
    @Volatile private var lastError: String? = null
    @Volatile private var closed = false
    @Volatile private var shutdownRequested = false
    @Volatile private var activeDescriptor: FunctionalEventSegment? = null
    private val enqueueLock = Any()
    private val worker = Thread(::writerLoop, "functional-event-journal").apply { isDaemon = true }

    init {
        require(sequenceBlockSize > 0)
        require(maxEventsPerSegment > 0)
        require(closeTimeoutMs > 0)
        require(controlTimeoutMs > 0)
        root.mkdirs()
        worker.start()
    }

    fun append(draft: FunctionalEventDraft): Boolean = synchronized(enqueueLock) {
        if (closed) return false
        if (queuedAppends.get() >= appendQueueCapacity) {
            droppedEvents.incrementAndGet()
            return false
        }
        val accepted = queue.offer(Command.Append(draft))
        if (accepted) queuedAppends.incrementAndGet() else droppedEvents.incrementAndGet()
        accepted
    }

    fun flush() {
        submitControl { Command.Flush(it) }
    }

    fun sealSnapshot(): FunctionalEventSnapshot = submitControl { Command.Seal(it) }

    fun page(beforeSequence: Long? = null, limit: Int): FunctionalEventPage {
        require(limit > 0)
        val events = ArrayList<FunctionalEvent>(limit)
        val segments = discoverSegments().sortedWith(
            compareByDescending<FunctionalEventSegment> { it.lastSequence ?: Long.MIN_VALUE }
                .thenByDescending { it.file.lastModified() },
        )
        for (segment in segments) {
            if (events.size >= limit) break
            if (beforeSequence != null && segment.firstSequence != null && segment.firstSequence >= beforeSequence) {
                continue
            }
            val lines = reverseLines(segment.file).iterator()
            while (events.size < limit && lines.hasNext()) {
                val line = lines.next()
                val event = codec.decode(line).event ?: continue
                if (beforeSequence == null || event.sequence < beforeSequence) events += event
            }
        }
        val ordered = events.sortedByDescending(FunctionalEvent::sequence).take(limit)
        return FunctionalEventPage(
            events = ordered,
            nextBeforeSequence = ordered.lastOrNull()?.sequence?.takeIf { hasEventBefore(it, segments) },
        )
    }

    fun stats(): FunctionalEventJournalStats {
        var valid = 0L
        var corrupt = 0L
        val categories = FunctionalEventCategory.entries.associateWithTo(mutableMapOf()) { 0L }
        val segments = discoverSegments()
        segments.forEach { segment ->
            if (
                segment.validEvents != null && segment.corruptLines != null &&
                segment.recordedSizeBytes == segment.file.length()
            ) {
                valid += segment.validEvents
                corrupt += segment.corruptLines
                segment.categoryCounts.forEach { (category, count) ->
                    categories[category] = categories.getValue(category) + count
                }
            } else {
                val recovered = inspectSegment(segment.file, codec)
                valid += recovered.validEvents ?: 0L
                corrupt += recovered.corruptLines ?: 0L
                recovered.categoryCounts.forEach { (category, count) ->
                    categories[category] = categories.getValue(category) + count
                }
            }
        }
        return FunctionalEventJournalStats(
            validEvents = valid,
            sizeBytes = root.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L,
            corruptLines = corrupt,
            categoryCounts = categories.filterValues { it > 0L },
        )
    }

    fun operationalState(): FunctionalEventOperationalState = FunctionalEventOperationalState(
        droppedEvents = droppedEvents.get(),
        failedWrites = failedWrites.get(),
        lastError = lastError,
    )

    override fun close() {
        val result = CompletableFuture<Unit>()
        val startedAt = System.nanoTime()
        val queued = synchronized(enqueueLock) {
            if (closed) return
            closed = true
            shutdownRequested = true
            queue.offer(Command.Stop(result))
        }
        if (!queued) {
            recordWriteFailure(TimeoutException("Timed out enqueueing journal shutdown"))
            worker.interrupt()
            return
        }
        val remainingAfterEnqueue = remainingMillis(startedAt)
        try {
            result.get(remainingAfterEnqueue, TimeUnit.MILLISECONDS)
        } catch (error: java.util.concurrent.ExecutionException) {
            worker.interrupt()
            return
        } catch (error: Exception) {
            recordWriteFailure(error)
            worker.interrupt()
            return
        }
        val remainingAfterStop = remainingMillis(startedAt)
        if (remainingAfterStop > 0L) worker.join(remainingAfterStop)
        if (worker.isAlive) {
            recordWriteFailure(TimeoutException("Timed out stopping journal writer"))
            worker.interrupt()
        }
    }

    private fun remainingMillis(startedAtNanos: Long): Long =
        (closeTimeoutMs - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)).coerceAtLeast(1L)

    private fun writerLoop() = runCatching { activeWriterLoop() }
        .onFailure { error ->
            recordWriteFailure(error)
            failedWriterLoop(error)
        }
        .let { Unit }

    private fun failedWriterLoop(cause: Throwable) {
        while (true) {
            val command = queue.poll(250, TimeUnit.MILLISECONDS)
            if (command == null) {
                if (shutdownRequested && queue.isEmpty()) return
                continue
            }
            if (command is Command.Append) queuedAppends.decrementAndGet()
            when (command) {
                is Command.Append -> recordWriteFailure(cause)
                is Command.Flush -> if (!command.result.isCancelled) command.result.complete(Unit)
                is Command.Seal -> if (!command.result.isCancelled) {
                    command.result.completeExceptionally(cause)
                }
                is Command.Stop -> {
                    command.result.complete(Unit)
                    return
                }
            }
        }
    }

    private fun activeWriterLoop() {
        FunctionalEventArchive.recoverInterruptedDelete(root)
        var active = createSegment()
        var count = 0
        var firstSequence: Long? = null
        var lastSequence: Long? = null
        var activeNeedsInspection = false
        val categoryCounts = FunctionalEventCategory.entries.associateWithTo(mutableMapOf()) { 0L }
        activeDescriptor = FunctionalEventSegment(active, null, null, 0, 0, recordedSizeBytes = 0)
        val allocator = SequenceAllocator(root, sequenceBlockSize, codec)

        fun sealActive() {
            if (activeNeedsInspection) {
                val recovered = resolveSegmentStats(
                    FunctionalEventSegment(active, null, null),
                    codec,
                )
                if ((recovered.validEvents ?: 0L) + (recovered.corruptLines ?: 0L) == 0L) {
                    active.delete()
                } else {
                    writeSegmentMetadata(
                        active,
                        recovered.firstSequence,
                        recovered.lastSequence,
                        recovered.validEvents ?: 0L,
                        recovered.corruptLines ?: 0L,
                        recovered.categoryCounts,
                    )
                }
            } else if (count == 0) {
                active.delete()
            } else {
                writeSegmentMetadata(active, firstSequence, lastSequence, count.toLong(), 0, categoryCounts)
            }
        }

        while (true) {
            val command = queue.poll(250, TimeUnit.MILLISECONDS)
            if (command == null) {
                if (shutdownRequested && queue.isEmpty()) {
                    sealActive()
                    return
                }
                continue
            }
            if (command is Command.Append) queuedAppends.decrementAndGet()
            when (command) {
                is Command.Append -> {
                    val previousLength = active.length()
                    runCatching {
                        beforeWrite()
                        val event = command.draft.withSequence(allocator.next())
                        FileOutputStream(active, true).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                            writer.append(codec.encode(event)).append('\n')
                        }
                        if (firstSequence == null) firstSequence = event.sequence
                        lastSequence = event.sequence
                        count++
                        categoryCounts[event.category] = categoryCounts.getValue(event.category) + 1L
                        activeDescriptor = FunctionalEventSegment(
                            active, firstSequence, lastSequence, count.toLong(), 0,
                            categoryCounts.toMap(), active.length(),
                        )
                        if (count >= maxEventsPerSegment) {
                            sealActive()
                            active = createSegment()
                            count = 0
                            firstSequence = null
                            lastSequence = null
                            activeNeedsInspection = false
                            categoryCounts.keys.forEach { categoryCounts[it] = 0L }
                            activeDescriptor = FunctionalEventSegment(
                                active, null, null, 0, 0, recordedSizeBytes = 0,
                            )
                        }
                    }.onFailure { error ->
                        val restored = runCatching {
                            RandomAccessFile(active, "rw").use { it.setLength(previousLength) }
                        }.isSuccess
                        if (!restored) activeNeedsInspection = true
                        recordWriteFailure(error)
                    }
                }
                is Command.Flush -> if (!command.result.isCancelled) command.result.complete(Unit)
                is Command.Seal -> if (!command.result.isCancelled) runCatching {
                    sealActive()
                    active = createSegment()
                    count = 0
                    firstSequence = null
                    lastSequence = null
                    activeNeedsInspection = false
                    categoryCounts.keys.forEach { categoryCounts[it] = 0L }
                    activeDescriptor = FunctionalEventSegment(
                        active, null, null, 0, 0, recordedSizeBytes = 0,
                    )
                    FunctionalEventSnapshot(discoverSegments().filter { it.file != active })
                }.fold(
                    onSuccess = command.result::complete,
                    onFailure = { error ->
                        command.result.completeExceptionally(error)
                        throw error
                    },
                ) else Unit
                is Command.Stop -> runCatching { sealActive() }.fold(
                    onSuccess = {
                        command.result.complete(Unit)
                        return
                    },
                    onFailure = { error ->
                        command.result.completeExceptionally(error)
                        throw error
                    },
                )
            }
        }
    }

    private fun recordWriteFailure(error: Throwable) {
        failedWrites.incrementAndGet()
        lastError = error.message ?: error.javaClass.simpleName
    }

    private fun createSegment(): File {
        root.mkdirs()
        return File(root, "segment-${UUID.randomUUID()}.jsonl").also { it.createNewFile() }
    }

    private fun discoverSegments(): List<FunctionalEventSegment> = root.listFiles()
        ?.filter { it.isFile && it.name.startsWith(SEGMENT_PREFIX) && it.extension == "jsonl" }
        ?.map { file ->
            activeDescriptor?.takeIf { it.file == file } ?: run {
                val metadata = readSegmentMetadata(file)
                metadata ?: inspectSegment(file, codec).also { recovered ->
                    if ((recovered.validEvents ?: 0L) + (recovered.corruptLines ?: 0L) > 0L) {
                        writeSegmentMetadata(
                            file,
                            recovered.firstSequence,
                            recovered.lastSequence,
                            recovered.validEvents ?: 0L,
                            recovered.corruptLines ?: 0,
                            recovered.categoryCounts,
                        )
                    }
                }
            }
        }
        .orEmpty()

    private fun hasEventBefore(sequence: Long, segments: List<FunctionalEventSegment>): Boolean =
        segments.any { segment ->
            segment.firstSequence?.let { it < sequence } ?: reverseLines(segment.file).any { line ->
                codec.decode(line).event?.sequence?.let { it < sequence } == true
            }
        }

    private fun <T> submitControl(factory: (CompletableFuture<T>) -> Command): T {
        val result = CompletableFuture<T>()
        val startedAt = System.nanoTime()
        val accepted = synchronized(enqueueLock) {
            check(!closed) { "Journal is closed" }
            queue.offer(factory(result))
        }
        if (!accepted) throw RejectedExecutionException("Journal control queue is full")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        return try {
            result.get((controlTimeoutMs - elapsedMs).coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            result.cancel(false)
            throw error
        }
    }

    private fun readSegmentMetadata(file: File): FunctionalEventSegment? = runCatching {
        val json = JSONObject(metadataFile(file).readText())
        val categoryCounts = FunctionalEventCategory.entries.mapNotNull { category ->
            json.optJSONObject("categoryCounts")?.optLong(category.code, -1L)
                ?.takeIf { it >= 0L }
                ?.let { category to it }
        }.toMap()
        FunctionalEventSegment(
            file = file,
            firstSequence = json.optLong("first").takeIf { json.has("first") },
            lastSequence = json.optLong("last").takeIf { json.has("last") },
            validEvents = json.optLong("validEvents", -1L).takeIf { it >= 0L },
            corruptLines = json.optLong("corruptLines", -1L).takeIf { it >= 0L },
            categoryCounts = categoryCounts,
            recordedSizeBytes = json.optLong("sizeBytes", -1L).takeIf { it >= 0L },
        ).takeIf { it.recordedSizeBytes == file.length() }
    }.getOrNull()

    companion object {
        const val DIRECTORY_NAME = "functional-event-journal"
        const val HIGH_WATER_FILE = "sequence-high-water"
        internal const val SEGMENT_PREFIX = "segment-"
        private const val DEFAULT_QUEUE_CAPACITY = 256
        private const val DEFAULT_SEQUENCE_BLOCK_SIZE = 64
        private const val DEFAULT_MAX_EVENTS_PER_SEGMENT = 512
        private const val DEFAULT_CLOSE_TIMEOUT_MS = 5_000L
        private const val DEFAULT_CONTROL_TIMEOUT_MS = 30_000L
        private const val CONTROL_QUEUE_RESERVE = 8

        internal fun metadataFile(segment: File): File = File(segment.parentFile, "${segment.name}.meta")

        internal fun writeSegmentMetadata(
            file: File,
            first: Long?,
            last: Long?,
            validEvents: Long,
            corruptLines: Long,
            categoryCounts: Map<FunctionalEventCategory, Long>,
        ) {
            atomicWrite(
                metadataFile(file),
                segmentMetadataJson(
                    first, last, validEvents, corruptLines, categoryCounts, file.length(),
                ),
            )
        }

        internal fun segmentMetadataJson(
            first: Long?,
            last: Long?,
            validEvents: Long,
            corruptLines: Long,
            categoryCounts: Map<FunctionalEventCategory, Long>,
            sizeBytes: Long,
        ): String = JSONObject()
                .putOpt("first", first)
                .putOpt("last", last)
                .put("validEvents", validEvents)
                .put("corruptLines", corruptLines)
                .put("sizeBytes", sizeBytes)
                .put(
                    "categoryCounts",
                    JSONObject().also { counts ->
                        categoryCounts.filterValues { it > 0L }.forEach { (category, count) ->
                            counts.put(category.code, count)
                        }
                    },
                )
                .toString()

        internal fun atomicWrite(target: File, content: String) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
            FileOutputStream(temporary).use { output ->
                output.write(content.toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                moveReplacingAtomically(temporary, target)
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }
        }

        internal fun moveReplacingAtomically(source: File, target: File) {
            try {
                Files.move(
                    source.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        internal fun resolveSegmentStats(
            segment: FunctionalEventSegment,
            codec: FunctionalEventCodec,
        ): FunctionalEventSegment = if (
            segment.validEvents != null && segment.corruptLines != null &&
            segment.recordedSizeBytes == segment.file.length()
        ) {
            segment
        } else {
            inspectSegment(segment.file, codec)
        }

        private fun inspectSegment(file: File, codec: FunctionalEventCodec): FunctionalEventSegment {
            var first: Long? = null
            var last: Long? = null
            var valid = 0L
            var corrupt = 0L
            val categories = FunctionalEventCategory.entries.associateWithTo(mutableMapOf()) { 0L }
            file.useLines { lines ->
                lines.forEach { line ->
                    val event = codec.decode(line).event
                    if (event == null) {
                        corrupt++
                    } else {
                        first = minOf(first ?: event.sequence, event.sequence)
                        last = maxOf(last ?: event.sequence, event.sequence)
                        valid++
                        categories[event.category] = categories.getValue(event.category) + 1L
                    }
                }
            }
            return FunctionalEventSegment(
                file, first, last, valid, corrupt, categories.filterValues { it > 0 }, file.length(),
            )
        }

        internal fun reverseLines(file: File): Sequence<String> = sequence {
            if (!file.isFile || file.length() == 0L) return@sequence
            RandomAccessFile(file, "r").use { input ->
                var position = input.length() - 1
                val bytes = ArrayList<Byte>()
                while (position >= 0) {
                    input.seek(position--)
                    val byte = input.readByte()
                    if (byte == '\n'.code.toByte()) {
                        if (bytes.isNotEmpty()) {
                            yield(bytes.asReversed().toByteArray().toString(StandardCharsets.UTF_8))
                            bytes.clear()
                        }
                    } else if (byte != '\r'.code.toByte()) {
                        bytes += byte
                    }
                }
                if (bytes.isNotEmpty()) yield(bytes.asReversed().toByteArray().toString(StandardCharsets.UTF_8))
            }
        }
    }

    private class SequenceAllocator(
        private val root: File,
        private val blockSize: Int,
        private val codec: FunctionalEventCodec,
    ) {
        private var next = 0L
        private var limit = 0L

        fun next(): Long {
            if (next >= limit) reserveBlock()
            return next++
        }

        private fun reserveBlock() {
            val highWaterFile = File(root, HIGH_WATER_FILE)
            val stored = highWaterFile.takeIf(File::isFile)?.readText()?.trim()?.toLongOrNull()
            val start = stored?.takeIf { it > 0 } ?: (scanMaximumSequence() + 1).coerceAtLeast(1)
            val newLimit = Math.addExact(start, blockSize.toLong())
            atomicWrite(highWaterFile, newLimit.toString())
            next = start
            limit = newLimit
        }

        private fun scanMaximumSequence(): Long = root.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.startsWith(SEGMENT_PREFIX) && it.extension == "jsonl" }
            ?.flatMap { file -> reverseLines(file) }
            ?.mapNotNull { codec.decode(it).event?.sequence }
            ?.maxOrNull()
            ?: 0L
    }
}
