package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.sin

internal fun DrawScope.drawRobotHeart(image: ImageBitmap, rect: Rect, p: Float) {
    if (p == 0f) return
    inArt(image, rect) {
        val center = Offset(175f, 190f)
        val alpha = (7f * artWave(p)).coerceAtMost(1f)
        rotate(-9f, center) {
            drawRoundRect(Color(0xFF171951), Offset(100f, 144f), Size(151f, 90f), CornerRadius(26f), alpha = alpha)
            scale(1f + .09f * sin(p * ART_PI * 4), center) {
                drawPath(Path().apply {
                    moveTo(175f, 219f)
                    cubicTo(122f, 188f, 144f, 151f, 175f, 172f)
                    cubicTo(206f, 151f, 228f, 188f, 175f, 219f)
                    close()
                }, Color(0xFF75FFFF), alpha)
            }
        }
    }
}
