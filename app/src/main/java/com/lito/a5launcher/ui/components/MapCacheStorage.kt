package com.lito.a5launcher.ui.components

import android.content.Context
import java.io.File

internal fun vectorMapRemovableTargets(context: Context): List<File> = listOf(
    File(context.filesDir, "map-styles"),
    File(context.cacheDir, "map-tiles"),
    File(context.cacheDir, "carto-dark-tiles"),
    File(context.cacheDir, "carto-light-tiles"),
    File(context.cacheDir, "osmdroid"),
)

internal fun vectorMapCacheTargets(context: Context): List<File> =
    listOf(File(context.filesDir, "mbgl-offline.db")) + vectorMapRemovableTargets(context)

internal fun vectorMapCacheSizeBytes(context: Context): Long =
    storageTargetsSizeBytes(vectorMapCacheTargets(context))
