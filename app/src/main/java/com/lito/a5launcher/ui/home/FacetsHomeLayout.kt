package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
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

/** Reading sizes as fractions of the screen height. */
private const val FACETS_VALUE = .12f
private const val FACETS_CAPTION = .04f
private const val FACETS_READING_GAP = .03f

/** Scale label size of both bands. */
private val BandLabelSize = 15.sp

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
        val bandWidth = h * .1f
        // Card geometry is measured from its content so every edge keeps the same padding.
        val metrics = rememberFacetsCardMetrics(h, bandWidth)
        val viewport = remember(h, w, metrics) {
            MapViewportSpec(
                obscuredLeft = margin + metrics.speedWidth,
                obscuredRight = margin + metrics.rpmWidth,
            )
        }

        // The map fills the band between the bars; the instruments float over it.
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topHeight, bottom = bottomHeight),
        ) {
            slots.map(Modifier.fillMaxSize(), viewport)
            StartupMapCover(state)
            // Statistics replace the whole map band; their content fills the
            // space between the two instrument cards.
            slots.mapOverlay(
                this,
                PaddingValues(
                    start = margin + metrics.speedWidth,
                    end = margin + metrics.rpmWidth,
                ),
            )
        }

        val cardModifier = Modifier
            .padding(top = topHeight + margin, bottom = bottomHeight + margin)
            .fillMaxHeight()
            .zIndex(2f)
        // Speed is an L: the band runs down the left and the reading sits in the foot.
        FacetsLCard(
            cardModifier.align(Alignment.CenterStart).offset(x = margin).width(metrics.speedWidth),
            state,
            metrics.speedLeg,
            metrics.speedFoot,
            metrics,
            mirrored = false,
            leg = { FacetsSpeedBand(state, bandWidth, Modifier.fillMaxSize(), labelsOnEnd = true) },
        ) {
            FacetsReading(
                value = { formatDialValue(state.speed.value, DialType.SPEED) },
                unit = "km/h",
                h = h,
            )
        }
        // RPM is the mirrored L, with the gear beside its reading.
        FacetsLCard(
            cardModifier.align(Alignment.CenterEnd).offset(x = -margin).width(metrics.rpmWidth),
            state,
            metrics.rpmLeg,
            metrics.rpmFoot,
            metrics,
            mirrored = true,
            leg = { FacetsRpmBand(state, bandWidth, Modifier.fillMaxSize()) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GearBox(state, metrics.gearSize, (h.value * .075f).sp)
                Spacer(Modifier.width(metrics.padding))
                FacetsReading(
                    value = { formatDialValue(state.rpm.value, DialType.RPM) },
                    unit = "x1000",
                    h = h,
                )
            }
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

/** Sizes of the two L cards, derived from the widest content they can show. */
@Immutable
private data class FacetsCardMetrics(
    val padding: Dp,
    val legVerticalPadding: Dp,
    val gearSize: Dp,
    val speedLeg: Dp,
    val rpmLeg: Dp,
    val speedFoot: DpSize,
    val rpmFoot: DpSize,
) {
    val speedWidth: Dp get() = speedLeg + speedFoot.width
    val rpmWidth: Dp get() = rpmLeg + rpmFoot.width
}

@Composable
private fun rememberFacetsCardMetrics(h: Dp, bandWidth: Dp): FacetsCardMetrics {
    val tokens = LocalHomeTokens.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    return remember(h, bandWidth, tokens, density) {
        fun width(text: String, size: TextUnit, weight: FontWeight, family: FontFamily?): Dp = with(density) {
            measurer.measure(text, TextStyle(fontSize = size, fontWeight = weight, fontFamily = family))
                .size.width.toDp()
        }
        fun cap(size: TextUnit): Dp = with(density) { size.toDp() * CAP_HEIGHT_RATIO }
        val padding = 18.dp
        val bandLabelSize = BandLabelSize
        // Tick gap, major tick and label gap drawn by VerticalBandGauge before the label text.
        val labelOffset = 19.dp
        val speedLabels = labelOffset + width("240", bandLabelSize, FontWeight.Normal, FontFamily.SansSerif)
        val rpmLabels = labelOffset + width("6", bandLabelSize, FontWeight.Normal, FontFamily.SansSerif)
        // End labels are centred on the band ends, 3 dp inside the canvas; keep their ink inside the padding.
        val legVertical = padding + cap(bandLabelSize) / 2f - 3.dp
        val valueSize = (h.value * FACETS_VALUE).sp
        val captionSize = (h.value * FACETS_CAPTION).sp
        val readingHeight = cap(valueSize) + h * FACETS_READING_GAP + cap(captionSize)
        val gearSize = h * .12f
        // Three speed digits are the widest reading; RPM never exceeds one digit and a decimal.
        val speedReading = maxOf(
            width("888", valueSize, FontWeight.Light, tokens.numberFamily),
            width("km/h", captionSize, FontWeight.Normal, tokens.labelFamily),
        )
        val rpmReading = maxOf(
            width("8.8", valueSize, FontWeight.Light, tokens.numberFamily),
            width("x1000", captionSize, FontWeight.Normal, tokens.labelFamily),
        )
        FacetsCardMetrics(
            padding = padding,
            legVerticalPadding = legVertical,
            gearSize = gearSize,
            speedLeg = padding * 2f + bandWidth + speedLabels,
            rpmLeg = padding * 2f + bandWidth + rpmLabels,
            speedFoot = DpSize(speedReading + padding * 2f, readingHeight + padding * 2f),
            rpmFoot = DpSize(
                gearSize + padding + rpmReading + padding * 2f,
                maxOf(gearSize, readingHeight) + padding * 2f,
            ),
        )
    }
}

/**
 * L-shaped card: a full-height leg holding the band and a foot along the bottom
 * holding the reading. [mirrored] puts the leg on the right. Leg and foot keep
 * the same padding between their content and the card edge.
 */
@Composable
private fun FacetsLCard(
    modifier: Modifier,
    state: HomeState,
    legWidth: Dp,
    foot: DpSize,
    metrics: FacetsCardMetrics,
    mirrored: Boolean,
    leg: @Composable () -> Unit,
    footContent: @Composable () -> Unit,
) {
    val tokens = LocalHomeTokens.current
    val density = LocalDensity.current
    val shape = remember(legWidth, foot, mirrored, density) {
        with(density) { LShape(legWidth.toPx(), foot.height.toPx(), 16.dp.toPx(), mirrored) }
    }
    Box(
        modifier
            .startupOutlineAlpha(state.startupProgress)
            .clip(shape)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder.copy(alpha = .35f), shape),
    ) {
        Box(
            Modifier
                .align(if (mirrored) Alignment.TopEnd else Alignment.TopStart)
                .width(legWidth)
                .fillMaxHeight()
                .padding(horizontal = metrics.padding, vertical = metrics.legVerticalPadding),
        ) { leg() }
        Box(
            Modifier
                .align(if (mirrored) Alignment.BottomStart else Alignment.BottomEnd)
                .size(foot)
                .padding(metrics.padding),
            contentAlignment = Alignment.Center,
        ) { footContent() }
    }
}

/** Union of a full-height leg and a bottom foot, both with rounded corners. */
private class LShape(
    private val legWidth: Float,
    private val footHeight: Float,
    private val radius: Float,
    private val mirrored: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = CornerRadius(radius)
        val legLeft = if (mirrored) size.width - legWidth else 0f
        val leg = Path().apply {
            addRoundRect(RoundRect(Rect(legLeft, 0f, legLeft + legWidth, size.height), corner))
        }
        val foot = Path().apply {
            addRoundRect(RoundRect(Rect(0f, size.height - footHeight, size.width, size.height), corner))
        }
        return Outline.Generic(Path.combine(PathOperation.Union, leg, foot))
    }
}

/** Big thin value with its unit underneath, as on the classic dials. */
@Composable
private fun FacetsReading(value: () -> String, unit: String, h: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FacetsValue(value, (h.value * FACETS_VALUE).sp)
        Spacer(Modifier.height(h * FACETS_READING_GAP))
        FacetsCaption(unit, (h.value * FACETS_CAPTION).sp)
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
                    fontSize = (h.value * .06f).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = tokens.numberFamily,
                    maxLines = 1,
                )
                Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(h * .06f).background(tokens.divider))
                Text(
                    state.date.value,
                    color = tokens.content.copy(alpha = .9f),
                    fontSize = (h.value * .03f).sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = tokens.labelFamily,
                    maxLines = 1,
                )
            }
            HomeCommandGroup(
                state,
                actions,
                // Icons fill most of the bar height; the bar itself keeps its size.
                CommandMetrics(buttonSize = h * .11f, iconSize = h * .072f, horizontalPadding = 18.dp),
                ReorderAxis.HORIZONTAL,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    formatOutsideTemperature(state.outside.value, LocalConfiguration.current.locales[0]),
                    color = tokens.content,
                    fontSize = (h.value * .046f).sp,
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
        labelSize = BandLabelSize,
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
        labelSize = BandLabelSize,
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
