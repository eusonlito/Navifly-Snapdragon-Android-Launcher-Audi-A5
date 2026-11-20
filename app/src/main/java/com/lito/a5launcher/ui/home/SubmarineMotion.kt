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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun DrawScope.drawSwimmingFish(image: ImageBitmap, rect: Rect, p: Float) {
    if (p == 0f) return drawArt(image, rect)
    inArt(image, rect) {
        val kick = artWave(p)
        translate(20f * kick, -8f * kick) {
            for (x in 0 until 80 step 4) {
                val weight = 1f - x / 80f
                val bend = (14f * weight * weight * kick * sin(p * ART_PI * 8)).roundToInt()
                drawArtSlice(image, x, 0, 4, image.height, destY = bend)
            }
            drawArtSlice(image, 80, 0, image.width - 80, image.height)
        }
        fun bubble(start: Float, rise: Float, radius: Float) {
            val u = (p - start) / (1f - start)
            if (u <= 0f || u >= 1f) return
            val opacity = min(1f, min(u * 9f, (1f - u) * 5f))
            val center = Offset(303f + 23f * u, 119f - rise * u)
            drawCircle(Color(0xFFCBF8FB), radius * (.4f + .6f * u), center, .85f * opacity, style = Stroke(2.8f))
        }
        bubble(.13f, 70f, 9f)
        bubble(.35f, 47f, 6f)
    }
}

// Glass panes of the 520 × 251 seabase artwork. Keep the ribs and brass rim in front.
private val glassPanes = Path().apply {
    moveTo(144f, 138f); quadraticTo(165f, 90f, 219f, 74f)
    quadraticTo(206f, 93f, 198f, 130f); close()
    moveTo(203f, 130f); quadraticTo(210f, 93f, 227f, 72f)
    lineTo(246f, 70f); quadraticTo(225f, 88f, 222f, 128f); close()
    moveTo(226f, 128f); quadraticTo(230f, 88f, 248f, 71f)
    lineTo(272f, 71f); quadraticTo(294f, 87f, 298f, 128f); close()
    moveTo(302f, 128f); quadraticTo(298f, 90f, 276f, 71f)
    lineTo(291f, 74f); quadraticTo(307f, 91f, 312f, 130f); close()
    moveTo(317f, 130f); quadraticTo(312f, 92f, 297f, 75f)
    quadraticTo(351f, 94f, 373f, 138f); close()
}

internal fun DrawScope.drawShoal(image: ImageBitmap, rect: Rect, p: Float) {
    if (p == 0f) return
    inArt(image, rect) {
        clipPath(glassPanes) {
            val lead = 97f + 415f * smoothStep(p)
            for (i in 0..2) {
                val x = lead - i * 40f
                val y = (if (i == 1) 118f else 103f) + 4f * sin(p * 12f + i * 1.8f)
                val r = 20f - i * 3.6f
                val color = if (i == 1) Color(0xFFFFF1B7) else Color(0xFFFFC876)
                val tail = r * (.43f + .14f * sin(p * 36f + i))
                drawPath(Path().apply {
                    moveTo(x - .7f * r, y); lineTo(x - 1.6f * r, y - tail); lineTo(x - 1.6f * r, y + tail); close()
                }, color)
                drawOval(color, Offset(x - r, y - r * .55f), Size(2 * r, 1.1f * r))
                drawCircle(Color(0xFF173A41), r * .13f, Offset(x + r * .5f, y - r * .12f))
            }
        }
    }
}

internal fun DrawScope.drawPeriscope(image: ImageBitmap, rect: Rect, p: Float) {
    val lift = (32f * artWave(p)).roundToInt()
    if (lift == 0) return drawArt(image, rect, .9f)
    inArt(image, rect) {
        val collar = 52
        drawArtSlice(image, 0, collar, image.width, image.height - collar, alpha = .9f)
        drawArtSlice(image, 0, collar - 1, image.width, 1, collar - lift, lift, .9f)
        drawArtSlice(image, 0, 0, image.width, collar, destY = -lift, alpha = .9f)
        val lens = Offset(102f, 21f - lift)
        val end = lens + Offset(-80f, sin(p * ART_PI * 2) * 9f)
        drawPath(Path().apply {
            moveTo(lens.x, lens.y - 3f); lineTo(end.x, end.y - 18f)
            lineTo(end.x, end.y + 18f); lineTo(lens.x, lens.y + 3f); close()
        }, Brush.linearGradient(listOf(Color(0x886DEFFF), Color.Transparent), lens, end), artWave(p))
    }
}
