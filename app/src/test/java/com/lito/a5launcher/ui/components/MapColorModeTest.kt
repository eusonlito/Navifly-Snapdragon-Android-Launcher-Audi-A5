package com.lito.a5launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MapColorModeTest {
    @Test
    fun automaticModeFollowsSystemNightWhenVehicleStateIsUnavailable() {
        assertEquals(
            MapTileStyle.POSITRON,
            resolveMapTileStyle(MapColorMode.AUTOMATIC, null, false, MapTileStyle.POSITRON),
        )
        assertEquals(
            MapTileStyle.DARK,
            resolveMapTileStyle(MapColorMode.AUTOMATIC, null, true, MapTileStyle.POSITRON),
        )
    }

    @Test
    fun automaticModePrefersVehicleLightsOverSystemState() {
        assertEquals(
            MapTileStyle.DARK,
            resolveMapTileStyle(MapColorMode.AUTOMATIC, true, false, MapTileStyle.BRIGHT),
        )
        assertEquals(
            MapTileStyle.BRIGHT,
            resolveMapTileStyle(MapColorMode.AUTOMATIC, false, true, MapTileStyle.BRIGHT),
        )
    }

    @Test
    fun forcedModesIgnoreSystemNightState() {
        assertEquals(
            MapTileStyle.BRIGHT,
            resolveMapTileStyle(MapColorMode.LIGHT, true, false, MapTileStyle.BRIGHT),
        )
        assertEquals(
            MapTileStyle.DARK,
            resolveMapTileStyle(MapColorMode.DARK, false, false, MapTileStyle.LIBERTY),
        )
    }

    @Test
    fun darkPaletteMakesEachRoadClassProgressivelyMoreVisible() {
        assertEquals("#46535C", darkLineColor("highway_path"))
        assertEquals("#5E6C75", darkLineColor("highway_minor"))
        assertEquals("#94A3AC", darkLineColor("highway_major_inner"))
        assertEquals("#C1CCD2", darkLineColor("highway_motorway_inner"))
        assertEquals("#252E34", darkLineColor("highway_motorway_casing"))
    }

    @Test
    fun darkPaletteSeparatesMapFeaturesFromLand() {
        assertEquals("#102B38", darkFillColor("water"))
        assertEquals("#1B2922", darkFillColor("landuse_park"))
        assertEquals("#283137", darkFillColor("building"))
        assertEquals("#1A2025", darkFillColor("landuse_residential"))
        assertEquals(null, darkFillColor("unknown_layer"))
    }
}
