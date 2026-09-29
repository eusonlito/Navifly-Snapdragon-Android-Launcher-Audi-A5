package com.lito.a5launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.JourneyHistoryKind
import com.lito.a5launcher.JourneyHistoryRecord
import com.lito.a5launcher.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

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
    HorizontalPager(state = pager, modifier = modifier.background(Color.Black)) { page ->
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
            modifier = Modifier.fillMaxSize(),
        )
    }
}

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
    modifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        val contentWidth = minOf(maxWidth * .4f, 430.dp)
        Column(
            modifier = Modifier
                .width(contentWidth)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (startedAtEpochMs == null) 12.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
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
                        tint = OemCockpitTokens.Cyan,
                        darkModeActive = darkModeActive,
                        onClick = onClose,
                    )
                }
            }

            startedAtEpochMs?.let { startedAt ->
                val coroutineScope = rememberCoroutineScope()
                val dateFormat = remember(locale) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = page > 0,
                        onClick = { coroutineScope.launch { pager.animateScrollToPage(page - 1) } },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(R.string.statistics_newer_record),
                            tint = Color.White.copy(alpha = if (page > 0) 1f else .3f),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.journey_history_period,
                            dateFormat.format(Date(startedAt)),
                            dateFormat.format(Date(endedAtEpochMs)),
                        ),
                        color = Color.White.copy(alpha = .66f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = page < pager.pageCount - 1,
                        onClick = { coroutineScope.launch { pager.animateScrollToPage(page + 1) } },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(R.string.statistics_older_record),
                            tint = Color.White.copy(alpha = if (page < pager.pageCount - 1) 1f else .3f),
                        )
                    }
                }
            }

            StatisticsRow(
                firstLabel = stringResource(R.string.statistics_distance),
                firstValue = stringResource(
                    R.string.statistics_distance_value,
                    formatOneDecimal(statistics.distanceKm, locale),
                ),
                secondLabel = stringResource(R.string.statistics_fuel_used),
                secondValue = stringResource(
                    R.string.statistics_fuel_value,
                    formatFuelUsed(statistics.fuelUsedLitres, locale),
                ),
            )
            StatisticsRow(
                firstLabel = stringResource(R.string.statistics_elapsed_time),
                firstValue = formatTripDuration(statistics.elapsedMs),
                secondLabel = stringResource(R.string.statistics_moving_time),
                secondValue = formatTripDuration(statistics.movingElapsedMs),
            )
            StatisticsRow(
                firstLabel = stringResource(R.string.statistics_average_speed),
                firstValue = statisticsSpeedValue(statistics.averageSpeedKmh, locale),
                secondLabel = stringResource(R.string.statistics_moving_average_speed),
                secondValue = statisticsSpeedValue(statistics.movingAverageSpeedKmh, locale),
            )
            StatisticsRow(
                firstLabel = stringResource(R.string.consumption_calculated),
                firstValue = statisticsConsumptionValue(
                    statistics.calculatedConsumption,
                    statistics.distanceKm > 0.0,
                    locale,
                ),
                secondLabel = stringResource(R.string.consumption_simple),
                secondValue = statisticsConsumptionValue(
                    statistics.observedCanConsumption,
                    statistics.confirmedCanFuelUsedLitres > 0.0 && statistics.distanceKm > 0.0,
                    locale,
                ),
            )
            StatisticsRow(
                firstLabel = stringResource(R.string.statistics_maximum_speed),
                firstValue = statisticsSpeedValue(statistics.maximumSpeedKmh.toDouble(), locale),
                secondLabel = stringResource(R.string.statistics_fuel_spent),
                secondValue = statistics.observedFuelSpentLitres?.let { fuelSpent ->
                    stringResource(
                        R.string.statistics_fuel_value,
                        formatFuelUsed(fuelSpent, locale),
                    )
                } ?: "—",
            )
        }
    }
}

@Composable
private fun StatisticsRow(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = .1f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(top = 1.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        StatisticsValue(firstLabel, firstValue, Modifier.weight(1f))
        StatisticsValue(secondLabel, secondValue, Modifier.weight(1f))
    }
}

@Composable
private fun StatisticsValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = .66f),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
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
