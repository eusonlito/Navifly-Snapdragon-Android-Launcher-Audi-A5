package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.A5_FUEL_TANK_CAPACITY_LITRES
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.TopCommandItem
import com.lito.a5launcher.ui.components.FuelSegmentTone
import com.lito.a5launcher.ui.components.formatOneDecimal
import com.lito.a5launcher.ui.components.fuelSegmentTone
import com.lito.a5launcher.ui.components.fuelSegments

@Composable
internal fun BlueHomeLayout(state: HomeState, actions: HomeActions, slots: HomeSlots, cyan: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().blueBackdrop(cyan)) {
        val screenHeight = maxHeight
        val unit = screenHeight.coerceAtMost(maxWidth * (896f / 2400f))
        val railWidth = maxWidth * .053f
        val gap = unit * .009f
        val corner = unit * .024f
        val viewport = remember(corner) { MapViewportSpec(cornerRadius = corner) }
        Row(Modifier.fillMaxSize()) {
            BlueRail(state, actions, cyan, railWidth, screenHeight, unit)
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = gap)) {
                Row(
                    Modifier.fillMaxWidth().height(screenHeight * .076f)
                        .startupContentAlpha(state).blueLightEdge(cyan, vertical = false)
                        .padding(horizontal = unit * .015f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ClockText(state, (unit.value * .043f).sp)
                    OutsideTemperatureText(state, (unit.value * .042f).sp)
                }
                Spacer(Modifier.height(gap))
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    val instrument = Modifier.weight(if (cyan) .44f else .29f).fillMaxHeight()
                    if (cyan) {
                        CyanInstruments(state, unit, instrument)
                    } else {
                        LightInstruments(state, unit, corner, instrument)
                    }
                    Box(
                        Modifier.weight(if (cyan) .56f else .71f).fillMaxHeight(),
                    ) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(corner))) {
                            slots.map(Modifier.fillMaxSize(), viewport)
                            StartupMapCover(state)
                            slots.mapOverlay(this, PaddingValues())
                        }
                        Canvas(Modifier.fillMaxSize().blueMapRim(cyan, corner)) { }
                    }
                }
                Spacer(Modifier.height(gap))
                BlueFooter(
                    state, actions, unit, cyan,
                    Modifier.fillMaxWidth().height(screenHeight * .133f)
                        .startupContentAlpha(state).padding(bottom = gap),
                )
            }
        }
    }
}

@Composable
private fun BlueRail(
    state: HomeState, actions: HomeActions, cyan: Boolean, width: Dp, height: Dp, unit: Dp,
) {
    val count = TopCommandItem.entries.size - if (state.assistantEnabled.value) 0 else 1
    Box(
        Modifier.width(width).fillMaxHeight().blueRailSurface(cyan).blueLightEdge(cyan, vertical = true)
            .startupContentAlpha(state),
        contentAlignment = Alignment.Center,
    ) {
        HomeCommandGroup(
            state, actions,
            CommandMetrics(
                buttonSize = height / count,
                iconSize = minOf(width * .50f, unit * .066f),
                horizontalPadding = width * .12f,
            ),
            ReorderAxis.VERTICAL,
            dividers = false,
        )
    }
}

@Composable
private fun CyanInstruments(state: HomeState, unit: Dp, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BlueSpeedometer(state, cyan = true, Modifier.weight(.66f).fillMaxHeight())
        Column(
            Modifier.weight(.34f).fillMaxHeight().padding(end = unit * .005f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(.18f))
            Box(Modifier.weight(.24f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BlueRpm(state, cyan = true, unit, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.weight(.06f))
            Box(Modifier.weight(.39f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BlueGear(state, cyan = true, Modifier.fillMaxSize())
            }
            Spacer(Modifier.weight(.13f))
        }
    }
}

@Composable
private fun LightInstruments(state: HomeState, unit: Dp, corner: Dp, modifier: Modifier) {
    Column(
        modifier.bluePearlPanel(corner),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            BlueSpeedometer(state, cyan = false, Modifier.fillMaxSize())
            BlueGear(
                state, cyan = false,
                Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * .016f)
                    .size(width = minOf(maxWidth * .43f, maxHeight * .67f), height = maxHeight * .195f),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(unit * .131f).padding(horizontal = unit * .023f),
            contentAlignment = Alignment.Center,
        ) {
            BlueRpm(state, cyan = false, unit, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BlueRpm(state: HomeState, cyan: Boolean, unit: Dp, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    val reading = formatOneDecimal(state.rpm.value / 1000.0, LocalConfiguration.current.locales[0])
    val fraction = animatedGaugeFraction(state.rpm, RPM_SCALE_MAX, rpm = true)
    val labelSize = (unit.value * if (cyan) .030f else .027f).sp
    val valueSize = (unit.value * if (cyan) .088f else .060f).sp
    val label: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BlueText("RPM", labelSize, tokens.content, cyan, FontWeight.Bold)
            BlueText("×1000", labelSize * .70f, tokens.label, cyan, FontWeight.Medium)
        }
    }
    val value: @Composable () -> Unit = {
        BlueText(reading, valueSize, tokens.content, cyan, FontWeight.Black)
    }
    val gauge: @Composable (Modifier) -> Unit = { area ->
        HorizontalBarGauge(
            fraction, RpmGaugeScale, state.startupProgress,
            barHeight = unit * .027f,
            labelSize = (unit.value * .023f).sp,
            modifier = area.height(unit * .078f),
            segments = 14,
            redZoneFrom = RPM_RED_ZONE_FRACTION,
            showMinorTicks = false,
            slantedSegments = true,
        )
    }
    if (cyan) {
        Column(modifier.startupContentAlpha(state)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label()
                value()
            }
            Spacer(Modifier.height(unit * .022f))
            gauge(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier.startupContentAlpha(state),
            horizontalArrangement = Arrangement.spacedBy(unit * .022f),
            verticalAlignment = Alignment.Top,
        ) {
            label()
            gauge(Modifier.weight(1f))
            value()
        }
    }
}

@Composable
internal fun BlueText(text: String, size: TextUnit, color: Color, italic: Boolean, weight: FontWeight) {
    Text(
        text, color = color, fontSize = size, lineHeight = size, fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = LocalHomeTokens.current.numberFamily,
        maxLines = 1, style = TightTextStyle, modifier = Modifier.capHeightBounds(size),
    )
}

@Composable
private fun BlueFooter(state: HomeState, actions: HomeActions, unit: Dp, cyan: Boolean, modifier: Modifier) {
    val corner = unit * .017f
    val labelSize = (unit.value * .022f).sp
    val valueSize = (unit.value * .041f).sp
    val unitSize = valueSize * .64f
    val tokens = LocalHomeTokens.current
    BlockStrip(
        state, actions, pinned = emptySet(),
        axis = ReorderAxis.HORIZONTAL,
        labelSize = labelSize, valueSize = valueSize, unitSize = unitSize,
        modifier = if (cyan) modifier else modifier.bluePearlPanel(corner),
        plateShape = RoundedCornerShape(corner),
        dividers = !cyan,
        itemWeight = {
            when (it) {
                FooterBlockItem.FUEL -> 1.74f
                FooterBlockItem.WITNESSES -> 1.58f
                FooterBlockItem.ODOMETER -> 1.27f
                FooterBlockItem.CONSUMPTION -> 1.08f
                else -> 1f
            }
        },
    ) { item ->
        val scope = blockStatisticsScope(item)
        val tile = if (cyan) {
            Modifier.padding(horizontal = unit * .0027f).blueCyanCard(corner)
        } else Modifier
        Box(
            Modifier.fillMaxSize().then(tile)
                .then(if (scope != null) Modifier.clickable { actions.onStatistics(scope) } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                FooterBlockItem.WITNESSES -> WitnessGroup(
                    state, Modifier.fillMaxWidth(),
                    scale = unit.value / 448f * if (cyan) .78f else .84f,
                    arrangement = Arrangement.SpaceEvenly,
                )
                FooterBlockItem.FUEL -> BlueFuel(state, unit, labelSize, valueSize, unitSize)
                else -> {
                    val reading = rememberBlockReading(item, state)
                    if (reading != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(unit * .009f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (cyan) BlockIcon(item, tokens.content, Modifier.size(unit * .035f))
                            Column(verticalArrangement = Arrangement.spacedBy(unit * .011f)) {
                                BlockLabel(blockLabel(item), labelSize)
                                ValueWithUnit(
                                    reading.value, reading.unit, valueSize, unitSize,
                                    color = if (reading.warning) tokens.warning else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlueFuel(state: HomeState, unit: Dp, labelSize: TextUnit, valueSize: TextUnit, unitSize: TextUnit) {
    val fuel = state.fuel.value
    val active = fuelSegments(fuel)
    val tone = fuelSegmentTone(active)
    val tokens = LocalHomeTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = unit * .012f),
        horizontalArrangement = Arrangement.spacedBy(unit * .010f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuelPumpIcon(fuelContentColor(tone, tokens), Modifier.size(unit * .042f))
        Column(verticalArrangement = Arrangement.spacedBy(unit * .011f)) {
            BlockLabel(blockLabel(FooterBlockItem.FUEL), labelSize)
            Row(
                horizontalArrangement = Arrangement.spacedBy(unit * .012f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.weight(1f).height(unit * .015f)) {
                    val step = size.width / 10
                    val radius = CornerRadius(size.height * .18f)
                    repeat(10) { i ->
                        val color = if (i < active && tone == FuelSegmentTone.NORMAL) {
                            tokens.accent
                        } else fuelSegmentColor(i < active, tone, tokens)
                        drawRoundRect(
                            color, topLeft = Offset(i * step, 0f),
                            size = Size(step * .84f, size.height), cornerRadius = radius,
                        )
                    }
                }
                ValueWithUnit(
                    "$fuel/$A5_FUEL_TANK_CAPACITY_LITRES", "L",
                    valueSize * .90f, unitSize, color = fuelContentColor(tone, tokens),
                )
            }
        }
    }
}
