package com.lito.a5launcher.ui.components

import com.lito.a5launcher.location.LocationState
import com.lito.a5launcher.location.ValidatedLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            point("Radar fijo · AC-11", 0.0, 0.002),
        )
        val alert = index.nearest(location(), 1_000)!!
        assertEquals("radar", alert.category)
        assertEquals("#FF3030", alert.color)
        assertEquals(222.39, alert.distanceMetres, 0.01)
        assertEquals(0.002, alert.latitude, 0.0)
        assertEquals(0.0, alert.longitude, 0.0)
        assertEquals(111.195, index.nearest(location(heading = 180f), 1_000)!!.distanceMetres, 0.01)
    }

    @Test
    fun `one kilometre is included and points beyond it are excluded`() {
        val kilometre = Math.toDegrees(1_000.0 / 6_371_000.0)
        assertNotNull(index(point("edge", kilometre, 0.0)).nearest(location(heading = 90f), 1_000))
        assertNull(index(point("outside", kilometre + 0.000001, 0.0)).nearest(location(heading = 90f), 1_000))
    }

    @Test
    fun `only points within 45 degrees on either side qualify including north wraparound`() {
        assertNull(index(point("side", 0.001, 0.0)).nearest(location(), 1_000))
        assertNull(index(point("rear", 0.001, -0.0001)).nearest(location(), 1_000))
        val north = index(point("north", 0.0, 0.001))
        assertNotNull(north.nearest(location(heading = 359f), 1_000))
        assertNotNull(north.nearest(location(heading = 45f), 1_000))
        assertNotNull(north.nearest(location(heading = 315f), 1_000))
        assertNull(north.nearest(location(heading = 45.01f), 1_000))
        assertNull(north.nearest(location(heading = 314.99f), 1_000))
        assertNotNull(index(point("here", 0.0, 0.0)).nearest(location(heading = 180f), 1_000))
    }

    @Test
    fun `each category uses its own distance and a nearer out of range point does not hide another`() {
        val data = snapshot(point("near", 0.0, 0.002), point("far", 0.0, 0.02, "section"))
        val catalog = data.categoryCatalog!!.copy(styles = data.categoryCatalog.styles +
            ("section" to PoiCategoryStyle("radar", true, "#FF3030")))
        val configured = data.copy(categoryCatalog = catalog, alertDistancesMetres = mapOf("radar" to 100, "section" to 5_000))
        assertEquals("section", PoiApproachIndex(configured).nearest(location(), 1_000)?.category)
        assertNull(PoiApproachIndex(configured.copy(alertDistancesMetres = mapOf("radar" to 100))).nearest(location(), 1_000))
        val fiveKm = Math.toDegrees(5_000.0 / 6_371_000.0)
        assertNotNull(PoiApproachIndex(snapshot(point("edge", fiveKm, 0.0)).copy(alertDistancesMetres = mapOf("radar" to 5_000)))
            .nearest(location(heading = 90f), 1_000))
        assertNull(PoiApproachIndex(snapshot(point("outside", fiveKm + 0.000001, 0.0)).copy(alertDistancesMetres = mapOf("radar" to 5_000)))
            .nearest(location(heading = 90f), 1_000))
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
        assertNotNull(PoiApproachIndex(snapshot).nearest(location, 1_000))
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
