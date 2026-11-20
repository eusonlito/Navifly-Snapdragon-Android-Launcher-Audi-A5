package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal fun DrawScope.drawGalaxyLaunch(rocket: ImageBitmap, rest: Rect, p: Float, filter: ColorFilter?) {
    if (p == 0f) return drawArt(rocket, rest, filter = filter)
    val width = size.width
    val offset: Offset
    val tilt: Float
    if (p < .36f) {
        val u = smoothStep((p - .06f) / .3f)
        offset = Offset(.51f, -.23f) * (width * u * u)
        tilt = -9f * u
    } else {
        val u = smoothStep((p - .36f) / .64f)
        val docking = smoothStep((u - .45f) / .55f)
        offset = Offset(-1.15f * (1f - u), -.17f * (1f - docking) + .035f * sin(2 * ART_PI * u)) * width
        tilt = 26f * (1f - docking)
    }
    val rect = rest.translate(offset)
    rotate(tilt, rect.center) {
        val power = smoothStep(p / .08f) * smoothStep((1f - p) / .22f)
        val nozzle = rect.center + Offset(-rect.width * .29f, rect.height * .29f)
        val tail = nozzle + Offset(-width * .15f, width * .067f) * power
        if (power > 0f) {
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFAD65), Color(0xFFFFEDAE)), tail, nozzle),
                tail, nozzle, width * .018f * power, cap = StrokeCap.Round,
            )
        }
        drawArt(rocket, rect, filter = filter)
    }
}

private val auroraLight = Brush.verticalGradient(
    0f to Color.Transparent, .28f to Color(0x85AC85FF), .55f to Color(0xCD67D8D2),
    .81f to Color(0xED49FFAE), .91f to Color(0xFFA1FFE8), 1f to Color.Transparent,
    startY = 0f, endY = 1f,
)

internal fun DrawScope.drawAurora(earth: Rect, p: Float) {
    if (p == 0f) return
    val strength = smoothStep(p / .15f) * smoothStep((1f - p) / .17f)
    val columns = 76
    val stripe = size.width / columns
    for (i in 0 until columns) {
        val u = (i + .5f) / columns
        val taper = sin(ART_PI * u).pow(.55f)
        val fold = sin(10 * u - 4 * p) * .008f + sin(19 * u + 5 * p) * .006f
        val height = size.width * (.108f + .034f * cos(11 * u - p * 6) + .018f * sin(31 * u + p * 11)) * taper
        val x = u - .51f
        val shore = earth.top + size.width * (.032f + .33f * x * x + fold)
        withTransform({
            translate(i * stripe, shore - height)
            scale(stripe, height, Offset.Zero)
        }) {
            drawRect(auroraLight, Offset.Zero, Size(1.02f, 1f), strength * taper * (.83f + .17f * cos(u * 78 + 6 * p)))
        }
    }
}

internal fun DrawScope.drawRover(p: Float) {
    if (p == 0f) return
    val width = size.width
    val x = width * (-.12f + 1.24f * p)
    val y = size.height - width * (.095f + .014f * artWave(p * 3f))
    withTransform({
        translate(x, y)
        scale(width / 248f, width / 248f, Offset.Zero)
        rotate(7f * sin(p * 23), Offset.Zero)
    }) {
        for (i in 1..5) {
            val dust = (p * 7f + i * .19f) % 1f
            drawCircle(Color(0xFFC3AF9F), 1.3f + 2.5f * dust, Offset(-16f - 19f * dust, 8f - 10f * dust), .42f * (1f - dust))
        }
        drawOval(Color(0xAA171B36), Offset(-21f, 6f), Size(42f, 6f))
        drawLine(Color.LightGray, Offset(-14f, 4f), Offset(0f, 0f), 2f)
        drawLine(Color.LightGray, Offset(0f, 0f), Offset(14f, 4f), 2f)
        for (i in -1..1) {
            val hub = Offset(i * 13f, 6f)
            drawCircle(Color(0xFF8795AA), 5.8f, hub)
            drawCircle(Color(0xFF192438), 4.5f, hub)
            drawCircle(Color.LightGray, 1.4f, hub)
            rotate(1130f * p, hub) {
                drawLine(Color(0xFFB0BCCE), hub + Offset(0f, -3.5f), hub + Offset(0f, 3.5f), 1f)
            }
        }
        drawRect(Color(0xFFE2C780), Offset(-15f, -7f), Size(29f, 7f))
        drawRect(Color(0xFF435B87), Offset(-13f, -6f), Size(17f, 4f))
        drawLine(Color(0xFFEADFBF), Offset(8f, -7f), Offset(8f, -17f), 2f)
        drawRect(Color(0xFFB3CAD8), Offset(4f, -19f), Size(12f, 5f))
    }
}
