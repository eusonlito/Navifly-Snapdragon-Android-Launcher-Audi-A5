package com.lito.a5launcher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

private val POI_IDENTIFIER_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

internal data class PoiFeature(
    val json: JSONObject,
    val category: String,
)

internal data class PoiCategoryStyle(
    val icon: String,
    val pulseEnabled: Boolean,
    val pulseColor: String,
)

internal data class PoiCategoryCatalog(
    val fileName: String,
    val styles: Map<String, PoiCategoryStyle>,
)

internal data class PoiSource(
    val fileName: String,
    val features: List<PoiFeature>,
) {
    val pointCount: Int get() = features.size
}

internal data class PoiIcon(
    val code: String,
    val file: File,
    val width: Int,
    val height: Int,
)

internal data class PoiSnapshot(
    val sources: List<PoiSource> = emptyList(),
    val icons: List<PoiIcon> = emptyList(),
    val categoryCatalog: PoiCategoryCatalog? = null,
) {
    val totalPoints: Int get() = sources.sumOf(PoiSource::pointCount)
    val hasPulsingPoints: Boolean = sources.any { source ->
        source.features.any { categoryCatalog?.styles?.get(it.category)?.pulseEnabled == true }
    }
    val geoJson: String = JSONObject().apply {
        val availableIcons = icons.mapTo(hashSetOf(PoiGeoJsonParser.DEFAULT_CATEGORY)) { it.code }
        put("type", "FeatureCollection")
        put("features", JSONArray().also { output ->
            sources.forEach { source ->
                source.features.forEach { feature ->
                    val properties = feature.json.getJSONObject("properties")
                    val style = categoryCatalog?.styles?.get(feature.category)
                    properties.put(
                        "resolvedIcon",
                        style?.icon?.takeIf(availableIcons::contains)
                            ?: PoiGeoJsonParser.DEFAULT_CATEGORY,
                    )
                    properties.put("pulseEnabled", style?.pulseEnabled ?: false)
                    properties.put(
                        "pulseColor",
                        style?.pulseColor ?: PoiCategoryParser.DEFAULT_PULSE_COLOR,
                    )
                    output.put(feature.json)
                }
            }
        })
    }.toString()
}

internal fun PoiSnapshot.iconsReferencedBySources(): List<PoiIcon> {
    val referencedIcons = sources.asSequence()
        .flatMap { it.features.asSequence() }
        .mapNotNull { categoryCatalog?.styles?.get(it.category)?.icon }
        .toSet()
    return icons.filter { it.code in referencedIcons }
}

internal object PoiGeoJsonParser {
    const val MAX_SOURCE_BYTES = 5 * 1024 * 1024
    const val MAX_POINTS_PER_SOURCE = 10_000
    const val MAX_TOTAL_POINTS = 50_000
    const val DEFAULT_CATEGORY = "poi-default"

    fun parse(fileName: String, input: InputStream): PoiSource {
        val root = JSONObject(
            readBoundedBytes(input, MAX_SOURCE_BYTES).toString(Charsets.UTF_8),
        )
        require(root.optString("type") == "FeatureCollection") {
            "GeoJSON must be a FeatureCollection"
        }
        val sourceFeatures = root.optJSONArray("features")
            ?: throw IllegalArgumentException("GeoJSON does not contain a features array")
        require(sourceFeatures.length() <= MAX_POINTS_PER_SOURCE) {
            "GeoJSON contains more than $MAX_POINTS_PER_SOURCE points"
        }
        val parsed = ArrayList<PoiFeature>(sourceFeatures.length())
        repeat(sourceFeatures.length()) { index ->
            val feature = sourceFeatures.optJSONObject(index)
                ?: throw IllegalArgumentException("Feature ${index + 1} is not an object")
            require(feature.optString("type") == "Feature") {
                "Feature ${index + 1} has an invalid type"
            }
            val geometry = feature.optJSONObject("geometry")
                ?: throw IllegalArgumentException("Feature ${index + 1} has no geometry")
            require(geometry.optString("type") == "Point") {
                "Feature ${index + 1} is not a Point"
            }
            validateCoordinates(geometry.optJSONArray("coordinates"), index)
            val properties = feature.optJSONObject("properties") ?: JSONObject().also {
                feature.put("properties", it)
            }
            val category = when {
                !properties.has("category") || properties.isNull("category") -> DEFAULT_CATEGORY
                properties.opt("category") !is String ->
                    throw IllegalArgumentException("Feature ${index + 1} has an invalid category")
                else -> properties.getString("category").lowercase(Locale.ROOT).also {
                    require(POI_IDENTIFIER_PATTERN.matches(it)) {
                        "Feature ${index + 1} has an invalid category"
                    }
                }
            }
            properties.put("category", category)
            properties.remove("icon")
            properties.remove("pulseEnabled")
            properties.remove("pulseColor")
            parsed += PoiFeature(feature, category)
        }
        return PoiSource(fileName, parsed)
    }

    private fun validateCoordinates(coordinates: JSONArray?, index: Int) {
        require(coordinates != null && coordinates.length() >= 2) {
            "Feature ${index + 1} has invalid coordinates"
        }
        val longitude = coordinates.opt(0) as? Number
        val latitude = coordinates.opt(1) as? Number
        require(longitude != null && longitude.toDouble() in -180.0..180.0) {
            "Feature ${index + 1} has invalid longitude"
        }
        require(latitude != null && latitude.toDouble() in -90.0..90.0) {
            "Feature ${index + 1} has invalid latitude"
        }
    }
}

internal object PoiCategoryParser {
    const val MAX_BYTES = 256 * 1024
    const val MAX_CATEGORIES = 256
    const val DEFAULT_PULSE_COLOR = "#FF3030"
    private val colorPattern = Regex("^#[0-9A-Fa-f]{6}$")

    fun parse(fileName: String, input: InputStream): PoiCategoryCatalog {
        val root = JSONObject(readBoundedBytes(input, MAX_BYTES).toString(Charsets.UTF_8))
        require(root.optInt("schema", -1) == 1) { "Unsupported categories schema" }
        val categories = root.optJSONObject("categories")
            ?: throw IllegalArgumentException("categories.json has no categories object")
        require(categories.length() <= MAX_CATEGORIES) {
            "categories.json contains more than $MAX_CATEGORIES categories"
        }
        val styles = linkedMapOf<String, PoiCategoryStyle>()
        categories.keys().forEach { rawCategory ->
            val category = rawCategory.lowercase(Locale.ROOT)
            require(POI_IDENTIFIER_PATTERN.matches(category) && rawCategory == category) {
                "Invalid POI category: $rawCategory"
            }
            val definition = categories.optJSONObject(rawCategory)
                ?: throw IllegalArgumentException("Category $category is not an object")
            val icon = definition.optString("icon", category).lowercase(Locale.ROOT)
            require(POI_IDENTIFIER_PATTERN.matches(icon)) { "Invalid icon for category $category" }
            val pulseEnabled = when {
                !definition.has("pulseEnabled") -> false
                definition.opt("pulseEnabled") is Boolean -> definition.getBoolean("pulseEnabled")
                else -> throw IllegalArgumentException("Invalid pulseEnabled for category $category")
            }
            val pulseColor = definition.optString("pulseColor", DEFAULT_PULSE_COLOR)
                .uppercase(Locale.ROOT)
            require(colorPattern.matches(pulseColor)) { "Invalid pulseColor for category $category" }
            styles[category] = PoiCategoryStyle(icon, pulseEnabled, pulseColor)
        }
        return PoiCategoryCatalog(fileName, styles)
    }
}

internal class PoiRepository internal constructor(
    private val root: File,
) {
    constructor(context: Context) : this(File(context.filesDir, "poi"))

    private val sourcesDirectory = File(root, "sources")
    private val iconsDirectory = File(root, "icons")
    private val categoriesFile = File(root, CATEGORIES_FILE_NAME)
    private val transactionMutex = Mutex()
    private val bitmapCacheMutex = Mutex()
    private val bitmapCache = linkedMapOf<String, CachedPoiBitmap>()

    suspend fun snapshot(): PoiSnapshot = withContext(Dispatchers.IO) { readSnapshot() }

    suspend fun importGeoJson(fileName: String, input: InputStream): PoiSnapshot =
        withContext(Dispatchers.IO) { transactionMutex.withLock {
            val safeName = safeGeoJsonName(fileName)
            val bytes = readBoundedBytes(input, PoiGeoJsonParser.MAX_SOURCE_BYTES)
            val parsed = PoiGeoJsonParser.parse(safeName, bytes.inputStream())
            val otherPoints = readSources(excluding = safeName).sumOf(PoiSource::pointCount)
            require(otherPoints + parsed.pointCount <= PoiGeoJsonParser.MAX_TOTAL_POINTS) {
                "POI sources contain more than ${PoiGeoJsonParser.MAX_TOTAL_POINTS} points"
            }
            atomicReplace(File(sourcesDirectory, safeName), bytes)
            readSnapshot()
        } }

    suspend fun importIcon(fileName: String, input: InputStream): PoiSnapshot =
        withContext(Dispatchers.IO) { transactionMutex.withLock {
            val code = categoryForIconFile(fileName)
            val bytes = readBoundedBytes(input, MAX_ICON_BYTES)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outMimeType == "image/png" && bounds.outWidth > 0 && bounds.outHeight > 0) {
                "The selected file is not a valid PNG"
            }
            require(bounds.outWidth <= MAX_ICON_DIMENSION && bounds.outHeight <= MAX_ICON_DIMENSION) {
                "PNG dimensions exceed ${MAX_ICON_DIMENSION}x$MAX_ICON_DIMENSION"
            }
            val otherIcons = readIcons().filterNot { it.code == code }
            require(otherIcons.size < MAX_ICONS) { "POI icon limit exceeded" }
            val totalPixels = otherIcons.sumOf { it.width.toLong() * it.height } +
                bounds.outWidth.toLong() * bounds.outHeight
            require(totalPixels <= MAX_ICON_PIXELS) { "POI icon pixel budget exceeded" }
            atomicReplace(File(iconsDirectory, "$code.png"), bytes)
            readSnapshot()
        } }

    suspend fun importCategories(fileName: String, input: InputStream): PoiSnapshot =
        withContext(Dispatchers.IO) { transactionMutex.withLock {
            require(fileName.equals(CATEGORIES_FILE_NAME, ignoreCase = true)) {
                "The category file must be named $CATEGORIES_FILE_NAME"
            }
            val bytes = readBoundedBytes(input, PoiCategoryParser.MAX_BYTES)
            PoiCategoryParser.parse(CATEGORIES_FILE_NAME, bytes.inputStream())
            atomicReplace(categoriesFile, bytes)
            readSnapshot()
        } }

    suspend fun deleteSource(fileName: String): PoiSnapshot = withContext(Dispatchers.IO) {
        transactionMutex.withLock {
        require(File(sourcesDirectory, fileName).canonicalFile.parentFile == sourcesDirectory.canonicalFile)
        File(sourcesDirectory, fileName).delete()
        readSnapshot()
        }
    }

    suspend fun deleteIcon(code: String): PoiSnapshot = withContext(Dispatchers.IO) {
        transactionMutex.withLock {
        val safeCode = categoryForIconFile("$code.png")
        File(iconsDirectory, "$safeCode.png").delete()
        readSnapshot()
        }
    }

    suspend fun deleteCategories(): PoiSnapshot = withContext(Dispatchers.IO) {
        transactionMutex.withLock {
        categoriesFile.delete()
        readSnapshot()
        }
    }

    /** Decodes only icons used by the current sources, always away from the UI thread. */
    suspend fun loadReferencedIconBitmaps(snapshot: PoiSnapshot): Map<String, Bitmap> =
        withContext(Dispatchers.IO) {
            bitmapCacheMutex.withLock {
                val referenced = snapshot.iconsReferencedBySources()
                    .take(MAX_ICONS)
                    .takeWhilePixelBudget(MAX_ICON_PIXELS)
                val requestedCodes = referenced.mapTo(hashSetOf()) { it.code }
                val staleCodes = bitmapCache.keys - requestedCodes
                staleCodes.forEach { code -> bitmapCache.remove(code)?.bitmap?.recycle() }

                referenced.forEach { icon ->
                    val fingerprint = PoiIconFingerprint(
                        icon.file.absolutePath,
                        icon.file.lastModified(),
                        icon.file.length(),
                    )
                    val cached = bitmapCache[icon.code]
                    if (cached?.fingerprint != fingerprint) {
                        bitmapCache.remove(icon.code)?.bitmap?.recycle()
                        val decoded = BitmapFactory.decodeFile(icon.file.absolutePath)
                        if (decoded != null) {
                            bitmapCache[icon.code] = CachedPoiBitmap(fingerprint, decoded)
                        }
                    }
                }
                referenced.mapNotNull { icon ->
                    bitmapCache[icon.code]?.bitmap?.let { icon.code to it }
                }.toMap(linkedMapOf())
            }
        }

    private fun readSnapshot(): PoiSnapshot = PoiSnapshot(
        sources = readSources(),
        icons = readIcons(),
        categoryCatalog = readCategories(),
    )

    private fun readCategories(): PoiCategoryCatalog? = if (categoriesFile.isFile) {
        runCatching {
            categoriesFile.inputStream().use {
                PoiCategoryParser.parse(CATEGORIES_FILE_NAME, it)
            }
        }.getOrNull()
    } else {
        null
    }

    private fun readSources(excluding: String? = null): List<PoiSource> {
        if (!sourcesDirectory.isDirectory) return emptyList()
        return sourcesDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.endsWith(".geojson", ignoreCase = true) }
            .filterNot { it.name == excluding }
            .sortedBy(File::getName)
            .mapNotNull { file ->
                runCatching { file.inputStream().use { PoiGeoJsonParser.parse(file.name, it) } }
                    .getOrNull()
            }
            .toList()
    }

    private fun readIcons(): List<PoiIcon> {
        if (!iconsDirectory.isDirectory) return emptyList()
        return iconsDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.endsWith(".png", ignoreCase = true) }
            .sortedBy(File::getName)
            .mapNotNull { file ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else PoiIcon(
                    code = file.nameWithoutExtension.lowercase(Locale.ROOT),
                    file = file,
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                )
            }
            .toList()
    }

    private fun atomicReplace(destination: File, bytes: ByteArray) {
        destination.parentFile?.mkdirs()
        val temporary = File.createTempFile(".${destination.name}.", ".tmp", destination.parentFile)
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            throw IllegalStateException("Could not store ${destination.name}")
        }
    }

    companion object {
        const val MAX_ICON_BYTES = 1024 * 1024
        const val MAX_ICON_DIMENSION = 512
        const val MAX_ICONS = 64
        const val MAX_ICON_PIXELS = 4L * 1024L * 1024L
        const val CATEGORIES_FILE_NAME = "categories.json"
        private val fileNamePattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

        internal fun safeGeoJsonName(fileName: String): String {
            require(fileNamePattern.matches(fileName) && fileName.endsWith(".geojson", true)) {
                "GeoJSON file name is invalid"
            }
            return fileName
        }

        internal fun categoryForIconFile(fileName: String): String {
            require(fileNamePattern.matches(fileName) && fileName.endsWith(".png", true)) {
                "Icon must be a PNG with a safe file name"
            }
            val code = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
            require(POI_IDENTIFIER_PATTERN.matches(code)) { "Icon category is invalid" }
            return code
        }
    }

    private data class PoiIconFingerprint(
        val absolutePath: String,
        val lastModified: Long,
        val byteCount: Long,
    )

    private data class CachedPoiBitmap(
        val fingerprint: PoiIconFingerprint,
        val bitmap: Bitmap,
    )
}

private fun List<PoiIcon>.takeWhilePixelBudget(maximumPixels: Long): List<PoiIcon> {
    var pixels = 0L
    return takeWhile { icon ->
        val next = icon.width.toLong() * icon.height
        (pixels + next <= maximumPixels).also { accepted ->
            if (accepted) pixels += next
        }
    }
}

internal fun readBoundedBytes(input: InputStream, maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= maximumBytes) { "File is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
