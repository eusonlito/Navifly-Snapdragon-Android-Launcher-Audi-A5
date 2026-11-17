package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lito.a5launcher.ui.components.CockpitMapIntegrationOverlay
import com.lito.a5launcher.ui.components.DialType
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.MapViewportSpec
import com.lito.a5launcher.ui.components.OEM_LEFT_DIAL_CENTER_FRACTION
import com.lito.a5launcher.ui.components.OEM_RIGHT_DIAL_CENTER_FRACTION
import com.lito.a5launcher.ui.components.ProgressRingIndicator
import com.lito.a5launcher.ui.components.SPEED_DIAL_MAX_KMH
import com.lito.a5launcher.ui.components.oemStartupPresentation
import java.util.Locale

internal const val RPM_SCALE_MAX = 6000
private val ClassicMapViewport = MapViewportSpec()

/** The original cockpit: two seated circular dials over a full-width map band. */
@Composable
internal fun ClassicHomeLayout(
    state: HomeState,
    actions: HomeActions,
    slots: HomeSlots,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHomeTokens.current
    BoxWithConstraints(modifier.fillMaxSize().background(tokens.background)) {
        val screenWidth = maxWidth
        val commandBarHeight = maxHeight * .125f
        val dialSize = (maxHeight - commandBarHeight * 2f).coerceAtMost(maxWidth * .305f)
        val commandMetrics = CommandMetrics(
            buttonSize = maxHeight * .095f,
            iconSize = maxHeight * .067f,
        )
        val headerTextSize = (maxHeight.value * .040f).sp
        val vitalsLabelSize = (maxHeight.value * .024f).sp
        val vitalsValueSize = (maxHeight.value * .038f).sp
        Column(Modifier.fillMaxSize()) {
            ClassicTopBar(
                state = state,
                actions = actions,
                metrics = commandMetrics,
                barHeight = commandBarHeight,
                textSize = headerTextSize,
                modifier = Modifier
                    .startupContentAlpha(state)
                    .zIndex(2f),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .zIndex(1f),
                contentAlignment = Alignment.Center,
            ) {
                slots.map(Modifier.fillMaxSize(), ClassicMapViewport)
                StartupMapCover(state)
                // Statistics content stays between the two dials.
                slots.mapOverlay(
                    this,
                    PaddingValues(horizontal = screenWidth * OEM_LEFT_DIAL_CENTER_FRACTION + dialSize / 2f),
                )
                CockpitMapIntegrationOverlay(
                    dialDiameter = dialSize,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = screenWidth * OEM_LEFT_DIAL_CENTER_FRACTION - dialSize / 2f)
                            .size(dialSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        ClassicSpeedDial(state, dialSize)
                    }
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = screenWidth * OEM_RIGHT_DIAL_CENTER_FRACTION - dialSize / 2f)
                            .size(dialSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        ClassicRpmDial(state, dialSize)
                    }
                }
            }

            ClassicFooter(
                state = state,
                actions = actions,
                barHeight = commandBarHeight,
                labelSize = vitalsLabelSize,
                valueSize = vitalsValueSize,
                modifier = Modifier
                    .startupContentAlpha(state)
                    .zIndex(2f),
            )
        }
    }
}

/** Fades the non-instrument content in at the end of the startup reveal. */
internal fun Modifier.startupContentAlpha(state: HomeState): Modifier = graphicsLayer {
    alpha = oemStartupPresentation(state.startupProgress.value).contentAlpha
}

/** Hides the map until the startup reveal hands over to the rest of the home. */
@Composable
internal fun StartupMapCover(state: HomeState, modifier: Modifier = Modifier) {
    val tokens = LocalHomeTokens.current
    Canvas(modifier.fillMaxSize()) {
        val hiddenAlpha = 1f - oemStartupPresentation(state.startupProgress.value).contentAlpha
        if (hiddenAlpha > 0f) {
            drawRect(tokens.background.copy(alpha = hiddenAlpha))
        }
    }
}

@Composable
private fun ClassicSpeedDial(state: HomeState, size: Dp) {
    ProgressRingIndicator(
        modifier = Modifier.size(size),
        value = state.speed.value,
        maxValue = SPEED_DIAL_MAX_KMH,
        dialType = DialType.SPEED,
        label = "",
        startupProgress = state.startupProgress,
    )
}

@Composable
private fun ClassicRpmDial(state: HomeState, size: Dp) {
    ProgressRingIndicator(
        modifier = Modifier.size(size),
        value = state.rpm.value,
        maxValue = RPM_SCALE_MAX,
        dialType = DialType.RPM,
        label = "",
        centerGear = state.gear.value,
        startupProgress = state.startupProgress,
    )
}

internal fun formatOutsideTemperature(outside: Double, locale: Locale): String =
    if (outside.isFinite()) String.format(locale, "%.1f°C", outside) else "—"

@Composable
internal fun ClockText(state: HomeState, textSize: TextUnit, separator: Boolean = true) {
    val tokens = LocalHomeTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = state.time.value,
            color = tokens.content,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            fontFamily = tokens.numberFamily,
            maxLines = 1,
        )
        if (separator) {
            Text(
                text = "·",
                color = tokens.content,
                fontSize = textSize * .48f,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Text(
            text = state.date.value,
            color = tokens.content,
            fontSize = textSize * .62f,
            fontWeight = FontWeight.Medium,
            fontFamily = tokens.labelFamily,
            maxLines = 1,
        )
    }
}

@Composable
internal fun OutsideTemperatureText(state: HomeState, textSize: TextUnit) {
    val tokens = LocalHomeTokens.current
    val locale = LocalConfiguration.current.locales[0]
    Text(
        formatOutsideTemperature(state.outside.value, locale),
        color = tokens.content,
        fontSize = textSize,
        fontWeight = FontWeight.Bold,
        fontFamily = tokens.numberFamily,
        maxLines = 1,
    )
}

@Composable
private fun ClassicTopBar(
    state: HomeState,
    actions: HomeActions,
    metrics: CommandMetrics,
    barHeight: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHomeTokens.current
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(tokens.chrome),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                ClockText(state, textSize)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                HomeCommandGroup(state, actions, metrics, ReorderAxis.HORIZONTAL)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                OutsideTemperatureText(state, textSize)
            }
        }
    }
}

@Composable
private fun ClassicFooter(
    state: HomeState,
    actions: HomeActions,
    barHeight: Dp,
    labelSize: TextUnit,
    valueSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHomeTokens.current
    val order = state.footerBlockOrder.value
    val visible = remember(order) { order.toSet() }
    val widths = remember { mutableStateMapOf<FooterBlockItem, Int>() }
    val dividerWidthPx = with(LocalDensity.current) { 1.dp.roundToPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(tokens.chrome),
    ) {
        Row(
            Modifier.fillMaxSize().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            order.forEachIndexed { index, item ->
                key(item) {
                    LinearReorderSlot(
                        item = item,
                        order = order,
                        visibleItems = visible,
                        axis = ReorderAxis.HORIZONTAL,
                        sizes = widths,
                        gapPx = dividerWidthPx,
                        plateShape = null,
                        onMove = actions.onFooterBlockOrderChanged,
                        modifier = (if (item == FooterBlockItem.WITNESSES) Modifier.weight(1f) else Modifier)
                            .fillMaxHeight(),
                    ) {
                        ClassicFooterBlock(item, state, actions, labelSize, valueSize)
                    }
                }
                if (index != order.lastIndex) FooterDivider()
            }
        }
    }
}

@Composable
private fun ClassicFooterBlock(
    item: FooterBlockItem,
    state: HomeState,
    actions: HomeActions,
    labelSize: TextUnit,
    valueSize: TextUnit,
) {
    when (item) {
        FooterBlockItem.WITNESSES -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WitnessGroup(state)
        }
        FooterBlockItem.FUEL -> FooterBlock { FuelValue(state.fuel.value, valueSize) }
        else -> {
            val scope = blockStatisticsScope(item)
            FooterBlock(
                modifier = if (scope != null) Modifier.clickable { actions.onStatistics(scope) } else Modifier,
            ) {
                val reading = rememberBlockReading(item, state)
                if (reading != null) {
                    MiniValue(blockLabel(item), reading.value, reading.warning, labelSize, valueSize)
                }
            }
        }
    }
}

@Composable
private fun FooterBlock(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 26.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.padding(horizontal = horizontalPadding).fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun FooterDivider() {
    val tokens = LocalHomeTokens.current
    Box(
        Modifier
            .fillMaxHeight(.68f)
            .width(1.dp)
            .background(tokens.divider)
    )
}
