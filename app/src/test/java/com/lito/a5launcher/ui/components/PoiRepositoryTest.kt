package com.lito.a5launcher.ui.components

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.io.path.createTempDirectory

class PoiRepositoryTest {
    @Test
    fun `parses standard point GeoJSON and normalizes optional presentation`() {
        val source = PoiGeoJsonParser.parse("radares.geojson", VALID_GEOJSON.byteInputStream())

        assertEquals(2, source.pointCount)
        assertEquals("radar-fijo", source.features.first().category)
        assertEquals(PoiGeoJsonParser.DEFAULT_CATEGORY, source.features.last().category)
        assertFalse(source.features.first().json.toString().contains("pulseEnabled"))
    }

    @Test
    fun `rejects non point geometry invalid properties and excessive points`() {
        val line = VALID_GEOJSON.replace("\"Point\"", "\"LineString\"")
        val badCategory = VALID_GEOJSON.replace("radar-fijo", "Radar Fijo")

        assertTrue(runCatching { PoiGeoJsonParser.parse("line.geojson", line.byteInputStream()) }.isFailure)
        assertTrue(runCatching { PoiGeoJsonParser.parse("bad.geojson", badCategory.byteInputStream()) }.isFailure)
        assertTrue(
            runCatching {
                PoiGeoJsonParser.parse(
                    "large.geojson",
                    featureCollection((0..PoiGeoJsonParser.MAX_POINTS_PER_SOURCE).joinToString(",") {
                        pointFeature(it)
                    }).byteInputStream(),
                )
            }.isFailure,
        )
    }

    @Test
    fun `repository replaces same source atomically and combines independent sources`() = runBlocking {
        val root = createTempDirectory("poi-repository-").toFile()
        try {
            val repository = PoiRepository(root)
            repository.importGeoJson("one.geojson", VALID_GEOJSON.byteInputStream())
            repository.importGeoJson("two.geojson", featureCollection(pointFeature(3)).byteInputStream())

            assertEquals(2, repository.snapshot().sources.size)
            assertEquals(3, repository.snapshot().totalPoints)

            val failure = runCatching {
                repository.importGeoJson("one.geojson", "invalid".byteInputStream())
            }
            assertTrue(failure.isFailure)
            assertEquals(2, repository.snapshot().sources.size)
            assertEquals(3, repository.snapshot().totalPoints)

            repository.importGeoJson("one.geojson", featureCollection(pointFeature(4)).byteInputStream())
            assertEquals(2, repository.snapshot().totalPoints)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `category catalog centralizes icon and pulse presentation`() = runBlocking {
        val root = createTempDirectory("poi-categories-").toFile()
        try {
            val repository = PoiRepository(root)
            repository.importGeoJson("one.geojson", VALID_GEOJSON.byteInputStream())
            val snapshot = repository.importCategories(
                "categories.json",
                CATEGORIES_JSON.byteInputStream(),
            )

            assertEquals(1, snapshot.categoryCatalog?.styles?.size)
            val style = snapshot.categoryCatalog?.styles?.get("radar-fijo")
            assertEquals("camera", style?.icon)
            assertTrue(style?.pulseEnabled == true)
            assertEquals("#AABBCC", style?.pulseColor)
            assertTrue(snapshot.geoJson.contains("\"pulseEnabled\":true"))
            assertTrue(snapshot.geoJson.contains("\"pulseColor\":\"#AABBCC\""))

            val withoutCatalog = repository.deleteCategories()
            assertTrue(withoutCatalog.categoryCatalog == null)
            assertTrue(withoutCatalog.geoJson.contains("\"pulseEnabled\":false"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `category catalog rejects unknown schema and invalid presentation`() {
        assertTrue(
            runCatching {
                PoiCategoryParser.parse(
                    "categories.json",
                    CATEGORIES_JSON.replace("\"schema\":1", "\"schema\":2").byteInputStream(),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                PoiCategoryParser.parse(
                    "categories.json",
                    CATEGORIES_JSON.replace("#aabbcc", "red").byteInputStream(),
                )
            }.isFailure,
        )
    }

    @Test
    fun `bounded input fails before retaining body beyond the limit`() {
        assertTrue(
            runCatching {
                readBoundedBytes(ByteArrayInputStream(ByteArray(33)), maximumBytes = 32)
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `icon codes derive from safe png file names`() {
        assertEquals("radar-fijo", PoiRepository.categoryForIconFile("Radar-Fijo.PNG"))
        assertTrue(runCatching { PoiRepository.categoryForIconFile("../radar.png") }.isFailure)
        assertTrue(runCatching { PoiRepository.categoryForIconFile("radar.webp") }.isFailure)
    }

    @Test
    fun `only referenced POI icons are selected for decoding`() {
        val root = createTempDirectory("poi-icon-selection-").toFile()
        try {
            val camera = PoiIcon("camera", File(root, "camera.png"), 64, 64)
            val unused = PoiIcon("unused", File(root, "unused.png"), 64, 64)
            val snapshot = PoiSnapshot(
                sources = listOf(PoiGeoJsonParser.parse("one.geojson", VALID_GEOJSON.byteInputStream())),
                icons = listOf(unused, camera),
                categoryCatalog = PoiCategoryParser.parse(
                    "categories.json",
                    CATEGORIES_JSON.byteInputStream(),
                ),
            )

            assertEquals(listOf(camera), snapshot.iconsReferencedBySources())
        } finally {
            root.deleteRecursively()
        }
    }

    companion object {
        private fun featureCollection(features: String) =
            """{"type":"FeatureCollection","features":[$features]}"""

        private fun pointFeature(id: Int) =
            """{"type":"Feature","id":"$id","geometry":{"type":"Point","coordinates":[-8.4,43.3]},"properties":{}}"""

        private val VALID_GEOJSON = featureCollection(
            """
            {"type":"Feature","id":"r1","geometry":{"type":"Point","coordinates":[-8.4,43.3]},"properties":{"category":"radar-fijo","pulseEnabled":true,"pulseColor":"#ff3030"}},
            {"type":"Feature","geometry":{"type":"Point","coordinates":[-7.8,42.3]},"properties":{"name":"POI"}}
            """.trimIndent(),
        )

        private val CATEGORIES_JSON =
            """{"schema":1,"categories":{"radar-fijo":{"icon":"camera","pulseEnabled":true,"pulseColor":"#aabbcc"}}}"""
    }
}
