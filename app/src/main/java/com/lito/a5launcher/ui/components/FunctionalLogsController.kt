package com.lito.a5launcher.ui.components

import com.lito.a5launcher.functional.FunctionalEvent
import com.lito.a5launcher.functional.FunctionalEventArchive
import com.lito.a5launcher.functional.FunctionalEventArchiveManifest
import com.lito.a5launcher.functional.FunctionalEventCategory
import com.lito.a5launcher.functional.FunctionalEventCodec
import com.lito.a5launcher.functional.FunctionalEventJournal
import com.lito.a5launcher.functional.FunctionalEventJournalStats
import com.lito.a5launcher.functional.FunctionalEventOperationalState
import com.lito.a5launcher.functional.FunctionalEventPage
import com.lito.a5launcher.functional.FunctionalEventSettings
import com.lito.a5launcher.functional.FunctionalEventSettingsSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream

internal sealed interface FunctionalLogsDeleteScope {
    data object All : FunctionalLogsDeleteScope
    data class Category(val category: FunctionalEventCategory) : FunctionalLogsDeleteScope
}

internal enum class FunctionalLogsOperation { IDLE, EXPORTING, DELETING }

internal enum class FunctionalLogsExportResult { SUCCESS, CANCELLED, FAILED, BUSY }

internal data class FunctionalLogsUiState(
    val settings: FunctionalEventSettingsSnapshot = FunctionalEventSettingsSnapshot(
        enabled = false,
        categories = FunctionalEventCategory.entries.toSet(),
    ),
    val events: List<FunctionalEvent> = emptyList(),
    val nextBeforeSequence: Long? = null,
    val expandedSequences: Set<Long> = emptySet(),
    val stats: FunctionalEventJournalStats = FunctionalEventJournalStats(0, 0, 0),
    val operational: FunctionalEventOperationalState = FunctionalEventOperationalState(0, 0, null),
    val initialLoading: Boolean = true,
    val pageLoading: Boolean = false,
    val endReached: Boolean = false,
    val displayLimitReached: Boolean = false,
    val loadError: String? = null,
    val actionError: String? = null,
    val operation: FunctionalLogsOperation = FunctionalLogsOperation.IDLE,
)

internal interface FunctionalLogsRepository {
    fun settings(): FunctionalEventSettingsSnapshot
    fun setGlobalEnabled(enabled: Boolean)
    fun setCategoryEnabled(category: FunctionalEventCategory, enabled: Boolean)
    fun page(beforeSequence: Long?, limit: Int): FunctionalEventPage
    fun stats(): FunctionalEventJournalStats
    fun operationalState(): FunctionalEventOperationalState
    fun export(output: OutputStream): FunctionalEventArchiveManifest
    fun delete(scope: FunctionalLogsDeleteScope): Long
}

internal class JournalFunctionalLogsRepository(
    private val journal: FunctionalEventJournal,
    private val settingsStore: FunctionalEventSettings,
    private val codec: FunctionalEventCodec = FunctionalEventCodec(),
) : FunctionalLogsRepository {
    override fun settings(): FunctionalEventSettingsSnapshot = settingsStore.snapshot()

    override fun setGlobalEnabled(enabled: Boolean) = settingsStore.setEnabled(enabled)

    override fun setCategoryEnabled(category: FunctionalEventCategory, enabled: Boolean) =
        settingsStore.setCategoryEnabled(category, enabled)

    override fun page(beforeSequence: Long?, limit: Int): FunctionalEventPage =
        journal.page(beforeSequence, limit)

    override fun stats(): FunctionalEventJournalStats = journal.stats()

    override fun operationalState(): FunctionalEventOperationalState = journal.operationalState()

    override fun export(output: OutputStream): FunctionalEventArchiveManifest =
        FunctionalEventArchive.export(journal.sealSnapshot(), output)

    override fun delete(scope: FunctionalLogsDeleteScope): Long {
        val snapshot = journal.sealSnapshot()
        return when (scope) {
            FunctionalLogsDeleteScope.All -> FunctionalEventArchive.deleteAll(snapshot)
            is FunctionalLogsDeleteScope.Category -> FunctionalEventArchive.deleteCategory(
                snapshot,
                scope.category,
                codec,
            ).deletedEvents
        }
    }
}

internal class FunctionalLogsController(
    private val repository: FunctionalLogsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxRetainedEvents: Int = DEFAULT_MAX_RETAINED_EVENTS,
) {
    init { require(maxRetainedEvents > 0) }
    private val pagingMutex = Mutex()
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(FunctionalLogsUiState())
    val state: StateFlow<FunctionalLogsUiState> = _state.asStateFlow()

    suspend fun refresh(): Unit = pagingMutex.withLock {
        _state.value = _state.value.copy(initialLoading = true, loadError = null)
        runCatching {
            withContext(ioDispatcher) {
                val page = repository.page(beforeSequence = null, limit = PAGE_SIZE)
                RefreshResult(
                    settings = repository.settings(),
                    page = page,
                    stats = repository.stats(),
                    operational = repository.operationalState(),
                )
            }
        }.onSuccess { result ->
            val initialEvents = result.page.events.take(maxRetainedEvents)
            val reachedWindowLimit = initialEvents.size >= maxRetainedEvents
            _state.value = _state.value.copy(
                settings = result.settings,
                events = initialEvents,
                nextBeforeSequence = result.page.nextBeforeSequence.takeUnless { reachedWindowLimit },
                expandedSequences = _state.value.expandedSequences.intersect(
                    initialEvents.mapTo(mutableSetOf(), FunctionalEvent::sequence),
                ),
                stats = result.stats,
                operational = result.operational,
                initialLoading = false,
                pageLoading = false,
                endReached = reachedWindowLimit || result.page.nextBeforeSequence == null,
                displayLimitReached = reachedWindowLimit && result.page.nextBeforeSequence != null,
                loadError = null,
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                initialLoading = false,
                pageLoading = false,
                loadError = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    suspend fun loadNextPage(): Unit = pagingMutex.withLock {
        val current = _state.value
        if (current.initialLoading || current.pageLoading || current.endReached) return@withLock
        val cursor = current.nextBeforeSequence ?: return@withLock
        _state.value = current.copy(pageLoading = true, loadError = null)
        runCatching {
            withContext(ioDispatcher) {
                PageResult(
                    page = repository.page(cursor, PAGE_SIZE),
                    operational = repository.operationalState(),
                )
            }
        }.onSuccess { result ->
            val existing = _state.value.events
            val knownSequences = existing.mapTo(mutableSetOf(), FunctionalEvent::sequence)
            val remainingCapacity = (maxRetainedEvents - existing.size).coerceAtLeast(0)
            val novelEvents = result.page.events.asSequence()
                .filter { knownSequences.add(it.sequence) }
                .take(remainingCapacity)
                .toList()
            val merged = existing + novelEvents
            val reachedWindowLimit = merged.size >= maxRetainedEvents
            val retainedSequences = merged.mapTo(mutableSetOf(), FunctionalEvent::sequence)
            _state.value = _state.value.copy(
                events = merged,
                expandedSequences = _state.value.expandedSequences.intersect(
                    retainedSequences,
                ),
                nextBeforeSequence = result.page.nextBeforeSequence.takeUnless { reachedWindowLimit },
                operational = result.operational,
                pageLoading = false,
                endReached = reachedWindowLimit || result.page.nextBeforeSequence == null,
                displayLimitReached = reachedWindowLimit && result.page.nextBeforeSequence != null,
                loadError = null,
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                pageLoading = false,
                loadError = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    suspend fun retry() {
        if (_state.value.events.isEmpty()) refresh() else loadNextPage()
    }

    suspend fun setGlobalEnabled(enabled: Boolean) {
        runCatching {
            withContext(ioDispatcher) {
                repository.setGlobalEnabled(enabled)
                repository.settings()
            }
        }.onSuccess { settings ->
            _state.value = _state.value.copy(settings = settings, actionError = null)
        }.onFailure(::recordActionError)
    }

    suspend fun setCategoryEnabled(category: FunctionalEventCategory, enabled: Boolean) {
        runCatching {
            withContext(ioDispatcher) {
                repository.setCategoryEnabled(category, enabled)
                repository.settings()
            }
        }.onSuccess { settings ->
            _state.value = _state.value.copy(settings = settings, actionError = null)
        }.onFailure(::recordActionError)
    }

    fun toggleExpanded(sequence: Long) {
        val expanded = _state.value.expandedSequences
        _state.value = _state.value.copy(
            expandedSequences = if (sequence in expanded) expanded - sequence else expanded + sequence,
        )
    }

    suspend fun export(output: OutputStream?): FunctionalLogsExportResult {
        if (output == null) return FunctionalLogsExportResult.CANCELLED
        if (!operationMutex.tryLock()) {
            runCatching { output.close() }
            return FunctionalLogsExportResult.BUSY
        }
        return try {
            _state.value = _state.value.copy(
                operation = FunctionalLogsOperation.EXPORTING,
                actionError = null,
            )
            runCatching {
                withContext(ioDispatcher) { output.use(repository::export) }
            }.fold(
                onSuccess = { FunctionalLogsExportResult.SUCCESS },
                onFailure = { error ->
                    recordActionError(error)
                    FunctionalLogsExportResult.FAILED
                },
            )
        } finally {
            _state.value = _state.value.copy(operation = FunctionalLogsOperation.IDLE)
            operationMutex.unlock()
        }
    }

    suspend fun delete(scope: FunctionalLogsDeleteScope): Long? {
        if (!operationMutex.tryLock()) return null
        return try {
            _state.value = _state.value.copy(
                operation = FunctionalLogsOperation.DELETING,
                actionError = null,
            )
            runCatching { withContext(ioDispatcher) { repository.delete(scope) } }
                .onFailure(::recordActionError)
                .getOrNull()
                ?.also {
                    _state.value = _state.value.copy(actionError = null)
                    refresh()
                }
        } finally {
            _state.value = _state.value.copy(operation = FunctionalLogsOperation.IDLE)
            operationMutex.unlock()
        }
    }

    private fun recordActionError(error: Throwable) {
        _state.value = _state.value.copy(actionError = error.message ?: error.javaClass.simpleName)
    }

    private data class RefreshResult(
        val settings: FunctionalEventSettingsSnapshot,
        val page: FunctionalEventPage,
        val stats: FunctionalEventJournalStats,
        val operational: FunctionalEventOperationalState,
    )

    private data class PageResult(
        val page: FunctionalEventPage,
        val operational: FunctionalEventOperationalState,
    )

    private companion object {
        const val PAGE_SIZE = 40
        const val DEFAULT_MAX_RETAINED_EVENTS = 800
    }
}
