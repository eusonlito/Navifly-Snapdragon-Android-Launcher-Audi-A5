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
    fun `alert distances persist independently of imports and reject invalid values`() = runBlocking {
        val root = createTempDirectory("poi-alert-distances-").toFile()
        try {
            val repository = PoiRepository(root)
            repository.importCategories("categories.json", CATEGORIES_JSON.byteInputStream())
            assertEquals(1_000, repository.snapshot().alertDistanceMetres("radar-fijo"))
            repository.setAlertDistance("radar-fijo", 100)
            assertEquals(100, PoiRepository(root).snapshot().alertDistanceMetres("radar-fijo"))
            repository.setAlertDistance("radar-fijo", 5_000)
            repository.importCategories("categories.json", CATEGORIES_JSON.byteInputStream())
            repository.importGeoJson("points.geojson", VALID_GEOJSON.byteInputStream())
            assertEquals(5_000, repository.snapshot().alertDistanceMetres("radar-fijo"))
            for (invalid in listOf(0, 99, 101, 5_001, 5_100)) {
                assertTrue(runCatching { repository.setAlertDistance("radar-fijo", invalid) }.isFailure)
            }
            assertTrue(runCatching { repository.setAlertDistance("missing", 1_000) }.isFailure)
            assertEquals(5_000, repository.snapshot().alertDistanceMetres("radar-fijo"))
            File(root, "alert-distances.json").writeText("""{"radar-fijo":99,"quiet":200,"bad":150.5}""")
            assertEquals(mapOf("quiet" to 200), repository.snapshot().alertDistancesMetres)
        } finally {
            root.deleteRecursively()
        }
    }

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
    fun `import skips nearby points of the same category across and within sources`() = runBlocking {
        val root = createTempDirectory("poi-duplicates-").toFile()
        try {
            val repository = PoiRepository(root)
            repository.importGeoJson("existing.geojson", featureCollection(
                pointFeature(1, longitude = 0.0, latitude = 0.0, category = "radar-fijo"),
            ).byteInputStream())
            repository.importGeoJson("new.geojson", featureCollection(listOf(
                pointFeature(2, 0.0004, 0.0, "RADAR-FIJO"), // About 44 m from existing.
                pointFeature(3, 0.0, 0.0, "radar-stop"),
                pointFeature(4, 0.001, 0.0, "radar-fijo"),
                pointFeature(5, 0.0011, 0.0, "radar-fijo"),
                pointFeature(6, 0.0015, 0.0, "radar-fijo"), // Far enough from retained point 4.
            ).joinToString(",")).byteInputStream())

            val stored = PoiRepository(root).snapshot()
            assertEquals(listOf("1"), stored.sources.first().features.map { it.json.getString("id") })
            assertEquals(listOf("3", "4", "6"), stored.sources.last().features.map { it.json.getString("id") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `proximity filtering handles the 50 metre boundary dateline and poles`() = runBlocking {
        val root = createTempDirectory("poi-distance-").toFile()
        try {
            val repository = PoiRepository(root)
            // At the equator these offsets are 49.9 m and 50.1 m on the mean Earth sphere.
            val points = listOf(
                pointFeature(1, 0.0, 0.0),
                pointFeature(2, 0.000448761, 0.0),
                pointFeature(3, -0.000450560, 0.0),
                pointFeature(4, 179.9999, 0.0),
                pointFeature(5, -179.9999, 0.0),
                pointFeature(6, 0.0, 89.9999),
                pointFeature(7, 180.0, 89.9999),
                pointFeature(8, 45.0, -89.9999),
                pointFeature(9, -135.0, -89.9999),
            )
            val result = repository.importGeoJson("edges.geojson", featureCollection(points.joinToString(",")).byteInputStream())
            assertEquals(listOf("1", "3", "4", "6", "8"), result.sources.single().features.map { it.json.getString("id") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `reimport ignores the old version but still checks other sources`() = runBlocking {
        val root = createTempDirectory("poi-reimport-").toFile()
        try {
            val repository = PoiRepository(root)
            val original = featureCollection(pointFeature(1, 0.0, 0.0))
            repository.importGeoJson("one.geojson", original.byteInputStream())
            repository.importGeoJson("one.geojson", original.byteInputStream())
            assertEquals(1, repository.snapshot().totalPoints)
            repository.importGeoJson("two.geojson", original.byteInputStream())
            assertEquals(0, repository.snapshot().sources.last().pointCount)
            repository.importGeoJson("two.geojson", featureCollection(pointFeature(2, 1.0, 1.0)).byteInputStream())
            val updated = featureCollection(listOf(
                pointFeature(3, 0.0001, 0.0),
                pointFeature(4, 1.0001, 1.0),
            ).joinToString(","))
            repository.importGeoJson("one.geojson", updated.byteInputStream())
            assertEquals(listOf("3"), repository.snapshot().sources.first().features.map { it.json.getString("id") })
            assertEquals(2, repository.snapshot().totalPoints)
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

        private fun pointFeature(
            id: Int,
            longitude: Double = -8.4 + id * 0.001,
            latitude: Double = 43.3,
            category: String = "poi-default",
        ) = """{"type":"Feature","id":"$id","geometry":{"type":"Point","coordinates":[$longitude,$latitude]},"properties":{"category":"$category"}}"""

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
