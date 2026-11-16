package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
import com.lito.a5launcher.ui.components.FuelSegmentTone
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.SPEED_DIAL_MAX_KMH
import com.lito.a5launcher.ui.components.formatDialValue
import com.lito.a5launcher.ui.components.fuelFraction
import com.lito.a5launcher.ui.components.fuelSegmentTone
import com.lito.a5launcher.ui.components.fuelSegments
import kotlin.math.roundToInt

private val NoPinnedBlocks = emptySet<FooterBlockItem>()

/** Straight vertical gauges hugging both edges, with the map running behind them. */
@Composable
internal fun FacetsHomeLayout(
    state: HomeState,
    actions: HomeActions,
    slots: HomeSlots,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHomeTokens.current
    BoxWithConstraints(modifier.fillMaxSize().background(tokens.background)) {
        val h = maxHeight
        val w = maxWidth
        val topHeight = h * .11f
        val bottomHeight = h * .13f
        val margin = h * .03f
        val cardWidth = w * .205f
        val bandWidth = h * .1f
        val viewport = remember(h, w) {
            MapViewportSpec(obscuredLeft = margin + cardWidth, obscuredRight = margin + cardWidth)
        }

        // The map fills the band between the bars; the instruments float over it.
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topHeight, bottom = bottomHeight),
        ) {
            slots.map(Modifier.fillMaxSize(), viewport)
            StartupMapCover(state)
            Box(Modifier.fillMaxSize().padding(horizontal = margin * 2f + cardWidth)) {
                slots.mapOverlay(this)
            }
        }

        val cardModifier = Modifier
            .padding(top = topHeight + margin, bottom = bottomHeight + margin)
            .width(cardWidth)
            .fillMaxHeight()
            .zIndex(2f)
        FacetsInstrumentCard(cardModifier.align(Alignment.CenterStart).offset(x = margin), state) {
            FacetsSpeedBand(state, bandWidth, Modifier.width(bandWidth + 52.dp).fillMaxHeight(), labelsOnEnd = true)
            FacetsReading(
                value = { formatDialValue(state.speed.value, DialType.SPEED) },
                unit = "km/h",
                h = h,
                modifier = Modifier.weight(1f),
            )
        }
        FacetsInstrumentCard(cardModifier.align(Alignment.CenterEnd).offset(x = -margin), state) {
            FacetsReading(
                value = { formatDialValue(state.rpm.value, DialType.RPM) },
                unit = "x1000",
                h = h,
                modifier = Modifier.weight(1f),
            ) {
                Spacer(Modifier.height(h * .05f))
                GearBox(state, h * .13f, (h.value * .08f).sp)
            }
            FacetsRpmBand(state, bandWidth, Modifier.width(bandWidth + 44.dp).fillMaxHeight())
        }

        FacetsTopBar(
            state,
            actions,
            h,
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topHeight)
                .startupContentAlpha(state)
                .zIndex(3f),
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomHeight)
                .background(tokens.chrome)
                .startupContentAlpha(state)
                .zIndex(3f),
        ) {
            GlowLine(Modifier.align(Alignment.TopCenter))
            // One label size and one value size for every block, fuel and witnesses included.
            val labelSize = (h.value * .025f).sp
            val valueSize = (h.value * .039f).sp
            val labelGap = 8.dp
            val unitSize = valueSize * .7f
            BlockStrip(
                state = state,
                actions = actions,
                pinned = NoPinnedBlocks,
                axis = ReorderAxis.HORIZONTAL,
                labelSize = labelSize,
                valueSize = valueSize,
                unitSize = unitSize,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
                itemWeight = { item ->
                    when (item) {
                        FooterBlockItem.WITNESSES -> 1.6f
                        FooterBlockItem.ODOMETER, FooterBlockItem.FUEL -> 1.15f
                        else -> 1f
                    }
                },
            ) { item ->
                when (item) {
                    FooterBlockItem.FUEL -> FacetsFuel(state, labelSize, valueSize, unitSize, labelGap)
                    FooterBlockItem.WITNESSES -> WitnessGroup(state, scale = .72f, spacing = 18.dp)
                    else -> DefaultBlockContent(item, state, actions, labelSize, valueSize, unitSize, labelGap)
                }
            }
        }
    }
}

/** Translucent navy card holding one straight band and its reading. */
@Composable
private fun FacetsInstrumentCard(
    modifier: Modifier,
    state: HomeState,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val tokens = LocalHomeTokens.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .startupOutlineAlpha(state.startupProgress)
            .clip(shape)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder.copy(alpha = .35f), shape)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Big thin value with its unit underneath, as on the classic dials. */
@Composable
private fun FacetsReading(
    value: () -> String,
    unit: String,
    h: Dp,
    modifier: Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FacetsValue(value, (h.value * .15f).sp)
        Spacer(Modifier.height(h * .035f))
        FacetsCaption(unit, (h.value * .042f).sp)
        extra()
    }
}

@Composable
private fun FacetsTopBar(state: HomeState, actions: HomeActions, h: Dp, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    Box(modifier.background(tokens.chrome)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.time.value,
                    color = tokens.content,
                    fontSize = (h.value * .072f).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = tokens.numberFamily,
                    maxLines = 1,
                )
                Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(h * .06f).background(tokens.divider))
                Text(
                    state.date.value,
                    color = tokens.content.copy(alpha = .9f),
                    fontSize = (h.value * .034f).sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = tokens.labelFamily,
                    maxLines = 1,
                )
            }
            HomeCommandGroup(
                state,
                actions,
                CommandMetrics(buttonSize = h * .09f, iconSize = h * .056f, horizontalPadding = 12.dp),
                ReorderAxis.HORIZONTAL,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    formatOutsideTemperature(state.outside.value, LocalConfiguration.current.locales[0]),
                    color = tokens.content,
                    fontSize = (h.value * .055f).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = tokens.numberFamily,
                    maxLines = 1,
                )
            }
        }
        GlowLine(Modifier.align(Alignment.BottomCenter))
    }
}

/** Hairline with a soft blue glow, brightest in the middle. */
@Composable
private fun GlowLine(modifier: Modifier = Modifier) {
    val tokens = LocalHomeTokens.current
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        val brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            .5f to tokens.accent.copy(alpha = .75f),
            1f to Color.Transparent,
        )
        drawRect(brush, topLeft = Offset(0f, size.height / 2f - .5.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
        drawRect(brush, alpha = .25f)
    }
}

@Composable
private fun FacetsSpeedBand(state: HomeState, bandWidth: Dp, modifier: Modifier, labelsOnEnd: Boolean) {
    val fraction = animatedGaugeFraction(state.speed, SPEED_DIAL_MAX_KMH, rpm = false)
    VerticalBandGauge(
        fraction = fraction,
        scale = SpeedGaugeScale,
        startupProgress = state.startupProgress,
        bandWidth = bandWidth,
        labelSize = 15.sp,
        labelsOnEnd = labelsOnEnd,
        modifier = modifier,
    )
}

@Composable
private fun FacetsRpmBand(state: HomeState, bandWidth: Dp, modifier: Modifier) {
    val fraction = animatedGaugeFraction(state.rpm, RPM_SCALE_MAX, rpm = true)
    VerticalBandGauge(
        fraction = fraction,
        scale = RpmGaugeScale,
        startupProgress = state.startupProgress,
        bandWidth = bandWidth,
        labelSize = 15.sp,
        labelsOnEnd = false,
        modifier = modifier,
        redZoneFrom = RPM_RED_ZONE_FRACTION,
    )
}

@Composable
private fun FacetsValue(value: () -> String, size: TextUnit) {
    val tokens = LocalHomeTokens.current
    Text(
        value(),
        color = tokens.content,
        fontSize = size,
        lineHeight = size,
        fontWeight = FontWeight.Light,
        fontFamily = tokens.numberFamily,
        maxLines = 1,
        style = TightTextStyle,
        modifier = Modifier.capHeightBounds(size),
    )
}

@Composable
private fun FacetsCaption(text: String, size: TextUnit) {
    val tokens = LocalHomeTokens.current
    Text(
        text,
        color = tokens.label,
        fontSize = size,
        lineHeight = size,
        fontWeight = FontWeight.Normal,
        fontFamily = tokens.labelFamily,
        maxLines = 1,
        style = TightTextStyle,
        modifier = Modifier.capHeightBounds(size),
    )
}

/** Fuel block laid out like its neighbours: label, litres and a slim level bar. */
@Composable
private fun FacetsFuel(
    state: HomeState,
    labelSize: TextUnit,
    valueSize: TextUnit,
    unitSize: TextUnit,
    labelGap: Dp,
) {
    val tokens = LocalHomeTokens.current
    val fuel = state.fuel.value
    val tone = fuelSegmentTone(fuelSegments(fuel))
    val barColor = fuelSegmentColor(true, tone, tokens)
    val fraction = fuelFraction(fuel)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BlockLabel(stringResource(R.string.dashboard_fuel), labelSize)
        Spacer(Modifier.height(labelGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ValueWithUnit(
                "$fuel/$A5_FUEL_TANK_CAPACITY_LITRES",
                "L",
                valueSize,
                unitSize,
                color = fuelContentColor(tone, tokens),
            )
            Spacer(Modifier.width(10.dp))
            Canvas(Modifier.width(48.dp).height(6.dp)) {
                val corner = CornerRadius(size.height / 2f)
                drawRoundRect(tokens.fuelInactive, cornerRadius = corner)
                if (fraction > 0f) {
                    drawRoundRect(barColor, size = Size(size.width * fraction, size.height), cornerRadius = corner)
                }
            }
        }
    }
}
