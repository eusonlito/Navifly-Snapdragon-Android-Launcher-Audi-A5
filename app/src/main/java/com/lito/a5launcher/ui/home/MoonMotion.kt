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
import kotlin.math.sin

internal fun DrawScope.drawUfoBeam(ship: Rect, end: Float, receiver: Offset, p: Float) {
    val strength = artWave(p)
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
    // Lower the star down the beam, then send it across to the robot's visor.
    val release = smoothStep((p - .44f) / .48f)
    val star = Offset(x + (receiver.x - x) * release,
        y + (end - y) * smoothStep(p / .44f) + (receiver.y - end) * release - ship.height * .33f * sin(ART_PI * release))
    drawStar(star, ship.width * .105f * smoothStep(p / .12f), Color(0xFF75FFFF))
}

internal fun DrawScope.drawRobotHeart(image: ImageBitmap, rect: Rect, p: Float) {
    if (p == 0f) return
    inArt(image, rect) {
        val center = Offset(175f, 190f)
        val alpha = (7f * artWave(p)).coerceAtMost(1f)
        rotate(-9f, center) {
            drawRoundRect(Color(0xFF171951), Offset(100f, 144f), Size(151f, 90f), CornerRadius(26f), alpha = alpha)
            scale(1f + .23f * sin(p * ART_PI * 6) * artWave(p), center) {
                if (p < .32f) drawStar(center, 23f, Color(0xFF75FFFF).copy(alpha = alpha))
                else drawPath(Path().apply {
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
        drawArt(image, moving, filter = filter)
    }
}

internal fun DrawScope.drawLunarSurvey(image: ImageBitmap, rest: Rect, p: Float, filter: ColorFilter?) {
    if (p == 0f) return drawArt(image, rest, filter = filter)
    // Two low-gravity hops, with a stop to plant and unfurl the pennant.
    val outward = smoothStep(p / .31f)
    val homeward = smoothStep((p - .68f) / .32f)
    val hop = sin(ART_PI * outward) + sin(ART_PI * homeward)
    val astronaut = rest.translate(Offset(rest.width * .52f * (outward - homeward), -rest.height * .24f * hop))
    val planted = smoothStep((p - .22f) / .18f) * smoothStep((.78f - p) / .17f)
    if (planted > 0f) {
        val foot = Offset(rest.right + rest.width * .42f, rest.bottom)
        val top = foot - Offset(0f, rest.height * .77f * planted)
        val tip = top + Offset(rest.width * .48f * planted, rest.height * (.09f + .025f * sin(6f * ART_PI * p)))
        drawLine(Color(0xFFCFD9EC), foot, top, rest.width * .029f)
        drawPath(Path().apply {
            moveTo(top.x, top.y)
            lineTo(tip.x, tip.y)
            lineTo(top.x, top.y + rest.height * .21f * planted)
            close()
        }, Color(0xFFFFC56F))
    }
    rotate(-12f * hop, astronaut.center) { drawArt(image, astronaut, filter = filter) }
}

internal fun DrawScope.drawOrbitingPlanet(image: ImageBitmap, p: Float, saturn: Boolean, filter: ColorFilter?) {
    val w = size.width
    val h = size.height
    if (p == 0f) return drawArt(image, Rect(Offset.Zero, size), filter = filter)
    val swing = artWave(p)
    val spin = 2f * ART_PI * p
    val angle = (if (saturn) 25f else 18f) * sin(spin) * swing
    val body = image.rectCentered(center.x + w * .052f * sin(spin) * swing, center.y - h * .06f * swing, w * (1f - .17f * swing))
    rotate(angle, body.center) { drawArt(image, body, filter = filter) }
    val visitor = Offset(w * (-.25f + 1.5f * smoothStep(p)), h * (.72f - .34f * swing))
    if (saturn) {
        drawSurveyProbe(visitor, h * .60f, p)
    } else {
        val radius = h * .17f
        drawCircle(Color(0xFFBAC3D2), radius, visitor)
        drawCircle(Color(0xFF78859B), radius * .30f, visitor - Offset(radius * .28f, radius * .32f))
        drawCircle(Color(0xFF939FB4), radius * .19f, visitor + Offset(radius * .34f, radius * .24f))
    }
}
