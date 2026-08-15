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
        assertEquals(
            2L,
            controller.affectedCount(
                FunctionalLogsDeleteScope.Category(FunctionalEventCategory.TRIP_SESSION),
            ),
        )

        repository.pages.clear()
        repository.pages += FunctionalEventPage(emptyList(), null)
        val deleted = controller.delete(
            FunctionalLogsDeleteScope.Category(FunctionalEventCategory.TRIP_SESSION),
        )

        assertEquals(2L, deleted)
        assertTrue(controller.state.value.events.isEmpty())
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
        var exportCalls = 0
        var pageError: Throwable? = null

        override fun settings(): FunctionalEventSettingsSnapshot = settings

        override fun setGlobalEnabled(enabled: Boolean) {
            settings = settings.copy(enabled = enabled)
        }

        override fun setCategoryEnabled(category: FunctionalEventCategory, enabled: Boolean) {
            settings = settings.copy(
                categories = if (enabled) settings.categories + category else settings.categories - category,
            )
        }

        override fun page(beforeSequence: Long?, limit: Int): FunctionalEventPage {
            pageCalls++
            pageError?.let { throw it }
            return pages.removeFirst()
        }

        override fun stats(): FunctionalEventJournalStats = stats

        override fun operationalState() = FunctionalEventOperationalState(0, 0, null)

        override fun export(output: OutputStream): FunctionalEventArchiveManifest {
            exportCalls++
            return FunctionalEventArchiveManifest(1, stats.validEvents, 0, 0, 0)
        }

        override fun delete(scope: FunctionalLogsDeleteScope): Long {
            val count = when (scope) {
                FunctionalLogsDeleteScope.All -> stats.validEvents
                is FunctionalLogsDeleteScope.Category -> stats.categoryCounts[scope.category] ?: 0
            }
            stats = FunctionalEventJournalStats(0, 0, 0, emptyMap())
            return count
        }
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
