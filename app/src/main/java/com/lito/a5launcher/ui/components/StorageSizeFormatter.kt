package com.lito.a5launcher.ui.components

import java.io.File
import java.util.Locale

private const val BYTES_PER_KIBIBYTE = 1_024L
private val STORAGE_UNITS = arrayOf("KB", "MB", "GB", "TB")

internal fun formatStorageSize(
    bytes: Long,
    locale: Locale = Locale.getDefault(),
): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < BYTES_PER_KIBIBYTE) return "$safeBytes B"

    var value = safeBytes.toDouble() / BYTES_PER_KIBIBYTE
    var unitIndex = 0
    while (value >= BYTES_PER_KIBIBYTE && unitIndex < STORAGE_UNITS.lastIndex) {
        value /= BYTES_PER_KIBIBYTE
        unitIndex++
    }
    return String.format(locale, "%.1f %s", value, STORAGE_UNITS[unitIndex])
}

internal fun storageTargetsSizeBytes(targets: Iterable<File>): Long =
    targets.sumOf { target ->
        runCatching {
            target.walkTopDown()
                .filter(File::isFile)
                .sumOf(File::length)
        }.getOrDefault(0L)
    }
