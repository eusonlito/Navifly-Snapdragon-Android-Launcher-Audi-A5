package com.lito.a5launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

class StorageSizeFormatterTest {
    @Test
    fun formatsBytesUsingReadableBinaryUnits() {
        assertEquals("0 B", formatStorageSize(0, Locale.US))
        assertEquals("999 B", formatStorageSize(999, Locale.US))
        assertEquals("1.0 KB", formatStorageSize(1_024, Locale.US))
        assertEquals("1.5 MB", formatStorageSize(1_572_864, Locale.US))
        assertEquals("2.0 GB", formatStorageSize(2L * 1_024 * 1_024 * 1_024, Locale.US))
    }

    @Test
    fun sumsOnlyFilesInsideStorageTargets() {
        val root = Files.createTempDirectory("storage-size-test").toFile()
        try {
            File(root, "nested").mkdirs()
            File(root, "first.bin").writeBytes(ByteArray(128))
            File(root, "nested/second.bin").writeBytes(ByteArray(384))

            assertEquals(512, storageTargetsSizeBytes(listOf(root)))
        } finally {
            root.deleteRecursively()
        }
    }
}
