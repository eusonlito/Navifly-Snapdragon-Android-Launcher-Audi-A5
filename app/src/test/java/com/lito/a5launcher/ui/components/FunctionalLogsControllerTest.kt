package com.lito.a5launcher.ui.components

import com.lito.a5launcher.functional.FunctionalEvent
import com.lito.a5launcher.functional.FunctionalEventArchiveManifest
import com.lito.a5launcher.functional.FunctionalEventCategory
import com.lito.a5launcher.functional.FunctionalEventJournalStats
import com.lito.a5launcher.functional.FunctionalEventOperationalState
import com.lito.a5launcher.functional.FunctionalEventPage
import com.lito.a5launcher.functional.FunctionalEventSettingsSnapshot
import com.lito.a5launcher.functional.FunctionalEventSource
import com.lito.a5launcher.functional.FunctionalEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class FunctionalLogsControllerTest {
    @Test
    fun `refresh and next page preserve order expansion and end state`() = runBlocking {
        val repository = FakeRepository(
            pages = mutableListOf(
                FunctionalEventPage(listOf(event(4), event(3)), 3),
                FunctionalEventPage(listOf(event(2), event(1)), null),
            ),
        )
        val controller = FunctionalLogsController(repository, Dispatchers.Unconfined)

        controller.refresh()
        controller.toggleExpanded(3)
        controller.loadNextPage()

        assertEquals(listOf(4L, 3L, 2L, 1L), controller.state.value.events.map { it.sequence })
        assertEquals(setOf(3L), controller.state.value.expandedSequences)
        assertTrue(controller.state.value.endReached)
        assertEquals(1, repository.statsCalls)
        controller.loadNextPage()
        assertEquals(2, repository.pageCalls)
    }

    @Test
    fun `page failure retains rows and retry continues from same cursor`() = runBlocking {
        val repository = FakeRepository(
            pages = mutableListOf(
                FunctionalEventPage(listOf(event(2)), 2),
            ),
        )
        val controller = FunctionalLogsController(repository, Dispatchers.Unconfined)
        controller.refresh()
        repository.pageError = IllegalStateException("read failed")

        controller.loadNextPage()
        assertEquals(listOf(2L), controller.state.value.events.map { it.sequence })
        assertEquals("read failed", controller.state.value.loadError)

        repository.pageError = null
        repository.pages += FunctionalEventPage(listOf(event(1)), null)
        controller.retry()
        assertEquals(listOf(2L, 1L), controller.state.value.events.map { it.sequence })
        assertNull(controller.state.value.loadError)
        assertEquals(listOf(null, 2L, 2L), repository.requestedCursors)
    }

    @Test
    fun `deep paging keeps a bounded event window`() = runBlocking {
        val repository = FakeRepository(
            pages = mutableListOf(
                FunctionalEventPage(listOf(event(5), event(4)), 4),
                FunctionalEventPage(listOf(event(3), event(2)), 2),
                FunctionalEventPage(listOf(event(1)), null),
            ),
        )
        val controller = FunctionalLogsController(
            repository,
            Dispatchers.Unconfined,
            maxRetainedEvents = 3,
        )

        controller.refresh()
        controller.toggleExpanded(5)
        controller.loadNextPage()
        controller.loadNextPage()

        assertEquals(listOf(5L, 4L, 3L), controller.state.value.events.map { it.sequence })
        assertEquals(setOf(5L), controller.state.value.expandedSequences)
        assertTrue(controller.state.value.endReached)
        assertTrue(controller.state.value.displayLimitReached)
        assertEquals(2, repository.pageCalls)
    }

    @Test
    fun `capture preferences remain editable while global capture is disabled`() = runBlocking {
        val repository = FakeRepository()
        val controller = FunctionalLogsController(repository, Dispatchers.Unconfined)
        controller.refresh()

        controller.setCategoryEnabled(FunctionalEventCategory.GEAR_ESTIMATION, false)
        assertFalse(repository.settings.categories.contains(FunctionalEventCategory.GEAR_ESTIMATION))
        assertFalse(controller.state.value.settings.enabled)

        controller.setGlobalEnabled(true)
        assertTrue(controller.state.value.settings.enabled)
    }

    @Test
    fun `settings failure does not turn into a paging error`() = runBlocking {
        val repository = FakeRepository()
        val controller = FunctionalLogsController(repository, Dispatchers.Unconfined)
        controller.refresh()
        repository.settingsError = IllegalStateException("settings failed")

        controller.setGlobalEnabled(true)

        assertEquals("settings failed", controller.state.value.actionError)
        assertNull(controller.state.value.loadError)
    }

    @Test
    fun `cancelled export is a no-op and operations always return to idle`() = runBlocking {
        val repository = FakeRepository()
        val controller = FunctionalLogsController(repository, Dispatchers.Unconfined)
        controller.refresh()

        assertEquals(FunctionalLogsExportResult.CANCELLED, controller.export(null))
        assertEquals(0, repository.exportCalls)
        assertEquals(FunctionalLogsOperation.IDLE, controller.state.value.operation)

        val output = ByteArrayOutputStream()
        assertEquals(FunctionalLogsExportResult.SUCCESS, controller.export(output))
        assertEquals(1, repository.exportCalls)
        assertEquals(FunctionalLogsOperation.IDLE, controller.state.value.operation)
    }

    @Test
    fun `failed export closes destination and reports action error`() = runBlocking {
        val repository = FakeRepository().apply {
            exportError = IllegalStateException("destination failed")
        }
        val controller = FunctionalLogsController(repository, Dispatchers.Unconfined)
        val output = TrackingOutputStream()

        assertEquals(FunctionalLogsExportResult.FAILED, controller.export(output))

        assertTrue(output.closed)
        assertEquals("destination failed", controller.state.value.actionError)
        assertNull(controller.state.value.loadError)
        assertEquals(FunctionalLogsOperation.IDLE, controller.state.value.operation)
    }

    @Test
    fun `delete scope uses category count and refreshes the chronology`() = runBlocking {
        val repository = FakeRepository(
            pages = mutableListOf(FunctionalEventPage(listOf(event(2), event(1)), null)),
        ).apply {
            stats = stats.copy(
                validEvents = 2,
                categoryCounts = mapOf(FunctionalEventCategory.TRIP_SESSION to 2),
            )
        }
        val controller = FunctionalLogsController(repository, Dispatchers.Unconfined)
        controller.refresh()
        repository.pages.clear()
        repository.pages += FunctionalEventPage(emptyList(), null)
        val deleted = controller.delete(
            FunctionalLogsDeleteScope.Category(FunctionalEventCategory.TRIP_SESSION),
        )

        assertEquals(2L, deleted)
        assertTrue(controller.state.value.events.isEmpty())
        assertEquals(FunctionalLogsOperation.IDLE, controller.state.value.operation)
    }

    @Test
    fun `deleting one event removes only that row and refreshes stats`() = runBlocking {
        val repository = FakeRepository(
            pages = mutableListOf(FunctionalEventPage(listOf(event(3), event(2), event(1)), null)),
        ).apply {
            stats = stats.copy(validEvents = 3)
        }
        val controller = FunctionalLogsController(repository, Dispatchers.Unconfined)
        controller.refresh()
        repository.pages.clear()
        repository.pages += FunctionalEventPage(listOf(event(3), event(1)), null)

        val deleted = controller.delete(FunctionalLogsDeleteScope.Event(sequence = 2L))

        assertEquals(1L, deleted)
        assertEquals(listOf(3L, 1L), controller.state.value.events.map { it.sequence })
        assertEquals(2L, controller.state.value.stats.validEvents)
        assertEquals(FunctionalLogsDeleteScope.Event(2L), repository.lastDeleteScope)
        assertEquals(FunctionalLogsOperation.IDLE, controller.state.value.operation)
    }

    private class FakeRepository(
        val pages: MutableList<FunctionalEventPage> = mutableListOf(
            FunctionalEventPage(emptyList(), null),
        ),
    ) : FunctionalLogsRepository {
        var settings = FunctionalEventSettingsSnapshot(
            enabled = false,
            categories = FunctionalEventCategory.entries.toSet(),
        )
        var stats = FunctionalEventJournalStats(0, 0, 0, emptyMap())
        var pageCalls = 0
        val requestedCursors = mutableListOf<Long?>()
        var statsCalls = 0
        var exportCalls = 0
        var pageError: Throwable? = null
        var settingsError: Throwable? = null
        var exportError: Throwable? = null
        var lastDeleteScope: FunctionalLogsDeleteScope? = null

        override fun settings(): FunctionalEventSettingsSnapshot = settings

        override fun setGlobalEnabled(enabled: Boolean) {
            settingsError?.let { throw it }
            settings = settings.copy(enabled = enabled)
        }

        override fun setCategoryEnabled(category: FunctionalEventCategory, enabled: Boolean) {
            settingsError?.let { throw it }
            settings = settings.copy(
                categories = if (enabled) settings.categories + category else settings.categories - category,
            )
        }

        override fun page(beforeSequence: Long?, limit: Int): FunctionalEventPage {
            pageCalls++
            requestedCursors += beforeSequence
            pageError?.let { throw it }
            return pages.removeFirst()
        }

        override fun stats(): FunctionalEventJournalStats {
            statsCalls++
            return stats
        }

        override fun operationalState() = FunctionalEventOperationalState(0, 0, null)

        override fun export(output: OutputStream): FunctionalEventArchiveManifest {
            exportCalls++
            exportError?.let { throw it }
            return FunctionalEventArchiveManifest(1, stats.validEvents, 0, 0, 0)
        }

        override fun delete(scope: FunctionalLogsDeleteScope): Long {
            lastDeleteScope = scope
            val count = when (scope) {
                FunctionalLogsDeleteScope.All -> stats.validEvents
                is FunctionalLogsDeleteScope.Category -> stats.categoryCounts[scope.category] ?: 0
                is FunctionalLogsDeleteScope.Event -> 1L
            }
            stats = when (scope) {
                FunctionalLogsDeleteScope.All -> FunctionalEventJournalStats(0, 0, 0, emptyMap())
                is FunctionalLogsDeleteScope.Category -> stats.copy(
                    validEvents = (stats.validEvents - count).coerceAtLeast(0),
                    categoryCounts = stats.categoryCounts - scope.category,
                )
                is FunctionalLogsDeleteScope.Event -> stats.copy(
                    validEvents = (stats.validEvents - count).coerceAtLeast(0),
                )
            }
            return count
        }
    }

    private class TrackingOutputStream : OutputStream() {
        var closed = false
        override fun write(value: Int) = Unit
        override fun close() { closed = true }
    }

    private fun event(sequence: Long) = FunctionalEvent(
        sequence = sequence,
        bootSession = 1,
        capturedAtEpochMs = sequence,
        capturedAtElapsedMs = sequence,
        source = FunctionalEventSource.REPLAY,
        category = FunctionalEventCategory.TRIP_SESSION,
        type = FunctionalEventType("test.$sequence"),
        context = emptyMap(),
    )
}
