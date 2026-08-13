package com.lito.a5launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object SettingsPalette {
    val Shell = Color(0xFF2B333C)
    val ShellControl = Color(0xFF3A444E)
    val Content = Color(0xFFF0F2F4)
    val Card = Color(0xFFF9FAFB)
    val Control = Color(0xFFE8EBEE)
    val Accent = Color(0xFF00A7CF)
    val Text = Color(0xFF26323D)
    val MutedText = Color(0xFF65727E)
    val Border = Color(0xFFD3D9DE)
    val Danger = Color(0xFFD34B4B)
}

internal object SettingsDimensions {
    val SelectorHeight = 32.dp
    val FieldHeight = 36.dp
    val ActionHeight = 40.dp
    val TabHeight = 46.dp
}

@Composable
internal fun <T> SettingsSegmentedSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    controlHeight: Dp,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit,
) {
    if (options.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val shape = RoundedCornerShape(8.dp)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(controlHeight)
            .clip(shape)
            .background(SettingsPalette.Control)
            .border(1.dp, SettingsPalette.Border, shape),
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "settings-segment-offset",
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(shape)
                .background(SettingsPalette.Accent),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else SettingsPalette.Text,
                    animationSpec = tween(durationMillis = 180),
                    label = "settings-segment-color",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { if (!isSelected) onSelected(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option).uppercase(locale),
                        color = textColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsActionButton(
    text: String,
    controlHeight: Dp,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val color = if (destructive) SettingsPalette.Danger else SettingsPalette.Accent
    Box(
        modifier = modifier
            .height(controlHeight)
            .clip(RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(SettingsPalette.Card)
            .border(
                1.dp,
                color.copy(alpha = if (enabled) .55f else .20f),
                RoundedCornerShape(7.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(locale),
            color = color.copy(alpha = if (enabled) 1f else .40f),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}
