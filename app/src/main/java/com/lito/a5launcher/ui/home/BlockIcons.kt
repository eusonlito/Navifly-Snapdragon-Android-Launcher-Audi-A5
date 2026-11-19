package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.lito.a5launcher.ui.components.FooterBlockItem

/** Units of the square grid every block pictogram is drawn on. */
private const val ICON_GRID = 24f

/**
 * Thin line pictogram of a value block, drawn in code so it takes the colour
 * and size of the label beside it. Fuel and witnesses have their own drawings.
 */
@Composable
internal fun BlockIcon(item: FooterBlockItem, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawBlockIcon(item, color) }
}

internal fun DrawScope.drawBlockIcon(item: FooterBlockItem, color: Color) {
    val unit = size.minDimension / ICON_GRID
    val stroke = Stroke(unit * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun at(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun line(vararg points: Pair<Float, Float>) {
        val path = Path()
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) path.moveTo(x * unit, y * unit) else path.lineTo(x * unit, y * unit)
        }
        drawPath(path, color, style = stroke)
    }
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) = drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = at(cx - r, cy - r),
        size = Size(r * 2f * unit, r * 2f * unit),
        style = stroke,
    )
    when (item) {
        // Drop.
        FooterBlockItem.CONSUMPTION -> {
            val drop = Path().apply {
                moveTo(12f * unit, 3f * unit)
                cubicTo(9.5f * unit, 7f * unit, 5.5f * unit, 11f * unit, 5.5f * unit, 14.8f * unit)
                arcTo(Rect(5.5f * unit, 8.3f * unit, 18.5f * unit, 21.3f * unit), 180f, -180f, false)
                cubicTo(18.5f * unit, 11f * unit, 14.5f * unit, 7f * unit, 12f * unit, 3f * unit)
                close()
            }
            drawPath(drop, color, style = stroke)
        }
        // Arrow running up to a limit: how far the fuel reaches.
        FooterBlockItem.RANGE -> {
            line(3f to 12f, 16f to 12f)
            line(11.5f to 7.5f, 16f to 12f, 11.5f to 16.5f)
            line(21f to 5f, 21f to 19f)
        }
        // Clock.
        FooterBlockItem.TIME -> {
            arc(12f, 12f, 8.5f, 0f, 360f)
            line(12f to 7.5f, 12f to 12f, 15.5f to 14f)
        }
        // Route between two points.
        FooterBlockItem.TRIP -> {
            arc(5.5f, 18.5f, 2.3f, 0f, 360f)
            arc(18.5f, 5.5f, 2.3f, 0f, 360f)
            line(8f to 18.5f, 14.5f to 18.5f)
            arc(14.5f, 15.25f, 3.25f, 90f, -180f)
            line(14.5f to 12f, 9.5f to 12f)
            arc(9.5f, 8.75f, 3.25f, 90f, 180f)
            line(9.5f to 5.5f, 16f to 5.5f)
        }
        // Flag planted at the last refuel, where this counter starts.
        FooterBlockItem.REFUEL_DISTANCE -> {
            line(6f to 3.5f, 6f to 21f)
            line(6f to 4.5f, 18.5f to 4.5f, 15f to 8.75f, 18.5f to 13f, 6f to 13f)
        }
        // Gauge.
        FooterBlockItem.ODOMETER -> {
            arc(12f, 15.5f, 9f, 180f, 180f)
            line(3f to 15.5f, 3f to 18.5f, 21f to 18.5f, 21f to 15.5f)
            line(12f to 15.5f, 16.2f to 10.2f)
        }
        FooterBlockItem.FUEL, FooterBlockItem.WITNESSES -> Unit
    }
}
