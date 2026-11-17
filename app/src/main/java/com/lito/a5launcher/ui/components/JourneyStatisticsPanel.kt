package com.lito.a5launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.JourneyHistoryKind
import com.lito.a5launcher.JourneyHistoryRecord
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.R
import com.lito.a5launcher.ui.home.LocalHomeTokens
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun statisticsPanelHistory(
    records: List<JourneyHistoryRecord>,
    scope: StatisticsPanelScope,
): List<JourneyHistoryRecord> = records.filter {
    when (scope) {
        StatisticsPanelScope.TRIP -> it.kind == JourneyHistoryKind.TRIP
        StatisticsPanelScope.PARTIAL -> it.kind == JourneyHistoryKind.PARTIAL
        StatisticsPanelScope.TOTAL -> false
    }
}.sortedByDescending { it.endedAtEpochMs }

@Composable
internal fun JourneyStatisticsPanel(
    title: String,
    statistics: JourneyStatisticsSnapshot,
    locale: Locale,
    darkModeActive: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    history: List<JourneyHistoryRecord> = emptyList(),
    startedAtEpochMs: Long? = null,
    contentPadding: PaddingValues = PaddingValues(),
) {
    BackHandler(onBack = onClose)
    val nowEpochMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    // This state belongs to the open panel: reopening starts at the live record.
    val pager = rememberPagerState(pageCount = { history.size + 1 })
    HorizontalPager(state = pager, modifier = modifier.background(LocalHomeTokens.current.background)) { page ->
        val record = history.getOrNull(page - 1)
        JourneyStatisticsContent(
            title = title,
            statistics = record?.statistics ?: statistics,
            locale = locale,
            darkModeActive = darkModeActive,
            onClose = onClose,
            onReset = onReset.takeIf { page == 0 },
            pager = pager,
            startedAtEpochMs = record?.startedAtEpochMs ?: startedAtEpochMs,
            endedAtEpochMs = record?.endedAtEpochMs ?: nowEpochMs,
            page = page,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Space between the statistics and the panels that surround the map area. */
private val StatisticsGap = PaddingValues(horizontal = 32.dp, vertical = 20.dp)

@Composable
private fun JourneyStatisticsContent(
    title: String,
    statistics: JourneyStatisticsSnapshot,
    locale: Locale,
    darkModeActive: Boolean,
    onClose: () -> Unit,
    onReset: (() -> Unit)?,
    pager: PagerState,
    startedAtEpochMs: Long?,
    endedAtEpochMs: Long,
    page: Int,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    // The panel takes the place of the map, so it wears the active home theme.
    val tokens = LocalHomeTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = modifier
            .background(tokens.background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .padding(contentPadding)
            .padding(StatisticsGap),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        color = tokens.content,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = tokens.labelFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    startedAtEpochMs?.let { startedAt ->
                        val dateFormat = remember(locale) {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
                        }
                        Text(
                            text = stringResource(
                                R.string.journey_history_period,
                                dateFormat.format(Date(startedAt)),
                                dateFormat.format(Date(endedAtEpochMs)),
                            ),
                            color = tokens.label,
                            fontSize = 12.sp,
                            fontFamily = tokens.labelFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (startedAtEpochMs != null) {
                    val coroutineScope = rememberCoroutineScope()
                    val hasNewer = page > 0
                    val hasOlder = page < pager.pageCount - 1
                    IconButton(
                        enabled = hasNewer,
                        onClick = { coroutineScope.launch { pager.animateScrollToPage(page - 1) } },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(R.string.statistics_newer_record),
                            tint = tokens.content.copy(alpha = if (hasNewer) 1f else .3f),
                        )
                    }
                    IconButton(
                        enabled = hasOlder,
                        onClick = { coroutineScope.launch { pager.animateScrollToPage(page + 1) } },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(R.string.statistics_older_record),
                            tint = tokens.content.copy(alpha = if (hasOlder) 1f else .3f),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    onReset?.let { reset ->
                        PanelIconAction(
                            icon = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.partial_statistics_reset),
                            tint = SettingsPalette.Danger,
                            darkModeActive = darkModeActive,
                            onClick = reset,
                        )
                    }
                    PanelIconAction(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dialog_close),
                        tint = tokens.accent,
                        darkModeActive = darkModeActive,
                        onClick = onClose,
                    )
                }
            }

            // Three themed columns use the full width the layout leaves free; fuel has
            // the longest labels and values, route the shortest.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                StatisticsGroup(
                    title = stringResource(R.string.statistics_group_route),
                    items = listOf(
                        stringResource(R.string.statistics_distance) to stringResource(
                            R.string.statistics_distance_value,
                            formatOneDecimal(statistics.distanceKm, locale),
                        ),
                        stringResource(R.string.statistics_elapsed_time) to
                            formatTripDuration(statistics.elapsedMs),
                        stringResource(R.string.statistics_moving_time) to
                            formatTripDuration(statistics.movingElapsedMs),
                    ),
                    modifier = Modifier.weight(.85f),
                )
                StatisticsGroup(
                    title = stringResource(R.string.statistics_group_speed),
                    items = listOf(
                        stringResource(R.string.statistics_average_speed) to
                            statisticsSpeedValue(statistics.averageSpeedKmh, locale),
                        stringResource(R.string.statistics_moving_average_speed) to
                            statisticsSpeedValue(statistics.movingAverageSpeedKmh, locale),
                        stringResource(R.string.statistics_maximum_speed) to
                            statisticsSpeedValue(statistics.maximumSpeedKmh.toDouble(), locale),
                    ),
                    modifier = Modifier.weight(1f),
                )
                StatisticsGroup(
                    title = stringResource(R.string.statistics_group_fuel),
                    items = listOf(
                        stringResource(R.string.consumption_calculated) to statisticsConsumptionValue(
                            statistics.calculatedConsumption,
                            statistics.distanceKm > 0.0,
                            locale,
                        ),
                        stringResource(R.string.consumption_simple) to statisticsConsumptionValue(
                            statistics.observedCanConsumption,
                            statistics.confirmedCanFuelUsedLitres > 0.0 && statistics.distanceKm > 0.0,
                            locale,
                        ),
                        stringResource(R.string.statistics_fuel_used) to stringResource(
                            R.string.statistics_fuel_value,
                            formatFuelUsed(statistics.fuelUsedLitres, locale),
                        ),
                        stringResource(R.string.statistics_fuel_spent) to (
                            statistics.observedFuelSpentLitres?.let { fuelSpent ->
                                stringResource(R.string.statistics_fuel_value, formatFuelUsed(fuelSpent, locale))
                            } ?: "—"
                            ),
                    ),
                    modifier = Modifier.weight(1.25f),
                )
            }
        }
    }
}

/** A titled column of label/value pairs separated by theme dividers. */
@Composable
private fun StatisticsGroup(title: String, items: List<Pair<String, String>>, modifier: Modifier) {
    val tokens = LocalHomeTokens.current
    Column(modifier) {
        Text(
            text = title.uppercase(),
            color = tokens.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = tokens.labelFamily,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(tokens.accent.copy(alpha = .55f)),
        )
        items.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.divider))
            }
            Column(
                Modifier.padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = label,
                    color = tokens.label,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    fontFamily = tokens.labelFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    color = tokens.content,
                    fontSize = 19.sp,
                    lineHeight = 21.sp,
                    fontWeight = if (tokens.numberWeight == FontWeight.Light) FontWeight.Normal else tokens.numberWeight,
                    fontFamily = tokens.numberFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun statisticsConsumptionValue(
    value: Double,
    available: Boolean,
    locale: Locale,
): String =
    if (available && value.isFinite()) {
        stringResource(R.string.consumption_value_format, formatOneDecimal(value, locale))
    } else {
        "—"
    }

@Composable
internal fun statisticsSpeedValue(value: Double, locale: Locale): String = stringResource(
    R.string.statistics_speed_value,
    formatOneDecimal(value, locale),
)

internal fun formatFuelUsed(value: Double, locale: Locale): String =
    String.format(locale, "%.2f", value.takeIf { it.isFinite() } ?: 0.0)
