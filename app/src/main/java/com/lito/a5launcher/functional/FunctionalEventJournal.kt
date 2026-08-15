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
import java.util.concurrent.atomic.AtomicLong

data class FunctionalEventPage(
    val events: List<FunctionalEvent>,
    val nextBeforeSequence: Long?,
)

data class FunctionalEventJournalStats(
    val validEvents: Long,
    val sizeBytes: Long,
    val corruptLines: Long,
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
    private val beforeWrite: () -> Unit = {},
) : AutoCloseable {
    private sealed interface Command {
        data class Append(val draft: FunctionalEventDraft) : Command
        data class Flush(val result: CompletableFuture<Unit>) : Command
        data class Seal(val result: CompletableFuture<FunctionalEventSnapshot>) : Command
        data class Stop(val result: CompletableFuture<Unit>) : Command
    }

    private val queue = ArrayBlockingQueue<Command>(queueCapacity.also { require(it > 0) })
    private val droppedEvents = AtomicLong()
    private val failedWrites = AtomicLong()
    @Volatile private var lastError: String? = null
    @Volatile private var closed = false
    @Volatile private var activeDescriptor: FunctionalEventSegment? = null
    private val worker = Thread(::writerLoop, "functional-event-journal").apply { isDaemon = true }

    init {
        require(sequenceBlockSize > 0)
        require(maxEventsPerSegment > 0)
        root.mkdirs()
        worker.start()
    }

    fun append(draft: FunctionalEventDraft): Boolean {
        if (closed) return false
        val accepted = queue.offer(Command.Append(draft))
        if (!accepted) droppedEvents.incrementAndGet()
        return accepted
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
        val segments = discoverSegments()
        segments.forEach { segment ->
            segment.file.useLines { lines ->
                lines.forEach { if (codec.decode(it).event == null) corrupt++ else valid++ }
            }
        }
        return FunctionalEventJournalStats(
            validEvents = valid,
            sizeBytes = root.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L,
            corruptLines = corrupt,
        )
    }

    fun operationalState(): FunctionalEventOperationalState = FunctionalEventOperationalState(
        droppedEvents = droppedEvents.get(),
        failedWrites = failedWrites.get(),
        lastError = lastError,
    )

    override fun close() {
        if (closed) return
        closed = true
        submitControl(allowClosed = true) { Command.Stop(it) }
        worker.join(CLOSE_TIMEOUT_MS)
    }

    private fun writerLoop() {
        var active = createSegment()
        var count = 0
        var firstSequence: Long? = null
        var lastSequence: Long? = null
        activeDescriptor = FunctionalEventSegment(active, null, null)
        val allocator = SequenceAllocator(root, sequenceBlockSize, codec)

        fun sealActive() {
            if (count == 0) {
                active.delete()
            } else {
                writeSegmentMetadata(active, firstSequence, lastSequence)
            }
        }

        while (true) {
            when (val command = queue.take()) {
                is Command.Append -> runCatching {
                    beforeWrite()
                    val event = command.draft.withSequence(allocator.next())
                    FileOutputStream(active, true).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                        writer.append(codec.encode(event)).append('\n')
                    }
                    if (firstSequence == null) firstSequence = event.sequence
                    lastSequence = event.sequence
                    activeDescriptor = FunctionalEventSegment(active, firstSequence, lastSequence)
                    count++
                    if (count >= maxEventsPerSegment) {
                        sealActive()
                        active = createSegment()
                        count = 0
                        firstSequence = null
                        lastSequence = null
                        activeDescriptor = FunctionalEventSegment(active, null, null)
                    }
                }.onFailure(::recordWriteFailure)
                is Command.Flush -> command.result.complete(Unit)
                is Command.Seal -> {
                    sealActive()
                    active = createSegment()
                    count = 0
                    firstSequence = null
                    lastSequence = null
                    activeDescriptor = FunctionalEventSegment(active, null, null)
                    command.result.complete(FunctionalEventSnapshot(discoverSegments().filter { it.file != active }))
                }
                is Command.Stop -> {
                    sealActive()
                    command.result.complete(Unit)
                    return
                }
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
                FunctionalEventSegment(file, metadata?.first, metadata?.second)
            }
        }
        .orEmpty()

    private fun hasEventBefore(sequence: Long, segments: List<FunctionalEventSegment>): Boolean =
        segments.any { segment ->
            segment.firstSequence?.let { it < sequence } ?: reverseLines(segment.file).any { line ->
                codec.decode(line).event?.sequence?.let { it < sequence } == true
            }
        }

    private fun <T> submitControl(
        allowClosed: Boolean = false,
        factory: (CompletableFuture<T>) -> Command,
    ): T {
        check(allowClosed || !closed) { "Journal is closed" }
        val result = CompletableFuture<T>()
        queue.put(factory(result))
        return result.get()
    }

    private fun readSegmentMetadata(file: File): Pair<Long, Long>? = runCatching {
        val json = JSONObject(metadataFile(file).readText())
        json.getLong("first") to json.getLong("last")
    }.getOrNull()

    private fun writeSegmentMetadata(file: File, first: Long?, last: Long?) {
        if (first == null || last == null) return
        atomicWrite(metadataFile(file), JSONObject().put("first", first).put("last", last).toString())
    }

    companion object {
        const val DIRECTORY_NAME = "functional-event-journal"
        const val HIGH_WATER_FILE = "sequence-high-water"
        internal const val SEGMENT_PREFIX = "segment-"
        private const val DEFAULT_QUEUE_CAPACITY = 256
        private const val DEFAULT_SEQUENCE_BLOCK_SIZE = 64
        private const val DEFAULT_MAX_EVENTS_PER_SEGMENT = 512
        private const val CLOSE_TIMEOUT_MS = 5_000L

        internal fun metadataFile(segment: File): File = File(segment.parentFile, "${segment.name}.meta")

        internal fun atomicWrite(target: File, content: String) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
            FileOutputStream(temporary).use { output ->
                output.write(content.toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
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
