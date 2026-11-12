package com.lito.a5launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.JourneyHistoryRecord
import com.lito.a5launcher.PartialStatistics
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
                        val selected = data.records.firstOrNull { it.id == selectedId } ?: data.records.last()
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsCard(Modifier.weight(2f).fillMaxHeight()) {
                                ConsumptionChart(data.records, selected.id, locale, shortDate) { selectedId = it }
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
    SettingsCard(modifier.height(72.dp)) {
        Text(label.uppercase(LocalConfiguration.current.locales[0]), color = SettingsPalette.MutedText, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
        Text(value, color = SettingsPalette.Text, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ConsumptionChart(
    records: List<JourneyHistoryRecord>, selectedId: String, locale: Locale,
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
    SettingsSectionTitle(stringResource(R.string.history_statistics_chart), SettingsIcons.Chart)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChartLegend(stringResource(R.string.history_statistics_estimated), SettingsPalette.Accent)
        ChartLegend(stringResource(R.string.history_statistics_can), SettingsPalette.Warning)
        Text(stringResource(R.string.history_statistics_consumption_unit), color = SettingsPalette.MutedText, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        ChartLegend(stringResource(R.string.history_statistics_speed), speedColor, line = true)
    }
    Spacer(Modifier.height(4.dp))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val plotHeight = (maxHeight - 52.dp).coerceAtLeast(40.dp)
        val groupWidth = ((maxWidth - 28.dp) / records.size.coerceAtMost(4)).coerceAtLeast(116.dp)
        val state = rememberLazyListState()
        LaunchedEffect(records) { state.scrollToItem((records.size - 4).coerceAtLeast(0)) }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(Modifier.fillMaxWidth().padding(top = 16.dp).height(plotHeight)) {
                    (0..4).forEach { tick ->
                        val y = size.height * tick / 4
                        drawLine(SettingsPalette.BorderStrong, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }
                LazyRow(state = state, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
                        val speedDescription = stringResource(R.string.statistics_average_speed) + ": " + number(record.statistics.averageSpeedKmh, locale, 0) + " km/h"
                        Column(
                            Modifier.width(groupWidth).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                                .background(if (record.id == selectedId) SettingsPalette.Accent.copy(alpha = .08f) else Color.Transparent)
                                .selectable(selected = record.id == selectedId, role = Role.RadioButton, onClick = { onSelect(record.id) })
                                .semantics { contentDescription = speedDescription },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().height(plotHeight + 16.dp)) {
                                Row(Modifier.align(Alignment.BottomCenter).width(84.dp).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                                    ConsumptionBar(partialEstimatedConsumption(record.statistics), maximum, plotHeight, SettingsPalette.Accent, locale, Modifier.weight(1f))
                                    ConsumptionBar(partialCanConsumption(record.statistics), maximum, plotHeight, SettingsPalette.Warning, locale, Modifier.weight(1f))
                                }
                                Canvas(Modifier.fillMaxWidth().padding(top = 16.dp).height(plotHeight)) {
                                    fun y(speed: Double) = size.height * (1 - speed / speedMaximum).toFloat()
                                    val current = record.statistics.averageSpeedKmh
                                    val point = Offset(size.width / 2, y(current))
                                    if (index > 0) {
                                        val previous = records[index - 1].statistics.averageSpeedKmh
                                        drawLine(speedColor, Offset(0f, y((previous + current) / 2)), point, 2.dp.toPx())
                                    }
                                    if (index < records.lastIndex) {
                                        val next = records[index + 1].statistics.averageSpeedKmh
                                        drawLine(speedColor, point, Offset(size.width, y((next + current) / 2)), 2.dp.toPx())
                                    }
                                    drawCircle(SettingsPalette.Card, 5.dp.toPx(), point)
                                    drawCircle(speedColor, 3.dp.toPx(), point)
                                }
                            }
                            Text(dateFormatter.format(Instant.ofEpochMilli(record.endedAtEpochMs)), color = SettingsPalette.Text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${number(record.statistics.distanceKm, locale, 0)} km", color = SettingsPalette.MutedText, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            Box(Modifier.width(28.dp).padding(top = 16.dp).height(plotHeight)) {
                (0..4).forEach { tick ->
                    Text(number(speedMaximum * (4 - tick) / 4, locale, 0), color = speedColor, fontSize = 10.sp, lineHeight = 12.sp,
                        modifier = Modifier.align(Alignment.TopEnd).offset(y = plotHeight * (tick / 4f) - 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ConsumptionBar(value: Double?, maximum: Double, plotHeight: androidx.compose.ui.unit.Dp, color: Color, locale: Locale, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.let { number(it, locale, 1) } ?: "—", color = color, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Box(Modifier.widthIn(max = 34.dp).fillMaxWidth().height(plotHeight * ((value ?: 0.0) / maximum).toFloat()).background(color))
    }
}

@Composable
private fun ChartLegend(label: String, color: Color, line: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(if (line) 15.dp else 7.dp, if (line) 2.dp else 7.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = SettingsPalette.Text, fontSize = 11.sp)
    }
}

@Composable
private fun PartialDetail(record: JourneyHistoryRecord, locale: Locale, dateFormatter: DateTimeFormatter) {
    val stats = record.statistics
    val moving = stats.movingElapsedMs.coerceIn(0L, stats.elapsedMs.coerceAtLeast(0L))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingsSectionTitle("${stringResource(R.string.journey_history_partial)} · ${dateFormatter.format(Instant.ofEpochMilli(record.endedAtEpochMs))}", SettingsIcons.Flag)
        Text("${number(stats.distanceKm, locale, 1)} km", color = SettingsPalette.Text, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        DetailLine(stringResource(R.string.history_statistics_estimated), consumption(partialEstimatedConsumption(stats), locale), SettingsPalette.Accent)
        DetailLine(stringResource(R.string.history_statistics_can), consumption(partialCanConsumption(stats), locale), SettingsPalette.Warning)
        SettingsDivider()
        DetailLine(stringResource(R.string.statistics_elapsed_time), duration(stats.elapsedMs))
        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)).background(SettingsPalette.SubtleText)) {
            if (stats.elapsedMs > 0L && moving > 0L) Box(Modifier.fillMaxHeight().fillMaxWidth(moving.toFloat() / stats.elapsedMs).background(SettingsPalette.Accent))
        }
        DetailLine(stringResource(R.string.statistics_moving_time), duration(moving))
        DetailLine(stringResource(R.string.history_statistics_stopped), duration((stats.elapsedMs - moving).coerceAtLeast(0)))
        SettingsDivider()
        DetailLine(stringResource(R.string.statistics_average_speed), "${number(stats.averageSpeedKmh, locale, 0)} km/h")
        DetailLine(stringResource(R.string.statistics_maximum_speed), "${stats.maximumSpeedKmh} km/h")
    }
}

@Composable
private fun DetailLine(label: String, value: String, color: Color = SettingsPalette.MutedText) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = 12.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = SettingsPalette.Text, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
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
