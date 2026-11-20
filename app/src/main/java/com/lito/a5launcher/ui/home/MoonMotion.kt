package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawUfoBeam(ship: Rect, end: Float, strength: Float) {
    val x = ship.center.x
    val y = ship.top + ship.height * .78f
    if (end <= y) return
    val path = Path().apply {
        moveTo(x - ship.width * .15f, y)
        lineTo(x - ship.width * .37f, end)
        lineTo(x + ship.width * .37f, end)
        lineTo(x + ship.width * .15f, y)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Color(0x9986FFE8), Color.Transparent), y, end), alpha = strength)
}

internal fun DrawScope.drawRobotHeart(image: ImageBitmap, rect: Rect, p: Float) {
    if (p == 0f) return
    inArt(image, rect) {
        val center = Offset(175f, 190f)
        val alpha = (7f * artWave(p)).coerceAtMost(1f)
        rotate(-9f, center) {
            drawRoundRect(Color(0xFF171951), Offset(100f, 144f), Size(151f, 90f), CornerRadius(26f), alpha = alpha)
            scale(1f + .23f * sin(p * ART_PI * 6) * artWave(p), center) {
                drawPath(Path().apply {
                    moveTo(175f, 219f)
                    cubicTo(122f, 188f, 144f, 151f, 175f, 172f)
                    cubicTo(206f, 151f, 228f, 188f, 175f, 219f)
                    close()
                }, Color(0xFF75FFFF), alpha)
            }
        }
        for (i in 0..2) {
            val travel = (p * 2.25f + i * .34f) % 1f
            val point = Offset(94f + i * 77f + 20f * sin(travel * ART_PI), 139f - travel * 100f)
            drawStar(point, 10f + 6f * artWave(travel), Color(0xFF8EFCF3).copy(alpha = alpha * artWave(travel)))
        }
    }
}

internal fun DrawScope.drawMoonLaunch(image: ImageBitmap, rest: Rect, p: Float, filter: ColorFilter?) {
    if (p == 0f) return drawArt(image, rest, filter = filter)
    val lift = artWave(p)
    val arc = sin(2f * ART_PI * p)
    val moving = rest.translate(Offset(rest.width * (.38f + .18f * arc) * lift, -size.height * .55f * lift))
    rotate(12f * arc * lift, moving.center) {
        for (i in 1..7) {
            val trail = Offset(moving.center.x - rest.width * .043f * i * arc, moving.bottom - rest.height * .07f + rest.height * .032f * i * lift)
            drawCircle(Color(0xFFFFBC78), rest.width * (.045f + i * .015f) * lift, trail, alpha = lift * .74f * (1f - i / 8f))
        }
        drawArt(image, moving, filter = filter)
    }
}

internal fun DrawScope.drawOrbitingPlanet(image: ImageBitmap, p: Float, saturn: Boolean, filter: ColorFilter?) {
    val w = size.width
    val h = size.height
    if (p == 0f) return drawArt(image, Rect(Offset.Zero, size), filter = filter)
    val swing = artWave(p)
    val spin = 2f * ART_PI * p
    val angle = (if (saturn) 25f else 18f) * sin(spin) * swing
    val body = image.rectCentered(center.x + w * .052f * sin(spin) * swing, center.y - h * .06f * swing, w * (1f - .17f * swing))
    val orbit = Offset(center.x + w * .46f * cos(2f * spin), center.y + h * .39f * sin(2f * spin))
    val light = if (saturn) Color(0xFFFFCF97) else Color(0xFF9EE3FF)
    if (orbit.y < center.y) drawStar(orbit, h * .10f, light.copy(alpha = swing))
    rotate(angle, body.center) { drawArt(image, body, filter = filter) }
    if (orbit.y >= center.y) drawStar(orbit, h * .10f, light.copy(alpha = swing))
}
