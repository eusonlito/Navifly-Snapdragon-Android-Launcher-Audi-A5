package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal fun DrawScope.drawGalaxyLaunch(rocket: ImageBitmap, rest: Rect, p: Float) {
    if (p == 0f) return drawArt(rocket, rest)
    val width = size.width
    val offset: Offset
    val tilt: Float
    if (p < .3f) {
        val u = p / .3f
        offset = Offset(.51f, -.23f) * (width * u * u)
        tilt = -2.3f * u
    } else {
        val u = smoothStep((p - .3f) / .7f)
        val docking = smoothStep((u - .5f) * 2f)
        offset = Offset(-1.15f * (1f - u), -.15f * (1f - docking) + .015f * sin(2 * ART_PI * u)) * width
        tilt = 32.7f * (1f - docking)
    }
    val rect = rest.translate(offset)
    rotate(tilt, rect.center) { drawArt(rocket, rect) }
}

private val auroraLight = Brush.verticalGradient(
    0f to Color.Transparent, .28f to Color(0x85AC85FF), .55f to Color(0xCD67D8D2),
    .81f to Color(0xED49FFAE), .91f to Color(0xFFA1FFE8), 1f to Color.Transparent,
    startY = 0f, endY = 1f,
)

internal fun DrawScope.drawAurora(earth: Rect, p: Float) {
    if (p == 0f) return
    val strength = smoothStep(p / .15f) * smoothStep((1f - p) / .17f)
    val columns = 112
    val stripe = size.width / columns
    for (i in 0 until columns) {
        val u = (i + .5f) / columns
        val taper = sin(ART_PI * u).pow(.55f)
        val fold = sin(10 * u - 4 * p) * .008f + sin(19 * u + 5 * p) * .006f
        val height = size.width * (.076f + .024f * cos(11 * u - p * 4) + .013f * sin(31 * u + p * 9)) * taper
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
    val x = width * (-.085f + 1.17f * p)
    val y = size.height - width * (.083f - .008f * sin(p * 10))
    withTransform({
        translate(x, y)
        scale(width / 280f, width / 280f, Offset.Zero)
        rotate(2.6f * sin(p * 22), Offset.Zero)
    }) {
        drawOval(Color(0xAA171B36), Offset(-21f, 6f), Size(42f, 6f))
        drawLine(Color.LightGray, Offset(-14f, 4f), Offset(0f, 0f), 2f)
        drawLine(Color.LightGray, Offset(0f, 0f), Offset(14f, 4f), 2f)
        for (i in -1..1) {
            val hub = Offset(i * 13f, 6f)
            drawCircle(Color(0xFF8795AA), 5.8f, hub)
            drawCircle(Color(0xFF192438), 4.5f, hub)
            drawCircle(Color.LightGray, 1.4f, hub)
        }
        drawRect(Color(0xFFE2C780), Offset(-15f, -7f), Size(29f, 7f))
        drawRect(Color(0xFF435B87), Offset(-13f, -6f), Size(17f, 4f))
        drawLine(Color(0xFFEADFBF), Offset(8f, -7f), Offset(8f, -17f), 2f)
        drawRect(Color(0xFFB3CAD8), Offset(4f, -19f), Size(12f, 5f))
    }
}
