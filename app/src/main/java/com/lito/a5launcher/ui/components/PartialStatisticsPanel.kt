package com.lito.a5launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.JourneyHistoryRecord
import com.lito.a5launcher.PartialStatistics
import com.lito.a5launcher.PARTIALS_PER_PAGE
import com.lito.a5launcher.partialStatisticsPage
import com.lito.a5launcher.R
import com.lito.a5launcher.buildPartialStatistics
import com.lito.a5launcher.partialCanConsumption
import com.lito.a5launcher.partialEstimatedConsumption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

@Composable
internal fun PartialStatisticsPanel(records: List<JourneyHistoryRecord>, onBack: () -> Unit) {
    // A stable entry snapshot keeps telemetry recompositions out of filtering and aggregation.
    val entryRecords = remember { records }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    var fromDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var toDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale) }
    val shortDate = remember(locale, zone) { DateTimeFormatter.ofPattern("dd MMM yy", locale).withZone(zone) }
    val result by produceState<PartialStatistics?>(null, entryRecords, fromDay, toDay) {
        value = null
        value = withContext(Dispatchers.Default) {
            buildPartialStatistics(
                entryRecords,
                fromDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(zone).toInstant().toEpochMilli() },
                toDay?.let { LocalDate.ofEpochDay(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() },
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentHeight = maxHeight.coerceAtLeast(368.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.fillMaxWidth().height(contentHeight), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsActionButton(stringResource(R.string.launcher_settings_tab_trips), 40.dp, icon = SettingsIcons.Back, onClick = onBack)
                    Text(stringResource(R.string.history_statistics_title), color = SettingsPalette.Text, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    JourneyDateField(stringResource(R.string.journey_history_from), fromDay, dateFormatter, Modifier.width(160.dp), { fromDay = null }) {
                        showDatePicker(context, fromDay?.let(LocalDate::ofEpochDay)) {
                            fromDay = it.toEpochDay()
                            if (toDay != null && fromDay!! > toDay!!) toDay = fromDay
                        }
                    }
                    JourneyDateField(stringResource(R.string.journey_history_to), toDay, dateFormatter, Modifier.width(160.dp), { toDay = null }) {
                        showDatePicker(context, toDay?.let(LocalDate::ofEpochDay)) {
                            toDay = it.toEpochDay()
                            if (fromDay != null && toDay!! < fromDay!!) fromDay = toDay
                        }
                    }
                }
                val data = result
                if (data == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SettingsPalette.Accent)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatisticSummary(stringResource(R.string.journey_history_filter_partials), data.records.size.toString(), Modifier.weight(1f))
                        StatisticSummary(stringResource(R.string.statistics_distance), "${number(data.distanceKm, locale, 0)} km", Modifier.weight(1f))
                        StatisticSummary(stringResource(R.string.history_statistics_mean_estimated), consumption(data.estimatedConsumption, locale), Modifier.weight(1f))
                        StatisticSummary(stringResource(R.string.history_statistics_mean_can), consumption(data.canConsumption, locale), Modifier.weight(1f))
                    }
                    if (data.records.isEmpty()) {
                        SettingsCard(Modifier.fillMaxWidth().weight(1f)) {
                            SettingsEmptyState(SettingsIcons.Chart, stringResource(R.string.history_statistics_empty), Modifier.fillMaxSize())
                        }
                    } else {
                        var page by remember(data.records) { mutableIntStateOf(0) }
                        val visibleRecords = partialStatisticsPage(data.records, page)
                        val selected = visibleRecords.firstOrNull { it.id == selectedId } ?: visibleRecords.last()
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsCard(
                                Modifier.weight(2f).fillMaxHeight(),
                                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                                footer = {
                                    StatisticsPagination(page, (data.records.size + PARTIALS_PER_PAGE - 1) / PARTIALS_PER_PAGE) { page = it }
                                },
                            ) {
                                ConsumptionChart(data.records, visibleRecords, selected.id, locale, shortDate) { selectedId = it }
                            }
                            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                                PartialDetail(selected, locale, shortDate)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticSummary(label: String, value: String, modifier: Modifier) {
    SettingsCard(modifier.height(50.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label.uppercase(LocalConfiguration.current.locales[0]), color = SettingsPalette.MutedText, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
        Text(value, color = SettingsPalette.Text, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ConsumptionChart(
    records: List<JourneyHistoryRecord>, visibleRecords: List<JourneyHistoryRecord>,
    selectedId: String, locale: Locale,
    dateFormatter: DateTimeFormatter, onSelect: (String) -> Unit,
) {
    val maximum = remember(records) {
        val peak = records.maxOf { maxOf(partialEstimatedConsumption(it.statistics) ?: 0.0, partialCanConsumption(it.statistics) ?: 0.0) }
        (ceil(peak / 2.0) * 2.0).coerceAtLeast(8.0)
    }
    val speedMaximum = remember(records) {
        (ceil(records.maxOf { it.statistics.averageSpeedKmh } / 20.0) * 20.0).coerceAtLeast(80.0)
    }
    val speedColor = Color(0xFFCEC4FF)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChartLegend(stringResource(R.string.history_statistics_estimated), stringResource(R.string.history_statistics_consumption_unit), SettingsPalette.Accent)
            ChartLegend(stringResource(R.string.history_statistics_can), stringResource(R.string.history_statistics_consumption_unit), SettingsPalette.Warning)
            ChartLegend(stringResource(R.string.history_statistics_speed), "km/h", speedColor)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val plotHeight = (maxHeight - 46.dp).coerceAtLeast(1.dp)
            Canvas(Modifier.fillMaxWidth().padding(top = 18.dp).height(plotHeight)) {
                (0..4).forEach { tick ->
                    val y = size.height * tick / 4
                    drawLine(SettingsPalette.BorderStrong, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
            }
            Row(Modifier.fillMaxSize()) {
                visibleRecords.forEach { record ->
                    val speedDescription = stringResource(R.string.statistics_average_speed) + ": " + number(record.statistics.averageSpeedKmh, locale, 0) + " km/h"
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                            .background(if (record.id == selectedId) SettingsPalette.Accent.copy(alpha = .08f) else Color.Transparent)
                            .selectable(selected = record.id == selectedId, role = Role.RadioButton, onClick = { onSelect(record.id) })
                            .semantics { contentDescription = speedDescription },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            Modifier.widthIn(max = 78.dp).fillMaxWidth().height(plotHeight + 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom,
                        ) {
                            StatisticsBar(partialEstimatedConsumption(record.statistics), maximum, plotHeight, SettingsPalette.Accent, locale, 1, Modifier.weight(1f))
                            StatisticsBar(partialCanConsumption(record.statistics), maximum, plotHeight, SettingsPalette.Warning, locale, 1, Modifier.weight(1f))
                            StatisticsBar(record.statistics.averageSpeedKmh, speedMaximum, plotHeight, speedColor, locale, 0, Modifier.weight(1f))
                        }
                        Text(dateFormatter.format(Instant.ofEpochMilli(record.endedAtEpochMs)), color = SettingsPalette.Text, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${number(record.statistics.distanceKm, locale, 0)} km", color = SettingsPalette.MutedText, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                    }
                }
                repeat(PARTIALS_PER_PAGE - visibleRecords.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatisticsPagination(page: Int, pages: Int, onPage: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.history_statistics_older).uppercase(locale),
            color = if (page < pages - 1) SettingsPalette.Accent else SettingsPalette.SubtleText,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clickable(enabled = page < pages - 1, role = Role.Button) { onPage(page + 1) }
                .padding(horizontal = 16.dp).wrapContentSize(Alignment.CenterStart),
        )
        Text(stringResource(R.string.history_statistics_page, pages - page, pages), color = SettingsPalette.MutedText, fontSize = 9.sp)
        Text(
            stringResource(R.string.history_statistics_newer).uppercase(locale),
            color = if (page > 0) SettingsPalette.Accent else SettingsPalette.SubtleText,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clickable(enabled = page > 0, role = Role.Button) { onPage(page - 1) }
                .padding(horizontal = 16.dp).wrapContentSize(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun StatisticsBar(value: Double?, maximum: Double, plotHeight: androidx.compose.ui.unit.Dp, color: Color, locale: Locale, decimals: Int, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text(value?.let { number(it, locale, decimals) } ?: "—", color = color, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Box(Modifier.widthIn(max = 22.dp).fillMaxWidth().height(plotHeight * ((value ?: 0.0) / maximum).toFloat()).background(color))
    }
}

@Composable
private fun ChartLegend(label: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Column {
            Text(label, color = SettingsPalette.Text, fontSize = 11.sp, lineHeight = 14.sp)
            Text(unit, color = SettingsPalette.MutedText, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun PartialDetail(record: JourneyHistoryRecord, locale: Locale, dateFormatter: DateTimeFormatter) {
    val stats = record.statistics
    val moving = stats.movingElapsedMs.coerceIn(0L, stats.elapsedMs.coerceAtLeast(0L))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.SpaceBetween) {
        SettingsSectionTitle("${stringResource(R.string.journey_history_partial)} · ${dateFormatter.format(Instant.ofEpochMilli(record.endedAtEpochMs))}", SettingsIcons.Flag)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetric(stringResource(R.string.statistics_distance), "${number(stats.distanceKm, locale, 1)} km", Modifier.weight(1f))
            DetailMetric(stringResource(R.string.statistics_elapsed_time), duration(stats.elapsedMs), Modifier.weight(1f))
        }
        SettingsDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetric(stringResource(R.string.history_statistics_estimated), consumption(partialEstimatedConsumption(stats), locale), Modifier.weight(1f), SettingsPalette.Accent)
            DetailMetric(stringResource(R.string.history_statistics_can), consumption(partialCanConsumption(stats), locale), Modifier.weight(1f), SettingsPalette.Warning)
        }
        SettingsDivider()
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailMetric(stringResource(R.string.statistics_moving_time), duration(moving), Modifier.weight(1f))
                DetailMetric(stringResource(R.string.history_statistics_stopped), duration((stats.elapsedMs - moving).coerceAtLeast(0)), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(SettingsPalette.SubtleText)) {
                if (stats.elapsedMs > 0L && moving > 0L) Box(Modifier.fillMaxHeight().fillMaxWidth(moving.toFloat() / stats.elapsedMs).background(SettingsPalette.Accent))
            }
        }
        SettingsDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetric(stringResource(R.string.history_statistics_speed), "${number(stats.averageSpeedKmh, locale, 0)} km/h", Modifier.weight(1f), Color(0xFFCEC4FF))
            DetailMetric(stringResource(R.string.statistics_maximum_speed), "${stats.maximumSpeedKmh} km/h", Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier, color: Color = SettingsPalette.Text) {
    Column(modifier) {
        Text(label, color = SettingsPalette.MutedText, fontSize = 10.sp, lineHeight = 13.sp)
        Text(value, color = color, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun number(value: Double, locale: Locale, decimals: Int): String = NumberFormat.getNumberInstance(locale).apply {
    minimumFractionDigits = decimals
    maximumFractionDigits = decimals
}.format(value)

@Composable
private fun consumption(value: Double?, locale: Locale): String = value?.let {
    "${number(it, locale, 1)} ${stringResource(R.string.history_statistics_consumption_unit)}"
} ?: "—"

@Composable
private fun duration(milliseconds: Long): String {
    val minutes = milliseconds.coerceAtLeast(0L) / 60_000
    return stringResource(R.string.history_statistics_duration, minutes / 60, minutes % 60)
}
