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

private val BlueDayTokens = ClassicHomeTokens.copy(
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
)

private val BlueNightTokens = BlueDayTokens.copy(
    background = Color(0xFF0D141F),
    chrome = Color(0xFF111B2B),
    content = Color(0xFFD6E1F0),
    label = Color(0xFFA5B5CC),
    accent = Color(0xFF71AEF4),
    gaugeFill = listOf(Color(0xFF347EBB), Color(0xFF70ABE0)),
    gaugeTrack = Color(0xFF26374D),
    redZone = Color(0xFFD26470),
    warning = Color(0xFFF2888C),
    divider = Color(0xFF354863),
    dragPlate = Color(0xFF263D5A),
    witnessInactive = Color(0xFF667D96),
    witnessLights = Color(0xFF63B879),
    fuelInactive = Color(0xFF32445C),
)

internal fun blueHomeTokens(dark: Boolean, mapDark: Boolean) = (if (dark) BlueNightTokens else BlueDayTokens).copy(
    map = MapThemeColors.Classic.copy(
        loadingBackground = if (mapDark) Color(0xFF151A1E) else Pearl,
        attribution = if (mapDark) Color.White.copy(alpha = .42f) else Color.Black.copy(alpha = .47f),
    ),
)

internal fun Modifier.blueBackdrop(dark: Boolean) = drawWithCache {
    val wash = Brush.verticalGradient(
        if (dark) listOf(Color(0xFF101B2B), BlueNightTokens.background, Color(0xFF111C2A))
        else listOf(Color(0xFFDDE5F2), Pearl, Color(0xFFE9F1FA)),
    )
    onDrawBehind { drawRect(wash) }
}

internal fun Modifier.blueRailSurface(dark: Boolean) = drawWithCache {
    val surface = Brush.horizontalGradient(
        if (dark) listOf(Color(0xFF131E2D), Color(0xFF101927))
        else listOf(Color(0xFFF5F8FD), Color(0xFFE4EAF5)),
    )
    onDrawBehind { drawRect(surface) }
}

internal fun Modifier.blueLightEdge(dark: Boolean, vertical: Boolean) = drawWithCache {
    val inset = 1.dp.toPx()
    val start = if (vertical) Offset(size.width - inset, 0f) else Offset(0f, size.height - inset)
    val end = Offset(size.width - inset, size.height - inset)
    val edge = if (dark) Color(0xFF35475D) else Color.White.copy(alpha = .66f)
    onDrawBehind { drawLine(edge, start, end, inset) }
}

internal fun Modifier.bluePearlPanel(corner: Dp, dark: Boolean) = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val glass = Brush.verticalGradient(
        if (dark) listOf(Color(0xFF1B2B40), Color(0xFF152234), Color(0xFF101B2C))
        else listOf(Color(0xFFF7F9FE), Color(0xFFF0F4FC), Color(0xFFDDE6F5)),
    )
    val rim = Brush.verticalGradient(
        if (dark) listOf(Color(0xFF455B74), Color(0xFF2C3D55), Color(0xFF304763))
        else listOf(Color.White, Color(0xFFE9EFF9), Color(0xFFBFCCE2)),
    )
    val shadow = if (dark) Color.Black else Color(0xFF9DAFCB)
    onDrawBehind {
        drawRoundRect(shadow, topLeft = Offset(0f, 2.dp.toPx()), cornerRadius = radius, alpha = .18f)
        drawRoundRect(glass, cornerRadius = radius)
        drawRoundRect(rim, cornerRadius = radius, style = Stroke(.9.dp.toPx()))
    }
}

internal fun Modifier.blueMapRim(corner: Dp, dark: Boolean) = drawWithCache {
    val width = 1.6.dp.toPx()
    val radius = CornerRadius(corner.toPx())
    val rim = Brush.verticalGradient(
        if (dark) listOf(Color(0xFF4B6381), Color(0xFF304057), Color(0xFF3D5371))
        else listOf(Color.White, Color(0xFFDDE8F9), Color(0xFFBAC8DE)),
    )
    onDrawBehind {
        drawRoundRect(rim, cornerRadius = radius, alpha = .18f, style = Stroke(width * 3f))
        drawRoundRect(rim, cornerRadius = radius, style = Stroke(width))
    }
}

@Composable
internal fun BlueSpeedometer(state: HomeState, dark: Boolean, modifier: Modifier) {
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
                    if (dark) listOf(Color(0xFF263850), Color(0xFF1A283B), Color(0xFF172337))
                    else listOf(Color.White.copy(alpha = .84f), Color(0xFFEDF2FC), Color(0xFFE0E8F6)),
                    center, radius,
                )
                val track = Brush.verticalGradient(
                    if (dark) listOf(Color(0xFF3B526E), Color(0xFF2A3B51), Color(0xFF1D2B3E))
                    else listOf(Color(0xFFCDD6E8), Color(0xFFE4EBF6), Color(0xFFEBF1FA)),
                    center.y - radius, center.y + radius,
                )
                val fill = Brush.verticalGradient(
                    if (dark) listOf(Color(0xFF67A8E9), Color(0xFF508BC8), Color(0xFF3572A4), Color(0xFF2B4C6C))
                    else listOf(Sapphire, Color(0xFF099CF5), Color(0xFF61D5FC), Color(0xFFD7EAFC)),
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
internal fun BlueGear(state: HomeState, dark: Boolean, modifier: Modifier) {
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
            val body = if (dark) Brush.verticalGradient(
                .08f to Color(0xFF243E61), .30f to Color(0xFF2F496C),
                .57f to Color(0xFF254162), .84f to Color(0xFF1D3350), 1f to Color(0xFF375577),
            ) else Brush.verticalGradient(
                .08f to Color(0xFFB1C9EC), .30f to Color(0xFFDFEBFD),
                .57f to Color(0xFFA6C6F3), .84f to Color(0xFF82AFE8), 1f to Color(0xFFE4EEFD),
            )
            val reflection = Brush.radialGradient(
                if (dark) listOf(Color(0xFF83A8D5).copy(alpha = .25f), Color.Transparent)
                else listOf(Color.White.copy(alpha = .94f), Color(0xFFE5F1FF).copy(alpha = .72f), Color.Transparent),
                center = Offset(w * .40f, h * .28f), radius = w * .46f,
            )
            val rim = Brush.verticalGradient(
                if (dark) listOf(Color(0xFF6B8BB3), Color(0xFF37618E), Color(0xFF244E80), Color(0xFF5489C1))
                else listOf(Color.White, Color(0xFF7CB6F4), Color(0xFF1764C5), SkyBlue),
            )
            val lip = if (dark) Brush.verticalGradient(
                .60f to Color(0xFF142942), .88f to Color(0xFF234978), 1f to Color(0xFF4D7EB4),
            ) else Brush.verticalGradient(
                .60f to Color(0xFF1150A3), .88f to Sapphire, 1f to Color(0xFF39B7F7),
            )
            val shine = Brush.horizontalGradient(
                if (dark) listOf(Color.Transparent, Color(0xFF538EBE), Color(0xFF8DB4E0), Color.Transparent)
                else listOf(Color.Transparent, Color(0xFF8EE8FF), Color.White, Color.Transparent),
                startX = w * .13f, endX = w * .87f,
            )
            val highlightColor = if (dark) Color(0xFF6085AF) else Color.White
            onDrawBehind {
                drawPath(edge, Sapphire, alpha = .08f, style = Stroke(bevel * 5f, join = StrokeJoin.Round))
                drawPath(edge, Sapphire, alpha = .13f, style = Stroke(bevel * 2.7f, join = StrokeJoin.Round))
                drawPath(edge, lip)
                drawPath(face, body)
                clipPath(face) { drawRect(reflection) }
                drawPath(face, rim, style = Stroke(bevel * .94f, join = StrokeJoin.Round))
                drawPath(highlight, highlightColor, alpha = .74f, style = Stroke(bevel * .36f))
                val y = h * .884f
                drawLine(shine, Offset(w * .13f, y), Offset(w * .87f, y), bevel * .58f)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val size = minOf(maxWidth * .61f, maxHeight * .655f).value.sp
        BlueText(state.gear.value, size, if (dark) Color(0xFFACCDF3) else Color(0xFF064CA9), FontWeight.Black)
    }
}
