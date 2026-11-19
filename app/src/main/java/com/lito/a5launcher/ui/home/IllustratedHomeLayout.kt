package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import com.lito.a5launcher.R
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.fuelFraction
import com.lito.a5launcher.ui.components.fuelSegmentTone
import com.lito.a5launcher.ui.components.fuelSegments

/** The porthole themes share one layout and differ in their art. */
internal enum class IllustratedStyle(val palette: IllustratedPalette) {
    SUBMARINE(SubmarinePalette),
    MOON(MoonPalette),
}

/** Rounded rectangle of the screen taken by an instrument, in dp. */
@Immutable
internal data class Footprint(val left: Dp, val top: Dp, val width: Dp, val height: Dp, val radius: Dp) {
    val right: Dp get() = left + width
    val centerX: Dp get() = left + width / 2f

    fun inflate(by: Dp) = Footprint(left - by, top - by, width + by * 2f, height + by * 2f, radius + by)
    fun shift(dx: Dp, dy: Dp) = copy(left = left + dx, top = top + dy)
}

/** Map window between the instruments: a rounded rectangle with their outlines cut out. */
private class CutoutMapShape(private val corner: Dp, private val cutouts: List<Footprint>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(cutoutMapPath(size, density, corner, cutouts))
}

private fun cutoutMapPath(size: Size, density: Density, corner: Dp, cutouts: List<Footprint>): Path = with(density) {
    val base = Path().apply {
        addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner.toPx())))
    }
    val holes = Path()
    cutouts.forEach { cut ->
        holes.addRoundRect(
            RoundRect(
                Rect(cut.left.toPx(), cut.top.toPx(), cut.right.toPx(), (cut.top + cut.height).toPx()),
                CornerRadius(cut.radius.toPx()),
            ),
        )
    }
    Path.combine(PathOperation.Difference, base, holes)
}

private val FooterWeights = mapOf(
    FooterBlockItem.TIME to 1f,
    FooterBlockItem.TRIP to 1.1f,
    FooterBlockItem.CONSUMPTION to 1.25f,
    FooterBlockItem.REFUEL_DISTANCE to 1.15f,
    FooterBlockItem.RANGE to 1.15f,
    FooterBlockItem.FUEL to 1.4f,
    FooterBlockItem.WITNESSES to 2f,
    FooterBlockItem.ODOMETER to 1.5f,
)

/** Seaweed width as a fraction of the instruments' height. */
private const val SEAWEED_WIDTH = .3f

/** Brass of the Submarine map trim, matching the portholes. */
private val SubmarineTrim = Color(0xFFF2B428)
private val SubmarineTrimShade = Color(0xFF0B2C35)
private val SubmarineTrimWidth = 5.5.dp

/** How far the map reaches under the rim of each instrument. */
private val MAP_TUCK = 1.5.dp

/** Opacity of the Moon value bar: barely translucent. */
private const val MOON_FOOTER_ALPHA = .86f

/**
 * Illustrated home: a framed top bar, the speed dial and the RPM instrument
 * against the screen edges, the map filling everything between them and a
 * framed value bar at the bottom.
 */
@Composable
internal fun IllustratedHomeLayout(
    style: IllustratedStyle,
    state: HomeState,
    actions: HomeActions,
    slots: HomeSlots,
    modifier: Modifier = Modifier,
) {
    val palette = style.palette
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        val margin = h * .02f
        val topHeight = h * .11f
        val footerHeight = h * .16f
        val middleTop = margin * 2f + topHeight
        val middleHeight = h - margin * 4f - topHeight - footerHeight
        val left = Footprint(margin, middleTop, middleHeight, middleHeight, middleHeight / 2f)
        val right = when (style) {
            IllustratedStyle.SUBMARINE -> (middleHeight * TALL_PORTHOLE_ASPECT).let { width ->
                Footprint(w - margin - width, middleTop, width, middleHeight, width / 2f)
            }
            IllustratedStyle.MOON -> Footprint(w - margin - middleHeight, middleTop, middleHeight, middleHeight, middleHeight / 2f)
        }
        // The map spans from one instrument centre to the other and runs right up
        // to their rims, as in Classic: no gap, so the eye does not keep jumping
        // between outlines while driving. In Submarine the brass trim wraps both
        // portholes evenly: the cut sits just outside them and the trim covers the
        // seam. The Moon disc is drawn smaller than its footprint: there the map
        // reaches a little under the disc and its top and bottom edges line up
        // with the discs.
        val mapLeft = left.centerX
        val mapWidth = right.centerX - mapLeft
        val corner = h * .05f
        val discInset = when (style) {
            IllustratedStyle.SUBMARINE -> 0.dp
            IllustratedStyle.MOON -> middleHeight * (.5f - NEON_DIAL_RADIUS)
        }
        val cutGrow = when (style) {
            IllustratedStyle.SUBMARINE -> SubmarineTrimWidth / 2f - MAP_TUCK
            IllustratedStyle.MOON -> -(discInset + MAP_TUCK)
        }
        val mapTop = middleTop + discInset
        val mapHeight = middleHeight - discInset * 2f
        val cutouts = remember(left, right, mapLeft, mapTop, cutGrow) {
            listOf(left, right).map { it.inflate(cutGrow).shift(-mapLeft, -mapTop) }
        }
        val visibleStart = cutouts[0].right
        val visibleEnd = mapWidth - cutouts[1].left
        val mapShape = remember(corner, cutouts) { CutoutMapShape(corner, cutouts) }
        val viewport = remember(visibleStart, visibleEnd, corner) {
            MapViewportSpec(obscuredLeft = visibleStart, obscuredRight = visibleEnd, cornerRadius = corner)
        }

        IllustratedBackdrop(
            style,
            Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress),
            middleTop,
            middleHeight,
            left,
            right,
            Rect(mapLeft.value, mapTop.value, (mapLeft + mapWidth).value, (mapTop + mapHeight).value),
            corner,
            topHeight,
            footerHeight,
            margin,
        )

        Box(
            Modifier
                .offset(x = mapLeft, y = mapTop)
                .size(mapWidth, mapHeight)
                .clip(mapShape),
        ) {
            slots.map(Modifier.fillMaxSize(), viewport)
            StartupMapCover(state)
            // Statistics replace only the map window, between the instruments.
            slots.mapOverlay(this, PaddingValues(start = visibleStart, end = visibleEnd))
        }
        // Thin frame in the instruments' style; along the cutouts it runs under
        // their rims, so it only shows on the map's free edges.
        Canvas(
            Modifier
                .offset(x = mapLeft, y = mapTop)
                .size(mapWidth, mapHeight)
                .startupOutlineAlpha(state.startupProgress),
        ) {
            drawMapFrame(style, cutoutMapPath(size, this, corner, cutouts))
        }
        Box(Modifier.offset(x = left.left, y = left.top).size(left.width, left.height).zIndex(1f)) {
            IllustratedSpeedInstrument(style, state, left.width)
        }
        Box(Modifier.offset(x = right.left, y = right.top).size(right.width, right.height).zIndex(1f)) {
            IllustratedRpmInstrument(style, state, right.width, right.height)
        }

        IllustratedTopBar(
            style,
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

        IllustratedFooter(
            style,
            state,
            actions,
            h,
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

private fun DrawScope.drawMapFrame(style: IllustratedStyle, outline: Path) {
    when (style) {
        // Thin brass trim like the portholes: a dark edge under a gold line.
        IllustratedStyle.SUBMARINE -> {
            drawPath(outline, SubmarineTrimShade, style = Stroke(SubmarineTrimWidth.toPx()))
            drawPath(outline, SubmarineTrim, style = Stroke(SubmarineTrimWidth.toPx() * .55f))
        }
        // The neon of the dial rims, a little heavier.
        IllustratedStyle.MOON -> drawGlow(outline, style.palette.frame, line = 2.5.dp, halo = 7.dp)
    }
}

/** Neon outline: two soft halos under a sharp line. */
internal fun DrawScope.drawGlow(outline: Path, color: Color, line: Dp = 1.5.dp, halo: Dp = 5.dp) {
    drawPath(outline, color.copy(alpha = .08f), style = Stroke(halo.toPx()))
    drawPath(outline, color.copy(alpha = .55f), style = Stroke(line.toPx()))
}

private fun roundRectPath(rect: Rect, radius: Float) = Path().apply {
    addRoundRect(RoundRect(rect, CornerRadius(radius)))
}

/** Frame of a bar or pill in the theme's style, drawn behind its content. */
private fun Modifier.illustratedPanel(
    style: IllustratedStyle,
    radiusFraction: Float = .5f,
    fillAlpha: Float = 1f,
): Modifier = drawWithCache {
    val palette = style.palette
    val inset = 2.dp.toPx()
    val rect = Rect(inset, inset, size.width - inset, size.height - inset)
    val radius = rect.height * radiusFraction
    val outline = roundRectPath(rect, radius)
    onDrawBehind {
        if (style != IllustratedStyle.SUBMARINE) drawPath(outline, palette.panel, alpha = fillAlpha)
        when (style) {
            // Clear glass over the water: only its outline is drawn.
            IllustratedStyle.SUBMARINE -> drawPath(outline, palette.panelBorder, style = Stroke(1.5.dp.toPx()))
            IllustratedStyle.MOON -> drawGlow(outline, palette.panelBorder)
        }
    }
}

@Composable
private fun IllustratedTopBar(
    style: IllustratedStyle,
    state: HomeState,
    actions: HomeActions,
    h: Dp,
    barHeight: Dp,
    modifier: Modifier,
) {
    val palette = style.palette
    val pad = h * .035f
    val timeSize = (h.value * .056f).sp
    val dateSize = (h.value * .033f).sp
    val metrics = CommandMetrics(buttonSize = barHeight, iconSize = barHeight * .6f, horizontalPadding = h * .026f)
    val clock: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IllustratedText(state.time.value, timeSize, palette.content, FontWeight.Black)
            Box(Modifier.padding(horizontal = pad * .7f).width(2.dp).height(barHeight * .45f).drawBehind { drawRect(palette.divider) })
            IllustratedText(state.date.value, dateSize, palette.content, FontWeight.Bold)
        }
    }
    val temperature: @Composable () -> Unit = {
        OutsideTemperatureText(state, (h.value * .05f).sp)
    }
    val commands: @Composable () -> Unit = {
        HomeCommandGroup(state, actions, metrics, ReorderAxis.HORIZONTAL, dividers = style == IllustratedStyle.MOON)
    }
    if (style == IllustratedStyle.MOON) {
        // Three separate pills, as in the design.
        Box(modifier) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().illustratedPanel(style).padding(horizontal = pad), contentAlignment = Alignment.Center) { clock() }
            Box(Modifier.align(Alignment.Center).fillMaxHeight().illustratedPanel(style).padding(horizontal = pad * .5f), contentAlignment = Alignment.Center) { commands() }
            Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                TopBarPlanet(R.drawable.illustration_moon_planet, barHeight * 1.05f)
                Spacer(Modifier.width(pad))
                Box(Modifier.fillMaxHeight().illustratedPanel(style).padding(horizontal = pad), contentAlignment = Alignment.Center) { temperature() }
            }
        }
    } else {
        Box(modifier.illustratedPanel(style).padding(horizontal = pad * 1.4f)) {
            Box(Modifier.align(Alignment.CenterStart)) { clock() }
            Box(Modifier.align(Alignment.Center)) { commands() }
            Box(Modifier.align(Alignment.CenterEnd)) { temperature() }
        }
    }
}

@Composable
internal fun TopBarPlanet(resource: Int, height: Dp) {
    val image = ImageBitmap.imageResource(resource)
    Image(image, contentDescription = null, modifier = Modifier.size(height * image.width / image.height, height))
}

@Composable
internal fun IllustratedText(text: String, size: TextUnit, color: Color, weight: FontWeight, modifier: Modifier = Modifier) {
    val tokens = LocalHomeTokens.current
    Text(
        text,
        color = color,
        fontSize = size,
        lineHeight = size,
        fontWeight = weight,
        fontFamily = tokens.numberFamily,
        maxLines = 1,
        style = TightTextStyle,
        modifier = modifier.capHeightBounds(size),
    )
}

@Composable
private fun IllustratedFooter(
    style: IllustratedStyle,
    state: HomeState,
    actions: HomeActions,
    h: Dp,
    modifier: Modifier,
) {
    val labelSize = (h.value * .024f).sp
    val valueSize = (h.value * .044f).sp
    val unitSize = (h.value * .028f).sp
    // On the Moon the bar lets a hint of the ground behind it show through.
    val moon = style == IllustratedStyle.MOON
    Box(modifier.illustratedPanel(style, radiusFraction = if (moon) .5f else .3f, fillAlpha = if (moon) MOON_FOOTER_ALPHA else 1f)) {
        BlockStrip(
            state = state,
            actions = actions,
            pinned = emptySet(),
            axis = ReorderAxis.HORIZONTAL,
            labelSize = labelSize,
            valueSize = valueSize,
            unitSize = unitSize,
            modifier = Modifier.fillMaxSize().padding(horizontal = h * .03f, vertical = 6.dp),
            itemWeight = { item -> if (item == FooterBlockItem.FUEL) 1.9f else FooterWeights.getValue(item) },
            labelGap = 9.dp,
        ) { item ->
            when (item) {
                FooterBlockItem.WITNESSES -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WitnessGroup(state, scale = .9f, arrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth())
                }
                FooterBlockItem.FUEL -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SegmentedFuel(state, h, valueSize)
                }
                else -> DefaultBlockContent(item, state, actions, labelSize, valueSize, unitSize, 9.dp)
            }
        }
    }
}

/** Pump, segmented level and litres on one line. */
@Composable
private fun SegmentedFuel(state: HomeState, h: Dp, valueSize: TextUnit) {
    val tokens = LocalHomeTokens.current
    val fuel = state.fuel.value
    val segments = 10
    val active = (fuelFraction(fuel) * segments).let { kotlin.math.round(it).toInt() }.coerceIn(if (fuel > 0) 1 else 0, segments)
    val tone = fuelSegmentTone(fuelSegments(fuel))
    val textColor = fuelContentColor(tone, tokens)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FuelPumpIcon(textColor, Modifier.size(h * .052f))
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.width(h * .19f).height(h * .03f)) {
            val gapPx = 3.dp.toPx()
            val segmentWidth = (size.width - gapPx * (segments - 1)) / segments
            repeat(segments) { index ->
                drawRoundRect(
                    fuelSegmentColor(index < active, tone, tokens),
                    Offset(index * (segmentWidth + gapPx), 0f),
                    Size(segmentWidth, size.height),
                    CornerRadius(3.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        IllustratedText(fuel.toString(), valueSize, textColor, FontWeight.Black)
    }
}

/** Background art that sits behind the instruments and the map window. */
@Composable
private fun IllustratedBackdrop(
    style: IllustratedStyle,
    modifier: Modifier,
    middleTop: Dp,
    middleHeight: Dp,
    left: Footprint,
    right: Footprint,
    mapWindow: Rect,
    mapCorner: Dp,
    topHeight: Dp,
    footerHeight: Dp,
    margin: Dp,
) {
    val palette = style.palette
    val art = when (style) {
        IllustratedStyle.SUBMARINE -> listOf(R.drawable.illustration_sub_seaweed)
        IllustratedStyle.MOON -> listOf(R.drawable.illustration_moon_ground)
    }.map { ImageBitmap.imageResource(it) }
    Canvas(
        modifier.drawWithCache {
            val sky = Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom))
            val bottom = (middleTop + middleHeight).toPx()
            val mid = middleHeight.toPx()
            // Map window and instruments, in pixels: nothing may peek out from behind them.
            val px = density
            val covers = listOf(
                Rect(mapWindow.left * px, mapWindow.top * px, mapWindow.right * px, mapWindow.bottom * px) to mapCorner.toPx(),
            ) + listOf(left, right).map { Rect(it.left.toPx(), it.top.toPx(), it.right.toPx(), (it.top + it.height).toPx()) to it.radius.toPx() }
            val hidden = { p: Offset, r: Float -> covers.any { (rect, radius) -> roundRectDistance(p, rect, radius) < r } }
            // Behind the clear bars the bubbles stay faint so the values read cleanly.
            val barTop = margin.toPx() + topHeight.toPx()
            val barBottom = size.height - margin.toPx() - footerHeight.toPx()
            val fade = { p: Offset -> if (p.y < barTop || p.y > barBottom) .45f else 1f }
            val seaweedLeft = left.left.toPx()
            onDrawBehind {
                drawRect(sky)
                when (style) {
                    IllustratedStyle.SUBMARINE -> {
                        drawBubbles(seed = 11, count = 90, color = Color(0xFFBFF3F0), hidden = hidden, fade = fade)
                        // Seaweed on its rock in the outer bottom corners, rising behind the
                        // portholes; the rock rests just above the value bar, never cut.
                        val seaweed = art[0]
                        val width = mid * SEAWEED_WIDTH
                        val base = seaweed.rectAboveBase(seaweedLeft + width / 2f, bottom, width)
                        drawArt(seaweed, base)
                        scale(-1f, 1f, pivot = Offset(size.width / 2f, 0f)) { drawArt(seaweed, base) }
                    }
                    IllustratedStyle.MOON -> {
                        drawStarField(seed = 3, count = 70, dot = Color.White, sparkle = Color.White, star = Color(0xFFFFD43B))
                        val ground = art[0]
                        // Lunar ground resting on the bottom edge of the screen, partly seen through the
                        // value bar. Each half of the screen holds one copy, the left one mirrored, so
                        // they meet seamlessly in the middle and the planet hill sits at both edges.
                        val half = size.width / 2f
                        val rightGround = ground.rectAboveBase(half + half / 2f, size.height, half)
                        drawArt(ground, rightGround)
                        scale(-1f, 1f, pivot = Offset(half, 0f)) { drawArt(ground, rightGround) }
                    }
                }
            }
        },
    ) { }
}
