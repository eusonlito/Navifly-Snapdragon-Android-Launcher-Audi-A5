package com.lito.a5launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Launcher settings palette. The settings live on the same absolute-black canvas as
 * the cockpit, so cards are flat graphite surfaces separated by hairline borders and
 * the dashboard cyan is the only accent.
 */
internal object SettingsPalette {
    val Background = Color.Black
    val Card = Color(0xFF0D1317)
    val Raised = Color(0xFF141C22)
    val Track = Color(0xFF070B0E)
    val Control = Color(0xFF1C262D)
    val Border = Color(0xFF1E2830)
    val BorderStrong = Color(0xFF2B3740)
    val Accent = OemCockpitTokens.Cyan
    val OnAccent = Color(0xFF00171B)
    val Text = Color(0xFFE9EFF2)
    val MutedText = Color(0xFF8E9BA4)
    val SubtleText = Color(0xFF5E6B74)
    val Danger = Color(0xFFFF5A5F)
    val Success = Color(0xFF3DD68C)
    val Warning = Color(0xFFFFB547)
}

internal object SettingsDimensions {
    val SelectorHeight = 36.dp
    val FieldHeight = 48.dp
    val InputHeight = 40.dp
    val ActionHeight = 40.dp
    val TabHeight = 40.dp
    val CardPadding = 16.dp
    val CardGap = 12.dp
    val SectionGap = 14.dp
    val CardRadius = 14.dp
    val ControlRadius = 10.dp
}

internal val SettingsControlShape = RoundedCornerShape(SettingsDimensions.ControlRadius)

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SettingsDimensions.CardRadius)
    Column(
        modifier
            .clip(shape)
            .background(SettingsPalette.Card)
            .border(1.dp, SettingsPalette.Border, shape)
            .padding(SettingsDimensions.CardPadding),
        content = content,
    )
}

/** Card heading: accent pictogram, uppercase title and an optional trailing slot. */
@Composable
internal fun SettingsSectionTitle(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = SettingsPalette.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text.uppercase(locale),
            color = SettingsPalette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Secondary label for a control inside a card. */
@Composable
internal fun SettingsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = SettingsPalette.MutedText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
internal fun SettingsCountBadge(text: String) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(SettingsPalette.Accent.copy(alpha = .14f))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = SettingsPalette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(SettingsPalette.Border))
}

@Composable
internal fun <T> SettingsSegmentedSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    controlHeight: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelected: (T) -> Unit,
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val indicatorShape = RoundedCornerShape(SettingsDimensions.ControlRadius - 3.dp)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(controlHeight)
            .alpha(if (enabled) 1f else .45f)
            .clip(SettingsControlShape)
            .background(SettingsPalette.Track)
            .border(1.dp, SettingsPalette.Border, SettingsControlShape)
            .padding(3.dp),
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
                .clip(indicatorShape)
                .background(SettingsPalette.Accent),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) SettingsPalette.OnAccent else SettingsPalette.MutedText,
                    animationSpec = tween(durationMillis = 180),
                    label = "settings-segment-color",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(indicatorShape)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { if (!isSelected) onSelected(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
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
    fontSize: TextUnit = 11.sp,
    enabled: Boolean = true,
    destructive: Boolean = false,
    prominent: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val tone = if (destructive) SettingsPalette.Danger else SettingsPalette.Accent
    val filled = prominent && enabled
    val contentColor = when {
        filled -> SettingsPalette.OnAccent
        enabled -> tone
        else -> SettingsPalette.SubtleText
    }
    Row(
        modifier = modifier
            .height(controlHeight)
            .clip(SettingsControlShape)
            .background(
                when {
                    filled -> tone
                    enabled -> tone.copy(alpha = .08f)
                    else -> SettingsPalette.Raised.copy(alpha = .5f)
                },
            )
            .border(
                1.dp,
                when {
                    filled -> tone
                    enabled -> tone.copy(alpha = .45f)
                    else -> SettingsPalette.Border
                },
                SettingsControlShape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(locale),
            color = contentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = .6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Square companion of [SettingsActionButton] for a single pictogram action. */
@Composable
internal fun SettingsIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(SettingsDimensions.ActionHeight)
            .clip(SettingsControlShape)
            .background(SettingsPalette.Raised)
            .border(1.dp, SettingsPalette.Border, SettingsControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) SettingsPalette.Accent else SettingsPalette.SubtleText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun SettingsDeleteIconButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SettingsIcons.Trash,
            contentDescription = contentDescription,
            tint = if (enabled) SettingsPalette.Danger else SettingsPalette.SubtleText.copy(alpha = .6f),
            modifier = Modifier.size(17.dp),
        )
    }
}

/** Raised surface shared by every input-like control. */
internal fun Modifier.settingsFieldSurface(enabled: Boolean = true): Modifier = this
    .clip(SettingsControlShape)
    .background(SettingsPalette.Raised)
    .border(1.dp, if (enabled) SettingsPalette.BorderStrong else SettingsPalette.Border, SettingsControlShape)

/** Labelled value box; clickable when it opens a picker. */
@Composable
internal fun SettingsField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color = SettingsPalette.Text,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .height(SettingsDimensions.FieldHeight)
            .settingsFieldSurface()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = SettingsPalette.MutedText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = SettingsPalette.MutedText, fontSize = 10.sp, maxLines = 1)
            Text(
                value,
                color = valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/** Read-only key/value line with optional pictogram and status dot. */
@Composable
internal fun SettingsValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    statusColor: Color? = null,
    divider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = SettingsPalette.MutedText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label,
                color = SettingsPalette.MutedText,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(end = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            if (statusColor != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(7.dp))
            }
            Text(
                value,
                color = SettingsPalette.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
        if (divider) SettingsDivider()
    }
}

/** Centred placeholder for an empty list. */
@Composable
internal fun SettingsEmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(SettingsPalette.Raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = SettingsPalette.SubtleText, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            color = SettingsPalette.MutedText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
