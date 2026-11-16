package com.lito.a5launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MapColorModeTest {
    @Test
    fun firstKnownLightsSignalAppliesImmediatelyAtStartup() {
        val state = DelayedVehicleLightsState().update(lightsOn = true, nowMs = 1_000L)

        assertEquals(true, state.effectiveLightsOn)
        assertEquals(null, state.remainingDelayMs(1_000L))
    }

    @Test
    fun laterLightsOnSignalRequiresOneContinuousMinute() {
        val lightsOff = DelayedVehicleLightsState().update(lightsOn = false, nowMs = 1_000L)
        val pending = lightsOff.update(lightsOn = true, nowMs = 2_000L)

        assertEquals(false, pending.effectiveLightsOn)
        assertEquals(60_000L, pending.remainingDelayMs(2_000L))
        assertEquals(
            false,
            pending.update(lightsOn = true, nowMs = 61_999L).effectiveLightsOn,
        )
        assertEquals(
            true,
            pending.update(lightsOn = true, nowMs = 62_000L).effectiveLightsOn,
        )
    }

    @Test
    fun lightsOffCancelsPendingDarkModeAndAppliesImmediately() {
        val pending = DelayedVehicleLightsState()
            .update(lightsOn = false, nowMs = 1_000L)
            .update(lightsOn = true, nowMs = 2_000L)

        val lightsOff = pending.update(lightsOn = false, nowMs = 30_000L)

        assertEquals(false, lightsOff.effectiveLightsOn)
        assertEquals(null, lightsOff.remainingDelayMs(30_000L))
    }

    @Test
    fun mapZoomSupportsTheFullMapLibreZoomOutRange() {
        assertEquals(0.0, clampMapZoom(-1.0), 0.0)
        assertEquals(7.5, clampMapZoom(7.5), 0.0)
        assertEquals(18.0, clampMapZoom(19.0), 0.0)
    }

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
        assertEquals("#46535C", MapPalette.ClassicDark.lineColor("highway_path"))
        assertEquals("#5E6C75", MapPalette.ClassicDark.lineColor("highway_minor"))
        assertEquals("#94A3AC", MapPalette.ClassicDark.lineColor("highway_major_inner"))
        assertEquals("#C1CCD2", MapPalette.ClassicDark.lineColor("highway_motorway_inner"))
        assertEquals("#252E34", MapPalette.ClassicDark.lineColor("highway_motorway_casing"))
    }

    @Test
    fun darkPaletteSeparatesMapFeaturesFromLand() {
        assertEquals("#102B38", MapPalette.ClassicDark.fillColor("water"))
        assertEquals("#1B2922", MapPalette.ClassicDark.fillColor("landuse_park"))
        assertEquals("#283137", MapPalette.ClassicDark.fillColor("building"))
        assertEquals("#1A2025", MapPalette.ClassicDark.fillColor("landuse_residential"))
        assertEquals(null, MapPalette.ClassicDark.fillColor("unknown_layer"))
    }
}
