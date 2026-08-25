package com.lito.a5launcher.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.JourneyHistoryKind
import com.lito.a5launcher.JourneyHistoryRecord
import com.lito.a5launcher.JourneyHistorySnapshot
import com.lito.a5launcher.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal enum class JourneyHistoryTypeFilter {
    ALL,
    TRIPS,
    PARTIALS,
}

internal fun filterJourneyHistory(
    records: List<JourneyHistoryRecord>,
    type: JourneyHistoryTypeFilter,
    fromEpochMs: Long?,
    untilEpochMs: Long?,
): List<JourneyHistoryRecord> = records.filter { record ->
    val typeMatches = when (type) {
        JourneyHistoryTypeFilter.ALL -> true
        JourneyHistoryTypeFilter.TRIPS -> record.kind == JourneyHistoryKind.TRIP
        JourneyHistoryTypeFilter.PARTIALS -> record.kind == JourneyHistoryKind.PARTIAL
    }
    typeMatches &&
        (fromEpochMs == null || record.endedAtEpochMs >= fromEpochMs) &&
        (untilEpochMs == null || record.startedAtEpochMs < untilEpochMs)
}

@Composable
internal fun JourneyHistoryPanel(
    snapshot: JourneyHistorySnapshot,
    onEnabledChanged: (Boolean) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    var typeFilter by rememberSaveable { mutableStateOf(JourneyHistoryTypeFilter.ALL) }
    var fromEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var toEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    val fromEpochMs = fromEpochDay?.let { epochDay ->
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val untilEpochMs = toEpochDay?.let { epochDay ->
        LocalDate.ofEpochDay(epochDay).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val filtered = remember(snapshot.records, typeFilter, fromEpochMs, untilEpochMs) {
        filterJourneyHistory(snapshot.records, typeFilter, fromEpochMs, untilEpochMs)
    }
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    }
    val filterLabels = mapOf(
        JourneyHistoryTypeFilter.ALL to stringResource(R.string.journey_history_filter_all),
        JourneyHistoryTypeFilter.TRIPS to stringResource(R.string.journey_history_filter_trips),
        JourneyHistoryTypeFilter.PARTIALS to stringResource(R.string.journey_history_filter_partials),
    )
    val enabledLabels = mapOf(
        false to stringResource(R.string.journey_history_disabled),
        true to stringResource(R.string.journey_history_enabled),
    )

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.journey_history_clear_title)) },
            text = { Text(stringResource(R.string.journey_history_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearAll()
                    },
                ) {
                    Text(
                        stringResource(R.string.journey_history_clear_all),
                        color = SettingsPalette.Danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.journey_history_cancel))
                }
            },
        )
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
            SettingsSectionTitle(stringResource(R.string.journey_history_recording))
            Spacer(Modifier.height(4.dp))
            SettingsSegmentedSelector(
                options = listOf(false, true),
                selected = snapshot.enabled,
                label = enabledLabels::getValue,
                controlHeight = SettingsDimensions.SelectorHeight,
                onSelected = onEnabledChanged,
            )

            Spacer(Modifier.height(8.dp))
            SettingsSectionTitle(stringResource(R.string.journey_history_filter_type))
            Spacer(Modifier.height(4.dp))
            SettingsSegmentedSelector(
                options = JourneyHistoryTypeFilter.entries,
                selected = typeFilter,
                label = filterLabels::getValue,
                controlHeight = SettingsDimensions.SelectorHeight,
                onSelected = { typeFilter = it },
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsSectionTitle(stringResource(R.string.journey_history_filter_dates))
                if (fromEpochDay != null || toEpochDay != null) {
                    Text(
                        text = stringResource(R.string.journey_history_clear_dates),
                        color = SettingsPalette.Accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            fromEpochDay = null
                            toEpochDay = null
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsActionButton(
                    text = fromEpochDay?.let {
                        stringResource(
                            R.string.journey_history_from_value,
                            LocalDate.ofEpochDay(it).format(dateFormatter),
                        )
                    } ?: stringResource(R.string.journey_history_from),
                    controlHeight = SettingsDimensions.FieldHeight,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showDatePicker(
                            context = context,
                            initialDate = fromEpochDay?.let(LocalDate::ofEpochDay),
                            onDateSelected = { selected ->
                                fromEpochDay = selected.toEpochDay()
                                if (toEpochDay != null && selected.toEpochDay() > toEpochDay!!) {
                                    toEpochDay = selected.toEpochDay()
                                }
                            },
                        )
                    },
                )
                SettingsActionButton(
                    text = toEpochDay?.let {
                        stringResource(
                            R.string.journey_history_to_value,
                            LocalDate.ofEpochDay(it).format(dateFormatter),
                        )
                    } ?: stringResource(R.string.journey_history_to),
                    controlHeight = SettingsDimensions.FieldHeight,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showDatePicker(
                            context = context,
                            initialDate = toEpochDay?.let(LocalDate::ofEpochDay),
                            onDateSelected = { selected ->
                                toEpochDay = selected.toEpochDay()
                                if (fromEpochDay != null && selected.toEpochDay() < fromEpochDay!!) {
                                    fromEpochDay = selected.toEpochDay()
                                }
                            },
                        )
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.journey_history_results,
                    filtered.size,
                    snapshot.records.size,
                ),
                color = SettingsPalette.MutedText,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(5.dp))
            SettingsActionButton(
                text = stringResource(R.string.journey_history_clear_all),
                controlHeight = SettingsDimensions.FieldHeight,
                modifier = Modifier.fillMaxWidth(),
                enabled = snapshot.records.isNotEmpty(),
                destructive = true,
                onClick = { showClearConfirmation = true },
            )
        }

        SettingsCard(Modifier.weight(2f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsSectionTitle(stringResource(R.string.journey_history_title))
                Text(
                    text = filtered.size.toString(),
                    color = SettingsPalette.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.journey_history_empty),
                        color = SettingsPalette.MutedText,
                        fontSize = 11.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(filtered, key = JourneyHistoryRecord::id) { record ->
                        JourneyHistoryRecordRow(record, locale) { onDeleteRecord(record.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyHistoryRecordRow(
    record: JourneyHistoryRecord,
    locale: Locale,
    onDelete: () -> Unit,
) {
    val statistics = record.statistics
    val dateTimeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SettingsPalette.Control, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (record.kind == JourneyHistoryKind.TRIP) {
                        R.string.journey_history_trip
                    } else {
                        R.string.journey_history_partial
                    },
                ),
                color = SettingsPalette.Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.journey_history_period,
                    formatRecordDate(record.startedAtEpochMs, dateTimeFormatter),
                    formatRecordDate(record.endedAtEpochMs, dateTimeFormatter),
                ),
                color = SettingsPalette.MutedText,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            SettingsDeleteIconButton(
                contentDescription = stringResource(R.string.journey_history_delete),
                onClick = onDelete,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryMetric(
                stringResource(R.string.statistics_distance),
                stringResource(R.string.statistics_distance_value, formatOneDecimal(statistics.distanceKm, locale)),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_elapsed_time),
                formatTripDuration(statistics.elapsedMs),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_moving_time),
                formatTripDuration(statistics.movingElapsedMs),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_average_speed),
                statisticsSpeedValue(statistics.averageSpeedKmh, locale),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_moving_average_speed),
                statisticsSpeedValue(statistics.movingAverageSpeedKmh, locale),
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryMetric(
                stringResource(R.string.statistics_maximum_speed),
                statisticsSpeedValue(statistics.maximumSpeedKmh.toDouble(), locale),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.consumption_calculated),
                statisticsConsumptionValue(
                    statistics.calculatedConsumption,
                    statistics.distanceKm > 0.0,
                    locale,
                ),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.consumption_simple),
                statisticsConsumptionValue(
                    statistics.observedCanConsumption,
                    statistics.confirmedCanFuelUsedLitres > 0.0 && statistics.distanceKm > 0.0,
                    locale,
                ),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_fuel_used),
                historyFuelValue(statistics.fuelUsedLitres, locale),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_fuel_spent),
                statistics.observedFuelSpentLitres?.let { historyFuelValue(it, locale) } ?: "—",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = SettingsPalette.MutedText,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = SettingsPalette.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun historyFuelValue(value: Double, locale: Locale): String = stringResource(
    R.string.statistics_fuel_value,
    formatFuelUsed(value, locale),
)

private fun formatRecordDate(epochMs: Long, formatter: DateTimeFormatter): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)

private fun showDatePicker(
    context: android.content.Context,
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
) {
    val date = initialDate ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth,
    ).show()
}
