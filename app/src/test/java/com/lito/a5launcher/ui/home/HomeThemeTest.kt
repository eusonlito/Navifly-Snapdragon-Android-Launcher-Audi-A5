package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.FuelSegmentTone
import com.lito.a5launcher.ui.components.MapPalette
import com.lito.a5launcher.ui.components.DefaultFooterBlockOrder
import com.lito.a5launcher.ui.components.MapThemeColors
import com.lito.a5launcher.ui.components.parseFooterBlockOrder
import com.lito.a5launcher.ui.components.MapTileStyle
import com.lito.a5launcher.ui.components.OemCockpitTokens
import com.lito.a5launcher.ui.components.StatisticsPanelScope
import com.lito.a5launcher.ui.components.mapFocalPadding
import com.lito.a5launcher.ui.components.mapPaletteFor
import com.lito.a5launcher.ui.theme.SLineRed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HomeThemeTest {
    @Test
    fun unknownOrMissingThemeFallsBackToClassic() {
        assertEquals(HomeTheme.CLASSIC, parseHomeTheme(null))
        assertEquals(HomeTheme.CLASSIC, parseHomeTheme("NEON"))
        assertEquals(HomeTheme.FACETS, parseHomeTheme("FACETS"))
        assertEquals(HomeTheme.CLASSIC, parseHomeTheme("ORBIT"))
        assertEquals(HomeTheme.GALAXY, parseHomeTheme("GALAXY"))
        assertEquals(HomeTheme.CLASSIC, parseHomeTheme("SATELLITE"))
        assertEquals(HomeTheme.ADVENTURE, parseHomeTheme("ADVENTURE"))
        assertEquals(HomeTheme.SUBMARINE, parseHomeTheme("SUBMARINE"))
        assertEquals(HomeTheme.MOON, parseHomeTheme("MOON"))
    }

    @Test
    fun illustratedThemesUseTheirOwnPaletteAndOrderKey() {
        IllustratedStyle.entries.forEach { style ->
            val theme = HomeTheme.valueOf(style.name)
            val tokens = homeTokensFor(theme, dark = false)
            assertEquals(style.palette.skyBottom, tokens.background)
            assertEquals(style.palette.arc, tokens.accent)
            assertEquals("footer_block_order_${style.name.lowercase()}", footerBlockOrderKey(theme))
            assertEquals(DefaultFooterBlockOrder, defaultFooterBlockOrder(theme))
        }
    }

    @Test
    fun galaxyUsesItsPaletteAndAdventureTheTelemetryOrder() {
        assertEquals(GalaxyPalette.skyBottom, homeTokensFor(HomeTheme.GALAXY, dark = false).background)
        assertEquals(DefaultFooterBlockOrder, defaultFooterBlockOrder(HomeTheme.GALAXY))
        assertEquals(defaultFooterBlockOrder(HomeTheme.TELEMETRY), defaultFooterBlockOrder(HomeTheme.ADVENTURE))
        assertEquals("footer_block_order_adventure", footerBlockOrderKey(HomeTheme.ADVENTURE))
    }

    @Test
    fun roundRectDistanceHandlesCirclesAndRoundedRectangles() {
        // A circle whose inner rectangle collapses to a point after float rounding.
        val circle = Rect(10.1f, 20.3f, 319.22003f, 329.42f)
        val radius = circle.width / 2f
        assertEquals(-radius, roundRectDistance(circle.center, circle, radius), .01f)
        assertEquals(10f, roundRectDistance(Offset(circle.right + 10f, circle.center.y), circle, radius), .01f)
        val box = Rect(0f, 0f, 100f, 50f)
        assertEquals(5f, roundRectDistance(Offset(50f, 55f), box, 10f), .001f)
        assertTrue(roundRectDistance(Offset(1f, 1f), box, 10f) > 0f)
    }

    @Test
    fun footprintsInflateEvenlyForTheMapCutouts() {
        val dial = Footprint(10.dp, 20.dp, 100.dp, 100.dp, 50.dp)
        val cut = dial.inflate(8.dp)
        assertEquals(2.dp, cut.left)
        assertEquals(12.dp, cut.top)
        assertEquals(116.dp, cut.width)
        assertEquals(58.dp, cut.radius)
        assertEquals(118.dp, cut.right)
        assertEquals(60.dp, dial.centerX)
    }

    @Test
    fun classicTokensReproduceTheOriginalCockpitColours() {
        val day = homeTokensFor(HomeTheme.CLASSIC, dark = false)
        val night = homeTokensFor(HomeTheme.CLASSIC, dark = true)
        assertEquals(day, night)
        assertEquals(Color.Black, day.chrome)
        assertEquals(Color.White, day.content)
        assertEquals(Color.White.copy(alpha = .50f), day.label)
        assertEquals(SLineRed, day.warning)
        assertEquals(Color(0xFFFFC400), day.caution)
        assertEquals(Color(0xFF273037), day.fuelInactive)
        assertEquals(Color(0xFFFF3B3B), day.witnessAlert)
        assertEquals(Color(0xFF263036), day.witnessInactive)
        assertEquals(Color(0xFF42D36B), day.witnessLights)
        assertEquals(OemCockpitTokens.GraphitePressed, day.dragPlate)
        assertEquals(MapThemeColors.Classic, day.map)
        assertNull(day.numberFamily)
    }

    @Test
    fun everyThemeSharesTheUserMapStyle() {
        assertNull(mapPaletteFor(MapTileStyle.POSITRON))
        assertNull(mapPaletteFor(MapTileStyle.LIBERTY))
        assertNull(mapPaletteFor(MapTileStyle.BRIGHT))
        assertEquals(MapPalette.ClassicDark, mapPaletteFor(MapTileStyle.DARK))
        assertNotEquals(
            homeTokensFor(HomeTheme.HORIZON, false).map.loadingBackground,
            homeTokensFor(HomeTheme.HORIZON, true).map.loadingBackground,
        )
    }

    @Test
    fun fuelLevelColoursMatchTheClassicFooterInEveryTheme() {
        HomeTheme.entries.forEach { theme ->
            val tokens = homeTokensFor(theme, dark = false)
            assertEquals(OemCockpitTokens.Cyan, fuelSegmentColor(true, FuelSegmentTone.NORMAL, tokens))
            assertEquals(Color(0xFFFFC400), fuelSegmentColor(true, FuelSegmentTone.YELLOW, tokens))
            assertEquals(SLineRed, fuelSegmentColor(true, FuelSegmentTone.RED, tokens))
            assertEquals(tokens.fuelInactive, fuelSegmentColor(false, FuelSegmentTone.RED, tokens))
        }
    }

    @Test
    fun blockOrderIsStoredPerTheme() {
        assertEquals("footer_block_order", footerBlockOrderKey(HomeTheme.CLASSIC))
        assertEquals("footer_block_order_facets", footerBlockOrderKey(HomeTheme.FACETS))
        assertEquals(DefaultFooterBlockOrder, defaultFooterBlockOrder(HomeTheme.HORIZON))
        assertEquals(FooterBlockItem.CONSUMPTION, defaultFooterBlockOrder(HomeTheme.TELEMETRY).first())
        assertEquals(
            defaultFooterBlockOrder(HomeTheme.TELEMETRY),
            parseFooterBlockOrder(null, defaultFooterBlockOrder(HomeTheme.TELEMETRY)),
        )
        assertEquals(FooterBlockItem.entries.toSet(), defaultFooterBlockOrder(HomeTheme.TELEMETRY).toSet())
    }

    @Test
    fun classicFocalPaddingKeepsTheVehicleAtEightyPercentOfTheMap() {
        val padding = mapFocalPadding(width = 2400f, height = 672f)
        assertEquals(0.0, padding.left, 0.001)
        assertEquals(672.0 * .60, padding.top, 0.01)
        assertEquals(0.0, padding.right, 0.001)
        assertEquals(0.0, padding.bottom, 0.001)
    }

    @Test
    fun focalPaddingCentresTheVehicleInTheUncoveredArea() {
        val padding = mapFocalPadding(
            width = 2400f,
            height = 896f,
            obscuredLeft = 600f,
            obscuredTop = 100f,
            obscuredRight = 400f,
            obscuredBottom = 200f,
        )
        // Free area x: 600..2000 → centre 1300; y: 100..696 → 80 % at 576.8.
        assertEquals(200.0, padding.left, 0.01)
        assertEquals(0.0, padding.right, 0.01)
        assertEquals(257.6, padding.top, 0.01)
        assertEquals(0.0, padding.bottom, 0.01)
    }

    @Test
    fun reorderKeepsPinnedBlocksInPlace() {
        val order = listOf(
            FooterBlockItem.TIME,
            FooterBlockItem.FUEL,
            FooterBlockItem.TRIP,
            FooterBlockItem.WITNESSES,
            FooterBlockItem.RANGE,
        )
        val visible = setOf(FooterBlockItem.TIME, FooterBlockItem.TRIP, FooterBlockItem.RANGE)
        assertEquals(
            listOf(
                FooterBlockItem.TRIP,
                FooterBlockItem.FUEL,
                FooterBlockItem.TIME,
                FooterBlockItem.WITNESSES,
                FooterBlockItem.RANGE,
            ),
            moveVisibleItem(order, FooterBlockItem.TIME, 1, visible),
        )
        assertEquals(
            listOf(
                FooterBlockItem.RANGE,
                FooterBlockItem.FUEL,
                FooterBlockItem.TIME,
                FooterBlockItem.WITNESSES,
                FooterBlockItem.TRIP,
            ),
            moveItemToVisibleIndex(order, FooterBlockItem.RANGE, 0, visible),
        )
        assertEquals(order, moveVisibleItem(order, FooterBlockItem.FUEL, 1, visible))
    }

    @Test
    fun gridTargetUsesTheCellUnderTheFingerOrTheNearestCell() {
        val cells = listOf(
            CellBounds(0f, 0f, 100f, 100f),
            CellBounds(110f, 0f, 210f, 100f),
            CellBounds(0f, 110f, 100f, 210f),
            null,
        )
        assertEquals(1, gridTargetIndex(cells, 150f, 50f))
        assertEquals(2, gridTargetIndex(cells, 40f, 190f))
        assertEquals(0, gridTargetIndex(cells, 104f, 40f))
        assertNull(gridTargetIndex(listOf(null, null), 0f, 0f))
    }

    @Test
    fun blocksOpenTheSameStatisticsAsTheOriginalFooter() {
        assertEquals(StatisticsPanelScope.TRIP, blockStatisticsScope(FooterBlockItem.TIME))
        assertEquals(StatisticsPanelScope.TRIP, blockStatisticsScope(FooterBlockItem.CONSUMPTION))
        assertEquals(StatisticsPanelScope.PARTIAL, blockStatisticsScope(FooterBlockItem.REFUEL_DISTANCE))
        assertEquals(StatisticsPanelScope.TOTAL, blockStatisticsScope(FooterBlockItem.ODOMETER))
        assertNull(blockStatisticsScope(FooterBlockItem.RANGE))
        assertNull(blockStatisticsScope(FooterBlockItem.WITNESSES))
    }

    @Test
    fun blockReadingsFormatValuesAndWarnings() {
        val spanish = Locale.forLanguageTag("es-ES")
        val trip = JourneyStatisticsSnapshot(distanceKm = 32.7, elapsedMs = 42 * 60_000L)
        assertEquals(
            BlockReading("00:42", null, false),
            blockReading(FooterBlockItem.TIME, trip, trip, 0, 0, spanish),
        )
        assertEquals(
            BlockReading("32,7", "km", false),
            blockReading(FooterBlockItem.TRIP, trip, trip, 0, 0, spanish),
        )
        assertEquals("—", blockReading(FooterBlockItem.RANGE, trip, trip, 0, 0, spanish)!!.value)
        assertEquals(true, blockReading(FooterBlockItem.RANGE, trip, trip, 60, 0, spanish)!!.warning)
        assertEquals(false, blockReading(FooterBlockItem.RANGE, trip, trip, 811, 0, spanish)!!.warning)
        assertEquals("220.024", blockReading(FooterBlockItem.ODOMETER, trip, trip, 0, 220_024, spanish)!!.value)
        assertNull(blockReading(FooterBlockItem.FUEL, trip, trip, 0, 0, spanish))
    }

    @Test
    fun outsideTemperatureUsesOneDecimalOrADash() {
        assertEquals("24,5°C", formatOutsideTemperature(24.5, Locale.forLanguageTag("es-ES")))
        assertEquals("—", formatOutsideTemperature(Double.NaN, Locale.US))
    }
}

class LinearGaugeTest {
    @Test
    fun speedScaleLabelsEveryFortyAndTicksEveryTen() {
        assertEquals(listOf("0", "40", "80", "120", "160", "200", "240"), gaugeLabels(SpeedGaugeScale).map { it.text })
        val ticks = gaugeTickFractions(SpeedGaugeScale)
        assertEquals(25, ticks.size)
        assertEquals(7, ticks.count { it.second })
    }

    @Test
    fun rpmScaleLabelsThousands() {
        assertEquals(listOf("0", "1", "2", "3", "4", "5", "6"), gaugeLabels(RpmGaugeScale).map { it.text })
        assertEquals(5f / 6f, gaugeLabels(RpmGaugeScale)[5].fraction, 1e-5f)
        assertEquals(.75f, RPM_RED_ZONE_FRACTION, 1e-5f)
    }

    @Test
    fun anyVisibleValueLightsAtLeastOneSegment() {
        assertEquals(0, gaugeSegmentsLit(0f, 18))
        assertEquals(1, gaugeSegmentsLit(.001f, 18))
        assertEquals(5, gaugeSegmentsLit(1800f / 6000f, 18))
        assertEquals(18, gaugeSegmentsLit(1.4f, 18))
        assertEquals(0, gaugeSegmentsLit(.5f, 0))
    }

    @Test
    fun redZoneStartsAtFiveThousandRpm() {
        assertEquals(18, redZoneStartIndex(RPM_RED_ZONE_FRACTION, 24))
        assertEquals(9, redZoneStartIndex(RPM_RED_ZONE_FRACTION, 12))
    }

    @Test
    fun shiftLightsGoGreenAmberRed() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF22C55E), shiftLightColor(7, 24))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFACC15), shiftLightColor(8, 24))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFACC15), shiftLightColor(17, 24))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFEF4444), shiftLightColor(18, 24))
    }

    @Test
    fun speedDialUsesTheTwoHundredFortyScale() {
        assertEquals(1f / 3f, com.lito.a5launcher.ui.components.dialTargetFraction(80, 240), 1e-5f)
        assertEquals(1f, com.lito.a5launcher.ui.components.dialTargetFraction(260, 240), 1e-5f)
    }
}
