package com.lito.a5launcher.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.R
import com.lito.a5launcher.ui.components.MapThemeColors
import com.lito.a5launcher.ui.components.SPEED_DIAL_MAX_KMH
import com.lito.a5launcher.ui.components.oemStartupPresentation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Midnight = Color(0xFF041421)
private val CyanIce = Color(0xFF68F5FF)
private val CyanLight = Color(0xFF04DBFF)
private val CyanDepth = Color(0xFF0878BA)
private val Pearl = Color(0xFFE9EEF9)
private val Sapphire = Color(0xFF0765E8)
private val SkyBlue = Color(0xFF36C8F2)
private val Ink = Color(0xFF111927)

internal fun blueHomeTokens(cyan: Boolean, dark: Boolean): HomeTokens = ClassicHomeTokens.copy(
    background = if (cyan) Midnight else Pearl,
    chrome = if (cyan) Midnight else Pearl,
    content = if (cyan) Color(0xFFF3FAFF) else Ink,
    label = if (cyan) Color(0xFFD0E2EE) else Color(0xFF3A4D64),
    accent = if (cyan) CyanLight else Sapphire,
    gaugeFill = if (cyan) listOf(CyanIce, CyanLight) else listOf(SkyBlue, Sapphire),
    gaugeTrack = if (cyan) Color(0xFF1C374B) else Color(0xFFD0DAEC),
    redZone = Color(0xFFEF3843),
    warning = if (cyan) Color(0xFFFF7C76) else Color(0xFFAE151C),
    divider = if (cyan) Color(0xFF265065) else Color(0xFFABBAD0),
    dragPlate = if (cyan) Color(0xFF164566) else Color(0xFFCADDFB),
    witnessInactive = if (cyan) Color(0xFF63879D) else Color(0xFF637991),
    witnessLights = if (cyan) Color(0xFF4ADE80) else Color(0xFF147829),
    fuelInactive = if (cyan) Color(0xFF244253) else Color(0xFFCED8EB),
    numberFamily = FontFamily.SansSerif,
    numberWeight = FontWeight.Bold,
    labelFamily = FontFamily.SansSerif,
    // MapLibre's style and colour mode still come from the user's map settings.
    map = MapThemeColors.Classic.copy(
        loadingBackground = if (dark) Color(0xFF151A1E) else Pearl,
        attribution = if (dark) Color.White.copy(alpha = .42f) else Color.Black.copy(alpha = .47f),
    ),
)

internal fun Modifier.blueBackdrop(cyan: Boolean) = drawWithCache {
    val wash = if (cyan) {
        Brush.radialGradient(
            listOf(Color(0xFF0B263A), Midnight, Color(0xFF020C15)),
            center = Offset(size.width * .23f, size.height * .37f),
            radius = size.width * .72f,
        )
    } else Brush.verticalGradient(listOf(Color(0xFFDDE5F2), Pearl, Color(0xFFE9F1FA)))
    onDrawBehind { drawRect(wash) }
}

internal fun Modifier.blueRailSurface(cyan: Boolean) = drawWithCache {
    val surface = if (cyan) {
        Brush.horizontalGradient(listOf(Color(0xFF020C16), Color(0xFF081C2C)))
    } else Brush.horizontalGradient(listOf(Color(0xFFF5F8FD), Color(0xFFE4EAF5)))
    onDrawBehind { drawRect(surface) }
}

internal fun Modifier.blueLightEdge(cyan: Boolean, vertical: Boolean) = drawWithCache {
    val brush = if (vertical) {
        Brush.verticalGradient(
            listOf(Color.Transparent, CyanDepth, CyanIce, CyanDepth, Color.Transparent),
        )
    } else Brush.horizontalGradient(
        listOf(Color.Transparent, CyanDepth, CyanIce, CyanDepth, Color.Transparent),
    )
    val start = if (vertical) Offset(size.width - 1.dp.toPx(), 0f) else Offset(0f, size.height - 1.dp.toPx())
    val end = Offset(size.width - 1.dp.toPx(), size.height - 1.dp.toPx())
    onDrawBehind {
        if (cyan) {
            drawLine(brush, start, end, 7.dp.toPx(), alpha = .10f)
            drawLine(brush, start, end, 3.dp.toPx(), alpha = .21f)
            drawLine(brush, start, end, .9.dp.toPx())
        } else {
            drawLine(Color.White.copy(alpha = .66f), start, end, 1.dp.toPx())
        }
    }
}

internal fun Modifier.blueCyanCard(corner: Dp) = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val glass = Brush.verticalGradient(listOf(Color(0xFF103147), Color(0xFF071B2A), Midnight))
    val frame = Brush.linearGradient(listOf(Color(0xFF32728F), CyanDepth, Color(0xFF123344)))
    onDrawBehind {
        drawRoundRect(glass, cornerRadius = radius)
        drawRoundRect(frame, cornerRadius = radius, style = Stroke(.65.dp.toPx()))
    }
}

internal fun Modifier.bluePearlPanel(corner: Dp) = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val glass = Brush.verticalGradient(listOf(Color(0xFFF7F9FE), Color(0xFFF0F4FC), Color(0xFFDDE6F5)))
    val rim = Brush.verticalGradient(listOf(Color.White, Color(0xFFE9EFF9), Color(0xFFBFCCE2)))
    onDrawBehind {
        drawRoundRect(Color(0xFF9DAFCB), topLeft = Offset(0f, 2.dp.toPx()), cornerRadius = radius, alpha = .18f)
        drawRoundRect(glass, cornerRadius = radius)
        drawRoundRect(rim, cornerRadius = radius, style = Stroke(.9.dp.toPx()))
    }
}

internal fun Modifier.blueMapRim(cyan: Boolean, corner: Dp) = drawWithCache {
    val width = if (cyan) .8.dp.toPx() else 1.6.dp.toPx()
    val radius = CornerRadius(corner.toPx())
    val rim = if (cyan) {
        Brush.linearGradient(listOf(CyanIce, CyanDepth, CyanLight, CyanDepth))
    } else Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE8F9), Color(0xFFBAC8DE)))
    onDrawBehind {
        drawRoundRect(rim, cornerRadius = radius, alpha = .18f, style = Stroke(width * 3f))
        drawRoundRect(rim, cornerRadius = radius, style = Stroke(width))
    }
}

@Composable
internal fun BlueSpeedometer(state: HomeState, cyan: Boolean, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    val speed = animatedGaugeFraction(state.speed, SPEED_DIAL_MAX_KMH, rpm = false)
    val road = if (cyan) ImageBitmap.imageResource(R.drawable.theme_cyan_road) else null
    val centerY = if (cyan) .466f else .465f
    BoxWithConstraints(modifier) {
        val diameter = minOf(maxWidth * .94f, maxHeight * .99f)
        val valueSize = (diameter.value * if (cyan) .365f else .36f).sp
        val unitSize = valueSize * .265f
        if (road != null) CyanRoad(
            road, state,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(minOf(maxHeight * .34f, maxWidth * road.height / road.width)),
        )
        Canvas(
            Modifier.fillMaxSize().drawWithCache {
                val radius = diameter.toPx() / 2f
                val center = Offset(size.width / 2f, size.height * centerY)
                val start = 145f
                val sweep = 250f
                val arcTopLeft = center - Offset(radius * .93f, radius * .93f)
                val arcSize = Size(radius * 1.86f, radius * 1.86f)
                val trackWidth = radius * if (cyan) .105f else .116f
                val face = if (cyan) {
                    Brush.radialGradient(
                        listOf(Color(0xFF0C2639), Color(0xFF041725), Color.Transparent),
                        center, radius,
                    )
                } else Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .84f), Color(0xFFEDF2FC), Color(0xFFE0E8F6)),
                    center, radius,
                )
                val track = Brush.verticalGradient(
                    if (cyan) listOf(Color(0xFF225470), Color(0xFF143042), Color(0xFF091E2C))
                    else listOf(Color(0xFFCDD6E8), Color(0xFFE4EBF6), Color(0xFFEBF1FA)),
                    center.y - radius, center.y + radius,
                )
                val fill = Brush.verticalGradient(
                    if (cyan) listOf(CyanIce, CyanLight, Color(0xFF04A8EA))
                    else listOf(Sapphire, Color(0xFF099CF5), Color(0xFF61D5FC), Color(0xFFD7EAFC)),
                    center.y - radius,
                    center.y + radius * .73f,
                )
                val rimGlow = Brush.radialGradient(
                    .84f to Color.Transparent, .935f to CyanLight.copy(alpha = .27f),
                    .975f to CyanDepth.copy(alpha = .22f), 1f to Color.Transparent,
                    center = center, radius = radius * 1.08f,
                )
                val outerLight = Brush.radialGradient(
                    listOf(CyanIce, CyanLight, CyanDepth.copy(alpha = .3f)),
                    Offset(center.x - radius * .58f, center.y - radius * .78f),
                    radius * 1.65f,
                )
                val painter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = radius * .114f
                    typeface = Typeface.create("sans-serif-medium", if (cyan) Typeface.ITALIC else Typeface.NORMAL)
                    color = tokens.content.toArgb()
                }
                val labelBaseline = -(painter.ascent() + painter.descent()) / 2f
                onDrawBehind {
                    val startup = oemStartupPresentation(state.startupProgress.value)
                    val outline = startup.dialOutlineAlpha
                    val shown = speed.value * startup.depthProgress
                    drawCircle(face, radius * .88f, center, alpha = outline)
                    if (cyan) {
                        drawArc(rimGlow, start - 4, sweep + 8, false, arcTopLeft, arcSize,
                            alpha = outline, style = Stroke(radius * .33f))
                        drawArc(outerLight, start, sweep, false, arcTopLeft, arcSize,
                            alpha = outline * .23f, style = Stroke(trackWidth * 1.43f))
                    }
                    drawArc(track, start, sweep, false, arcTopLeft, arcSize,
                        alpha = outline, style = Stroke(trackWidth))
                    if (shown > 0f) {
                        if (cyan) drawArc(fill, start, sweep * shown, false, arcTopLeft, arcSize,
                            alpha = .21f, style = Stroke(trackWidth * 1.55f))
                        drawArc(fill, start, sweep * shown, false, arcTopLeft, arcSize,
                            style = Stroke(trackWidth, cap = if (cyan) StrokeCap.Butt else StrokeCap.Round))
                    }
                    if (cyan) {
                        val rimRadius = radius * .996f
                        drawArc(outerLight, start - 2, sweep + 4, false,
                            center - Offset(rimRadius, rimRadius), Size(rimRadius * 2, rimRadius * 2),
                            alpha = outline, style = Stroke(radius * .008f))
                        drawArc(Color(0xFFD9FFFF), start + 106, 23f, false,
                            center - Offset(rimRadius, rimRadius), Size(rimRadius * 2, rimRadius * 2),
                            alpha = outline * .82f, style = Stroke(radius * .012f, cap = StrokeCap.Round))
                    }
                    for (i in 0..16) {
                        val major = i % 4 == 0
                        val at = start + sweep * i / 16f
                        val tint = if (cyan && i / 16f <= shown) CyanIce else tokens.content
                        drawLine(
                            tint.copy(alpha = startup.scaleProgress * if (major) .89f else .33f),
                            speedPoint(center, radius * .88f, at),
                            speedPoint(center, radius * if (major) .798f else .85f, at),
                            if (major) radius * .012f else radius * .005f,
                        )
                        if (major) {
                            val p = speedPoint(center, radius * .69f, at)
                            painter.alpha = (255 * startup.scaleProgress).toInt()
                            drawContext.canvas.nativeCanvas.drawText(
                                (i * 15).toString(), p.x, p.y + labelBaseline, painter,
                            )
                        }
                    }
                }
            },
        ) { }
        Column(
            Modifier.align(Alignment.TopCenter)
                .offset(y = maxHeight * (centerY - if (cyan) .16f else .175f))
                .startupContentAlpha(state),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(diameter * .037f),
        ) {
            BlueText(state.speed.value.toString(), valueSize, tokens.content, cyan, FontWeight.Black)
            BlueText("km/h", unitSize, tokens.content, cyan, FontWeight.Medium)
        }
    }
}

/** The road is painted on its own static layer so the fade never redraws with speed. */
@Composable
private fun CyanRoad(road: ImageBitmap, state: HomeState, modifier: Modifier) {
    Canvas(
        modifier.startupContentAlpha(state)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val art = road.rectAboveBase(size.width * .5f, size.height, size.width)
                val vertical = Brush.verticalGradient(
                    0f to Color.Transparent, .28f to Color.Black, .89f to Color.Black,
                    1f to Color.Transparent,
                )
                val sides = Brush.horizontalGradient(
                    0f to Color.Transparent, .16f to Color.Black, .84f to Color.Black,
                    1f to Color.Transparent,
                )
                onDrawBehind {
                    drawArt(road, art)
                    drawRect(vertical, blendMode = BlendMode.DstIn)
                    drawRect(sides, blendMode = BlendMode.DstIn)
                }
            },
    ) { }
}

private fun speedPoint(center: Offset, radius: Float, degrees: Float): Offset {
    val a = degrees * PI.toFloat() / 180f
    return center + Offset(cos(a) * radius, sin(a) * radius)
}

@Composable
internal fun BlueGear(state: HomeState, cyan: Boolean, modifier: Modifier) {
    BoxWithConstraints(
        modifier.startupContentAlpha(state).drawWithCache {
            fun shape(inset: Float, lift: Float): Path = Path().apply {
                val w = size.width
                val h = size.height
                if (cyan) {
                    moveTo(w * .28f + inset, h * .10f + inset)
                    lineTo(w * .87f - inset, h * .10f + inset)
                    quadraticTo(w * .985f - inset, h * .10f + inset, w * .965f - inset, h * .235f)
                    lineTo(w * .755f - inset, h * .82f + lift - inset)
                    quadraticTo(w * .731f - inset, h * .90f + lift - inset, w * .661f - inset, h * .90f + lift - inset)
                    lineTo(w * .13f + inset, h * .90f + lift - inset)
                    quadraticTo(w * .012f + inset, h * .90f + lift - inset, w * .043f + inset, h * .76f + lift)
                    close()
                } else {
                    moveTo(w * .25f + inset, h * .10f + inset)
                    lineTo(w * .75f - inset, h * .10f + inset)
                    quadraticTo(w * .81f - inset, h * .10f + inset, w * .84f - inset, h * .215f + inset)
                    lineTo(w * .965f - inset, h * .72f + lift - inset)
                    quadraticTo(w * 1.02f - inset, h * .875f + lift - inset, w * .886f - inset, h * .885f + lift - inset)
                    lineTo(w * .114f + inset, h * .885f + lift - inset)
                    quadraticTo(-w * .02f + inset, h * .875f + lift - inset, w * .035f + inset, h * .72f + lift - inset)
                    lineTo(w * .16f + inset, h * .215f + inset)
                    quadraticTo(w * .19f + inset, h * .10f + inset, w * .25f + inset, h * .10f + inset)
                    close()
                }
            }
            val step = size.minDimension * .023f
            val face = shape(step, 0f)
            val edge = shape(0f, if (cyan) 0f else step * 2f)
            val highlight = if (cyan) null else shape(step * 1.8f, 0f)
            val reflection = Brush.verticalGradient(
                if (cyan) listOf(Color(0xFF092739), Color(0xFF041521), Color(0xFF0A273B))
                else listOf(Color(0xFFCCDDF7), Color.White, Color(0xFFE4EEFC), Color(0xFFB1CAE9)),
            )
            val border = if (cyan) Brush.linearGradient(
                0f to Color.Transparent, .27f to CyanDepth, .48f to CyanIce,
                .68f to CyanLight, 1f to Color(0xFF092030),
                start = Offset(0f, 0f), end = Offset(size.width * .88f, size.height),
            ) else Brush.verticalGradient(
                listOf(Color.White, Color(0xFF91CCFF), Sapphire, SkyBlue),
            )
            val shine = Brush.horizontalGradient(
                listOf(Color.Transparent, if (cyan) CyanLight else Sapphire, CyanIce, Color.Transparent),
            )
            onDrawBehind {
                drawPath(edge, border, alpha = if (cyan) .08f else .15f, style = Stroke(step * 5f, join = StrokeJoin.Round))
                drawPath(edge, border, alpha = if (cyan) .18f else .23f, style = Stroke(step * 2.7f, join = StrokeJoin.Round))
                drawPath(edge, if (cyan) Midnight else Sapphire)
                drawPath(face, reflection)
                drawPath(face, border, style = Stroke(step * if (cyan) .48f else .9f, join = StrokeJoin.Round))
                if (highlight != null) drawPath(highlight, Color.White,
                    alpha = .81f, style = Stroke(step * .35f, join = StrokeJoin.Round))
                val y = size.height * if (cyan) .9f else .897f
                drawLine(shine, Offset(size.width * .14f, y), Offset(size.width * .69f, y), step * .8f)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val size = minOf(maxWidth * .61f, maxHeight * if (cyan) .65f else .75f).value.sp
        BlueText(state.gear.value, size, if (cyan) LocalHomeTokens.current.content else Sapphire, cyan, FontWeight.Black)
    }
}
