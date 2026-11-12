package com.lito.a5launcher.ui.components

import com.lito.a5launcher.location.LocationState
import com.lito.a5launcher.location.ValidatedLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoiApproachTest {
    @Test
    fun `only pulsing categories participate and nearest point ahead wins`() {
        val index = index(
            point("behind", 0.0, -0.001),
            point("disabled", 0.0, 0.0001, "quiet"),
            point("missing", 0.0, 0.0002, "missing"),
            point("further", 0.0, 0.005),
            point("nearest", 0.0, 0.002),
        )
        val alert = index.nearest(location(), 1_000)!!
        assertEquals("nearest", alert.name)
        assertEquals("#FF3030", alert.color)
        assertEquals(222.39, alert.distanceMetres, 0.01)
        assertEquals("behind", index.nearest(location(heading = 180f), 1_000)?.name)
    }

    @Test
    fun `one kilometre is included and points beyond it are excluded`() {
        val kilometre = Math.toDegrees(1_000.0 / 6_371_000.0)
        assertEquals("edge", index(point("edge", kilometre, 0.0)).nearest(location(heading = 90f), 1_000)?.name)
        assertNull(index(point("outside", kilometre + 0.000001, 0.0)).nearest(location(heading = 90f), 1_000))
    }

    @Test
    fun `sideways points qualify but points behind do not including north wraparound`() {
        assertEquals("side", index(point("side", 0.001, 0.0)).nearest(location(), 1_000)?.name)
        assertNull(index(point("rear", 0.001, -0.0001)).nearest(location(), 1_000))
        assertEquals("north", index(point("north", 0.0, 0.001)).nearest(location(heading = 359f), 1_000)?.name)
        assertEquals("here", index(point("here", 0.0, 0.0)).nearest(location(heading = 180f), 1_000)?.name)
    }

    @Test
    fun `no live GPS or usable heading means no alert`() {
        val index = index(point("near", 0.0, 0.001))
        assertNull(index.nearest(LocationState(), 1_000))
        assertNull(index.nearest(location(heading = null), 1_000))
        assertNull(index.nearest(location().copy(lastGpsFixElapsedMillis = 0), 1_000))
        assertNull(index.nearest(location(), 31_001))
        assertNull(index.nearest(location().let { it.copy(position = it.position!!.copy(acceptedElapsedMillis = null)) }, 1_000))
    }

    @Test
    fun `dateline proximity and catalog removal are handled`() {
        val snapshot = snapshot(point("across", -179.999, 0.0))
        val location = location(heading = 90f).let { it.copy(position = it.position!!.copy(longitude = 179.999)) }
        assertEquals("across", PoiApproachIndex(snapshot).nearest(location, 1_000)?.name)
        assertNull(PoiApproachIndex(snapshot.copy(categoryCatalog = null)).nearest(location, 1_000))
    }

    private fun location(heading: Float? = 0f) = LocationState(
        ValidatedLocation(0.0, 0.0, 0f, 0f, 1_000, heading), 1_000,
    )

    private fun point(name: String, longitude: Double, latitude: Double, category: String = "radar") =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$longitude,$latitude]},"properties":{"name":"$name","category":"$category"}}"""

    private fun index(vararg points: String) = PoiApproachIndex(snapshot(*points))

    private fun snapshot(vararg points: String) = PoiSnapshot(
        sources = listOf(PoiGeoJsonParser.parse("test.geojson", """{"type":"FeatureCollection","features":[${points.joinToString(",")}]}""".byteInputStream())),
        categoryCatalog = PoiCategoryCatalog("categories.json", mapOf(
            "radar" to PoiCategoryStyle("radar", true, "#FF3030"),
            "quiet" to PoiCategoryStyle("quiet", false, "#FFFFFF"),
        )),
    )
}
