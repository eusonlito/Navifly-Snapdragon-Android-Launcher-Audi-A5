package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lito.a5launcher.A5_FUEL_TANK_CAPACITY_LITRES
import com.lito.a5launcher.ui.components.DialType
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.FuelSegmentTone
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.SPEED_DIAL_MAX_KMH
import com.lito.a5launcher.ui.components.formatDialValue
import com.lito.a5launcher.ui.components.fuelFraction
import com.lito.a5launcher.ui.components.fuelSegmentTone
import com.lito.a5launcher.ui.components.fuelSegments
import kotlin.math.roundToInt

private val TelemetrySpeedScale = GaugeScale(maxValue = SPEED_DIAL_MAX_KMH, labelStep = 60, minorPerLabel = 2)
/** Inner padding shared by every Telemetry tile. */
private val CellPadding = 14.dp

/** Space between a reading and the gauge under it. */
private val ReadingGap = 12.dp

private val CellShape = RoundedCornerShape(4.dp)
private val MapShape = RoundedCornerShape(6.dp)
private val WideTiles = setOf(FooterBlockItem.FUEL, FooterBlockItem.WITNESSES)
private const val GRID_COLUMNS = 3

/** One segment per 250 rpm, so the red zone starts exactly at 4500 rpm. */
private const val SHIFT_LIGHT_SEGMENTS = 24
private const val FUEL_SEGMENTS = 14

/**
 * Data-dense layout: command rail, framed map, instruments, a 3 × 2 grid of
 * values and two wide tiles for fuel and witnesses.
 */
@Composable
internal fun TelemetryHomeLayout(
    state: HomeState,
    actions: HomeActions,
    slots: HomeSlots,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHomeTokens.current
    BoxWithConstraints(modifier.fillMaxSize().background(tokens.background)) {
        val h = maxHeight
        val w = maxWidth
        val railWidth = w * .05f
        val topHeight = h * .088f
        val gap = h * .016f
        val panelWidth = w * .425f
        val viewport = remember { MapViewportSpec(cornerRadius = 6.dp) }
        Row(Modifier.fillMaxSize()) {
            TelemetryCommandRail(
                state,
                actions,
                h,
                Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .startupContentAlpha(state)
                    .zIndex(3f),
            )
            Box(Modifier.padding(vertical = gap).width(1.dp).fillMaxHeight().background(tokens.divider))
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = gap, end = gap)) {
                TelemetryTopBar(
                    state,
                    h,
                    Modifier
                        .fillMaxWidth()
                        .height(topHeight)
                        .startupContentAlpha(state),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.divider))
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = gap)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(MapShape)
                                .border(1.dp, tokens.surfaceBorder, MapShape),
                        ) {
                            slots.map(Modifier.fillMaxSize(), viewport)
                            StartupMapCover(state)
                        }
                        Column(
                            Modifier.width(panelWidth).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            TelemetryInstruments(state, h, gap, Modifier.fillMaxWidth().weight(1.25f))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(2.45f)
                                    .startupContentAlpha(state),
                                verticalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                TelemetryValueGrid(state, actions, h, gap, Modifier.fillMaxWidth().weight(1.4f))
                                TelemetryWideTiles(state, actions, h, gap, Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                    }
                    // Statistics cover the whole content area so the panel keeps its width.
                    Box(Modifier.fillMaxSize().clip(MapShape).zIndex(5f)) {
                        slots.mapOverlay(this)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCommandRail(state: HomeState, actions: HomeActions, h: Dp, modifier: Modifier) {
    Box(modifier.padding(vertical = h * .05f), contentAlignment = Alignment.Center) {
        HomeCommandGroup(
            state,
            actions,
            CommandMetrics(buttonSize = h * .11f, iconSize = h * .068f, horizontalPadding = 4.dp),
            ReorderAxis.VERTICAL,
            dividers = false,
            spacing = h * .02f,
        )
    }
}

@Composable
private fun TelemetryTopBar(state: HomeState, h: Dp, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    Row(modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.time.value,
            color = tokens.content,
            fontSize = (h.value * .05f).sp,
            fontWeight = tokens.numberWeight,
            fontFamily = tokens.numberFamily,
            maxLines = 1,
        )
        Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(h * .05f).background(tokens.divider))
        Text(
            state.date.value,
            color = tokens.content,
            fontSize = (h.value * .042f).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = tokens.labelFamily,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatOutsideTemperature(state.outside.value, LocalConfiguration.current.locales[0]),
            color = tokens.content,
            fontSize = (h.value * .045f).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = tokens.labelFamily,
            maxLines = 1,
        )
    }
}

@Composable
private fun TelemetryInstruments(state: HomeState, h: Dp, gap: Dp, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    val valueSize = (h.value * .115f).sp
    Row(modifier.startupOutlineAlpha(state.startupProgress), horizontalArrangement = Arrangement.spacedBy(gap)) {
        TelemetryCell(Modifier.weight(.43f).fillMaxHeight()) {
            // Reading and gauge form one group centred in the tile.
            Column(Modifier.fillMaxSize().padding(CellPadding), verticalArrangement = Arrangement.Center) {
                TelemetryReading({ formatDialValue(state.speed.value, DialType.SPEED) }, "km/h", valueSize)
                Spacer(Modifier.height(ReadingGap))
                TelemetrySpeedBar(state, Modifier.fillMaxWidth().height(h * .07f))
            }
        }
        TelemetryCell(Modifier.weight(.57f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxSize().padding(CellPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    TelemetryReading({ formatDialValue(state.rpm.value, DialType.RPM) }, "x1000", valueSize)
                    Spacer(Modifier.height(ReadingGap))
                    TelemetryShiftLights(state, Modifier.fillMaxWidth().height(h * .07f))
                }
                Spacer(Modifier.width(CellPadding))
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(h * .18f)
                        .border(3.dp, tokens.accent, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.gear.value,
                        color = tokens.content,
                        fontSize = (h.value * .15f).sp,
                        lineHeight = (h.value * .15f).sp,
                        fontWeight = tokens.numberWeight,
                        fontFamily = tokens.numberFamily,
                        maxLines = 1,
                        style = TightTextStyle,
                        modifier = Modifier.capHeightBounds((h.value * .15f).sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryReading(value: () -> String, unit: String, size: TextUnit) {
    ValueWithUnit(value(), unit, size, size * .38f)
}

@Composable
private fun TelemetrySpeedBar(state: HomeState, modifier: Modifier) {
    val fraction = animatedGaugeFraction(state.speed, SPEED_DIAL_MAX_KMH, rpm = false)
    HorizontalBarGauge(
        fraction = fraction,
        scale = TelemetrySpeedScale,
        startupProgress = state.startupProgress,
        barHeight = 5.dp,
        labelSize = 12.sp,
        modifier = modifier,
        marker = true,
    )
}

@Composable
private fun TelemetryShiftLights(state: HomeState, modifier: Modifier) {
    val fraction = animatedGaugeFraction(state.rpm, RPM_SCALE_MAX, rpm = true)
    HorizontalBarGauge(
        fraction = fraction,
        scale = RpmGaugeScale,
        startupProgress = state.startupProgress,
        barHeight = 7.dp,
        labelSize = 12.sp,
        modifier = modifier,
        segments = SHIFT_LIGHT_SEGMENTS,
        segmentColor = ::shiftLightColor,
        showMinorTicks = false,
        marker = true,
    )
}

@Composable
private fun TelemetryCell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val tokens = LocalHomeTokens.current
    Box(
        modifier
            .clip(CellShape)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder, CellShape),
        content = content,
    )
}

/** The six value blocks, reorderable by dragging in both directions. */
@Composable
private fun TelemetryValueGrid(state: HomeState, actions: HomeActions, h: Dp, gap: Dp, modifier: Modifier) {
    val order = state.footerBlockOrder.value
    val visibleItems = remember(order) { order.filterNot(WideTiles::contains) }
    val visibleSet = remember(visibleItems) { visibleItems.toSet() }
    val cells = remember { mutableMapOf<Int, CellBounds>() }
    val labelSize = (h.value * .03f).sp
    val valueSize = (h.value * .064f).sp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        visibleItems.chunked(GRID_COLUMNS).forEachIndexed { row, items ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                items.forEachIndexed { column, item ->
                    key(item) {
                        GridReorderSlot(
                            item = item,
                            index = row * GRID_COLUMNS + column,
                            order = order,
                            visibleItems = visibleSet,
                            slots = cells,
                            plateShape = CellShape,
                            onMove = actions.onFooterBlockOrderChanged,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            TelemetryCell(Modifier.fillMaxSize()) {
                                TelemetryValueTile(item, state, actions, labelSize, valueSize)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryValueTile(
    item: FooterBlockItem,
    state: HomeState,
    actions: HomeActions,
    labelSize: TextUnit,
    valueSize: TextUnit,
) {
    val tokens = LocalHomeTokens.current
    val scope = blockStatisticsScope(item)
    Column(
        Modifier
            .fillMaxSize()
            .then(if (scope != null) Modifier.clickable { actions.onStatistics(scope) } else Modifier)
            .padding(CellPadding),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Label against the top padding, value against the bottom padding, both centred.
        BlockLabel(blockLabel(item), labelSize, color = tokens.content)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val reading = rememberBlockReading(item, state)
            if (reading != null) {
                // Long values such as the odometer shrink to keep their unit in the tile.
                val fit = (6f / reading.value.length).coerceAtMost(1f)
                ValueWithUnit(
                    reading.value,
                    reading.unit,
                    valueSize * fit,
                    valueSize * .45f,
                    color = if (reading.warning) tokens.warning else null,
                )
            }
        }
    }
}

/** Fuel and witnesses as two wide tiles; long-press drag swaps them. */
@Composable
private fun TelemetryWideTiles(state: HomeState, actions: HomeActions, h: Dp, gap: Dp, modifier: Modifier) {
    val order = state.footerBlockOrder.value
    val tiles = remember(order) { order.filter(WideTiles::contains) }
    val sizes = remember { mutableStateMapOf<FooterBlockItem, Int>() }
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        tiles.forEach { item ->
            key(item) {
                LinearReorderSlot(
                    item = item,
                    order = order,
                    visibleItems = WideTiles,
                    axis = ReorderAxis.HORIZONTAL,
                    sizes = sizes,
                    gapPx = gapPx,
                    plateShape = CellShape,
                    onMove = actions.onFooterBlockOrderChanged,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    TelemetryCell(Modifier.fillMaxSize()) {
                        when (item) {
                            FooterBlockItem.FUEL -> TelemetryFuel(state, h)
                            else -> TelemetryWitnesses(state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryFuel(state: HomeState, h: Dp) {
    val tokens = LocalHomeTokens.current
    val fuel = state.fuel.value
    val tone = fuelSegmentTone(fuelSegments(fuel))
    val lit = (fuelFraction(fuel) * FUEL_SEGMENTS).roundToInt().coerceIn(if (fuel > 0) 1 else 0, FUEL_SEGMENTS)
    val contentColor = fuelContentColor(tone, tokens)
    // Pump, litres and level bar form one group centred in the tile.
    Column(Modifier.fillMaxSize().padding(CellPadding), verticalArrangement = Arrangement.Center) {
        Row(Modifier.height(h * .085f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FuelPumpIcon(
                color = if (tone == FuelSegmentTone.NORMAL) tokens.content else contentColor,
                modifier = Modifier.size(h * .085f),
            )
            Box(Modifier.padding(horizontal = CellPadding).width(1.dp).fillMaxHeight().background(tokens.divider))
            Column(Modifier.weight(1f)) {
                // Litres and tank capacity share one baseline.
                Row(verticalAlignment = Alignment.Bottom) {
                    ValueWithUnit(fuel.toString(), "L", (h.value * .068f).sp, (h.value * .068f).sp, color = contentColor)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$A5_FUEL_TANK_CAPACITY_LITRES L",
                        color = tokens.content,
                        fontSize = (h.value * .034f).sp,
                        fontFamily = tokens.labelFamily,
                        maxLines = 1,
                        style = TightTextStyle,
                        modifier = Modifier.capHeightBounds((h.value * .034f).sp),
                    )
                }
            }
        }
        Spacer(Modifier.height(ReadingGap))
        Canvas(Modifier.fillMaxWidth().height(h * .036f)) {
            val gapPx = 3.dp.toPx()
            val segmentWidth = (size.width - gapPx * (FUEL_SEGMENTS - 1)) / FUEL_SEGMENTS
            for (index in 0 until FUEL_SEGMENTS) {
                drawRect(
                    fuelSegmentColor(index < lit, tone, tokens),
                    topLeft = androidx.compose.ui.geometry.Offset(index * (segmentWidth + gapPx), 0f),
                    size = androidx.compose.ui.geometry.Size(segmentWidth, size.height),
                )
            }
        }
    }
}

/** Same witness set and proportions as the other themes. */
@Composable
private fun TelemetryWitnesses(state: HomeState) {
    Box(Modifier.fillMaxSize().padding(CellPadding), contentAlignment = Alignment.Center) {
        WitnessGroup(
            state,
            scale = 1.1f,
            modifier = Modifier.fillMaxWidth(),
            arrangement = Arrangement.SpaceBetween,
        )
    }
}
