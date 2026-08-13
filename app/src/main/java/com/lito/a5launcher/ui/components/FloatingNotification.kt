package com.lito.a5launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val FLOATING_NOTIFICATION_VISIBLE_MS = 3_000L

internal enum class FloatingNotificationTone {
    PROGRESS,
    SUCCESS,
    ERROR,
}

internal class FloatingNotification(
    val message: String,
    val tone: FloatingNotificationTone,
)

@Composable
internal fun FloatingNotificationHost(
    notification: FloatingNotification?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        FloatingNotificationBanner(
            text = notification?.message.orEmpty(),
            color = notification?.tone.notificationColor(),
        )
    }
}

@Composable
internal fun FloatingNotificationBanner(
    text: String,
    color: Color,
    detail: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .widthIn(max = 760.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF20A1216))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                text,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    detail,
                    color = Color(0xFF75DDEA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingContent?.invoke()
    }
}

private fun FloatingNotificationTone?.notificationColor(): Color = when (this) {
    FloatingNotificationTone.PROGRESS -> Color.White
    FloatingNotificationTone.SUCCESS -> Color(0xFF75DDEA)
    FloatingNotificationTone.ERROR -> Color(0xFFFF7777)
    null -> Color.White
}
