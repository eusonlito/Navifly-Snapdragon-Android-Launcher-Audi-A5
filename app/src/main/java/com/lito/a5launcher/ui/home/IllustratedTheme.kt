package com.lito.a5launcher.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lito.a5launcher.ui.components.MapThemeColors

/** Colours of one illustrated theme (Submarine, Moon and the space and adventure themes). */
@Immutable
internal data class IllustratedPalette(
    val skyTop: Color,
    val skyBottom: Color,
    /** Fill of the bars and pills. */
    val panel: Color,
    /** Outline of the bars and pills. */
    val panelBorder: Color,
    /** Bezels and the map frame. */
    val frame: Color,
    val frameShade: Color,
    /** Decorative highlight: fins, tabs, dividers. */
    val accent: Color,
    val dialFace: Color,
    val arc: Color,
    val arcTrack: Color,
    val needle: Color?,
    val content: Color,
    val label: Color,
    val unit: Color,
    val divider: Color,
    val redZone: Color,
)

internal val SubmarinePalette = IllustratedPalette(
    skyTop = Color(0xFF105060),
    skyBottom = Color(0xFF0A3540),
    panel = Color(0xFF0D3C48),
    panelBorder = Color(0xFF2B7684),
    frame = Color(0xFFCDBF98),
    frameShade = Color(0xFF857A5D),
    accent = Color(0xFFF07A2E),
    dialFace = Color(0xFF0B3440),
    arc = Color(0xFF2EC5BC),
    arcTrack = Color(0xFF1A5866),
    needle = Color(0xFFF07A2E),
    content = Color(0xFFF8F4E8),
    label = Color(0xFFE6F2F0),
    unit = Color(0xFFF8F4E8),
    divider = Color(0xFF6FB4BA).copy(alpha = .55f),
    redZone = Color(0xFFE5483A),
)

internal val MoonPalette = IllustratedPalette(
    skyTop = Color(0xFF12184C),
    skyBottom = Color(0xFF262C7E),
    panel = Color(0xFF10174E),
    panelBorder = Color(0xFF35E0FF),
    frame = Color(0xFF2AD7FF),
    frameShade = Color(0xFF1B2885),
    accent = Color(0xFF2AD7FF),
    dialFace = Color(0xFF131C5A),
    arc = Color(0xFF2AD7FF),
    arcTrack = Color(0xFF26337E),
    needle = null,
    content = Color.White,
    label = Color.White,
    unit = Color(0xFF2AD7FF),
    divider = Color(0xFF7480DA).copy(alpha = .6f),
    redZone = Color(0xFFFF3B4E),
)

internal val GalaxyPalette = IllustratedPalette(
    skyTop = Color(0xFF060A22),
    skyBottom = Color(0xFF111947),
    panel = Color(0xEB13205A),
    panelBorder = Color(0xFF4CCBFF),
    frame = Color(0xFF55D2FF),
    frameShade = Color(0xFFB07CFF),
    accent = Color(0xFFFFA544),
    dialFace = Color(0xEB0A1036),
    arc = Color(0xFF22D3EE),
    arcTrack = Color(0xFF3A4672),
    needle = null,
    content = Color.White,
    label = Color(0xFFE6ECFF),
    unit = Color(0xFF5ED8FF),
    divider = Color(0xFF7C8BE0).copy(alpha = .55f),
    redZone = Color(0xFFE5484D),
)

/** Bright cartoon tiles on a night-blue sky. */
internal val AdventurePalette = IllustratedPalette(
    skyTop = Color(0xFF0F2D7A),
    skyBottom = Color(0xFF0B2468),
    panel = Color(0xFF123A96),
    panelBorder = Color(0xFF3E7BEA),
    frame = Color.White,
    frameShade = Color(0xFF0A1D57),
    accent = Color(0xFFFFD43B),
    dialFace = Color(0xFF0D2C80),
    arc = Color(0xFFFFD43B),
    arcTrack = Color(0xFF8CC8FF),
    needle = null,
    content = Color.White,
    label = Color.White,
    unit = Color.White,
    divider = Color.White.copy(alpha = .35f),
    redZone = Color(0xFFFF4D4D),
)

internal fun illustratedTokens(palette: IllustratedPalette, dark: Boolean) = HomeTokens(
    background = palette.skyBottom,
    chrome = palette.panel,
    surface = palette.panel,
    surfaceBorder = palette.panelBorder,
    content = palette.content,
    label = palette.label,
    accent = palette.arc,
    gaugeFill = listOf(palette.arc, palette.arc),
    gaugeTrack = palette.arcTrack,
    gaugeOutline = palette.frame,
    redZone = palette.redZone,
    warning = Color(0xFFFF6B5E),
    caution = Color(0xFFFFC93C),
    divider = palette.divider,
    commandDivider = palette.divider,
    dragPlate = palette.arcTrack,
    witnessAlert = Color(0xFFFF4D4D),
    witnessInactive = palette.content.copy(alpha = .55f),
    witnessLights = Color(0xFF4ADE80),
    fuelInactive = palette.arcTrack,
    numberFamily = FontFamily.SansSerif,
    numberWeight = FontWeight.Black,
    labelFamily = FontFamily.SansSerif,
    map = MapThemeColors.Classic.copy(
        loadingBackground = if (dark) palette.dialFace else Color(0xFFE8ECF2),
        attribution = if (dark) Color.White.copy(alpha = .42f) else Color.Black.copy(alpha = .45f),
        cardBackground = palette.panel.copy(alpha = .94f),
        cardBorder = palette.panelBorder.copy(alpha = .6f),
        cardText = palette.content.copy(alpha = .88f),
        cardValue = palette.content,
        controlBackground = palette.panel.copy(alpha = .94f),
        controlBorder = palette.panelBorder.copy(alpha = .6f),
        controlIcon = palette.content,
        controlAccent = palette.accent,
    ),
)
