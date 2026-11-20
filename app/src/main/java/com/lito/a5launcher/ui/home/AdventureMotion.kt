package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun DrawScope.drawAdventureFlight(image: ImageBitmap, rect: Rect, rightLimit: Float, p: Float) {
    if (p == 0f) return drawArt(image, rect)
    val angle = 2 * ART_PI * smoothStep(p)
    // The dotted trail is printed on the sky. Only the craft turns around.
    val distance = min(rect.width * 1.06f, (rightLimit - rect.right).coerceAtLeast(0f))
    val dx = distance * (1f - cos(angle)) / 2f
    inArt(image, rect) {
        val split = (image.width * .595f).roundToInt()
        drawArtSlice(image, 0, 0, split, image.height)
        val pivot = Offset(image.width * .82f, image.height * .56f)
        val turn = smoothStep((p - .46f) / .085f) * (1f - smoothStep((p - .89f) / .10f))
        val zoom = 1f - .14f * artWave(p)
        withTransform({
            translate(dx * image.width / rect.width, 4f * sin(angle))
            rotate(-2.6f * sin(2 * angle), pivot)
            scale((1f - 2f * turn) * zoom, zoom, pivot)
        }) { drawArtSlice(image, split, 0, image.width - split, image.height) }
    }
}

internal fun DrawScope.drawButterfly(p: Float) {
    if (p == 0f) return
    val u = 2 * ART_PI * p
    val unit = size.height / 896f
    val x = -30 + 124 * sin(ART_PI * p) + 4 * sin(2 * u) * artWave(p)
    val y = 856 - 32 * artWave(p) + 10 * sin(u)
    withTransform({
        translate(x * unit, y * unit)
        rotate(-19f * cos(ART_PI * p) + 10 * sin(2 * u), Offset.Zero)
        scale(unit * 1.85f * (.7f + .3f * cos(5 * u)), unit * 1.85f, Offset.Zero)
    }) {
        for (side in -1..1 step 2) scale(side.toFloat(), 1f, Offset.Zero) {
            rotate(-36f, Offset(6f, -4f)) {
                drawOval(Color(0xFFFFA73B), Offset(-2f, -11f), Size(16f, 14f))
                drawOval(Color(0xFF543455), Offset(-2f, -11f), Size(16f, 14f), style = Stroke(1.4f))
            }
            drawOval(Color(0xFFFFDC63), Offset(-1f, -1f), Size(12f, 10f))
            drawOval(Color(0xFF543455), Offset(-1f, -1f), Size(12f, 10f), style = Stroke(1.4f))
            drawCircle(Color(0xFFFFF2AE), 1.9f, Offset(10f, -7f))
        }
        drawRect(Color(0xFF482D46), Offset(-1f, -6f), Size(2f, 13f))
    }
}

internal fun DrawScope.drawShootingStars(left: Float, right: Float, p: Float) {
    if (p == 0f || right <= left) return
    // The entire trail stays inside the header gap between the date and the printed rocket.
    clipRect(left, 0f, right, size.height) {
        withTransform({
            translate(left, 0f)
            scale((right - left) / 378f, size.height / 90f, Offset.Zero)
        }) {
            for (i in 0..1) {
                val u = (p - i * .22f) / .78f
                if (u <= 0f || u >= 1f) continue
                val height = 181f + 10f * i
                val x = 30f + 282f * u
                val y = -139f + height * sin(ART_PI * u)
                val color = if (i == 0) Color(0xFFFFCF68) else Color(0xFFA9F5FF)
                withTransform({
                    translate(x, y)
                    rotate(atan2(height * ART_PI * cos(ART_PI * u), 282f) * 180f / ART_PI, Offset.Zero)
                }) {
                    val tail = Path().apply {
                        moveTo(-117f, -9f); quadraticTo(-62f, 0f, 0f, -5f)
                        lineTo(0f, 5f); quadraticTo(-55f, 7f, -117f, -9f); close()
                    }
                    drawPath(tail, Brush.horizontalGradient(listOf(Color.Transparent, color), -117f, 0f))
                    rotate(u * 86f, Offset.Zero) {
                        val r = 14f - 4f * i
                        drawStar(Offset.Zero, r, color)
                        drawStar(Offset.Zero, r * .78f, Color(0xFFFFF9DB))
                    }
                }
            }
        }
    }
}
