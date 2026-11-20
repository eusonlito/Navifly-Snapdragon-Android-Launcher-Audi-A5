package com.lito.a5launcher.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.ui.components.MapThemeColors
import com.lito.a5launcher.ui.components.SPEED_DIAL_MAX_KMH
import com.lito.a5launcher.ui.components.oemStartupPresentation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Pearl = Color(0xFFE9EEF9)
private val Sapphire = Color(0xFF0765E8)
private val SkyBlue = Color(0xFF36C8F2)
private val Ink = Color(0xFF111927)

internal fun blueHomeTokens(dark: Boolean): HomeTokens = ClassicHomeTokens.copy(
    background = Pearl,
    chrome = Pearl,
    content = Ink,
    label = Color(0xFF3A4D64),
    accent = Sapphire,
    gaugeFill = listOf(SkyBlue, Sapphire),
    gaugeTrack = Color(0xFFD0DAEC),
    redZone = Color(0xFFEF3843),
    warning = Color(0xFFAE151C),
    divider = Color(0xFFABBAD0),
    dragPlate = Color(0xFFCADDFB),
    witnessInactive = Color(0xFF637991),
    witnessLights = Color(0xFF147829),
    fuelInactive = Color(0xFFCED8EB),
    numberFamily = FontFamily.SansSerif,
    numberWeight = FontWeight.Bold,
    labelFamily = FontFamily.SansSerif,
    // MapLibre's style and colour mode still come from the user's map settings.
    map = MapThemeColors.Classic.copy(
        loadingBackground = if (dark) Color(0xFF151A1E) else Pearl,
        attribution = if (dark) Color.White.copy(alpha = .42f) else Color.Black.copy(alpha = .47f),
    ),
)

internal fun Modifier.blueBackdrop() = drawWithCache {
    val wash = Brush.verticalGradient(listOf(Color(0xFFDDE5F2), Pearl, Color(0xFFE9F1FA)))
    onDrawBehind { drawRect(wash) }
}

internal fun Modifier.blueRailSurface() = drawWithCache {
    val surface = Brush.horizontalGradient(listOf(Color(0xFFF5F8FD), Color(0xFFE4EAF5)))
    onDrawBehind { drawRect(surface) }
}

internal fun Modifier.blueLightEdge(vertical: Boolean) = drawWithCache {
    val inset = 1.dp.toPx()
    val start = if (vertical) Offset(size.width - inset, 0f) else Offset(0f, size.height - inset)
    val end = Offset(size.width - inset, size.height - inset)
    onDrawBehind { drawLine(Color.White.copy(alpha = .66f), start, end, inset) }
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

internal fun Modifier.blueMapRim(corner: Dp) = drawWithCache {
    val width = 1.6.dp.toPx()
    val radius = CornerRadius(corner.toPx())
    val rim = Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE8F9), Color(0xFFBAC8DE)))
    onDrawBehind {
        drawRoundRect(rim, cornerRadius = radius, alpha = .18f, style = Stroke(width * 3f))
        drawRoundRect(rim, cornerRadius = radius, style = Stroke(width))
    }
}

@Composable
internal fun BlueSpeedometer(state: HomeState, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    val speed = animatedGaugeFraction(state.speed, SPEED_DIAL_MAX_KMH, rpm = false)
    val centerY = .465f
    BoxWithConstraints(modifier) {
        val diameter = minOf(maxWidth * .94f, maxHeight * .96f)
        val valueSize = (diameter.value * .36f).sp
        val unitSize = valueSize * .265f
        Canvas(
            Modifier.fillMaxSize().drawWithCache {
                val radius = diameter.toPx() / 2f
                val center = Offset(size.width / 2f, size.height * centerY)
                val start = 145f
                val sweep = 250f
                val arcTopLeft = center - Offset(radius * .93f, radius * .93f)
                val arcSize = Size(radius * 1.86f, radius * 1.86f)
                val trackWidth = radius * .116f
                val face = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .84f), Color(0xFFEDF2FC), Color(0xFFE0E8F6)),
                    center, radius,
                )
                val track = Brush.verticalGradient(
                    listOf(Color(0xFFCDD6E8), Color(0xFFE4EBF6), Color(0xFFEBF1FA)),
                    center.y - radius, center.y + radius,
                )
                val fill = Brush.verticalGradient(
                    listOf(Sapphire, Color(0xFF099CF5), Color(0xFF61D5FC), Color(0xFFD7EAFC)),
                    center.y - radius, center.y + radius * .73f,
                )
                val painter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = radius * .114f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    color = tokens.content.toArgb()
                }
                val labelBaseline = -(painter.ascent() + painter.descent()) / 2f
                onDrawBehind {
                    val startup = oemStartupPresentation(state.startupProgress.value)
                    val outline = startup.dialOutlineAlpha
                    val shown = speed.value * startup.depthProgress
                    drawCircle(face, radius * .88f, center, alpha = outline)
                    drawArc(track, start, sweep, false, arcTopLeft, arcSize,
                        alpha = outline, style = Stroke(trackWidth))
                    if (shown > 0f) {
                        drawArc(fill, start, sweep * shown, false, arcTopLeft, arcSize,
                            style = Stroke(trackWidth, cap = StrokeCap.Round))
                    }
                    painter.alpha = (255 * startup.scaleProgress).toInt()
                    for (i in 0..16) {
                        val major = i % 4 == 0
                        val at = start + sweep * i / 16f
                        drawLine(
                            tokens.content.copy(alpha = startup.scaleProgress * if (major) .89f else .33f),
                            speedPoint(center, radius * .88f, at),
                            speedPoint(center, radius * if (major) .798f else .85f, at),
                            if (major) radius * .012f else radius * .005f,
                        )
                        if (major) {
                            val p = speedPoint(center, radius * .69f, at)
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
                .offset(y = maxHeight * (centerY - .175f))
                .startupContentAlpha(state),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(diameter * .037f),
        ) {
            BlueText(state.speed.value.toString(), valueSize, tokens.content, FontWeight.Black)
            BlueText("km/h", unitSize, tokens.content, FontWeight.Medium)
        }
    }
}

private fun speedPoint(center: Offset, radius: Float, degrees: Float): Offset {
    val a = degrees * PI.toFloat() / 180f
    return center + Offset(cos(a) * radius, sin(a) * radius)
}

@Composable
internal fun BlueGear(state: HomeState, modifier: Modifier) {
    BoxWithConstraints(
        modifier.startupContentAlpha(state).drawWithCache {
            val w = size.width
            val h = size.height
            fun shape(inset: Float, lift: Float): Path = Path().apply {
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

            val bevel = h * .024f
            val face = shape(bevel, 0f)
            val edge = shape(0f, bevel * 2.1f)
            val highlight = shape(bevel * 2.15f, 0f)
            val body = Brush.verticalGradient(
                .08f to Color(0xFFB1C9EC), .30f to Color(0xFFDFEBFD),
                .57f to Color(0xFFA6C6F3), .84f to Color(0xFF82AFE8), 1f to Color(0xFFE4EEFD),
            )
            val reflection = Brush.radialGradient(
                listOf(Color.White.copy(alpha = .94f), Color(0xFFE5F1FF).copy(alpha = .72f), Color.Transparent),
                center = Offset(w * .40f, h * .28f), radius = w * .46f,
            )
            val rim = Brush.verticalGradient(
                listOf(Color.White, Color(0xFF7CB6F4), Color(0xFF1764C5), SkyBlue),
            )
            val lip = Brush.verticalGradient(
                .60f to Color(0xFF1150A3), .88f to Sapphire, 1f to Color(0xFF39B7F7),
            )
            val shine = Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0xFF8EE8FF), Color.White, Color.Transparent),
                startX = w * .13f, endX = w * .87f,
            )
            onDrawBehind {
                drawPath(edge, Sapphire, alpha = .08f, style = Stroke(bevel * 5f, join = StrokeJoin.Round))
                drawPath(edge, Sapphire, alpha = .13f, style = Stroke(bevel * 2.7f, join = StrokeJoin.Round))
                drawPath(edge, lip)
                drawPath(face, body)
                clipPath(face) { drawRect(reflection) }
                drawPath(face, rim, style = Stroke(bevel * .94f, join = StrokeJoin.Round))
                drawPath(highlight, Color.White, alpha = .74f, style = Stroke(bevel * .36f))
                val y = h * .884f
                drawLine(shine, Offset(w * .13f, y), Offset(w * .87f, y), bevel * .58f)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val size = minOf(maxWidth * .61f, maxHeight * .75f).value.sp
        BlueText(state.gear.value, size, Color(0xFF064CA9), FontWeight.Black)
    }
}
