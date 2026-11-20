package com.lito.a5launcher.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lito.a5launcher.R
import com.lito.a5launcher.ui.components.DefaultFooterBlockOrder
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.MapThemeColors
import com.lito.a5launcher.ui.components.OemCockpitTokens
import com.lito.a5launcher.ui.theme.SLineRed

internal const val HOME_THEME_KEY = "home_theme"

internal enum class HomeTheme(val labelRes: Int) {
    CLASSIC(R.string.home_theme_classic),
    HORIZON(R.string.home_theme_horizon),
    FACETS(R.string.home_theme_facets),
    TELEMETRY(R.string.home_theme_telemetry),
    SUBMARINE(R.string.home_theme_submarine),
    MOON(R.string.home_theme_moon),
    GALAXY(R.string.home_theme_galaxy),
    ADVENTURE(R.string.home_theme_adventure),
    BLUE_CYAN(R.string.home_theme_blue_cyan),
    BLUE_LIGHT(R.string.home_theme_blue_light),
}

internal fun parseHomeTheme(serialized: String?): HomeTheme =
    HomeTheme.entries.firstOrNull { it.name == serialized } ?: HomeTheme.CLASSIC

/** Visual tokens shared by every home layout; Classic reproduces the original values. */
@Immutable
internal data class HomeTokens(
    val background: Color,
    val chrome: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val content: Color,
    val label: Color,
    val accent: Color,
    val gaugeFill: List<Color>,
    val gaugeTrack: Color,
    val gaugeOutline: Color,
    val redZone: Color,
    val warning: Color,
    val caution: Color,
    val divider: Color,
    val commandDivider: Color,
    val dragPlate: Color,
    val witnessAlert: Color,
    val witnessInactive: Color,
    val witnessLights: Color,
    val fuelInactive: Color,
    val numberFamily: FontFamily?,
    val numberWeight: FontWeight,
    val labelFamily: FontFamily?,
    val map: MapThemeColors,
)

internal val ClassicHomeTokens = HomeTokens(
    background = Color.Black,
    chrome = Color.Black,
    surface = Color.Black,
    surfaceBorder = Color.Transparent,
    content = Color.White,
    label = Color.White.copy(alpha = .50f),
    accent = OemCockpitTokens.Cyan,
    gaugeFill = listOf(OemCockpitTokens.Cyan, OemCockpitTokens.Cyan),
    gaugeTrack = OemCockpitTokens.DialChannel,
    gaugeOutline = OemCockpitTokens.Titanium,
    redZone = Color(0xFFE23036),
    warning = SLineRed,
    caution = Color(0xFFFFC400),
    divider = Color.White.copy(alpha = .12f),
    commandDivider = OemCockpitTokens.Titanium.copy(alpha = .18f),
    dragPlate = OemCockpitTokens.GraphitePressed,
    witnessAlert = Color(0xFFFF3B3B),
    witnessInactive = Color(0xFF263036),
    witnessLights = Color(0xFF42D36B),
    fuelInactive = Color(0xFF273037),
    numberFamily = null,
    numberWeight = FontWeight.Bold,
    labelFamily = null,
    map = MapThemeColors.Classic,
)

private val HorizonCyan = Color(0xFF22D3EE)
private val HorizonCyanDeep = Color(0xFF0B8FA3)
private val HorizonGlass = Color(0xFF1C2127)

private fun horizonTokens(dark: Boolean) = HomeTokens(
    background = Color(0xFF101418),
    chrome = Color.Transparent,
    surface = HorizonGlass.copy(alpha = if (dark) .90f else .84f),
    // A fine edge of light that keeps the glass apart from the dark map.
    surfaceBorder = Color(0xFFC8D1D8).copy(alpha = if (dark) .20f else .26f),
    content = Color.White,
    label = Color.White.copy(alpha = .64f),
    accent = HorizonCyan,
    gaugeFill = listOf(HorizonCyanDeep, HorizonCyan),
    gaugeTrack = Color.White.copy(alpha = .20f),
    gaugeOutline = Color.White.copy(alpha = .55f),
    redZone = Color(0xFFEF4444),
    warning = Color(0xFFF87171),
    caution = Color(0xFFFACC15),
    divider = Color.White.copy(alpha = .14f),
    commandDivider = Color.White.copy(alpha = .14f),
    dragPlate = Color.White.copy(alpha = .14f),
    witnessAlert = Color(0xFFEF4444),
    witnessInactive = Color.White.copy(alpha = .62f),
    witnessLights = Color(0xFF4ADE80),
    fuelInactive = Color.White.copy(alpha = .16f),
    numberFamily = FontFamily.SansSerif,
    numberWeight = FontWeight.Bold,
    labelFamily = FontFamily.SansSerif,
    map = MapThemeColors.Classic.copy(
        loadingBackground = if (dark) Color(0xFF151A1E) else Color(0xFFE9EBEE),
        attribution = if (dark) Color.White.copy(alpha = .42f) else Color.Black.copy(alpha = .45f),
        cardBackground = HorizonGlass.copy(alpha = .92f),
        cardBorder = Color.White.copy(alpha = .10f),
        cardText = Color.White.copy(alpha = .86f),
        cardValue = Color.White,
        controlBackground = HorizonGlass.copy(alpha = .92f),
        controlBorder = Color.White.copy(alpha = .18f),
        controlIcon = Color.White,
        controlAccent = HorizonCyan,
    ),
)

private val FacetsNavy = Color(0xFF050B18)
private val FacetsSurface = Color(0xFF0A1428)

private fun facetsTokens(dark: Boolean) = HomeTokens(
    background = FacetsNavy,
    chrome = FacetsNavy,
    surface = FacetsSurface.copy(alpha = .95f),
    surfaceBorder = Color(0xFF7FA6E8).copy(alpha = .55f),
    content = Color.White,
    label = Color(0xFF9DB0CF),
    accent = Color(0xFF6FA8FF),
    gaugeFill = listOf(Color(0xFFC2378F), Color(0xFFF0587A), Color(0xFFFF9C7A)),
    gaugeTrack = Color(0xFF7FA6E8).copy(alpha = .10f),
    gaugeOutline = Color(0xFFB9D0F5),
    redZone = Color(0xFFE5484D),
    warning = Color(0xFFFF6B7A),
    caution = Color(0xFFFFC857),
    divider = Color(0xFF8FB0E8).copy(alpha = .20f),
    commandDivider = Color(0xFF8FB0E8).copy(alpha = .22f),
    dragPlate = Color(0xFF1A2C4D),
    witnessAlert = Color(0xFFFF5C6C),
    witnessInactive = Color(0xFF41567A),
    witnessLights = Color(0xFF4ADE80),
    fuelInactive = Color(0xFF7FA6E8).copy(alpha = .16f),
    numberFamily = FontFamily.SansSerif,
    numberWeight = FontWeight.Light,
    labelFamily = FontFamily.SansSerif,
    map = MapThemeColors.Classic.copy(
        loadingBackground = if (dark) Color(0xFF0A1830) else Color(0xFFE2E8F0),
        attribution = if (dark) Color.White.copy(alpha = .42f) else Color(0xFF34445C).copy(alpha = .55f),
        cardBackground = Color(0xF00A1428),
        cardBorder = Color(0xFF7FA6E8).copy(alpha = .45f),
        cardText = Color(0xFFD6E2F4),
        cardValue = Color.White,
        controlBackground = Color(0xEE0A1428),
        controlBorder = Color(0xFF7FA6E8).copy(alpha = .45f),
        controlIcon = Color.White,
        controlAccent = Color(0xFFFF8A7A),
    ),
)

private val TelemetryYellow = Color(0xFFFACC15)

private fun telemetryTokens(dark: Boolean) = HomeTokens(
    background = Color.Black,
    chrome = Color.Black,
    surface = Color.Black,
    surfaceBorder = Color.White.copy(alpha = .42f),
    content = Color.White,
    label = Color.White.copy(alpha = .88f),
    accent = TelemetryYellow,
    gaugeFill = listOf(TelemetryYellow, TelemetryYellow),
    gaugeTrack = Color.White.copy(alpha = .22f),
    gaugeOutline = Color.White.copy(alpha = .55f),
    redZone = Color(0xFFEF4444),
    warning = Color(0xFFEF4444),
    caution = TelemetryYellow,
    divider = Color.White.copy(alpha = .26f),
    commandDivider = Color.White.copy(alpha = .20f),
    dragPlate = Color(0xFF262626),
    witnessAlert = Color(0xFFEF4444),
    witnessInactive = Color.White.copy(alpha = .86f),
    witnessLights = Color(0xFF22C55E),
    fuelInactive = Color.White.copy(alpha = .20f),
    // Roboto Black: the heaviest system face, closest to the design's square figures.
    numberFamily = FontFamily.SansSerif,
    numberWeight = FontWeight.Black,
    labelFamily = FontFamily.SansSerif,
    map = MapThemeColors.Classic.copy(
        loadingBackground = if (dark) Color(0xFF0D0D0D) else Color(0xFFF1F1EE),
        attribution = if (dark) Color.White.copy(alpha = .42f) else Color.Black.copy(alpha = .45f),
        cardBackground = Color(0xF0070707),
        cardBorder = Color.White.copy(alpha = .55f),
        cardText = Color.White,
        cardValue = Color.White,
        controlBackground = Color(0xEE070707),
        controlBorder = Color.White.copy(alpha = .40f),
        controlIcon = Color.White,
        controlAccent = TelemetryYellow,
    ),
)

/** The classic order keeps its original key so existing installs keep their arrangement. */
internal fun footerBlockOrderKey(theme: HomeTheme): String =
    if (theme == HomeTheme.CLASSIC) "footer_block_order" else "footer_block_order_${theme.name.lowercase()}"

internal fun defaultFooterBlockOrder(theme: HomeTheme): List<FooterBlockItem> = when (theme) {
    HomeTheme.TELEMETRY, HomeTheme.ADVENTURE -> listOf(
        FooterBlockItem.CONSUMPTION,
        FooterBlockItem.RANGE,
        FooterBlockItem.TIME,
        FooterBlockItem.TRIP,
        FooterBlockItem.REFUEL_DISTANCE,
        FooterBlockItem.ODOMETER,
        FooterBlockItem.FUEL,
        FooterBlockItem.WITNESSES,
    )
    HomeTheme.BLUE_CYAN, HomeTheme.BLUE_LIGHT ->
        DefaultFooterBlockOrder - FooterBlockItem.WITNESSES + FooterBlockItem.WITNESSES
    else -> DefaultFooterBlockOrder
}

internal fun homeTokensFor(theme: HomeTheme, dark: Boolean): HomeTokens = when (theme) {
    HomeTheme.CLASSIC -> ClassicHomeTokens
    HomeTheme.HORIZON -> horizonTokens(dark)
    HomeTheme.FACETS -> facetsTokens(dark)
    HomeTheme.TELEMETRY -> telemetryTokens(dark)
    HomeTheme.SUBMARINE -> illustratedTokens(SubmarinePalette, dark)
    HomeTheme.MOON -> illustratedTokens(MoonPalette, dark)
    HomeTheme.GALAXY -> illustratedTokens(GalaxyPalette, dark)
    // White witnesses and a dark empty fuel level read better on the bright tiles.
    HomeTheme.BLUE_CYAN -> blueHomeTokens(cyan = true, dark = dark)
    HomeTheme.BLUE_LIGHT -> blueHomeTokens(cyan = false, dark = dark)
    HomeTheme.ADVENTURE -> illustratedTokens(AdventurePalette, dark).copy(
        witnessInactive = Color.White.copy(alpha = .9f),
        fuelInactive = Color(0x66081A4D),
    )
}

internal val LocalHomeTokens = staticCompositionLocalOf { ClassicHomeTokens }
