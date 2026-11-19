package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val HorizonPinnedBlocks = setOf(FooterBlockItem.FUEL, FooterBlockItem.WITNESSES)
private val HorizonSpeedScale = GaugeScale(maxValue = SPEED_DIAL_MAX_KMH, labelStep = 80, minorPerLabel = 4)
private val PanelShape = RoundedCornerShape(14.dp)

/** Full-screen map with dark glass panels floating over it. */
@Composable
internal fun HorizonHomeLayout(
    state: HomeState,
    actions: HomeActions,
    slots: HomeSlots,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        // One spacing value for the screen margin, the gaps between boxes and
        // the inner padding of every box, so all of them read the same.
        val pad = h * .03f
        val iconSize = h * .06f
        val topHeight = iconSize + pad * 2f
        val labelSize = (h.value * .03f).sp
        val valueSize = (h.value * .052f).sp
        val bottomHeight = h * .142f
        val instrumentWidth = w * .27f
        val witnessWidth = w * .23f
        val viewport = remember(h, w) {
            MapViewportSpec(
                obscuredLeft = pad + instrumentWidth,
                obscuredTop = pad + topHeight,
                obscuredRight = pad + witnessWidth,
                obscuredBottom = pad + bottomHeight,
            )
        }
        slots.map(Modifier.fillMaxSize(), viewport)
        StartupMapCover(state)
        // Statistics replace the whole map; their content fills the space the
        // floating panels leave free.
        Box(Modifier.fillMaxSize()) {
            slots.mapOverlay(
                this,
                PaddingValues(
                    start = pad + instrumentWidth,
                    top = pad + topHeight,
                    end = pad + witnessWidth,
                    bottom = pad + bottomHeight,
                ),
            )
        }

        // Both side columns run from the top margin to the value strip with the
        // same gap between their bottom boxes; the first box of each is as tall
        // as the command bar.
        val instrumentHeight = h * .25f
        val columns = Modifier
            .fillMaxHeight()
            .padding(top = pad, bottom = pad * 2f + bottomHeight)
            .zIndex(2f)

        // Commands, centred and as tall as the clock and temperature boxes.
        HorizonPanel(
            0.dp,
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = pad)
                .height(topHeight)
                .startupContentAlpha(state)
                .zIndex(2f),
        ) {
            HomeCommandGroup(
                state,
                actions,
                CommandMetrics(buttonSize = topHeight, iconSize = iconSize, horizontalPadding = pad),
                ReorderAxis.HORIZONTAL,
                dividers = false,
            )
        }

        // Left column: clock at the top; speed and RPM sit on the bottom line,
        // mirroring the fuel and witness boxes on the right.
        Column(
            columns.align(Alignment.TopStart).padding(start = pad).width(instrumentWidth),
            verticalArrangement = Arrangement.spacedBy(pad),
        ) {
            HorizonPanel(pad, Modifier.height(topHeight).startupContentAlpha(state), Alignment.CenterStart) {
                InlineClock(state, (h.value * .058f).sp, (h.value * .03f).sp)
            }
            Spacer(Modifier.weight(1f))
            HorizonInstrument(state, pad, Modifier.fillMaxWidth().height(instrumentHeight)) {
                Column(Modifier.fillMaxSize()) {
                    HorizonReading(
                        value = { formatDialValue(state.speed.value, DialType.SPEED) },
                        unit = "km/h",
                        valueSize = (h.value * .1f).sp,
                    )
                    Spacer(Modifier.weight(1f))
                    HorizonSpeedBar(state, Modifier.fillMaxWidth().height(h * .095f))
                }
            }
            HorizonInstrument(state, pad, Modifier.fillMaxWidth().height(instrumentHeight)) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        HorizonReading(
                            value = { formatDialValue(state.rpm.value, DialType.RPM) },
                            unit = "x1000",
                            valueSize = (h.value * .1f).sp,
                        )
                        Spacer(Modifier.weight(1f))
                        HorizonRpmBar(state, Modifier.fillMaxWidth().height(h * .095f))
                    }
                    Spacer(Modifier.width(pad))
                    GearBox(state, h * .14f, (h.value * .09f).sp)
                }
            }
        }

        // Right column: temperature at the top; fuel and witnesses sit on the
        // same bottom line as the RPM box, as wide as the witnesses panel.
        Column(
            columns.align(Alignment.TopEnd).padding(end = pad).width(IntrinsicSize.Max).startupContentAlpha(state),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(pad),
        ) {
            HorizonPanel(pad, Modifier.height(topHeight)) {
                OutsideTemperatureWithIcon(state, (h.value * .05f).sp, iconSize)
            }
            Spacer(Modifier.weight(1f))
            HorizonPanel(pad, Modifier.fillMaxWidth()) {
                HorizonFuel(state, h)
            }
            HorizonPanel(pad) {
                WitnessGroup(state, scale = .95f, spacing = 26.dp)
            }
        }

        // Bottom value strip: blocks spread edge to edge inside the shared padding.
        HorizonPanel(
            0.dp,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = pad, end = pad, bottom = pad)
                .fillMaxWidth()
                .height(bottomHeight)
                .startupContentAlpha(state)
                .zIndex(2f),
        ) {
            BlockStrip(
                state = state,
                actions = actions,
                pinned = HorizonPinnedBlocks,
                axis = ReorderAxis.HORIZONTAL,
                labelSize = labelSize,
                valueSize = valueSize,
                unitSize = valueSize * .64f,
                modifier = Modifier.fillMaxSize().padding(horizontal = pad),
                fadingDividers = true,
                labelGap = 8.dp,
            )
        }
    }
}

/** Glass panel whose content sits [padding] away from every edge. */
@Composable
private fun HorizonPanel(
    padding: Dp,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassPanel(modifier, PanelShape, contentAlignment) {
        Box(Modifier.padding(padding), contentAlignment = contentAlignment, content = content)
    }
}

@Composable
private fun HorizonInstrument(
    state: HomeState,
    padding: Dp,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    HorizonPanel(padding, modifier.startupOutlineAlpha(state.startupProgress), Alignment.TopStart, content)
}

/** Clock and date on one baseline. */
@Composable
private fun InlineClock(state: HomeState, timeSize: TextUnit, dateSize: TextUnit) {
    val tokens = LocalHomeTokens.current
    // Time and date share one vertical centre line.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.time.value,
            color = tokens.content,
            fontSize = timeSize,
            lineHeight = timeSize,
            style = TightTextStyle,
            fontWeight = tokens.numberWeight,
            fontFamily = tokens.numberFamily,
            maxLines = 1,
            modifier = Modifier.capHeightBounds(timeSize),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            state.date.value,
            color = tokens.content.copy(alpha = .86f),
            fontSize = dateSize,
            lineHeight = dateSize,
            style = TightTextStyle,
            fontWeight = FontWeight.Medium,
            fontFamily = tokens.labelFamily,
            maxLines = 1,
            modifier = Modifier.capHeightBounds(dateSize),
        )
    }
}

/** Value text in its own scope so a telemetry change only redraws this text. */
@Composable
private fun HorizonReading(value: () -> String, unit: String, valueSize: TextUnit) {
    ValueWithUnit(value(), unit, valueSize, valueSize * .36f, weight = FontWeight.Bold)
}

@Composable
private fun HorizonSpeedBar(state: HomeState, modifier: Modifier) {
    val fraction = animatedGaugeFraction(state.speed, SPEED_DIAL_MAX_KMH, rpm = false)
    HorizontalBarGauge(
        fraction = fraction,
        scale = HorizonSpeedScale,
        startupProgress = state.startupProgress,
        barHeight = 12.dp,
        labelSize = 14.sp,
        modifier = modifier,
        brightTip = true,
    )
}

@Composable
private fun HorizonRpmBar(state: HomeState, modifier: Modifier) {
    val fraction = animatedGaugeFraction(state.rpm, RPM_SCALE_MAX, rpm = true)
    HorizontalBarGauge(
        fraction = fraction,
        scale = RpmGaugeScale,
        startupProgress = state.startupProgress,
        barHeight = 12.dp,
        labelSize = 14.sp,
        modifier = modifier,
        redZoneFrom = RPM_RED_ZONE_FRACTION,
        showMinorTicks = false,
        brightTip = true,
    )
}

@Composable
internal fun GearBox(state: HomeState, size: Dp, textSize: TextUnit, modifier: Modifier = Modifier) {
    val tokens = LocalHomeTokens.current
    Box(
        modifier
            .size(size)
            .border(1.5.dp, tokens.gaugeOutline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            state.gear.value,
            color = tokens.content,
            fontSize = textSize,
            lineHeight = textSize,
            fontWeight = FontWeight.Bold,
            fontFamily = tokens.numberFamily,
            maxLines = 1,
        )
    }
}

@Composable
private fun HorizonFuel(state: HomeState, h: Dp) {
    val tokens = LocalHomeTokens.current
    val fuel = state.fuel.value
    val tone = fuelSegmentTone(fuelSegments(fuel))
    val color = fuelSegmentColor(true, tone, tokens)
    val textColor = fuelContentColor(tone, tokens)
    val fraction = fuelFraction(fuel)
    // Horizontal: pump, level bar filling from the left and litres.
    Row(verticalAlignment = Alignment.CenterVertically) {
        FuelPumpIcon(
            color = textColor.copy(alpha = if (tone == FuelSegmentTone.NORMAL) .80f else 1f),
            modifier = Modifier.size(h * .055f),
        )
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.weight(1f).height(h * .03f)) {
            val corner = CornerRadius(size.height / 2f)
            drawRoundRect(tokens.fuelInactive, cornerRadius = corner)
            val filled = size.width * fraction
            if (filled > 0f) {
                drawRoundRect(color, size = Size(filled, size.height), cornerRadius = corner)
            }
        }
        Spacer(Modifier.width(12.dp))
        ValueWithUnit(fuel.toString(), "L", (h.value * .046f).sp, (h.value * .03f).sp, color = textColor)
    }
}
