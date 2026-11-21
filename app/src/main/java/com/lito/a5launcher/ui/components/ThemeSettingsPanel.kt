package com.lito.a5launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.R
import com.lito.a5launcher.ui.home.CAP_HEIGHT_RATIO
import com.lito.a5launcher.ui.home.HomeTheme
import com.lito.a5launcher.ui.home.TightTextStyle
import com.lito.a5launcher.ui.home.capHeightBounds

/** Screen proportions of the head unit, shared by every preview. */
private const val PREVIEW_ASPECT = 2400f / 896f
private const val GRID_COLUMNS = 4

/** Theme name under each preview. */
private val NameSize = 14.sp

// The last card selects a theme at random on each boot.
private val themeOptions: List<HomeTheme?> = HomeTheme.entries + listOf(null)

/**
 * The one spacing inside a card: from its edge to the preview, from the preview
 * to the name and from the name to the bottom edge. The name is measured by
 * its capital letters, so the three gaps look the same.
 */
private val CardInset = 10.dp

internal fun themePreviewRes(theme: HomeTheme): Int = when (theme) {
    HomeTheme.CLASSIC -> R.drawable.theme_preview_classic
    HomeTheme.HORIZON -> R.drawable.theme_preview_horizon
    HomeTheme.FACETS -> R.drawable.theme_preview_facets
    HomeTheme.TELEMETRY -> R.drawable.theme_preview_telemetry
    HomeTheme.SUBMARINE -> R.drawable.theme_preview_submarine
    HomeTheme.MOON -> R.drawable.theme_preview_moon
    HomeTheme.GALAXY -> R.drawable.theme_preview_galaxy
    HomeTheme.ADVENTURE -> R.drawable.theme_preview_adventure
    HomeTheme.BLUE_LIGHT -> R.drawable.theme_preview_blue_light
}

/** Grid of home themes, each shown by its screenshot and its name; the active one is outlined. */
@Composable
internal fun ThemeSettingsPanel(
    selected: HomeTheme?,
    onSelected: (HomeTheme?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier) {
        ThemeGrid(selected, onSelected)
    }
}

@Composable
private fun ThemeGrid(selected: HomeTheme?, onSelected: (HomeTheme?) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = SettingsDimensions.CardGap
        val rows = (themeOptions.size + GRID_COLUMNS - 1) / GRID_COLUMNS
        val nameHeight = with(LocalDensity.current) { NameSize.toDp() * CAP_HEIGHT_RATIO }
        val columnWidth = (maxWidth - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val rowHeight = (maxHeight - gap * (rows - 1)) / rows
        // The preview keeps the screen proportions and is as large as the cell allows.
        val previewWidth = min(
            columnWidth - CardInset * 2,
            (rowHeight - CardInset * 3 - nameHeight) * PREVIEW_ASPECT,
        )
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
        ) {
            themeOptions.chunked(GRID_COLUMNS).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start),
                ) {
                    row.forEach { theme ->
                        Box(Modifier.width(columnWidth), contentAlignment = Alignment.Center) {
                            ThemeCard(
                                theme = theme,
                                selected = theme == selected,
                                previewWidth = previewWidth,
                                onClick = { onSelected(theme) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: HomeTheme?,
    selected: Boolean,
    previewWidth: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SettingsDimensions.ControlRadius + CardInset)
    val name = stringResource(theme?.labelRes ?: R.string.home_theme_random)
    Box(
        Modifier
            .clip(shape)
            .background(SettingsPalette.Raised)
            .border(if (selected) 2.dp else 1.dp, if (selected) SettingsPalette.Accent else SettingsPalette.Border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(CardInset),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val previewModifier = Modifier
                .width(previewWidth)
                .height(previewWidth / PREVIEW_ASPECT)
                .clip(RoundedCornerShape(SettingsDimensions.ControlRadius))
            if (theme == null) {
                Box(previewModifier.background(SettingsPalette.Track), contentAlignment = Alignment.Center) {
                    Icon(
                        SettingsIcons.Shuffle,
                        contentDescription = null,
                        tint = SettingsPalette.Accent,
                        modifier = Modifier.size(previewWidth / PREVIEW_ASPECT * .55f),
                    )
                }
            } else {
                Image(
                    painter = painterResource(themePreviewRes(theme)),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = previewModifier,
                )
            }
            Spacer(Modifier.height(CardInset))
            Text(
                name,
                color = if (selected) SettingsPalette.Accent else SettingsPalette.Text,
                fontSize = NameSize,
                lineHeight = NameSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TightTextStyle,
                modifier = Modifier.widthIn(max = previewWidth).capHeightBounds(NameSize),
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(CardInset + 6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(SettingsPalette.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = SettingsPalette.OnAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}
