package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.oemStartupPresentation
import kotlin.math.roundToInt

/**
 * Text metrics for the theme layouts: no font padding and the line box centred
 * on the glyphs, so a value sits the same distance from its panel's top and
 * bottom edges as from its sides.
 */
internal val TightTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** Roboto cap height (1456/2048 em); figures and capitals reach exactly this high. */
internal const val CAP_HEIGHT_RATIO = .711f

/**
 * Reports a single-line text as only as tall as its capitals, with the baseline
 * at the bottom, so padding around figures and labels is measured from the ink
 * instead of from the font's ascent. The glyphs still draw in full.
 */
internal fun Modifier.capHeightBounds(fontSize: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val baseline = placeable[FirstBaseline]
    if (baseline == AlignmentLine.Unspecified) {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val cap = (fontSize.toPx() * CAP_HEIGHT_RATIO).roundToInt()
        layout(placeable.width, cap, mapOf(FirstBaseline to cap, LastBaseline to cap)) {
            placeable.place(0, cap - baseline)
        }
    }
}

/** Translucent rounded panel used by the themes that float chrome over the map. */
/** Opacity of the light along the top of a glass panel. */
private const val GLASS_SHEEN_ALPHA = .07f

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalHomeTokens.current
    Box(
        modifier
            .clip(shape)
            .background(tokens.surface)
            // Light falling on the top of the glass, fading out before the content.
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = GLASS_SHEEN_ALPHA), Color.Transparent),
                        endY = size.height * .55f,
                    ),
                )
            }
            .border(1.dp, tokens.surfaceBorder, shape),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Large value followed by a small unit on the same baseline. */
@Composable
internal fun ValueWithUnit(
    value: String,
    unit: String?,
    valueSize: TextUnit,
    unitSize: TextUnit,
    color: Color? = null,
    weight: FontWeight? = null,
) {
    val tokens = LocalHomeTokens.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            color = color ?: tokens.content,
            fontSize = valueSize,
            lineHeight = valueSize,
            fontWeight = weight ?: tokens.numberWeight,
            fontFamily = tokens.numberFamily,
            maxLines = 1,
            style = TightTextStyle,
            modifier = Modifier.alignByBaseline().capHeightBounds(valueSize),
        )
        if (unit != null) {
            Text(
                " $unit",
                color = (color ?: tokens.content).copy(alpha = .78f),
                fontSize = unitSize,
                lineHeight = unitSize,
                fontWeight = FontWeight.Medium,
                fontFamily = tokens.labelFamily,
                maxLines = 1,
                style = TightTextStyle,
                modifier = Modifier.alignByBaseline().capHeightBounds(unitSize),
            )
        }
    }
}

/** Clock stacked over the date, used by the floating themes. */
@Composable
internal fun StackedClock(state: HomeState, timeSize: TextUnit, dateSize: TextUnit) {
    val tokens = LocalHomeTokens.current
    Column {
        Text(
            state.time.value,
            color = tokens.content,
            fontSize = timeSize,
            lineHeight = timeSize,
            fontWeight = tokens.numberWeight,
            fontFamily = tokens.numberFamily,
            maxLines = 1,
        )
        Text(
            state.date.value,
            color = tokens.content.copy(alpha = .86f),
            fontSize = dateSize,
            lineHeight = dateSize * 1.1f,
            fontWeight = FontWeight.Medium,
            fontFamily = tokens.labelFamily,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ThermometerIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.width * .12f
        val bulbRadius = size.width * .24f
        val cx = size.width / 2f
        val bulbCenter = Offset(cx, size.height - bulbRadius - stroke / 2f)
        val tubeHalf = size.width * .17f
        drawRoundRect(
            color,
            topLeft = Offset(cx - tubeHalf, 0f),
            size = androidx.compose.ui.geometry.Size(tubeHalf * 2f, bulbCenter.y - bulbRadius * .6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(tubeHalf),
            style = Stroke(stroke),
        )
        drawCircle(color, bulbRadius, bulbCenter, style = Stroke(stroke))
        drawLine(color, Offset(cx, size.height * .32f), Offset(cx, bulbCenter.y), stroke, StrokeCap.Round)
        drawCircle(color, bulbRadius * .45f, bulbCenter)
    }
}

/** Thin outline icon for a value block; only the thermometer is needed today. */
@Composable
internal fun OutsideTemperatureWithIcon(state: HomeState, textSize: TextUnit, iconSize: Dp) {
    val tokens = LocalHomeTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThermometerIcon(tokens.content, Modifier.width(iconSize * .55f).height(iconSize))
        OutsideTemperatureText(state, textSize)
    }
}

/**
 * Value strip of the reorderable blocks that are not [pinned] elsewhere,
 * each block taking the same width, or with [spread] its own width with equal
 * gaps so the first and last blocks touch the strip padding. Long-press drag
 * reorders along [axis].
 */
@Composable
internal fun BlockStrip(
    state: HomeState,
    actions: HomeActions,
    pinned: Set<FooterBlockItem>,
    axis: ReorderAxis,
    labelSize: TextUnit,
    valueSize: TextUnit,
    unitSize: TextUnit,
    modifier: Modifier = Modifier,
    plateShape: Shape = RoundedCornerShape(10.dp),
    dividers: Boolean = true,
    fadingDividers: Boolean = false,
    itemWeight: (FooterBlockItem) -> Float = { 1f },
    spread: Boolean = false,
    labelGap: Dp = 0.dp,
    block: @Composable BoxScope.(FooterBlockItem) -> Unit = { item ->
        DefaultBlockContent(item, state, actions, labelSize, valueSize, unitSize, labelGap, fillWidth = !spread)
    },
) {
    val tokens = LocalHomeTokens.current
    val order = state.footerBlockOrder.value
    val visibleItems = remember(order, pinned) { order.filterNot(pinned::contains) }
    val visibleSet = remember(visibleItems) { visibleItems.toSet() }
    val sizes = remember { mutableStateMapOf<FooterBlockItem, Int>() }
    val gapPx = with(LocalDensity.current) { 1.dp.roundToPx() }
    val items: @Composable (FooterBlockItem.() -> Modifier) -> Unit = { slotModifier ->
        visibleItems.forEachIndexed { index, item ->
            key(item) {
                LinearReorderSlot(
                    item = item,
                    order = order,
                    visibleItems = visibleSet,
                    axis = axis,
                    sizes = sizes,
                    gapPx = gapPx,
                    plateShape = plateShape,
                    onMove = actions.onFooterBlockOrderChanged,
                    modifier = item.slotModifier(),
                ) { block(item) }
            }
            if (dividers && index != visibleItems.lastIndex) {
                if (axis == ReorderAxis.HORIZONTAL && fadingDividers) {
                    Box(
                        Modifier.fillMaxHeight(.7f).width(1.dp).background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, tokens.divider, tokens.divider, Color.Transparent),
                            ),
                        ),
                    )
                } else if (axis == ReorderAxis.HORIZONTAL) {
                    Box(Modifier.fillMaxHeight(.56f).width(1.dp).background(tokens.divider))
                } else {
                    Box(Modifier.fillMaxWidth(.56f).height(1.dp).background(tokens.divider))
                }
            }
        }
    }
    if (axis == ReorderAxis.HORIZONTAL) {
        if (spread) {
            Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                items { Modifier.fillMaxHeight() }
            }
        } else {
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                items { Modifier.weight(itemWeight(this)).fillMaxHeight() }
            }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            items { Modifier.weight(itemWeight(this)).fillMaxWidth() }
        }
    }
}

/** Label over value and unit; opens the statistics panel the classic footer opened. */
@Composable
internal fun BoxScope.DefaultBlockContent(
    item: FooterBlockItem,
    state: HomeState,
    actions: HomeActions,
    labelSize: TextUnit,
    valueSize: TextUnit,
    unitSize: TextUnit,
    labelGap: Dp = 0.dp,
    fillWidth: Boolean = true,
) {
    val scope = blockStatisticsScope(item)
    Box(
        Modifier
            .then(if (fillWidth) Modifier.fillMaxSize() else Modifier.fillMaxHeight())
            .then(if (scope != null) Modifier.clickable { actions.onStatistics(scope) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val reading = rememberBlockReading(item, state)
        if (reading != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BlockLabel(blockLabel(item), labelSize)
                if (labelGap > 0.dp) Spacer(Modifier.height(labelGap))
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

/** Alpha for instrument panels: they appear with the dial outlines at startup. */
internal fun Modifier.startupOutlineAlpha(startupProgress: State<Float?>): Modifier = graphicsLayer {
    alpha = oemStartupPresentation(startupProgress.value).dialOutlineAlpha
}
