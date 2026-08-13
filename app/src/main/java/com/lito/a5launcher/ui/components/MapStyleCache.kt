package com.lito.a5launcher.ui.components

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal enum class MapStyleOrigin {
    NETWORK,
    CACHE,
    UNAVAILABLE,
}

internal data class MapStyleResolution(
    val json: String?,
    val origin: MapStyleOrigin,
    val error: String? = null,
)

/** Persistent style JSON fallback; vector resources remain in MapLibre's ambient cache. */
internal class MapStyleCache(
    private val directory: File,
    private val downloader: (String) -> String,
) {
    fun resolve(cacheKey: String, styleUrl: String, networkAvailable: Boolean): MapStyleResolution {
        val cached = readValid(cacheKey)
        if (!networkAvailable) {
            return cached?.let { MapStyleResolution(it, MapStyleOrigin.CACHE) }
                ?: MapStyleResolution(
                    json = null,
                    origin = MapStyleOrigin.UNAVAILABLE,
                    error = "No network and no persisted style for $cacheKey",
                )
        }

        return runCatching {
            val downloaded = downloader(styleUrl)
            require(isValidStyle(downloaded)) { "Downloaded map style is invalid" }
            writeAtomically(cacheKey, downloaded)
            MapStyleResolution(downloaded, MapStyleOrigin.NETWORK)
        }.getOrElse { error ->
            cached?.let {
                MapStyleResolution(it, MapStyleOrigin.CACHE, error.message)
            } ?: MapStyleResolution(null, MapStyleOrigin.UNAVAILABLE, error.message)
        }
    }

    private fun readValid(cacheKey: String): String? = cacheFile(cacheKey)
        .takeIf(File::isFile)
        ?.runCatching(File::readText)
        ?.getOrNull()
        ?.takeIf(::isValidStyle)

    private fun writeAtomically(cacheKey: String, json: String) {
        directory.mkdirs()
        val destination = cacheFile(cacheKey)
        val temporary = File(directory, ".${destination.name}.tmp")
        temporary.writeText(json)
        runCatching {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    private fun cacheFile(cacheKey: String): File {
        val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(directory, "$safeKey.json")
    }

    private fun isValidStyle(json: String): Boolean {
        val content = json.trim()
        return content.startsWith('{') &&
            content.endsWith('}') &&
            "\"version\"" in content &&
            "\"sources\"" in content &&
            "\"layers\"" in content
    }
}
