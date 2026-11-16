package com.lito.a5launcher.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Runtime colours applied over the OpenFreeMap Positron/Dark layer families. */
@Immutable
internal data class MapPalette(
    val background: String,
    val water: String,
    val land: String,
    val green: String,
    val building: String,
    val path: String,
    val minorRoad: String,
    val majorRoad: String,
    val motorway: String,
    val roadCasing: String,
    val railway: String,
    val boundary: String,
    val label: String,
    val labelHalo: String,
) {
    fun fillColor(layerId: String): String? = when (layerId) {
        "water" -> water
        "landcover_wood", "landuse_park", "park" -> green
        "building" -> building
        "landcover_ice_shelf", "landcover_glacier", "landuse_residential",
        "aeroway-area", "road_area_pier" -> land
        else -> null
    }

    fun lineColor(layerId: String): String? = when {
        layerId == "waterway" -> water
        layerId == "highway_path" -> path
        layerId == "highway_minor" -> minorRoad
        layerId.contains("highway_major") && layerId.contains("casing") -> roadCasing
        layerId.contains("highway_major") -> majorRoad
        layerId.contains("motorway") && layerId.contains("casing") -> roadCasing
        layerId.contains("motorway") -> motorway
        layerId.startsWith("railway") -> railway
        layerId.startsWith("boundary") -> boundary
        else -> null
    }

    companion object {
        /** OpenFreeMap's near-black defaults need more separation on the vehicle display. */
        val ClassicDark = MapPalette(
            background = "#151A1E",
            water = "#102B38",
            land = "#1A2025",
            green = "#1B2922",
            building = "#283137",
            path = "#46535C",
            minorRoad = "#5E6C75",
            majorRoad = "#94A3AC",
            motorway = "#C1CCD2",
            roadCasing = "#252E34",
            railway = "#45515A",
            boundary = "#586770",
            label = "#D6DDE1",
            labelHalo = "#11161A",
        )
    }
}

/** Colours of the controls drawn over the map. */
@Immutable
internal data class MapThemeColors(
    val loadingBackground: Color,
    val attribution: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val cardText: Color,
    val cardValue: Color,
    val badgeBackground: Color,
    val badgeIcon: Color,
    val badgeText: Color,
    val controlBackground: Color,
    val controlBorder: Color,
    val controlIcon: Color,
    val controlAccent: Color,
) {
    companion object {
        val Classic = MapThemeColors(
            loadingBackground = Color(0xFF071014),
            attribution = Color.White.copy(alpha = .42f),
            cardBackground = Color(0xF010181D),
            cardBorder = Color(0xFF344149),
            cardText = Color(0xFFD4DCE0),
            cardValue = Color.White,
            badgeBackground = Color(0xCC261E13),
            badgeIcon = Color(0xFFFF9D32),
            badgeText = Color(0xFFFFC16F),
            controlBackground = Color(0xEE071014),
            controlBorder = Color.White.copy(alpha = .34f),
            controlIcon = Color.White.copy(alpha = .92f),
            controlAccent = Color(0xFF00C8E0),
        )
    }
}

/** Every theme shares the map style; only the dark base gets the classic contrast palette. */
internal fun mapPaletteFor(style: MapTileStyle): MapPalette? =
    if (style == MapTileStyle.DARK) MapPalette.ClassicDark else null

/** Edges of the map covered by home panels; the vehicle stays in the free area. */
@Immutable
internal data class MapViewportSpec(
    val obscuredLeft: Dp = 0.dp,
    val obscuredTop: Dp = 0.dp,
    val obscuredRight: Dp = 0.dp,
    val obscuredBottom: Dp = 0.dp,
    val cornerRadius: Dp = 0.dp,
)

internal enum class MapMarkerState { OK, NO_GPS, NO_NETWORK }

internal fun mapMarkerState(networkAvailable: Boolean, gpsAvailable: Boolean): MapMarkerState =
    when {
        !networkAvailable -> MapMarkerState.NO_NETWORK
        !gpsAvailable -> MapMarkerState.NO_GPS
        else -> MapMarkerState.OK
    }

internal data class MapFocalPadding(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

/**
 * MapLibre centres the camera target in the padded rectangle. The vehicle is
 * horizontally centred in the visible area and placed at 80 % of its height.
 */
internal fun mapFocalPadding(
    width: Float,
    height: Float,
    obscuredLeft: Float = 0f,
    obscuredTop: Float = 0f,
    obscuredRight: Float = 0f,
    obscuredBottom: Float = 0f,
): MapFocalPadding {
    val left = obscuredLeft.coerceIn(0f, width)
    val right = obscuredRight.coerceIn(0f, width - left)
    val top = obscuredTop.coerceIn(0f, height)
    val bottom = obscuredBottom.coerceIn(0f, height - top)
    val focalX = left + (width - left - right) / 2f
    val focalY = top + (height - top - bottom) * .80f
    return MapFocalPadding(
        left = (2f * focalX - width).coerceAtLeast(0f).toDouble(),
        top = (2f * focalY - height).coerceAtLeast(0f).toDouble(),
        right = (width - 2f * focalX).coerceAtLeast(0f).toDouble(),
        bottom = (height - 2f * focalY).coerceAtLeast(0f).toDouble(),
    )
}
