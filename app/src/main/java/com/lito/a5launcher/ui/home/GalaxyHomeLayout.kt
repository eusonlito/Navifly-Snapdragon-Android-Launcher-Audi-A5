package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lito.a5launcher.R
import com.lito.a5launcher.ui.components.DialType
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.SPEED_DIAL_MAX_KMH
import com.lito.a5launcher.ui.components.formatDialValue
import com.lito.a5launcher.ui.components.fuelFraction
import com.lito.a5launcher.ui.components.fuelSegmentTone
import com.lito.a5launcher.ui.components.fuelSegments
import kotlin.math.max

private val SpaceSpeedScale = GaugeScale(maxValue = SPEED_DIAL_MAX_KMH, labelStep = 80, minorPerLabel = 4)
private val SpaceSideTiles = setOf(FooterBlockItem.FUEL, FooterBlockItem.WITNESSES)

/**
 * Galaxy home: glass panels with speed and RPM on the left, the map in the
 * middle, fuel and witnesses on the right and the six values at the bottom.
 * No artwork is drawn over the map.
 */
@Composable
internal fun GalaxyHomeLayout(
    state: HomeState,
    actions: HomeActions,
    slots: HomeSlots,
    modifier: Modifier = Modifier,
) {
    val palette = currentIllustratedPalette(HomeTheme.GALAXY)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        val margin = h * .02f
        val gap = margin
        val topHeight = h * .11f
        val corner = h * .045f
        val leftWidth = w * .2f
        val rightWidth = w * .2f
        val footerHeight = h * .125f
        val middleTop = margin * 2f + topHeight
        val middleBottom = h - margin * 2f - footerHeight
        val middleHeight = middleBottom - middleTop
        val mapLeft = margin + leftWidth + gap
        val mapWidth = w - margin * 2f - leftWidth - rightWidth - gap * 2f
        val viewport = remember(corner) { MapViewportSpec(cornerRadius = corner) }

        SpaceBackdrop(Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress))

        Box(
            Modifier
                .offset(x = mapLeft, y = middleTop)
                .size(mapWidth, middleHeight)
                .clip(RoundedCornerShape(corner)),
        ) {
            slots.map(Modifier.fillMaxSize(), viewport)
            StartupMapCover(state)
            slots.mapOverlay(this, PaddingValues())
        }
        Canvas(
            Modifier
                .offset(x = mapLeft, y = middleTop)
                .size(mapWidth, middleHeight)
                .startupOutlineAlpha(state.startupProgress),
        ) {
            drawSpaceOutline(palette, Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner.toPx()))) })
        }

        Column(
            Modifier
                .offset(x = margin, y = middleTop)
                .size(leftWidth, middleHeight)
                .zIndex(1f),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            SpaceSpeedPanel(state, h, corner, Modifier.weight(1f).fillMaxWidth())
            SpaceRpmPanel(state, h, corner, Modifier.weight(1f).fillMaxWidth())
        }

        GalaxySideTiles(
            state,
            actions,
            h,
            gap,
            corner,
            Modifier
                .offset(x = w - margin - rightWidth, y = middleTop)
                .size(rightWidth, middleHeight)
                .startupContentAlpha(state)
                .zIndex(1f),
        )

        SpaceTopBar(
            state,
            actions,
            h,
            topHeight,
            Modifier
                .padding(start = margin, end = margin, top = margin)
                .fillMaxWidth()
                .height(topHeight)
                .startupContentAlpha(state)
                .zIndex(2f),
        )

        GalaxyFooter(
            state,
            actions,
            h,
            corner,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = margin, end = margin, bottom = margin)
                .fillMaxWidth()
                .height(footerHeight)
                .startupContentAlpha(state)
                .zIndex(2f),
        )
    }
}

/** Thin glowing outline, cyan at the top fading to the theme's second tone. */
private fun DrawScope.drawSpaceOutline(palette: IllustratedPalette, outline: Path) {
    val border = Brush.verticalGradient(listOf(palette.frame, palette.frameShade))
    drawPath(outline, border, alpha = .14f, style = Stroke(6.dp.toPx()))
    drawPath(outline, border, alpha = .9f, style = Stroke(1.5.dp.toPx()))
}

/** Glass panel; [art] is drawn inside it, clipped, behind the content. */
private fun Modifier.spacePanel(
    palette: IllustratedPalette,
    corner: Dp,
    art: (DrawScope.() -> Unit)? = null,
): Modifier = drawWithCache {
    val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner.toPx()))) }
    val fill = Brush.verticalGradient(listOf(palette.panel, palette.dialFace))
    onDrawBehind {
        drawPath(outline, fill)
        if (art != null) clipPath(outline) { art() }
        drawSpaceOutline(palette, outline)
    }
}

/** Image scaled to cover [rect] entirely, centred, cropped by the caller's clip. */
private fun DrawScope.drawArtCover(image: ImageBitmap, rect: Rect, alpha: Float, filter: ColorFilter?) {
    val scale = max(rect.width / image.width, rect.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    drawArt(image, Rect(Offset(rect.center.x - width / 2f, rect.center.y - height / 2f), Size(width, height)), alpha, filter)
}

@Composable
private fun SpaceSpeedPanel(state: HomeState, h: Dp, corner: Dp, modifier: Modifier) {
    val fraction = animatedGaugeFraction(state.speed, SPEED_DIAL_MAX_KMH, rpm = false)
    val motion = LocalTripArt.current
    val rocket = ImageBitmap.imageResource(R.drawable.illustration_galaxy_rocket)
    val palette = currentIllustratedPalette(HomeTheme.GALAXY)
    val filter = illustratedArtworkFilter()
    Box(
        modifier
            .startupOutlineAlpha(state.startupProgress)
            .spacePanel(palette, corner) {
                drawGalaxyLaunch(rocket, rocket.rectCentered(size.width * .78f, size.height * .30f, size.width * .40f), motion[TripGesture.LAUNCH], filter)
            },
    ) {
        SpaceReading(
            value = { formatDialValue(state.speed.value, DialType.SPEED) },
            unit = "km/h",
            h = h,
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalBarGauge(
                fraction = fraction,
                scale = SpaceSpeedScale,
                startupProgress = state.startupProgress,
                barHeight = h * .03f,
                labelSize = (h.value * .032f).sp,
                modifier = Modifier.fillMaxWidth().height(h * .1f),
            )
        }
        TripTapTarget(TripGesture.LAUNCH) { (w, h) -> rocket.rectCentered(w * .78f, h * .30f, w * .40f) }
    }
}

@Composable
private fun SpaceRpmPanel(state: HomeState, h: Dp, corner: Dp, modifier: Modifier) {
    val palette = currentIllustratedPalette(HomeTheme.GALAXY)
    val fraction = animatedGaugeFraction(state.rpm, RPM_SCALE_MAX, rpm = true)
    val filter = illustratedArtworkFilter()
    val nebula = ImageBitmap.imageResource(R.drawable.illustration_galaxy_nebula)
    Box(
        modifier
            .startupOutlineAlpha(state.startupProgress)
            .spacePanel(palette, corner) {
                drawArtCover(nebula, Rect(Offset.Zero, size), alpha = .5f, filter = filter)
            },
    ) {
        Row(
            Modifier.fillMaxSize().padding(end = h * .03f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpaceReading(
                value = { formatDialValue(state.rpm.value, DialType.RPM) },
                unit = "x1000",
                h = h,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                valueScale = .9f,
            ) {
                HorizontalBarGauge(
                    fraction = fraction,
                    scale = RpmGaugeScale,
                    startupProgress = state.startupProgress,
                    barHeight = h * .03f,
                    labelSize = (h.value * .032f).sp,
                    redZoneFrom = RPM_RED_ZONE_FRACTION,
                    showMinorTicks = false,
                    modifier = Modifier.fillMaxWidth().height(h * .1f),
                )
            }
            SpaceGear(state, palette, h)
        }
    }
}

/** Value over its bar, both inset from the panel edges. */
@Composable
private fun SpaceReading(
    value: () -> String,
    unit: String,
    h: Dp,
    modifier: Modifier,
    valueScale: Float = 1f,
    gauge: @Composable () -> Unit,
) {
    Column(
        modifier.padding(horizontal = h * .035f),
        verticalArrangement = Arrangement.Center,
    ) {
        ValueWithUnit(value(), unit, (h.value * .12f * valueScale).sp, (h.value * .042f).sp)
        Spacer(Modifier.height(h * .035f))
        gauge()
    }
}

/** Gear in a glowing rounded square. */
@Composable
private fun SpaceGear(state: HomeState, palette: IllustratedPalette, h: Dp) {
    val size = h * .145f
    Box(
        Modifier
            .size(size)
            .drawWithCache {
                val outline = Path().apply {
                    addRoundRect(RoundRect(Rect(Offset.Zero, this@drawWithCache.size), CornerRadius(this@drawWithCache.size.width * .2f)))
                }
                onDrawBehind {
                    drawPath(outline, Color(0xE6090E2C))
                    drawPath(outline, palette.accent, alpha = .22f, style = Stroke(7.dp.toPx()))
                    drawPath(outline, palette.accent, style = Stroke(2.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        IllustratedText(state.gear.value, (h.value * .095f).sp, palette.content, FontWeight.Black)
    }
}

/** Fuel above witnesses; long-press drag swaps them. */
@Composable
private fun GalaxySideTiles(state: HomeState, actions: HomeActions, h: Dp, gap: Dp, corner: Dp, modifier: Modifier) {
    val palette = currentIllustratedPalette(HomeTheme.GALAXY)
    val order = state.footerBlockOrder.value
    val tiles = remember(order) { order.filter(SpaceSideTiles::contains) }
    val sizes = remember { mutableStateMapOf<FooterBlockItem, Int>() }
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    val motion = LocalTripArt.current
    val earth = ImageBitmap.imageResource(R.drawable.illustration_galaxy_earth)
    val filter = illustratedArtworkFilter()
    val lunar = ImageBitmap.imageResource(R.drawable.illustration_galaxy_lunar)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        tiles.forEach { item ->
            key(item) {
                LinearReorderSlot(
                    item = item,
                    order = order,
                    visibleItems = SpaceSideTiles,
                    axis = ReorderAxis.VERTICAL,
                    sizes = sizes,
                    gapPx = gapPx,
                    plateShape = RoundedCornerShape(corner),
                    onMove = actions.onFooterBlockOrderChanged,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    when (item) {
                        FooterBlockItem.FUEL -> Box(
                            Modifier.fillMaxSize().spacePanel(palette, corner) {
                                val horizon = earth.rectAboveBase(size.width * .5f, size.height * 1.02f, size.width * 1.15f)
                                drawArt(earth, horizon, filter = filter)
                                drawAurora(horizon, motion[TripGesture.AURORA])
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            SpaceFuel(state, h, Modifier.fillMaxWidth().padding(horizontal = h * .04f).offset(y = -h * .03f))
                            TripTapTarget(TripGesture.AURORA) { (w, h) -> earth.rectAboveBase(w * .5f, h * 1.02f, w * 1.15f) }
                        }
                        else -> Box(
                            Modifier.fillMaxSize().spacePanel(palette, corner) {
                                drawArt(lunar, lunar.rectAboveBase(size.width * .5f, size.height * 1.02f, size.width * 1.1f), alpha = .9f, filter = filter)
                                drawRover(motion[TripGesture.ROVER])
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            WitnessGroup(
                                state,
                                scale = 1.05f,
                                arrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth().offset(y = -h * .03f),
                            )
                            TripTapTarget(TripGesture.ROVER) { (w, h) -> lunar.rectAboveBase(w * .5f, h * 1.02f, w * 1.1f) }
                        }
                    }
                }
            }
        }
    }
}

/** Pump, continuous level bar and litres on one line. */
@Composable
private fun SpaceFuel(state: HomeState, h: Dp, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    val fuel = state.fuel.value
    val tone = fuelSegmentTone(fuelSegments(fuel))
    val color = fuelContentColor(tone, tokens)
    val fraction = fuelFraction(fuel)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FuelPumpIcon(color, Modifier.size(h * .075f))
        Spacer(Modifier.width(h * .025f))
        Canvas(Modifier.weight(1f).height(h * .03f)) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(tokens.fuelInactive, cornerRadius = radius)
            if (fraction > 0f) {
                drawRoundRect(
                    fuelSegmentColor(true, tone, tokens),
                    size = Size(max(size.height, size.width * fraction), size.height),
                    cornerRadius = radius,
                )
            }
        }
        Spacer(Modifier.width(h * .025f))
        ValueWithUnit(fuel.toString(), "L", (h.value * .075f).sp, (h.value * .04f).sp, color = color)
    }
}

@Composable
private fun SpaceTopBar(
    state: HomeState,
    actions: HomeActions,
    h: Dp,
    barHeight: Dp,
    modifier: Modifier,
) {
    val palette = currentIllustratedPalette(HomeTheme.GALAXY)
    val pad = h * .035f
    val pill = Modifier.fillMaxHeight().spacePanel(palette, barHeight / 2f)
    val metrics = CommandMetrics(buttonSize = barHeight, iconSize = barHeight * .58f, horizontalPadding = h * .026f)
    Box(modifier) {
        Box(Modifier.align(Alignment.CenterStart).then(pill).padding(horizontal = pad), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IllustratedText(state.time.value, (h.value * .056f).sp, palette.content, FontWeight.Black)
                Box(
                    Modifier
                        .padding(horizontal = pad * .7f)
                        .width(1.5.dp)
                        .height(barHeight * .45f)
                        .drawWithCache { onDrawBehind { drawRect(palette.divider) } },
                )
                IllustratedText(state.date.value, (h.value * .033f).sp, palette.content, FontWeight.Bold)
            }
        }
        Box(Modifier.align(Alignment.Center).then(pill).padding(horizontal = pad * .5f), contentAlignment = Alignment.Center) {
            HomeCommandGroup(state, actions, metrics, ReorderAxis.HORIZONTAL, dividers = true)
        }
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            TopBarPlanet(R.drawable.illustration_galaxy_planet, barHeight * 1.1f, TripGesture.SATURN)
            Spacer(Modifier.width(pad))
            Box(pill.padding(horizontal = pad), contentAlignment = Alignment.Center) {
                OutsideTemperatureWithIcon(state, (h.value * .05f).sp, barHeight * .5f)
            }
        }
    }
}

/** Galaxy: the six values over a lunar horizon. */
@Composable
private fun GalaxyFooter(state: HomeState, actions: HomeActions, h: Dp, corner: Dp, modifier: Modifier) {
    val palette = currentIllustratedPalette(HomeTheme.GALAXY)
    val filter = illustratedArtworkFilter()
    val lunar = ImageBitmap.imageResource(R.drawable.illustration_galaxy_lunar)
    val labelSize = (h.value * .03f).sp
    val valueSize = (h.value * .055f).sp
    val unitSize = (h.value * .034f).sp
    Box(
        modifier.spacePanel(palette, corner) {
            // The horizon repeats along the bar, mirrored every other time so it stays seamless.
            val tileWidth = size.height * .6f * lunar.width / lunar.height
            var x = 0f
            var index = 0
            while (x < size.width) {
                val rect = lunar.rectAboveBase(x + tileWidth / 2f, size.height * 1.05f, tileWidth)
                if (index % 2 == 0) {
                    drawArt(lunar, rect, alpha = .45f, filter = filter)
                } else {
                    scaleHorizontally(rect.center.x) { drawArt(lunar, rect, alpha = .45f, filter = filter) }
                }
                x += tileWidth
                index++
            }
        },
    ) {
        BlockStrip(
            state = state,
            actions = actions,
            pinned = SpaceSideTiles,
            axis = ReorderAxis.HORIZONTAL,
            labelSize = labelSize,
            valueSize = valueSize,
            unitSize = unitSize,
            modifier = Modifier.fillMaxSize().padding(horizontal = h * .03f, vertical = 6.dp),
            labelGap = 7.dp,
        ) { item ->
            val scope = blockStatisticsScope(item)
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (scope != null) Modifier.clickable { actions.onStatistics(scope) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val reading = rememberBlockReading(item, state)
                if (reading != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BlockLabel(blockLabel(item), labelSize, color = palette.unit)
                        Spacer(Modifier.height(7.dp))
                        ValueWithUnit(
                            reading.value,
                            reading.unit,
                            valueSize,
                            unitSize,
                            color = if (reading.warning) LocalHomeTokens.current.warning else null,
                        )
                    }
                }
            }
        }
    }
}

private inline fun DrawScope.scaleHorizontally(pivotX: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(-1f, 1f, Offset(pivotX, 0f))
    block()
    drawContext.transform.scale(-1f, 1f, Offset(pivotX, 0f))
}

/** Deep space with stars and a shooting star next to the clock. */
@Composable
private fun SpaceBackdrop(modifier: Modifier) {
    val palette = currentIllustratedPalette(HomeTheme.GALAXY)
    Canvas(
        modifier.drawWithCache {
            val sky = Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom))
            onDrawBehind {
                drawRect(sky)
                drawStarField(seed = 5, count = 80, dot = palette.content, sparkle = palette.label)
                drawShootingStar(Offset(size.width * .33f, size.height * .07f), size.height * .09f)
            }
        },
    ) { }
}

private fun DrawScope.drawShootingStar(head: Offset, length: Float) {
    val tail = Offset(head.x - length, head.y - length * .32f)
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFB45C)), start = tail, end = head),
        tail,
        head,
        strokeWidth = 2.dp.toPx(),
    )
    drawCircle(Color(0xFFFFE2B0), 2.2.dp.toPx(), head)
}
