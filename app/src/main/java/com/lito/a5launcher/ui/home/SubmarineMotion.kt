package com.lito.a5launcher.ui.home

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val meshPaint = Paint(Paint.FILTER_BITMAP_FLAG)

internal fun DrawScope.drawSwimmingFish(image: ImageBitmap, rect: Rect, p: Float, filter: ColorFilter?) {
    if (p == 0f) return drawArt(image, rect, filter = filter)
    inArt(image, rect) {
        val kick = artWave(p)
        translate(130f * kick + 38f * kick * sin(p * 2f * ART_PI), -19f * kick + 27f * kick * sin(p * 4f * ART_PI)) {
            // Shared vertices keep the outline continuous as the tail bends.
            val columns = 12
            val vertices = FloatArray((columns + 1) * 4)
            val swing = 20f * kick * sin(p * ART_PI * 10)
            for (col in 0..columns) {
                val x = image.width * col.toFloat() / columns
                val weight = (1f - x / 80f).coerceAtLeast(0f)
                val bend = swing * weight * weight
                val top = col * 2
                val bottom = top + (columns + 1) * 2
                vertices[top] = x
                vertices[top + 1] = bend
                vertices[bottom] = x
                vertices[bottom + 1] = image.height + bend
            }
            drawArtMesh(image, columns, 1, vertices, filter)
        }
        fun bubble(start: Float, rise: Float, radius: Float) {
            val u = (p - start) / (1f - start)
            if (u <= 0f || u >= 1f) return
            val opacity = min(1f, min(u * 9f, (1f - u) * 5f))
            val center = Offset(303f + 23f * u, 119f - rise * u)
            drawCircle(Color(0xFFCBF8FB), radius * (.4f + .6f * u), center, .85f * opacity, style = Stroke(2.8f))
        }
        bubble(.13f, 70f, 9f)
        bubble(.23f, 86f, 7f)
        bubble(.35f, 47f, 6f)
        bubble(.48f, 65f, 10f)
    }
}

internal fun DrawScope.drawSwayingSeaweed(image: ImageBitmap, rect: Rect, p: Float, filter: ColorFilter?) {
    if (p == 0f) return drawArt(image, rect, filter = filter)
    inArt(image, rect) {
        val rows = 12
        val vertices = FloatArray((rows + 1) * 4)
        for (row in 0..rows) {
            val depth = row.toFloat() / rows
            val tip = (1f - depth / .86f).coerceAtLeast(0f)
            val sway = sin(p * ART_PI * 5f - depth * 2f) * artWave(p) * image.width * .36f * tip * tip
            vertices[row * 4] = sway
            vertices[row * 4 + 1] = image.height * depth
            vertices[row * 4 + 2] = image.width + sway
            vertices[row * 4 + 3] = image.height * depth
        }
        drawArtMesh(image, 1, rows, vertices, filter)
    }
}

private fun DrawScope.drawArtMesh(image: ImageBitmap, columns: Int, rows: Int, vertices: FloatArray, filter: ColorFilter?) {
    drawIntoCanvas {
        meshPaint.colorFilter = filter?.asAndroidColorFilter()
        it.nativeCanvas.drawBitmapMesh(image.asAndroidBitmap(), columns, rows, vertices, 0, null, 0, meshPaint)
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
            for (i in 0..3) {
                val x = lead - i * 34f
                val y = (if (i % 2 == 1) 116f else 96f) + 8f * sin(p * 15f + i * 1.8f)
                val r = 24f - i * 3.5f
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

internal fun DrawScope.drawPeriscope(image: ImageBitmap, rect: Rect, p: Float, filter: ColorFilter?) {
    val lift = (49f * artWave(p)).roundToInt()
    if (lift == 0) return drawArt(image, rect, .9f, filter)
    inArt(image, rect) {
        val collar = 52
        drawArtSlice(image, 0, collar, image.width, image.height - collar, alpha = .9f, filter = filter)
        drawArtSlice(image, 0, collar - 1, image.width, 1, collar - lift, lift, .9f, filter)
        drawArtSlice(image, 0, 0, image.width, collar, destY = -lift, alpha = .9f, filter = filter)
        val lens = Offset(102f, 21f - lift)
        val end = lens + Offset(-124f, sin(p * ART_PI * 2) * 27f)
        drawPath(Path().apply {
            moveTo(lens.x, lens.y - 3f); lineTo(end.x, end.y - 18f)
            lineTo(end.x, end.y + 18f); lineTo(lens.x, lens.y + 3f); close()
        }, Brush.linearGradient(listOf(Color(0xC080EAFF), Color.Transparent), lens, end), artWave(p))
    }
}
