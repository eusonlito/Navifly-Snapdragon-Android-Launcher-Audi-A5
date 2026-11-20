package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.R
import com.lito.a5launcher.ui.components.DialType
import com.lito.a5launcher.ui.components.SPEED_DIAL_MAX_KMH
import com.lito.a5launcher.ui.components.formatDialValue

import kotlin.math.sin

private val CoarseSpeedScale = GaugeScale(maxValue = SPEED_DIAL_MAX_KMH, labelStep = 40, minorPerLabel = 4)
private val FineSpeedScale = GaugeScale(maxValue = SPEED_DIAL_MAX_KMH, labelStep = 20, minorPerLabel = 2)

/*
 * Openings of the framed illustrations, measured on the images as fractions
 * of their width and height. The scales are drawn inside them, so the values
 * stay centred on the artwork.
 */

/** Radius of the round porthole's opening, as a fraction of its width. */
private const val PORTHOLE_OPENING_RADIUS = .382f

/** Opening of the tall porthole. */
private val TallPortholeOpening = Rect(.1133f, .0682f, .8783f, .9298f)

/** Proportions of the tall porthole (width / height). */
internal const val TALL_PORTHOLE_ASPECT = .66f

private fun Rect.scaled(width: Float, height: Float) =
    Rect(left * width, top * height, right * width, bottom * height)

/** Speed dial of the illustrated themes, [size] wide and tall. */
@Composable
internal fun IllustratedSpeedInstrument(style: IllustratedStyle, state: HomeState, size: Dp) {
    val motion = LocalTripArt.current
    val palette = style.palette
    val fraction = animatedGaugeFraction(state.speed, SPEED_DIAL_MAX_KMH, rpm = false)
    val art = when (style) {
        IllustratedStyle.SUBMARINE -> listOf(
            R.drawable.illustration_sub_porthole,
            R.drawable.illustration_sub_seabase,
            R.drawable.illustration_sub_fish,
        )
        IllustratedStyle.MOON -> listOf(R.drawable.illustration_moon_astronaut, R.drawable.illustration_moon_rocket)
    }.map { ImageBitmap.imageResource(it) }
    // Inside the porthole the scale fits its opening; the Moon dial is drawn whole.
    val spec = when (style) {
        IllustratedStyle.MOON -> ArcGaugeSpec(
            FineSpeedScale, 150f, 240f, radius = .72f, width = .07f, labelRadius = .56f, labelSize = .085f,
            ticksOutside = true, litLabels = true,
        )
        else -> ArcGaugeSpec(
            CoarseSpeedScale, 150f, 240f, radius = .66f, width = .055f, labelRadius = .45f, labelSize = .10f,
        )
    }
    Box(Modifier.size(size)) {
        Canvas(Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress)) {
            val d = this.size.width
            when (style) {
                IllustratedStyle.SUBMARINE -> {
                    val opening = d * (PORTHOLE_OPENING_RADIUS + .01f)
                    drawCircle(palette.dialFace, opening)
                    clipPath(Path().apply { addOval(Rect(center, opening)) }) {
                        val base = art[1].rectAboveBase(d * .5f, d * .9f, d * .5f)
                        drawArt(art[1], base, alpha = .9f)
                        drawShoal(art[1], base, motion[TripGesture.SHOAL])
                    }
                    drawArt(art[0], Rect(0f, 0f, d, d))
                }
                IllustratedStyle.MOON -> drawNeonDial(palette)
            }
        }
        IllustratedArcGauge(fraction, state.startupProgress, spec, palette, Modifier.fillMaxSize())
        DialReading(
            value = { formatDialValue(state.speed.value, DialType.SPEED) },
            unit = if (style == IllustratedStyle.MOON) "KM/H" else "km/h",
            valueSize = (size.value * if (style == IllustratedStyle.MOON) .18f else .17f).sp,
            unitSize = (size.value * .055f).sp,
            unitColor = palette.unit,
            centerY = size * if (style == IllustratedStyle.MOON) .44f else .5f,
        )
        Canvas(Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress)) {
            val d = this.size.width
            when (style) {
                IllustratedStyle.SUBMARINE -> drawSwimmingFish(art[2], art[2].rectCentered(d * .09f, d * .07f, d * .2f), motion[TripGesture.FISH])
                IllustratedStyle.MOON -> {
                    val hop = artWave(motion[TripGesture.ASTRONAUT])
                    val astronaut = art[0].rectAboveBase(d * (.5f + .007f * hop), d * (.99f - .031f * hop), d * .25f)
                    rotate(-1.4f * hop, astronaut.center) { drawArt(art[0], astronaut) }
                    drawArt(art[1], art[1].rectAboveBase(d * .05f, d * .99f, d * .13f))
                }
            }
        }
    }
}

/** RPM instrument with the gear, [width] × [height]. */
@Composable
internal fun IllustratedRpmInstrument(style: IllustratedStyle, state: HomeState, width: Dp, height: Dp) {
    val motion = LocalTripArt.current
    val palette = style.palette
    val fraction = animatedGaugeFraction(state.rpm, RPM_SCALE_MAX, rpm = true)
    when (style) {
        IllustratedStyle.MOON -> Box(Modifier.size(width, height)) {
            val ufo = ImageBitmap.imageResource(R.drawable.illustration_moon_alien_ufo)
            val robot = ImageBitmap.imageResource(R.drawable.illustration_moon_robot)
            Canvas(Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress)) { drawNeonDial(palette) }
            IllustratedArcGauge(
                fraction,
                state.startupProgress,
                ArcGaugeSpec(
                    RpmGaugeScale, 150f, 240f, radius = .72f, width = .07f, labelRadius = .55f, labelSize = .11f,
                    ticksOutside = true, redZoneFrom = RPM_RED_ZONE_FRACTION, litLabels = true,
                ),
                palette,
                Modifier.fillMaxSize(),
            )
            DialReading(
                value = { formatDialValue(state.rpm.value, DialType.RPM) },
                unit = "x1000",
                valueSize = (width.value * .16f).sp,
                unitSize = (width.value * .05f).sp,
                unitColor = palette.unit,
                centerY = height * .40f,
            )
            Canvas(Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress)) {
                val d = size.width
                val p = motion[TripGesture.UFO]
                val bob = artWave(p)
                val ship = ufo.rectCentered(d * (.5f + .012f * bob * sin(p * 6f)), d * (.68f - .015f * bob), d * .28f)
                rotate(4.9f * bob * sin(p * 9f), ship.center) { drawArt(ufo, ship) }
                val base = robot.rectAboveBase(d * .93f, d, d * .19f)
                drawArt(robot, base)
                drawRobotHeart(robot, base, motion[TripGesture.HEART])
                val badge = Offset(d * .5f, d * .89f)
                drawCircle(palette.panel, d * .07f, badge)
                drawGlow(Path().apply { addOval(Rect(badge, d * .07f)) }, palette.frame)
            }
            GearText(state, (width.value * .085f).sp, palette.content, Modifier.align(Alignment.TopCenter), height * .89f)
        }
        IllustratedStyle.SUBMARINE -> Box(Modifier.size(width, height)) {
            val porthole = ImageBitmap.imageResource(R.drawable.illustration_sub_porthole_tall)
            val hut = ImageBitmap.imageResource(R.drawable.illustration_sub_hut)
            val opening = TallPortholeOpening
            Canvas(Modifier.fillMaxSize().startupOutlineAlpha(state.startupProgress)) {
                val w = size.width
                val h = size.height
                val glass = opening.scaled(w, h).inflate(w * .01f)
                val glassShape = RoundRect(glass, CornerRadius(glass.width / 2f))
                drawPath(Path().apply { addRoundRect(glassShape) }, palette.dialFace)
                clipPath(Path().apply { addRoundRect(glassShape) }) {
                    drawPeriscope(hut, hut.rectAboveBase(w * .5f, h * .955f, w * .42f), motion[TripGesture.PERISCOPE])
                }
                drawArt(porthole, Rect(0f, 0f, w, h))
                // Gear window.
                val gear = Rect(w * .30f, h * .15f, w * .70f, h * .27f)
                drawRoundRect(palette.panel, gear.topLeft, gear.size, CornerRadius(gear.height * .3f))
                drawRoundRect(Color(0xFFBFD7D3), gear.topLeft, gear.size, CornerRadius(gear.height * .3f), style = Stroke(w * .012f))
            }
            Box(
                Modifier
                    .offset(x = width * .14f, y = height * .28f)
                    .size(width * .72f, height * .28f),
            ) {
                IllustratedArcGauge(
                    fraction,
                    state.startupProgress,
                    ArcGaugeSpec(
                        RpmGaugeScale, 180f, 180f, radius = .82f, width = .08f, labelRadius = .55f, labelSize = .17f,
                        redZoneFrom = RPM_RED_ZONE_FRACTION, centerY = .88f,
                    ),
                    palette,
                    Modifier.fillMaxSize(),
                )
            }
            DialReading(
                value = { formatDialValue(state.rpm.value, DialType.RPM) },
                unit = "x1000",
                valueSize = (width.value * .2f).sp,
                unitSize = (width.value * .075f).sp,
                unitColor = palette.unit,
                centerY = height * .60f,
            )
            GearText(state, (width.value * .15f).sp, palette.content, Modifier.align(Alignment.TopCenter), height * .21f)
        }
    }
}

/** Value over its unit, centred horizontally with the value's middle at [centerY]. */
@Composable
private fun BoxScope.DialReading(
    value: () -> String,
    unit: String,
    valueSize: TextUnit,
    unitSize: TextUnit,
    unitColor: Color,
    centerY: Dp,
) {
    val tokens = LocalHomeTokens.current
    val capHeight = with(LocalDensity.current) { valueSize.toDp() * CAP_HEIGHT_RATIO }
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = centerY - capHeight / 2f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IllustratedText(value(), valueSize, tokens.content, FontWeight.Black)
        Spacer(Modifier.height(capHeight * .3f))
        IllustratedText(unit, unitSize, unitColor, FontWeight.Bold)
    }
}

/** Gear figure centred on [centerY]. */
@Composable
private fun GearText(state: HomeState, textSize: TextUnit, color: Color, modifier: Modifier, centerY: Dp) {
    val capHeight = with(LocalDensity.current) { textSize.toDp() * CAP_HEIGHT_RATIO }
    Box(modifier.offset(y = centerY - capHeight / 2f)) {
        IllustratedText(state.gear.value, textSize, color, FontWeight.Black)
    }
}

/** Neon dial of the Moon theme: dark rim, soft edge and a lighter inner disc, all concentric. */
/** Radius of the Moon dial disc, as a fraction of its footprint width. */
internal const val NEON_DIAL_RADIUS = .485f

private fun DrawScope.drawNeonDial(palette: IllustratedPalette) {
    val d = size.width
    val c = center
    val outer = d * NEON_DIAL_RADIUS
    drawCircle(Color(0xFF0C1246), outer, c)
    drawGlow(Path().apply { addOval(Rect(c, outer)) }, palette.frame)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1C2A86), palette.dialFace), c, outer), d * .44f, c)
    drawCircle(Color(0xFF1B2780), d * .22f, c)
    drawCircle(palette.frame.copy(alpha = .22f), d * .22f, c, style = Stroke(d * .004f))
}
