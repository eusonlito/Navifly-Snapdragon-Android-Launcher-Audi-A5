package com.lito.a5launcher.ui.home

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.model.DoorStatus
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.StatisticsPanelScope
import com.lito.a5launcher.ui.components.TopCommandItem

/**
 * Home data as observable holders. Layouts read each value in the leaf that
 * draws it, so a telemetry update never recomposes the whole home.
 */
@Stable
internal class HomeState(
    val speed: State<Int>,
    val rpm: State<Int>,
    val gear: State<String>,
    val doors: State<DoorStatus>,
    val fuel: State<Int>,
    val mileage: State<Int>,
    val range: State<Int>,
    val outside: State<Double>,
    val seatbeltAlert: State<Boolean>,
    val parkingBrake: State<Boolean>,
    val lightsActive: State<Boolean>,
    val trip: State<JourneyStatisticsSnapshot>,
    val partial: State<JourneyStatisticsSnapshot>,
    val time: State<String>,
    val date: State<String>,
    val startupProgress: State<Float?>,
    val topCommandOrder: State<List<TopCommandItem>>,
    val footerBlockOrder: State<List<FooterBlockItem>>,
    val assistantEnabled: State<Boolean>,
    val assistantActive: State<Boolean>,
    val navigationEnabled: State<Boolean>,
)

@Immutable
internal class HomeActions(
    val onNavigation: () -> Unit,
    val onAssistant: () -> Unit,
    val onApps: () -> Unit,
    val onLauncherSettings: () -> Unit,
    val onDeviceSettings: () -> Unit,
    val onRecents: () -> Unit,
    val onMmi: () -> Unit,
    val onStatistics: (StatisticsPanelScope) -> Unit,
    val onTopCommandOrderChanged: (List<TopCommandItem>) -> Unit,
    val onFooterBlockOrderChanged: (List<FooterBlockItem>) -> Unit,
)

/** Content owned by DashboardScreen that every layout places in its own map area. */
@Immutable
internal class HomeSlots(
    val map: @Composable (Modifier, MapViewportSpec) -> Unit,
    /**
     * Drawn over the whole map area, replacing it. The padding is the part of
     * that area covered by floating panels, measured up to their edges; the
     * overlay adds its own gap from them.
     */
    val mapOverlay: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
)
