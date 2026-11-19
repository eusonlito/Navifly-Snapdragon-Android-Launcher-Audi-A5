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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lito.a5launcher.A5_FUEL_TANK_CAPACITY_LITRES
import com.lito.a5launcher.R
import com.lito.a5launcher.ui.components.DialType
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.SPEED_DIAL_MAX_KMH
import com.lito.a5launcher.ui.components.formatDialValue
import com.lito.a5launcher.ui.components.fuelFraction
import com.lito.a5launcher.ui.components.fuelSegmentTone
import com.lito.a5launcher.ui.components.fuelSegments
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val AdventureSpeedScale = GaugeScale(maxValue = SPEED_DIAL_MAX_KMH, labelStep = 60, minorPerLabel = 2)
private val AdventureWideTiles = setOf(FooterBlockItem.FUEL, FooterBlockItem.WITNESSES)
private const val ADVENTURE_GRID_COLUMNS = 3
private const val ADVENTURE_SEGMENTS = 24
private const val ADVENTURE_FUEL_SEGMENTS = 14

/** Top and bottom colours of a tile; each value keeps its colour wherever it is moved. */
private val TileColors = mapOf(
    FooterBlockItem.CONSUMPTION to (Color(0xFF52CC3C) to Color(0xFF2E9A28)),
    FooterBlockItem.RANGE to (Color(0xFFFFA63E) to Color(0xFFEE7A12)),
    FooterBlockItem.TIME to (Color(0xFFF46FB8) to Color(0xFFD93F8F)),
    FooterBlockItem.TRIP to (Color(0xFF38B0F6) to Color(0xFF1B7FDD)),
    FooterBlockItem.REFUEL_DISTANCE to (Color(0xFF9D6EF3) to Color(0xFF7244D8)),
    FooterBlockItem.ODOMETER to (Color(0xFF2DBDF2) to Color(0xFF1787D6)),
    FooterBlockItem.FUEL to (Color(0xFF2F7DEB) to Color(0xFF1653C6)),
    FooterBlockItem.WITNESSES to (Color(0xFF2050B6) to Color(0xFF173C92)),
)
/** A value in warning (low range, for instance) turns its whole tile red and keeps white figures. */
private val WarningTileColors = Color(0xFFF0353F) to Color(0xFFB8122B)
private val SpeedTileColors = Color(0xFF3DA9F8) to Color(0xFF1B6BDD)
private val RpmTileColors = Color(0xFF8C5EF1) to Color(0xFF6033D3)
private val GearTileColors = Color(0xFFFFE066) to Color(0xFFF6B100)
private val TileOutline = Color(0xFF0A1D57)

private val AdventureIcons = mapOf(
    FooterBlockItem.CONSUMPTION to R.drawable.illustration_adv_icon_consumption,
    FooterBlockItem.RANGE to R.drawable.illustration_adv_icon_range,
    FooterBlockItem.TIME to R.drawable.illustration_adv_icon_time,
    FooterBlockItem.TRIP to R.drawable.illustration_adv_icon_trip,
    FooterBlockItem.REFUEL_DISTANCE to R.drawable.illustration_adv_icon_refuel,
    FooterBlockItem.ODOMETER to R.drawable.illustration_adv_icon_odometer,
)

/**
 * Cartoon road-trip home: command rail, a large map with nothing drawn over
 * it and, on the right, colourful tiles for every value.
 */
@Composable
internal fun AdventureHomeLayout(
    state: HomeState,
    actions: HomeActions,
    slots: HomeSlots,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        val railWidth = w * .055f
        val topHeight = h * .1f
        val gap = h * .018f
        val panelWidth = w * .42f
        val corner = h * .04f
        val viewport = remember(corner) { MapViewportSpec(cornerRadius = corner) }

        AdventureBackdrop(Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress))

        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .padding(top = topHeight, bottom = h * .04f)
                    .startupContentAlpha(state)
                    .zIndex(3f),
                contentAlignment = Alignment.Center,
            ) {
                HomeCommandGroup(
                    state,
                    actions,
                    CommandMetrics(buttonSize = h * .11f, iconSize = h * .068f, horizontalPadding = 4.dp),
                    ReorderAxis.VERTICAL,
                    dividers = false,
                    spacing = h * .025f,
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(end = gap)) {
                AdventureTopBar(state, h, Modifier.fillMaxWidth().height(topHeight).startupContentAlpha(state))
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = gap * 1.5f),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(corner)),
                        ) {
                            slots.map(Modifier.fillMaxSize(), viewport)
                            StartupMapCover(state)
                            slots.mapOverlay(this, PaddingValues())
                        }
                        Canvas(Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress)) {
                            drawRoundRect(
                                Color.White,
                                cornerRadius = CornerRadius(corner.toPx()),
                                style = Stroke(3.dp.toPx()),
                            )
                        }
                    }
                    Column(
                        Modifier.width(panelWidth).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        AdventureInstruments(state, h, gap, Modifier.fillMaxWidth().weight(1.45f))
                        AdventureValueGrid(
                            state,
                            actions,
                            h,
                            gap,
                            Modifier.fillMaxWidth().weight(2.1f).startupContentAlpha(state),
                        )
                        AdventureWideTilesRow(
                            state,
                            actions,
                            h,
                            gap,
                            Modifier.fillMaxWidth().weight(1f).startupContentAlpha(state),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdventureTopBar(state: HomeState, h: Dp, modifier: Modifier) {
    val rocket = ImageBitmap.imageResource(R.drawable.illustration_adv_rocket)
    Box(modifier) {
        Row(Modifier.align(Alignment.CenterStart).padding(start = h * .01f), verticalAlignment = Alignment.CenterVertically) {
            IllustratedText(state.time.value, (h.value * .062f).sp, Color.White, FontWeight.Black)
            Box(
                Modifier
                    .padding(horizontal = h * .03f)
                    .width(2.dp)
                    .height(h * .05f)
                    .drawWithCache { onDrawBehind { drawRect(Color.White.copy(alpha = .6f)) } },
            )
            IllustratedText(state.date.value, (h.value * .04f).sp, Color.White, FontWeight.Bold)
        }
        Image(
            rocket,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = h * .25f)
                .size(h * .085f * rocket.width / rocket.height, h * .085f),
        )
        Box(Modifier.align(Alignment.CenterEnd).padding(end = h * .01f)) {
            OutsideTemperatureText(state, (h.value * .05f).sp)
        }
    }
}

/** Glossy cartoon tile: darker lip under a vertical gradient and a light top edge. */
private fun Modifier.adventureTile(colors: Pair<Color, Color>, corner: Dp = 14.dp): Modifier = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val lip = 4.dp.toPx()
    val body = Size(size.width, size.height - lip)
    val fill = Brush.verticalGradient(listOf(colors.first, colors.second), endY = body.height)
    val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, body), radius)) }
    onDrawBehind {
        drawRoundRect(colors.second.copy(red = colors.second.red * .7f, green = colors.second.green * .7f, blue = colors.second.blue * .7f), Offset(0f, lip), body, radius)
        drawPath(outline, fill)
        drawPath(outline, Color.White.copy(alpha = .35f), style = Stroke(1.5.dp.toPx()))
        drawPath(outline, TileOutline.copy(alpha = .35f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun AdventureInstruments(state: HomeState, h: Dp, gap: Dp, modifier: Modifier) {
    val valueSize = (h.value * .085f).sp
    val gaugeHeight = h * .085f
    val speed = animatedGaugeFraction(state.speed, SPEED_DIAL_MAX_KMH, rpm = false)
    val rpm = animatedGaugeFraction(state.rpm, RPM_SCALE_MAX, rpm = true)
    Row(modifier.startupOutlineAlpha(state.startupProgress), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Box(Modifier.weight(1f).fillMaxHeight().adventureTile(SpeedTileColors)) {
            Column(Modifier.fillMaxSize().padding(horizontal = h * .03f).padding(bottom = 4.dp), verticalArrangement = Arrangement.Center) {
                ValueWithUnit(formatDialValue(state.speed.value, DialType.SPEED), "km/h", valueSize, valueSize * .42f)
                Spacer(Modifier.height(h * .025f))
                HorizontalBarGauge(
                    fraction = speed,
                    scale = AdventureSpeedScale,
                    startupProgress = state.startupProgress,
                    barHeight = h * .036f,
                    labelSize = (h.value * .028f).sp,
                    modifier = Modifier.fillMaxWidth().height(gaugeHeight),
                    segments = ADVENTURE_SEGMENTS,
                    marker = true,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().adventureTile(RpmTileColors)) {
            Column(Modifier.fillMaxSize().padding(horizontal = h * .03f).padding(bottom = 4.dp), verticalArrangement = Arrangement.Center) {
                ValueWithUnit(state.rpm.value.toString(), "rpm", valueSize, valueSize * .42f)
                Spacer(Modifier.height(h * .025f))
                HorizontalBarGauge(
                    fraction = rpm,
                    scale = RpmGaugeScale,
                    startupProgress = state.startupProgress,
                    barHeight = h * .036f,
                    labelSize = (h.value * .028f).sp,
                    modifier = Modifier.fillMaxWidth().height(gaugeHeight),
                    segments = ADVENTURE_SEGMENTS,
                    segmentColor = ::shiftLightColor,
                    showMinorTicks = false,
                    marker = true,
                )
            }
        }
        Box(
            Modifier.width(h * .21f).fillMaxHeight().adventureTile(GearTileColors).drawWithCache {
                val star = starPath(Offset(size.width / 2f, (size.height - 4.dp.toPx()) / 2f), size.width * .49f)
                onDrawBehind {
                    drawPath(star, Color(0xFFFFEB8A))
                    drawPath(star, Color(0xFFE8A200), style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
                    drawStar(Offset(size.width * .17f, size.height * .15f), size.width * .07f, Color(0xFFFFF3B0))
                    drawStar(Offset(size.width * .85f, size.height * .14f), size.width * .06f, Color(0xFFFFF3B0))
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            IllustratedText(state.gear.value, (h.value * .112f).sp, Color(0xFF15204A), FontWeight.Black, Modifier.offset(y = -2.dp))
        }
    }
}

/** Space between a value tile's label and its reading, as in Telemetry. */
private val ValueGap = 12.dp

/** Five-point star with rounded joins, for the gear tile. */
private fun starPath(center: Offset, radius: Float) = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * .55f
        val a = (-90f + i * 36f) * PI.toFloat() / 180f
        val x = center.x + cos(a) * r
        val y = center.y + sin(a) * r + radius * .08f
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The six values, reorderable by dragging in both directions. */
@Composable
private fun AdventureValueGrid(state: HomeState, actions: HomeActions, h: Dp, gap: Dp, modifier: Modifier) {
    val order = state.footerBlockOrder.value
    val visibleItems = remember(order) { order.filterNot(AdventureWideTiles::contains) }
    val visibleSet = remember(visibleItems) { visibleItems.toSet() }
    val cells = remember { mutableMapOf<Int, CellBounds>() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        visibleItems.chunked(ADVENTURE_GRID_COLUMNS).forEachIndexed { row, items ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                items.forEachIndexed { column, item ->
                    key(item) {
                        GridReorderSlot(
                            item = item,
                            index = row * ADVENTURE_GRID_COLUMNS + column,
                            order = order,
                            visibleItems = visibleSet,
                            slots = cells,
                            plateShape = RoundedCornerShape(14.dp),
                            onMove = actions.onFooterBlockOrderChanged,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            val warning = rememberBlockReading(item, state)?.warning == true
                            val colors = if (warning) WarningTileColors else TileColors.getValue(item)
                            Box(Modifier.fillMaxSize().adventureTile(colors).padding(bottom = 4.dp)) {
                                IconValueTile(
                                    item,
                                    state,
                                    actions,
                                    AdventureIcons.getValue(item),
                                    labelSize = (h.value * .03f).sp,
                                    valueSize = (h.value * .062f).sp,
                                    padding = h * .028f,
                                    labelColor = Color.White,
                                    contentColor = Color.White,
                                    iconScale = 2.2f,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Fuel and witnesses as two wide tiles; long-press drag swaps them. */
@Composable
private fun AdventureWideTilesRow(state: HomeState, actions: HomeActions, h: Dp, gap: Dp, modifier: Modifier) {
    val order = state.footerBlockOrder.value
    val tiles = remember(order) { order.filter(AdventureWideTiles::contains) }
    val sizes = remember { mutableStateMapOf<FooterBlockItem, Int>() }
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        tiles.forEach { item ->
            key(item) {
                LinearReorderSlot(
                    item = item,
                    order = order,
                    visibleItems = AdventureWideTiles,
                    axis = ReorderAxis.HORIZONTAL,
                    sizes = sizes,
                    gapPx = gapPx,
                    plateShape = RoundedCornerShape(14.dp),
                    onMove = actions.onFooterBlockOrderChanged,
                    modifier = Modifier.weight(if (item == FooterBlockItem.FUEL) 1.1f else 1f).fillMaxHeight(),
                ) {
                    Box(
                        Modifier.fillMaxSize().adventureTile(TileColors.getValue(item)).padding(bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (item) {
                            FooterBlockItem.FUEL -> AdventureFuel(state, h)
                            else -> WitnessGroup(
                                state,
                                scale = 1f,
                                arrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdventureFuel(state: HomeState, h: Dp) {
    val tokens = LocalHomeTokens.current
    val fuel = state.fuel.value
    val tone = fuelSegmentTone(fuelSegments(fuel))
    val color = fuelContentColor(tone, tokens)
    val lit = (fuelFraction(fuel) * ADVENTURE_FUEL_SEGMENTS).roundToInt()
        .coerceIn(if (fuel > 0) 1 else 0, ADVENTURE_FUEL_SEGMENTS)
    Column(Modifier.fillMaxSize().padding(horizontal = h * .03f), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FuelPumpIcon(color, Modifier.size(h * .055f))
            Spacer(Modifier.width(h * .025f))
            ValueWithUnit(fuel.toString(), "L", (h.value * .062f).sp, (h.value * .032f).sp, color = color)
            Spacer(Modifier.weight(1f))
            IllustratedText("$A5_FUEL_TANK_CAPACITY_LITRES L", (h.value * .03f).sp, Color.White.copy(alpha = .85f), FontWeight.Bold)
        }
        Spacer(Modifier.height(h * .022f))
        Canvas(Modifier.fillMaxWidth().height(h * .026f)) {
            val gapPx = 2.dp.toPx()
            val segmentWidth = (size.width - gapPx * (ADVENTURE_FUEL_SEGMENTS - 1)) / ADVENTURE_FUEL_SEGMENTS
            repeat(ADVENTURE_FUEL_SEGMENTS) { index ->
                drawRoundRect(
                    fuelSegmentColor(index < lit, tone, tokens),
                    Offset(index * (segmentWidth + gapPx), 0f),
                    Size(segmentWidth, size.height),
                    CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/** Night-blue sky with stars, clouds and bushes in the lower corners. */
@Composable
private fun AdventureBackdrop(modifier: Modifier) {
    val palette = AdventurePalette
    val cloud = ImageBitmap.imageResource(R.drawable.illustration_adv_cloud)
    val bush = ImageBitmap.imageResource(R.drawable.illustration_adv_bush)
    Canvas(
        modifier.drawWithCache {
            val sky = Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom))
            onDrawBehind {
                drawRect(sky)
                drawStarField(seed = 21, count = 45, dot = Color.White, sparkle = Color.White, star = Color(0xFFFFD43B))
                val unit = size.height
                drawArt(cloud, cloud.rectCentered(size.width * .47f, unit * .07f, unit * .15f), alpha = .85f)
                drawArt(cloud, cloud.rectCentered(size.width * .79f, unit * .065f, unit * .18f), alpha = .85f)
                drawArt(bush, bush.rectAboveBase(unit * .05f, unit * 1.01f, unit * .2f))
                drawArt(bush, bush.rectAboveBase(size.width - unit * .04f, unit * 1.01f, unit * .2f))
            }
        },
    ) { }
}

/**
 * Icon beside the label and, under both, the value across the whole tile
 * width, so the icon never takes room from the figures. Long values shrink.
 */
@Composable
private fun IconValueTile(
    item: FooterBlockItem,
    state: HomeState,
    actions: HomeActions,
    icon: Int,
    labelSize: TextUnit,
    valueSize: TextUnit,
    padding: Dp,
    labelColor: Color? = null,
    contentColor: Color? = null,
    iconScale: Float = 2.4f,
) {
    val tokens = LocalHomeTokens.current
    val scope = blockStatisticsScope(item)
    val iconImage = ImageBitmap.imageResource(icon)
    val labelHeight = with(LocalDensity.current) { labelSize.toDp() * CAP_HEIGHT_RATIO }
    val iconHeight = labelHeight * iconScale
    // Built like the Telemetry tiles: label and value centred, the icon hanging
    // beside the label without moving it, and the same gap between both.
    Column(
        Modifier
            .fillMaxSize()
            .then(if (scope != null) Modifier.clickable { actions.onStatistics(scope) } else Modifier)
            .padding(horizontal = padding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LabelWithHangingIcon(
            icon = {
                Image(
                    iconImage,
                    contentDescription = null,
                    modifier = Modifier.size(iconHeight * iconImage.width / iconImage.height, iconHeight),
                )
            },
            label = { BlockLabel(blockLabel(item), labelSize, color = labelColor) },
            gap = labelHeight * .8f,
        )
        Spacer(Modifier.height(ValueGap))
        // Every value keeps the full-size height, so a shrunk odometer stays aligned.
        val valueHeight = with(LocalDensity.current) { valueSize.toDp() * CAP_HEIGHT_RATIO }
        Box(Modifier.fillMaxWidth().height(valueHeight), contentAlignment = Alignment.BottomCenter) {
            val reading = rememberBlockReading(item, state)
            if (reading != null) {
                val fit = (7f / reading.value.length).coerceAtMost(1f)
                ValueWithUnit(
                    reading.value,
                    reading.unit,
                    valueSize * fit,
                    valueSize * .5f,
                    // The warning is shown by the tile colour, so the figures stay white.
                    color = contentColor,
                )
            }
        }
    }
}
