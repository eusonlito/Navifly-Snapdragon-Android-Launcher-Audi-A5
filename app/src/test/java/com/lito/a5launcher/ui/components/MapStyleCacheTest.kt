package com.lito.a5launcher.ui.components

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleCacheTest {
    private val validStyle = """{"version":8,"sources":{},"layers":[]}"""

    @Test
    fun offlineStartupUsesPersistedStyleWithoutTouchingNetwork() = withCache { directory ->
        var downloads = 0
        val online = MapStyleCache(directory) {
            downloads++
            validStyle
        }
        assertEquals(MapStyleOrigin.NETWORK, online.resolve("positron", "https://style", true).origin)

        val offline = MapStyleCache(directory) {
            downloads++
            error("network must not be used offline")
        }.resolve("positron", "https://style", false)

        assertEquals(MapStyleOrigin.CACHE, offline.origin)
        assertEquals(validStyle, offline.json)
        assertEquals(1, downloads)
    }

    @Test
    fun failedRefreshFallsBackToLastValidStyle() = withCache { directory ->
        MapStyleCache(directory) { validStyle }.resolve("positron", "https://style", true)

        val result = MapStyleCache(directory) { error("provider unavailable") }
            .resolve("positron", "https://style", true)

        assertEquals(MapStyleOrigin.CACHE, result.origin)
        assertEquals(validStyle, result.json)
        assertTrue(result.error.orEmpty().contains("provider unavailable"))
    }

    @Test
    fun invalidRefreshNeverReplacesValidCache() = withCache { directory ->
        MapStyleCache(directory) { validStyle }.resolve("positron", "https://style", true)

        val result = MapStyleCache(directory) { "<html>failure</html>" }
            .resolve("positron", "https://style", true)

        assertEquals(MapStyleOrigin.CACHE, result.origin)
        assertEquals(validStyle, result.json)
        assertTrue(result.error.orEmpty().contains("invalid", ignoreCase = true))
    }

    @Test
    fun firstOfflineStartupReportsMissingCache() = withCache { directory ->
        val result = MapStyleCache(directory) { error("must not run") }
            .resolve("positron", "https://style", false)

        assertEquals(MapStyleOrigin.UNAVAILABLE, result.origin)
        assertEquals(null, result.json)
        assertFalse(result.error.isNullOrBlank())
    }

    private fun withCache(block: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("map-style-cache-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
