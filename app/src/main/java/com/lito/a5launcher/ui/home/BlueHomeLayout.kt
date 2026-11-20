package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.A5_FUEL_TANK_CAPACITY_LITRES
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.FuelSegmentTone
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.formatOneDecimal
import com.lito.a5launcher.ui.components.fuelSegmentTone
import com.lito.a5launcher.ui.components.fuelSegments

@Composable
internal fun BlueHomeLayout(state: HomeState, actions: HomeActions, slots: HomeSlots, dark: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().blueBackdrop(dark)) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        val unit = screenHeight.coerceAtMost(screenWidth * (896f / 2400f))
        val railWidth = screenWidth * .053f
        val gap = unit * .017f
        val corner = unit * .024f
        val viewport = remember(corner) { MapViewportSpec(cornerRadius = corner) }
        Row(Modifier.fillMaxSize()) {
            HomeVerticalCommandRail(
                state,
                actions,
                screenWidth,
                Modifier.width(railWidth).fillMaxHeight().blueRailSurface(dark).blueLightEdge(dark, vertical = true)
                    .startupContentAlpha(state),
            )
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = gap, end = gap, bottom = gap)) {
                Row(
                    Modifier.fillMaxWidth().height(screenHeight * .076f)
                        .startupContentAlpha(state).blueLightEdge(dark, vertical = false)
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
                    BlueInstruments(state, unit, corner, dark, Modifier.weight(.29f).fillMaxHeight())
                    Box(Modifier.weight(.71f).fillMaxHeight()) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(corner))) {
                            slots.map(Modifier.fillMaxSize(), viewport)
                            StartupMapCover(state)
                            slots.mapOverlay(this, PaddingValues())
                        }
                        Canvas(Modifier.fillMaxSize().blueMapRim(corner, dark)) { }
                    }
                }
                Spacer(Modifier.height(gap))
                BlueFooter(
                    state, actions, unit, dark,
                    Modifier.fillMaxWidth().height(screenHeight * .124f).startupContentAlpha(state),
                )
            }
        }
    }
}

@Composable
private fun BlueInstruments(state: HomeState, unit: Dp, corner: Dp, dark: Boolean, modifier: Modifier) {
    Column(
        modifier.bluePearlPanel(corner, dark).padding(top = unit * .017f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            BlueSpeedometer(state, dark, Modifier.fillMaxSize())
            BlueGear(
                state, dark,
                Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * .016f)
                    .size(width = minOf(maxWidth * .37f, maxHeight * .60f), height = maxHeight * .224f),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(unit * .131f).padding(horizontal = unit * .023f),
            contentAlignment = Alignment.Center,
        ) {
            BlueRpm(state, unit, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BlueRpm(state: HomeState, unit: Dp, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    val reading = formatOneDecimal(state.rpm.value / 1000.0, LocalConfiguration.current.locales[0])
    val fraction = animatedGaugeFraction(state.rpm, RPM_SCALE_MAX, rpm = true)
    val labelSize = (unit.value * .027f).sp
    val valueSize = (unit.value * .060f).sp
    val barHeight = unit * .027f
    val labelGap = unit * .004f
    val lineHeight = with(LocalDensity.current) {
        maxOf(valueSize.toDp() * CAP_HEIGHT_RATIO, labelSize.toDp() * (1.7f * CAP_HEIGHT_RATIO) + labelGap)
    }
    Row(
        modifier.startupContentAlpha(state),
        horizontalArrangement = Arrangement.spacedBy(unit * .022f),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.height(lineHeight),
            verticalArrangement = Arrangement.spacedBy(labelGap, Alignment.CenterVertically),
        ) {
            BlueText("RPM", labelSize, tokens.content, FontWeight.Bold)
            BlueText("×1000", labelSize * .70f, tokens.label, FontWeight.Medium)
        }
        // Only the bar shares the readings' centre line. Its scale hangs below it.
        HorizontalBarGauge(
            fraction, RpmGaugeScale, state.startupProgress,
            barHeight = barHeight,
            labelSize = (unit.value * .023f).sp,
            modifier = Modifier.weight(1f).padding(top = (lineHeight - barHeight) / 2f).height(unit * .078f),
            segments = 14,
            redZoneFrom = RPM_RED_ZONE_FRACTION,
            showMinorTicks = false,
            slantedSegments = true,
        )
        Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
            BlueText(reading, valueSize, tokens.content, FontWeight.Black)
        }
    }
}

@Composable
internal fun BlueText(text: String, size: TextUnit, color: Color, weight: FontWeight) {
    Text(
        text, color = color, fontSize = size, lineHeight = size, fontWeight = weight,
        fontFamily = LocalHomeTokens.current.numberFamily,
        maxLines = 1, style = TightTextStyle, modifier = Modifier.capHeightBounds(size),
    )
}

@Composable
private fun BlueFooter(state: HomeState, actions: HomeActions, unit: Dp, dark: Boolean, modifier: Modifier) {
    val corner = unit * .017f
    val labelSize = (unit.value * .022f).sp
    val valueSize = (unit.value * .041f).sp
    val unitSize = valueSize * .64f
    val labelGap = unit * .017f
    BlockStrip(
        state, actions, pinned = emptySet(),
        axis = ReorderAxis.HORIZONTAL,
        labelSize = labelSize, valueSize = valueSize, unitSize = unitSize,
        modifier = modifier.bluePearlPanel(corner, dark),
        plateShape = RoundedCornerShape(corner),
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
        when (item) {
            FooterBlockItem.WITNESSES -> WitnessGroup(
                state, Modifier.fillMaxSize().padding(horizontal = unit * .008f),
                scale = unit.value / 448f * .84f,
                arrangement = Arrangement.SpaceEvenly,
            )
            FooterBlockItem.FUEL -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BlueFuel(state, unit, labelSize, valueSize, unitSize, labelGap)
            }
            else -> DefaultBlockContent(
                item, state, actions, labelSize, valueSize, unitSize, labelGap = labelGap,
            )
        }
    }
}

@Composable
private fun BlueFuel(
    state: HomeState, unit: Dp, labelSize: TextUnit, valueSize: TextUnit, unitSize: TextUnit, labelGap: Dp,
) {
    val fuel = state.fuel.value
    val active = fuelSegments(fuel)
    val tone = fuelSegmentTone(active)
    val tokens = LocalHomeTokens.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = unit * .018f),
        verticalArrangement = Arrangement.spacedBy(labelGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockLabel(blockLabel(FooterBlockItem.FUEL), labelSize)
        Row(
            horizontalArrangement = Arrangement.spacedBy(unit * .014f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = fuelContentColor(tone, tokens)
            FuelPumpIcon(color, Modifier.size(unit * .031f))
            Canvas(Modifier.weight(1f).height(unit * .015f)) {
                val step = size.width / 10
                val radius = CornerRadius(size.height * .18f)
                repeat(10) { i ->
                    val segmentColor = if (i < active && tone == FuelSegmentTone.NORMAL) {
                        tokens.accent
                    } else fuelSegmentColor(i < active, tone, tokens)
                    drawRoundRect(
                        segmentColor, topLeft = Offset(i * step, 0f),
                        size = Size(step * .84f, size.height), cornerRadius = radius,
                    )
                }
            }
            ValueWithUnit(
                "$fuel/$A5_FUEL_TANK_CAPACITY_LITRES", "L",
                valueSize, unitSize, color = color,
            )
        }
    }
}
