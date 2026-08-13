package com.lito.a5launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class MapDebugLogArchiveTest {
    @Test
    fun writesEveryMapLogIntoSingleArchiveFolder() {
        val directory = createTempDirectory("map-debug-archive-").toFile()
        try {
            File(directory, "map-debug-1.log").writeText("first")
            File(directory, "map-debug-2.log").writeText("second")
            File(directory, "unrelated.txt").writeText("ignored")
            val output = ByteArrayOutputStream()

            ZipOutputStream(output).use { zip ->
                MapDebugLogArchive.addFiles(
                    directory.listFiles().orEmpty().filter(MapDebugLogArchive::isMapLog),
                    zip,
                )
            }

            val entries = mutableMapOf<String, String>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries[entry.name] = zip.bufferedReader().readText()
                    entry = zip.nextEntry
                }
            }
            assertEquals(
                mapOf(
                    "map-debug/map-debug-1.log" to "first",
                    "map-debug/map-debug-2.log" to "second",
                ),
                entries,
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
