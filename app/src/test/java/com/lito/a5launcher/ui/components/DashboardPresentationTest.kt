package com.lito.a5launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class DashboardPresentationTest {
    @Test
    fun startupPresentationFollowsTheApprovedFourPhaseTimeline() {
        val black = oemStartupPresentation(0f)
        assertEquals(0f, black.dialOutlineAlpha, 0.001f)
        assertEquals(0f, black.scaleProgress, 0.001f)
        assertEquals(0f, black.depthProgress, 0.001f)
        assertEquals(0f, black.contentAlpha, 0.001f)

        val outlineReady = oemStartupPresentation(250f / OEM_STARTUP_DURATION_MS)
        assertEquals(1f, outlineReady.dialOutlineAlpha, 0.001f)
        assertEquals(0f, outlineReady.scaleProgress, 0.001f)

        val scaleReady = oemStartupPresentation(1_250f / OEM_STARTUP_DURATION_MS)
        assertEquals(1f, scaleReady.scaleProgress, 0.001f)
        assertEquals(0f, scaleReady.contentAlpha, 0.001f)

        val depthMidpoint = oemStartupPresentation(1_400f / OEM_STARTUP_DURATION_MS)
        assertEquals(0.5f, depthMidpoint.depthProgress, 0.001f)
        assertEquals(0f, depthMidpoint.contentAlpha, 0.001f)

        val contentMidpoint = oemStartupPresentation(1_950f / OEM_STARTUP_DURATION_MS)
        assertEquals(1f, contentMidpoint.depthProgress, 0.001f)
        assertEquals(0.5f, contentMidpoint.contentAlpha, 0.001f)

        val settled = oemStartupPresentation(1f)
        assertEquals(true, settled.isActive)
        assertEquals(1f, settled.depthProgress, 0.001f)
        assertEquals(1f, settled.contentAlpha, 0.001f)

        val completed = oemStartupPresentation(null)
        assertEquals(false, completed.isActive)
        assertEquals(1f, completed.dialOutlineAlpha, 0.001f)
        assertEquals(1f, completed.scaleProgress, 0.001f)
        assertEquals(1f, completed.depthProgress, 0.001f)
        assertEquals(1f, completed.contentAlpha, 0.001f)
    }

    @Test
    fun gearPulseKeepsRestingScaleAndPeaksAtTenPercent() {
        assertEquals(1f, oemGearPulseScale(0f), 0.001f)
        assertEquals(1.1f, oemGearPulseScale(0.5f), 0.001f)
        assertEquals(1f, oemGearPulseScale(1f), 0.001f)
    }

    @Test
    fun topCommandOrderRestoresKnownItemsAndAppendsNewDefaults() {
        assertEquals(
            listOf(
                TopCommandItem.MMI,
                TopCommandItem.APPS,
                TopCommandItem.ASSISTANT,
                TopCommandItem.NAVIGATION,
                TopCommandItem.LAUNCHER_SETTINGS,
                TopCommandItem.DEVICE_SETTINGS,
                TopCommandItem.RECENTS,
            ),
            parseTopCommandOrder("MMI,APPS,UNKNOWN,APPS"),
        )
    }

    @Test
    fun topCommandMoveSkipsCommandsThatAreCurrentlyHidden() {
        val visible = DefaultTopCommandOrder.toSet() - TopCommandItem.ASSISTANT

        assertEquals(
            listOf(
                TopCommandItem.ASSISTANT,
                TopCommandItem.APPS,
                TopCommandItem.NAVIGATION,
                TopCommandItem.LAUNCHER_SETTINGS,
                TopCommandItem.DEVICE_SETTINGS,
                TopCommandItem.RECENTS,
                TopCommandItem.MMI,
            ),
            moveVisibleTopCommand(
                DefaultTopCommandOrder,
                TopCommandItem.NAVIGATION,
                direction = 1,
                visibleItems = visible,
            ),
        )
    }

    @Test
    fun footerBlockOrderRestoresKnownBlocksAndAppendsNewDefaults() {
        assertEquals(
            listOf(
                FooterBlockItem.ODOMETER,
                FooterBlockItem.TIME,
                FooterBlockItem.TRIP,
                FooterBlockItem.CONSUMPTION,
                FooterBlockItem.REFUEL_DISTANCE,
                FooterBlockItem.RANGE,
                FooterBlockItem.FUEL,
                FooterBlockItem.WITNESSES,
            ),
            parseFooterBlockOrder("ODOMETER,TIME,UNKNOWN,TIME"),
        )
    }

    @Test
    fun footerBlockMoveKeepsEveryBlockAndClampsAtEnds() {
        assertEquals(
            listOf(
                FooterBlockItem.TIME,
                FooterBlockItem.CONSUMPTION,
                FooterBlockItem.TRIP,
                FooterBlockItem.REFUEL_DISTANCE,
                FooterBlockItem.RANGE,
                FooterBlockItem.FUEL,
                FooterBlockItem.WITNESSES,
                FooterBlockItem.ODOMETER,
            ),
            moveFooterBlock(DefaultFooterBlockOrder, FooterBlockItem.TRIP, 1),
        )
        assertEquals(
            DefaultFooterBlockOrder,
            moveFooterBlock(DefaultFooterBlockOrder, FooterBlockItem.TIME, -1),
        )
    }

    @Test
    fun footerDragSwapWaitsUntilThePointerCrossesTheNextBlockCenter() {
        val widths = mapOf(
            FooterBlockItem.TIME to 100,
            FooterBlockItem.TRIP to 260,
            FooterBlockItem.CONSUMPTION to 180,
        )

        assertEquals(
            180f,
            footerSwapDistancePx(DefaultFooterBlockOrder, FooterBlockItem.TIME, 1, widths)!!,
            0.001f,
        )
        assertEquals(
            180f,
            footerSwapDistancePx(DefaultFooterBlockOrder, FooterBlockItem.TRIP, -1, widths)!!,
            0.001f,
        )
        assertEquals(
            220f,
            footerSwapDistancePx(DefaultFooterBlockOrder, FooterBlockItem.TRIP, 1, widths)!!,
            0.001f,
        )
        assertEquals(
            null,
            footerSwapDistancePx(DefaultFooterBlockOrder, FooterBlockItem.TIME, -1, widths),
        )
    }

    @Test
    fun tripDistanceUsesConfiguredDecimalSeparator() {
        assertEquals("12,3", formatTripDistance(12.34, Locale.forLanguageTag("es-ES")))
        assertEquals("12.3", formatTripDistance(12.34, Locale.US))
    }

    @Test
    fun consumptionDetailsUseConfiguredDecimalSeparator() {
        assertEquals(
            "6,4",
            formatConsumptionNumber(6.44, Locale.forLanguageTag("es-ES")),
        )
        assertEquals("6.4", formatConsumptionNumber(6.44, Locale.US))
    }

    @Test
    fun tripDurationUsesElapsedTimeSinceDeviceBoot() {
        assertEquals("00:00", formatTripDuration(0L))
        assertEquals("00:59", formatTripDuration(59 * 60_000L + 59_000L))
        assertEquals("01:01", formatTripDuration(61 * 60_000L))
        assertEquals("123:45", formatTripDuration((123 * 60L + 45L) * 60_000L))
    }

    @Test
    fun buildDateKeepsLocalizedDateAndAlwaysUses24HourTime() {
        val utc = TimeZone.getTimeZone("UTC")

        assertEquals("11/14/23 22:13", formatBuildDate(1_700_000_000_000L, Locale.US, utc))
        assertEquals(
            "14/11/23 22:13",
            formatBuildDate(1_700_000_000_000L, Locale.forLanguageTag("es-ES"), utc),
        )
    }

    @Test
    fun fuelFractionUsesA5TankCapacityAndClampsInvalidReadings() {
        assertEquals(0f, fuelFraction(-1), 0.001f)
        assertEquals(0.5f, fuelFraction(31.5), 0.001f)
        assertEquals(1f, fuelFraction(63), 0.001f)
        assertEquals(1f, fuelFraction(80), 0.001f)
    }

    @Test
    fun fuelSegmentsRoundsToTenDiscreteStates() {
        assertEquals(0, fuelSegments(-1))
        assertEquals(0, fuelSegments(0))
        assertEquals(1, fuelSegments(4))
        assertEquals(5, fuelSegments(31.5))
        assertEquals(9, fuelSegments(56))
        assertEquals(10, fuelSegments(63))
        assertEquals(10, fuelSegments(80))
    }

    @Test
    fun fuelWarningToneFollowsDiscreteRemainingSegments() {
        assertEquals(FuelSegmentTone.RED, fuelSegmentTone(1))
        assertEquals(FuelSegmentTone.YELLOW, fuelSegmentTone(2))
        assertEquals(FuelSegmentTone.YELLOW, fuelSegmentTone(3))
        assertEquals(FuelSegmentTone.YELLOW, fuelSegmentTone(4))
        assertEquals(FuelSegmentTone.NORMAL, fuelSegmentTone(5))
        assertEquals(FuelSegmentTone.NORMAL, fuelSegmentTone(10))
    }

    @Test
    fun rpmArcUsesTheUnroundedCanValue() {
        assertEquals(1_923f / 6_000f, dialTargetFraction(1_923, 6_000), 0.000_001f)
        assertEquals("1.9", formatDialValue(1_923, DialType.RPM))
    }
}
