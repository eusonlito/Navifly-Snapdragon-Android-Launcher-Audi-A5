package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/*
 * Small decorations drawn in code for the illustrated themes: stars and
 * bubbles. Characters and frames are images (`illustration_*.webp`).
 */

/** Four-point sparkle star. */
internal fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

/** Rounded five-point star. */
internal fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * .48f
        val a = (-90f + i * 36f) * PI.toFloat() / 180f
        val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
    drawPath(path, color, style = Stroke(radius * .22f, join = StrokeJoin.Round))
}

/** Scattered stars over the whole canvas, always in the same places. */
internal fun DrawScope.drawStarField(seed: Int, count: Int, dot: Color, sparkle: Color, star: Color? = null) {
    val random = Random(seed)
    val unit = size.height / 100f
    repeat(count) { index ->
        val p = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        when {
            star != null && index % 9 == 0 -> drawStar(p, unit * (1.6f + random.nextFloat()), star)
            index % 4 == 0 -> drawSparkle(p, unit * (.9f + random.nextFloat() * .8f), sparkle)
            else -> drawCircle(dot.copy(alpha = .35f + random.nextFloat() * .5f), unit * (.18f + random.nextFloat() * .22f), p)
        }
    }
}

/**
 * Bubbles scattered in the water, in loose clusters of different sizes. A
 * bubble that would be partly hidden ([hidden] returns true for its centre and
 * radius) is left out, so none appears cut by the map or an instrument;
 * [fade] scales the opacity of each bubble by its position.
 */
internal fun DrawScope.drawBubbles(
    seed: Int,
    count: Int,
    color: Color,
    hidden: (Offset, Float) -> Boolean = { _, _ -> false },
    fade: (Offset) -> Float = { 1f },
) {
    val random = Random(seed)
    val unit = size.height / 100f
    var drawn = 0
    var attempts = 0
    while (drawn < count && attempts < count * 20) {
        attempts++
        val cluster = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        // Two to four bubbles rising together, smaller towards the top.
        val members = 2 + random.nextInt(3)
        var y = cluster.y
        var r = unit * (.7f + random.nextFloat() * .8f)
        repeat(members) {
            val p = Offset(cluster.x + (random.nextFloat() - .5f) * unit * 2.4f, y)
            if (drawn < count && !hidden(p, r + unit * .6f)) {
                val alpha = (.22f + random.nextFloat() * .2f) * fade(p)
                drawCircle(color.copy(alpha = alpha), r, p, style = Stroke(unit * .18f))
                drawCircle(color.copy(alpha = alpha + .15f), r * .25f, Offset(p.x - r * .35f, p.y - r * .35f))
                drawn++
            }
            y -= r * 2.2f + unit * (.6f + random.nextFloat() * 1.2f)
            r *= .62f + random.nextFloat() * .2f
        }
    }
}

/** Distance from [point] to a rounded rectangle; negative inside it. */
internal fun roundRectDistance(point: Offset, rect: Rect, radius: Float): Float {
    // For a circle the inner rectangle collapses to a point; rounding may invert it.
    val left = rect.left + radius
    val top = rect.top + radius
    val right = max(left, rect.right - radius)
    val bottom = max(top, rect.bottom - radius)
    val nearest = Offset(point.x.coerceIn(left, right), point.y.coerceIn(top, bottom))
    return (point - nearest).getDistance() - radius
}
