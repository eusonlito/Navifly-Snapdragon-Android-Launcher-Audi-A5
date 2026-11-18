package com.lito.a5launcher.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Draws an illustration stretched to [rect]; callers keep the source proportions. */
internal fun DrawScope.drawArt(image: ImageBitmap, rect: Rect, alpha: Float = 1f) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
}

/** Rectangle of [width] keeping the image proportions, anchored by its bottom centre. */
internal fun ImageBitmap.rectAboveBase(centerX: Float, baseY: Float, width: Float): Rect {
    val height = width * this.height / this.width
    return Rect(centerX - width / 2f, baseY - height, centerX + width / 2f, baseY)
}

/** Rectangle of [width] keeping the image proportions, centred on a point. */
internal fun ImageBitmap.rectCentered(centerX: Float, centerY: Float, width: Float): Rect {
    val height = width * this.height / this.width
    return Rect(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
}
