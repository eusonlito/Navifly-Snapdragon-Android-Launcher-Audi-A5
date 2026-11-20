package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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

internal fun DrawScope.drawAdventureFlight(image: ImageBitmap, rect: Rect, rightLimit: Float, p: Float, filter: ColorFilter?) {
    if (p == 0f) return drawArt(image, rect, filter = filter)
    val angle = 2 * ART_PI * smoothStep(p)
    // The dotted trail is printed on the sky. Only the craft turns around.
    val distance = min(rect.width * 1.4f, (rightLimit - rect.right).coerceAtLeast(0f))
    val dx = distance * (1f - cos(angle)) / 2f
    inArt(image, rect) {
        val split = (image.width * .595f).roundToInt()
        drawArtSlice(image, 0, 0, split, image.height, filter = filter)
        val pivot = Offset(image.width * .82f, image.height * .56f)
        val turn = smoothStep((p - .46f) / .085f) * (1f - smoothStep((p - .89f) / .10f))
        withTransform({
            translate(dx * image.width / rect.width, image.height * .095f * sin(angle))
            rotate(-11f * sin(angle), pivot)
            scale(1f - 2f * turn, 1f, pivot)
        }) {
            val glow = artWave(p)
            val nozzle = Offset(image.width * .689f, image.height * .63f)
            drawCircle(Color(0xFFFFC57A), image.height * (.055f + .065f * glow), nozzle, alpha = glow * .7f)
            drawArtSlice(image, split, 0, image.width - split, image.height, filter = filter)
        }
    }
}

internal fun DrawScope.drawButterfly(p: Float) {
    if (p == 0f) return
    val u = 2 * ART_PI * p
    val unit = size.height / 896f
    val x = -60 + (size.width / unit * .72f + 60f) * sin(ART_PI * p) + 8f * sin(2 * u) * artWave(p)
    val y = 853 - 48 * artWave(p) + 15f * sin(2f * u) * artWave(p)
    withTransform({
        translate(x * unit, y * unit)
        rotate(-19f * cos(ART_PI * p) + 10 * sin(2 * u), Offset.Zero)
        scale(unit * 2.45f * (.63f + .37f * cos(6 * u)), unit * 2.45f, Offset.Zero)
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
            for (i in 0..2) {
                val u = (p - i * .18f) / .64f
                if (u <= 0f || u >= 1f) continue
                val height = 197f + 5f * i
                val x = 27f + 276f * u + i * 13f
                val y = -139f + height * sin(ART_PI * u)
                val color = if (i == 0) Color(0xFFFFCF68) else Color(0xFFA9F5FF)
                withTransform({
                    translate(x, y)
                    rotate(atan2(height * ART_PI * cos(ART_PI * u), 282f) * 180f / ART_PI, Offset.Zero)
                }) {
                    val tail = Path().apply {
                        moveTo(-138f, -9f); quadraticTo(-62f, 0f, 0f, -7f)
                        lineTo(0f, 7f); quadraticTo(-55f, 10f, -138f, -9f); close()
                    }
                    drawPath(tail, Brush.horizontalGradient(listOf(Color.Transparent, color), -138f, 0f))
                    rotate(u * 86f, Offset.Zero) {
                        val r = 17f - 3f * i
                        drawStar(Offset.Zero, r, color)
                        drawStar(Offset.Zero, r * .78f, Color(0xFFFFF9DB))
                    }
                }
            }
        }
    }
}
